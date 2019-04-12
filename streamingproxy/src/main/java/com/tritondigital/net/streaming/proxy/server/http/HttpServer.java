package com.tritondigital.net.streaming.proxy.server.http;

import com.tritondigital.net.streaming.proxy.dataprovider.DataProvider;
import com.tritondigital.net.streaming.proxy.dataprovider.Packet;
import com.tritondigital.net.streaming.proxy.dataprovider.raw.RawPacketProvider;
import com.tritondigital.net.streaming.proxy.server.Server;
import com.tritondigital.net.streaming.proxy.server.Server.StateChangedListener.ErrorDetail;
import com.tritondigital.net.streaming.proxy.utils.Log;
import com.tritondigital.net.streaming.proxy.utils.QueueInputStream;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ServerSocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * <p>Implementation of a Server that responds to Http requests.
 * The Server uses a socket to listen and negotiate the connection. It then streams using the already established TCP connection in response
 * to an HTTP GET request.
 *
 * <p>The server listens and accepts a connection, the uses non-blocking sockets to read the requests. To be able to use a BufferedReader, the
 * data that is read from the non-blocking socket is pushed in the QueueInputStream, on which the BufferedReader reads. The read blocks
 * until new data is read from the socket and pushed in the QueueInputStream. This adds a new thread, but it simplifies the code and allows
 * interrupting the reads, which a blocking socket does not.
 *
 * <p>Streaming works by having a background thread that continuously loops and get a packet from the packet provider and send this packet
 * using the appropriate transport. If there are no packet ready, the thread blocks due to the provider blocking until a packet is available.
 *
 * <p>Therefore, this server uses 3 threads. One for the server (accept the connection, read from the non-blocking socket and push the new data
 * in the QueueInputStream), one for the Http Request Reception (reads from the QueueInputStream, process the received request and blocks when
 * no more data to read) and one for the Audio packets transfer (blocks when no packets available).
 */
public class HttpServer extends Server
{
    public  static final String SERVER_NAME = "Triton Digital HTTP Proxy";
    private static final String DATE_FORMAT = "EEE, d MMM yyyy HH:mm:ss z";

    private static final int    READ_BUFFER_SIZE = 1024;

    private final SimpleDateFormat  mDateFormatter = new SimpleDateFormat(DATE_FORMAT, Locale.ENGLISH);
    private RawPacketProvider       mRawPacketProvider;

    private Socket                  mConnectedSocket;   // Used for the Request and Response (streaming of the data as a response)
    private ServerSocket            mListeningSocket;   // Listening to accept the connection.

    private QueueInputStream        mHttpRequestInputStream; // Used to read from the connected socket. Blocks until the non-blocking socket has available data.

    private Thread                  mServerThread;
    private Thread                  mAudioPacketsTransferThread;
    private Thread                  mHttpRequestReceiveThread;

    private URI                     mUri;

    private volatile boolean        mPlaying = false;

    private volatile boolean        mListeningSocketBound = false;
    private final Object            mBoundSocketLock = new Object();

    final CharsetEncoder                  mCharsetEncoder = Charset.forName("US-ASCII").newEncoder();

    /**
     * Constructs a HTTP Server that streams the data from the given provider.
     * The new instance needs to have a source set ({@code setDataProvider}) before it starts listening.
     * Use {@code blockUntilReady} before starting to listen to avoid delays in responses.
     */
    public HttpServer()
    {
    }


