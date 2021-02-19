package com.tritondigital.player;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.mediarouter.media.MediaRouter;

import com.tritondigital.util.*;

import java.io.Serializable;


/**
 * Plays a station provided by Triton Digital.
 *
 */
@SuppressWarnings("UnusedDeclaration")
public class StationPlayer extends MediaPlayer
{
    // Public settings
    public static final String SETTINGS_STATION_BROADCASTER                 = PlayerConsts.STATION_BROADCASTER;
    public static final String SETTINGS_STATION_MOUNT                       = PlayerConsts.STATION_MOUNT;
    public static final String SETTINGS_STATION_NAME                        = PlayerConsts.STATION_NAME;
    public static final String SETTINGS_TRANSPORT                           = PlayerConsts.TRANSPORT;
    public static final String SETTINGS_AUTH_TOKEN                          = PlayerConsts.AUTH_TOKEN;
    public static final String SETTINGS_TARGETING_LOCATION_TRACKING_ENABLED = PlayerConsts.TARGETING_LOCATION_TRACKING_ENABLED;
    public static final String SETTINGS_TARGETING_PARAMS                    = PlayerConsts.TARGETING_PARAMS;
    public static final String SETTINGS_MEDIA_ITEM_METADATA                 = PlayerConsts.MEDIA_ITEM_METADATA;
    public static final String SETTINGS_LOW_DELAY                           = PlayerConsts.LOW_DELAY;
    public static final String SETTINGS_TTAGS                               = PlayerConsts.TTAGS;


    private static final String TAG = Log.makeTag("StationPlayer");

    private StreamPlayer            mStreamPlayer;
    private StationConnectionClient mConnectionClient;
    private boolean                 mResetConnectionClient;
    private String                  mUserAgent;
    private MediaRouter.RouteInfo   mMediaRoute;
    private String                  mLiveStreamingUrl;

    /**
     * Constructor
     */
    public StationPlayer(@NonNull Context context, @NonNull Bundle settings) {
        super(context, settings);
        initUserAgent();
    }


    @Override
    protected void internalPlay() {
        setState(STATE_CONNECTING);

        if (!NetworkUtil.isNetworkConnected(getContext())) {
            setErrorState(ERROR_NO_NETWORK);
            return;
        }

        if (mResetConnectionClient || (mConnectionClient == null)) {
            mResetConnectionClient = false;
            cancelConnectionClient();
            createConnectionClient();
        }

        mConnectionClient.start();
    }


    @Override
    public boolean isPausable() {
        return false;
    }


    // Won't be called because isPausable() --> false
    @Override
    protected void internalPause() {}


    @Override
    protected void internalStop() {
        cancelConnectionClient();
        releaseStreamPlayer();
        setState(STATE_STOPPED);
    }


    @Override
    protected void internalRelease() {
        cancelConnectionClient();
        releaseStreamPlayer();
        setState(STATE_RELEASED);
    }


    @Override
    protected String makeTag() {
        return Log.makeTag("StationPlayer");
    }


    @Override
    protected boolean isEventLoggingEnabled() {
        return false;
    }


    /**
     * Returns the alternate mount used if the station is using content blocking.
     * The alternate mount is known only after a play request has been made.
     */
    public String getAlternateMount() {
        return mConnectionClient.getAlternateMount();
    }

    /**
     * Returns the Side Band MetaData Url.
     */
    public String getSideBandMetadataUrl()
    {
        return mConnectionClient.getSideBandMetadataUrl();
    }

    /**
     * Returns the Stream URL to cast to Google Cast devices.
     */
    public String getCastStreamingUrl(){
        String castUrl = mConnectionClient.getCastStreamingUrl();
        if(castUrl == null)
        {
            return null;
        }

        if(mLiveStreamingUrl != null)
        {
            Uri uri = Uri.parse(mLiveStreamingUrl);
            Uri sbmUri = Uri.parse(getSideBandMetadataUrl());
            String sbmId = (sbmUri==null)?null: sbmUri.getQueryParameter("sbmid");

            Uri.Builder uriBuilder =  Uri.parse(castUrl).buildUpon();
            uriBuilder.query(uri.getQuery());
            if(sbmId != null)
            {
                uriBuilder.appendQueryParameter("sbmid", sbmId);
            }
            return uriBuilder.build().toString();
        }

        return castUrl ;
    }


