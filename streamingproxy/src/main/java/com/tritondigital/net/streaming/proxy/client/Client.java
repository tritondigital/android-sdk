package com.tritondigital.net.streaming.proxy.client;

import com.tritondigital.net.streaming.proxy.client.Client.StateChangedListener.ErrorDetail;
import com.tritondigital.net.streaming.proxy.dataprovider.DataProvider;
import com.tritondigital.net.streaming.proxy.decoder.StreamContainerDecoder;
import com.tritondigital.net.streaming.proxy.utils.Log;

import java.net.URI;

/**
 * Base class for any network client allowing connection to a stream.
 * Any child of this class should provide functionality to stream from a source
 * and notify its listener whenever new data becomes available.
 *
 * The listener receives the stream data, but not the response header) and is
 * responsible for interpreting and decoding this data. The received data is not modified
 * of buffered. The listener is responsible for determining if the data is complete and
 * should cumulate incomplete chunks in order to use them when enough data was received.
 *
 * Child classes should correctly set the state to avoid having inconsistent behaviours in the proxy.
 * Each state update method should be called at the appropriate time: onConnected, onDisconnected, onError
 * and onMessageReceived. It should implement the connection mechanism, make sure that the connection
 * runs in a separate thread (method startConnectingInBackground) and that it can be disconnected easily
 * (method disconnect).
 *
 * The connection and transfer are all done in a background thread.
 */
public abstract class Client
{
    public static final String TAG = "Client";

    /**
     * Interface definition for a callback to be invoked when new data is received from the stream.
     * Methods on this listener may be invoked from a secondary thread. The class implementing this listener
     * should be thread-safe.
     */
    public interface DataReceivedListener
    {
        /**
         * Data has been received from the stream.
         * To allow buffers reusing, the length of valid data in the array is passed.
         * Returns false if there was an error while treating / enqueuing the data, which typically indicates to terminate the connection.
         */
        boolean onDataReceived(byte[] data, int dataLength);
    }


    /**
     * All different states in which the client can be.
     * Mostly used for logging and debugging purpose.
     */
    public enum State
    {
        INITIAL,        /** Initial state. */
        CONNECTING,     /** Client is connecting. Stays in this state until the connection succeeds and the GET request is made. */
        CONNECTED,      /** Client is connected and transmitting / receiving data. */
        DISCONNECTED,   /** Client has been connected and is now disconnected. */
        STOPPING,       /** Client is currently stopping. */
        RECONNECTING,   /** Client is currently stopping to reconnect when the disconnection is done. */
        ERROR,          /** Client is disconnected because it encountered an error. */
    }


    /**
     * Interface to be implemented in order to be notified about the important changes of state of the client.
     * Calls to this listener may come from any thread, so thread safety should be considered when designing the implementation.
     */
    public interface StateChangedListener
    {
        /**
         * All different reason for error, used when notifying the listener about an error (onClientError).
         */
        enum ErrorDetail
        {
            UNKNOWN,          /** Generic error, cause could not be identified. */
            MALFORMED_URI,    /** URI is malformed when passed as a String. */
            UNSUPPORTED_URI,  /** URI is not supported (may have an unsupported scheme). */
            ENQUEUE_NEW_DATA, /** Failure when sending the new data to the listener. Listener was unable to treat or enqueue it. */
            NETWORK_ERROR,    /**Failure caused by some network error */
        }

        /** Client has started a connection attempt, but has not connected yet. Its internal state is State.CONNECTING. */
        void onClientConnecting();

        /** Client is connected. Its internal state is State.CONNECTED. */
        void onClientConnected();

        /** Client has been disconnected voluntarily. Its internal state is State.DISCONNECTED. */
        void onClientDisconnected();

        /** Client started to stop. It disconnected and is waiting for the disconnected callback. Its internal state is State.STOPPING. */
        void onClientStopping();

        /** Client encountered an unrecoverable error. It has been disconnected and its internal state is State.ERROR. */
        void onClientError(ErrorDetail errorDetail);
    }


    private volatile State         mState = State.INITIAL;
    private StateChangedListener   mStateChangedListener;
    protected DataReceivedListener mDataReceivedListener;
    protected StreamContainerDecoder streamContainerDecoder;
    protected URI                  mUri;
    private final Object           mDisconnectionLock = new Object();
    protected String               mUserAgent = "TritonDigital Streaming Proxy";

    /**
     * Sets the User Agent used when connecting.
     * Does nothing if set after the {@code connect} was called.
     */
    public void setUserAgent(String userAgent)
    {
        if (userAgent != null)
        {
            mUserAgent = userAgent;
        }
    }


    /**
     * Connects the client to the given URI and start streaming. This call is asynchronous, it starts
     * the connection process in a secondary thread and return immediately.
     * If the URI is not supported, an error is returned and the listener is notified. If there
     * is an error or a timeout during the connection, the listener is also notified.
     */
    public void connect(URI uri)
    {
        stop();

        mUri = uri;
        setStateConnecting();
        startConnectingInBackground();
    }


    /**
     * Connects the client to the given URI and start streaming. This call is asynchronous, it starts
     * the connection process in a secondary thread and return immediately.
     * If the URI is not supported, an error is returned and the listener is notified. If there
     * is an error or a timeout during the connection, the listener is also notified.
     */
    protected abstract void startConnectingInBackground();


