package com.tritondigital.net.streaming.proxy;

import com.tritondigital.net.streaming.proxy.Proxy.StateChangedListener.ErrorDetail;
import com.tritondigital.net.streaming.proxy.client.Client;
import com.tritondigital.net.streaming.proxy.dataprovider.DataProvider;
import com.tritondigital.net.streaming.proxy.decoder.StreamContainerDecoder;
import com.tritondigital.net.streaming.proxy.server.Server;
import com.tritondigital.net.streaming.proxy.utils.Log;

import java.net.URI;

/**
 * <p>This class allows connecting to a server to receive a stream, extract the Meta Data (if any), the Audio Data, repacketize it in a different
 * streaming protocol and open a local server to be used instead of the original one. The proxy is transparent to the application client. It is started
 * with the original URI that the application wishes to connect to, then it returns a different URL that the application client should use instead.
 *
 * <p>If the local server type is not a Raw Data type or has a different
 * transport than the original stream, a repacking will occur to transform the original data into something appropriate for the server. For example,
 * there is no repacking if the client is Flv (which uses Http) and the local server is Http Raw Data (the data is simply passed through). On the other
 * side, an Flv client with an RTSP server automatically implies repacking, because the protocol changes.
 *
 * <p>The purpose of this is to support additional stream transport protocols than the platform originally supports. A good example is Android, which
 * does not allow, as of this writing, streaming from an FLV source. However, it supports streaming from RTSP sources. This proxy can then be used
 * to connect to the FLV server, convert all the data at runtime and produce a RTSP server for the Android media server to connect to. The advantage
 * of using the proxy as a repacking tool is that it may allow supporting more formats, because some formats (like AAC on Android) are only supported
 * over some protocols. The disadvantage of using it to convert is that it may decrease performances, especially due to the intermediate bytes array
 * that may need to be created and garbage-collected. Great efforts have been made to reduce those creation to a minimum by reusing buffers, but there
 * may still be cases where new bytes array are created and copied, which may impact the performance. Always benchmark before using the repacking.
 *
 * <p>It can be also be used without any repacking, just to extract MetaData from the streaming source, which is something that is not supported for
 * all platforms. Again, as of this writing, Android supports stream raw MP3, but not streaming FLV. In order have CuePoint, this proxy could be used
 * to connect to the FLV source, extract the MetaData to notify a listener, extract the MP3 data and pass it as-is to the media player. The advantage
 * is that it reduces the overhead since there is no repackaging of the packets. This feature is currently not available out of the box, it requires
 * writing an extension to the Server class which would simply take all data decoded and sent it to the client as is.
 *
 * <p>The Metadata is sent to the listener when the associated timestamp is reached. When receiving the Metadata from the original stream, its timestamp
 * may refer to a date in the future. This is especially true if the stream is sent by burst instead of in real time. There is also a delay caused by
 * the Android MediaPlayer implementation of RTSP which requires a very large buffering to occur before the playback actually starts. To compensate for
 * all this, when the proxy receives a Metadata, it computes the time at which this metadata should be treated, based on the the metadata timestamp sent
 * by the server and the reference timestamp at which the playback actually started. It then waits for the good delay and only notify the listener when
 * the metadata should be treated. For this to work, the proxy needs to be notified when the playback starts. This is done by calling audioPlaybackDidStart
 * on the Proxy instance.
 *
 * <p>In total, the Proxy can use up to 6 threads. It uses one thread for the Client, two for the Decoder and two or three for the server. See each component's
 * documentation for details. When stopping, either because the media player closed the connection, because of an error an because of a manual stop,
 * all those threads are stopped gracefully.
 *
 * <p>The simplest way to create a Proxy is by using the ProxyFactory, which takes care of the creation of all the layers needed by a proxy (Client, Stream
 * Container Decoder, packetizer that provides data to the local server, local server). However, for more grained control or to add additional protocols
 * for the client or the local server, it is possible to create all instances manually.
 *
 * <p>The proxy is implemented as a small state machine. See the documentation of Proxy.State for details.
 *
 * <p>Usage example for manual creation of a Proxy:
 *  <pre>
 *     public void startPlayback()
 *     {
 *        Proxy proxy = new Proxy();
 *        proxy.setClient(new HttpClient());
 *        proxy.setStreamContainerDecoder(new FlvDecoder());
 *        proxy.setDataProvider(new RtpPacketProviderMp4Latm());
 *        proxy.setServer(new RtspServer());
 *
 *        // Connect metadata listener
 *       proxy.getStreamContainerDecoder().setMetaDataDecodedListener(metadataDecodedListener);
 *
 *        // Start using the proxy and playing (mMediaPlayer could be, for example, an allocated instance of the Android MediaPlayer class)
 *        mMediaPlayer.start(proxy.startAsync("http://myFlvUrl"));
 *
 *        // Optionally, but strongly recommended, make sure that the MediaPlayer.OnPreparedListener calls proxy.audioPlaybackDidStart() in
 *        // its 'onPrepared' implementation.
 *     }
 *
 *  </pre>
 */
