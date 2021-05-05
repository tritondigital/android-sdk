package com.tritondigital.player;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultAllocator;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.util.MimeTypes;
import com.tritondigital.player.exoplayer.extractor.flv.TdDefaultExtractorsFactory;
import com.tritondigital.player.exoplayer.extractor.flv.TdMetaDataListener;
import com.tritondigital.util.Assert;
import com.tritondigital.util.Log;
import com.tritondigital.util.NetworkUtil;

import java.util.ArrayList;
import java.util.Map;

import static com.google.android.exoplayer2.Player.DISCONTINUITY_REASON_SEEK;

/**
 * Wraps Android's native player
 *
 */
public class TdExoPlayer extends MediaPlayer implements TdMetaDataListener {
    /**
     * @copybrief Const.STREAM_URL
     */
    public static final String SETTINGS_STREAM_URL = PlayerConsts.STREAM_URL;

    /**
     * @copybrief Const.MIME_TYPE
     * Used only for FLV streams
     */
    public static final String SETTINGS_MIME_TYPE = PlayerConsts.MIME_TYPE;

    /**
     * @copybrief Const.TRANSPORT
     * Use only for FLV streams
     */
    public static final String SETTINGS_TRANSPORT = PlayerConsts.TRANSPORT;

    /**
     * @copybrief Const.USER_AGENT
     */
    public static final String SETTINGS_USER_AGENT = PlayerConsts.USER_AGENT;

    /**
     * @copybrief PlayerConsts.POSITION
     */
    public static final String SETTINGS_POSITION = PlayerConsts.POSITION;

    public static final String SETTINGS_STATION_MOUNT = PlayerConsts.STATION_MOUNT;

    public static final String SETTINGS_LOW_DELAY = PlayerConsts.LOW_DELAY;

    private static final int CALLBACK_CUE_POINT_RECEIVED = 60;
    private static final int CALLBACK_ON_INFO            = 61;
    private static final int CALLBACK_HANDLER_READY      = 62;
    private static final int CALLBACK_STATE_CHANGED      = 63;
    private static final int CALLBACK_METADATA_RECEIVED  = 64;

    private static final String TAG = Log.makeTag("TdExoPlayer:Thread");

    /**
     * ExoPlayer: Whether cross-protocol redirects (i.e. redirects from HTTP to HTTPS and vice versa) are enabled
     */
    private static final boolean SETTINGS_ALLOW_CROSS_PROTOCOL_REDIRECT =  true ;

    private final ArrayList<Message> mMessageQueue = new ArrayList<>();
    private final MainHandler        mMainHandler;
    private volatile PlayerHandler   mPlayerHandler;



    /**
     * Constructor
     */
    public TdExoPlayer(@NonNull final Context context, @NonNull final Bundle settings) {
        super(context, settings);
        mMainHandler = new MainHandler(this);

        // Start the player thread
        Thread playerThread = new Thread(TAG) {
            @Override
            public void run() {
                super.run();
                Looper.prepare();

                try {
                    if (mMainHandler != null) {
                        mPlayerHandler = new PlayerHandler(context, mMainHandler, settings);
                        Looper.loop();
                    }
                } catch (Exception e) {
                    Log.e(TAG, e, "Background thread creation");
                }

                mPlayerHandler = null;
            }
        };
        playerThread.start();
    }


    @Override
    protected void internalPlay() {
        setState(STATE_CONNECTING);

        // Network error check
        String streamUrl = mSettings.getString(SETTINGS_STREAM_URL);
        if (streamUrl != null && streamUrl.startsWith("http") && !NetworkUtil.isNetworkConnected(getContext())) {
            setErrorState(ERROR_NO_NETWORK);
            return;
        }

        sendPlayerMsg(PlayerHandler.ACTION_PLAY, 0, null);
    }


    @Override
    protected void internalPause() {
        sendPlayerMsg(PlayerHandler.ACTION_PAUSE, 0, null);
        setState(STATE_PAUSED);
    }