    @Override
    public void bindAndListen(final int port)
    {
        Log.i(TAG, "Listening on " + (port == -1 ? "any port" : "port " + port));

        if (mRawPacketProvider == null)
        {
            onError(ErrorDetail.NO_DATA_PROVIDER);
            return;
        }

        // Execute all connection code in background
        Runnable runnable = new Runnable()
        {
            @Override
            public void run()
            {
                boolean stoppedByError = false;

                try
                {
                    try
                    {
                        bindSocket(port);
                        mListeningSocketBound = true;
                    }
                    catch (Exception e)
                    {
                        Log.e(TAG, "Exception Caught (binding): " + e);
                        e.printStackTrace();

                        setStateError(ErrorDetail.LISTEN_FAILED);
                        return;
                    }
                    finally
                    {
                        //Unblock the thread waiting for the bind to complete or fail
                        synchronized (mBoundSocketLock)
                        {
                            mBoundSocketLock.notify();
                        }
                    }


                    while(!Thread.interrupted() && mListeningSocket.isBound())
                    {
                        // Accept a connection from the client
                        Socket acceptedSocket;
                        acceptedSocket = mListeningSocket.accept();
                        acceptedSocket.getChannel().configureBlocking(false);

                        if (mConnectedSocket != null)
                        {
                            Log.i(TAG, "Second connection, disconnecting current one.");

                            // Second connection, drop the first one
                            stopAudioPacketsTransferThread();
                            cleanUpConnectedSocket();
                        }

                        // Notify parent that connection was established
                        onConnected();

                        mConnectedSocket = acceptedSocket;

                        // Create input stream
                        mHttpRequestInputStream = new QueueInputStream(READ_BUFFER_SIZE);

                        // Start the thread that receives Http Requests
                        mHttpRequestReceiveThread = new Thread(mHttpReceiveRequestRunnable, "StreamingProxy " + TAG + " requestThread");
                        Log.i(TAG, "Thread " + mHttpRequestReceiveThread.getName() + " starting.");
                        mHttpRequestReceiveThread.start();

                        // Read until server is closed
                        ByteBuffer buf = ByteBuffer.allocateDirect(READ_BUFFER_SIZE);
                        while(!Thread.interrupted() && getState() == State.CONNECTED && mConnectedSocket.isConnected())
                        {
                            buf.clear();

                            // Non-blocking read from the socket. On error break the reading loop, but keep the listening socket in case new connections arrive.
                            int readLength;
                            try
                            {
                                readLength = mConnectedSocket.getChannel().read(buf);
                            }
                            catch (Exception e)
                            {
                                readLength = 0;
                            }

                            if (readLength < 0)
                            {
                                Log.i(TAG, "Other side interrupted connection.");
                                break;
                            }

                            // Put the data so it can be read by the stream reader running in another thread
                            buf.flip();
                            byte[] bytes = new byte[readLength];
                            buf.get(bytes, 0, bytes.length);
                            mHttpRequestInputStream.put(bytes);

                            // Leave the chance to other process to run.
                            Thread.sleep(mPlaying ? 500 : 1);
                        }

                        // Stop the transfer thread, block until stopped
                        stopAudioPacketsTransferThread();

                        // Stop requests reception thread, block until stopped
                        stopHttpRequestReceiveThread();
                    }
                } catch(ClosedChannelException e)
                {
                    // Accept was interrupted, this means that the HTTP server is closing.
                    // The audioPacketsTransfer or the httpRequestReceiveThread was not started, only the serverSocket.accept() throws this exception.

                    stoppedByError = false; // Server is already in the process of closing
                }
                catch (InterruptedException e)
                {
                    // Reading was interrupted by a Thread.interrupt, which means that the server is stopping.
                    stopAudioPacketsTransferThread();
                    stopHttpRequestReceiveThread();

                    stoppedByError = false; // Server is already in the process of stopping
                }
                catch(Exception e)
                {
                    Log.e(TAG, "Exception Caught (server thread): " + e);
                    e.printStackTrace();

                    stopAudioPacketsTransferThread();
                    stopHttpRequestReceiveThread();

                    stoppedByError = true;
                }
                finally
                {
                    setStateStopping();

                    cleanUpConnectedSocket();
                    cleanUpListeningSocket();

                    if (stoppedByError)
                    {
                        //Notify parent that there was an error
                        onError(ErrorDetail.UNKNOWN);
                    }
                    else
                    {
                        // Notify parent that connection was disconnected
                        onDisconnected();
                    }

                    Log.i(TAG, Thread.currentThread().getName() + " exited.");
                }
            }
        };

        // Start this thread
        mServerThread = new Thread(runnable, "StreamingProxy " + TAG + " serverThread");
        Log.i(TAG, "Thread " + mServerThread.getName() + " starting.");
        mServerThread.start();

        // Block until either there is an error or the socket is bound
        synchronized (mBoundSocketLock)
        {
            try
            {
                while (!mListeningSocketBound && getState() != State.ERROR)
                    mBoundSocketLock.wait();
            }
            catch (InterruptedException e)
            {
                // Ignred
            }
        }
    }


