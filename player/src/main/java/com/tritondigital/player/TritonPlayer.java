package com.tritondigital.player;

import static android.content.Context.AUDIO_SERVICE;
import android.content.Context;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import androidx.mediarouter.media.MediaRouter;
import androidx.media3.common.Format;
import com.tritondigital.util.Log;
import com.tritondigital.util.TrackingUtil;

/**
 * Plays a station provided by Triton Digital or an on-demand stream.
 *
 * @par Example - Play a Station
 * @code{.java}
 *     // Create the player settings.
 *     Bundle settings = new Bundle();
 *     settings.putString(TritonPlayer.SETTINGS_STATION_BROADCASTER, "Triton Digital");
 *     settings.putString(TritonPlayer.SETTINGS_STATION_NAME,        "MOBILEFM");
 *     settings.putString(TritonPlayer.SETTINGS_STATION_MOUNT,       "MOBILEFM_AACV2");
 *
 *     // Create the player.
 *     TritonPlayer player = new TritonPlayer(this, settings);
 *     player.play();
 * @endcode
 *
 * @par Example - Change the Station
 * @code{.java}
 *     // Release the current player instance.
 *     if (player != null)
 *     {
 *         player.release();
 *     }
 *
 *     // Recreate the player with the next station settings.
 *     player = new TritonPlayer(this, nextStationSettings);
 *     player.play();
 * @endcode
 *
 * @par Example - Target Audio Ads
 * @code{.java}
 *     // Create the targeting parameters
 *     HashMap<String, String> targetingParams = new HashMap();
 *     targetingParams.put(StreamUrlBuilder.COUNTRY_CODE,  "US");
 *     targetingParams.put(StreamUrlBuilder.POSTAL_CODE,   "12345");
 *     targetingParams.put(StreamUrlBuilder.GENDER,        "m");
 *     targetingParams.put(StreamUrlBuilder.YEAR_OF_BIRTH, "1990");
 *
 *     // Create the player settings.
 *     Bundle settings = new Bundle();
 *     settings.putBoolean(TritonPlayer.SETTINGS_TARGETING_LOCATION_TRACKING_ENABLED, true);
 *     settings.putSerializable(TritonPlayer.SETTINGS_TARGETING_PARAMS, targetingParams);
 *     settings.putString(TritonPlayer.SETTINGS_STATION_BROADCASTER,    "Triton Digital");
 *     settings.putString(TritonPlayer.SETTINGS_STATION_NAME,           "MOBILEFM");
 *     settings.putString(TritonPlayer.SETTINGS_STATION_MOUNT,          "MOBILEFM_AACV2");
 *
 *     // Create the player.
 *     TritonPlayer player = new TritonPlayer(this, settings);
 *     player.play();
 * @endcode
 *
 * @par Example - Token Authorization (self signed)
 * @code{.java}
 *     // Create the targeting parameters
 *     HashMap<String, String> targetingParams = new HashMap();
 *     targetingParams.put(StreamUrlBuilder.COUNTRY_CODE,  "US");
 *     targetingParams.put(StreamUrlBuilder.POSTAL_CODE,   "12345");
 *     targetingParams.put(StreamUrlBuilder.GENDER,        "m");
 *     targetingParams.put(StreamUrlBuilder.YEAR_OF_BIRTH, "1990");
 *
 *     // Create the authentication token
 *     String token = AuthUtil.createJwtToken("MySecretKey", "MySecretKeyId", true, "foo@bar.com", targetingParams);
 *
 *     // Create the player settings.
 *     Bundle settings = new Bundle();
 *     settings.putBoolean(TritonPlayer.SETTINGS_TARGETING_LOCATION_TRACKING_ENABLED, true);
 *     settings.putSerializable(TritonPlayer.SETTINGS_TARGETING_PARAMS, targetingParams);
 *     settings.putString(TritonPlayer.SETTINGS_AUTH_TOKEN,             token);
 *     settings.putString(TritonPlayer.SETTINGS_STATION_BROADCASTER,    "Triton Digital");
 *     settings.putString(TritonPlayer.SETTINGS_STATION_NAME,           "MOBILEFM");
 *     settings.putString(TritonPlayer.SETTINGS_STATION_MOUNT,          "MOBILEFM_AACV2");
 *
 *     // Create the player.
 *     TritonPlayer player = new TritonPlayer(this, settings);
 *     player.play();
 * @endcode
 *
 * @par Example - Receive Cue Points
 * @code{.java}
 *     // Create a cue point listener.
 *     OnCuePointReceivedListener listener = new OnCuePointReceivedListener() {
 *         @Override
 *         public void onCuePointReceived(MediaPlayer player, Bundle cuePoint) {
 *             // Handle the cue points here.
 *         }
 *     };
 *
 *
 *     // Create the player.
 *     TritonPlayer player = new TritonPlayer(this, settings);
 *     player.setOnCuePointReceivedListener(listener);
 *     player.play();
 * @endcode
 *
 * @par Example - Display Sync Banners
 * @code{.java}
 *     private SyncBannerView mSyncBannerView;
 *
 *
 *     @Override
 *     protected void onPause() {
 *         mSyncBannerView.onPause();
 *         super.onPause();
 *     }
 *
 *
 *     @Override
 *     protected void onResume() {
 *         super.onResume();
 *         mSyncBannerView.onResume();
 *     }
 *
 *
 *     @Override
 *     protected void onDestroy() {
 *         mSyncBannerView.release();
 *         super.onDestroy();
 *     }
 *
 *
 *     @Override
 *     public void onCuePointReceived(MediaPlayer player, Bundle cuePoint) {
 *         // Update the banner. See previous example on how to get cue points.
 *         mSyncBannerView.loadCuePoint(cuePoint);
 *     }
 *
 *
 *     private void initBanner() {
 *         // Get the banner from the layout and set its size.
 *         mSyncBannerView = (SyncBannerView)findViewById(mySyncBannerId);
 *         mSyncBannerView.setBannerSize(320, 50);
 *     }
 * @endcode
 *
 * @par Example - Play an On-Demand Stream
 * @code{.java}
 *      // Create the player settings.
 *      Bundle settings = new Bundle();
 *      settings.putString(TritonPlayer.SETTINGS_STREAM_URL, "http://storage.googleapis.com/automotive-media/Jazz_In_Paris.mp3");
 *
 *      // Create the player.
 *      TritonPlayer player = new TritonPlayer(this, settings);
 *      player.play();
 * @endcode
 */
