package com.tritondigital.ads;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;

import com.tritondigital.ads.AdLoader.AdLoaderListener;
import com.tritondigital.util.*;

import java.util.Random;


/**
 * Displays a full-screen Triton ad.
 * <p>
 * Interstitials are full screen UI that can play audios and videos ads. Usually, an interstitial
 * audio ad comes with a banner. It is recommended to preload intersitials so they can be shown quickly.
 * when needed.
 *
 * @par AndroidManifest.xml
 * Applications using interstitial ads must include the following code in their manifest.
 * @code{.xml} <activity
 * android:name="com.tritondigital.ads.InterstitialActivity"
 * android:configChanges="keyboard|keyboardHidden|orientation|screenLayout|uiMode|screenSize|smallestScreenSize" />
 * @endcode
 * @par Skip
 * We can't force an Android activities to stay on top without being cancelable. Therefore,
 * interstitials can be skipped at any time.
 * @par Tracking
 * The ad tracking is done automatically when using interstitials.
 * @par Behaviour
 * <table>
 * <tr>
 * <th></th>
 * <th>Audio with Banner</th>
 * <th>Video</th>
 * </tr>
 * <tr>
 * <th>Screen orientation</th>
 * <td>Multi-Orientation</td>
 * <td>Locked to the video orientation</td>
 * </tr>
 * <tr>
 * <th>Start from background</th>
 * <td>Will be played when app comes back to foreground</td>
 * <td>Will be played when app comes back to foreground</td>
 * </tr>
 * <tr>
 * <th>Move to background</th>
 * <td>Continues playing</td>
 * <td>Is skipped</td>
 * </tr>
 * </table>
 */
@SuppressWarnings({"JavaDoc", "unused"})
public final class Interstitial {

    /**
     * A listener for receiving Interstitial notifications
     */
    public interface InterstitialListener {
        /**
         * Called after an interstitial has closed
         */
        void onInterstitialClosed(Interstitial interstitial);

        /**
         * Called after an error has occurred
         */
        void onInterstitialError(Interstitial interstitial, int errorCode);

        /**
         * Called when an interstitial opens and overlay the application
         */
        void onInterstitialStarted(Interstitial Interstitial);
    }


    /**
     * @copybrief AdLoader::ERROR_UNKNOWN
     */
    public static final int ERROR_UNKNOWN = AdLoader.ERROR_UNKNOWN;

    /**
     * @copybrief AdLoader::ERROR_NO_INVENTORY
     */
    public static final int ERROR_NO_INVENTORY = AdLoader.ERROR_NO_INVENTORY;

    /**
     * @copybrief AdLoader::ERROR_UNKNOWN_HOST
     */
    public static final int ERROR_UNKNOWN_HOST = AdLoader.ERROR_UNKNOWN_HOST;

    /**
     * @copybrief AdLoader::ERROR_NETWORK_NOT_AVAILABLE
     */
    public static final int ERROR_NETWORK_NOT_AVAILABLE = AdLoader.ERROR_NETWORK_NOT_AVAILABLE;

    /**
     * Invalid media URL
     */
    public static final int ERROR_INVALID_MEDIA_URL = 8005;

    /**
     * Trying to load an ad after release()
     */
    public static final int ERROR_LOAD_AFTER_RELEASE = 8006;

    /**
     * An ad loading is already in progress
     */
    public static final int ERROR_LOADING_IN_PROGRESS = 8007;

    /**
     * Error while try to play the audio or video ad
     */
    public static final int ERROR_MEDIA_PLAYER_ERROR = 8008;

    /**
     * MIME type not starting with "audio" or "video"
     */
    public static final int ERROR_UNSUPPORTED_MIME_TYPE = 8009;

    private static final String TAG = Log.makeTag("Interstitial");

    private final Context mContext;
    private final int mRequestCode;
    private InterstitialListener mListener;
    private boolean mReleased;
    private boolean mActive;


    /**
     * Constructor
     */
    public Interstitial(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Null context");
        }