public class Proxy
{
    public final String TAG = "Proxy";


    /**
     * <p>Represents the various states that the proxy could be in, depending on the state of both the client and the server.
     * The typical sequence is
     *
     * <ol>
     * <li>Initial state ({@code NOT_RUNNING})</li>
     * <li>Client connects ({@code CONNECTING})</li>
     * <li>Receives the minimal data from the client to be ready to use the local server ({@code SERVER_READY})</li>
     * <li>The Media Player (or another application client) connects to the local server ({@code RUNNING})</li>
     * <li>All disconnect when done ({@code NOT_RUNNING})</li>
     * </ol>
     */
    public enum State
    {
        NOT_RUNNING,    /** Initial state and received after a call to stop. Proxy is not entirely ready. Connecting to it may result in the server thread blocking during the generation of a response as all required data may not have been received yet. */
        CONNECTING,     /** The client is connecting. This is the first step of the proxy start, received after a call to start. he proxy stays in the state Connecting after its client connected until it has received enough data to switch to the mode SERVER_READY. */
        SERVER_READY,   /** Proxy is ready to be connected to. Received after a call to start, when all data needed to generate responses without blocking has been received. */
        RUNNING,        /** The application client connected to the localserver and the proxy is working to transfer the stream from the original source to the application client. */
        STOPPING,       /** The client or the server are stopping. The Proxy is about to stop running. */
        ERROR,          /** There was an error preventing the work of the proxy. It is stopped. */
    }


    /**
     * Interface to be implemented in order to be notified about the important changes of state of the server.
     * Calls to this listener may come from any thread, so thread safety should be considered when designing the implementation.
     */
    public interface StateChangedListener
    {
        /**
         * All different reason for error, used when notifying the listener about an error (onServerError).
         */
        enum ErrorDetail
        {
            UNKNOWN,            /** Generic error, cause could not be identified. */
            MISSING_CLIENT,     /** Client was not set (use setClient). */
            MISSING_SERVER,     /** Server was not set (use setServer). */
            CLIENT_ERROR,       /** The client produced an error. Use getDetails() to debug. */
            SERVER_ERROR,       /** The server produced an error. Use getDetails() to debug. */
        }
    }


    private volatile State mState = State.NOT_RUNNING;

    private Client                  mClient;
    private Server                  mServer;
    private StreamContainerDecoder  mStreamContainerDecoder;
    private DataProvider            mDataProvider;
    private volatile boolean        mStopping;


    /**
     * <p>Sets the client to be used to connect to the stream.
     * This client should be preconfigured (or at least configured before {@code start} is called).
     *
     * <p>Note that the proxy automatically registers to be the listener of the client, replacing the previous listener. There is no need
     * to keep a listener on the client directly as the proxy already forwards all the important messages in its own listener implementation.
     * When it comes to messages and listeners, the proxy should be seen as a whole, not as an individual client and server.
     *
     * @param client The client that will be used to connect to the remote server, which will do the job of pulling the data from the external server. Should be configured with
     *               its Listener, that will decode, extract data (Meta and Audio) and repack for the local server before {@code start} is called.
     */
    public void setClient(Client client)
    {
        mClient = client;
        linkAllComponents();
    }