    /**
     * Only does the state changed because this method gets called when releasing a player.
     * stop() should NOT be called directly on this player. Use "release()" instead.
     */
    @Override
    protected void internalStop() {
        setState(STATE_STOPPED);
    }


    @Override
    protected void internalRelease() {
        sendPlayerMsg(PlayerHandler.ACTION_RELEASE, 0, null);
        setState(STATE_RELEASED);
    }


    @Override
    protected void internalSeekTo(int position) {
        sendPlayerMsg(PlayerHandler.ACTION_SEEK_TO, position, null);
    }


    @Override
    public int getDuration() {
        return (mPlayerHandler == null)
                ? DURATION_UNKNOWN : mPlayerHandler.getDuration();
    }


    @Override
    public int getPosition() {
        return (mPlayerHandler == null)
                ? POSITION_UNKNOWN : mPlayerHandler.getPosition();
    }


    @Override
    public float getVolume() {
        return (mPlayerHandler == null)
                ? VOLUME_NORMAL : mPlayerHandler.getVolume();
    }

    @Override
    public void setVolume(float volume) {
        sendPlayerMsg(PlayerHandler.ACTION_SET_VOLUME, 0, volume);
    }

    @Override
    protected String makeTag() {
        return TAG;
    }


    @Override
    protected boolean isEventLoggingEnabled() {
        return false;
    }


    private void onStateChanged(int state, int detail) {
        if (state == STATE_ERROR) {
            setErrorState(detail);
        } else {
            setState(state);
        }
    }

    private void onCuePointReceived(Bundle cuePoint) {
        notifyCuePoint(cuePoint);
    }

    private void onMetaDataReceived(Bundle msg) {        
        notifyMetadata(msg);
    }

    private void onInfo(int info, int detail) {
        notifyInfo(info, detail);
    }


    private void sendPlayerMsg(int action, int arg1, Object obj) {
        int state = getState();
        if (state == STATE_RELEASED) {
            return;
        }

        if (mPlayerHandler != null) {
            // Send the message now
            try {
                mPlayerHandler.removeMessages(action);
                Message msg = mPlayerHandler.obtainMessage(action, arg1, 0, obj);
                mPlayerHandler.sendMessage(msg);
            } catch (Exception e) {
                Log.w(TAG, e, "sendPlayerMsg " + PlayerHandler.debugActionToStr(action));
            }

        } else {
            // Add the message to the queue
            Message msg = new Message();
            msg.what = action;
            msg.arg1 = arg1;
            msg.arg2 = 0;
            msg.obj  = obj;

            mMessageQueue.add(msg);
        }
    }


    private void onPlayerHandlerReady() {
        if ((getState() != STATE_RELEASED) && (mPlayerHandler != null)) {
            // Send only the "ACTION_RELEASE"
            for (Message msg : mMessageQueue) {
                if (msg.what == PlayerHandler.ACTION_RELEASE) {
                    mPlayerHandler.sendMessage(msg);
                    mMessageQueue.clear();
                    return;
                }
            }

            // Send all pending messages
            for (Message msg : mMessageQueue) {
                mPlayerHandler.sendMessage(msg);
            }
            mMessageQueue.clear();
        }
    }

    @Override
    public void onMetaDataReceived(Map<String, Object> metadata) {
        sendPlayerMsg(PlayerHandler.ACTION_META, 0, metadata);
    }


    private static class MainHandler extends Handler {
        final TdExoPlayer mTdExoPlayer;

        MainHandler(TdExoPlayer exoPlayer) {
            mTdExoPlayer = exoPlayer;
        }

