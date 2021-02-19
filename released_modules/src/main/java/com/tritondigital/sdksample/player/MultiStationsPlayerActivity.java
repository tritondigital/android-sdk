package com.tritondigital.sdksample.player;

import android.os.Bundle;
import androidx.mediarouter.media.MediaItemMetadata;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;

import com.tritondigital.player.TritonPlayer;
import com.tritondigital.sdksample.R;

public class MultiStationsPlayerActivity  extends TritonPlayerActivity
{
    // IMPORTANT: use your real values in your apps.
    private static final String BROADCASTER   = "TritonDigital";
    private static final String STATION_NAME  = "Sdk Sample";


    private TextView mSelectedMount;
    private Button mPreviousButton;
    private Button mNextButton;
    private CheckBox mHlsCheckebox;
    private int mCurrentStationIndex;

    private final static String[] STATIONS = {
        "S1_FLV_AAC",
        "S1_FLV_MP3",
        "S1_HLS_AAC",
        "S2_FLV_AAC",
        "S2_FLV_MP3",
        "S3_FLV_MP3",
        "S4_FLV_AAC",
        "S5_HLS_AAC"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSelectedMount = (TextView) findViewById(R.id.selected_mount);
        mPreviousButton = (Button) findViewById(R.id.previous_button);
        mNextButton = (Button) findViewById(R.id.next_button);

        mHlsCheckebox = (CheckBox)findViewById(R.id.hlx_checkbox);

        mPreviousButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v) {
                previousButtonClicked();
            }
        });


        mNextButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v) {
                nextButtonClicked();
            }
        });
    }


    @Override
    protected int getLayout() {
        return R.layout.player_multistations;
    }


    @Override
    protected void reset() {
        super.reset();
        mSelectedMount.setText(STATIONS[0]);
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

        // settings.putBoolean(TritonPlayer.SETTINGS_FORCE_DISABLE_EXOPLAYER, false);

        if(mHlsCheckebox.isChecked())
        {
            settings.putString(TritonPlayer.SETTINGS_TRANSPORT,        TritonPlayer.TRANSPORT_HLS);
        }

        return settings;
    }


    private String getMount() {
        return mSelectedMount.getText().toString().trim();
    }


    @Override
    protected void setInputEnabled(boolean enabled) {
        mSelectedMount.setEnabled(false);
    }



    private void updatePlayerSettings()
    {
        int size = STATIONS.length;
        if(mCurrentStationIndex < 0) mCurrentStationIndex = size-1;
        mCurrentStationIndex= mCurrentStationIndex%size;

        String stationMount = STATIONS[mCurrentStationIndex];
        mSelectedMount.setText(stationMount);
    }

    private void previousButtonClicked()
    {
        mCurrentStationIndex--;
        updatePlayerSettings();
        changeStation();
    }

    private void nextButtonClicked()
    {
        mCurrentStationIndex++;
        updatePlayerSettings();


        changeStation();
    }


    private void changeStation()
    {
        //Stop
        stopPlayer();

        //Release
        releasePlayer();

        //Recreate and start with new settings
        startPlayer();
    }
}
