package com.tritondigital.player;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.media.MediaRouter;
import android.text.TextUtils;

import com.tritondigital.util.*;

import java.util.HashMap;
import java.util.Map;


/**
 * Low level streaming player.
 *
 * A new instance must be created in order to change the URL.
 *
 * Supported cue points:
 * - FLV stream
 * - Side-Band Metadata
 */
// TODO: keep the volume when changing the station
public class StreamPlayer extends MediaPlayer {

    public static final String SETTINGS_AUTH_TOKEN                          = PlayerConsts.AUTH_TOKEN;
    public static final String SETTINGS_MEDIA_ITEM_METADATA                 = PlayerConsts.MEDIA_ITEM_METADATA;
    public static final String SETTINGS_STATION_MOUNT                       = PlayerConsts.STATION_MOUNT;
    public static final String SETTINGS_SBM_URL                             = PlayerConsts.SBM_URL;
    public static final String SETTINGS_TARGETING_LOCATION_TRACKING_ENABLED = PlayerConsts.TARGETING_LOCATION_TRACKING_ENABLED;
    public static final String SETTINGS_TARGETING_PARAMS                    = PlayerConsts.TARGETING_PARAMS;
    public static final String SETTINGS_STREAM_MIME_TYPE                    = PlayerConsts.MIME_TYPE;
    public static final String SETTINGS_STREAM_POSITION                     = PlayerConsts.POSITION;
    public static final String SETTINGS_STREAM_URL                          = PlayerConsts.STREAM_URL;
    public static final String SETTINGS_TRANSPORT                           = PlayerConsts.TRANSPORT;
    public static final String SETTINGS_LOW_DELAY                           = PlayerConsts.LOW_DELAY; //-1 (AUTO), 0 (DISABLED), 1 - 60 for seconds
    public static final String SETTINGS_TTAGS                               = PlayerConsts.TTAGS;

    private static  final String USE_EXOPLAYER                              ="UseExoPlayer";

    // Other Settings
    public static final String SETTINGS_USER_AGENT = PlayerConsts.USER_AGENT;

    private StreamUrlBuilder      mUrlBuilder;
    private Bundle                mLowLevelPlayerSettings;
    private MediaRouter.RouteInfo mMediaRoute;
    private MediaPlayer           mAndroidPlayer;
    private RemotePlayer          mRemotePlayer;
    private SbmPlayer             mSbmPlayer;
    private boolean               mSeekableCache;
    private boolean               mBuffering;
    private int                   mRestorePosition;

