package com.tritondigital.player;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import androidx.annotation.NonNull;
import androidx.mediarouter.media.MediaControlIntent;
import androidx.mediarouter.media.MediaItemStatus;
import androidx.mediarouter.media.MediaRouter;
import androidx.mediarouter.media.MediaSessionStatus;
import androidx.mediarouter.media.RemotePlaybackClient;
import androidx.mediarouter.media.RemotePlaybackClient.ItemActionCallback;
import android.text.TextUtils;

import com.tritondigital.util.Assert;
import com.tritondigital.util.Log;


/**
 * Basic remote player implementation.
 *
 * Issues with bad network: https://developers.google.com/cast/docs/discovery
 *
 * TODO: playing a podcast too soon after a station change fails. No reconnection like station player does
 */
class RemotePlayer extends MediaPlayer {
    /** @copybrief PlayerConsts.STREAM_URL */
    public static final String SETTINGS_STREAM_URL = PlayerConsts.STREAM_URL;

    /** @copybrief PlayerConsts.MIME_TYPE */
    public static final String SETTINGS_STREAM_MIME_TYPE = PlayerConsts.MIME_TYPE;

    /** @copybrief PlayerConsts.MEDIA_ITEM_METADATA */
    public static final String SETTINGS_MEDIA_ITEM_METADATA = PlayerConsts.MEDIA_ITEM_METADATA;

    /** @copybrief PlayerConsts.POSITION */
    public static final String SETTINGS_POSITION = PlayerConsts.POSITION;

    private static final String TAG = Log.makeTag("RemotePlayer");

    private final Uri                   mStreamUri;
    private final Bundle                mMetadata;
    private final String                mMimeType;
    private final RemotePlaybackClient  mRemotePlaybackClient;
    private final Handler               mHandler;
    private final int                   mInitPosition;

    private String mItemId;
    private int mDuration = DURATION_UNKNOWN;
    private int mPosition = POSITION_UNKNOWN;


    public RemotePlayer(@NonNull Context context, @NonNull Bundle settings, @NonNull MediaRouter.RouteInfo route) {
        super(context, settings);

        // Stream URL
        String streamUrl = settings.getString(SETTINGS_STREAM_URL);
        if (streamUrl == null) {
            throw new IllegalArgumentException("settings's SETTINGS_STREAM_URL must not be null");
        }

        if (route == null) {
            throw new IllegalArgumentException("route must not be null");
        }

        // Other final members
        mStreamUri            = Uri.parse(streamUrl);
        mMimeType             = settings.getString(SETTINGS_STREAM_MIME_TYPE);
        mInitPosition         = settings.getInt(SETTINGS_POSITION);
        mMetadata             = settings.getBundle(SETTINGS_MEDIA_ITEM_METADATA);
        mRemotePlaybackClient = new RemotePlaybackClient(context, route);
        mHandler              = new Handler();
    }


    @Override
    protected void internalPlay() {
        if (getState() == STATE_PAUSED) {
            setState(STATE_CONNECTING);
            remoteResume();
        } else {
            setState(STATE_CONNECTING);
            enableRemotePlayCallbackWatchdog();
            mPlayTimeoutTime = SystemClock.elapsedRealtime() + PLAY_TIMEOUT;
            remotePlay();
        }
    }


    @Override
    protected void internalPause() {
        remotePause();
    }


    /** Called only when releasing the player */
    @Override
    protected void internalStop() {
        setState(STATE_STOPPED);
    }


    @Override
    protected void internalRelease() {
        remoteRelease();

        try {
            mRemotePlaybackClient.release();
        } catch (Exception e) {
            Log.e(TAG, e, "mRemotePlaybackClient.release()");
        }

        setState(STATE_RELEASED);
    }


    @Override
    protected String makeTag() { return Log.makeTag("RemotePlayer"); }

    @Override
    protected boolean isEventLoggingEnabled() { return false; }


    /**
     * Returns true if the remote playback client supports the provided routeInfo.
     *
     * This validation was taken from RemotePlaybackClient source code.
     */
    public static boolean isRouteSupported(MediaRouter.RouteInfo routeInfo) {
        return (routeInfo != null)
                && routeSupportsAction(routeInfo, MediaControlIntent.ACTION_PLAY)
                && routeSupportsAction(routeInfo, MediaControlIntent.ACTION_SEEK)
                && routeSupportsAction(routeInfo, MediaControlIntent.ACTION_GET_STATUS)
                && routeSupportsAction(routeInfo, MediaControlIntent.ACTION_PAUSE)
                && routeSupportsAction(routeInfo, MediaControlIntent.ACTION_RESUME)
                && routeSupportsAction(routeInfo, MediaControlIntent.ACTION_STOP)
                && routeSupportsAction(routeInfo, MediaControlIntent.ACTION_START_SESSION)
                && routeSupportsAction(routeInfo, MediaControlIntent.ACTION_GET_SESSION_STATUS)
                && routeSupportsAction(routeInfo, MediaControlIntent.ACTION_END_SESSION);
    }


