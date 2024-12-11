package com.tritondigital.ads;

import android.annotation.TargetApi;
import android.os.AsyncTask;
import android.os.Bundle;

import com.tritondigital.util.Assert;
import com.tritondigital.util.Log;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Loads an ad from an ad request.
 */
@SuppressWarnings("unused")
public final class AdLoader {

    /** A listener for receiving AdLoader notifications */
    public interface AdLoaderListener {
        /** Called after an ad has been loaded */
        void onAdLoaded(AdLoader adLoader, Bundle ad);

        /** Called after an error has occurred */
        void onAdLoadingError(AdLoader adLoader, int errorCode);
    }


    /** Unknown host */
    public static final int ERROR_UNKNOWN_HOST = 8001;

    /** Unspecified error */
    public static final int ERROR_UNKNOWN = 8003;

    /** Network not available */
    public static final int ERROR_NETWORK_NOT_AVAILABLE = 8054;

    /** No ad available */
    public static final int ERROR_NO_INVENTORY = 8004;


    private String TAG = Log.makeTag("AdLoader");

    private VastParsingTask  mVastParsingTask;
    private AdLoaderListener mListener;

    // Parsing result
    private Bundle mAd;
    private int    mError;
    private String mAdRequest;

    /** Counter to ensure there is no infinite loop because of VAST Wrappers */
    private int mNoRequest = 0;

    private ArrayList<String> mImpressionUrls = new ArrayList<>();

    public void setTag(String msg) {
        TAG = msg;
    }