    /**
     * Constructor
     */
    @SuppressWarnings("unchecked")
    public StreamPlayer(@NonNull Context context, @NonNull Bundle settings) {
        super(context, settings);

        // Validate the URL
        String streamUrl = settings.getString(SETTINGS_STREAM_URL);
        if (streamUrl == null) {
            throw new IllegalArgumentException("settings's SETTINGS_STREAM_URL must not be null");
        }

        // MIME type
        String mimeType = settings.getString(SETTINGS_STREAM_MIME_TYPE);
        if (TextUtils.isEmpty(mimeType)) {
            // Deduced from URL
            mimeType = PlayerUtil.fileNameToMimeType(streamUrl);
            Log.i(TAG, "SETTINGS_STREAM_MIME_TYPE not set. Deduced from URL: " + mimeType);
            settings.putString(SETTINGS_STREAM_MIME_TYPE, mimeType);
        } else {
            // Normalize the mime type to avoid the risk of errors
            mimeType = PlayerUtil.normalizeMimeType(mimeType);
            settings.putString(SETTINGS_STREAM_MIME_TYPE, mimeType);
        }

        // Transport
        String transport = settings.getString(SETTINGS_TRANSPORT);
        if (TextUtils.isEmpty(transport)) {
            transport = PlayerUtil.fileNameToTransport(streamUrl);
            Log.i(TAG, "SETTINGS_TRANSPORT not set. Deduced from URL: " + transport);
            settings.putString(SETTINGS_TRANSPORT, transport);
        } else {
            // Normalize the mime type to avoid the risk of errors
            transport = PlayerUtil.normalizeTransport(transport);
            settings.putString(SETTINGS_TRANSPORT, transport);
        }

        boolean forceDisableExoPlayer = settings.getBoolean(PlayerConsts.FORCE_DISABLE_EXOPLAYER, false);

        boolean useExoPlayer = forceDisableExoPlayer? false: isExoPlayerPackageInClassPath();

        settings.putBoolean(USE_EXOPLAYER, useExoPlayer);

        // Init Url Builder
        boolean locationTrackingEnabled = settings.getBoolean(SETTINGS_TARGETING_LOCATION_TRACKING_ENABLED);
        HashMap<String, String> params  = (HashMap<String, String>) settings.getSerializable(SETTINGS_TARGETING_PARAMS);
        String authToken                = settings.getString(SETTINGS_AUTH_TOKEN);

        if (locationTrackingEnabled || (params != null) || !TextUtils.isEmpty(authToken) || isTritonUrl(streamUrl)) {
            mUrlBuilder = new StreamUrlBuilder(getContext())
                    .setHost(streamUrl)
                    .enableLocationTracking(locationTrackingEnabled);

            // Append the targeting params to those contained in the provided URL
            if (params != null) {
                for (Map.Entry<String, String> entry : params.entrySet()) {
                    mUrlBuilder.addQueryParameter(entry.getKey(), entry.getValue());
                }
            }

            // Add token to the parameters
            if (!TextUtils.isEmpty(authToken)) {
                mUrlBuilder.addQueryParameter(StreamUrlBuilder.AUTH_TOKEN, authToken);
            }
        }

        // We can do a dummy copy because the settings constant values are the same
        mLowLevelPlayerSettings = new Bundle(settings);
    }


    @Override
    protected void internalPlay() {
        setState(STATE_CONNECTING);
        String streamUrl = getSettings().getString(SETTINGS_STREAM_URL);

        // Network error check
        if (streamUrl.startsWith("http") && !NetworkUtil.isNetworkConnected(getContext())) {
            setErrorState(ERROR_NO_NETWORK);
            return;
        }

        // Create the low level player if needed
        createLowLevelPlayerIfNeeded();
        acquireWifiLock();

        // Start the playback
        if      (mAndroidPlayer != null) { mAndroidPlayer.play(); }
        else if (mRemotePlayer  != null) { mRemotePlayer.play(); }
        else {
            Assert.fail(TAG, "A low level player should exist");
        }
    }


    @Override
    protected void internalPause() {
        if      (mAndroidPlayer != null) { mAndroidPlayer.pause(); }
        else if (mRemotePlayer  != null) { mRemotePlayer.pause(); }
        setState(STATE_PAUSED);
    }


    @Override
    protected void internalStop() {
        releaseLowLevelPlayer();
        setState(STATE_STOPPED);
        verifySeekableChanged();
    }


    @Override
    protected void internalRelease() {
        releaseLowLevelPlayer();
        setState(STATE_RELEASED);
    }


    @Override
    protected void internalSeekTo(int position) {
        if      (mAndroidPlayer != null) { mAndroidPlayer.seekTo(position); }
        else if (mRemotePlayer  != null) { mRemotePlayer.seekTo(position); }
    }


    @Override
    public int getDuration() {
        if      (mAndroidPlayer != null) { return mAndroidPlayer.getDuration(); }
        else if (mRemotePlayer  != null) { return mRemotePlayer.getDuration(); }
        else                             { return DURATION_UNKNOWN; }
    }


    @Override
    public int getPosition() {
        if      (mAndroidPlayer != null) { return mAndroidPlayer.getPosition(); }
        else if (mRemotePlayer  != null) { return mRemotePlayer.getPosition(); }
        else                             { return POSITION_UNKNOWN; }
    }


    @Override
    public float getVolume() {
        if      (mAndroidPlayer != null) { return mAndroidPlayer.getVolume(); }
        else if (mRemotePlayer  != null) { return mRemotePlayer.getVolume(); }
        else                             { return VOLUME_NORMAL; }
    }

    @Override
    public void setVolume(float volume) {
        if      (mAndroidPlayer != null) { mAndroidPlayer.setVolume(volume); }
        else if (mRemotePlayer  != null) { mRemotePlayer.setVolume(volume); }
    }


