package com.tritondigital.sdksample;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.view.View;
import android.widget.TextView;

import com.tritondigital.sdksample.ads.CustomAdsActivity;
import com.tritondigital.sdksample.ads.InterstitialAdsActivity;
import com.tritondigital.sdksample.player.CuePointHistoryActivity;
import com.tritondigital.sdksample.player.MultiStationsPlayerActivity;
import com.tritondigital.sdksample.player.StreamPlayerActivity;
import com.tritondigital.sdksample.player.SbmPlayerActivity;
import com.tritondigital.sdksample.player.StationPlayerActivity;
import com.tritondigital.util.SdkUtil;


public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        ((TextView)findViewById(R.id.textView_sdkVersion)).setText(SdkUtil.VERSION);
    }


    private void launchActivity(Class<?> cls) {
        Intent intent = new Intent(this, cls);
        startActivity(intent);
    }


    public void onCuePointHistoryClick(View view) { launchActivity(CuePointHistoryActivity.class);}
    public void onCustomAdsClick(View view)       { launchActivity(CustomAdsActivity.class); }
    public void onInterstitialAdsClick(View view) { launchActivity(InterstitialAdsActivity.class); }
    public void onOnDemandPlayerClick(View view)  { launchActivity(StreamPlayerActivity.class);}
    public void onSbmPlayerClick(View view)       { launchActivity(SbmPlayerActivity.class); }
    public void onStationPlayerClick(View view)   { launchActivity(StationPlayerActivity.class); }
    public void onMultiStationsPlayerClick(View view)   { launchActivity(MultiStationsPlayerActivity.class); }
}
