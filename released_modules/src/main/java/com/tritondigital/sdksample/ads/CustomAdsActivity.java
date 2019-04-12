
package com.tritondigital.sdksample.ads;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.VideoView;

import com.tritondigital.ads.Ad;
import com.tritondigital.ads.AdLoader;
import com.tritondigital.ads.AdRequestBuilder;
import com.tritondigital.sdksample.R;


/**
 * This sample shows a custom UI implementation.
 *
 * - Ads must be loaded at each play request so the server can update the tracking URLs.
 * - No support will be given on custom ad playback.
 * - Make sure to call the tracking methods from AdUtil at the right place or the ads might not be paid.
 */
public class CustomAdsActivity extends AdsActivity implements
        AdLoader.AdLoaderListener, MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener {

    private BannersWrapper mBannersWrapper;
    private VideoView      mVideoView;
    private MediaPlayer    mAudioPlayer;
    private AdLoader       mAdLoader;


    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        mBannersWrapper = new BannersWrapper(findViewById(android.R.id.content));
        initVideoView();

        // Init the ad loader
        mAdLoader = new AdLoader();
        mAdLoader.setListener(this);

        if (savedInstanceState == null) {
            reset();
        }
    }


    @Override
    protected void onResume()
    {
        super.onResume();
        mBannersWrapper.onResume();
    }


    @Override
    protected void onPause()
    {
        mBannersWrapper.onPause();
        super.onPause();
    }


    @Override
    protected void onDestroy()
    {
        clearAd();
        mAdLoader.cancel();
        mBannersWrapper.release();
        super.onDestroy();
    }


    protected void reset() {
        super.reset();
        clearAd();
    }


    @Override
    protected int getLayout()
    {
        return R.layout.ads_custom;
    }


    @Override
    protected void loadAdRequest(AdRequestBuilder adRequestBuilder) {
        clearAd();
        setStatus("Loading ad request");
        mAdLoader.load(adRequestBuilder);
    }


    @Override
    public void onAdLoaded(AdLoader adLoader, Bundle ad)
    {
        if ((ad == null) || ad.isEmpty() || (ad.getString(Ad.URL) == null))
        {
            setStatus("Ad loading ERROR: NULL ad");
        }
        else {
            setStatus("Ad loaded.");
            mBannersWrapper.showAd(ad);

            String mimeType = ad.getString(Ad.MIME_TYPE);
            if (mimeType == null)
            {
                setStatus("Error: MIME type not set");
            }
            else if (mimeType.startsWith("video"))
            {
                playVideoAd(ad);
            }
            else if (mimeType.startsWith("audio"))
            {
                playAudioAd(ad);
            }
        }
    }


    @Override
    public void onAdLoadingError(AdLoader adLoader, int errorCode) {
        setStatus("Ad loading FAILED: " + AdLoader.debugErrorToStr(errorCode));
    }


    private void clearAd()
    {
        // Clear the previous banner content without destroying the view.
        mBannersWrapper.clear();

        // Cancel the video playback
        mVideoView.stopPlayback();
        mVideoView.setOnTouchListener(null);
        mVideoView.setVisibility(View.GONE);

        // Cancel the audio playback
        if (mAudioPlayer != null)
        {
            mAudioPlayer.release();
            mAudioPlayer = null;
        }

        setStatus(null);
    }


    ///////////////////////////////////////////////////////////////////////////
    // Ads playback
    ///////////////////////////////////////////////////////////////////////////

    @Override
    public void onCompletion(MediaPlayer mp)
    {
        clearAd();
        setStatus("Playback completed.");
    }


    @Override
    public boolean onError(MediaPlayer mp, int what, int extra)
    {
        clearAd();
        setStatus("Playback error: " + what + '/' + extra);
        return true;
    }


    private void playAudioAd(final Bundle ad)
    {
        try
        {
            setStatus("Start audio buffering");

            mAudioPlayer = new MediaPlayer();
            mAudioPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mAudioPlayer.setOnCompletionListener(this);
            mAudioPlayer.setOnErrorListener(this);
            mAudioPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener()
            {
                @Override
                public void onPrepared(MediaPlayer mp)
                {
                    setStatus("Start audio playback.");
                    mp.start();
                    Ad.trackImpression(ad);
                }
            });

            String mediaUrl = ad.getString(Ad.URL);
            mAudioPlayer.setDataSource(mediaUrl);
            mAudioPlayer.prepareAsync();
        }
        catch (Exception e)
        {
            setStatus("Audio prepare exception: " + e);
        }
    }


    private void initVideoView()
    {
        mVideoView = (VideoView)findViewById(R.id.videoView1);
        mVideoView.setOnCompletionListener(this);
        mVideoView.setOnErrorListener(this);
        mVideoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener()
        {
            @Override
            public void onPrepared(MediaPlayer mp)
            {
                // IMPORTANT: Track implressions
                mVideoView.start();
                Ad.trackImpression(mAdLoader.getAd());
                setStatus("Starting playback.");
            }
        });
    }


    @SuppressLint("ClickableViewAccessibility")
    private void playVideoAd(final Bundle ad)
    {
        try
        {
            setStatus("Start video buffering");

            // Update the video view layout
            int width  = ad.getInt(Ad.WIDTH);
            int height = ad.getInt(Ad.HEIGHT);
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(width, height);
            mVideoView.setLayoutParams(layoutParams);
            mVideoView.setOnTouchListener(null);
            mVideoView.setVisibility(View.VISIBLE);

            // Handle the video clicks
            final String clickUrl = ad.getString(Ad.VIDEO_CLICK_THROUGH_URL);
            if (clickUrl != null)
            {
                mVideoView.setOnTouchListener(new View.OnTouchListener()
                {
                    // Not using onClickListener because of bugs in some OS versions.
                    @Override
                    public boolean onTouch(View v, MotionEvent event)
                    {
                        if (event.getAction() == MotionEvent.ACTION_DOWN)
                        {
                            setStatus("Video clicked.");

                            // IMPORTANT: track video clicks
                            Ad.trackVideoClick(ad);

                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(clickUrl));
                            startActivity(intent);

                            // We can stop the video ad when the user has clicked it
                            clearAd();
                        }

                        return true;
                    }
                });
            }

            String mediaUrl = ad.getString(Ad.URL);
            mVideoView.setKeepScreenOn(true);
            mVideoView.setVideoURI(Uri.parse(mediaUrl));
            mVideoView.requestFocus();
        }
        catch (Exception e)
        {
            setStatus("Video prepare exception: " + e);
        }
    }
}
