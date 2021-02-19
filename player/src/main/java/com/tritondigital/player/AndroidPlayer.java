package com.tritondigital.player;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.AudioManager;
import android.media.TimedText;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import androidx.annotation.NonNull;

import com.tritondigital.net.streaming.proxy.Proxy;
import com.tritondigital.net.streaming.proxy.ProxyFactory;
import com.tritondigital.net.streaming.proxy.decoder.StreamContainerDecoder.MetaDataDecodedListener;
import com.tritondigital.util.Assert;
import com.tritondigital.util.Debug;
import com.tritondigital.util.Log;
import com.tritondigital.util.NetworkUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;


/**
 * Wraps Android's native player
 *
 * TODO: fix volume
 */
public class AndroidPlayer extends MediaPlayer
{
    /** @copybrief Const.STREAM_URL */
    public static final String SETTINGS_STREAM_URL = PlayerConsts.STREAM_URL;

    /** @copybrief Const.MIME_TYPE
     * Used only for FLV streams */
    public static final String SETTINGS_MIME_TYPE = PlayerConsts.MIME_TYPE;

    /** @copybrief Const.TRANSPORT
     * Use only for FLV streams */
    public static final String SETTINGS_TRANSPORT = PlayerConsts.TRANSPORT;

    /** @copybrief Const.USER_AGENT */
    public static final String SETTINGS_USER_AGENT = PlayerConsts.USER_AGENT;

    /** @copybrief PlayerConsts.POSITION */
    public static final String SETTINGS_POSITION = PlayerConsts.POSITION;


    private static final int CALLBACK_CUE_POINT_RECEIVED = 60;
    private static final int CALLBACK_ON_INFO            = 61;
    private static final int CALLBACK_HANDLER_READY      = 62;
    private static final int CALLBACK_STATE_CHANGED      = 63;

    private static final String TAG = Log.makeTag("AndroidPlayer");

    private final ArrayList<Message> mMessageQueue = new ArrayList<>();
    private final MainHandler        mMainHandler;
    private volatile PlayerHandler   mPlayerHandler;