    /**
     * Gets the client to be used to connect to the stream.
     */
    public Client getClient()
    {
        return mClient;
    }


    /**
     * <p>Sets the server to be used locally to send the stream to the Media Player (or the actual client that the application wishes to use).
     * This server should be preconfigured (or at least configured before {@code start} is called).
     *
     * <p>Note that the proxy automatically registers to be the listener of the server, replacing the previous listener. There is no need
     * to keep a listener on the server directly as the proxy already forwards all the important messages in its own listener implementation.
     * When it comes to messages and listeners, the proxy should be seen as a whole, not as an individual client and server.
     *
     * @param server The server that will be used to listen locally, to which the Media Player should connect. Should be configured with its Data Provider
     *               before {@code start} is called.
     */
    public void setServer(Server server)
    {
        mServer = server;
        linkAllComponents();
    }


    /**
     * Sets the instance that decodes the data received by the client and links it to the client.
     */
    public void setStreamContainerDecoder(StreamContainerDecoder streamContainerDecoder)
    {
        mStreamContainerDecoder = streamContainerDecoder;
        linkAllComponents();
    }


    /**
     * Gets the instance that decodes the data received by the client.
     */
    public  StreamContainerDecoder getStreamContainerDecoder()
    {
        return mStreamContainerDecoder;
    }


    /**
     * Gets the instance that provides data to the server (after re-encoding it if needed) and links it to the server.
     */
    public void setDataProvider(DataProvider dataProvider)
    {
        mDataProvider = dataProvider;
        linkAllComponents();
    }


    /**
     * Starts proxying the stream at the given uri.
     * This method blocks until the server has started (which may need to wait for some data from the server at the given uri), then returns the URI that should
     * be used instead of the original one.
     *
     * @param uri The URI of the external server that will send the stream.
     *
     * @return The uri (as a String) of the local server, that should be used in the Media Player instead of the original one
     *         or null if there was an error starting the proxy.
     */
    public URI start(URI uri)
    {
        return commonStart(uri, false);
    }


    /**
     * Starts proxying the stream at the given uri.
     * Convenience method to work with uri as Strings.
     *
     * @param uriStr The uri (as a String) of the external server that will send the stream.
     *
     * @return The uri (as a String) of the local server, that should be used in the Media Player instead of the original one
     *         or null if there was an error starting the proxy.
     */
    public String start(String uriStr)
    {
        try
        {
            URI uri = new URI(uriStr);
            return start(uri).toString();
        }
        catch (Exception e)
        {
            updateState();
            return null;
        }
    }


    /**
     * Starts proxying the stream asynchronously at the given uri.
     * Unlike start(URI), this method does not block until the server has started but returns immediately with the Uri that should be used as a replacement
     * for the original one. However, before using this Uri, the caller could wait for the onProxyServerReady notification (this listener should be added before
     * the call to connectAsync to avoid missing the notification). If the caller does not wait for the ready notification, then the server might block when
     * responding to an RTSP request, until the available data is ready. A sensitive client might consider this as a timeout.
     *
     * @param uri The URI of the external server that will send the stream.
     *
     * @return The uri (as a String) of the local server, that should be used in the Media Player instead of the original one
     *         or null if there was an error starting the proxy.
     *
     * \see start(URI)
     */
    public URI startAsync(URI uri)
    {
        return commonStart(uri, true);
    }