        @Override
        public void handleMessage(Message msg) {
            if ((mTdExoPlayer != null) && (mTdExoPlayer.getState() != STATE_RELEASED)) {
                switch (msg.what) {
                    case CALLBACK_CUE_POINT_RECEIVED:
                        mTdExoPlayer.onCuePointReceived((Bundle) msg.obj);
                        break;
                    case CALLBACK_METADATA_RECEIVED:
                        mTdExoPlayer.onMetaDataReceived((Bundle) msg.obj);
                        break;
                    case CALLBACK_ON_INFO:
                        mTdExoPlayer.onInfo(msg.arg1, msg.arg2);
                        break;

                    case CALLBACK_HANDLER_READY:
                        mTdExoPlayer.onPlayerHandlerReady();
                        break;

                    case CALLBACK_STATE_CHANGED:
                        mTdExoPlayer.onStateChanged(msg.arg1, msg.arg2);
                        break;

                    default:
                        Assert.failUnhandledValue(TAG, msg.what, "handleMessage");
                        break;
                }
            }
        }
    }


    protected static class PlayerHandler extends Handler implements Player.EventListener {

        static final int ACTION_PAUSE           = 350;
        static final int ACTION_PLAY            = 351;
        static final int ACTION_RELEASE         = 352;
        static final int ACTION_SEEK_TO         = 353;
        static final int ACTION_SET_VOLUME      = 354;
        static final int ACTION_POLL_IS_PLAYING = 355;
        static final int ACTION_META = 356;


        private static final int BUFFER_SEGMENT_SIZE = 64*1024;
        private static final int BUFFER_SEGMENTS = 256;

        private static final int TRITON_BUFFER_SCALE_UP_FACTOR = 4;

        private static final String TAG = Log.makeTag("ExoPlayerBkg");
        protected final Bundle mSettings;
        private final Context mContext;
        private final MainHandler mMainHandler;
        private int dPrebuffer = 2000;
        private int dRebuffer = 4000;
        private int dBufferGaurd = 4000;
        private int dPrebufferMultiplier = 1;
        private int lowDelay = 0;       

        private SimpleExoPlayer mExoPlayerLib;


        private int   mDuration = DURATION_UNKNOWN;
        private float mVolume   = VOLUME_NORMAL;
        private boolean mFinishing;

        private CountDownTimer bufferTimer;


        PlayerHandler(Context context, MainHandler mainHandler, Bundle settings) {
            super(Looper.getMainLooper());
            mContext     = context;
            mMainHandler = mainHandler;
            mSettings    = settings;
            lowDelay    = mSettings.getInt(SETTINGS_LOW_DELAY,0); //-1 (AUTO), 0 (DISABLED), 1 - 60 for seconds (0/Disabled is default)
            if ( lowDelay > 60 ) {
                lowDelay = 60;
            }
            if ( lowDelay < 0 ) {
                lowDelay = -1;
            }
            // Notify the background thread is ready to receive commands
            Message msg = mMainHandler.obtainMessage(CALLBACK_HANDLER_READY);
            mMainHandler.sendMessageDelayed(msg, 50);
        }

        ////////////////////////////////////////////////////////////////////////////////////////////
        // Cue Points
        ////////////////////////////////////////////////////////////////////////////////////////////

        @SuppressWarnings("unchecked")
        private static Bundle decodeCuePoint(Map<String, Object> metaData) {
            if (metaData == null) {
                return null;
            }

            final Map<String, Object> cuePoint = (Map<String, Object>) metaData.get("onCuePoint");
            if (cuePoint == null) {
                return null;
            }

            final Map<String, Object> params = (Map<String, Object>) cuePoint.get("parameters");
            final String type = (String) cuePoint.get("name");

            // "cue_title" is the only required field.
            if (params.containsKey("cue_title")) {
                return decodeStwCuePoint(params, type);
            } else {
                return decodeAndoCuePoint(params, type);
            }
        }

        private static Bundle decodeStwCuePoint(Map<String, Object> params, String type) {
            Bundle cuePoint = new Bundle();
            cuePoint.putString(CuePoint.CUE_TYPE, type);

            for (Map.Entry<String, Object> entry : params.entrySet()) {
                String key = entry.getKey();
                String value = (String) entry.getValue();
                CuePoint.addCuePointAttribute(cuePoint, type, key, value);
            }

            return cuePoint;
        }

