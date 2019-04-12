package com.tritondigital.player;

import android.content.Context;
import android.os.Bundle;

import com.tritondigital.util.Assert;
import com.tritondigital.util.Log;

import java.util.UUID;


/**
 * Side-Band Metadata player.
 *
 * \note For special cases only.
 *
 * This player connects to a Triton server and downloads the cue points linked to an
 * audio stream session. SBM needs to be enabled on the server side.
 *
 * @par Session ID (sbmid)
 * The same session ID (<b>sbmid</b>) must be provided to the audio URL and SBM
 * URL parameters. Use SbmPlayer.generateSbmId() to create it. The current SbmPlayer
 * implementation doesn't support updating the ID; therefore, you must create a new
 * instance on each play request.
 *
 * @par Example
 *
 * @code{.java}
 *     public class SbmPlayerActivity extends Activity implements
 *         MediaPlayer.OnErrorListener, MediaPlayer.OnPreparedListener,
 *         SbmPlayer.OnCuePointReceivedListener
 *     {
 *         private static final String SBM_URL    = "http://1651.live.preprod01.streamtheworld.net:80/HLS_TEST_SBM";
 *         private static final String STREAM_URL = "http://1651.live.preprod01.streamtheworld.net:80/HLS_TEST_SC";
 *
 *         private MediaPlayer mMediaPlayer;
 *         private SbmPlayer   mSbmPlayer;
 *         private boolean     mPlayRequested;
 *
 *         private StreamUrlBuilder mStreamUrlBuilder;
 *
 *
 *         @Override
 *         protected void onCreate(Bundle savedInstanceState) {
 *             super.onCreate(savedInstanceState);
 *             setVolumeControlStream(AudioManager.STREAM_MUSIC);
 *             setContentView(layout);
 *
 *             // Create the Android player
 *             mMediaPlayer = new MediaPlayer();
 *             mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
 *             mMediaPlayer.setOnErrorListener(this);
 *             mMediaPlayer.setOnPreparedListener(this);
 *
 *             // Add your tracking parameters here.
 *             mStreamUrlBuilder = new StreamUrlBuilder(this)
 *                 .enableLocationTracking(true)
 *                 .setHost(STREAM_URL);
 *         }
 *
 *
 *         @Override
 *         protected void onDestroy() {
 *             mMediaPlayer.release();
 *             releaseSbmPlayer();
 *             super.onDestroy();
 *         }
 *
 *
 *         @Override
 *         public boolean onError(MediaPlayer androidPlayer, int what, int extra) {
 *             releaseSbmPlayer();
 *             return false;
 *         }
 *
 *
 *         @Override
 *         public void onPrepared(MediaPlayer androidPlayer) {
 *             if (!isFinishing()) {
 *                 androidPlayer.start();
 *
 *                 if (mSbmPlayer != null) {
 *                     mSbmPlayer.play();
 *                 }
 *             }
 *         }
 *
 *
 *         @Override
 *         public void onCuePointReceived(MediaPlayer sbmPlayer, Bundle cuePoint) {
 *             // Do something here
 *         }
 *
 *
 *         public void startPlayback() {
 *             if (!mPlayRequested) {
 *                 mPlayRequested = true;
 *
 *                 try {
 *                     // Create SBM and player URLS. We calling "mStreamUrlBuilder.build()"
 *                     // every time to refresh the location and the advertising ID.
 *                     String sbmid = SbmPlayer.generateSbmId();
 *                     String streamUrl = mStreamUrlBuilder.build() + "&sbmid=" + sbmid;
 *                     String sbmUrl = SBM_URL + "?sbmid=" + sbmid;
 *
 *                     // Create SBM player
 *                     Bundle sbmPlayerSettings = new Bundle();
 *                     sbmPlayerSettings.putString(SbmPlayer.SETTINGS_STREAM_URL, sbmUrl);
 *                     mSbmPlayer = new SbmPlayer(this, sbmPlayerSettings);
 *                     mSbmPlayer.setOnCuePointReceivedListener(this);
 *
 *                     // Create Android Player
 *                     mMediaPlayer.setDataSource(this, Uri.parse(streamUrl));
 *                     mMediaPlayer.prepareAsync();
 *                 } catch (Exception e) {
 *                     e.printStackTrace();
 *                 }
 *             }
 *         }
 *
 *
 *         public void stopPlayback() {
 *             // Ignore double stop requests.
 *             if (!mPlayRequested) {
 *                 return;
 *             }
 *
 *             mPlayRequested = false;
 *             mMediaPlayer.reset();
 *             releaseSbmPlayer();
 *         }
 *
 *
 *         private void releaseSbmPlayer() {
 *             mCuePointIdx = 0;
 *             mPlayRequested = false;
 *
 *             if (mSbmPlayer != null) {
 *                 mSbmPlayer.release();
 *                 mSbmPlayer = null;
 *             }
 *         }
 *     }
 * @endcode
 *
 */
