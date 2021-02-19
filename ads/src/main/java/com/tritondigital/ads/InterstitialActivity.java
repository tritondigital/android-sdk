package com.tritondigital.ads;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.VideoView;

import com.tritondigital.util.*;

import java.util.ArrayList;


/**
 * To be used only by class Interstitial. Not to be used externally.
 */
public final class InterstitialActivity extends Activity implements
        MediaPlayer.OnErrorListener, MediaPlayer.OnPreparedListener,
        MediaPlayer.OnCompletionListener, AudioManager.OnAudioFocusChangeListener {
    public static final String ACTION_CLOSED = "com.tritondigital.ads.InterstitialActivity.ACTION_CLOSED";
    public static final String ACTION_ERROR  = "com.tritondigital.ads.InterstitialActivity.ACTION_ERROR";
    public static final String ACTION_FINISHED = "com.tritondigital.ads.InterstitialActivity.ACTION_FINISHED";

    public static final String EXTRA_AD           = "com.tritondigital.ads.EXTRA_AD";
    public static final String EXTRA_ERROR_CODE   = "com.tritondigital.ads.EXTRA_ERROR_CODE";
    public static final String EXTRA_REQUEST_CODE = "com.tritondigital.ads.EXTRA_REQUEST_CODE";

    // Args and result
    private Bundle mAd;
    private int    mRequestCode;
    private int    mErrorCode;
    private boolean mPlaybackFinished;

    private ProgressBar              mProgressBar;
    private ViewGroup.LayoutParams   mMatchParentLayoutParam;
    private FrameLayout.LayoutParams mWrapContentCenteredLayoutParam;
    private BannerView               mAudioAdBanner;
    private VideoView                mVideoView;

    // Other
    private static final int[] BANNER_SIZES[] = {{320, 480}, {300, 300}, {300, 250}, {320, 50}, {300, 50}, {180, 150}};
    private static final String TAG = Log.makeTag("InterstitialActivity");
    private MediaPlayer  mAudioPlayer;
    private AudioManager mAudioManager;


    ///////////////////////////////////////////////////////////////////////////
    // Life Cycle
    ///////////////////////////////////////////////////////////////////////////

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        Bundle args  = getIntent().getExtras();
        mAd          = args.getBundle(EXTRA_AD);
        mRequestCode = args.getInt(EXTRA_REQUEST_CODE);
        mPlaybackFinished = false;

        initAudioManager();
        lockOrientation();
        initLayout();
    }


    @Override
    protected void onStart() {
        super.onStart();

        // Get the media URL
        String mediaUrl = mAd.getString(Ad.URL);
        if (!isHttpOrHttpsUrl(mediaUrl)) {
            Log.e(TAG, "Invalid media URL:  " + mediaUrl);
            finishWithError(Interstitial.ERROR_INVALID_MEDIA_URL);
            return;
        }

        if (mVideoView != null) {
            // Begin buffering the video ad.
            Log.d(TAG, "Buffering: " + mediaUrl);

            mVideoView.setKeepScreenOn(true);
            mVideoView.setVideoURI(Uri.parse(mediaUrl));
            mVideoView.requestFocus();

        } else if (mAudioPlayer == null) {
            // Begin buffering the audio ad. Ignore if coming back from background.
            try {
                mAudioPlayer = new MediaPlayer();
                mAudioPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                mAudioPlayer.setOnCompletionListener(this);
                mAudioPlayer.setOnErrorListener(this);
                mAudioPlayer.setOnPreparedListener(this);
                mAudioPlayer.setDataSource(mediaUrl);
                mAudioPlayer.prepareAsync();

            } catch (Exception e) {
                Log.e(TAG, e, "MediaPlayer.setDataSource() exception");
                finishWithError(Interstitial.ERROR_MEDIA_PLAYER_ERROR);
            }
        }
    }


    @Override
    protected void onPause() {
        // For videos, skip the ad when the app goes to background.
        if (mVideoView != null) {
            finishWithSuccess();
        }

        super.onPause();
    }


    @Override
    public void onDestroy() {
        if (mAudioManager != null) {
            mAudioManager.abandonAudioFocus(this);
            mAudioManager = null;
        }

        if (mAudioAdBanner != null) {
            mAudioAdBanner.release();
            mAudioAdBanner = null;
        }

        if (mVideoView != null) {
            mVideoView.stopPlayback();
            mVideoView.setOnClickListener(null);
            mVideoView.setOnCompletionListener(null);
            mVideoView.setOnErrorListener(null);
            mVideoView.setOnPreparedListener(null);
            mVideoView = null;
            broadcastPlaybackFinished();
        }

        // Broadcast the ad has finished
        Intent i = new Intent((mErrorCode > 0) ? ACTION_ERROR : ACTION_CLOSED);
        i.putExtra(EXTRA_ERROR_CODE,   mErrorCode);
        i.putExtra(EXTRA_REQUEST_CODE, mRequestCode);
        sendBroadcast(i);

        super.onDestroy();
    }


    private void finishWithError(int error) {
        // Handle only the first error.
        if (mErrorCode == 0) {
            mErrorCode = error;
            finishWithSuccess();
        }

    }


    private void finishWithSuccess() {
        // Handle only the first error.
        if (mErrorCode == 0) {
            finish();
        }
        playbackFinished();
    }

    private void playbackFinished() {

        if (mAudioPlayer != null) {

            if (mPlaybackFinished == true) {
                    mAudioPlayer.release();
                    mAudioPlayer = null;
                    mAudioPlayer = null;
                    broadcastPlaybackFinished();
            }
        }
    }

    private void broadcastPlaybackFinished() {

        Intent i = new Intent(ACTION_FINISHED);
        i.putExtra(EXTRA_ERROR_CODE,   mErrorCode);
        i.putExtra(EXTRA_REQUEST_CODE, mRequestCode);
        sendBroadcast(i);
    }

    ///////////////////////////////////////////////////////////////////////////
    // UI
    ///////////////////////////////////////////////////////////////////////////

    private void initLayout() {
        // Main layout
        mMatchParentLayoutParam = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);

        mWrapContentCenteredLayoutParam = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER);

        FrameLayout frameLayout = new FrameLayout(this);
        frameLayout.setBackgroundColor(Color.BLACK);

        // Audio or video ad layout
        String mimeType = mAd.getString(Ad.MIME_TYPE);
        if (mimeType == null) {
            Log.e(TAG, "MIME type not set");
            finishWithError(Interstitial.ERROR_UNSUPPORTED_MIME_TYPE);
            return;
        }

        if (mimeType.startsWith("audio")) {
            initAudioAdLayout(frameLayout);
        } else if (mimeType.startsWith("video")) {
            initVideoAdLayout(frameLayout);
        } else {
            Log.e(TAG, "Unsupported MIME type: " + mimeType);
            finishWithError(Interstitial.ERROR_UNSUPPORTED_MIME_TYPE);
            return;
        }

        // Progress bar and close button
        addProgressBar(frameLayout);
        addCloseButton(frameLayout);
        setContentView(frameLayout, mMatchParentLayoutParam);
    }


    /**
     * Initialize the layout for an audio ad.
     */
    private void initAudioAdLayout(FrameLayout parent) {
        ArrayList<Bundle> banners = mAd.getParcelableArrayList(Ad.BANNERS);
        if ((banners != null) && !banners.isEmpty()) {
            // Display the best banner size of know sizes.
            for (int[] bannerSize : BANNER_SIZES) {
                if (hasBannerSize(banners, bannerSize[0], bannerSize[1])) {
                    addBanner(parent, bannerSize[0], bannerSize[1]);
                    return;
                }
            }

            // Banner size not found. Take the first banner smaller than the screen.
            DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
            float density = displayMetrics.density;
            int screenWidth  = (int)(displayMetrics.widthPixels  / density);
            int screenHeight = (int)(displayMetrics.heightPixels / density);

            for (Bundle banner : banners) {
                int bannerWidth  = banner.getInt(Ad.WIDTH);
                int bannerHeight = banner.getInt(Ad.HEIGHT);

                if ((bannerWidth <= screenWidth) && (bannerHeight <= screenHeight)) {
                    addBanner(parent, bannerWidth, bannerHeight);
                    return;
                }
            }
        }

        // Fallback to the ad title
        addTitleTextView(parent);
    }


    private void addTitleTextView(FrameLayout parent) {
        TextView textView = new TextView(this);
        String title = mAd.getString(Ad.TITLE);
        textView.setText(title);
        parent.addView(textView, mWrapContentCenteredLayoutParam);
    }


    private void addBanner(FrameLayout parent, int width, int height) {
        mAudioAdBanner = new BannerView(this);
        mAudioAdBanner.setBannerSize(width, height);
        mAudioAdBanner.showAd(mAd);
        parent.addView(mAudioAdBanner, mWrapContentCenteredLayoutParam);
    }


    static boolean hasBannerSize(ArrayList<Bundle> banners, int width, int height) {
        if (banners != null) {
            for (Bundle banner : banners) {
                if ((banner != null) &&
                        (banner.getInt(Ad.WIDTH)  == width) &&
                        (banner.getInt(Ad.HEIGHT) == height)) {
                    return true;
                }
            }
        }

        return false;
    }


    /**
     * Initialize the layout for a video ad.
     */
    private void initVideoAdLayout(FrameLayout parent) {
        // Video view
        mVideoView = new VideoView(this);
        mVideoView.setOnCompletionListener(this);
        mVideoView.setOnErrorListener(this);
        mVideoView.setOnPreparedListener(this);
        parent.addView(mVideoView, mWrapContentCenteredLayoutParam);

        // Click through button
        final String clickThroughUrl = mAd.getString(Ad.VIDEO_CLICK_THROUGH_URL);
        if (isHttpOrHttpsUrl(clickThroughUrl)) {
            Button clickThroughButton = new Button(this);
            clickThroughButton.setBackgroundColor(Color.TRANSPARENT);
            parent.addView(clickThroughButton, mMatchParentLayoutParam);

            clickThroughButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Ad.trackVideoClick(mAd);

                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(clickThroughUrl));
                    startActivity(intent);

                    // TODO: call this only when coming back from browser
                    finishWithSuccess();
                }
            });
        }
    }


    /**
     * Adds a progress bar to the main layout.
     */
    private void addProgressBar(FrameLayout parent) {
        mProgressBar = new ProgressBar(this);
        parent.addView(mProgressBar, mWrapContentCenteredLayoutParam);
    }


    /**
     * Adds a close button to the main layout.
     */
    private void addCloseButton(final FrameLayout parent) {
        final Button closeBtn = new Button(this);
        closeBtn.setPadding(0, 0, 0, 0);
        closeBtn.setText("\u00D7");
        closeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mPlaybackFinished = false;
                finishWithSuccess();
            }
        });

        final float scale = DisplayUtil.getDeviceDensityPixelScale(this);
        final int densitySize = DisplayUtil.densityPixelsToPixels(scale, 32);
        FrameLayout.LayoutParams wrapContentParam = new FrameLayout.LayoutParams(densitySize, densitySize);
        parent.addView(closeBtn, wrapContentParam);
    }


    /**
     * Locks the orientation to the ad's orientation.
     */
    private void lockOrientation() {
        String mimeType = mAd.getString(Ad.MIME_TYPE);
        if ((mimeType != null) && mimeType.startsWith("video")) {
            int width  = mAd.getInt(Ad.WIDTH);
            int height = mAd.getInt(Ad.HEIGHT);
            setRequestedOrientation((width > height) ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
    }


    ///////////////////////////////////////////////////////////////////////////
    // Playback
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Initialize the audio system.
     */
    private void initAudioManager() {
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mAudioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
    }


    @Override
    public void onPrepared(MediaPlayer mediaPlayer) {
        Log.d(TAG, "Buffering completed. Starting playback.");

        if (mAudioPlayer == mediaPlayer) {
            mAudioPlayer.start();
        } else if (mVideoView != null) {
            mVideoView.start();
        }

        // Track the impression
        Ad.trackImpression(mAd);

        // Hide progress bar
        Animation fadeOutAnimation = AnimationUtils.loadAnimation(this, android.R.anim.fade_out);
        mProgressBar.startAnimation(fadeOutAnimation);
        mProgressBar.setVisibility(View.GONE);
    }


    @Override
    public boolean onError(MediaPlayer mediaPlayer, int what, int extra) {
        Log.w(TAG, "Media player error: " + what + "/" + extra);

        if (mAd != null) {
            Log.w(TAG, "   URL: " + mAd.getString(Ad.URL));
        }

        finishWithError(Interstitial.ERROR_MEDIA_PLAYER_ERROR);

        return true;
    }


    @Override
    public void onCompletion(MediaPlayer mediaPlayer) {
        mPlaybackFinished = true;
        finishWithSuccess();
    }


    @Override
    public void onAudioFocusChange(int focusChange) {}


    /**
     * Returns true if the provided URL is "http" of "https"
     */
    static boolean isHttpOrHttpsUrl(String url) {
        return (null != url) &&
               (url.length() > 6) &&
               url.substring(0, 4).equalsIgnoreCase("http") &&
                url.contains("://");
    }
}
