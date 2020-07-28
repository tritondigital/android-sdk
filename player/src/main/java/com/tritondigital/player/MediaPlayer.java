package com.tritondigital.player;

import android.content.Context;
import android.os.Bundle;

import com.tritondigital.util.Assert;
import com.tritondigital.util.Log;
import com.tritondigital.util.AnalyticsTracker;

/**
 * Base class for a media player.
 *
 * Always use the constants instead of hardcoding the values in your app.
 *
 * @par Life Cycle
 *
 * \image html mediaplayer_fsm.png
 */
@SuppressWarnings("unused")
public abstract class MediaPlayer {

    // TODO: add a volume changed info
    // TODO: send a BufferingEnded info when stopping the stream if we had a "buffering started" event.
    // TODO: Test network change --> onCompletion for mp3

    /**
     * Callback for receiving CuePoint.
     */
    public interface OnCuePointReceivedListener {
        /**
         * Called when a player has received a cue point.
         *
         * @param player    Source where this event comes from
         * @param cuePoint  Received CuePoint
         */
        void onCuePointReceived(MediaPlayer player, Bundle cuePoint);
    }

    /**
     * Callback for receiving Metadata.
     */
    public interface OnMetaDataReceivedListener {
        /**
         * Called when a player has received metadata.
         *
         * @param player    Source where this event comes from
         * @param metadata  Received metadata
         */
        void onMetaDataReceived(MediaPlayer player, Bundle metadata);
    }

    /**
     * Callback for receiving player info.
     */
    public interface OnInfoListener {
        /**
         * Called when a player info is available.
         *
         * - MediaPlayer.INFO_ALTERNATE_MOUNT
         * - MediaPlayer.INFO_BUFFERING_COMPLETED
         * - MediaPlayer.INFO_BUFFERING_START
         * - MediaPlayer.INFO_DURATION_CHANGED
         * - MediaPlayer.INFO_SEEK_COMPLETED
         * - MediaPlayer.INFO_SEEK_STARTED
         * - MediaPlayer.INFO_SEEKABLE_CHANGED
         *
         * @param player    Source where this event comes from
         * @param info      The type of info
         * @param extra     Info extra
         */
        void onInfo(MediaPlayer player, int info, int extra);
    }


    /**
     * Callback for receiving state changes.
     *
     * \image html mediaplayer_fsm.png
     */
    public interface OnStateChangedListener {
        /**
         * Called when a player state has changed.
         *
         * @param player    Source where this event comes from
         * @param state     New state
         */
        void onStateChanged(MediaPlayer player, int state);
    }

    /** Error code indicating an error in the OS player or Google Cast */
    public static final int ERROR_LOW_LEVEL_PLAYER_ERROR = 210;

    /** Error code indicating a connection problem to the server */
    public static final int ERROR_CONNECTION_FAILED = 211;

    /** Error code indicating than the connection has timed out */
    public static final int ERROR_CONNECTION_TIMEOUT = 408;  // Value sync with HTTP status code

    /** Error code indicating the end of a media which shouldn't occur on live streaming */
    public static final int ERROR_UNEXPECTED_END_OF_MEDIA = 213;

    /** Error code indicating that the stream can't be used in the user location */
    public static final int ERROR_GEOBLOCKED = 453; // Same value as in provisioning

    /** Error code indicating that the URL isn't in a valid format */
    public static final int ERROR_INVALID_URL = 216;

    /** Error code indicating the lack of network connectivity */
    public static final int ERROR_NO_NETWORK = 217;

    /** Error code indicating that the input source couldn't be found */
    public static final int ERROR_NOT_FOUND = 404;

    /** Error code indicating that no server is available */
    public static final int ERROR_SERVICE_UNAVAILABLE = 503;


    /** The end of the media source has been reached (on-demand only) */
    public static final int STATE_COMPLETED = 200;

    /** The player is connecting to the server or buffering */
    public static final int STATE_CONNECTING = 201;

    /** An error has occurred */
    public static final int STATE_ERROR = 202;

    /** The playback is started */
    public static final int STATE_PLAYING = 203;

    /** The resources have been released and this player instance can no longer be used */
    public static final int STATE_RELEASED = 204;

    /** The playback is stopped */
    public static final int STATE_STOPPED = 205;

    /** The playback is paused (on-demand only) */
    public static final int STATE_PAUSED = 206;

    /** Live stream */
    public static final int DURATION_LIVE_STREAM = Integer.MAX_VALUE;

