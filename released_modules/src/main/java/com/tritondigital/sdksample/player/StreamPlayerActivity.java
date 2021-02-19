package com.tritondigital.sdksample.player;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import androidx.mediarouter.media.MediaItemMetadata;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.tritondigital.player.MediaPlayer;
import com.tritondigital.player.TritonPlayer;
import com.tritondigital.sdksample.R;



/**
 * Shows how to play a basic media stream from a URL.
 */
public class StreamPlayerActivity extends TritonPlayerActivity implements SeekBar.OnSeekBarChangeListener {

    private static final String[] URLS = {
        "https://storage.googleapis.com/automotive-media/Jazz_In_Paris.mp3"
    };


    private static final String[] TRANSPORTS = {TritonPlayer.TRANSPORT_SC, TritonPlayer.TRANSPORT_FLV};
    private static final String[] MIME_TYPES = {TritonPlayer.MIME_TYPE_MPEG, TritonPlayer.MIME_TYPE_AAC};

    private Spinner mUrlSpinner;
    private Spinner mTransportSpinner;
    private Spinner mMimeTypeSpinner;


    private void requestPermission(){
        // Here, thisActivity is the current activity
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_CONTACTS)) {
                Log.i("StreamPlayer", "Should show permission reques rational.");
            } else {
                Log.i("StreamPlayer", "No permission request rational required.");
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},1);
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestPermission();

        // Init URLs spinner
        ArrayAdapter<String> urlAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, URLS);
        urlAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mUrlSpinner = (Spinner) findViewById(R.id.spinner_url);
        mUrlSpinner.setAdapter(urlAdapter);

        // Init the transport spinner
        ArrayAdapter<String> transportAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, TRANSPORTS);
        transportAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mTransportSpinner = (Spinner) findViewById(R.id.spinner_transport);
        mTransportSpinner.setAdapter(transportAdapter);

        // Init the MIME type spinner
        ArrayAdapter<String> mimeTypeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, MIME_TYPES);
        mimeTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mMimeTypeSpinner = (Spinner) findViewById(R.id.spinner_mimeType);
        mMimeTypeSpinner.setAdapter(mimeTypeAdapter);

        findViewById(R.id.button_pause).setOnClickListener(this);
        initSeekViews();

    }


    @Override
    protected int getLayout() {
        return R.layout.player_stream;
    }


    @Override
    protected void reset() {
        super.reset();
        mUrlSpinner.setSelection(0);
        mTransportSpinner.setSelection(0);
        mMimeTypeSpinner.setSelection(0);
        if(mPlayerLogView != null){
            mPlayerLogView.setText("");
        }
    }


    @Override
    protected Bundle createPlayerSettings() {
        String mediaUrl = getMediaUrl();

        // Google Cast Metadata.
        Bundle metadata = new Bundle();
        metadata.putString(MediaItemMetadata.KEY_ARTWORK_URI, IMAGE_URI);
        metadata.putString(MediaItemMetadata.KEY_TITLE,       mediaUrl);

        // Player Settings
        Bundle settings = new Bundle();
        settings.putString(TritonPlayer.SETTINGS_STREAM_URL, mediaUrl);
        settings.putString(TritonPlayer.SETTINGS_STREAM_MIME_TYPE, getMimeType());
        settings.putString(TritonPlayer.SETTINGS_TRANSPORT, getTransport());
        settings.putBundle(TritonPlayer.SETTINGS_MEDIA_ITEM_METADATA, metadata);
        return settings;
    }


    private String getMediaUrl() {
        return URLS[mUrlSpinner.getSelectedItemPosition()];
    }


    private String getMimeType() {
        return MIME_TYPES[mMimeTypeSpinner.getSelectedItemPosition()];
    }


    private String getTransport() {
        return TRANSPORTS[mTransportSpinner.getSelectedItemPosition()];
    }


    @Override
    public void onClick(View v) {
        super.onClick(v);
        if (v.getId() == R.id.button_pause) {
            pausePlayer();
        }
    }


    @Override
    protected void setInputEnabled(boolean enabled) {
        mUrlSpinner.setEnabled(enabled);
        mTransportSpinner.setEnabled(enabled);
        mMimeTypeSpinner.setEnabled(enabled);
    }


    private void pausePlayer() {
        if (mTritonPlayer != null) {
            mTritonPlayer.pause();
        }
    }


    protected void releasePlayer() {
        super.releasePlayer();
        updateSeekable();
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Seek views
    ////////////////////////////////////////////////////////////////////////////////////////////////
    private Handler mHandler = new Handler();

    private View mSeekableContainer;
    private TextView mDurationTextView;
    private TextView mPositionTextView;
    private SeekBar mPositionSeekBar;


    @Override
    public void onInfo(MediaPlayer player, int info, int extra) {
        super.onInfo(player, info, extra);

        if (mTritonPlayer == player) {
            if (info == MediaPlayer.INFO_SEEKABLE_CHANGED) {
                updateSeekable();
            }
        }
    }


    private void initSeekViews() {
        mSeekableContainer = findViewById(R.id.view_seekable);
        mDurationTextView  = (TextView) mSeekableContainer.findViewById(R.id.textView_duration);
        mPositionTextView  = (TextView) mSeekableContainer.findViewById(R.id.textView_position);
        mPositionSeekBar   = (SeekBar) mSeekableContainer.findViewById(R.id.seekBar);
        mPositionSeekBar.setOnSeekBarChangeListener(this);
    }


    private void updateSeekable() {
        if ((mTritonPlayer != null) && mTritonPlayer.isSeekable()) {
            mSeekableContainer.setVisibility(View.VISIBLE);
            mHandler.removeCallbacks(mPositionPollingRunnable);
            mHandler.post(mPositionPollingRunnable);
        } else {
            mSeekableContainer.setVisibility(View.GONE);
            mHandler.removeCallbacks(mPositionPollingRunnable);
            updatePosition();
        }
    }


    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (fromUser && (mTritonPlayer != null)) {
            mTritonPlayer.seekTo(progress);
        }
    }


    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {}


    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {}


    // TODO: Stop updating the position when seeking.
    private void updatePosition() {
        if (mPositionTextView == null) {
            return;
        }

        if ((mTritonPlayer == null) || (mTritonPlayer.getDuration() <= 0)) {
            mPositionTextView.setText(null);
            mDurationTextView.setText(null);
            mPositionSeekBar.setProgress(0);
        } else {
            int position = mTritonPlayer.getPosition();
            int duration = mTritonPlayer.getDuration();

            // Update text
            mPositionTextView.setText(DateUtils.formatElapsedTime(mDateFormatBuffer, (position / 1000)));
            mDurationTextView.setText(DateUtils.formatElapsedTime(mDateFormatBuffer, (duration / 1000)));

            // Update progress bar
            mPositionSeekBar.setProgress(position);
            mPositionSeekBar.setMax(duration);
            mPositionSeekBar.setVisibility((duration > 0) ? View.VISIBLE : View.INVISIBLE);
        }
    }


    private final Runnable mPositionPollingRunnable = new Runnable() {
        @Override
        public void run() {
            updatePosition();
            mHandler.postDelayed(this, 800);
        }
    };
}