@SuppressWarnings({"JavaDoc", "unused"})
public final class TritonPlayer extends MediaPlayer {

    ////////////////////////////////////////////////////////////////////////
    // Station player
    ////////////////////////////////////////////////////////////////////////

    /** @copybrief PlayerConsts::STATION_BROADCASTER */
    public static final String SETTINGS_STATION_BROADCASTER = PlayerConsts.STATION_BROADCASTER;

    /** @copybrief PlayerConsts::STATION_NAME */
    public static final String SETTINGS_STATION_NAME = PlayerConsts.STATION_NAME;

    /** @copybrief PlayerConsts::STATION_MOUNT */
    public static final String SETTINGS_STATION_MOUNT = PlayerConsts.STATION_MOUNT;

    /** @copybrief PlayerConsts::STREAM_URL */
    public static final String SETTINGS_STREAM_URL = PlayerConsts.STREAM_URL;

    /** @copybrief PlayerConsts::MIME_TYPE
     *
     * Needed by Google Cast. The default is MP3.
     */
    public static final String SETTINGS_STREAM_MIME_TYPE = PlayerConsts.MIME_TYPE;

    /* @copydoc PlayerConsts::AUTH_TOKEN
    */
    public static final String SETTINGS_AUTH_TOKEN = PlayerConsts.AUTH_TOKEN;

    public static final String SETTINGS_AUTH_KEY_ID = PlayerConsts.AUTH_KEY_ID;
    public static final String SETTINGS_AUTH_SECRET_KEY = PlayerConsts.AUTH_SECRET_KEY;
    public static final String SETTINGS_AUTH_REGISTERED_USER = PlayerConsts.AUTH_REGISTERED_USER;
    public static final String SETTINGS_AUTH_USER_ID = PlayerConsts.AUTH_USER_ID;
    /** @copydoc PlayerConsts::TARGETING_PARAMS */
    public static final String SETTINGS_TARGETING_PARAMS = PlayerConsts.TARGETING_PARAMS;

    /** @copybrief PlayerConsts::TARGETING_LOCATION_TRACKING_ENABLED */
    public static final String SETTINGS_TARGETING_LOCATION_TRACKING_ENABLED = PlayerConsts.TARGETING_LOCATION_TRACKING_ENABLED;

    /** @copybrief PlayerConsts::MEDIA_ITEM_METADATA */
    public static final String SETTINGS_MEDIA_ITEM_METADATA = PlayerConsts.MEDIA_ITEM_METADATA;

    /** @copybrief PlayerConsts::TRANSPORT */
    public static final String SETTINGS_TRANSPORT = PlayerConsts.TRANSPORT;

    /** @copybrief PlayerConsts::MIME_TYPE_AAC */
    public static final String MIME_TYPE_AAC  = PlayerConsts.MIME_TYPE_AAC;

    /** @copybrief PlayerConsts::MIME_TYPE_MPEG */
    public static final String MIME_TYPE_MPEG = PlayerConsts.MIME_TYPE_MPEG;