        private static Bundle decodeAndoCuePoint(Map<String, Object> params, String type) {
            Bundle cuePoint = new Bundle();

            // Common
            decodeAndoString(params, "Type", cuePoint, CuePoint.LEGACY_TYPE);
            String durationStr = (String) params.get("Time");
            try {
                int value = Integer.parseInt(durationStr);
                cuePoint.putInt(CuePoint.CUE_TIME_DURATION, (value * 1000));
            } catch (NumberFormatException e) {
                Log.v(TAG, "NumberFormatException: " + e);
            }

            switch (type) {
                case "NowPlaying":
                    cuePoint.putString(CuePoint.CUE_TYPE, CuePoint.CUE_TYPE_VALUE_TRACK);
                    decodeAndoString(params, "Album", cuePoint, CuePoint.TRACK_ALBUM_NAME);
                    decodeAndoString(params, "Artist", cuePoint, CuePoint.TRACK_ARTIST_NAME);
                    decodeAndoString(params, "IMGURL", cuePoint, CuePoint.TRACK_COVER_URL);
                    decodeAndoString(params, "Label", cuePoint, CuePoint.TRACK_ALBUM_PUBLISHER);
                    decodeAndoString(params, "Title", cuePoint, CuePoint.CUE_TITLE);
                    decodeAndoString(params, "BuyNowURL", cuePoint, CuePoint.LEGACY_BUY_URL);
                    break;

                case "Ads":
                    cuePoint.putString(CuePoint.CUE_TYPE, CuePoint.CUE_TYPE_VALUE_AD);
                    decodeAndoString(params, "BREAKADID", cuePoint, CuePoint.AD_ID);
                    decodeAndoString(params, "BREAKTYPE", cuePoint, CuePoint.AD_TYPE);
                    decodeAndoString(params, "IMGURL", cuePoint, CuePoint.LEGACY_AD_IMG_URL);
                    break;

                default:
                    cuePoint.putString(CuePoint.CUE_TYPE, CuePoint.CUE_TYPE_VALUE_UNKNOWN);
                    Log.e(TAG, "Unknown AndoXML cue point type: " + type);
                    break;
            }

            return cuePoint;
        }

        private static void decodeAndoString(Map<String, Object> map, String mapKey, Bundle bundle, String bundleKey) {
            String valueStr = (String) map.get(mapKey);
            if (valueStr != null) {
                bundle.putString(bundleKey, valueStr);
            }
        }

        static String debugActionToStr(int action) {
            switch (action) {
                case ACTION_PAUSE:
                    return "ACTION_PAUSE";
                case ACTION_PLAY:
                    return "ACTION_PLAY";
                case ACTION_RELEASE:
                    return "ACTION_RELEASE";
                case ACTION_SEEK_TO:
                    return "ACTION_SEEK_TO";
                case ACTION_SET_VOLUME:
                    return "ACTION_SET_VOLUME";
                case ACTION_POLL_IS_PLAYING:
                    return "ACTION_POLL_IS_PLAYING";
                default:
                    Assert.failUnhandledValue(TAG, action, "debugActionToStr");
                    return "UNKNOWN";
            }
        }

        @Override
        public void handleMessage(Message msg) {
            if (!mFinishing) {
                switch (msg.what) {
                    case ACTION_PAUSE:
                        pause();
                        break;
                    case ACTION_PLAY:
                        play();
                        break;
                    case ACTION_RELEASE:
                        release();
                        break;
                    case ACTION_SEEK_TO:
                        seekTo(msg.arg1);
                        break;
                    case ACTION_SET_VOLUME:
                        setVolume((Float) msg.obj);
                        break;
                    case ACTION_POLL_IS_PLAYING:
                        pollIsPlaying();
                        break;
                    case ACTION_META:
                        onMetaDataReceived((Map<String, Object>) msg.obj);
                        break;

                    default:
                        Assert.failUnhandledValue(TAG, msg.what, "PlayerHandler.handleMessage");
                        break;
                }
            }
        }