    @Override
    protected void disconnectAndUnbind()
    {
        try
        {
            mServerThread.interrupt();
            mServerThread.join(5000);
        }
        catch (InterruptedException e)
        {
            // Ignored
        }
    }

    public void cleanUpConnectedSocket()
    {
        try
        {
            mHttpRequestInputStream.close();
            mHttpRequestInputStream = null;
        }
        catch (Exception e)
        {
            // Ignored
        }

        try
        {
            mConnectedSocket.close();
            mConnectedSocket = null;
        }
        catch (Exception e)
        {
            // Ignored
        }
    }

    public void cleanUpListeningSocket()
    {
        try
        {
            mListeningSocketBound = false;
            mListeningSocket.close();
            mListeningSocket = null;
        }
        catch (Exception e)
        {
            // Ignored
        }
    }


    @Override
    public URI getUri()
    {
        return mUri;
    }


    @Override
    protected void onProcessMessage(Object userInfo)
    {
        // Process this request
        processHttpRequest((HttpRequest)userInfo);
    }


    /**
     * Bind the socket to the given port, or any available one if port is negative.
     *
     * @param port The port to listen on, -1 for any available port.
     *             When using any available port, make sure to use getUri in order to know the address of the server.
     *             If the port is specified (not -1), and is not available for bind, an exception is thrown.
     *
     * @throws IOException          If impossible to bind to the given (or to any) port.
     * @throws URISyntaxException   If the final Uri is invalid. Very unlikely to happen!
     */
    private void bindSocket(final int port) throws IOException, URISyntaxException
    {
        int curPort = port >= 0 ? port : 1234;
        boolean bound = false;
        while (!bound)
        {
            try
            {
                // Create the listening socket (use a serverSocketChannel to allow thread interrupt, http://stackoverflow.com/questions/1510403/how-to-unblock-a-thread-blocked-on-serversocket-accept)
                ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
                mListeningSocket = serverSocketChannel.socket();

                // Bind the listening socket to the given port
                mListeningSocket.bind(new InetSocketAddress(curPort), 0);
                bound = true;
            }
            catch (BindException e)
            {
                if (port < 0 && curPort < 2048)
                {
                    Log.e(TAG, "Failed to bind to port " + curPort + ", trying next one...");
                    curPort++;
                }
                else
                {
                    throw e;
                }
            }
        }

        // Create the address of this server on LocalHost
        int mPort = curPort;
        mUri = new URI("http://" + LOCALHOST + ":" + mPort);
    }