        mContext = context;
        mRequestCode = new Random().nextInt();
        registerReceiver();
        createAdLoader();
    }


    /**
     * Destroys the Interstitial.
     * <p>
     * No other methods should be called this.
     * <p>
     * You'll get a "leaked broadcast receiver" message if you don't call this
     * method before leaving your application.
     */
    public void release() {
        mReleased = true;
        unregisterReceiver();
        releaseAdLoader();
    }


    ///////////////////////////////////////////////////////////////////////////
    // Listener
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Sets the interstitial listener
     */
    public void setListener(InterstitialListener listener) {
        mListener = listener;
    }


    /**
     * Returns the interstitial listener
     */
    public InterstitialListener getListener() {
        return mListener;
    }


    private void onError(int error) {
        Log.w(TAG, "Interstitial error: " + debugErrorToStr(error));
        mActive = false;

        if ((mListener != null) && !mReleased) {
            mListener.onInterstitialError(this, error);
        }
    }


    private void onClosed() {
        mActive = false;

        if ((mListener != null) && !mReleased) {
            mListener.onInterstitialClosed(this);
        }
    }


    private void onStarted() {
        mActive = true;

        if ((mListener != null) && !mReleased) {
            mListener.onInterstitialStarted(this);
        }
    }


    ///////////////////////////////////////////////////////////////////////////
    // Broadcast receiver
    ///////////////////////////////////////////////////////////////////////////

    private void registerReceiver() {
        IntentFilter playerFilter = new IntentFilter();
        playerFilter.addAction(InterstitialActivity.ACTION_CLOSED);
        playerFilter.addAction(InterstitialActivity.ACTION_ERROR);
        mContext.registerReceiver(mReceiver, playerFilter);
    }


    private void unregisterReceiver() {
        mContext.unregisterReceiver(mReceiver);
    }


    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action != null) {
                switch (action) {
                    case InterstitialActivity.ACTION_CLOSED:
                        onClosed();
                        break;

                    case InterstitialActivity.ACTION_ERROR:
                        int error = intent.getIntExtra(InterstitialActivity.EXTRA_ERROR_CODE, ERROR_UNKNOWN);
                        onError(error);
                        break;

                    default:
                        Assert.failUnhandledValue(TAG, action, "BroadcastReceiver");
                }
            } else {
                Assert.fail(TAG, "NULL action in BroadcastReceiver");
            }
        }
    };


    ///////////////////////////////////////////////////////////////////////////
    // Ad loading
    ///////////////////////////////////////////////////////////////////////////

    private AdLoader mAdLoader;

    private final AdLoaderListener mAdLoaderListener = new AdLoaderListener() {
        @Override
        public void onAdLoadingError(AdLoader adLoader, int errorCode) {
            // Improve error code. AdLoader doesn't know have a Context si it can't check
            // if there is a network connection.
            if ((errorCode == AdLoader.ERROR_UNKNOWN_HOST) && !NetworkUtil.isNetworkConnected(mContext)) {
                errorCode = ERROR_NETWORK_NOT_AVAILABLE;
            }

            onError(errorCode);
        }


        @Override
        public void onAdLoaded(AdLoader adLoader, Bundle ad) {
            showAd(ad);
        }
    };


    private void createAdLoader() {
        mAdLoader = new AdLoader();
        mAdLoader.setListener(mAdLoaderListener);
        mAdLoader.setTag(Log.makeTag("InterstitialLoader"));
    }


    private void releaseAdLoader() {
        if (mAdLoader != null) {
            mAdLoader.cancel();
            mAdLoader = null;
        }
    }


    /**
     * Shows an ad from an ad request
     */
    public void showAd(AdRequestBuilder adRequestBuilder) {
        if (mAdLoader != null) {
            mAdLoader.load(adRequestBuilder);
        }

    }


    /**
     * Shows an ad from an ad request
     */
    public void showAd(String adRequest) {
        if (mAdLoader != null) {
            mAdLoader.load(adRequest);
        }

    }


    /**
     * Shows an ad
     */
    public void showAd(Bundle ad) {
        int error = 0;
        if ((ad == null) || ad.isEmpty() || (ad.getString(Ad.URL) == null)) {
            error = ERROR_NO_INVENTORY;
            onError(error);
            return;

        } else if (mReleased) {
            error = ERROR_LOAD_AFTER_RELEASE;

        } else if (mActive) {
            error = ERROR_LOADING_IN_PROGRESS;

        } else if (!NetworkUtil.isNetworkConnected(mContext)) {
            error = ERROR_NETWORK_NOT_AVAILABLE;
        }

        if (error != 0) {
            onError(error);
           return;
        }

        mActive = true;

        // Adding a request code so we are able to filter the broadcasts sent by the activity this class has started.
        Intent intent = new Intent(mContext, InterstitialActivity.class);
        intent.putExtra(InterstitialActivity.EXTRA_AD, ad);
        intent.putExtra(InterstitialActivity.EXTRA_REQUEST_CODE, mRequestCode);
        mContext.startActivity(intent);

        onStarted();
    }


    /**
     * Converts the error codes to a string.
     *
     * @note Only use for debugging purpose.
     */
    public static String debugErrorToStr(int errorCode) {
        switch (errorCode) {
            case ERROR_INVALID_MEDIA_URL:
                return "Invalid media URL";
            case ERROR_LOAD_AFTER_RELEASE:
                return "Trying to load an ad after release()";
            case ERROR_LOADING_IN_PROGRESS:
                return "Loading already in progress";
            case ERROR_MEDIA_PLAYER_ERROR:
                return "Media player error";
            case ERROR_NETWORK_NOT_AVAILABLE:
                return "Network not available";
            case ERROR_NO_INVENTORY:
                return "No ad inventory";
            case ERROR_UNKNOWN:
                return "Unknown";
            case ERROR_UNKNOWN_HOST:
                return "Unknown host";
            case ERROR_UNSUPPORTED_MIME_TYPE:
                return "Unsupported MIME type";
            case 0:
                return "No error";

            default:
                Assert.failUnhandledValue(TAG, errorCode, "debugErrorToStr");
                return "Unknown";
        }
    }
}
