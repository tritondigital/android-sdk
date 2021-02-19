package com.tritondigital.sdksample.ads;

import android.os.Bundle;

import com.tritondigital.ads.AdRequestBuilder;
import com.tritondigital.ads.Interstitial;
import com.tritondigital.sdksample.R;


/**
 * Shows how to display an on-demand interstitial ad.
 */
public class InterstitialAdsActivity extends AdsActivity implements
        Interstitial.InterstitialListener {

    private Interstitial mInterstitial;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mInterstitial = new Interstitial(this);
        mInterstitial.setListener(this);

        if (savedInstanceState == null) {
            reset();
        }
    }


    @Override
    protected void onDestroy() {
        mInterstitial.release();
        super.onDestroy();
    }


    @Override
    protected int getLayout() {
        return R.layout.ads_interstitial;
    }


    @Override
    protected void loadAdRequest(AdRequestBuilder adRequestBuilder) {
        mInterstitial.showAd(adRequestBuilder);
    }


    @Override
    public void onInterstitialClosed(Interstitial interstitial) {
        setStatus("Interstitial closed");
    }

    @Override
    public void onInterstitialFinished(Interstitial interstitial) {
        setStatus("Interstitial finished");
    }

    @Override
    public void onInterstitialError(Interstitial interstitial, int errorCode) {
        setStatus("Interstitial error: " + Interstitial.debugErrorToStr(errorCode));
    }


    @Override
    public void onInterstitialStarted(Interstitial Interstitial) {
        setStatus("Interstitial started");
    }
}