    /**
     * Reads a request from the buffered reader, line by line until the last CRLF is read.
     * The caller is responsible for providing a preallocated clean HttpRequest instance to store
     * the result.
     *
     * @param br            The Reader to use to read the request lines.
     * @param request   Instance where to store the parsed request. If the request was of unsupported type, its 'method' field is null.
     * @return true if the request was read, false if reading failed.
     */
    boolean receiveRequest(BufferedReader br, HttpRequest request) throws IOException
    {
        String str = br.readLine();

        // Early out if disconnected
        if (str == null)
            return false;

        //Log.i(TAG, "Received " + str);

        // Determine the request type, loop through all enums to find the right one.
        for (HttpMethod.Method curMethod : HttpMethod.Method.values())
        {
            if (str.startsWith(curMethod.toString()))
            {
                request.setMethod(curMethod);

                // Extract uri and version
                String[] tokens = str.split(" ");

                if (tokens.length >= 2)
                {
                    request.setUri(tokens[1]);
                }

                if (tokens.length >= 3)
                {
                    try
                    {
                        request.setVersion(HttpVersion.Version.getEnum(tokens[2]));
                    }
                    catch (IllegalArgumentException e)
                    {
                        // Ignored
                    }
                }

                break;
            }
        }

        // Read line by line until the CRLF signalling the end of request
        boolean receivingRequest = true;
        while (receivingRequest)
        {
            str = br.readLine();

            // Early out if disconnected
            if (str == null)
                return false;

            //Log.i(TAG, "Received " + str);

            if (str.length() == 0) // Empty string means crlf was received. For all supported methods, there is no content, only a header, so CRLF means request end
            {
                receivingRequest = false;
            }
            else
            {
                // Split the line (assumed to be a header line) into field:value pairs.
                String[] splittedHeader = str.split(":");   // Produces: {User-Agent, AndroidMediaPlayer}

                // Trim strings to remove the spaces in 'UserAgent: AndroidMediaPlayer' (second token would be ' AndroidMediaPlayer').
                String fieldName = splittedHeader[0].trim();
                String value = splittedHeader.length > 1 ? splittedHeader[1].trim() : "";

                // Loop through all enums to find the right one.
                HttpHeaderField.Field field = null;
                for (HttpHeaderField.Field curField : HttpHeaderField.Field.values())
                {
                    if (fieldName.equals(curField.toString()))
                    {
                        field = curField;
                        break;
                    }
                }

                // If the field was found, it is a field that we care about, add it to the request.
                if (field != null)
                {
                    request.setHeader(field, value);
                }
            }
        }

        return true;
    }


    /**
     * Called when an HTTP Request is received. It is assumed that the HTTP Request is actually an HTTP request.
     * If is is not a supported HTTP Request, a METHOD_NOT_ALLOWED response is returned to the server.
     */
    private void processHttpRequest(HttpRequest request)
    {
        Log.v(TAG, "Received request");
        Log.v(TAG, request.toString());

        try
        {
            if (request.getMethod() == HttpMethod.Method.HEAD)
            {
                processHeadRequest();
            }
            else if (request.getMethod() == HttpMethod.Method.GET)
            {
                processGetRequest();
            }
            else
            {
                HttpResponse response = new HttpResponse();
                response.setVersion(HttpVersion.Version.HTTP_1_0);
                response.setStatus(HttpResponseStatus.Status.METHOD_NOT_ALLOWED);
                response.setHeader(HttpHeaderField.Field.SERVER, SERVER_NAME);

                sendResponse(response, false);
            }
        }
        catch (Exception e)
        {
            Log.e(TAG, "Exception Caught (process Http request): " + e);
            e.printStackTrace();

            HttpResponse response = new HttpResponse();
            response.setVersion(HttpVersion.Version.HTTP_1_0);
            response.setStatus(HttpResponseStatus.Status.INTERNAL_SERVER_ERROR);
            response.setHeader(HttpHeaderField.Field.SERVER, SERVER_NAME);

            sendResponse(response, false);
        }
    }


    /**
     * <p>Sends the response to the client.
     * Convenience method to send the response and log it to the console at the same time.
     *
     * <p>For some requests, errors can be ignored. For example, the client might
     * have disconnected right after sending the TearDown message so sending the
     * response may fail.
     */
    private void sendResponse(HttpResponse response, boolean ignoreErrors)
    {
        if (response != null)
        {
            try
            {
                Log.v(TAG, "Sending Response");
                Log.v(TAG, response.toString());

                CharBuffer charBuf = CharBuffer.wrap(response.toString());

                mConnectedSocket.getChannel().write(mCharsetEncoder.encode(charBuf));
            }
            catch (IOException e)
            {
                Log.e(TAG, "Exception Caught (sending response)" + (ignoreErrors ? " [ignored]" : "") + ": " + e);
                e.printStackTrace();

                if (!ignoreErrors)
                    onError(ErrorDetail.SEND_RESPONSE);
            }
        }
    }