    /**
     * Starts proxying the stream asynchronously at the given uri.
     * Convenience method to work asynchronously with uri as Strings.
     *
     * @param uriStr The uri (as a String) of the external server that will send the stream.
     *
     * @return The uri (as a String) of the local server, that should be used in the Media Player instead of the original one
     *         or null if there was an error starting the proxy.
     */
    public String startAsync(String uriStr)
    {
        try
        {
            URI uri = new URI(uriStr);
            URI finalUri = startAsync(uri);

            if (finalUri == null)
                return null;

            return mServer.getState() == Server.State.LISTENING ? finalUri.toString() : null;
        }
        catch (Exception e)
        {
            updateState();
            return null;
        }
    }


    /**
     * Stops proxying the stream at the given uri.
     * Cuts the connection to the remote server, flushes any data accumulated internally and disconnect the local server.
     */
    public void stop()
    {       
        if (!mStopping)
        {
            Log.i(TAG, "Stopping");
            mStopping = true;
            updateState();

            mClient.stop();
            mStreamContainerDecoder.stop();

            mServer.stop();
            mDataProvider.stop();

            updateState();
        }
    }


    /**
     * Called by the media player class, which typically also starts the proxy, when the playback starts. This is used
     * to reset internal counters and timers which ensure that the data is treated at the right time. More specifically,
     * it makes sure that the Metadata is sent to the listeners at the right time, based on the timestamp associated with
     * it.
     */
    public void audioPlaybackDidStart()
    {
        mStreamContainerDecoder.setReferenceTimestampToNow();
    }


    /**
     * All start / startAsync lead to this method to have a single 'start' code.
     */
    @SuppressWarnings("StatementWithEmptyBody")
    private URI commonStart(URI uri, boolean async)
    {
        try
        {
            if ((mState == State.CONNECTING) || (mState == State.SERVER_READY) || (mState == State.RUNNING))
            {
                stop();
            }

            // TODO Temporary! A wait/notify would be a lot better!
            while((mState != State.NOT_RUNNING) && (mState != State.ERROR)) {}

            Log.i(TAG, "Starting");
            mStopping = false;

            mStreamContainerDecoder.startDecodingInBackground();
            mClient.connect(uri);
            if (async || mServer.blockUntilReady())
            {
                int mServerPort = -1;
                mServer.listen(mServerPort);
            }

            // Server.getUri() returns something like rtsp://127.0.0.1:1234, append the rest (e.g. /LOS40MOBILEAAC)
            String serverUriStr =  mServer.getUri().toString();
            serverUriStr = serverUriStr + uri.getRawPath();
            return new URI(serverUriStr);
        }
        catch (Exception e)
        {
            updateState();
            return null;
        }
    }


    /**
     * Make sure that all components are linked between themselves, which one is the listener to which one.
     * Typically called when a component changes.
     */
    private void linkAllComponents()
    {
        if (mClient != null)
        {
            mClient.setStateChangedListener(mClientStateChangedListener);
            mClient.setDataReceivedListener(mStreamContainerDecoder);
            mClient.setStreamContainerDecoder(mStreamContainerDecoder);
        }

        if (mServer != null)
        {
            mServer.setStateChangedListener(mServerStateChangedListener);
            mServer.setDataProvider(mDataProvider);
        }

        if (mStreamContainerDecoder != null)
        {
            mStreamContainerDecoder.setAudioDataDecodedListener(mDataProvider);
        }
    }


    /**
     * Get the state in which the proxy should be, based on the state of each of its components.
     */
    private State getExpectedState()
    {
        State newState;

        if (mServer.getState() == Server.State.STOPPING || mClient.getState() == Client.State.STOPPING)
        {
            // Client or server are stopping, Proxy should be stopping too.
            newState = State.STOPPING;
        }
        else if (mClient.getState() == Client.State.CONNECTED)
        {
            if (mServer.getState() == Server.State.CONNECTED)
            {
                // Client and server are both connected, Proxy is Running.
                newState = State.RUNNING;
            }
            else if (mServer.getState() == Server.State.READY || mServer.getState() == Server.State.LISTENING)
            {
                // Client is connected and server listening, Proxy is Server_Ready.
                newState = State.SERVER_READY;
            }
            else
            {
                // Client is connected and server is starting (neither connected nor listening), Proxy is Connecting.
                newState = State.CONNECTING;
            }
        }
        else
        {
            if (mServer.getState() == Server.State.NOTREADY)
            {
                // Client and server are both disconnected, Proxy is Not Running.
                newState = State.NOT_RUNNING;
            }
            else if (mClient.getState() == Client.State.CONNECTING)
            {
                // Client is disconnected but connecting. Proxy is Connecting.
                newState = State.CONNECTING;

            }
            else if (mState == State.RUNNING)
            {
                // Client is disconnected but not server, Proxy was running, it should now be Stopping.
                newState = State.STOPPING;

            }
            else
            {
                newState = State.NOT_RUNNING;
            }
        }

        return newState;
    }