    /**
     * Constructor
     */
    public AndroidPlayer(@NonNull final Context context, @NonNull final Bundle settings) {
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
        if (!NetworkUtil.isNetworkConnected(getContext())) {
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
    public void setVolume(float volume) {
        sendPlayerMsg(PlayerHandler.ACTION_SET_VOLUME, 0, volume);
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


    private static class MainHandler extends Handler {
        final AndroidPlayer mAndroidPlayer;

        MainHandler(AndroidPlayer androidPlayer) {
            mAndroidPlayer = androidPlayer;
        }


        @Override
        public void handleMessage(Message msg) {
            if ((mAndroidPlayer != null) && (mAndroidPlayer.getState() != STATE_RELEASED)) {
                switch (msg.what) {
                    case CALLBACK_CUE_POINT_RECEIVED:
                        mAndroidPlayer.onCuePointReceived((Bundle) msg.obj);
                        break;

                    case CALLBACK_ON_INFO:
                        mAndroidPlayer.onInfo(msg.arg1, msg.arg2);
                        break;

                    case CALLBACK_HANDLER_READY:
                        mAndroidPlayer.onPlayerHandlerReady();
                        break;

                    case CALLBACK_STATE_CHANGED:
                        mAndroidPlayer.onStateChanged(msg.arg1, msg.arg2);
                        break;

                    default:
                        Assert.failUnhandledValue(TAG, msg.what, "handleMessage");
                        break;
                }
            }
        }
    }


    private static class PlayerHandler extends Handler implements
            android.media.MediaPlayer.OnCompletionListener, android.media.MediaPlayer.OnErrorListener,
            android.media.MediaPlayer.OnSeekCompleteListener, android.media.MediaPlayer.OnInfoListener {

        static final int ACTION_PAUSE           = 350;
        static final int ACTION_PLAY            = 351;
        static final int ACTION_RELEASE         = 352;
        static final int ACTION_SEEK_TO         = 353;
        static final int ACTION_SET_VOLUME      = 354;
        static final int ACTION_POLL_IS_PLAYING = 355;


        private final Context mContext;
        private final Handler mMainHandler;
        private final Bundle  mSettings;

        private static final String TAG = Log.makeTag("AndroidPlayerBkg");

        private Proxy                     mStreamingProxy;
        private android.media.MediaPlayer mNativePlayer;

        private int   mDuration = DURATION_UNKNOWN;
        private float mVolume   = VOLUME_NORMAL;
        private boolean mFinishing;
        private boolean mPlayPollingEnabled;


        PlayerHandler(Context context, Handler mainHandler, Bundle settings) {
            mContext     = context;
            mMainHandler = mainHandler;
            mSettings    = settings;

            // Notify the background thread is ready to receive commands
            Message msg = mMainHandler.obtainMessage(CALLBACK_HANDLER_READY);
            mMainHandler.sendMessageDelayed(msg, 50);
        }


        @Override
        public void handleMessage(Message msg) {
            if (!mFinishing) {
                switch (msg.what) {
                    case ACTION_PAUSE:           pause();                    break;
                    case ACTION_PLAY:            play();                     break;
                    case ACTION_RELEASE:         release();                  break;
                    case ACTION_SEEK_TO:         seekTo(msg.arg1);           break;
                    case ACTION_SET_VOLUME:      setVolume((Float) msg.obj); break;
                    case ACTION_POLL_IS_PLAYING: pollIsPlaying();            break;

                    default:
                        Assert.failUnhandledValue(TAG, msg.what, "PlayerHandler.handleMessage");
                        break;
                }
            }
        }


        private void play() {
            try {
                if (mNativePlayer == null) {
                    notifyStateChanged(STATE_CONNECTING);

                    // Create stream URL
                    String streamUrl = mSettings.getString(SETTINGS_STREAM_URL);

                    if (streamUrl == null) {
                        notifyStateChanged(STATE_ERROR, ERROR_INVALID_URL);
                        return;
                    }

                    // Create the Android media player
                    mNativePlayer = new android.media.MediaPlayer();
                    mNativePlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                    mNativePlayer.setWakeMode(mContext, PowerManager.PARTIAL_WAKE_LOCK);
                    mNativePlayer.setOnCompletionListener(this);
                    mNativePlayer.setOnErrorListener(this);
                    mNativePlayer.setOnInfoListener(this);
                    mNativePlayer.setOnSeekCompleteListener(this);

                    // Create the RTSP proxy
                    String transport = mSettings.getString(SETTINGS_TRANSPORT);
                    if (PlayerConsts.TRANSPORT_FLV.equals(transport)) {
                        mStreamingProxy = createStreamingProxy();
                        String proxyUrl = mStreamingProxy.startAsync(streamUrl);

                        if (proxyUrl == null) {
                            notifyStateChanged(STATE_ERROR, ERROR_INVALID_URL);
                            return;
                        }

                        Log.d(TAG, "Proxy URL: " + proxyUrl);
                        mNativePlayer.setDataSource(proxyUrl);
                    }
                    else {
                        Uri uri = Uri.parse(streamUrl);
                        setDataSource(uri);
                    }

                    // Prepare the stream
                    Log.d(TAG, "Prepare native player for: " + streamUrl);
                    mNativePlayer.prepare();
                    mNativePlayer.setVolume(mVolume, mVolume);

                    int position = mSettings.getInt(SETTINGS_POSITION);
                    if (position > 0) {
                        mNativePlayer.seekTo(position);
                    }

                    // Start the playback
                    Log.d(TAG, "Start native player's playback");
                    mNativePlayer.start();
                    debugEnableExtraLogs();

                    if (PlayerConsts.MIME_TYPE_AAC.equals(mSettings.getString(SETTINGS_MIME_TYPE))) {
                        mPlayPollingEnabled = true;
                        pollIsPlaying();
                    } else {
                        // The duration stays to 0 for MP3 on some devices
                        onPlaybackStarted();
                    }
                } else {
                    mNativePlayer.start();
                    onPlaybackStarted();
                }

            } catch (IOException e) {
                // Occurs when the stream is not found.
                Log.e(TAG, e, "play()");
                notifyStateChanged(STATE_ERROR, ERROR_CONNECTION_FAILED);

            } catch (IllegalStateException | IllegalArgumentException | SecurityException e) {
                Log.e(TAG, e, "play()");
                notifyStateChanged(STATE_ERROR, ERROR_LOW_LEVEL_PLAYER_ERROR);
            }
        }


        private void pause() {
            if (mNativePlayer != null) {
                try {
                    mNativePlayer.pause();
                    notifyStateChanged(STATE_PAUSED);
                } catch (Exception e) {
                    Log.w(TAG, e, "pause()");
                }
            }
        }


        private void release() {
            try {
                if (mNativePlayer != null) {
                    mNativePlayer.release();
                    mNativePlayer = null;
                }

                mPlayPollingEnabled = false;
                mFinishing = true;
                releaseStreamingProxy();
                getLooper().quit();
            } catch (Exception e) {
                Log.w(TAG, e, "release()");
            }
        }


        @Override
        public void onCompletion(android.media.MediaPlayer mp) {
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
        public boolean onError(android.media.MediaPlayer mp, int what, int extra) {
            mPlayPollingEnabled = false;
            Log.e(TAG, "Native player error: " + debugErrorWhatToStr(what) + " / extra: " + extra);
            notifyStateChanged(STATE_ERROR, ERROR_LOW_LEVEL_PLAYER_ERROR);
            return true;
        }


        @Override
        public boolean onInfo(android.media.MediaPlayer mp, int what, int extra) {
            Log.i(TAG, "onInfo what:" + debugInfoToStr(what) + " / extra:" + extra);

            switch (what) {
                case android.media.MediaPlayer.MEDIA_INFO_BUFFERING_START:
                    notifyInfo(INFO_BUFFERING_START);
                    return true;

                case android.media.MediaPlayer.MEDIA_INFO_BUFFERING_END:
                    notifyInfo(INFO_BUFFERING_COMPLETED);
                    onPlaybackStarted();
                    return true;
            }

            return false;
        }


        @Override
        public void onSeekComplete(android.media.MediaPlayer androidPlayer) {
            notifyInfo(INFO_SEEK_COMPLETED, androidPlayer.getCurrentPosition());
        }


        private void onDurationChanged(int duration) {
            duration = StreamPlayer.normalizeDuration(duration);

            if (mDuration != duration) {
                mDuration = duration;
                notifyInfo(INFO_DURATION_CHANGED, duration);
            }
        }


        private void onPlaybackStarted() {
            if (mNativePlayer != null) {
                proxyOnAudioPlaying();
                onDurationChanged(mNativePlayer.getDuration());
                notifyStateChanged(STATE_PLAYING);
            }
        }


        private void notifyCuePointReceived(Bundle cuePoint) {
            Message msg = mMainHandler.obtainMessage(CALLBACK_CUE_POINT_RECEIVED, cuePoint);
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
            if (mFinishing || (mNativePlayer == null) || !mPlayPollingEnabled) {
                return;
            }

            int position = mNativePlayer.getCurrentPosition();
            Log.v(TAG, "Stream Position: " + position + " ms");

            if (position > 0) {
                onPlaybackStarted();
            } else {
                Message msg = obtainMessage(ACTION_POLL_IS_PLAYING);
                sendMessageDelayed(msg, 250);
            }
        }


        private void seekTo(int position) {
            if (mNativePlayer != null) {
                try {
                    notifyInfo(INFO_SEEK_STARTED);
                    mNativePlayer.seekTo(position);
                } catch (IllegalStateException e) {
                    Log.w(TAG, e, "seekTo");
                }
            }
        }


        public int getPosition() {
            if (mNativePlayer != null) {
                try {
                    return mNativePlayer.getCurrentPosition();
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
            if (mNativePlayer != null) {
                mVolume = volume;
                mNativePlayer.setVolume(volume, volume);
            }
        }


        @TargetApi(14)
        private void setDataSource(Uri uri) throws IllegalArgumentException, SecurityException, IllegalStateException, IOException {
            // Add the user agent for OS versions where it is supported.
            if (android.os.Build.VERSION.SDK_INT >= 14) {
                String userAgent = mSettings.getString(SETTINGS_USER_AGENT);
                if (userAgent != null) {
                    Map<String, String> headerMap = new HashMap<>();
                    headerMap.put("User-Agent", userAgent);
                    mNativePlayer.setDataSource(mContext, uri, headerMap);
                    return;
                }
            }

            mNativePlayer.setDataSource(mContext, uri);
        }


        public static String debugErrorWhatToStr(int what)
        {
            switch (what)
            {
                case android.media.MediaPlayer.MEDIA_ERROR_IO:          return "MEDIA_ERROR_IO";
                case android.media.MediaPlayer.MEDIA_ERROR_MALFORMED:   return "MEDIA_ERROR_MALFORMED";
                case android.media.MediaPlayer.MEDIA_ERROR_SERVER_DIED: return "MEDIA_ERROR_SERVER_DIED";
                case android.media.MediaPlayer.MEDIA_ERROR_TIMED_OUT:   return "MEDIA_ERROR_TIMED_OUT";
                case android.media.MediaPlayer.MEDIA_ERROR_UNKNOWN:     return "MEDIA_ERROR_UNKNOWN";
                case android.media.MediaPlayer.MEDIA_ERROR_UNSUPPORTED: return "MEDIA_ERROR_UNSUPPORTED";
                case android.media.MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK:
                    return "MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK";

                default:
                    return "Other";
            }
        }


        public static String debugInfoToStr(int what)
        {
            switch (what)
            {
                case android.media.MediaPlayer.MEDIA_INFO_BAD_INTERLEAVING:      return "BAD_INTERLEAVING";
                case android.media.MediaPlayer.MEDIA_INFO_BUFFERING_END:         return "BUFFERING_END";
                case android.media.MediaPlayer.MEDIA_INFO_BUFFERING_START:       return "BUFFERING_START";
                case android.media.MediaPlayer.MEDIA_INFO_METADATA_UPDATE:       return "METADATA_UPDATE";
                case android.media.MediaPlayer.MEDIA_INFO_NOT_SEEKABLE:          return "NOT_SEEKABLE";
                case android.media.MediaPlayer.MEDIA_INFO_SUBTITLE_TIMED_OUT:    return "SUBTITLE_TIMED_OUT";
                case android.media.MediaPlayer.MEDIA_INFO_UNKNOWN:               return "UNKNOWN";
                case android.media.MediaPlayer.MEDIA_INFO_UNSUPPORTED_SUBTITLE:  return "UNSUPPORTED_SUBTITLE";
                case android.media.MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START: return "VIDEO_RENDERING_START";
                case android.media.MediaPlayer.MEDIA_INFO_VIDEO_TRACK_LAGGING:   return "VIDEO_TRACK_LAGGING";
                default:                                                         return "Other";
            }
        }


        ////////////////////////////////////////////////////////////////////////////////////////////
        // Streaming Proxy
        ////////////////////////////////////////////////////////////////////////////////////////////

        private Proxy createStreamingProxy() {
            Proxy streamingProxy;

            MetaDataDecodedListener metaDataListener = new MetaDataDecodedListener() {
                @Override
                public void onMetaDataDecoded(Map<String, Object> metaData) {
                    Bundle cuePoint = decodeCuePoint(metaData);
                    notifyCuePointReceived(cuePoint);
                }
            };

            // The streaming proxy is required for an FLV stream.
            String mimeType = mSettings.getString(SETTINGS_MIME_TYPE);
            if (PlayerConsts.MIME_TYPE_MPEG.equals(mimeType)) {
                streamingProxy = ProxyFactory.createHttpProxy(metaDataListener);
            } else {
                streamingProxy = ProxyFactory.createRtspProxy(metaDataListener);
            }

            String userAgent = mSettings.getString(SETTINGS_USER_AGENT);
            if ((userAgent != null) && (streamingProxy != null)) {
                streamingProxy.getClient().setUserAgent(userAgent);
            }

            com.tritondigital.net.streaming.proxy.utils.Log.setEnabled(false);
            return streamingProxy;
        }


        private void releaseStreamingProxy() {
            if (mStreamingProxy != null) {
                mStreamingProxy.stop();
            }
        }


        private void proxyOnAudioPlaying() {
            if (mStreamingProxy != null) {
                mStreamingProxy.audioPlaybackDidStart();
            }
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
                    decodeAndoString(params, "Album",     cuePoint, CuePoint.TRACK_ALBUM_NAME);
                    decodeAndoString(params, "Artist",    cuePoint, CuePoint.TRACK_ARTIST_NAME);
                    decodeAndoString(params, "IMGURL",    cuePoint, CuePoint.TRACK_COVER_URL);
                    decodeAndoString(params, "Label",     cuePoint, CuePoint.TRACK_ALBUM_PUBLISHER);
                    decodeAndoString(params, "Title",     cuePoint, CuePoint.CUE_TITLE);
                    decodeAndoString(params, "BuyNowURL", cuePoint, CuePoint.LEGACY_BUY_URL);
                    break;

                case "Ads":
                    cuePoint.putString(CuePoint.CUE_TYPE, CuePoint.CUE_TYPE_VALUE_AD);
                    decodeAndoString(params, "BREAKADID", cuePoint, CuePoint.AD_ID);
                    decodeAndoString(params, "BREAKTYPE", cuePoint, CuePoint.AD_TYPE);
                    decodeAndoString(params, "IMGURL",    cuePoint, CuePoint.LEGACY_AD_IMG_URL);
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


        ////////////////////////////////////////////////////////////////////////////////////////////
        // Logs
        ////////////////////////////////////////////////////////////////////////////////////////////

        @TargetApi(16)
        private void debugEnableExtraLogs() {
            if (Debug.isDebugMode() && (android.os.Build.VERSION.SDK_INT >= 16)) {

                mNativePlayer.setOnTimedTextListener(new android.media.MediaPlayer.OnTimedTextListener() {
                    @Override
                    public void onTimedText(android.media.MediaPlayer mp, TimedText text) {
                        Log.d(TAG, "onTimedText: " + text);
                    }
                });

                try {
                    android.media.MediaPlayer.TrackInfo[] tracks = mNativePlayer.getTrackInfo();
                    if (tracks != null) {
                        final int TRACK_COUNT = tracks.length;
                        if (TRACK_COUNT == 0) {
                            Log.d(TAG, "No tracks");
                        } else {
                            for (int i = 0; i < TRACK_COUNT; i++) {
                                int trackType = tracks[i].getTrackType();
                                Log.d(TAG, "Track " + i + ": " + debugTrackInfoToStr(trackType));
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.v(TAG, "No tracks");
                }
            }
        }


        private static String debugTrackInfoToStr(int trackInfo) {
            switch (trackInfo) {
                case android.media.MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_AUDIO:     return "audio";
                case android.media.MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_SUBTITLE:  return "subtitle";
                case android.media.MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_TIMEDTEXT: return "timed text";
                case android.media.MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_VIDEO:     return "video";
                case android.media.MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_UNKNOWN:
                default:                                                             return "unknown";
            }
        }


        static String debugActionToStr(int action) {
            switch (action) {
                case ACTION_PAUSE:           return "ACTION_PAUSE";
                case ACTION_PLAY:            return "ACTION_PLAY";
                case ACTION_RELEASE:         return "ACTION_RELEASE";
                case ACTION_SEEK_TO:         return "ACTION_SEEK_TO";
                case ACTION_SET_VOLUME:      return "ACTION_SET_VOLUME";
                case ACTION_POLL_IS_PLAYING: return "ACTION_POLL_IS_PLAYING";
                default:
                    Assert.failUnhandledValue(TAG, action, "debugActionToStr");
                    return "UNKNOWN";
            }
        }
    }
}