    /** @copybrief PlayerConsts::TRANSPORT_FLV */
    public static final String TRANSPORT_FLV = PlayerConsts.TRANSPORT_FLV;

    /** @copybrief PlayerConsts::TRANSPORT_HLS */
    public static final String TRANSPORT_HLS = PlayerConsts.TRANSPORT_HLS;

    /** @copybrief PlayerConsts::TRANSPORT_SC */
    public static final String TRANSPORT_SC  = PlayerConsts.TRANSPORT_SC;

    /** @copybrief PlayerConsts::LOW_DELAY */
    public static final String SETTINGS_LOW_DELAY  = PlayerConsts.LOW_DELAY; //-1 (AUTO), 0 (DISABLED), 1 - 60 for seconds

    /** @copybrief PlayerConsts::TTAGS */
    public static final String SETTINGS_TTAGS  = PlayerConsts.TTAGS;

    /** @copybrief PlayerConsts::FORCE_DISABLE_EXOPLAYER */
    public static final String SETTINGS_FORCE_DISABLE_EXOPLAYER = PlayerConsts.FORCE_DISABLE_EXOPLAYER;

    /** @copybrief PlayerConsts::PLAYER_SERVICES_REGION */
    public static final String SETTINGS_PLAYER_SERVICES_REGION = PlayerConsts.PLAYER_SERVICES_REGION;

    /** @copybrief PlayerConsts::DMP_SEGMENTS */
    public static final String SETTINGS_DMP_SEGMENTS = PlayerConsts.DMP_SEGMENTS;

    private final MediaPlayer mPlayer;

    private AudioManager mAudioManager;