    /**
     * Responds to an HEAD request by sending the response header but not starting the transfer thread
     */
    void processHeadRequest() throws Exception
    {
        String mimeType = mRawPacketProvider.getMimeType();
        if (mimeType == null)
            throw new Exception("No mime type.");

        // Create the 'Now' timestamp
        String nowStr = mDateFormatter.format(new Date());

        // Send the response
        HttpResponse response = new HttpResponse();
        response.setVersion(HttpVersion.Version.HTTP_1_0);
        response.setStatus(HttpResponseStatus.Status.OK);
        response.setHeader(HttpHeaderField.Field.SERVER, SERVER_NAME);
        response.setHeader(HttpHeaderField.Field.DATE, nowStr);
        response.setHeader(HttpHeaderField.Field.CONTENT_TYPE, mimeType);
        response.setHeader(HttpHeaderField.Field.EXPIRES, "Thu, 01 Dec 2003 16:00:00 GMT");
        response.setHeader(HttpHeaderField.Field.CACHE_CONTROL, "no-cache, must-revalidate");
        response.setHeader(HttpHeaderField.Field.PRAGMA, "no-cache");

        sendResponse(response, false);
    }


    /**
     * Responds to an GET request by starting the thread that send the Audio packets using the desired channel.
     * @throws Exception
     */
    void processGetRequest() throws Exception
    {
        processHeadRequest();

        // Start the playback thread, that will poll the RawPacketsProvider and block if there are none
        mPlaying = true;
        mAudioPacketsTransferThread = new Thread(mAudioPacketsTransferRunnable, "StreamingProxy " + TAG + " transferThread");
        Log.i(TAG, "Thread " + mAudioPacketsTransferThread.getName() + " starting.");
        mAudioPacketsTransferThread.start();
    }


    /**
     * Stops the Thread that reads the requests. Blocks until stop is complete.
     */
    private void stopHttpRequestReceiveThread()
    {
        try
        {
            if (mHttpRequestReceiveThread != null)
            {
                Log.i(TAG, "Interrupting Thread " + mHttpRequestReceiveThread.getName());
                mHttpRequestReceiveThread.interrupt();
                mHttpRequestReceiveThread.join(5000);
                Log.i(TAG, "Interrupted Thread " + mHttpRequestReceiveThread.getName());
                mHttpRequestReceiveThread = null;
            }
        }
        catch (Exception e)
        {
            // Ignored
        }
    }


    /**
     * Stops the Thread that transfers Audio packets. Blocks until stop is complete.
     */
    private void stopAudioPacketsTransferThread()
    {
        try
        {
            if (mAudioPacketsTransferThread != null)
            {
                Log.i(TAG, "Interrupting Thread " + mAudioPacketsTransferThread.getName());
                mPlaying = false;
                mAudioPacketsTransferThread.interrupt();
                mAudioPacketsTransferThread.join(5000);
                Log.i(TAG, "Interrupted Thread " + mAudioPacketsTransferThread.getName());
                mAudioPacketsTransferThread = null;
            }
        }
        catch (Exception e)
        {
            // Ignored
        }
    }