    /**
     * Returns true if the two provided routes have the same ID.
     */
    public static boolean isSameRoute(MediaRouter.RouteInfo routeInfo0, MediaRouter.RouteInfo routeInfo1) {
        return TextUtils.equals(getRouteId(routeInfo0), getRouteId(routeInfo1));
    }


    private static boolean routeSupportsAction(MediaRouter.RouteInfo routeInfo, String action) {
        return (routeInfo != null) && routeInfo.supportsControlAction(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK, action);
    }


    private static String getRouteId(MediaRouter.RouteInfo routeInfo) {
        return (routeInfo == null) ? null : routeInfo.getId();
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Remote play
    ////////////////////////////////////////////////////////////////////////////////////////////////
    private static final int PLAY_RETRY_TIME = 1000;
    private static final int PLAY_TIMEOUT    = 10000;
    private long mPlayTimeoutTime;
    private boolean mPlayTimeoutEnabled;


    private void remotePlay() {
        if (getRequestedAction() == REQUESTED_ACTION_PLAY) {
            Log.i(TAG, "Starting stream (" + mMimeType + ")");
            Log.d(TAG, " * Url:" + mStreamUri);
            mRemotePlaybackClient.play(mStreamUri, mMimeType, mMetadata, mInitPosition, null, mRemotePlayCallback);
        }
    }


    private final Runnable mRemotePlayRunnable = new Runnable() {
        @Override
        public void run() {
            if (getRequestedAction() != REQUESTED_ACTION_RELEASE) {
                remotePlay();
            }
        }
    };


    private boolean isPlayTimeoutReached() {
        return mPlayTimeoutEnabled && (SystemClock.elapsedRealtime() < mPlayTimeoutTime);
    }


    private final ItemActionCallback mRemotePlayCallback = new ItemActionCallback() {
        @Override
        public void onResult(Bundle data, String sessionId, MediaSessionStatus sessionStatus,
                             String itemId, MediaItemStatus itemStatus) {

            mHandler.removeCallbacks(mRemotePlayWatchdogRunnable);

            switch (getRequestedAction()) {
                case REQUESTED_ACTION_PAUSE:
                case REQUESTED_ACTION_PLAY:
                    Log.i(TAG, "Play request success. ItemId: " + itemId);
                    setItemId(itemId);
                    startStatusPolling();
                    break;

                case REQUESTED_ACTION_RELEASE:
                    Log.i(TAG, "Play request success. Executing pending release request.");
                    remoteRelease();
                    break;

                case REQUESTED_ACTION_STOP:
                default:
                    Assert.failUnhandledValue(TAG, getRequestedAction(), "RemotePlayCallback");
                    break;
            }
        }


        @Override
        public void onError(String error, int code, Bundle data) {
            onRemotePlayFailed();
        }
    };


    /**
     * The play request has failed. Retry it until it works or the timeout has been reached.
     */
    private void onRemotePlayFailed() {
        switch (getRequestedAction()) {
            case REQUESTED_ACTION_PAUSE:
            case REQUESTED_ACTION_PLAY:
                setItemId(null);

                if (isPlayTimeoutReached()) {
                    Log.i(TAG, "Play request failed. Timeout reached: " + (PLAY_TIMEOUT / 1000) + "s");
                    setErrorState(ERROR_CONNECTION_TIMEOUT);
                }
                else {
                    Log.i(TAG, "Play request failed. Retry in " + (PLAY_RETRY_TIME / 1000) + "s");
                    mHandler.postDelayed(mRemotePlayRunnable, PLAY_RETRY_TIME);
                }
                break;

            case REQUESTED_ACTION_RELEASE:
                Log.i(TAG, "Play request failed. Executing a pending release request.");
                remoteRelease();
                break;

            case REQUESTED_ACTION_STOP:
            default:
                Assert.failUnhandledValue(TAG, getRequestedAction(), "onRemotePlayFailed");
                break;
        }
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Remote Play Watchdog
    //
    // The first remote play request sometimes fail when changing the station
    ////////////////////////////////////////////////////////////////////////////////////////////////
    private static final int REMOTE_PLAY_WATCHDOG_TIMEOUT = 5000;


    private void enableRemotePlayCallbackWatchdog() {
        mHandler.removeCallbacks(mRemotePlayWatchdogRunnable);
        mHandler.postDelayed(mRemotePlayWatchdogRunnable, REMOTE_PLAY_WATCHDOG_TIMEOUT);
    }


    private final Runnable mRemotePlayWatchdogRunnable = new Runnable() {
        @Override
        public void run() {
            if ((getRequestedAction() == REQUESTED_ACTION_PLAY) && (getState() != STATE_PLAYING)) {
                Log.w(TAG, "Play watchdog. Couldn't connect in " + REMOTE_PLAY_WATCHDOG_TIMEOUT / 1000 + "s.");
                onRemotePlayFailed();
            }
        }
    };


    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Item status polling
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private static final int ITEM_STATUS_POLLING_PERIOD = 500;

    /**
     * Starts and stops the status polling
     */
    private void setItemId(String itemId) {
        if (!TextUtils.equals(mItemId, itemId)) {
            Log.i(TAG, "Item ID changed: " + itemId);
            mItemId = itemId;
        }
    }


    private void startStatusPolling() {
        Log.d(TAG, "Start status polling");
        mHandler.post(mItemStatusRunnable);
    }


    private final Runnable mItemStatusRunnable = new Runnable() {
        @Override
        public void run() {
            if ((mItemId != null) && (getRequestedAction() != REQUESTED_ACTION_RELEASE)) {
                mRemotePlaybackClient.getStatus(mItemId, null, mItemStatusCallback);
            }
        }
    };


    private static final int END_OF_FILE_PROTECTION = 10000;

    private boolean nearEndOfFile() {
        return (mDuration > 0) && (mDuration < DURATION_LIVE_MIN_VALUE)
                && (mPosition >= (mDuration - END_OF_FILE_PROTECTION));
    }


    private final ItemActionCallback mItemStatusCallback = new ItemActionCallback() {
        @Override
        public void onResult(Bundle data, String sessionId, MediaSessionStatus sessionStatus,
                             String itemId, MediaItemStatus itemStatus) {

            Log.v(TAG, "Item status: " + debugItemPlaybackStateToStr(itemStatus.getPlaybackState()));
            mPosition = (int)itemStatus.getContentPosition();

            switch (getRequestedAction()) {
                case REQUESTED_ACTION_PLAY: {

                    int externalState = getState();
                    if (externalState == STATE_CONNECTING) {
                        if (itemStatus.getPlaybackState() == MediaItemStatus.PLAYBACK_STATE_PLAYING) {
                            // Remote playback started
                            mPlayTimeoutEnabled = false;
                            updateDuration(itemStatus);
                            setState(STATE_PLAYING);
                        }
                    }
                     else if (externalState == STATE_PLAYING) {
                        if (nearEndOfFile() && (itemStatus.getPlaybackState() == MediaItemStatus.PLAYBACK_STATE_BUFFERING)) {
                            setState(STATE_COMPLETED);
                            return;
                        }
                    }

                    mHandler.postDelayed(mItemStatusRunnable, ITEM_STATUS_POLLING_PERIOD);
                    break;
                }

                case REQUESTED_ACTION_PAUSE: {
                    switch (itemStatus.getPlaybackState()) {
                        case MediaItemStatus.PLAYBACK_STATE_BUFFERING:
                        case MediaItemStatus.PLAYBACK_STATE_PLAYING:
                            remotePause();
                            mHandler.postDelayed(mItemStatusRunnable, ITEM_STATUS_POLLING_PERIOD);
                            break;

                        case MediaItemStatus.PLAYBACK_STATE_PAUSED: {
                            // We stop the polling here
                            setState(STATE_PAUSED);
                            break;
                        }
                    }
                    break;
                }

                case REQUESTED_ACTION_RELEASE:
                    remoteRelease();
                    break;

                case REQUESTED_ACTION_STOP:
                default:
                    Assert.failUnhandledValue(TAG, getRequestedAction(), "ItemStatusCallback success");
                    break;
            }
        }


        @Override
        public void onError(String error, int code, Bundle data) {

            switch (getRequestedAction()) {
                case REQUESTED_ACTION_PLAY:
                    if (isPlayTimeoutReached()) {
                        Log.i(TAG, "Item status FAILED. Play timeout reached: " + (PLAY_TIMEOUT / 1000) + "s");
                        setErrorState(ERROR_CONNECTION_TIMEOUT);
                    } else {
                        Log.v(TAG, "Item status FAILED");
                        mHandler.postDelayed(mItemStatusRunnable, ITEM_STATUS_POLLING_PERIOD);
                    }
                    break;

                case REQUESTED_ACTION_RELEASE:
                    Log.d(TAG, "Item status FAILED. Release request pending");
                    remoteRelease();
                    break;

                case REQUESTED_ACTION_PAUSE:
                    Log.d(TAG, "Item status FAILED. Pause request pending");
                    remotePause();
                    break;

                case REQUESTED_ACTION_STOP:
                default:
                    Assert.failUnhandledValue(TAG, getRequestedAction(), "ItemStatusCallback failed");
                    break;
            }
        }
    };


    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Duration
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private void updateDuration(MediaItemStatus itemStatus) {
        int duration = DURATION_UNKNOWN;

        if (itemStatus != null) {
            duration = StreamPlayer.normalizeDuration(itemStatus.getContentDuration());
        }

        // Update the duration
        if (mDuration != duration) {
            mDuration = duration;
            notifyInfo(INFO_DURATION_CHANGED, mDuration);
        }
    }


    @Override
    public int getDuration() {
        return mDuration;
    }


    @Override
    public int getPosition() {
        return mPosition;
    }


    @Override
    public float getVolume() {
        return VOLUME_NORMAL;
    }


    private final RemotePlaybackClient.SessionActionCallback mEmptySessionActionCallback = new RemotePlaybackClient.SessionActionCallback() {};


    private static String debugItemPlaybackStateToStr(int state) {
        switch (state) {
            case MediaItemStatus.PLAYBACK_STATE_BUFFERING:      return "Buffering";
            case MediaItemStatus.PLAYBACK_STATE_CANCELED:       return "Canceled";
            case MediaItemStatus.PLAYBACK_STATE_ERROR:          return "Error";
            case MediaItemStatus.PLAYBACK_STATE_FINISHED:       return "Finished";
            case MediaItemStatus.PLAYBACK_STATE_INVALIDATED:    return "Invalidated";
            case MediaItemStatus.PLAYBACK_STATE_PAUSED:         return "Paused";
            case MediaItemStatus.PLAYBACK_STATE_PENDING:        return "Pending";
            case MediaItemStatus.PLAYBACK_STATE_PLAYING:        return "Playing";
            default:                                            return "Unknown";
        }
    }


    private void remotePause() {
        try {
            Log.d(TAG, "RemotePlaybackClient.pause()");
            mRemotePlaybackClient.pause(null, mEmptySessionActionCallback);
        } catch (Exception e) {
            Log.w(TAG, e, "remotePause");
        }
    }


    private void remoteResume() {
        if ((getRequestedAction() == REQUESTED_ACTION_PLAY) && (getState() == STATE_CONNECTING)) {
            try {
                Log.d(TAG, "RemotePlaybackClient.resume()");
                mRemotePlaybackClient.resume(null, mPauseCallback);
            } catch (Exception e) {
                Log.w(TAG, e, "remoteResume");
            }
        }
    }


    private final RemotePlaybackClient.SessionActionCallback mPauseCallback = new RemotePlaybackClient.SessionActionCallback() {
        @Override
        public void onResult(Bundle data, String sessionId, MediaSessionStatus sessionStatus) {
            super.onResult(data, sessionId, sessionStatus);
            startStatusPolling();
        }

        @Override
        public void onError(String error, int code, Bundle data) {
            super.onError(error, code, data);
            remoteResume();
        }
    };


    private void remoteRelease() {
        int requestedAction = getRequestedAction();
        if (requestedAction == REQUESTED_ACTION_RELEASE) {

            // End the session
            try {
                if (mRemotePlaybackClient.hasSession()) {
                    Log.d(TAG, "RemotePlaybackClient.endSession()");
                    mRemotePlaybackClient.endSession(null, mEmptySessionActionCallback);
                }
            } catch (Exception e) {
                Log.e(TAG, e, "EndSession");
            }
        }

        // Make sure all messages are removed
        setItemId(null);
        mPosition = POSITION_UNKNOWN;
        mHandler.removeCallbacks(mItemStatusRunnable);
        mHandler.removeCallbacks(mRemotePlayRunnable);
        mHandler.removeCallbacks(mRemotePlayWatchdogRunnable);
    }


    @Override
    public void setVolume(float volume) {}


    @Override
    protected void internalSeekTo(int position) {
        if ((mItemId != null) && mRemotePlaybackClient.hasSession()) {
            notifyInfo(RemotePlayer.INFO_SEEK_STARTED);

            Log.d(TAG, "RemotePlaybackClient.seek()");
            mRemotePlaybackClient.seek(mItemId, position, null, new ItemActionCallback() {
                @Override
                public void onResult(Bundle data, String sessionId, MediaSessionStatus sessionStatus,
                                     String itemId, MediaItemStatus itemStatus) {

                    mPosition = (int)itemStatus.getContentPosition();
                    notifyInfo(INFO_SEEK_COMPLETED, mPosition);
                }
            });
        }
    }
}