    /**
     * Updates the internal state based on the state of the client and server. Called whenever there is a change on either of those objects
     * to reflect it in the internal state. notifies the listener about it.
     */
    private void updateState()
    {
        // Early out if server not set
        if (mServer == null)
        {
            setStateError(ErrorDetail.MISSING_SERVER);
            return;
        }

        // Early out if client not set
        if (mClient == null)
        {
            setStateError(ErrorDetail.MISSING_CLIENT);
            return;
        }

        // Set error state if client or server is on error state
        if (mClient.getState() == Client.State.ERROR)
        {
            setStateError(ErrorDetail.CLIENT_ERROR);
            return;
        }
        if (mServer.getState() == Server.State.ERROR)
        {
            setStateError(ErrorDetail.SERVER_ERROR);
            return;
        }

        State newState = getExpectedState();

        // Update internal variable (and notify listener) if state changed
        if (mState != newState)
        {
            Log.i(TAG, "State Transition: " + mState + " => " + newState);

            mState = newState;
        }
    }


    /**
     * Sets the internal state to error state and notify listener about it.
     * Called when changing state and at least one component encountered an unrecoverable error.
     */
    private void setStateError(ErrorDetail errorDetail)
    {
        final State newState = State.ERROR;

        if (mState != newState)
        {
            Log.i(TAG, "State Transition: " + mState + " => " + newState + " (" + errorDetail + ")");
            mState = newState;
        }
    }


    /**
     * Receive notifications from the client to update internal state.
     */
    private final Client.StateChangedListener mClientStateChangedListener = new Client.StateChangedListener()
    {
        @Override
        public void onClientConnected()
        {
            Proxy.this.updateState();
        }

        @Override
        public void onClientConnecting()
        {
            Proxy.this.updateState();
        }

        @Override
        public void onClientDisconnected()
        {
            stop();

            Proxy.this.updateState();
        }

        @Override
        public void onClientStopping()
        {
            stop();

            Proxy.this.updateState();
        }

        @Override
        public void onClientError(ErrorDetail errorDetail)
        {
            stop();
            Proxy.this.setStateError(Proxy.StateChangedListener.ErrorDetail.CLIENT_ERROR);
        }
    };


    /**
     * Receive notifications from the server to update internal state.
     */
    private final Server.StateChangedListener mServerStateChangedListener = new Server.StateChangedListener()
    {
        @Override
        public void onServerReady()
        {
            // This state can be reached in 2 ways:
            // 1- Server was just started and is ready.
            // 2- Server was connected and disconnected. In the case of this proxy, we simply stop the proxy if the server disconnected.
            if (mState == State.RUNNING)
                stop();

            Proxy.this.updateState();
        }

        @Override
        public void onServerNotReady()
        {
            Proxy.this.updateState();
        }

        @Override
        public void onServerListening()
        {
            Proxy.this.updateState();
        }

        @Override
        public void onServerConnected()
        {
            Proxy.this.updateState();
        }

        @Override
        public void onServerStopping()
        {
            stop();

            Proxy.this.updateState();
        }

        @Override
        public void onServerError()
        {
            stop();
            Proxy.this.setStateError(Proxy.StateChangedListener.ErrorDetail.SERVER_ERROR);
        }
    };
}
