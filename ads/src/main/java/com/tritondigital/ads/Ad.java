package com.tritondigital.ads;

import android.annotation.TargetApi;
import android.os.AsyncTask;
import android.os.Bundle;

import com.tritondigital.util.Debug;
import com.tritondigital.util.Log;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;


/**
 * Advertisement bundle keys.
 *
 * \note This class is only useful for applications doing their own layout for on-demand advertisements.
 */
public final class Ad {
    private static final String TAG = Log.makeTag("Ad");

    /** _Bundle Array List_ - Companion banners. Available keys for each banners are Ad.URL, Ad.WIDTH and Ad.HEIGHT */
    public static final String BANNERS = "banners";

    /** _String Array List_ - URLs to connect to when main creative playback has started */
    public static final String IMPRESSION_TRACKING_URLS = "impression_tracking_urls";

    /** _String_ - The main creative's duration in _HH:MM:SS.mmm_ or _HH:MM:SS_ format */
    public static final String DURATION = "mime_type";

    /** _String_ - The main creative's MIME type */
    public static final String MIME_TYPE = "mime_type";

    /** _String_ - The main creative's URL */
    public static final String URL = "url";

    /** _String_ - The main creative's raw HTML */
    public static final String HTML = "html";

    /** _int_ - The main creative's width */
    public static final String WIDTH = "width";

    /** _int_ - The main creative's height */
    public static final String HEIGHT = "height";

    /** _String_ - The ad's title */
    public static final String TITLE = "title";

    /** _String_ - The ad's format: eg: VAST or DAAST */
    public static final String FORMAT = "format";

    /** _String_ - URL to launch in the browser when the video has been clicked */
    public static final String VIDEO_CLICK_THROUGH_URL = "video_click_through_url";

    /** _String Array List_ - URLs to connect to when a video has been clicked */
    public static final String VIDEO_CLICK_TRACKING_URLS = "video_click_tracking_urls";

    /** _String _ - URI element in VAST Wrapper  */
    public static final String VAST_AD_TAG = "VASTAdTagURI";

    private Ad(){}

    /**
     * Tracks the ad impression.
     *
     * To be used only with on-demand ads when an application takes care
     * of playing the audio or video.
     */
    public static void trackImpression(Bundle ad) {
        if (ad == null) {
            Log.w(TAG, "Error: Can't track impression for a NULL ad.");
        } else {
            ArrayList<String> urls = ad.getStringArrayList(Ad.IMPRESSION_TRACKING_URLS);
            trackUrls(urls);
        }
    }


    /**
     * Tracks the clicks on a video ad.
     *
     * To be used only with on-demand ads when an application takes care
     * of playing video.
     */
    public static void trackVideoClick(Bundle ad) {
        if (ad == null) {
            Log.w(TAG, "Error: Can't track video clicks for a NULL ad.");
        } else {
            ArrayList<String> urls = ad.getStringArrayList(Ad.VIDEO_CLICK_TRACKING_URLS);
            trackUrls(urls);
        }
    }


    @TargetApi(11)
    static void trackUrls(ArrayList<String> urls) {
        if ((urls != null) && !urls.isEmpty()) {
            String[] urlArray = urls.toArray(new String[urls.size()]);

            if (android.os.Build.VERSION.SDK_INT < 11) {
                new TrackingUrlTask().execute(urlArray);
            } else {
                new TrackingUrlTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, urlArray);
            }
        }
    }


    private static class TrackingUrlTask extends AsyncTask<String, Void, Void> {
        @Override
        protected Void doInBackground(String... trackingUrls) {
            Debug.renameThread("AdTrackingTask");

            // Connect to all URL one after the other.
            for (String url : trackingUrls) {
                trackUrl(url);
            }

            return null;
        }


        private void trackUrl(String urlStr) {
            HttpURLConnection conn = null;

            try {
                URL url = new URL(urlStr);
                conn = (HttpURLConnection) url.openConnection();
                conn.setReadTimeout(10000);
                conn.setConnectTimeout(15000);
                conn.setRequestMethod("GET");
                conn.setDoInput(true);
                conn.connect();
                int responseCode = conn.getResponseCode();

                if (responseCode != 200) {
                    Log.w(TAG, "Tracking failed: " + responseCode);
                }

            } catch (IOException e) {
                Log.e(TAG, e, "Tracking exception for: " + urlStr);

            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }
    }
}
