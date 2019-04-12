package com.tritondigital.sdksample.ads;

import android.os.Bundle;
import android.view.View;

import com.tritondigital.ads.SyncBannerView;
import com.tritondigital.sdksample.R;

import java.util.ArrayList;


/**
 * Wraps multiple banner sizes.
 *
 * For CustomAdsActivity, we could have used a BannerView instead of a SyncBannerView.
 */
public class BannersWrapper {
    private ArrayList<SyncBannerView> mSyncBannerViews = new ArrayList<>();

    public BannersWrapper(View parentView) {
        SyncBannerView syncBannerView;

        syncBannerView = (SyncBannerView) parentView.findViewById(R.id.banner970x250);
        syncBannerView.setBannerSize(970, 250);
        mSyncBannerViews.add(syncBannerView);

        syncBannerView = (SyncBannerView) parentView.findViewById(R.id.banner120x60);
        syncBannerView.setBannerSize(120, 60);
        mSyncBannerViews.add(syncBannerView);

        syncBannerView = (SyncBannerView) parentView.findViewById(R.id.banner300x600);
        syncBannerView.setBannerSize(300, 600);
        mSyncBannerViews.add(syncBannerView);

        syncBannerView = (SyncBannerView) parentView.findViewById(R.id.banner728x90);
        syncBannerView.setBannerSize(728, 90);
        mSyncBannerViews.add(syncBannerView);

        syncBannerView = (SyncBannerView) parentView.findViewById(R.id.banner970x100);
        syncBannerView.setBannerSize(970, 100);
        mSyncBannerViews.add(syncBannerView);

        syncBannerView = (SyncBannerView) parentView.findViewById(R.id.banner300x250);
        syncBannerView.setBannerSize(300, 250);
        mSyncBannerViews.add(syncBannerView);

        syncBannerView = (SyncBannerView) parentView.findViewById(R.id.banner88x31);
        syncBannerView.setBannerSize(88, 31);
        mSyncBannerViews.add(syncBannerView);

        syncBannerView = (SyncBannerView) parentView.findViewById(R.id.banner300x1050);
        syncBannerView.setBannerSize(300, 1050);
        mSyncBannerViews.add(syncBannerView);

        syncBannerView = (SyncBannerView) parentView.findViewById(R.id.banner970x90);
        syncBannerView.setBannerSize(970, 90);
        mSyncBannerViews.add(syncBannerView);

        syncBannerView = (SyncBannerView) parentView.findViewById(R.id.banner180x150);
        syncBannerView.setBannerSize(180, 150);
        mSyncBannerViews.add(syncBannerView);

        syncBannerView = (SyncBannerView) parentView.findViewById(R.id.banner320x480);
        syncBannerView.setBannerSize(320, 480);
        mSyncBannerViews.add(syncBannerView);

        syncBannerView = (SyncBannerView) parentView.findViewById(R.id.banner300x50);
        syncBannerView.setBannerSize(300, 50);
        mSyncBannerViews.add(syncBannerView);

        syncBannerView = (SyncBannerView) parentView.findViewById(R.id.banner320x50);
        syncBannerView.setBannerSize(320, 50);
        mSyncBannerViews.add(syncBannerView);

        syncBannerView = (SyncBannerView) parentView.findViewById(R.id.banner300x300);
        syncBannerView.setBannerSize(300, 300);
        mSyncBannerViews.add(syncBannerView);

        syncBannerView = (SyncBannerView) parentView.findViewById(R.id.banner970x66);
        syncBannerView.setBannerSize(970, 66);
        mSyncBannerViews.add(syncBannerView);

        syncBannerView = (SyncBannerView) parentView.findViewById(R.id.banner160x600);
        syncBannerView.setBannerSize(160, 600);
        mSyncBannerViews.add(syncBannerView);
    }


    public void onPause() {
        for (SyncBannerView bannerView : mSyncBannerViews) {
            bannerView.onPause();
        }
    }


    public void onResume() {
        for (SyncBannerView bannerView : mSyncBannerViews) {
            bannerView.onResume();
        }
    }


    public void clear() {
        for (SyncBannerView bannerView : mSyncBannerViews) {
            bannerView.clearBanner();
        }
    }


    public void release() {
        for (SyncBannerView bannerView : mSyncBannerViews) {
            bannerView.release();
        }
        mSyncBannerViews.clear();
    }


    public void loadCuePoint(Bundle cuePoint) {
        for (SyncBannerView bannerView : mSyncBannerViews) {
            bannerView.loadCuePoint(cuePoint);
        }
    }


    public void showAd(Bundle ad) {
        for (SyncBannerView bannerView : mSyncBannerViews) {
            bannerView.showAd(ad);
        }
    }
}
