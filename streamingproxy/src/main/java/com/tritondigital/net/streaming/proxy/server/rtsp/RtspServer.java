package com.tritondigital.net.streaming.proxy.server.rtsp;

import com.tritondigital.net.streaming.proxy.dataprovider.DataProvider;
import com.tritondigital.net.streaming.proxy.dataprovider.Packet;
import com.tritondigital.net.streaming.proxy.dataprovider.rtp.RtpPacketProvider;
import com.tritondigital.net.streaming.proxy.server.Server;
import com.tritondigital.net.streaming.proxy.server.Server.StateChangedListener.ErrorDetail;
import com.tritondigital.net.streaming.proxy.utils.Log;
import com.tritondigital.net.streaming.proxy.utils.QueueInputStream;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.BindException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * <p>Implementation of a Server that responds to Rtsp requests.
 * The Server uses a socket to listen and negotiate the connection. It then streams using the already established TCP connection, or
 * by sending UDP datagrams.
 *
 * <p>The RTSP contains the minimum to simply start streaming, based on the RFC 2326 (http://www.ietf.org/rfc/rfc2326.txt). It responds
 * to OPTIONS, DESCRIBE with the minimal SDP information, SETUP and PLAY. RTCP messages are ignored when received, and never generated.
 * They are not needed for the type of work that the proxy does. If needed, they would be added later on.
 *
 * <p>It supports streaming RTP Packets through UDP or through TCP using interleaved mode. It is also possible to force the use of TCP
 * by returning an UNSUPPORTED_TRANSPORT message to a SETUP request with UDP ports. This works only if the client supports it. A client
 * typically tries to SETUP using UDP, then TCP if UDP is not supported.
 *
 * <p>In the case of this server, used in a proxy, there are no chances of packets loss or misorder, so UDP is more efficient. However, for debugging
 * purposes, it might be easier to monitor all the data (RTSP negociation + RTP Packets Stream) in a single connection. For this reason,
 * it is supported to force RTP over TCP. This should typically used only for debugging.
 *
 * <p>The server listens and accepts a connection, the uses blocking sockets to read the requests. To be able to use a BufferedReader, the
 * data that is read from the non-blocking socket is pushed in the QueueInputStream, on which the BufferedReader reads. The read blocks
 * until new data is read from the socket and pushed in the QueueInputStream. This adds a new thread, but it simplifies the code and allows
 * interrupting the reads.
 *
 * <p>Streaming works by having a background thread that continuously loops and get a packet from the packet provider and send this packet
 * using the appropriate transport. If there are no packet ready, the thread blocks due to the provider blocking until a packet is available.
 *
 * <p>Therefore, this server uses 3 threads. One for the server (accept the connection, read from the non-blocking socket and push the new data
 * in the QueueInputStream), one for the Rtsp Request Reception (reads from the QueueInputStream, process the received request and blocks when
 * no more data to read) and one for the Rtp packets transfer (blocks when no packets available).
 */
public class RtspServer extends Server
{
    public  static final String SERVER_NAME = "Triton Digital RTSP Proxy";
    private static final String DATE_FORMAT = "EEE, d MMM yyyy HH:mm:ss z";
    private static final int    SESSIONID   = 666; // Fixed session Id, there is only one session at at time.
    private static final String STREAMID    = "streamId=0"; // Fixed stream Id, there is only one stream at at time.

    private static final int    READ_BUFFER_SIZE = 1024;

    /** All options supported by this server */
    final String OPTIONS = RtspMethod.Method.DESCRIBE + ", " + RtspMethod.Method.SETUP + ", "
                         + RtspMethod.Method.TEARDOWN + ", " + RtspMethod.Method.PLAY  + ", "
                         + RtspMethod.Method.OPTIONS  + ", " + RtspMethod.Method.PAUSE;


    private final SimpleDateFormat  mDateFormatter = new SimpleDateFormat(DATE_FORMAT, Locale.ENGLISH);
    private RtpPacketProvider       mRtpPacketProvider;

    private InetSocketAddress       mRemoteAddress;     // Used for RTP over UDP mode, null when RTP over TCP is used.
    private SocketChannel           mConnectedSocket;   // Used for RTP over TCP (interleaved) mode and for the RTSP Connection.
                                                        // Connected, does the RTSP Request and response.
    private ServerSocketChannel     mListeningSocket;   // Listening to accept the connection.

    private QueueInputStream        mRtspRequestInputStream; // Used to read from the connected socket. Blocks until the non-blocking socket has available data.

    private Thread                  mServerThread;
    private Thread                  mRtpTransferThread;
    private Thread                  mRtspRequestReceiveThread;

    private URI                     mUri;

    private volatile boolean        mPlaying = false;
    private volatile boolean        mPaused = false;

    private volatile boolean        mListeningSocketBound = false;
    private final Object            mBoundSocketLock = new Object();
    private final Object            mConnectedSocketLock = new Object();

    final CharsetEncoder                  mCharsetEncoder = Charset.forName("US-ASCII").newEncoder();

    private int                     mClientPortRtp  = -1;


    /**
     * Constructs a RTSP Server that streams the data from the given provider.
     * The new instance needs to have a source set ({@code setDataProvider}) before it starts listening.
     * Use {@code blockUntilReady} before starting to listen to avoid delays in responses.
     */
    public RtspServer()
    {
    }


    @Override
    public void bindAndListen(final int port)
    {
        Log.i(TAG, "Listening on" + (port == -1 ? " any port" : "port " + port));

        if (mRtpPacketProvider == null)
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


                    // Accept a connection from the client
                    mConnectedSocket = mListeningSocket.accept();
                    mConnectedSocket.configureBlocking(true);

                    // Notify parent that connection was established
                    onConnected();

                    // Create input stream
                    mRtspRequestInputStream = new QueueInputStream(READ_BUFFER_SIZE);

                    // Start the thread that receives Rtsp Requests
                    mRtspRequestReceiveThread = new Thread(mRtspReceiveRequestRunnable, "StreamingProxy " + TAG + " requestThread");
                    Log.i(TAG, "Thread " + mRtspRequestReceiveThread.getName() + " starting.");
                    mRtspRequestReceiveThread.start();

                    // Read until server is closed
                    ByteBuffer buf = ByteBuffer.allocateDirect(READ_BUFFER_SIZE);
                    byte[] bytes = new byte[READ_BUFFER_SIZE];
                    while(!Thread.interrupted() && getState() == State.CONNECTED && mConnectedSocket.isConnected())
                    {
                        buf.clear();

                        // Blocking read from the socket
                        int readLength = mConnectedSocket.read(buf);
                        if (readLength < 0)
                            throw new Exception("Failed to read from socket, other side terminated connection unexpectedly.");

                        // Put the data so it can be read by the stream reader running in another thread
                        buf.flip();
                        buf.get(bytes, 0, readLength);
                        mRtspRequestInputStream.put(bytes, readLength);

                        // Leave the chance to other process to run.
                        Thread.sleep(mPlaying ? 500 : 1);
                    }

                    // Stop the transfer thread, block until stopped
                    stopRtpTransferThread();

                    // Stop requests reception thread, block until stopped
                    stopRtspRequestReceiveThread();

                    // Notify parent that connection was disconnected
                    onDisconnected();
                }
                catch (ClosedByInterruptException | InterruptedException e)
                {
                    // Reading was interrupted by a Thread.interrupt, which means that the server is stopping.
                    stopRtpTransferThread();
                    stopRtspRequestReceiveThread();

                    // Notify parent that connection was disconnected
                    onDisconnected();
                } catch(Exception e)
                {
                    Log.e(TAG, "Exception Caught (server thread): " + e);
                    e.printStackTrace();

                    stopRtpTransferThread();
                    stopRtspRequestReceiveThread();

                    //Notify parent that there was an error
                    onError(ErrorDetail.UNKNOWN);
                }
                finally
                {
                    try
                    {
                        mRtspRequestInputStream.close();
                        mRtspRequestInputStream = null;
                    }
                    catch (Exception e)
                    {
                        // Ignored
                    }

                    try
                    {
                        synchronized(mConnectedSocketLock)
                        {
                            if (mConnectedSocket != null)
                            {
                                mConnectedSocket.socket().shutdownInput();
                                mConnectedSocket.socket().shutdownOutput();
                                mConnectedSocket.close();
                                mConnectedSocket = null;
                            }
                        }
                    }
                    catch (Exception e)
                    {
                        // Ignored
                    }

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
                // Ignored
            }
        }
    }


    @Override
    protected void disconnectAndUnbind()
    {
        try
        {
            synchronized(mConnectedSocketLock)
            {
                if (mConnectedSocket != null)
                {
                    mConnectedSocket.socket().shutdownInput();
                    mConnectedSocket.socket().shutdownOutput();
                    mConnectedSocket.close();
                }
            }
            mServerThread.interrupt();
            mServerThread.join(5000);
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
        processRtspRequest((RtspRequest)userInfo);
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
                mListeningSocket = ServerSocketChannel.open();

                // Bind the listening socket to the given port
                mListeningSocket.socket().bind(new InetSocketAddress(curPort), 1);
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
        mUri = new URI("rtsp://" + LOCALHOST + ":" + curPort);
    }


    /**
     * Reads a request from the buffered reader, line by line until the last CRLF is read.
     * The caller is responsible for providing a preallocated clean RtspRequest instance to store
     * the result.
     *
     * @param br            The Reader to use to read the request lines.
     * @param request   Instance where to store the parsed request. If the request was of unsupported type, its 'method' field is null.
     * @return true if the request was read, false if reading failed.
     */
    boolean receiveRequest(BufferedReader br, RtspRequest request) throws IOException
    {
        // When using TCP, the RTCP messages are sent through the same TCP channel socket than the request.
        // Read them before the readline as those do not contain an end of line character.
        if (isUsingRtpOverTcp())
        {
            readInterleavedRTCPMessages(br);
        }

        String str = br.readLine();

        // Early out if disconnected
        if (str == null)
            return false;

        //Log.i(TAG, "Received " + str);

        // Determine the request type, loop through all enums to find the right one.
        for (RtspMethod.Method curMethod : RtspMethod.Method.values())
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
                        request.setVersion(RtspVersion.Version.getEnum(tokens[2]));
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
                String[] splittedHeader = str.split(":");   // Produces: {CSeq, 1}

                // Trim strings to remove the spaces in 'CSeq: 1' (second token would be ' 1').
                String fieldName = splittedHeader[0].trim();
                String value = splittedHeader.length > 1 ? splittedHeader[1].trim() : "";

                // Loop through all enums to find the right one.
                RtspHeaderField.Field field = null;
                for (RtspHeaderField.Field curField : RtspHeaderField.Field.values())
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
     * Called when an HTTP Request is received. It is assumed that the HTTP Request is actually an RTSP request.
     * If is is not a supported RTSP Request, a METHOD_NOT_ALLOWED response is returned to the server.
     */
    private void processRtspRequest(RtspRequest request)
    {
        Log.v(TAG, "Received request");
        Log.v(TAG, request.toString());

        try
        {
            if (request.getMethod() == RtspMethod.Method.OPTIONS)
            {
                processOptionsRequest(request);
            }
            else if (request.getMethod() == RtspMethod.Method.DESCRIBE)
            {
                processDescribeRequest(request);
            }
            else if (request.getMethod() == RtspMethod.Method.SETUP)
            {
                processSetupRequest(request);
            }
            else if (request.getMethod() == RtspMethod.Method.PLAY)
            {
                processPlayRequest(request);
            }
            else if (request.getMethod() == RtspMethod.Method.PAUSE)
            {
                processPauseRequest(request);
            }
            else if (request.getMethod() == RtspMethod.Method.TEARDOWN)
            {
                processTearDownRequest(request);
            }
            else
            {
                RtspResponse response = new RtspResponse();
                response.setVersion(RtspVersion.Version.RTSP_1_0);
                response.setStatus(RtspResponseStatus.Status.METHOD_NOT_ALLOWED);
                response.setHeader(RtspHeaderField.Field.SERVER, SERVER_NAME);
                if (request.containsHeader(RtspHeaderField.Field.CSEQ))
                {
                    response.setHeader(RtspHeaderField.Field.CSEQ, request.getHeader(RtspHeaderField.Field.CSEQ));
                }
                response.setHeader(RtspHeaderField.Field.ALLOW, OPTIONS);

                sendResponse(response, false);
            }
        }
        catch (Exception e)
        {
            Log.e(TAG, "Exception Caught (process Rtsp request): " + e);
            e.printStackTrace();

            RtspResponse response = new RtspResponse();
            response.setVersion(RtspVersion.Version.RTSP_1_0);
            response.setStatus(RtspResponseStatus.Status.INTERNAL_SERVER_ERROR);
            response.setHeader(RtspHeaderField.Field.SERVER, SERVER_NAME);
            if (request.containsHeader(RtspHeaderField.Field.CSEQ))
            {
                response.setHeader(RtspHeaderField.Field.CSEQ, request.getHeader(RtspHeaderField.Field.CSEQ));
            }

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
    private void sendResponse(RtspResponse response, boolean ignoreErrors)
    {
        if (response != null)
        {
            try
            {
                Log.v(TAG, "Sending Response");
                Log.v(TAG, response.toString());

                CharBuffer charBuf = CharBuffer.wrap(response.toString());

                mConnectedSocket.write(mCharsetEncoder.encode(charBuf));
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
     * <p>Extracts the RTP and RTCP ports used for the RTP Streaming.
     * Stores the ports in the internal member variables and returns the client ine for convenience
     * (because this method is typically called in response to a SETUP request, which must return the same client port
     * line).
     *
     * <p>This may be used to extract URP ports or the Channels in interleaved mode.
     *
     *  @param transportRequestLine Line of the SETUP request that contains the transport information
     *                              (Transport=RTP/AVP;unicast;client_port=51502-51503)
     *  @param portTag              Tag that identifies the port in the transport line
     *                              ('client_port' for UDP, 'interleaved' for TCP-interleaved)
     *
     *  @return The client port string or interleaved string to be appended to the SETUP response
     *          ('client_port=1234-1235' for UDP, 'interleaved=0-1' for TCP-interleaved)
     */
    private String extractClientPorts(String transportRequestLine, String portTag)
    {
        // Extract channels for RTP and RTCP
        String[] transportParameters = transportRequestLine.split(";");
        for (String curTransportParameter : transportParameters)
        {
            if (curTransportParameter.contains(portTag))
            {
                String[] values = curTransportParameter.split("="); // produces { interleaved, 0-1 }
                String clientPortStr = values[1];                   // take 0-1
                values = clientPortStr.split("-");                  // produces { 0, 1 }

                mClientPortRtp = values.length > 0 ? Integer.parseInt(values[0])  : -1;

                return clientPortStr;
            }
        }

        return null;
    }


    /**
     * <p>Validates that the given request contains the correct session (which is constant for this server).
     * This is simply used to ensure that the request from the server seems valid.
     *
     * <p>If the session is invalid, an error response is sent to the client and the {@code false} is returned.
     */
    private boolean validateSession(RtspRequest request)
    {
        // Validate that session is present in the request
        String sessionIdStr = request.getHeader(RtspHeaderField.Field.SESSION);
        if (sessionIdStr == null)
        {
            RtspResponse response = new RtspResponse();
            response.setVersion(RtspVersion.Version.RTSP_1_0);
            response.setStatus(RtspResponseStatus.Status.BAD_REQUEST);
            response.setHeader(RtspHeaderField.Field.CSEQ, request.getHeader(RtspHeaderField.Field.CSEQ));
            response.setHeader(RtspHeaderField.Field.SERVER, SERVER_NAME);

            sendResponse(response, false);
            return false;
        }

        // Validate that session in the request is the good one
        int sessionId = Integer.parseInt(sessionIdStr);
        if (sessionId != SESSIONID)
        {
            RtspResponse response = new RtspResponse();
            response.setVersion(RtspVersion.Version.RTSP_1_0);
            response.setStatus(RtspResponseStatus.Status.SESSION_NOT_FOUND);
            response.setHeader(RtspHeaderField.Field.CSEQ, request.getHeader(RtspHeaderField.Field.CSEQ));
            response.setHeader(RtspHeaderField.Field.SERVER, SERVER_NAME);

            sendResponse(response, false);
            return false;
        }

        return true;
    }


    /**
     * Responds to an OPTION request by sending all supported options.
     */
    void processOptionsRequest(RtspRequest request)
    {
        // Send the response
        RtspResponse response = new RtspResponse();
        response.setVersion(RtspVersion.Version.RTSP_1_0);
        response.setStatus(RtspResponseStatus.Status.OK);
        response.setHeader(RtspHeaderField.Field.SERVER, SERVER_NAME);
        response.setHeader(RtspHeaderField.Field.CSEQ, request.getHeader(RtspHeaderField.Field.CSEQ));
        response.setHeader(RtspHeaderField.Field.PUBLIC, OPTIONS);

        sendResponse(response, false);
    }


    /**
     * Responds to an DESCRIBE request by sending the minimal required information about the server / media, taking SDP from the data provider.
     */
    void processDescribeRequest(RtspRequest request)
    {
        // Create the 'Now' timestamp
        String nowStr = mDateFormatter.format(new Date());

        // Create the absolute a:control URL. Cannot use relative URL because it causes some devices to fail.
        String absControlUrl = request.getUri();
        if (!absControlUrl.endsWith("/"))
        {
            absControlUrl = absControlUrl + "/";
        }

        String sdp = mRtpPacketProvider.getSdpConfig(absControlUrl + STREAMID);

        // Send the response
        RtspResponse response = new RtspResponse();
        response.setVersion(RtspVersion.Version.RTSP_1_0);
        response.setStatus(RtspResponseStatus.Status.OK);
        response.setHeader(RtspHeaderField.Field.SERVER, SERVER_NAME);
        response.setHeader(RtspHeaderField.Field.CSEQ, request.getHeader(RtspHeaderField.Field.CSEQ));
        response.setHeader(RtspHeaderField.Field.DATE, nowStr);
        response.setHeader(RtspHeaderField.Field.CONTENT_TYPE, "application/sdp");
        response.setHeader(RtspHeaderField.Field.CONTENT_LENGTH, sdp.length());
        response.setHeader(RtspHeaderField.Field.CONTENT_BASE, request.getUri());
        response.setHeader(RtspHeaderField.Field.LAST_MODIFIED, nowStr); // Who cares... Could be taken from the Stream Decoder (e.g. FLV Decoder) in the Proxy if really needed.
        response.setContent(sdp);

        sendResponse(response, false);
    }


    /**
     * Responds to an SETUP request by storing the desired ports / interleaved channel.
     * This method assumes that there will be no SETUP command received when the stream is playing. Changing the transport during after Play
     * is not supported.
     */
    void processSetupRequest(RtspRequest request)
    {
        // Get the client ports
        String clientPortStr;

        // Determine the transport mode
        String transportRequest = request.getHeader(RtspHeaderField.Field.TRANSPORT);
        if (transportRequest.contains("interleaved"))
        {
            // Using TCP
            mRemoteAddress = null;

            // Extract ports for RTP and RTCP
            clientPortStr = extractClientPorts(transportRequest, "interleaved");
        }
        else
        {
            // Extract ports for RTP and RTCP
            clientPortStr = extractClientPorts(transportRequest, "client_port");

            // Create the Client Address to which the UDP datagram containing the RTP Packets should be sent
            String clientIp = mConnectedSocket.socket().getInetAddress().getHostAddress();
            mRemoteAddress = new InetSocketAddress(clientIp, mClientPortRtp);
        }

        // Create the 'Now' timestamp
        String nowStr = mDateFormatter.format(new Date());

        // Transport confirmation line
        String transportResponse;
        if (isUsingRtpOverTcp())
        {
            transportResponse = "RTP/AVP/TCP;unicast" + ";";
            transportResponse = transportResponse + "interleaved=" + clientPortStr;
        }
        else
        {
            transportResponse = "RTP/AVP/UDP;unicast" + ";";
            transportResponse = transportResponse + "client_port=" + clientPortStr + ";server_port=" + 6970 + "-" + 6971;
        }

        // Send the response
        RtspResponse response = new RtspResponse();
        response.setVersion(RtspVersion.Version.RTSP_1_0);
        response.setStatus(RtspResponseStatus.Status.OK);
        response.setHeader(RtspHeaderField.Field.SERVER, SERVER_NAME);
        response.setHeader(RtspHeaderField.Field.CSEQ, request.getHeader(RtspHeaderField.Field.CSEQ));
        response.setHeader(RtspHeaderField.Field.SESSION, SESSIONID);
        response.setHeader(RtspHeaderField.Field.TRANSPORT, transportResponse);
        response.setHeader(RtspHeaderField.Field.DATE, nowStr);

        sendResponse(response, false);
    }


    /**
     * Responds to an PLAY request by starting the thread that send the RTP packets using the desired channel.
     */
    void processPlayRequest(RtspRequest request)
    {
        if (!validateSession(request))
            return;

        String uri = request.getUri();
        if (!uri.endsWith("/"))
        {
            uri = uri + "/";
        }

        // Create the range string
        String rangeStr = request.getHeader(RtspHeaderField.Field.RANGE);
        if (rangeStr == null || rangeStr.length() == 0)
        {
            rangeStr = "npt=0-"; // No end range (rfc2326: A PLAY request without a Range header is legal. It starts playing a stream from the beginning unless the stream has been paused.)
        }

        // Create RTP-info string
        String rtpInfo = "url=" + uri + STREAMID + ";seq=" + mRtpPacketProvider.getFirstPacketSequenceNumber() + ";rtptime=" + mRtpPacketProvider.getFirstPacketTimestamp();

        // Send the response
        RtspResponse response = new RtspResponse();
        response.setVersion(RtspVersion.Version.RTSP_1_0);
        response.setStatus(RtspResponseStatus.Status.OK);
        response.setHeader(RtspHeaderField.Field.SERVER, SERVER_NAME);
        response.setHeader(RtspHeaderField.Field.CSEQ, request.getHeader(RtspHeaderField.Field.CSEQ));
        response.setHeader(RtspHeaderField.Field.SESSION, SESSIONID);
        response.setHeader(RtspHeaderField.Field.RTP_INFO, rtpInfo);
        response.setHeader(RtspHeaderField.Field.RANGE, rangeStr);

        sendResponse(response, false);

        // Start the playback thread, that will poll the RTPPacketsProvider and block if there are none
        if (!mPlaying)
        {
            mPlaying = true;
            mRtpTransferThread = new Thread(mRtpTransferRunnable, "StreamingProxy " + TAG + " transferThread");
            Log.i(TAG, "Thread " + mRtpTransferThread.getName() + " starting.");
            mRtpTransferThread.start();
        }

        mPaused = false;
    }


    /**
     * Responds to an PAUSE request by starting the thread that send the RTP packets using the desired channel.
     */
    void processPauseRequest(RtspRequest request)
    {
        if (!validateSession(request))
            return;

        // Send the response
        RtspResponse response = new RtspResponse();
        response.setVersion(RtspVersion.Version.RTSP_1_0);
        response.setStatus(RtspResponseStatus.Status.OK);
        response.setHeader(RtspHeaderField.Field.SERVER, SERVER_NAME);
        response.setHeader(RtspHeaderField.Field.CSEQ, request.getHeader(RtspHeaderField.Field.CSEQ));
        response.setHeader(RtspHeaderField.Field.SESSION, SESSIONID);

        sendResponse(response, false);

        mPaused = true;
    }


    /**
     * Responds to an TEARDOWN request by stopping the thread that send the RTP packets and disconnecting.
     */
    void processTearDownRequest(RtspRequest request)
    {
        // Send the response
        RtspResponse response = new RtspResponse();
        response.setVersion(RtspVersion.Version.RTSP_1_0);
        response.setStatus(RtspResponseStatus.Status.OK);
        response.setHeader(RtspHeaderField.Field.SERVER, SERVER_NAME);
        response.setHeader(RtspHeaderField.Field.CSEQ, request.getHeader(RtspHeaderField.Field.CSEQ));
        response.setHeader(RtspHeaderField.Field.SESSION, SESSIONID);

        sendResponse(response, true);

        // Stop the playback thread
        stop();
    }


    /**
     * Stops the Thread that reads the requests. Blocks until stop is complete.
     */
    private void stopRtspRequestReceiveThread()
    {
        try
        {
            if (mRtspRequestReceiveThread != null)
            {
                Log.i(TAG, "Interrupting Thread " + mRtspRequestReceiveThread.getName());
                mRtspRequestReceiveThread.interrupt();
                mRtspRequestReceiveThread.join(5000);
                Log.i(TAG, "Interrupted Thread " + mRtspRequestReceiveThread.getName());
                mRtspRequestReceiveThread = null;
            }
        }
        catch (Exception e) {
            // Ignored
        }
    }


    /**
     * Stops the Thread that transfers Rtp packets. Blocks until stop is complete.
     */
    private void stopRtpTransferThread()
    {
        try
        {
            if (mRtpTransferThread != null)
            {
                Log.i(TAG, "Interrupting Thread " + mRtpTransferThread.getName());
                mPlaying = false;
                mRtpTransferThread.interrupt();
                mRtpTransferThread.join(5000);
                Log.i(TAG, "Interrupted Thread " + mRtpTransferThread.getName());
                mRtpTransferThread = null;
            }
        }
        catch (Exception e) {
            // Ignored
        }
    }

    /**
     * Sets the RTSP Server to streams the data from the given provider.
     * Ignored if the server is already listening or connected.
     * Use {@code blockUntilReady} or wait for the onServerReady notification after the provider changes and before starting to
     * listen to avoid delays in responses.
     *
     * @param rtpPacketProvider Source of the packets to transmit.
     */
    @Override
    public void setDataProvider(DataProvider rtpPacketProvider)
    {
        // Early-out when invalid provider
        if (!(rtpPacketProvider instanceof RtpPacketProvider))
        {
            mRtpPacketProvider = null;
            return;
        }

        // Early-out when incorrect state
        if (getState() != State.NOTREADY && getState() != State.READY && getState() != State.ERROR)
        {
            return;
        }

        mRtpPacketProvider = (RtpPacketProvider)rtpPacketProvider;
        mRtpPacketProvider.setStateChangedListener(mDataProviderStateChangedListener);

        // If the SDP Line can already be retrieved from this provider, we are ready to listen without blocking.
        if (mRtpPacketProvider.isSdpConfigReady())
        {
            setStateReady();
        }
        else
        {
            setStateNotReady();
        }
    }


    /**
     * Tells if the RTP packets need to be sent over TCP.
     * Note: This also returns true if SETUP has not been called yet. For now, it does not matter that it is impossible to differentiate a transfer
     *       using UDP than a transfer that was not setupped yet, as the method is only intended to be called after the stream has started (SETUP is made).
     */
    private boolean isUsingRtpOverTcp()
    {
        return mRemoteAddress == null;
    }


    private final Runnable mRtspReceiveRequestRunnable = new Runnable()
    {
        @Override
        public void run()
        {
            if (mRtspRequestInputStream == null)
            {
                Log.w(TAG, Thread.currentThread().getName() + "Null mRtspRequestInputStream");
                return;
            }

            InputStreamReader isr = new InputStreamReader(mRtspRequestInputStream);
            BufferedReader    br  = new BufferedReader(isr, READ_BUFFER_SIZE);

            try
            {
                while (!Thread.interrupted() && (getState() == State.CONNECTED) && mConnectedSocket.isConnected())
                {
                    // Read the request (stop the server if the socket failed to read)
                    RtspRequest request = new RtspRequest();
                    if (!receiveRequest(br, request) && getState() == State.CONNECTED)
                            throw new Exception("Failed to read from buffer.");

                    // Process this request, notify the parent, which will synchronise and in time, call the onProcessMessage method.
                    onMessageReceived(request);
                }
            }
            catch (InterruptedException e)
            {
                // Reading was interrupted by a Thread.interrupt, which means that the server is stopping. Nothing special to do, just
                // let the execution flow complete and close all resources.
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
                catch (IOException e) {
                    // Ignore
                }
            }

            Log.i(TAG, Thread.currentThread().getName() + " exiting.");
        }
    };


    /**
     * Thread that runs to take packets from the data provider and send them to the client as soon as they are ready.
     */
    private final Runnable mRtpTransferRunnable = new Runnable()
    {
        @Override
        public void run()
        {
            if (isUsingRtpOverTcp())
                streamRtpOverTcp();
            else
                streamRtpOverUdp();

            Log.i(TAG, Thread.currentThread().getName() + " exiting.");
        }
    };


    /**
     * Streams the Rtp packets using the UDP datagrams.
     * Loops until the thread is interrupted or the client requested a stop.
     * Blocks the thread until a new packet is ready, then send it.
     */
    private void streamRtpOverUdp()
    {
        DatagramSocket udpSocket = null;
        DatagramPacket udpPacket = null;

        // Prepare transport
        try
        {
            udpSocket = new DatagramSocket();
            udpPacket = new DatagramPacket(new byte[1], 1, mRemoteAddress.getAddress(), mRemoteAddress.getPort());
        }
        catch (Exception e)
        {
            onError(ErrorDetail.OPEN_STREAM_SOCKET);
        }

        // Loop until connection is closed and send all packets as soon as they are available
        while (!Thread.interrupted() && mPlaying)
        {
            try
            {
                if (mPaused)
                {
                    Thread.sleep(500);
                    continue;
                }

                Packet packetData = mRtpPacketProvider.getPacket();

                if (packetData != null)
                {
                    if (udpPacket != null) {
                        udpPacket.setData(packetData.getData(), 0, packetData.getLength());
                    }
                    if (udpSocket != null) {
                        udpSocket.send(udpPacket);
                    }

                    mRtpPacketProvider.addFreePacketToPool(packetData);
                }
            }
            catch (Exception e)
            {
                // Packet dropped, ignore error, proceed to the next packet.
                Log.d(TAG, "Packet failed to send - " + e);
            }
        }
    }


    /**
     * <p>Read the RTCP interleaved RTCP header ($XSS where X is the channel, SS is the size of the message).
     * If there are multiple batched RTCP messages, all are read.
     * Since there is currently no need for RTCP, those messages are ignored.
     *
     * <p>This method returns when a non-rtcp message is on the reader.
     *
     * @throws IOException
     */
    @SuppressWarnings({"unused", "UnusedAssignment"})
    private void readInterleavedRTCPMessages(BufferedReader br) throws IOException
    {
        char firstChar = 0;

        do
        {
            if (firstChar == '$')
            {
                br.read();                                                 // Read the the interleaved sign ($)
                int channel = br.read();                                   // Channel should match the mClientPortRtcp variable
                short messageSize = (short) (br.read()  << 8 | br.read()); // Message size excludes the interleave header (4 bytes)

                // Ignore the RTCP message
                br.skip(messageSize);
            }

            // Peek first character of next message
            br.mark(2);
            firstChar = (char) br.read();
            br.reset();
        }
        while (firstChar == '$');
    }


    /**
     * Streams the Rtp packets using the TCP connection.
     * Loops until the thread is interrupted or the client requested a stop.
     * Blocks the thread until a new packet is ready, then send it.
     */
    private void streamRtpOverTcp()
    {
        // Loop until connection is closed and send all packets as soon as they are available
        while (!Thread.interrupted() && mPlaying)
        {
            try
            {
                Packet packetData = mRtpPacketProvider.getPacket();

                if (packetData != null)
                {
                    short rtpPacketDataLength = (short) (packetData.getLength());
                    byte[] interleavedPacketData = new byte[rtpPacketDataLength + 4];
                    interleavedPacketData[0]= '$';
                    interleavedPacketData[1] = (byte)mClientPortRtp;
                    interleavedPacketData[2] = (byte)(rtpPacketDataLength >> 8);
                    interleavedPacketData[3] = (byte)rtpPacketDataLength;
                    System.arraycopy(packetData.getData(), 0, interleavedPacketData, 4, rtpPacketDataLength);


                    ByteBuffer buf = ByteBuffer.wrap(interleavedPacketData);
                    mConnectedSocket.write(buf);

                    mRtpPacketProvider.addFreePacketToPool(packetData);
                }
            }
            catch (Exception e)
            {
                // Packet dropped, ignore error, proceed to the next packet.
                Log.d(TAG, "Packet failed to send - " + e);
            }
        }
    }


    final RtpPacketProvider.StateChangedListener mDataProviderStateChangedListener = new RtpPacketProvider.StateChangedListener()
    {
        @Override
        public void onProviderSdpConfigReady()
        {
            if (getState() == State.NOTREADY || getState() == State.ERROR)
                setStateReady();
        }

        @Override
        public void onProviderError(ErrorDetail errorDetail)
        {
            switch (errorDetail)
            {
                case WRONG_MEDIA_TYPE:
                    onError(Server.StateChangedListener.ErrorDetail.WRONG_MEDIA_TYPE);
                    break;

                default:
                    onError(Server.StateChangedListener.ErrorDetail.CREATE_PACKET);
            }
        }
    };
}