        private void play() {

            if (mExoPlayerLib == null) {
                // Create stream URL
                String streamUrl = mSettings.getString(SETTINGS_STREAM_URL);

                if (streamUrl == null) {
                    notifyStateChanged(STATE_ERROR, ERROR_INVALID_URL);
                    return;
                } else {
                    if ( lowDelay == -1 || lowDelay > 0  ) {
                        int dPrebufferSeconds;

                        if ( lowDelay == -1 ) {
                            dPrebufferSeconds = dPrebuffer / 1000;
                            dBufferGaurd = 2000;
                        } else {
                            dPrebufferSeconds = lowDelay;
                            dPrebuffer = lowDelay * 1000;
                            dRebuffer = dPrebuffer; // add 2 seconds to a rebuffer
                            dBufferGaurd = 10000;
                        }

                        int fixUp = dPrebufferSeconds+1;
                        streamUrl = streamUrl + "&burst-time=" + fixUp;

                    } else {
                        dPrebuffer = 2500;
                        dRebuffer = 8000;
                        dBufferGaurd = 7000;
                    }
                }

                notifyStateChanged(STATE_CONNECTING);

                Log.i(TAG, "ExoPlayer URL: " +  streamUrl);

                Uri uri = Uri.parse(streamUrl);


                int timeout = dRebuffer + dBufferGaurd;
                Log.i(TAG, "ExoPlayer buffer start: " +  dPrebuffer + " rebuffer: " + dRebuffer + " timeout: " + timeout);

                String userAgent = mSettings.getString(SETTINGS_USER_AGENT);
                if ( userAgent == null ) {
                    userAgent = "TxExoPlayer/MPEG Compatible";
                }


                     String transport = mSettings.getString(SETTINGS_TRANSPORT);


                    BandwidthMeter bandwidthMeter = new DefaultBandwidthMeter.Builder(mContext).build();

                    TrackSelector trackSelector = new DefaultTrackSelector(mContext);

                    DefaultRenderersFactory defaultRenderersFactory = new DefaultRenderersFactory(mContext);

                   int minBufferMs = TRITON_BUFFER_SCALE_UP_FACTOR*DefaultLoadControl.DEFAULT_MIN_BUFFER_MS;
                   int maxBufferMs = TRITON_BUFFER_SCALE_UP_FACTOR*DefaultLoadControl.DEFAULT_MAX_BUFFER_MS;
                   int bufferForPlaybackMs = dPrebuffer;
                   int bufferForPlaybackAfterRebufferMs = dRebuffer;                   

                   LoadControl loadControl = new DefaultLoadControl.Builder()
                           .setBufferDurationsMs(minBufferMs, maxBufferMs, bufferForPlaybackMs, bufferForPlaybackAfterRebufferMs)
                           .setAllocator(new DefaultAllocator(true, BUFFER_SEGMENT_SIZE,BUFFER_SEGMENTS))
                           .build();

                mExoPlayerLib = new SimpleExoPlayer.Builder(mContext,defaultRenderersFactory)
                        .setTrackSelector(trackSelector)
                        .setLoadControl(loadControl)
                        .setBandwidthMeter(bandwidthMeter)
                        .setWakeMode(C.WAKE_MODE_NETWORK)
                        .build();

                mExoPlayerLib.setThrowsWhenUsingWrongThread(false);
                    mExoPlayerLib.addListener(this);

                    // Produces DataSource instances through which media data is loaded.
                    DataSource.Factory dataSourceFactory;
                if (streamUrl.startsWith("http")) {
                        dataSourceFactory = new DefaultHttpDataSourceFactory(userAgent,null,DefaultHttpDataSource.DEFAULT_CONNECT_TIMEOUT_MILLIS,
                                DefaultHttpDataSource.DEFAULT_READ_TIMEOUT_MILLIS, SETTINGS_ALLOW_CROSS_PROTOCOL_REDIRECT);
                } else {
                        dataSourceFactory = new DefaultDataSourceFactory(mContext,userAgent);
                    }


                    // Produces Extractor instances for parsing the media data.
                    TdDefaultExtractorsFactory extractorsFactory = new TdDefaultExtractorsFactory(mMainHandler.mTdExoPlayer);

                    // This is the MediaSource representing the media to be played.
                    MediaSource audioSource;
                if (PlayerConsts.TRANSPORT_HLS.equals(transport)) {
                        audioSource = new HlsMediaSource.Factory(dataSourceFactory).createMediaSource(new MediaItem.Builder().setUri(uri).setMimeType(MimeTypes.APPLICATION_M3U8).build());
                } else {
                        audioSource = new ProgressiveMediaSource.Factory(dataSourceFactory, extractorsFactory).createMediaSource(new MediaItem.Builder().setUri(uri).build());
                    }

                AudioAttributes audioAttributes = new AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.CONTENT_TYPE_MUSIC)
                        .build();

                mExoPlayerLib.setAudioAttributes(audioAttributes,true);
                    // Prepare the stream
                    Log.d(TAG, "Prepare ExoPlayer for: " + streamUrl);

                    // Prepare the player with the source.
                    mExoPlayerLib.setMediaSource(audioSource);
                    mExoPlayerLib.prepare();

                    mExoPlayerLib.setPlayWhenReady(true);

                    int position = mSettings.getInt(SETTINGS_POSITION);
                    if (position > 0) {
                        mExoPlayerLib.seekTo(position);
                    }

            } else {
                mExoPlayerLib.setPlayWhenReady(true);
            }
        }


