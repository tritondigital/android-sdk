package com.tritondigital.ads;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.AttributeSet;

import com.tritondigital.util.*;


/**
 * High-end banner able to load an ad from a cue point.
 *
 * This class takes care of loading the ad and displaying the right banner. This class
 * can return error codes defined in AdLoader.
 */
public final class SyncBannerView extends BannerView implements AdLoader.AdLoaderListener
{
    private final AdLoader mAdLoader;


    /**
     * Constructor
     */
    public SyncBannerView(Context context) {
        this(context, null);
    }


    /**
     * Constructor
     */
    public SyncBannerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }


    /**
     * Constructor
     */
    public SyncBannerView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mAdLoader = new AdLoader();
        mAdLoader.setListener(this);
        mAdLoader.setTag(Log.makeTag("SyncBannerLoader"));
    }


    @Override
    public void release() {
        mAdLoader.cancel();
        super.release();
    }


    @Override
    public void onAdLoaded(AdLoader adLoader, Bundle ad) {
        showAd(ad);
    }


    @Override
    public void onAdLoadingError(AdLoader adLoader, int errorCode) {
        onError(errorCode);
    }


    /**
     * Loads a banner from a cue point.
     *
     * The banner will be cleared if the cue point doesn't contain an ad.
     */
    public void loadCuePoint(Bundle cuePoint) {
        String adRequest = null;

        if (cuePoint != null) {
            adRequest = cuePoint.getString("ad_vast");
            if (TextUtils.isEmpty(adRequest)) {
                adRequest = cuePoint.getString("ad_vast_url");
            }
        }

        if (adRequest == null) {
            mAdLoader.cancel();
            clearBanner();
        } else {
            mAdLoader.load(adRequest);
        }
    }
}