    /** Unknown duration */
    public static final int DURATION_UNKNOWN = -1;

    /** Unknown position */
    public static final int POSITION_UNKNOWN = 0;

    /** Ducked volume */
    public static final float VOLUME_DUCK   = 0.2f;

    /** Muted volume */
    public static final float VOLUME_MUTE   = 0.0f;

    /** Normal volume */
    public static final float VOLUME_NORMAL = 1.0f;

    /** A the player has connected to an alternate mount */
    public static final int INFO_ALTERNATE_MOUNT = 270;

    /** A seek operation has completed */
    public static final int INFO_SEEK_COMPLETED = 271;

    /** Media duration changed (extra=duration)*/
    public static final int INFO_DURATION_CHANGED = 272;

    /** Seekable state change (0=false / 1=true) */
    public static final int INFO_SEEKABLE_CHANGED = 273;

    /** A seek operation has begun */
    public static final int INFO_SEEK_STARTED = 274;

    /** Temporarily pausing playback internally in order to buffer more data */
    public static final int INFO_BUFFERING_START = 275;

    /** The playback is resumed after filling buffers */
    public static final int INFO_BUFFERING_COMPLETED = 276;

    // Hidden field. 12 hours (arbitrary value)
    static final int DURATION_LIVE_MIN_VALUE = 43200000;

    static final int REQUESTED_ACTION_PAUSE   = 2001;
    static final int REQUESTED_ACTION_PLAY    = 2002;
    static final int REQUESTED_ACTION_RELEASE = 2003;
    static final int REQUESTED_ACTION_STOP    = 2004;

    protected final String TAG = makeTag();
    private static final String STATIC_TAG = Log.makeTag("MediaPlayer");
    private final Context mContext;
    protected final Bundle  mSettings;

    // Listeners.
    private OnCuePointReceivedListener mCuePointListener;
    private OnMetaDataReceivedListener mMetadataListener;
    private OnInfoListener             mOnInfoListener;
    private OnStateChangedListener     mStateChangedListener;

    private Bundle  mLastCuePoint;
    private int     mLastErrorCode;
    private int     mState           = STATE_STOPPED;
    private int     mRequestedAction = REQUESTED_ACTION_STOP;

    protected abstract void internalPause();
    protected abstract void internalPlay();
    protected abstract void internalStop();
    protected abstract void internalRelease();
    protected abstract void internalSeekTo(int position);
    protected abstract String makeTag();
    protected abstract boolean isEventLoggingEnabled();


