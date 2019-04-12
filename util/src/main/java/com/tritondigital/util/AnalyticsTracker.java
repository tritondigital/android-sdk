package com.tritondigital.util;


import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.*;
import android.text.TextUtils;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

import com.tritondigital.util.Debug.CountUpTimer;


public class AnalyticsTracker
{
    private static final String TAG                     ="AnalyticsTracker";

    private static final String KEY_TRACKER_ID          ="tid";
    private static final String KEY_GA_VERSION          ="v";
    private static final String KEY_USER_ID             ="cid";
    private static final String KEY_TYPE                ="t";
    private static final String KEY_APP_NAME            ="an";
    private static final String KEY_APP_VERSION         ="av";
    private static final String KEY_APP_MAJOR_VERSION   ="aid";
    private static final String KEY_APP_CATEGORY        ="aiid";

    private static final String KEY_CATEGORY            ="ec";
    private static final String KEY_ACTION              ="ea";
    private static final String KEY_LABEL               ="el";
    private static final String KEY_METRIC              ="cm";



    private static final String GA_RELEASE_HOST         = "https://www.google-analytics.com/collect";
    private static final String GA_DEBUG_HOST           = "https://www.google-analytics.com/debug/collect";
    private static final String GA_VERSION              = "1";
    private static final String GA_DEBUG_TRACKER_ID     = "";
    private static final String GA_RELEASE_TRACKER_ID   = "";
    private static final String SDK_VERSION             = SdkUtil.VERSION;
    private static final String SDK_MAJOR_VERIONS       = getSdkMajorVersion();
    private static final String SDK_NAME                = "android-sdk";
    private static final String SDK_CATEGORY_TRITON     = "player";
    private static final String SDK_CATEGORY_DEFAULT    = "custom";

    static AnalyticsTracker mAnalyticsTracker;

    Context mContext;
    String  mUserId;
    CountUpTimer mCountUpTimer;
    boolean mHasBeenInitialized;
    boolean mIsTritonStdApp;
    boolean mIsAppSignedInDebug;

    Random mRandom;

    public enum Dimension
    {
         Tech("cd1"), MediaType("cd2"), Mount("cd3"), Station("cd4"),Broadcaster("cd5"), MediaFormat("cd6"),
         AdSource("cd8"),AdFormat("cd9"),AdParser("cd10"),AdBlock("cd11"), SBM("cd12"),HLS("cd13"),AudioAdaptive("cd14"),
         GaId("cd15"),AlternateContent("cd16"),AdCompanionType("cd17");

         String mCode;
         Dimension(String code){ mCode= code;}
    }


    private enum StreamingConnectionState
    {
        Success("Success"), Unavailable("Unavailable"), StreamError("Stream Error"), GeoBlocked("GeoBlocking"), Failed("Failed");

        String mLabel;
        StreamingConnectionState(String label){mLabel= label;}
    }



    private AnalyticsTracker(Context context, boolean isTritonStdApp)
    {
        mContext = context;
        mUserId  = TrackingUtil.getGeneratedId(context);
        mCountUpTimer = new CountUpTimer();
        mIsTritonStdApp = isTritonStdApp;
        mIsAppSignedInDebug = Debug.isAppSignedInDebug(context);

        mRandom     = new Random(SystemClock.elapsedRealtime());
    }

    public synchronized static AnalyticsTracker getTracker(Context context,boolean isTritonStdApp)
    {
        if(mAnalyticsTracker == null)
        {
            mAnalyticsTracker = new AnalyticsTracker(context,isTritonStdApp);
            mAnalyticsTracker.addMandatoryParams(isTritonStdApp);
        }

        return mAnalyticsTracker;
    }


    public synchronized static AnalyticsTracker getTracker(Context context)
    {
        return getTracker(context, false);
    }



    public void initialize()
    {
        if(!mHasBeenInitialized)
        {
            mHasBeenInitialized= true;

            addType("event");

            addCategory("Init");
            addAction("Config");
            addLabel("Success");
            addDimension(Dimension.Tech.mCode, "Android");
            addDimension(Dimension.AdBlock.mCode, false);
            addDimension(Dimension.SBM.mCode, true);
            addDimension(Dimension.HLS.mCode, false);
            addDimension(Dimension.AudioAdaptive.mCode, false);
            addDimension(Dimension.GaId.mCode, true);

            addMetric(0);

            //send init request
            sendRequest();
        }
    }



    private void resetTracker()
    {
        resetQueryParameters();

        if(mCountUpTimer != null)
        {
            mCountUpTimer.stop();
            mCountUpTimer.reset();
        }

        addMandatoryParams(mIsTritonStdApp);
    }




