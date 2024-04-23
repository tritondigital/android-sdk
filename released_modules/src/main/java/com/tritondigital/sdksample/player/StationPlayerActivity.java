package com.tritondigital.sdksample.player;

import android.graphics.Color;
import android.os.Bundle;
import androidx.mediarouter.media.MediaItemMetadata;

import android.os.Handler;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;

import com.tritondigital.player.MediaPlayer;
import com.tritondigital.player.PlayerConsts;
import com.tritondigital.player.StreamUrlBuilder;
import com.tritondigital.player.TritonPlayer;
import com.tritondigital.sdksample.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;


/**
 * Shows how to play a station using Triton Player.
 *
 * IMPORTANT: You must use your real broadcaster and station names when making an app in order to
 * maximise your advertising revenues.
 */
public class StationPlayerActivity extends TritonPlayerActivity implements SeekBar.OnSeekBarChangeListener{

    // IMPORTANT: use your real values in your apps.
    private static final String BROADCASTER   = "TritonDigital";
    private static final String STATION_NAME  = "Sdk Sample";
    //private static final String DEFAULT_MOUNT = "S1_FLV_AAC";
    private static final String DEFAULT_MOUNT = "S1_HLS_AAC";
    private static final int GREEN = Color.rgb(123, 232, 143);
    private static final int RED = Color.rgb(240, 94, 84);
    private Button         mProgramButton;


    private EditText mMountEditText;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mMountEditText = (EditText) findViewById(R.id.editText_mount);
        initSeekViews();
        findViewById(R.id.button_pause).setOnClickListener(this);
        findViewById(R.id.button_get_cloudstreaminfo).setOnClickListener(this);
        findViewById(R.id.button_play_program).setOnClickListener(this);
        mProgramButton = (Button) findViewById(R.id.button_play_program);
        mProgramButton.setVisibility(View.GONE);
    }


    @Override
    protected int getLayout() {
        return R.layout.player_station;
    }


    @Override
    protected void reset() {
        super.reset();
        mMountEditText.setText(DEFAULT_MOUNT);
    }

    @Override
    public void onClick(View v) {
        super.onClick(v);
        if (v.getId() == R.id.button_pause) {
            pausePlayer();
        }
    }


    @Override
    protected void createPlayer() {
        super.createPlayer();
        mTritonPlayer.setOnCloudStreamInfoReceivedListener(this);
    }

    @Override
    protected void startPlayer() {
        super.startPlayer();
        mProgramButton.setVisibility(View.GONE);
    }

    @Override
    protected Bundle createPlayerSettings() {
        String mount = getMount();

        // Google Cast Metadata.
        Bundle metadata = new Bundle();
        metadata.putString(MediaItemMetadata.KEY_ARTWORK_URI, IMAGE_URI);
        metadata.putString(MediaItemMetadata.KEY_TITLE,       BROADCASTER + " - " + mount);

        // Player Settings
        Bundle settings = new Bundle();
        settings.putBoolean(TritonPlayer.SETTINGS_TARGETING_LOCATION_TRACKING_ENABLED, true);
        settings.putBundle(TritonPlayer.SETTINGS_MEDIA_ITEM_METADATA, metadata);
        settings.putString(TritonPlayer.SETTINGS_STATION_MOUNT, mount);
        settings.putString(TritonPlayer.SETTINGS_STATION_BROADCASTER, BROADCASTER);
        settings.putString(TritonPlayer.SETTINGS_STATION_NAME, STATION_NAME);
        settings.putString(TritonPlayer.SETTINGS_TRANSPORT, TritonPlayer.TRANSPORT_HLS);
        settings.putBoolean(TritonPlayer.SETTINGS_TIMESHIFT_ENABLED, false);

        //settings.putBoolean(TritonPlayer.SETTINGS_FORCE_DISABLE_EXOPLAYER, true);
        //settings.putString(TritonPlayer.SETTINGS_TRANSPORT, TritonPlayer.TRANSPORT_HLS);
        //settings.putString(TritonPlayer.SETTINGS_TRANSPORT, TritonPlayer.TRANSPORT_SC);

        // Add the targeting parameters
        //HashMap<String, String> targetingParams = new HashMap();
        //targetingParams.put(StreamUrlBuilder.COUNTRY_CODE, "US");
        //targetingParams.put(StreamUrlBuilder.POSTAL_CODE, "12345");
        //targetingParams.put(StreamUrlBuilder.GENDER, "m");
        //targetingParams.put(StreamUrlBuilder.YEAR_OF_BIRTH, "1990");
        //settings.putSerializable(TritonPlayer.SETTINGS_TARGETING_PARAMS, targetingParams);

        // Add the authorization token
        //String token = AuthUtil.createJwtToken("MySecretKey", "MySecretKeyId", true, "foo@bar.com", targetingParams);
        //settings.putString(TritonPlayer.SETTINGS_AUTH_TOKEN, token);

        // Add low delay setting
        //settings.putInt(TritonPlayer.SETTINGS_LOW_DELAY, -1); // Auto
        settings.putInt(TritonPlayer.SETTINGS_LOW_DELAY, 10); // 10 sec
        //settings.putInt(TritonPlayer.SETTINGS_LOW_DELAY, 0);  // Disabled

        // Add TTags
        //String[] tTags = {"mobile:android","cola:diet"};
        //settings.putStringArray(TritonPlayer.SETTINGS_TTAGS,tTags);

        //PlayerServices Prefix: e.g  "EU" , "AP"
        //settings.putString(TritonPlayer.SETTINGS_PLAYER_SERVICES_REGION,"AP");

        return settings;
    }


    private String getMount() {
        return mMountEditText.getText().toString().trim();
    }

    private void pausePlayer() {
        if (mTritonPlayer != null) {
            mTritonPlayer.pause();
        }
    }

    @Override
    protected void setInputEnabled(boolean enabled) {
        mMountEditText.setEnabled(enabled);
    }
}