    /**
     * Constructor
     *
     * A copy "settings" is made to make sure the client doesn't modified it while
     * the MediaPlayer is using it.
     *
     * @throws IllegalArgumentException if context is null
     * @throws IllegalArgumentException if settings is null
     */
    public MediaPlayer(Context context, Bundle settings) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }

        if (settings == null) {
            throw new IllegalArgumentException("settings must not be null");
        }

        mContext = context;

        // Creating a copy in case the client tries to update the bundle
        // after creating this class.
        mSettings = new Bundle(settings);

        //Initialize Google Analytics Tracker
        AnalyticsTracker.getTracker(context).initialize();
    }

    /**
     * Returns the settings of this player.
     */
    public Bundle getSettings() {
        return mSettings;
    }


    /**
     * Returns the player's state
     */
    public int getState() {
        return mState;
    }


    /**
     * Returns the last received cue point.
     */
    public Bundle getLastCuePoint() {
        return mLastCuePoint;
    }


    /**
     * Returns the last error code.
     */
    public int getLastErrorCode() {
        return mLastErrorCode;
    }


    /**
     * Returns the user requested action
     */
    int getRequestedAction() { return mRequestedAction; }


    /**
     * Returns the current context
     */
    final Context getContext() { return mContext; }


    /**
     * Returns the cue point listener.
     */
    @SuppressWarnings("UnusedDeclaration")
    public OnCuePointReceivedListener getOnCuePointReceivedListener() {
        return mCuePointListener;
    }

    /**
     * Returns the metadata listener.
     */
    @SuppressWarnings("UnusedDeclaration")
    public OnMetaDataReceivedListener getMetadataListener() {
        return mMetadataListener;
    }


    /**
     * Returns the info listener.
     */
    @SuppressWarnings("UnusedDeclaration")
    public OnInfoListener getOnInfoListener() {
        return mOnInfoListener;
    }


    /**
     * Returns the state changed listener.
     */
    @SuppressWarnings("UnusedDeclaration")
    public OnStateChangedListener getOnStateChangedListener() {
        return mStateChangedListener;
    }


    /**
     * Sets the cue point event listener.
     */
    public void setOnCuePointReceivedListener(OnCuePointReceivedListener listener) {
        mCuePointListener = listener;
    }

    /**
     * Sets the cue point event listener.
     */
    public void setOnMetaDataReceivedListener(OnMetaDataReceivedListener listener) {
        mMetadataListener = listener;
    }


    /**
     * Sets the info listener.
     */
    public void setOnInfoListener(OnInfoListener listener) {
        mOnInfoListener = listener;
    }


    /**
     * Sets the state changed listener.
     */
    public void setOnStateChangedListener(OnStateChangedListener listener) {
        mStateChangedListener = listener;
    }


    /**
     * Returns the duration of the current media in milliseconds.
     *
     * @retval MediaPlayer.DURATION_LIVE_STREAM     Live stream
     * @retval MediaPlayer.DURATION_UNKNOWN         Unavailable duration
     */
    public abstract int getDuration();


    /**
     * Returns the current position playback position in milliseconds.
     */
    public abstract int getPosition();


    /**
     * Returns true if the media is seekable in the current state
     *
     * For now, it is the same as MediaPlayer.isPausable()
     */
    public boolean isSeekable() {
        return isPausable();
    }


    /**
     * Returns true if the media can be paused at the current state
     */
    public boolean isPausable() {
        int state = getState();
        if ((state == STATE_CONNECTING) || (state == STATE_PLAYING) || (state == STATE_PAUSED)) {
            int duration = getDuration();
            return ((duration > 0) && (duration < DURATION_LIVE_STREAM));
        } else {
            return false;
        }
    }


    /**
     * Seek to the provided position.
     *
     * Does nothing if the media isn't loaded or seekable.
     */
    public final void seekTo(int position) {
        if (isSeekable()) {
            internalSeekTo(position);
        } else {
            Log.w(TAG, "The media isn't seekable");
        }
    }


    /**
     * Seek to a time from the current position.
     */
    public final void seek(int delta) {
        if (isSeekable()) {
            if (delta != 0) {
                int position = getPosition() + delta;
                position = Math.max(0, position);
                position = Math.min(position, getDuration());

                seekTo(position);
            }
        } else {
            Log.w(TAG, "Not seekable");
        }
    }


    /**
     * Normalize the duration received by lo level players
     */
    static int normalizeDuration(long duration) {
        if      (duration <= 0)                      {return DURATION_UNKNOWN;}
        else if (duration > DURATION_LIVE_MIN_VALUE) {return DURATION_LIVE_STREAM;}
        else                                         {return (int)duration;}
    }


    /**
     * Returns the current volume (0.0f to 1.0f)
     */
    public abstract float getVolume();


    /**
     * Sets the volume on this player.
     *
     * This method can be used to lower the volume when losing the device audio focus.
     * http://developer.android.com/training/managing-audio/audio-focus.html
     *
     * @param volume Volume (0.0f to 1.0f)
     */
    public abstract void setVolume(float volume);


    /**
     * Starts the playback
     */
    public final void play() {
        switch (mState) {
            case STATE_COMPLETED:
            case STATE_ERROR:
            case STATE_STOPPED:
            case STATE_PAUSED:
                mRequestedAction = REQUESTED_ACTION_PLAY;
                internalPlay();
                break;

            case STATE_CONNECTING:
            case STATE_PLAYING:
                break;

            case STATE_RELEASED:
                Log.i(TAG, "play() invalid in state: " + debugStateToStr(mState));
                break;

            default:
                Assert.failUnhandledValue(TAG, mState, "play()");
        }
    }


    /**
     * Pauses the playback so it can be resumed at the same
     * position by calling play().
     *
     * For live content, this method does the same as MediaPlayer.stop().
     */
    public final void pause() {
        if (isPausable()) {
            switch (mState) {
                case STATE_CONNECTING:
                case STATE_PLAYING:
                    mRequestedAction = REQUESTED_ACTION_PAUSE;
                    internalPause();
                    break;

                case STATE_PAUSED:
                    break;

                case STATE_COMPLETED:
                case STATE_ERROR:
                case STATE_STOPPED:
                case STATE_RELEASED:
                    Log.i(TAG, "pause() invalid in state: " + debugStateToStr(mState));
                    break;

                default:
                    Assert.failUnhandledValue(TAG, mState, "pause()");
            }

        } else {
            stop();
        }
    }


    /**
     * Stops the playback
     */
    public final void stop() {
        switch (mState) {
            case STATE_CONNECTING:
            case STATE_PLAYING:
            case STATE_COMPLETED:
            case STATE_ERROR:
            case STATE_PAUSED:
                mRequestedAction = REQUESTED_ACTION_STOP;
                internalStop();
                break;

            case STATE_STOPPED:
                break;

            case STATE_RELEASED:
                Log.i(TAG, "stop() invalid in state: " + debugStateToStr(mState));
                break;

            default:
                Assert.failUnhandledValue(TAG, mState, "stop()");
        }
    }


    /**
     * Releases the player resources.
     *
     * No other method may be called on this instance after release.
     */
    public final void release() {
        if (mState != STATE_RELEASED)
        {
            if (mState != STATE_STOPPED) {
                stop();
            }

            mRequestedAction = REQUESTED_ACTION_RELEASE;
            internalRelease();

            mCuePointListener     = null;
            mMetadataListener     = null;
            mOnInfoListener       = null;
            mStateChangedListener = null;
        }
    }


    /**
     * Set this player's state.
     */
    final void setState(int state) {
        if (isTransitionValid(mState, state)) {
            if (isEventLoggingEnabled()) {
                Log.i(TAG, "State changed: " + debugStateToStr(mState) + " -> " + debugStateToStr(state));
            }
            mState = state;

            // Clear the current cue point
            if (!isCuePointValidInCurrentState()) {
                notifyCuePoint(null);
            }

            // Notify state changed.
            if (mStateChangedListener != null) {
                mStateChangedListener.onStateChanged(this, state);
            }
        } else if (mState != state) {
            Log.w(TAG, "Invalid state transition: " + debugStateToStr(mState) + " -> " + debugStateToStr(state));
        }
    }


    /**
     * Change the state to STATE_ERROR with the provided error detail.
     */
    final void setErrorState(int errorCode) {
        mLastErrorCode = errorCode;
        setState(STATE_ERROR);
    }


    final void notifyInfo(int info, int detail) {
        debugLogInfo(info, detail);

        if (mOnInfoListener != null) {
            mOnInfoListener.onInfo(this, info, detail);
        }
    }


    private void debugLogInfo(int info, int detail) {
        if (isEventLoggingEnabled()) {
            switch (info) {
                case INFO_DURATION_CHANGED:
                    Log.i(TAG, "Info: " + debugInfoToStr(info) + " to " + (detail / 1000) + "s");
                    break;

                case MediaPlayer.INFO_SEEK_COMPLETED:
                    Log.i(TAG, "Info: " + debugInfoToStr(info) + ": " + (detail / 1000) + "s");
                    break;

                case MediaPlayer.INFO_SEEKABLE_CHANGED:
                    Log.i(TAG, "Info: " + debugInfoToStr(info) + ": " + ((detail == 1) ? "true" : "false"));
                    break;
            }
        }
    }


    final void notifyInfo(int info) {
        notifyInfo(info, 0);
    }


    final void notifyCuePoint(Bundle cuePoint) {
        // Bad state --> null cue point
        if (!isCuePointValidInCurrentState()) {
            cuePoint = null;
        }

        // Ignore multiple null cue points
        if ((mLastCuePoint == null) && (cuePoint == null)) {
            return;
        }

        mLastCuePoint = cuePoint;

        if (isEventLoggingEnabled()) {
            Log.i(TAG, "Cue point: " + cuePoint);
        }

        if (mCuePointListener != null) {
            mCuePointListener.onCuePointReceived(this, cuePoint);
        }
    }

    final void notifyMetadata(Bundle msg) {

        if (!isCuePointValidInCurrentState()) {
            return;
        }

        if (isEventLoggingEnabled()) {
            Log.i(TAG, "Metadata: " + msg);
        }

        if (mMetadataListener != null) {
            mMetadataListener.onMetaDataReceived(this, msg);
        }
    }



    private boolean isCuePointValidInCurrentState() {
        return (mState == STATE_CONNECTING) || (mState == STATE_PLAYING);
    }


    /**
     * Returns true if a play request is valid in the provided state
     */
    public static boolean isPlayValidInState(int state) {
        return isTransitionValid(state, STATE_CONNECTING);
    }


    /**
     * Returns true if the provided state transition is valid.
     *
     * @param state0 Initial state.
     * @param state1 Final state.
     */
    public static boolean isTransitionValid(int state0, int state1) {
        switch (state0) {
            case STATE_CONNECTING:
                return (state1 == STATE_ERROR) || (state1 == STATE_PLAYING) || (state1 == STATE_STOPPED) || (state1 == STATE_PAUSED);

            case STATE_ERROR:
                return (state1 == STATE_CONNECTING) || (state1 == STATE_STOPPED);

            case STATE_PLAYING:
                return (state1 == STATE_ERROR) || (state1 == STATE_STOPPED) || (state1 == STATE_PAUSED) || (state1 == STATE_COMPLETED);

            case STATE_STOPPED:
                return (state1 == STATE_CONNECTING) || (state1 == STATE_ERROR) || (state1 == STATE_RELEASED);

            case STATE_PAUSED:
                return (state1 == STATE_CONNECTING) || (state1 == STATE_ERROR) || (state1 == STATE_STOPPED);

            case STATE_COMPLETED:
                return (state1 == STATE_CONNECTING) || (state1 == STATE_STOPPED);

            case STATE_RELEASED:
                return false;

            default:
                Assert.failUnhandledValue(STATIC_TAG, state0, "isTransitionValid");
                return false;
        }
    }


    /**
     * Utility method to convert an error code to a debug string.
     *
     * @note To be used only for debugging purpose.
     */
    public static String debugErrorToStr(int errorCode) {
        switch (errorCode) {
            case ERROR_LOW_LEVEL_PLAYER_ERROR:  return "Low level player error";
            case ERROR_CONNECTION_FAILED:       return "Connection failed";
            case ERROR_CONNECTION_TIMEOUT:      return "Connection timeout";
            case ERROR_GEOBLOCKED:              return "Geoblocked";
            case ERROR_NOT_FOUND:               return "Not found";
            case ERROR_INVALID_URL:             return "Invalid URL";
            case ERROR_NO_NETWORK:              return "No network";
            case ERROR_SERVICE_UNAVAILABLE:     return "No servers available";
            case ERROR_UNEXPECTED_END_OF_MEDIA:            return "Unexpected end of media";
            default:
                Assert.failUnhandledValue(STATIC_TAG, errorCode, "debugErrorToStr");
                return "Unknown";
        }
    }


    /**
     * Utility method to convert an info code to a debug string.
     *
     * @copydetails debugErrorToStr(int)
     */
    public static String debugInfoToStr(int info) {
        switch (info) {
            case INFO_DURATION_CHANGED:     return "Duration changed";
            case INFO_SEEK_COMPLETED:       return "Seek completed";
            case INFO_SEEK_STARTED:         return "Seek started";
            case INFO_SEEKABLE_CHANGED:     return "Seekable changed";
            case INFO_ALTERNATE_MOUNT:      return "Redirected to an alternate mount";
            case INFO_BUFFERING_START:      return "Buffering started";
            case INFO_BUFFERING_COMPLETED:  return "Buffering ended";

            default:
                Assert.failUnhandledValue(STATIC_TAG, info, "debugInfoToStr");
                return "Unknown";
        }
    }


    /**
     * Utility method to convert the player state to a debug string.
     *
     * @copydetails debugErrorToStr(int)
     */
    public static String debugStateToStr(int state) {
        switch (state) {
            case STATE_COMPLETED:   return "Completed";
            case STATE_CONNECTING:  return "Connecting";
            case STATE_ERROR:       return "Error";
            case STATE_PAUSED:      return "Paused";
            case STATE_PLAYING:     return "Playing";
            case STATE_RELEASED:    return "Released";
            case STATE_STOPPED:     return "Stopped";
            default:
                Assert.failUnhandledValue(STATIC_TAG, state, "debugStateToStr");
                return "Unknown";
        }
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Deprecated
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * \par Deprecated
     * Use MediaPlayer::ERROR_UNEXPECTED_END_OF_MEDIA instead
     */
    @SuppressWarnings("UnusedDeclaration")
    @Deprecated
    public static final int ERROR_END_OF_MEDIA = ERROR_UNEXPECTED_END_OF_MEDIA;

    /**
     * \par Deprecated
     * Use MediaPlayer::getLastErrorCode() instead
     */
    public int getErrorCode() {
        return getLastErrorCode();
    }
}