    @Override
    protected String makeTag() {
        return Log.makeTag("StreamPlayer");
    }


    @Override
    protected boolean isEventLoggingEnabled() {
        return false;
    }


    private static boolean isTritonUrl(String url) {
        return (url != null)
                && (url.contains("streamtheworld.")
                ||  url.contains("tritondigital.")
                ||  url.contains("triton."));
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Low level players
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private void createLowLevelPlayerIfNeeded() {
        if ((mAndroidPlayer == null) && (mRemotePlayer == null)) {

            // Initial position
            mLowLevelPlayerSettings.putInt(SETTINGS_STREAM_POSITION, mRestorePosition);

            // Update the stream URL
            if (mUrlBuilder != null) {
                String sbmId = createSbmPlayerIfNeeded();
                mUrlBuilder.addQueryParameter("sbmid", sbmId);

                String newUrl = mUrlBuilder.build();

                String[] tTags = mLowLevelPlayerSettings.getStringArray(SETTINGS_TTAGS);
                if ( tTags != null && tTags.length >0)
                {
                    String allTtags  = TextUtils.join(",",tTags);
                    newUrl = newUrl + "&ttag=" + allTtags;
                }

                mLowLevelPlayerSettings.putString(SETTINGS_STREAM_URL, newUrl);
            }

            MediaPlayer lowLevelPlayer;
            if (mMediaRoute == null) {
                boolean userExoPlayer   = mLowLevelPlayerSettings.getBoolean(USE_EXOPLAYER, false);
                if ( userExoPlayer && (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH) )
                    lowLevelPlayer = mAndroidPlayer = new TdExoPlayer(getContext(), mLowLevelPlayerSettings);
                else
                    lowLevelPlayer = mAndroidPlayer = new AndroidPlayer(getContext(), mLowLevelPlayerSettings);
            } else {
                lowLevelPlayer = mRemotePlayer = new RemotePlayer(getContext(), mLowLevelPlayerSettings, mMediaRoute);
            }

            lowLevelPlayer.setOnInfoListener(mInputOnInfoListener);
            lowLevelPlayer.setOnCuePointReceivedListener(mInputCuePointListener);
            lowLevelPlayer.setOnMetaDataReceivedListener(mInputMetaDataListener);
            lowLevelPlayer.setOnStateChangedListener(mInputOnStateChangedListener);
        }
    }


    private void releaseLowLevelPlayer() {
        if (mSbmPlayer != null) {
            mSbmPlayer.release();
            mSbmPlayer = null;
        }

        if (mAndroidPlayer != null) {
            mAndroidPlayer.release();
            mAndroidPlayer = null;
        } else if (mRemotePlayer != null) {
            mRemotePlayer.release();
            mRemotePlayer = null;
        }

        releaseWifiLock();
    }


    private final OnInfoListener mInputOnInfoListener = new OnInfoListener() {
        @Override
        public void onInfo(MediaPlayer player, int info, int extra) {
            if ((player == mAndroidPlayer) || (player == mRemotePlayer)) {

                switch (info) {
                    case INFO_BUFFERING_COMPLETED:
                        setBuffering(false);
                        break;

                    case INFO_BUFFERING_START:
                        setBuffering(true);
                        break;

                    case INFO_DURATION_CHANGED:
                        notifyInfo(info, extra);
                        verifySeekableChanged();
                        break;

                    default:
                        notifyInfo(info, extra);
                        break;
                }
            }
        }
    };


    private void verifySeekableChanged() {
        boolean seekable = isSeekable();
        if (mSeekableCache != seekable) {
            mSeekableCache = seekable;
            notifyInfo(INFO_SEEKABLE_CHANGED, (seekable ? 1 : 0));
        }
    }


    private void setBuffering(boolean buffering) {
        if (mBuffering != buffering) {
            mBuffering = buffering;
            notifyInfo(buffering ? INFO_BUFFERING_START : INFO_BUFFERING_COMPLETED);
        }
    }


    private final OnCuePointReceivedListener mInputCuePointListener = new OnCuePointReceivedListener() {
        @Override
        public void onCuePointReceived(MediaPlayer player, Bundle cuePoint) {
            if ((player == mAndroidPlayer) || (player == mRemotePlayer) || (player == mSbmPlayer)) {
                notifyCuePoint(cuePoint);
            }
        }
    };


    private final OnMetaDataReceivedListener mInputMetaDataListener = new OnMetaDataReceivedListener() {
        @Override
        public void onMetaDataReceived(MediaPlayer player, Bundle metadata) {
            if (player == mAndroidPlayer) {
                notifyMetadata(metadata);
            }
        }
    };


    private final OnStateChangedListener mInputOnStateChangedListener = new OnStateChangedListener() {
        @Override
        public void onStateChanged(MediaPlayer player, int state) {
            if ((player == mAndroidPlayer) || (player == mRemotePlayer)) {
                switch (state) {
                    case STATE_COMPLETED:
                    case STATE_CONNECTING:
                    case STATE_PAUSED:
                        setState(state);
                        break;

                    case STATE_PLAYING:
                        startSbmPlayer();
                        setState(state);
                        break;

                    case STATE_ERROR:
                        setErrorState(player.getLastErrorCode());
                        verifySeekableChanged();
                        setBuffering(false);
                        break;

                    case STATE_STOPPED:
                    case STATE_RELEASED:
                        setState(STATE_STOPPED);
                        verifySeekableChanged();
                        setBuffering(false);
                        break;

                    default:
                        Assert.failUnhandledValue(TAG, debugStateToStr(state), "mInputOnStateChangedListener");
                        break;
                }
            }
        }
    };


    ////////////////////////////////////////////////////////////////////////////////////////////////
    // WIFI lock
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private WifiManager.WifiLock mWifiLock;


    private void acquireWifiLock() {
        if (mWifiLock == null) {
            try {
                WifiManager wifiManager = (WifiManager) getContext().getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                mWifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL, TAG);
                mWifiLock.acquire();
            } catch (Exception e) {
                Log.e(TAG, e, "acquireWifiLock()");
            }
        }
    }