        private void pause() {
            if (mExoPlayerLib != null) {
                try {
                    mExoPlayerLib.setPlayWhenReady(false);
                    notifyStateChanged(STATE_PAUSED);
                } catch (Exception e) {
                    Log.w(TAG, e, "pause()");
                }
            }
        }


        private void release() {
            try {
                if (mExoPlayerLib != null) {
                    mExoPlayerLib.release();
                    mExoPlayerLib = null;
                }

                mFinishing = true;

            } catch (Exception e) {
                Log.w(TAG, e, "release()");
            }
        }


        private void onDurationChanged(int duration) {
            duration = StreamPlayer.normalizeDuration(duration);

            //if (mDuration != duration) {
                mDuration = duration;
                notifyInfo(INFO_DURATION_CHANGED, duration);
            //}
        }


        private void onPlaybackStarted() {
            if (mExoPlayerLib != null) {
                mDuration = (int)mExoPlayerLib.getDuration();

                onDurationChanged(mDuration);
                notifyStateChanged(STATE_PLAYING);
            }
        }


        private void notifyCuePointReceived(Bundle cuePoint, long delay) {
            Message msg = mMainHandler.obtainMessage(CALLBACK_CUE_POINT_RECEIVED, cuePoint);
            if ( delay > 0 )
                mMainHandler.sendMessageDelayed(msg, delay);        
            else
                mMainHandler.sendMessage(msg);
        }

        private void notifyMetadataReceived(Bundle metadata, long delay) {
            Message msg = mMainHandler.obtainMessage(CALLBACK_METADATA_RECEIVED, metadata);
            if ( delay > 0 )
                mMainHandler.sendMessageDelayed(msg, delay);
            else
                mMainHandler.sendMessage(msg);
        }

        private void notifyInfo(int info, int detail) {
            Message msg = mMainHandler.obtainMessage(CALLBACK_ON_INFO, info, detail);
            mMainHandler.sendMessage(msg);
        }


        private void notifyInfo(int info) {
            notifyInfo(info, 0);
        }


        private void notifyStateChanged(int state, int detail) {
            Message msg = mMainHandler.obtainMessage(CALLBACK_STATE_CHANGED, state, detail);
            mMainHandler.sendMessage(msg);
        }


        private void notifyStateChanged(int state) {
            notifyStateChanged(state, 0);
        }