    /**
     * Trigger the disconnection if already connected.
     */
    public void stop()
    {
        if (getState() == State.CONNECTED || getState() == State.CONNECTING || mState == State.RECONNECTING)
        {
            synchronized(mDisconnectionLock)
            {
                setStateStopping();
            }
            disconnect();
        }
    }


    /**
     * Does the job of Stops connecting or disconnect.
     */
    protected abstract void disconnect();


    /**
     * Called when the child class connects to a server. When it is safe (thread synchronisation), the
     * internal state is changed.
     */
    protected void onConnected()
    {
        synchronized(mDisconnectionLock)
        {
            if (mState == State.CONNECTING || mState == State.RECONNECTING)
            {
                setStateConnected();
            }
        }
    }


    /**
     * Called when the child class disconnected. When it is safe (thread synchronisation), the
     * internal state is changed.
     */
    protected void onDisconnected()
    {
        synchronized(mDisconnectionLock)
        {
            if (mState == State.RECONNECTING)
            {
                startConnectingInBackground();
            }
            else if (mState != State.ERROR)
            {
                setStateDisconnected();
            }
        }
    }


    /**
     * Called when the child class encounters an error. When it is safe (thread synchronisation), the
     * internal state is changed.
     */
    protected void onError(ErrorDetail errorDetail)
    {
        synchronized(mDisconnectionLock)
        {
            setStateError(errorDetail);
        }
    }


    /**
     * Called when the child class receives data. When it is safe (thread synchronisation), the
     * message is posted to the listener.
     */
    protected void onMessageReceived(byte[] buffer, int bufferLength)
    {
        boolean connected;
        synchronized(mDisconnectionLock)
        {
            connected = mState == State.CONNECTED;
        }

        if (connected && !mDataReceivedListener.onDataReceived(buffer, bufferLength))
        {
            setStateError(ErrorDetail.ENQUEUE_NEW_DATA);
        }
    }


    /**
     * Sets the listener notified whenever a chunk of data is downloaded.
     * It is strongly discouraged to change the listener when a connection or a download is in progress
     * as there is no way to know which of the new or the old listener will receive the callback during the transition.
     */
    public void setDataReceivedListener(DataReceivedListener listener)
    {
        mDataReceivedListener = listener;
    }


    /**
     * Sets the listener to be notified about the various state change on the client.
     */
    public void setStateChangedListener(StateChangedListener stateChangedListener)
    {
        mStateChangedListener = stateChangedListener;
    }


    /**
     * Gets the current state of the client.
     */
    public State getState()
    {
        return mState;
    }


    /**
     * Sets the internal state to connecting state and notify listener about it.
     * Called by subclasses when they start connect to a server but has not connected yet.
     */
    private void setStateConnecting()
    {
        final State newState = State.CONNECTING;

        if (mState != newState)
        {
            Log.i(TAG, "State Transition: " + mState + " => " + newState);

            mState = newState;

            if (mStateChangedListener != null)
            {
                mStateChangedListener.onClientConnecting();
            }
        }
    }


    /**
     * Sets the internal state to connected state and notify listener about it.
     * Called by subclasses when they connect to a server.
     */
    private void setStateConnected()
    {
        final State newState = State.CONNECTED;

        if (mState != newState)
        {
            Log.i(TAG, "State Transition: " + mState + " => " + newState);

            mState = newState;

            if (mStateChangedListener != null)
            {
                mStateChangedListener.onClientConnected();
            }
        }
    }


    /**
     * Sets the internal state to disconnected state and notify listener about it.
     * Called by subclasses when they disconnect from server.
     */
    private void setStateDisconnected()
    {
        final State newState = State.DISCONNECTED;

        if (mState != newState)
        {
            Log.i(TAG, "State Transition: " + mState + " => " + newState);

            mState = newState;

            if (mStateChangedListener != null)
            {
                mStateChangedListener.onClientDisconnected();
            }
        }
    }


    /**
     * Sets the internal state to stopping state and notify listener about it.
     * Called by subclasses when they start to stop. Stays in this state until the stop is complete.
     */
    private void setStateStopping()
    {
        final State newState = State.STOPPING;

        if (mState != newState)
        {
            Log.i(TAG, "State Transition: " + mState + " => " + newState);

            mState = newState;

            if (mStateChangedListener != null)
            {
                mStateChangedListener.onClientStopping();
            }
        }
    }


    /**
     * Sets the internal state to error state and notify listener about it.
     * Called by subclasses when they encounter an unrecoverable error.
     */
    private void setStateError(ErrorDetail errorDetail)
    {
        final State newState = State.ERROR;

        if (mState != newState)
        {
            Log.i(TAG, "State Transition: " + mState + " => " + newState + " (" + errorDetail + ")");

            mState = newState;

            if (mStateChangedListener != null)
            {
                mStateChangedListener.onClientError(errorDetail);
            }
        }
    }

    public StreamContainerDecoder getStreamContainerDecoder() {
        return streamContainerDecoder;
    }

    public void setStreamContainerDecoder(StreamContainerDecoder streamContainerDecoder) {
        this.streamContainerDecoder = streamContainerDecoder;
    }
}
