package com.tritondigital.net.streaming.proxy.client.http;

import com.tritondigital.net.streaming.proxy.client.Client;
import com.tritondigital.net.streaming.proxy.client.Client.StateChangedListener.ErrorDetail;
import com.tritondigital.net.streaming.proxy.utils.Log;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.SocketException;
import java.net.URL;
import java.util.List;
import java.util.Map;

/**
 * Implementation of a Client that connects to a stream using an HTTP GET that typically never closes.
 */
public class HttpClient extends Client {
    private static final int HTTP_READ_BUFFER_SIZE = 2048;  // In Bytes
    private static final int CONNECTION_TIMEOUT = 10;       // In Seconds
    private static final int READ_TIMEOUT = 10;             // In Seconds
    private static int retryCount = 0;
    private static final int MAX_CONNECTION_RETRIES = 10;

    private Thread mClientThread;
    private HttpURLConnection urlConnection = null;

    public void connect() throws Exception {

        if (urlConnection != null) {
            urlConnection.disconnect();
        }

        Log.i(TAG, "Connecting to " + mUri);

        // Create and connect a socket to the server
        URL url = new URL(mUri.toString());
        urlConnection = (HttpURLConnection) url.openConnection();
        urlConnection.setConnectTimeout(CONNECTION_TIMEOUT * 1000);
        urlConnection.setReadTimeout(READ_TIMEOUT * 1000);
        appendUrlHeaders(urlConnection);

        // Force an operation n the URLConnection, which will throw an exception if connection fails
        urlConnection.getResponseCode();

        // Notify parent that connection was established
        onConnected();

        //  Log response header for debugging purposes
        DebugLog(urlConnection);
        retryCount = 0;

        // Receive the response from the server. This typically is a loop that never ends, but depending on the
        // response header, it may do other stuff (like try to authenticate and reconnect in a secure client).
        receiveResponse(urlConnection);

    }

    @Override
    protected void startConnectingInBackground() {
        if (!isUriValid()) {
            onError(ErrorDetail.UNSUPPORTED_URI);
            return;
        }

        // Execute all connection code in background
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                boolean shouldRun = true;
                while (shouldRun) {
                    try {
                        connect();
                        onDisconnected();
                        shouldRun = false;
                    } catch (SocketException e) {
                        try {
                            if (retryCount++ >= MAX_CONNECTION_RETRIES) {
                                // Notify parent that connection was disconnected
                                Log.i(TAG, Thread.currentThread().getName() + " exited.");
                                onError(ErrorDetail.UNKNOWN);
                                shouldRun = false;
                            }else{
                                Log.e(TAG, "Retry connect: " + retryCount);
                                Thread.sleep(500);
                                getStreamContainerDecoder().stopDecodingThread();
                                getStreamContainerDecoder().startDecodingInBackground();
                            }

                        } catch (Exception e1) {
                            e1.printStackTrace();
                        }

                    } catch (Exception e) {
                        Log.e(TAG, "Exception Caught: " + e);
                        Log.i(TAG, Thread.currentThread().getName() + " exited.");

                        // Notify parent that there was an error
                        if (e.getMessage() != null && e.getMessage().equals("Software caused connection abort")) {
                            onError(ErrorDetail.NETWORK_ERROR);
                        } else {
                            onError(ErrorDetail.UNKNOWN);
                        }
                        shouldRun = false;
                    }
                }
            }
        };

        // Start this thread
        mClientThread = new Thread(runnable, "StreamingProxy " + TAG + " clientThread");
        Log.i(TAG, "Thread " + mClientThread.getName() + " starting.");
        mClientThread.start();
    }


    @Override
    public void disconnect() {
        if (mClientThread == null) {
            return;
        }

        try {
            Log.i(TAG, "Interrupting Thread " + mClientThread.getName());
            mClientThread.interrupt();
            mClientThread.join(5000);
            mClientThread = null;
        } catch (InterruptedException e) {
            // Ignored
        }
    }


    /**
     * <p>Tells if the URI used by this client is valid. If there are no scheme on the URI,
     * 'http' will be used by default.
     * <p>
     * <p>This is typically called just before connecting to ensure that the URI is supported.
     */
    private boolean isUriValid() {
        String scheme = mUri.getScheme() == null ? "http" : mUri.getScheme();
        return scheme.equalsIgnoreCase("http");
    }


    /**
     * Appends the headers required for this type of connection to the header of the HTTP GET request.
     */
    protected void appendUrlHeaders(HttpURLConnection urlConnection) {
        urlConnection.setRequestProperty("User-Agent", mUserAgent);
        urlConnection.setRequestProperty("Connection", "close");
    }


    /**
     * Receive the response from the server.
     * Infinite loop, that ends when the client is closed that pushes all the data to the parent class, which in turn
     * pushes it to the listener.
     *
     * @param urlConnection The connection that received the data.
     */
    protected void receiveResponse(HttpURLConnection urlConnection) throws IOException {
        DataInputStream inStream = null;

        try {
            inStream = new DataInputStream(urlConnection.getInputStream());

            // Read response body (never ends)
            long now = System.currentTimeMillis();
            long lastReadTime;
            boolean timeOut = false;
            byte[] buffer = new byte[HTTP_READ_BUFFER_SIZE];
            while (!Thread.interrupted() && getState() == State.CONNECTED && !timeOut) {
                lastReadTime = now;

                int readLength = inStream.read(buffer);
                // Notify parent that a new message was received
                onMessageReceived(buffer, readLength);

                now = System.currentTimeMillis();
                timeOut = now > (lastReadTime + READ_TIMEOUT * 1000);
            }

            // Looping ended. If it ended because of a timeout, throw an exception to go to error state
            if (timeOut) {
                throw new RuntimeException("Read timeout.");
            }
        } catch (Exception ex) {
            Log.e("NetworkError", "This is a network error, please handle and reconnect");
            throw ex;
        }
        //Intentionally, no catch block. The exception is mean to be caught by the caller.
        finally {
            try {
                if (inStream != null) {
                    inStream.close();
                }
            } catch (Exception e) {
                // Ignored
            }
        }
    }

    /**
     * Prints the content of a response (return code + headers).
     * Does not print the content.
     */
    protected void DebugLog(HttpURLConnection urlConnection) {
        Log.v(TAG, "Received Response: ");

        try {
            Log.v(TAG, "STATUS: " + urlConnection.getResponseCode());

            Map<String, List<String>> responseHeaders = urlConnection.getHeaderFields();
            if (responseHeaders.size() > 0) {
                for (Map.Entry<String, List<String>> curEntry : responseHeaders.entrySet()) {
                    Log.v(TAG, "HEADER: " + curEntry.getKey() + " = " + curEntry.getValue());
                }
                Log.v(TAG, "");
            }
        } catch (IOException e) {
            // Ignored
        }
    }
}