        private void pollIsPlaying() {
            if (mFinishing || (mExoPlayerLib == null) ) {
                return;
            }

            long position = mExoPlayerLib.getCurrentPosition();
            Log.v(TAG, "Stream Position: " + position + " ms");

            if (position > 0) {
                onPlaybackStarted();
            } else {
                Message msg = obtainMessage(ACTION_POLL_IS_PLAYING);
                sendMessageDelayed(msg, 250);
            }
        }


        private void seekTo(int position) {
            if (mExoPlayerLib != null) {
                try {
                    notifyInfo(INFO_SEEK_STARTED);
                    mExoPlayerLib.seekTo(position);
                    notifyInfo(INFO_SEEK_COMPLETED, (int) mExoPlayerLib.getCurrentPosition());
                } catch (IllegalStateException e) {
                    Log.w(TAG, e, "seekTo()");
                }
            }
        }


        public int getPosition() {
            if (mExoPlayerLib != null) {
                try {
                    return (int) mExoPlayerLib.getCurrentPosition();
                } catch (Exception e) {
                    Log.w(TAG, e, "getPosition()");
                }
            }

            return POSITION_UNKNOWN;
        }


        public int getDuration() {
            return mDuration;
        }


        public float getVolume() {
            return mVolume;
        }


        private void setVolume(float volume) {
            if (mExoPlayerLib != null) {
                mVolume = volume;
                mExoPlayerLib.setVolume(volume);
            }
        }

        private boolean isMountEmpty() {
            //If Mount is null or empty, it means the TdExoPlayer is setup to play an on demand stream
            if (mSettings != null) {
               return TextUtils.isEmpty(mSettings.getString(SETTINGS_STATION_MOUNT));
            }
            return false;
        }