    private void sendRequest()
    {
        int nextValue = mRandom.nextInt(100);

        Log.w(TAG, "sendRequest  nextValue: " + nextValue);

        if(nextValue < 5)
        {
            String requestUrl = buildRequest();

            if (android.os.Build.VERSION.SDK_INT < 11)
            {
                new TrackerRequestTask().execute(requestUrl);
            }
            else
            {
                new TrackerRequestTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, requestUrl);
            }
        }
    }


    private String buildRequest()
    {
        if (mHostUri == null) {
            throw new IllegalArgumentException("The host must be set.");
        }

        Uri.Builder uriBuilder = mHostUri.buildUpon();

        // Append the query parameter to the URL
        if (!mQueryParams.isEmpty()) {
            for (HashMap.Entry<String, String> entry : mQueryParams.entrySet()) {
                uriBuilder.appendQueryParameter(entry.getKey(), entry.getValue());
            }
        }


        String requestUrl= uriBuilder.build().toString();
        Log.d(TAG, "Tracker request built: " + requestUrl);

        return requestUrl;
    }


    private void addMandatoryParams(boolean isTritonStdApp)
    {
        //Host
        String host = mIsAppSignedInDebug ? GA_DEBUG_HOST: GA_RELEASE_HOST;
        setHost(host);

        //tid : Ga tracker id
       String trackerId = mIsAppSignedInDebug ? GA_DEBUG_TRACKER_ID: GA_RELEASE_TRACKER_ID;
       addQueryParameter(KEY_TRACKER_ID, trackerId);


        //Version
        addQueryParameter(KEY_GA_VERSION, GA_VERSION);

        //user id
        addQueryParameter(KEY_USER_ID, mUserId);

        //App name
        addQueryParameter(KEY_APP_NAME, SDK_NAME);

        //App version
        addQueryParameter(KEY_APP_VERSION, SDK_VERSION);

        //App Major version
        addQueryParameter(KEY_APP_MAJOR_VERSION, SDK_MAJOR_VERIONS);

        //SDK Category
        String sdkCat = isTritonStdApp ? SDK_CATEGORY_TRITON: SDK_CATEGORY_DEFAULT;
        addQueryParameter(KEY_APP_CATEGORY, sdkCat);
    }


    public void addType(String typeValue)
    {
        addQueryParameter(KEY_TYPE, typeValue) ;
    }


    private void addCategory(String catValue) {
        addQueryParameter(KEY_CATEGORY, catValue) ;
    }

    private void addAction(String action) {
        addQueryParameter(KEY_ACTION, action) ;
    }

    private void addLabel(String label) {
        addQueryParameter(KEY_LABEL, label) ;
    }

    public void addDimension(String key, String dimen) {
        addQueryParameter(key, dimen) ;
    }


    private void addDimension(String key,boolean dimen) {
        addQueryParameter(key, dimen) ;
    }

    private void addMetric(long metric) {
        addQueryParameter(KEY_METRIC, String.valueOf(metric));
    }


    private static String getSdkMajorVersion()
    {

         return TextUtils.substring(SDK_VERSION,0,3);
    }



    public void startTimer()
    {
        mCountUpTimer.stop();
        mCountUpTimer.reset();
        mCountUpTimer.start();
    }

    public long stopTimer()
    {
        return mCountUpTimer.stop();
    }

    private HashMap<Dimension, String> createStreamingDimensions(String mount, String broadcaster)
    {
        HashMap<Dimension, String> dimens = new HashMap<Dimension, String>();
        dimens.put(Dimension.MediaType, "Audio");
        dimens.put(Dimension.Mount,mount);
        dimens.put(Dimension.Broadcaster, broadcaster);
        dimens.put(Dimension.HLS, String.valueOf(false));
        return dimens;
    }


    private HashMap<Dimension, String> createAdPrerollDimensions(String adFormat,boolean isVideo)
    {
        String mediaType = isVideo ? "Video": "Audio";
        HashMap<Dimension, String> dimens = new HashMap<Dimension, String>();
        dimens.put(Dimension.AdParser, "VASTModule");
        dimens.put(Dimension.AdFormat,adFormat);
        dimens.put(Dimension.MediaType, mediaType);
        dimens.put(Dimension.AdSource, "TAP");

        return dimens;
    }

    public void trackStreamingConnectionSuccess(String mount,String broadcaster, long loadTime)
    {
        HashMap<Dimension, String> dimens = createStreamingDimensions(mount, broadcaster);
        trackStreamingConnection(StreamingConnectionState.Success.mLabel, dimens, loadTime);
    }

    public void trackStreamingConnectionUnavailable(String mount,String broadcaster,long loadTime)
    {
        HashMap<Dimension, String> dimens = createStreamingDimensions(mount, broadcaster);
        trackStreamingConnection(StreamingConnectionState.Unavailable.mLabel, dimens, loadTime);
    }

    public void trackStreamingConnectionError(String mount,String broadcaster,long loadTime)
    {
        HashMap<Dimension, String> dimens = createStreamingDimensions(mount, broadcaster);
        trackStreamingConnection(StreamingConnectionState.StreamError.mLabel, dimens, loadTime);
    }


    public void trackStreamingConnectionGeoBlocked(String mount,String broadcaster,long loadTime)
    {
        HashMap<Dimension, String> dimens = createStreamingDimensions(mount, broadcaster);
        trackStreamingConnection(StreamingConnectionState.GeoBlocked.mLabel, dimens, loadTime);
    }

    public void trackStreamingConnectionFailed(String mount,String broadcaster,long loadTime)
    {
        HashMap<Dimension, String> dimens = createStreamingDimensions(mount, broadcaster);
        trackStreamingConnection(StreamingConnectionState.Failed.mLabel, dimens, loadTime);
    }

    public void trackAdPrerollSuccess(String adFormat, long loadTime, boolean isVideo)
    {
        trackAdPreroll("Success",adFormat, loadTime,isVideo);
    }


    public void trackAdPrerollError(String adFormat,long loadTime, boolean isVideo)
    {
        trackAdPreroll("Error", adFormat, loadTime, isVideo);
    }


    public void trackOnDemandPlaySuccess()
    {
        trackOnDemandPlay("Success");
    }


    public void trackOnDemandPlayError()
    {
        trackOnDemandPlay("Error");
    }



    private void trackStreamingConnection(String state,HashMap<Dimension, String> dimens, long loadTime)
    {
        resetTracker();

        addType("event");
        addCategory("Streaming");
        addAction("Connection");
        addLabel(state);

        if (!dimens.isEmpty())
        {
            for (HashMap.Entry<Dimension, String> entry : dimens.entrySet())
            {
                addDimension(entry.getKey().mCode, entry.getValue());
            }
        }

        addMetric(loadTime);

        //send init request
        sendRequest();
    }



    private void trackAdPreroll(String result, String adFormat, long loadTime, boolean isVideo)
    {
        resetTracker();

        addType("event");
        addCategory("Ad");
        addAction("Preroll");
        addLabel(result);
        addMetric(loadTime);

        HashMap<Dimension, String> dimens = createAdPrerollDimensions(adFormat,isVideo);

        if (!dimens.isEmpty())
        {
            for (HashMap.Entry<Dimension, String> entry : dimens.entrySet())
            {
                addDimension(entry.getKey().mCode, entry.getValue());
            }
        }

        //send init request
        sendRequest();
    }


    private void trackOnDemandPlay(String result)
    {
        resetTracker();

        addType("event");
        addCategory("On Demand");
        addAction("Play");
        addLabel(result);

        //send init request
        sendRequest();
    }

    /**
     * Google Analytics Tracker Request: to Send request to GA server
     */
    private static class TrackerRequestTask extends AsyncTask<String, Void, Void> {
        @Override
        protected Void doInBackground(String... trackerUrls) {
            Debug.renameThread("TrackerRequestTask");

            // Connect to all URL one after the other.
            for (String url : trackerUrls) {
                trackerUrl(url);
            }


            return null;
        }


        private void trackerUrl(String urlStr) {
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


                Log.w(TAG, "TrackerRequestTask    url:  " + url);

                if (responseCode != 200) {
                    Log.w(TAG, "Tracker failed: " + responseCode);
                }

            } catch (IOException e) {
                Log.e(TAG, e, "Tracker exception for: " + urlStr);

            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }
    }


   // Google Analytics Request Builder
    Uri mHostUri;
    HashMap<String, String> mQueryParams = new HashMap<>();

    void setHost(String host)
    {
        mHostUri = Uri.parse(host);
    }

    void resetQueryParameters()
    {
        mQueryParams.clear();
    }

    void addQueryParameter(String key, String value)
    {
        if (value == null)
        {
            mQueryParams.remove(key);
        }
        else
        {
            mQueryParams.put(key, value);
        }
    }

    void addQueryParameter(String key,boolean value)
    {
        addQueryParameter(key, String.valueOf(value));
    }

}