    private void createConnectionClient() {
        Bundle connectionClientSettings = new Bundle(getSettings());
        connectionClientSettings.putString(StationConnectionClient.SETTINGS_USER_AGENT, mUserAgent);

        if (mMediaRoute != null) {
            // Use SHOUTCast for Google Cast
            String transport = PlayerConsts.TRANSPORT_SC;
            connectionClientSettings.putString(StationConnectionClient.SETTINGS_TRANSPORT, transport);
        }

        mConnectionClient = new StationConnectionClient(getContext(), connectionClientSettings, mConnectionClientListener);
        mConnectionClient.setTag(TAG);
    }


    private void cancelConnectionClient() {
        if (mConnectionClient != null) {
            mConnectionClient.cancel();
        }
    }


    final StationConnectionClient.Listener mConnectionClientListener = new StationConnectionClient.Listener() {
        @Override
        public void onStationConnectionError(StationConnectionClient src, int errorCode) {
            setErrorState(errorCode);
        }


        @Override
        public void onStationConnectionNextStream(StationConnectionClient src, Bundle streamSettings) {

            if (getRequestedAction() == MediaPlayer.REQUESTED_ACTION_PLAY) {
                if (getAlternateMount() != null) {
                    notifyInfo(INFO_ALTERNATE_MOUNT);
                }
                // Add extra stream info
                Bundle stationSettings = getSettings();
                boolean locationTrackingEnabled = stationSettings.getBoolean(SETTINGS_TARGETING_LOCATION_TRACKING_ENABLED);
                Serializable targetingParams    = stationSettings.getSerializable(SETTINGS_TARGETING_PARAMS);
                String authToken                = stationSettings.getString(SETTINGS_AUTH_TOKEN);
                Bundle metaData                 = stationSettings.getBundle(SETTINGS_MEDIA_ITEM_METADATA);
                String mount                    = stationSettings.getString(SETTINGS_STATION_MOUNT);
                Integer lowDelay                = stationSettings.getInt(SETTINGS_LOW_DELAY, 0);
                String[] tTags                  = stationSettings.getStringArray(SETTINGS_TTAGS);
                boolean disableExoPlayer        = stationSettings.getBoolean(PlayerConsts.FORCE_DISABLE_EXOPLAYER, false);

                streamSettings.putBoolean(StreamPlayer.SETTINGS_TARGETING_LOCATION_TRACKING_ENABLED, locationTrackingEnabled);
                streamSettings.putSerializable(StreamPlayer.SETTINGS_TARGETING_PARAMS, targetingParams);
                streamSettings.putString(StreamPlayer.SETTINGS_AUTH_TOKEN, authToken);
                streamSettings.putString(StreamPlayer.SETTINGS_USER_AGENT, mUserAgent);
                streamSettings.putBundle(StreamPlayer.SETTINGS_MEDIA_ITEM_METADATA, metaData);
                streamSettings.putString(StreamPlayer.SETTINGS_STATION_MOUNT, mount);
                streamSettings.putInt(StreamPlayer.SETTINGS_LOW_DELAY, lowDelay);
                streamSettings.putBoolean(PlayerConsts.FORCE_DISABLE_EXOPLAYER, disableExoPlayer);

                //update transport on stationSettings
                String transport = streamSettings.getString(SETTINGS_TRANSPORT);
                if(transport != null)
                {
                    getSettings().putString(SETTINGS_TRANSPORT, transport);
                }

                if ( tTags != null )
                    streamSettings.putStringArray(StreamPlayer.SETTINGS_TTAGS, tTags);

                mStreamPlayer = new StreamPlayer(getContext(), streamSettings);
                mStreamPlayer.setMediaRoute(mMediaRoute);
                mStreamPlayer.setOnCuePointReceivedListener(mStreamPlayerCuePointListener);
                mStreamPlayer.setOnInfoListener(mStreamPlayerOnInfoListener);
                mStreamPlayer.setOnStateChangedListener(mStreamPlayerStateChangeListener);
                mStreamPlayer.play();

                mLiveStreamingUrl = mStreamPlayer.getSettings().getString(StreamPlayer.SETTINGS_STREAM_URL);
            }
        }
    };


    ////////////////////////////////////////////////////////////////////////////////////////////////
    // StreamPlayer
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public int getDuration() {
        return DURATION_LIVE_STREAM;
    }