    private void releaseWifiLock() {
        while ((mWifiLock != null) && mWifiLock.isHeld()) {
            mWifiLock.release();
            mWifiLock = null;
        }
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Side-Band Player
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private String createSbmPlayerIfNeeded() {
        String sbmUrl = getSettings().getString(SETTINGS_SBM_URL);
        String sbmId = null;

        if (!TextUtils.isEmpty(sbmUrl)) {
            char argPrefix = sbmUrl.contains("?") ? '&' : '?';
            sbmId = SbmPlayer.generateSbmId();
            sbmUrl += argPrefix + "sbmid=" + sbmId;

            Bundle sbmPlayerSettings = new Bundle();
            sbmPlayerSettings.putString(SbmPlayer.SETTINGS_SBM_URL, sbmUrl);
            mSbmPlayer = new SbmPlayer(getContext(), sbmPlayerSettings);
            mSbmPlayer.setOnCuePointReceivedListener(mInputCuePointListener);
        }

        return sbmId;
    }

    private void startSbmPlayer() {
        if (mSbmPlayer != null) {
            mSbmPlayer.play();
        }
    }



    private boolean isExoPlayerPackageInClassPath()
    {
        boolean present = false;
        try {
             Class.forName("com.google.android.exoplayer2.ExoPlayer");
            present = true;
        }
        catch (ClassNotFoundException ex)
        {
          present= false;
        }
        return present;
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Media Router
    ////////////////////////////////////////////////////////////////////////////////////////////////

    public void setMediaRoute(MediaRouter.RouteInfo routeInfo) {

        // Merge all unsupported routes to "null"
        if (!RemotePlayer.isRouteSupported(routeInfo)) {
            routeInfo = null;
        }

        if (!RemotePlayer.isSameRoute(mMediaRoute, routeInfo)) {
            mMediaRoute = routeInfo;
            mRestorePosition = isSeekable()? getPosition() : 0;

            // Resume playback on the new route
            internalStop();
            if (getRequestedAction() == REQUESTED_ACTION_PLAY) {
                play();
            }
        }
    }
}