    /**
     * Constructor
     *
     * @throws IllegalArgumentException if SETTINGS_STATION_MOUNT and SETTINGS_STREAM_URL are not set
     * @throws IllegalArgumentException if SETTINGS_STATION_MOUNT and SETTINGS_STREAM_URL are both set
     */
    public TritonPlayer(Context context, Bundle settings) {
        super(context, settings);

        // It may take time to get the gaid so we will make the first call here.
        TrackingUtil.getTrackingId(context);

        String mount     = settings.getString(SETTINGS_STATION_MOUNT);
        String streamUrl = settings.getString(SETTINGS_STREAM_URL);

        if (!TextUtils.isEmpty(mount) && !TextUtils.isEmpty(streamUrl)) {
            throw new IllegalArgumentException("\"settings.SETTINGS_STATION_MOUNT\" and \"settings.SETTINGS_STREAM_URL\" can't be set at the same time.");
        } else if (!TextUtils.isEmpty(mount)) {
            mPlayer = new StationPlayer(context, settings);
        } else if (!TextUtils.isEmpty(streamUrl)) {
            mPlayer = new StreamPlayer(context, settings, false);
        } else {
            throw new IllegalArgumentException("\"settings.SETTINGS_STATION_MOUNT\" or \"settings.SETTINGS_STREAM_URL\" must be set");
        }

        mPlayer.setOnCuePointReceivedListener(mInOnCuePointReceivedListener);
        mPlayer.setOnMetaDataReceivedListener(mInOnMetaDataReceivedListener);
        mPlayer.setOnInfoListener(mInOnInfoListener);
        mPlayer.setOnStateChangedListener(mInOnStateChangedListener);
        mPlayer.setOnCloudStreamInfoReceivedListener(mOnCloudStreamInfoReceivedListener);

        mAudioManager = (AudioManager)context.getSystemService(AUDIO_SERVICE);
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Methods forwarded to its delegate
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public int getDuration() { return mPlayer.getDuration(); }

    @Override
    public Bundle getLastCuePoint() { return mPlayer.getLastCuePoint(); }

    @Override
    public int getLastErrorCode() { return mPlayer.getLastErrorCode(); }

    @Override
    public boolean isTimeshiftStreaming() { return mPlayer.isTimeshiftStreaming(); }

    @Override
    public int getPosition() { return mPlayer.getPosition(); }

    @Override
    public Bundle getSettings() { return mPlayer.getSettings(); }

    @Override
    public int getState() { return mPlayer.getState(); }

    @Override
    public float getVolume() { return mPlayer.getVolume(); }

    public MediaPlayer getMediaPlayer() { return mPlayer; }

    @Override
    protected void internalPause() { mPlayer.pause();
    }

    @Override
    protected void internalPlay() {
      internalPlay(false);
    }

    @Override
    protected void internalPlay( boolean timeshiftStreaming ) {
        checkVolume();
        mPlayer.play();
    }

    @Override
    protected void internalRelease() { mPlayer.release(); }

    @Override
    protected void internalSeekTo(int position, int original) { mPlayer.seekTo(position, original); }

    @Override
    protected void internalStop() { mPlayer.stop(); }

    @Override
    protected void internalChangeSpeed(Float speed) {
        mPlayer.internalChangeSpeed(speed);
    }

    @Override
    public boolean isSeekable() { return mPlayer.isSeekable(); }

    @Override
    public boolean isPausable() { return mPlayer.isPausable(); }

    @Override
    public void setVolume(float volume) { mPlayer.setVolume(volume); }

    @Override
    protected String makeTag() { return Log.makeTag("TritonPlayer"); }

    @Override
    protected boolean isEventLoggingEnabled() { return true; }

    @Override
    protected void internalGetCloudStreamInfo() {
        mPlayer.internalGetCloudStreamInfo();
    }

    @Override
    protected void internalPlayProgram(String programId) {
        mPlayer.playProgram(programId);
    }

    /**
     * Sets the <a href="http://developer.android.com/reference/android/support/v7/media/MediaRouter.RouteInfo.html">media route</a> to use for the current player instance.
     *
     * The route change is only effective on the next play().
     * @warning BETA. Only for live streams.
     */
    public void setMediaRoute(MediaRouter.RouteInfo routeInfo) {
        if (mPlayer instanceof StationPlayer) {
            ((StationPlayer) mPlayer).setMediaRoute(routeInfo);
        } else {
            ((StreamPlayer) mPlayer).setMediaRoute(routeInfo);
        }
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Station Player
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Returns the alternate mount used if the station is using content blocking.
     */
    public String getAlternateMount() {
        return (mPlayer instanceof StationPlayer)
                ? ((StationPlayer) mPlayer).getAlternateMount() : null;
    }


    /**
     * Returns the Side Band MetaData Url.
     */
    public String getSideBandMetadataUrl()
    {
        return (mPlayer instanceof StationPlayer)
                ? ((StationPlayer) mPlayer).getSideBandMetadataUrl() : null;
    }


    /**
     * Returns the Stream URL to cast to Google Cast devices.
     */
    public String getCastStreamingUrl()
    {
        if (mPlayer instanceof StationPlayer) {
            return ((StationPlayer) mPlayer).getCastStreamingUrl() ;
        } else {
           return  ((StreamPlayer) mPlayer).getSettings().getString(StreamPlayer.SETTINGS_STREAM_URL);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Listeners
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("FieldCanBeLocal")
    private final OnCuePointReceivedListener mInOnCuePointReceivedListener = new OnCuePointReceivedListener() {
        @Override
        public void onCuePointReceived(MediaPlayer player, Bundle cuePoint) {
            notifyCuePoint(cuePoint);
        }
    };

    private final OnMetaDataReceivedListener mInOnMetaDataReceivedListener = new OnMetaDataReceivedListener() {

        @Override
        public void onMetaDataReceived(MediaPlayer player, Bundle metadata) {
            notifyMetadata(metadata);
        }
    };

    @SuppressWarnings("FieldCanBeLocal")
    private final OnStateChangedListener mInOnStateChangedListener = new OnStateChangedListener() {
        @Override
        public void onStateChanged(MediaPlayer player, int state) {
            if (state == STATE_ERROR) {
                setErrorState(player.getLastErrorCode());
            } else {
                setState(state);
            }
        }
    };

    private final OnCloudStreamInfoReceivedListener mOnCloudStreamInfoReceivedListener = new OnCloudStreamInfoReceivedListener() {
        @Override
        public void onCloudStreamInfoReceivedListener(MediaPlayer player, String cloudStreamInfo) {
            notifyCloudStreamInfo(cloudStreamInfo);
        }
    };


    @SuppressWarnings("FieldCanBeLocal")
    private final OnInfoListener mInOnInfoListener = new OnInfoListener() {
        @Override
        public void onInfo(MediaPlayer player, int info, int extra) {
            notifyInfo(info, extra);
        }
    };

    private class SettingsContentObserver extends ContentObserver {

        public SettingsContentObserver(Handler handler) {
            super(handler);
        }
        private boolean volumeStopped = false;

        @Override
        public boolean deliverSelfNotifications() {
            return super.deliverSelfNotifications();
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            int volume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            if(volume == 0) {
                if(getState() != STATE_STOPPED){
                    volumeStopped = true;
                }
                pause();
            }else  if (getState() == STATE_STOPPED && volume > 0 && volumeStopped) {
                play();
                volumeStopped = false;
            }
        }
    }

    private void checkVolume() {
        int volume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        if(volume == 0) {
            int maxVolume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int minVolume = Math.round(maxVolume * 0.4f) ;
            mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, minVolume, 0);
        }
    }
}