    /**
     * Sets the HTTP Server to streams the data from the given provider.
     * Ignored if the server is already listening or connected.
     * Use {@code blockUntilReady} or wait for the onServerReady notification after the provider changes and before starting to
     * listen to avoid delays in responses.
     *
     * @param rawPacketProvider Source of the packets to transmit.
     */
    @Override
    public void setDataProvider(DataProvider rawPacketProvider)
    {
        // Early-out when invalid provider
        if (!(rawPacketProvider instanceof RawPacketProvider))
        {
            mRawPacketProvider = null;
            return;
        }

        // Early-out when incorrect state
        if (getState() != State.NOTREADY && getState() != State.READY && getState() != State.ERROR)
        {
            return;
        }

        mRawPacketProvider = (RawPacketProvider)rawPacketProvider;
        mRawPacketProvider.setStateChangedListener(mDataProviderStateChangedListener);

        // If the SDP Line can already be retrieved from this provider, we are ready to listen without blocking.
        if (mRawPacketProvider.isAudioConfigReady())
        {
            setStateReady();
        }
        else
        {
            setStateNotReady();
        }
    }


    private final Runnable mHttpReceiveRequestRunnable = new Runnable()
    {
        @Override
        public void run()
        {
            // Bug that probably occurred when launching and closing very quickly
            if (mHttpRequestInputStream == null)
            {
                return;
            }

            InputStreamReader isr   = new InputStreamReader(mHttpRequestInputStream);
            BufferedReader    br    = new BufferedReader(isr, READ_BUFFER_SIZE);

            try
            {
                while(!Thread.interrupted() && getState() == State.CONNECTED && mConnectedSocket.isConnected())
                {
                    // Read the request (stop the server if the socket failed to read)
                    HttpRequest request = new HttpRequest();
                    if (!receiveRequest(br, request) && getState() == State.CONNECTED)
                    {
                        Log.i(TAG, "Other side interrupted connection.");
                        break;
                    }


                    // Process this request, notify the parent, which will synchronise and in time, call the onProcessMessage method.
                    onMessageReceived(request);
                }
            }
            catch (Exception e)
            {
                Log.e(TAG, "Exception Caught: " + e);
                e.printStackTrace();

                setStateError(ErrorDetail.RECEIVE_REQUEST);
            }
            finally
            {
                try
                {
                    br.close();
                }
                catch (IOException e)
                {
                    // Ignored
                }
            }

            Log.i(TAG, Thread.currentThread().getName() + " exiting.");
        }
    };


    /**
     * Thread that runs to take packets from the data provider and send them to the client as soon as they are ready.
     */
    private final Runnable mAudioPacketsTransferRunnable = new Runnable()
    {
        @Override
        public void run()
        {
            streamAudioPackets();

            Log.i(TAG, Thread.currentThread().getName() + " exiting.");
        }
    };


    /**
     * Streams the Audio Packets using the current TCP connection.
     * Loops until the thread is interrupted or the client requested a stop.
     * Blocks the thread until a new packet is ready, then send it.
     */
    private void streamAudioPackets()
    {
        // Loop until connection is closed and send all packets as soon as they are available
        while (!Thread.interrupted() && mPlaying)
        {
            try
            {
                Packet packetData = mRawPacketProvider.getPacket();

                if (packetData != null)
                {
                    short packetDataLength = (short) (packetData.getLength());
                    byte[] packetBytes = new byte[packetDataLength];
                    System.arraycopy(packetData.getData(), 0, packetBytes, 0, packetDataLength);

                    ByteBuffer buf = ByteBuffer.wrap(packetBytes);
                    mConnectedSocket.getChannel().write(buf);

                    mRawPacketProvider.addFreePacketToPool(packetData);
                }
            }
            catch (Exception e)
            {
                // Packet dropped, ignore error, proceed to the next packet.
                Log.d(TAG, "Packet failed to send - " + e);
            }
        }
    }

    final RawPacketProvider.StateChangedListener mDataProviderStateChangedListener = new RawPacketProvider.StateChangedListener()
    {
        @Override
        public void onProviderAudioConfigReady()
        {
            if (getState() == State.NOTREADY || getState() == State.ERROR)
                setStateReady();
        }
    };
}