    /**
     * Load an ad request.
     *
     * If this class is already loading an ad, it will be cancelled.
     */
    @TargetApi(11)
    public void load(String adRequest, Map<String,List<Integer>> dmpSegments) {
        cancel();

        // Ignore null ad request.
        if (adRequest == null) {
            return;
        }

        mAd        = null;
        mError     = 0;
        mAdRequest = adRequest;

        // Start VAST parsing
        Log.i(TAG, "Loading ad request: " + adRequest);
        if(dmpSegments == null){
            mVastParsingTask = new VastParsingTask();
        }else{
            mVastParsingTask = new VastParsingTask(dmpSegments);
        }


        if (android.os.Build.VERSION.SDK_INT < 11) {
            mVastParsingTask.execute(adRequest);
        } else {
            mVastParsingTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, adRequest);
        }
    }

    /**
     * Load an ad request.
     *
     * If this class is already loading an ad, it will be cancelled.
     */
    @TargetApi(11)
    public void load(String adRequest) {
        load(adRequest, null);
    }


    /**
     * Load an ad request from its builder.
     */
    public void load(AdRequestBuilder adRequestBuilder) {
        load(adRequestBuilder,null);

        if (adRequestBuilder == null) {
            load((String) null);
        } else {
            load(adRequestBuilder.build());
        }
    }

    /**
     * Load an ad request from its builder with DMP Segments
     */
    public void load(AdRequestBuilder adRequestBuilder, Map<String, List<Integer>> dmpSegments) {
        if (adRequestBuilder == null) {
            load((String) null);
        } else {
            if(dmpSegments == null){
                load(adRequestBuilder.build());
            }else{
                load(adRequestBuilder.build(), dmpSegments);
            }

        }
    }


    /**
     * Cancel the current loading
     */
    public void cancel() {
        if (mVastParsingTask != null) {
            mVastParsingTask.cancel(true);
            mVastParsingTask = null;
        }
    }


    /**
     * Returns the loaded ad
     */
    public Bundle getAd() {
        return mAd;
    }


    /**
     * Returns the error code
     */
    public int getError() {
        return mError;
    }


    /**
     * Sets the ad loader listener
     */
    public void setListener(AdLoaderListener listener)
    {
        mListener = listener;
    }


    /**
     * Returns the ad loader listener
     */
    public AdLoaderListener getListener() {
        return mListener;
    }


    private void onAdLoaded(Bundle ad) {
        Log.i(TAG, "Ad request loaded: " + mAdRequest);
        mAd = ad;

        if(ad.containsKey(Ad.IMPRESSION_TRACKING_URLS)) {
            mImpressionUrls.addAll(ad.getStringArrayList(Ad.IMPRESSION_TRACKING_URLS));
        }

        if(!isVastWrapper(ad)) {

            if (mListener != null) {
                ad.putStringArrayList(Ad.IMPRESSION_TRACKING_URLS, mImpressionUrls);
                mListener.onAdLoaded(this, ad);
                mNoRequest = 0;
                mImpressionUrls = new ArrayList<>();
            }
        }
    }

    private boolean isVastWrapper(Bundle ad) {
        mNoRequest++;

        if(ad.containsKey(Ad.VAST_AD_TAG) && mNoRequest <= 5) {
            load(ad.getString(Ad.VAST_AD_TAG));
            return true;
        }
        return false;
    }

    private void onError(int error) {
        Log.w(TAG, "Error: " + debugErrorToStr(error));
        Log.w(TAG, " *   " + mAdRequest);

        mError = error;

        if (mListener != null) {
            mListener.onAdLoadingError(this, error);
        }
    }


    /**
     * Converts the error codes to a string.
     *
     * @note Only use for debugging purpose.
     */
    public static String debugErrorToStr(int errorCode) {
        switch (errorCode) {
            case ERROR_UNKNOWN:               return "Unknown";
            case ERROR_UNKNOWN_HOST:          return "Unknown host";
            case ERROR_NETWORK_NOT_AVAILABLE: return "Network not available";
            case ERROR_NO_INVENTORY:          return "No ad inventory";
            case 0:                           return "No error";

            default:
                Assert.failUnhandledValue(Log.makeTag("AdLoader"), errorCode, "debugErrorToStr");
                return "Unknown";
        }
    }


    private class VastParsingTask extends AsyncTask<String, Void, Bundle> {
        private volatile int mParseError;
        private Map<String, List<Integer>> dmpSegments;

        public VastParsingTask() {
        }

        public VastParsingTask(Map<String, List<Integer>> dmpSegments) {
            this.dmpSegments = dmpSegments;
        }

        @Override
        protected Bundle doInBackground(String... adRequests) {
            AdParser adParser = new AdParser();
            Bundle ad = null;

            try {
                InputStream is;

                if (adRequests[0] != null) {
                    if (adRequests[0].startsWith("http")) {
                        URL url = new URL(adRequests[0]);
                        HttpURLConnection urlConnection=(HttpURLConnection) url.openConnection();
                        if(dmpSegments != null){
                            JSONObject segments = new JSONObject(dmpSegments);
                            urlConnection.setRequestProperty("X-DMP-Segment-IDs", segments.toString());
                        }

                        is = new BufferedInputStream(urlConnection.getInputStream());
                    } else {
                        is = new ByteArrayInputStream(adRequests[0].getBytes());
                    }

                    ad = adParser.parse(is);
                }

            } catch (java.net.UnknownHostException e) {
                Log.e(TAG, e, "Download exception");
                mParseError = ERROR_UNKNOWN_HOST;

            } catch (java.io.FileNotFoundException e) {
                // Happens when:
                //      - DAAST and video
                //      - Server doesn't recognise the station
                Log.w(TAG, e, "Download exception: " + adRequests[0]);
                mParseError = ERROR_UNKNOWN;

            } catch (Exception e) {
                // Happens when:
                //      - URL not starting with http
                //      - VAST error (should never happen)
                mParseError = ERROR_UNKNOWN;
                Log.e(TAG, e, "VAST parser exception: " + adRequests[0]);
            }

            return ad;
        }


        @Override
        protected void onPostExecute(Bundle ad) {
            if (mVastParsingTask == this) {
                mVastParsingTask = null;

                // Handle errors
                if (mParseError != 0) {
                    onError(mParseError);

                } else if ((ad == null) || ad.isEmpty()) {
                    onError(ERROR_NO_INVENTORY);

                } else {
                    onAdLoaded(ad);
                }
            }
        }
    }
}
