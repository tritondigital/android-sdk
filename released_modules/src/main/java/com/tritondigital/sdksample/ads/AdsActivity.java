package com.tritondigital.sdksample.ads;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.tritondigital.ads.AdRequestBuilder;
import com.tritondigital.sdksample.R;


/**
 * Shows how to display an on-demand ad.
 */
public abstract class AdsActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String DEFAULT_HOST       = "cmod.live.streamtheworld.com";
    private static final String DEFAULT_STATION_ID = "WEB_SDK_TEST_S1";



    private AdRequestBuilder mAdRequestBuilder;

    private EditText     mHostEditText;
    private EditText     mStationEditText;
    private TextView     mAdRequestTextView;
    private TextView     mStatusView;
    private RadioGroup   mAssetTypeRadioGroup;
    private RadioGroup   mTypeRadioGroup;


    protected abstract int getLayout();
    protected abstract void loadAdRequest(AdRequestBuilder adRequest);


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(getLayout());

        mHostEditText        = (EditText) findViewById(R.id.editText_url);
        mStationEditText     = (EditText) findViewById(R.id.editText_station);
        mAdRequestTextView   = (TextView) findViewById(R.id.textView_adRequest);
        mStatusView          = (TextView) findViewById(R.id.textView_status);
        mAssetTypeRadioGroup = (RadioGroup) findViewById(R.id.radioGroup_assetType);
        mTypeRadioGroup      = (RadioGroup) findViewById(R.id.radioGroup_type);

        findViewById(R.id.button_showAd).setOnClickListener(this);
        findViewById(R.id.button_reset).setOnClickListener(this);

        mAdRequestBuilder = new AdRequestBuilder(this);

        // Add some TTags
        //String[] tTags = {"mobile:android","cola:diet"};
        //mAdRequestBuilder.addTtags(tTags);
    }


    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.button_showAd) {
            updateAdRequest();
            loadAdRequest(mAdRequestBuilder);
        } else if (id == R.id.button_reset) {
            reset();
        }
    }


    protected void reset() {
        mAdRequestTextView.setText(null);
        mStatusView.setText(null);
        mStationEditText.setText(DEFAULT_STATION_ID);
        mHostEditText.setText(DEFAULT_HOST);

        ((RadioButton)findViewById(R.id.radioButton_adType_preroll)).setChecked(true);
        ((RadioButton)findViewById(R.id.radioButton_assetType_undefined)).setChecked(true);
    }


    private void updateAdRequest() {
        setStatus("Building an ad request");

        // Reset previous values
        mAdRequestBuilder.resetQueryParameters();

        // Set the user defined values
        mAdRequestBuilder.setHost(getHost());
        mAdRequestBuilder.addQueryParameter(AdRequestBuilder.ASSET_TYPE, getAssetType());
        mAdRequestBuilder.addQueryParameter(AdRequestBuilder.TYPE, getType());

        String station = getStation();
        mAdRequestBuilder.addQueryParameter(TextUtils.isDigitsOnly(station)
                ? AdRequestBuilder.STATION_ID : AdRequestBuilder.STATION_NAME, station);

        String adRequest = mAdRequestBuilder.build();

        // Update UI
        setStatus("Showing the ad from a request");
        mAdRequestTextView.setText(adRequest);
    }


    protected void setStatus(String status) {
        if (mStatusView != null) {
            mStatusView.setText(status);
        }
    }


    private String getHost() {
        return mHostEditText.getText().toString().trim();
    }


    private String getStation() {
        return mStationEditText.getText().toString().trim();
    }


    private String getType() {
        return (mTypeRadioGroup.getCheckedRadioButtonId() == R.id.radioButton_adType_midroll)
                ? AdRequestBuilder.TYPE_VALUE_MIDROLL : AdRequestBuilder.TYPE_VALUE_PREROLL;
    }


    private String getAssetType() {
        int assetButtonId = mAssetTypeRadioGroup.getCheckedRadioButtonId();

        if (assetButtonId == R.id.radioButton_assetType_audio) {
            return AdRequestBuilder.ASSET_TYPE_VALUE_AUDIO;
        } else if (assetButtonId == R.id.radioButton_assetType_video) {
            return AdRequestBuilder.ASSET_TYPE_VALUE_VIDEO;
        } else {
            return null;
        }
    }
}
