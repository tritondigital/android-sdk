package com.tritondigital.sdksample.player;

import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import com.tritondigital.player.SbmPlayer;
import com.tritondigital.player.StreamUrlBuilder;
import com.tritondigital.sdksample.R;
import com.tritondigital.sdksample.ads.BannersWrapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * This example shows how to receive metadata when creating a custom player.
 *
 * IMPORTANT:
 * - NOT RECOMMENDED. You should use TritonPlayer instead
 * - No support will be provided by Triton Mobile Team when using a custom player.
 * - The SbmPlayer must be recreated on each play request.
 */
public class SbmPlayerActivity extends AppCompatActivity implements
        com.tritondigital.player.MediaPlayer.OnCuePointReceivedListener,
        com.tritondigital.player.MediaPlayer.OnStateChangedListener,
        MediaPlayer.OnErrorListener, MediaPlayer.OnPreparedListener,
        View.OnClickListener {


    private static final String[] URLS = {
            "http://20203.live.streamtheworld.com:80/S1_HLS_AAC_SC"
    };

    // UI
    private TextView            mSbmPlayerView;
    private TextView            mAndroidPlayerView;
    private ListView            mListView;
    private BannersWrapper      mBannersWrapper;
    private List<String[]>      mListItems = new ArrayList<>();
    private StreamUrlBuilder    mStreamUrlBuilder;
    private SbmPlayer           mSbmPlayer;
    private MediaPlayer         mAndroidPlayer;
    private Spinner             mUrlSpinner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        setContentView(R.layout.player_sbm);

        mBannersWrapper = new BannersWrapper(findViewById(android.R.id.content));

        findViewById(R.id.button_play).setOnClickListener(this);
        findViewById(R.id.button_stop).setOnClickListener(this);
        findViewById(R.id.button_reset).setOnClickListener(this);

        ((TextView)findViewById(android.R.id.text1)).setText("Android Player: ");
        ((TextView)findViewById(android.R.id.text2)).setText("SBM Player: ");

        ArrayAdapter<String> urlAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, URLS);
        urlAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mUrlSpinner = findViewById(R.id.spinner_url);
        mUrlSpinner.setAdapter(urlAdapter);


        mAndroidPlayerView = findViewById(R.id.textView_playerState);
        mSbmPlayerView     = findViewById(R.id.textView_playerInfo);
        mListView          = findViewById(android.R.id.list);
        mListView.setAdapter(new SimpleListItem2Adapter(this, mListItems));

        // TODO: Add your tracking parameters here.
        mStreamUrlBuilder = new StreamUrlBuilder(this)
                .enableLocationTracking(true);

        if (savedInstanceState == null) {
            reset();
        }
    }


    @Override
    protected void onResume() {
        super.onResume();
        mBannersWrapper.onResume();
    }


    @Override
    protected void onPause() {
        mBannersWrapper.onPause();
        super.onPause();
    }


    @Override
    protected void onDestroy() {
        mBannersWrapper.release();
        releasePlayers();
        super.onDestroy();
    }


    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.button_play) {
            startPlayer();
        } else if (id == R.id.button_stop) {
            releasePlayers();
        } else if (id == R.id.button_reset) {
            reset();
        }
    }


    private void reset() {
        mUrlSpinner.setSelection(0);
        releasePlayers();
    }


    private void releasePlayers() {
        // Release the SBM player
        if (mSbmPlayer != null) {
            mSbmPlayer.release();
            mSbmPlayer = null;
        }

        // Release the Android player
        if (mAndroidPlayer != null)
        {
            mAndroidPlayer.release();
            mAndroidPlayer = null;
        }

        // Update the UI
        setAndroidPlayerInfo(null);
        setSbmPlayerInfo(null);
    }


    private void startPlayer() {
        // Release current players
        releasePlayers();

        // Create the SBM and stream URLS
        mStreamUrlBuilder.setHost(getRawUrl());
        mStreamUrlBuilder.addQueryParameter("sbmid", SbmPlayer.generateSbmId());
        String streamUrl = mStreamUrlBuilder.build();
        Log.i("SbmPlayerActivity", "Stream url: " + streamUrl);

        //
        // Create a new SBM Player
        //
        try {
            setSbmPlayerInfo("Preparing for playback");
            String sbmUrl = createSbmUrl(streamUrl);
            Log.i("SbmPlayerActivity", "SBM url: " + sbmUrl);

            Bundle sbmSettings = new Bundle();
            sbmSettings.putString(SbmPlayer.SETTINGS_SBM_URL, sbmUrl);

            mSbmPlayer = new SbmPlayer(this, sbmSettings);
            mSbmPlayer.setOnCuePointReceivedListener(this);
            mSbmPlayer.setOnStateChangedListener(this);
        } catch (Exception e) {
            setSbmPlayerInfo("Creation exception: " + e);
        }

        //
        // Create the Android player
        //
        try {
            setAndroidPlayerInfo("Preparing for playback");

            mAndroidPlayer = new MediaPlayer();
            mAndroidPlayer.setDataSource(this, Uri.parse(streamUrl));
            mAndroidPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mAndroidPlayer.setOnErrorListener(this);
            mAndroidPlayer.setOnPreparedListener(this);
            mAndroidPlayer.prepareAsync();
        } catch (IOException e) {
            setAndroidPlayerInfo("Creation exception: " + e);
        }
    }


    private String getRawUrl() {
        return URLS[mUrlSpinner.getSelectedItemPosition()];
    }


    private void setAndroidPlayerInfo(String msg)
    {
        mAndroidPlayerView.setText(msg);
    }


    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        setAndroidPlayerInfo("Error: " + what);
        return false;
    }


    @Override
    public void onPrepared(MediaPlayer mp) {
        if ((mAndroidPlayer == mp) && !isFinishing()) {

            mAndroidPlayer.start();
            setAndroidPlayerInfo("Playback started");

            if (mSbmPlayer != null) {
                mSbmPlayer.play();
                setSbmPlayerInfo("Playback started");
            }
        }
    }


    @Override
    public void onCuePointReceived(com.tritondigital.player.MediaPlayer sbmPlayer, Bundle cuePoint) {
        if (mSbmPlayer == sbmPlayer) {

            mBannersWrapper.loadCuePoint(cuePoint);
            mListItems.clear();

            if (cuePoint != null) {
                List<String> keys = new ArrayList<>(cuePoint.keySet());
                Collections.sort(keys);

                for (String key : keys) {
                    Object value = cuePoint.get(key);
                    if (value != null) {
                        String valueStr = value.toString();
                        if (!TextUtils.isEmpty(valueStr)) {
                            mListItems.add(new String[]{key, valueStr});
                        }
                    }
                }
            }

            ((ArrayAdapter) mListView.getAdapter()).notifyDataSetChanged();
        }
    }


    @Override
    public void onStateChanged(com.tritondigital.player.MediaPlayer sbmPlayer, int state) {
        if (mSbmPlayer == sbmPlayer) {
            String stateStr = com.tritondigital.player.MediaPlayer.debugStateToStr(state);

            if (state == com.tritondigital.player.MediaPlayer.STATE_ERROR) {
                stateStr += ' ' + com.tritondigital.player.MediaPlayer.debugErrorToStr(mSbmPlayer.getErrorCode());
            }

            setSbmPlayerInfo(stateStr);
        }
    }


    private void setSbmPlayerInfo(String msg)
    {
        mSbmPlayerView.setText(msg);
    }


    // Not the right way to do it. The configuration can vary from one client to another.
    private String createSbmUrl(String streamUrl) {
        if (streamUrl == null)
            return null;
        else if (streamUrl.contains("_SC:"))
            return streamUrl.replace("_SC:", "_SBM:");
        else if (streamUrl.contains("_SC?"))
            return streamUrl.replace("_SC?", "_SBM?");
        else if (streamUrl.contains("_HLS:"))
            return streamUrl.replace("_HLS:", "_SBM:");
        else if (streamUrl.contains("_HLS?"))
            return streamUrl.replace("_HLS?", "_SBM?");
        else
            return null;
    }
}
