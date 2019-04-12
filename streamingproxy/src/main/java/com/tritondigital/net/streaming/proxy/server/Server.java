package com.tritondigital.net.streaming.proxy.server;

import com.tritondigital.net.streaming.proxy.dataprovider.DataProvider;
import com.tritondigital.net.streaming.proxy.server.Server.StateChangedListener.ErrorDetail;
import com.tritondigital.net.streaming.proxy.utils.Log;

import java.net.URI;


/**
 * <p>Base class for any network server allowing sending a stream.
 * Any child of this class should provide functionality to take packets from a source (provider)
 * and send it to the client.
 *
 * <p>Typically, child classes will call onConnected, onDisconnected, onMessageReceived and onError when those events are received. The superclass will
 * synchronise the calls, ensure that the states are consistent and call the onCompleteConnection, onCompleteDisconnection, onProcessRequest and
 * onProcessError methods on the child class.
 *
 * <p>If they choose to manipulate the state directly, child classes should make sure to match the desired behaviour, especially setting the READY state
 * when the server is ready to listen, as this will unblock any thread that called blockUntilReady.
 */
public abstract class Server
{
    public final String TAG = "Server";

    protected static final String LOCALHOST = "127.0.0.1";

    /**
     * All different states in which the server can be.
     * Mostly used for logging and debugging purpose.
     */
    public enum State
    {
        NOTREADY,       /** Initial state or state when the Data Provider changes, until the provider tells that the listener is ready. */
        READY,          /** Ready to listen, all needed data from the provider has been received. blockUntilReady unblocks when this state is reached.*/
        LISTENING,      /** Accepting incoming connections. */
        CONNECTED,      /** Server is connected and streaming. */
        STOPPING,       /** Server is currently stopping. */
        ERROR,          /** Server is disconnected because it encountered an error. */
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
            CREATE_PACKET,      /** Failed to create a packet (received an error from the Data Provider) */
            LISTEN_FAILED,      /** Failed to start listening. */
            NO_DATA_PROVIDER,   /** A data provider must be set before listening. */
            OPEN_STREAM_SOCKET, /** An additional socket is required for streaming but it failed to open. */
            RECEIVE_REQUEST,    /** Failed to receive the Rtsp Request, other than the stream was closed becuase the server is stopping. */
            SEND_RESPONSE,      /** Failed to send the Rtsp Response to an Rtsp Request. */
            WRONG_MEDIA_TYPE,   /** The data is from a wrong media (e.g. MP3 when expecting AAC). */
        }


        /** Server is not ready to listen yet, it is missing some data. This happens when a new Data Provider is set and this provider is not ready yet. Its internal state is State.NOTREADY. */
        void onServerNotReady();

        /** Server is ready to listen, all required data has been received. When a server disconnects, it returns to the Ready state. Its internal state is State.READY. */
        void onServerReady();

        /** Server started to listen for incoming connections. Its internal state is State.LISTENING. */
        void onServerListening();

        /** Server accepted a connection and is streaming. Its internal state is State.CONNECTED. */
        void onServerConnected();

        /** Server started to stop. It disconnected and is waiting for the disconnected callback. Its internal state is State.STOPPING. */
        void onServerStopping();