@SuppressWarnings({"JavaDoc", "unused"})
public final class SbmPlayer extends MediaPlayer {
    /**
     * @copybrief PlayerConsts::SBM_URL
     */
    public static final String SETTINGS_SBM_URL = PlayerConsts.SBM_URL;

    private static final String TAG = Log.makeTag("SbmPlayer");
    private static final int    MAX_ERROR_RECOVERY = 10;

    private SbmSseClient mSseClient;
    private int          mErrorRecoveryCount;


    /**
     * Constructor
     */
    public SbmPlayer(Context context, Bundle settings) {
        super(context, settings);

        String url = settings.getString(SETTINGS_SBM_URL);
        if ((url == null) || !url.startsWith("http")) {
            throw new IllegalArgumentException("Invalid settings.SETTINGS_SBM_URL: \"" + url + "\"");
        } else if (!url.contains("sbmid=")) {
            throw new IllegalArgumentException("settings.SETTINGS_SBM_URL doesn't have a \"sbmid\" parameter: \"" + url + "\"");
        }
    }


    @Override
    protected void internalPlay() {
        setState(STATE_CONNECTING);

        // Recreating a new client every time so we don't have to wait for
        // the previous client to be ready to start a new connection.
        String url = getSettings().getString(SETTINGS_SBM_URL);
        mSseClient = new SbmSseClient(url, mSseClientListener);
    }


    @Override
    protected void internalStop() {
        releaseSseClient();
        setState(STATE_STOPPED);
    }


    @Override
    protected void internalRelease() {
        releaseSseClient();
        setState(STATE_RELEASED);
    }


    // TODO: add a delay
    private void recoverError() {
        if (mErrorRecoveryCount < MAX_ERROR_RECOVERY) {
            mErrorRecoveryCount++;
            internalPlay();
        }
    }


    /**
     * Returns the metadata/audio offset in milliseconds
     */
    public int getOffset() {
        return (mSseClient == null) ? 0 : mSseClient.getOffset();
    }


    /**
     * Sets the offset in milliseconds of to apply to the side band player
     * to synchronise it with you media stream
     */
    public void setOffset(int offset) {
        if (mSseClient != null) {
            mSseClient.setOffset(offset);
        }
    }


    /**
     * Generates a Side-Band Metadata session ID.
     *
     * The same session ID (<b>sbmid</b>) must be provided to the audio URL and SBM URL parameters.
     */
    @SuppressWarnings("UnusedDeclaration")
    public static String generateSbmId() {
        return UUID.randomUUID().toString();
    }


    private void releaseSseClient() {
        if (mSseClient != null) {
            mSseClient.release();
            mSseClient = null;
        }
    }


    // The source validation in callbacks are important to make sure the source
    // being released doesn't interfere with the one being created.
    private final SbmSseClient.SseClientListener mSseClientListener = new SbmSseClient.SseClientListener() {
        @Override
        public void onSbmSseClientCuePointReceived(SbmSseClient sseClient, Bundle cuePoint) {
            if ((mSseClient == sseClient) && (getRequestedAction() == REQUESTED_ACTION_PLAY)) {
                notifyCuePoint(cuePoint);
            }
        }


        @Override
        public void onSbmSseClientStateChanged(SbmSseClient sseClient, int state) {
            if (mSseClient == sseClient) {
                switch (state) {
                    case SbmSseClient.STATE_CONNECTED:
                        setState(STATE_PLAYING);
                        break;

                    case SbmSseClient.STATE_ERROR:
                        setState(STATE_ERROR);
                        recoverError();
                        break;

                    case SbmSseClient.STATE_CONNECTING:
                    case SbmSseClient.STATE_DISCONNECTED:
                        break;

                    default:
                        Assert.failUnhandledValue(TAG, state, "onSbmSseClientStateChanged");
                        break;
                }
            }
        }
    };


    @Override
    protected String makeTag() { return Log.makeTag("SbmPlayer"); }

    @Override
    protected boolean isEventLoggingEnabled() { return true; }

    @Override
    protected void internalPause() {}

    @Override
    protected void internalSeekTo(int position) {}

    @Override
    public boolean isPausable() { return false; }

    @Override
    public int getDuration() { return DURATION_LIVE_STREAM; }

    @Override
    public int getPosition() { return POSITION_UNKNOWN; }

    @Override
    public float getVolume() { return VOLUME_NORMAL; }

    @Override
    public boolean isSeekable() { return false; }

    @Override
    public void setVolume(float volume) {}
}