        @Override
        public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
            Log.i(TAG, "onPlayerStateChanged  playWhenReady: " + playWhenReady+ " playbackState:" + playbackState);
            // Anytime we change state, we should cancel the bufferTimer if it's running
            if (bufferTimer != null) {
                bufferTimer.cancel();
                bufferTimer = null;
            }
            switch (playbackState) {
                case ExoPlayer.STATE_IDLE:
                    //ignore
                     break;
                case ExoPlayer.STATE_BUFFERING:
                    notifyInfo(INFO_BUFFERING_START);

                    bufferTimer = new CountDownTimer(dRebuffer + dBufferGaurd, 900) {

                        public void onTick(long millisUntilFinished) {
                            Log.i(TAG, "ExoPlayer buffer count down timer: " + millisUntilFinished / 1000);
                        }

                        public void onFinish() {
                            bufferTimer = null;

                            if (lowDelay == -1) {
                                dPrebufferMultiplier = 2;
                                dPrebuffer = dPrebuffer * dPrebufferMultiplier;
                                dRebuffer = dRebuffer * dPrebufferMultiplier;

                                if (dPrebuffer < 10000) {
                                    Log.i(TAG, "ExoPlayer increasing prebuffer due to ExoLib buferring timeout, must reconnect");
                                    mExoPlayerLib.release();
                                    mExoPlayerLib = null;
                                    play();
                                } else {
                                    Log.i(TAG, "ExoPlayer resetting after 3 increases, must reconnect");
                                    notifyStateChanged(STATE_ERROR, ERROR_UNEXPECTED_END_OF_MEDIA);
                                }
                            } else {
                                //Log.i(TAG, "ExoPlayer timeout");
                               //notifyStateChanged(STATE_ERROR, ERROR_UNEXPECTED_END_OF_MEDIA);
                            }
                        }

                    }.start();
                    break;

                case ExoPlayer.STATE_READY:
                    notifyInfo(INFO_BUFFERING_COMPLETED);
                    onPlaybackStarted();
                    break;

                case ExoPlayer.STATE_ENDED:
                    if ( mDuration > 0 && isMountEmpty())  //it is an on Demand streaming
                        notifyStateChanged(STATE_COMPLETED);
                    else {
                        notifyStateChanged(STATE_ERROR, ERROR_UNEXPECTED_END_OF_MEDIA);
                    }

                    break;

            }
        }

        @Override
        public void onRepeatModeChanged(int i) {
            Log.i(TAG, "ExoPlayer onRepeatModeChanged()   i: " + i);
        }

        @Override
        public void onShuffleModeEnabledChanged(boolean b) {
            Log.i(TAG, "ExoPlayer onShuffleModeEnabledChanged()   b: " + b);
        }

        @Override
        public void onLoadingChanged(boolean isLoading) {
            Log.i(TAG, "ExoPlayer onLoadingChanged()   isLoading: " + isLoading);
        }

        @Override
        public void onTimelineChanged(Timeline timeline, @Nullable Object manifest, int reason) {
            Log.i(TAG, "ExoPlayer onTimelineChanged()");
        }

        @Override
        public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
            Log.i(TAG, "ExoPlayer onTracksChanged()");
        }

        @Override
        public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
            Log.e(TAG, "ExoPlayer onPlaybackParametersChanged()");
        }


        @Override
        public void onPlayerError(ExoPlaybackException e) {
            Log.e(TAG, "ExoPlayer error: " + e.getMessage());
            Log.e(TAG, "ExoPlayer Network connected: " +  com.tritondigital.util.NetworkUtil.isNetworkConnected(mContext));
            if (!isMountEmpty()) {
                Log.e(TAG, "ExoPlayer onError: we restart the player");
                mExoPlayerLib.stop();
                mExoPlayerLib.release();
                mExoPlayerLib = null;
                play();
                return;
            }

            if ((mDuration <= 0) || (mDuration >= DURATION_LIVE_MIN_VALUE)) {
                // Some devices calls this method instead of onError() when there is a network problem.
                Log.e(TAG, "onCompletion()");
                notifyStateChanged(STATE_ERROR, ERROR_UNEXPECTED_END_OF_MEDIA);
            } else {
                // TODO: check if the position is close to the duration
                notifyStateChanged(STATE_COMPLETED);
            }

        }

        @Override
        public void onPositionDiscontinuity(int i) {
            if (i == DISCONTINUITY_REASON_SEEK) {
                Log.i(TAG, "ExoPlayer onSeekProcessed()");
            } else{
            Log.i(TAG, "ExoPlayer onPositionDiscontinuity()   i: " + i);
            }
        }


        public void onMetaDataReceived( Map<String, Object> metadata) {
            long nowTimeStamp = mExoPlayerLib.getCurrentPosition()*1000;
            long whenTimeStamp = (long ) metadata.get(TdMetaDataListener.KEY_TIMESTAMP);
            long delay = (whenTimeStamp - nowTimeStamp)/1000;
            String name = (String) metadata.get(TdMetaDataListener.KEY_NAME);

            if(TdMetaDataListener.NAME_CUEPOINT.equalsIgnoreCase(name)){
                Bundle cuePoint = decodeCuePoint(metadata);

                if ( cuePoint != null) {
                    Log.d(TAG, "CuePoint Received:  Delay: "+ delay );
                    notifyCuePointReceived(cuePoint, delay);
                }
            } else if (TdMetaDataListener.NAME_METADATA.equalsIgnoreCase(name)) {
                Bundle msg = new Bundle();
                Map<String, Object> params = (Map<String, Object>) metadata.get(TdMetaDataListener.NAME_METADATA);
                if(params != null) {
                    for (Map.Entry<String, Object> entry : params.entrySet()) {
                        String key = entry.getKey();
                        Object value = entry.getValue();

                        if(value != null) {
                            String valueStr = value.toString();
                            msg.putString(key, valueStr);
                        } else {
                            msg.putString(key, null);
                        }
                    }

                    if (!msg.isEmpty()) {
                        notifyMetadataReceived(msg, delay);
                    }
                }
            }
        }
    }
}