        /** Server encountered an unrecoverable error. It has been disconnected and its internal state is State.ERROR. */
        void onServerError();
    }


    private volatile State       mState                 = State.NOTREADY;
    private StateChangedListener mStateChangedListener  = null;
    private final Object         mDataProviderReadyLock = new Object();
    private final Object         mDisconnectionLock     = new Object();

    /** Used to unblock the thread. Set to true to force the blocking loop to break, then reset to true so the next time that blockUntilReady is called, it will block. */
    private volatile boolean     mBlockUntilReadyEnabled = true;


    /**
     * Starts listening and accepting connections on the given port.
     *
     * @param port The port to listen on, -1 for any available port.
     *             When using any available port, make sure to use getUri in order to know the address of the server.
     *             If the port is specified (not -1), and is not available for bind, the server enters the error state.
     */
	public void listen(int port)
	{
            stop();

            setStateListening();
            bindAndListen(port);
	}


	/**
	 * Does the job of binding a socket and start accepting incoming connections.
	 */
	protected abstract void bindAndListen(int port);


    /**
     * Let the child class do whatever it needs with the message.
     * Only called if the server is in the correct state, and protected for thread safety.
     */
    protected abstract void onProcessMessage(Object userInfo);


	 /**
	 * Stops the server. Stops listening and disconnects the connected client if there is one.
	 * The server loses the ready state of its data provider when disconnected. It therefore has to
	 * wait for its Data Provider to send the ready notification before it can be used to listen again.
	 */
	public void stop()
	{
        if (mState == State.CONNECTED || mState == State.LISTENING)
        {
            synchronized(mDisconnectionLock)
            {
                setStateStopping();
            }

            disconnectAndUnbind();
        }

        // Unblock any thread waiting for the server to become ready
        stopBlockingUntilReady();
	}


    /**
     * Does the job of disconnecting, unbinding the socket and stop accepting incoming connections.
     */
	protected abstract void disconnectAndUnbind();


	   /**
     * Called when the child class accepts a connection. When it is safe (thread synchronisation), the
     * internal state is changed.
     */
    protected void onConnected()
    {
        synchronized(mDisconnectionLock)
        {
            if (mState == State.LISTENING)
                setStateConnected();
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
            if (mState != State.ERROR)
                setStateNotReady();
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
     * child is notified via onProcessMessage and receives the user info that it passed.
     */
    protected void onMessageReceived(Object userInfo)
    {
        boolean connected;
        synchronized(mDisconnectionLock)
        {
            connected = mState == State.CONNECTED;
        }

        if (connected)
            onProcessMessage(userInfo);
    }


	/**
	 *  Returns the Uri that should be used to connect to this server, typically localhost on the port specified in the call to listen.
	 *  Returns null if the server is not listening.
	 */
	public abstract URI getUri();


	/**
	 * <p>Some servers might require a certain amount of data to be already available (either to fill a buffer or to extract configuration information
	 * needed in the stream negotiation)  before being ready to listen. This method blocks the calling thread until the server has all required
	 * information and is ready to start listening.
	 *
	 * <p>This is typically between the data provider and the call to {@code listen}.
	 * There is also a notification sent when the server becomes ready, to avoid blocking the thread and allow it to do some UI animation while waiting.
	 * If the server is not ready, it may block when generating a response, causing the client to consider it as a timeout and disconnect.
	 *
	 * <p>This method blocks until either the server is ready, the server is stopped / encountered an error or until stopBlockingUntilReady is invoked.
	 * The return code will differ if the server is unlocked and the server is not ready.
	 *
	 * @return {@code true} If the server is ready, {@code false} if not (there was an error or the server was stopped).
	 */
	public boolean blockUntilReady()
	{
	    synchronized (mDataProviderReadyLock)
	    {
	        mBlockUntilReadyEnabled = true; // Set to true, may be set to false on a secondary thread to unblock this one.
            try
            {
        	    while (mBlockUntilReadyEnabled && mState != State.READY && mState != State.ERROR)
        	    {
                    Log.i(TAG, "Waiting for data provider to be ready");
                    mDataProviderReadyLock.wait();
        	    }
            }
            catch (InterruptedException e)
            {
                // Ignored
            }

    	    return mState == State.READY;
	    }
	}


	/**
	 * Unblock the thread waiting on a call to blockUntilReady.
	 */
	public void stopBlockingUntilReady()
	{
        synchronized (mDataProviderReadyLock)
        {
            mBlockUntilReadyEnabled = false;
            mDataProviderReadyLock.notify();
        }
	}


	/**
     * Sets the instance that provides the data to stream to the server.
     * Ignored if the server is already listening or connected.
     *
     * The servers often require a specific class of providers, make sure to read the specific server documentation for this method.
     */
    public abstract void setDataProvider(DataProvider rtpPacketProvider);


	/**
	 * Sets the listener to be notified about the various state change on the server.
	 */
	public void setStateChangedListener(StateChangedListener stateChangedListener)
	{
	    mStateChangedListener = stateChangedListener;
	}


	/**
	 * Gets the current state of the server.
	 */
	public State getState()
	{
	    return mState;
	}


	/**
     * Sets the internal state to not ready state and notify listener about it.
     * Called by subclasses when the DataProvider changes and is not ready to be used for streaming.
     */
    protected void setStateNotReady()
    {
        final State newState = State.NOTREADY;

        if (mState != newState)
        {
            Log.i(TAG, "State Transition: " + mState + " => " + newState);

            mState = newState;

            if (mStateChangedListener != null)
                mStateChangedListener.onServerNotReady();
        }
    }


	/**
     * Sets the internal state to ready state and notify listener about it.
     * Called by subclasses when they are ready to listen (all needed data have been received from the provider).
     */
    protected void setStateReady()
    {
        final State newState = State.READY;

        if (mState != newState)
        {
            Log.i(TAG, "State Transition: " + mState + " => " + newState);

            mState = newState;

            if (mStateChangedListener != null)
                mStateChangedListener.onServerReady();

            // Unblock any thread waiting for the server to become ready
            stopBlockingUntilReady();
        }
    }


	/**
     * Sets the internal state to listening state and notify listener about it.
     * Called by subclasses when they start listening.
     */
    protected void setStateListening()
    {
        final State newState = State.LISTENING;

        if (mState != newState)
        {
            Log.i(TAG, "State Transition: " + mState + " => " + newState);

            mState = newState;

            if (mStateChangedListener != null)
                mStateChangedListener.onServerListening();
        }
    }


    /**
     * Sets the internal state to connected state and notify listener about it.
     * Called by subclasses when they accept an incoming connection.
     */
    protected void setStateConnected()
    {
        final State newState = State.CONNECTED;

        if (mState != newState)
        {
            Log.i(TAG, "State Transition: " + mState + " => " + newState);

            mState = newState;

            if (mStateChangedListener != null)
                mStateChangedListener.onServerConnected();
        }
    }


    /**
     * Sets the internal state to stopping state and notify listener about it.
     * Called by subclasses when they start to stop. Stays in this state until the stop is complete.
     */
    protected void setStateStopping()
    {
        final State newState = State.STOPPING;

        if (mState != newState)
        {
            Log.i(TAG, "State Transition: " + mState + " => " + newState);

            mState = newState;

            if (mStateChangedListener != null)
                mStateChangedListener.onServerStopping();

            // Unblock any thread waiting for the server to become ready
            stopBlockingUntilReady();
        }
    }


    /**
     * Sets the internal state to error state and notify listener about it.
     * Called by subclasses when they encounter an unrecoverable error.
     */
    protected void setStateError(ErrorDetail errorDetail)
    {
        final State newState = State.ERROR;

        if (mState != newState)
        {
            Log.i(TAG, "State Transition: " + mState + " => " + newState + " (" + errorDetail + ")");

            mState = newState;

            if (mStateChangedListener != null)
                mStateChangedListener.onServerError();

            // Unblock any thread waiting for the server to become ready
            stopBlockingUntilReady();
        }
    }
}
