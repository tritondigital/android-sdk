package com.tritondigital.player;

import static com.tritondigital.player.StationConnectionClient.SETTINGS_USER_AGENT;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.mediarouter.media.MediaRouter;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import androidx.media3.common.Format;
import com.tritondigital.util.Log;
import com.tritondigital.util.NetworkUtil;
import com.tritondigital.util.SdkUtil;

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
    public static final String SETTINGS_AUTH_SECRET_KEY                     = PlayerConsts.AUTH_SECRET_KEY;
    public static final String SETTINGS_AUTH_KEY_ID                         = PlayerConsts.AUTH_KEY_ID;
    public static final String SETTINGS_AUTH_USER_ID                         = PlayerConsts.AUTH_USER_ID;
    public static final String SETTINGS_AUTH_REGISTERED_USER                 = PlayerConsts.AUTH_REGISTERED_USER;
    public static final String SETTINGS_TARGETING_LOCATION_TRACKING_ENABLED = PlayerConsts.TARGETING_LOCATION_TRACKING_ENABLED;
    public static final String SETTINGS_TARGETING_PARAMS                    = PlayerConsts.TARGETING_PARAMS;
    public static final String SETTINGS_MEDIA_ITEM_METADATA                 = PlayerConsts.MEDIA_ITEM_METADATA;
    public static final String SETTINGS_LOW_DELAY                           = PlayerConsts.LOW_DELAY;
    public static final String SETTINGS_TTAGS                               = PlayerConsts.TTAGS;
    public static final String SETTINGS_DMP_SEGMENTS                        = PlayerConsts.DMP_SEGMENTS;


    private static final String TAG = Log.makeTag("StationPlayer");

    private StreamPlayer            mStreamPlayer;
    private StationConnectionClient mConnectionClient;
    private boolean                 mResetConnectionClient;
    private String                  mUserAgent;
    private MediaRouter.RouteInfo   mMediaRoute;
    private String                  mLiveStreamingUrl;
    private boolean                 timeshiftStreaming = false;

    private int originalSeekValue;

    private static final String DOMAIN_NAME_PROD = "example.com";
    private static final String DOMAIN_NAME_PREPROD = "example.prepord.net";

    /**
     * Constructor
     */
    public StationPlayer(@NonNull Context context, @NonNull Bundle settings) {
        super(context, settings);
        initUserAgent();
    }

    @Override
    protected void internalPlay() {
       internalPlay(false);
    }

    @Override
    protected void internalPlay( boolean timeshiftStreaming ) {
        if(this.timeshiftStreaming && getState() == STATE_PAUSED){
            mStreamPlayer.internalPlay(true);
        }else{
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
    }


    @Override
    public boolean isPausable() {
        return timeshiftStreaming;
    }


    // Will be called if we are in timeshift mode
    @Override
    protected void internalPause() {
        if(timeshiftStreaming){
            mStreamPlayer.internalPause();
        }
    }


    @Override
    protected void internalStop() {
        cancelConnectionClient();
        releaseStreamPlayer();
        setState(STATE_STOPPED);
    }

    @Override
    protected void internalChangeSpeed(Float speed) {
        mStreamPlayer.internalChangeSpeed(speed);
    }

    @Override
    protected void internalRelease() {
        cancelConnectionClient();
        releaseStreamPlayer();
        setState(STATE_RELEASED);
    }

    @Override
    protected void internalGetCloudStreamInfo() {
        getCloudStreamInfoAndNotify();
    }

    @Override
    protected void internalPlayProgram(String programId) {
        internalStop();
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

        mConnectionClient.setProgramId(programId);
        mConnectionClient.start();

        timeshiftStreaming = true;

    }

    public void getCloudStreamInfoAndNotify(){
        RequestQueue queue = Volley.newRequestQueue(getContext());
        String mount = getSettings().getString(SETTINGS_STATION_MOUNT);
        String programUrl = String.format("https://%s/api/cloud-redirect/%s/stream-info", DOMAIN_NAME_PROD, mount);

        try {
            if (mount.indexOf('.') != -1) {
                String[] mountArgs = mount.split("\\.");
                switch (mountArgs[1].toLowerCase()) {
                    case "preprod": programUrl = String.format("https://%s/api/cloud-redirect/%s/stream-info", DOMAIN_NAME_PREPROD, mountArgs[0]); break;
                }
            }

            StringRequest stringRequest = new StringRequest(Request.Method.GET, programUrl,
                    new Response.Listener<String>() {
                        @Override
                        public void onResponse(String response) {
                            notifyCloudStreamInfo(response);
                        }
                    }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    notifyCloudStreamInfo("{\"error\":\"Could not get the cloud stream info\"}");
                    Log.e(TAG, error);
                }
            });
            queue.add(stringRequest);
        } catch (Exception e) {
            notifyCloudStreamInfo("{\"error\":\"Could not get the cloud stream info\"}");
            Log.e(TAG, e);
        }
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
        connectionClientSettings.putString(SETTINGS_USER_AGENT, mUserAgent);

        connectionClientSettings.putInt(PlayerConsts.ORIGINAL_SEEK_VALUE, this.originalSeekValue);
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
                String authSecretKey                = stationSettings.getString(SETTINGS_AUTH_SECRET_KEY);
                String authKeyId                = stationSettings.getString(SETTINGS_AUTH_KEY_ID);
                String authUserId               = stationSettings.getString(SETTINGS_AUTH_USER_ID);
                boolean authRegisteredUser      = stationSettings.getBoolean(SETTINGS_AUTH_REGISTERED_USER);
                Bundle metaData                 = stationSettings.getBundle(SETTINGS_MEDIA_ITEM_METADATA);
                String mount                    = stationSettings.getString(SETTINGS_STATION_MOUNT);
                Integer lowDelay                = stationSettings.getInt(SETTINGS_LOW_DELAY, 0);
                String[] tTags                  = stationSettings.getStringArray(SETTINGS_TTAGS);
                boolean disableExoPlayer        = stationSettings.getBoolean(PlayerConsts.FORCE_DISABLE_EXOPLAYER, false);
                Serializable dmpSegments        = stationSettings.getSerializable(SETTINGS_DMP_SEGMENTS);

                streamSettings.putBoolean(StreamPlayer.SETTINGS_TARGETING_LOCATION_TRACKING_ENABLED, locationTrackingEnabled);
                streamSettings.putSerializable(StreamPlayer.SETTINGS_TARGETING_PARAMS, targetingParams);
                streamSettings.putString(StreamPlayer.SETTINGS_AUTH_TOKEN, authToken);
                streamSettings.putString(StreamPlayer.SETTINGS_AUTH_SECRET_KEY, authSecretKey );
                streamSettings.putBoolean(StreamPlayer.SETTINGS_AUTH_REGISTERED_USER, authRegisteredUser);
                streamSettings.putString(StreamPlayer.SETTINGS_AUTH_USER_ID, authUserId);
                streamSettings.putString(StreamPlayer.SETTINGS_AUTH_KEY_ID, authKeyId );
                streamSettings.putString(StreamPlayer.SETTINGS_USER_AGENT, mUserAgent);
                streamSettings.putBundle(StreamPlayer.SETTINGS_MEDIA_ITEM_METADATA, metaData);
                streamSettings.putString(StreamPlayer.SETTINGS_STATION_MOUNT, mount);
                streamSettings.putInt(StreamPlayer.SETTINGS_LOW_DELAY, lowDelay);
                streamSettings.putBoolean(PlayerConsts.FORCE_DISABLE_EXOPLAYER, disableExoPlayer);
                streamSettings.putSerializable(StreamPlayer.SETTINGS_DMP_SEGMENTS, dmpSegments);

                //update transport on stationSettings
                String transport = streamSettings.getString(SETTINGS_TRANSPORT);
                if(transport != null)
                {
                    getSettings().putString(SETTINGS_TRANSPORT, transport);
                }

                if ( tTags != null )
                    streamSettings.putStringArray(StreamPlayer.SETTINGS_TTAGS, tTags);

                mStreamPlayer = new StreamPlayer(getContext(), streamSettings, timeshiftStreaming);
                mStreamPlayer.setMediaRoute(mMediaRoute);
                mStreamPlayer.setOnCuePointReceivedListener(mStreamPlayerCuePointListener);
                mStreamPlayer.setOnInfoListener(mStreamPlayerOnInfoListener);
                mStreamPlayer.setOnStateChangedListener(mStreamPlayerStateChangeListener);
                mStreamPlayer.setOnCloudStreamInfoReceivedListener(mClousStreamInfoReceivedListener);
                mStreamPlayer.play(timeshiftStreaming);

                mLiveStreamingUrl = mStreamPlayer.getSettings().getString(StreamPlayer.SETTINGS_STREAM_URL);
            }
        }

        @Override
        public void onStationConnectionError(StationConnectionClient src, int errorCode) {
            //TODO
        }
    };

    public boolean isTimeshiftStreaming() {
        return timeshiftStreaming;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // StreamPlayer
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public int getDuration() {
        return (mStreamPlayer == null) ? DURATION_LIVE_STREAM : mStreamPlayer.getDuration();
    }


    @Override
    public int getPosition() {
        return (mStreamPlayer == null) ? POSITION_UNKNOWN : mStreamPlayer.getPosition();
    }


    @Override
    public boolean isSeekable() {
        return (mStreamPlayer == null) ? false : mStreamPlayer.isSeekable();
    }


    @Override
    protected void internalSeekTo(int position, int original) {
        if (mStreamPlayer != null) {
            if(timeshiftStreaming && position != 0) {
                mStreamPlayer.seekTo(position, original);
            }else if(timeshiftStreaming && position == 0){
                switchToLiveStreaming();
            }else{
                switchToCloudStreaming(original);
        }
        }
    }


    private void switchToCloudStreaming(int originalSeekValue){
        this.originalSeekValue = originalSeekValue;
        internalStop();
        mResetConnectionClient = true;
        play(true);
        timeshiftStreaming = true;
    }

    private void switchToLiveStreaming(){
        this.originalSeekValue = 0;
        internalStop();
        mResetConnectionClient = true;
        play(false);
        timeshiftStreaming = false;
    }


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
        this.timeshiftStreaming = false;
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
                case STATE_CONNECTING:
                    setState(STATE_CONNECTING);
                    break;
                case STATE_STOPPED:
                    setState(STATE_STOPPED);
                    break;
                case STATE_PLAYING:
                    setState(STATE_PLAYING);
                    break;

                case STATE_PAUSED:{
                    if(timeshiftStreaming){
                        setState(STATE_PAUSED);
                    }else {
                    setState(STATE_STOPPED);
                    stop();
                    }

                    break;
                }

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

    private final OnCloudStreamInfoReceivedListener mClousStreamInfoReceivedListener = new OnCloudStreamInfoReceivedListener() {
        @Override
        public void onCloudStreamInfoReceivedListener(MediaPlayer player, String cloudStreamInfo) {
            notifyCloudStreamInfo(cloudStreamInfo);
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