    @Override
    public int getPosition() {
        return (mStreamPlayer == null) ? POSITION_UNKNOWN : mStreamPlayer.getPosition();
    }


    @Override
    public boolean isSeekable() {
        return false;
    }


    @Override
    protected void internalSeekTo(int position) {}


    @Override
    public float getVolume() {
        return (mStreamPlayer == null) ? VOLUME_NORMAL : mStreamPlayer.getVolume();
    }


    @Override
    public void setVolume(float volume) {
        if (mStreamPlayer != null) {
            mStreamPlayer.setVolume(volume);
        }
    }


    private void releaseStreamPlayer() {
        if (mStreamPlayer != null) {
            mStreamPlayer.release();
            mStreamPlayer = null;
        }
    }


    private final OnStateChangedListener mStreamPlayerStateChangeListener = new OnStateChangedListener() {
        @Override
        public void onStateChanged(MediaPlayer player, int state) {
            if ((mStreamPlayer != player) || (getRequestedAction() == REQUESTED_ACTION_RELEASE)) {
                return;
            }

            switch (state) {
                case STATE_PLAYING:
                    setState(STATE_PLAYING);
                    break;

                case STATE_ERROR: {
                    int stationPlayerState = StationPlayer.this.getState();

                    if (stationPlayerState == STATE_CONNECTING) {
                        releaseStreamPlayer();
                        mConnectionClient.notifyConnectionFailed();

                    } else if (stationPlayerState == STATE_PLAYING) {
                        setErrorState(player.getLastErrorCode());

                        // Restart provisioning when stream disconnected during playback
                        releaseStreamPlayer();
                        internalPlay();

                    } else {
                        Log.e(TAG, "Received a StreamPlayer error while StationPlayer was neither in CONNECTING or PLAYING state.");
                    }

                    break;
                }
            }
        }
    };


    private final OnCuePointReceivedListener mStreamPlayerCuePointListener = new OnCuePointReceivedListener() {
        @Override
        public void onCuePointReceived(MediaPlayer player, Bundle cuePoint) {
            notifyCuePoint(cuePoint);
        }
    };


    private final OnInfoListener mStreamPlayerOnInfoListener = new OnInfoListener() {
        @Override
        public void onInfo(MediaPlayer player, int info, int extra) {
            notifyInfo(info, extra);
        }
    };


    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Media Router
    ////////////////////////////////////////////////////////////////////////////////////////////////

    public void setMediaRoute(MediaRouter.RouteInfo routeInfo) {
        if (!RemotePlayer.isRouteSupported(routeInfo)) {
            // Merge all unsupported routes to "null"
            routeInfo = null;
        }

        if ( !RemotePlayer.isSameRoute(mMediaRoute, routeInfo)) {
            mMediaRoute = routeInfo;
            mResetConnectionClient = true;

            // Restart playback on the new route
            internalStop();
            if (getRequestedAction() == REQUESTED_ACTION_PLAY) {
                play();
            }
        }
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    // User Agent
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Returns the user agent of the media player.
     */
    private void initUserAgent() {
        final String broadcaster = getUserAgentMandatoryField(SETTINGS_STATION_BROADCASTER);
        final String station     = getUserAgentMandatoryField(SETTINGS_STATION_NAME);
        final String appVersion  = userAgentStrip(getAppVersion(getContext()));
        final String osVersion   = userAgentStrip(android.os.Build.VERSION.RELEASE);
        final String deviceName  = userAgentStrip(android.os.Build.MANUFACTURER + '-' + android.os.Build.MODEL);

        mUserAgent = "CustomPlayer1/" + appVersion + " Android/" + osVersion + ' ' + deviceName + ' ' + broadcaster + '/' + station + " TdSdk/android-" + SdkUtil.VERSION;
        Log.i(TAG, "User agent: " + mUserAgent);
    }


    private String getUserAgentMandatoryField(String key) {
        String value = userAgentStrip(getSettings().getString(key));
        if ((value == null) || value.isEmpty()) {
            throw new IllegalArgumentException("Missing argument: " + key);
        } else {
            return userAgentStrip(value);
        }
    }


    private static String userAgentStrip(String token) {
        return (token == null) ? null : token.replaceAll("[^0-9a-zA-Z.,-]", "");
    }


    private static String getAppVersion(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return info.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, e, "getAppVersion() exception");
        }

        return null;
    }
}
