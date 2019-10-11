package com.tritondigital.ads;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Point;
import android.net.Uri;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import com.tritondigital.util.*;

import java.util.ArrayList;


/**
 * The View to display banner ads. The size must be set prior to calling showAd(Bundle).
 *
 * @par Ad Size
 * The ad size must be set prior to calling showAd(Bundle). The ad size and the
 * <a href="https://developer.android.com/reference/android/view/View.html">View</a> size
 * are not the same thing. For example, a 320x50 ad can be loaded in a
 * <a href="http://developer.android.com/reference/android/view/ViewGroup.LayoutParams.html#WRAP_CONTENT">
 * WRAP_CONTENT</a> view.
 *
 * @par Fallback Size
 * A fallback size can be set in case no ad is available for the main size.
 * This feature was added in order to easily support 320x50 and 300x50 in the same view.
 */
@SuppressWarnings("UnusedDeclaration")
public class BannerView extends FrameLayout {

    /** A listener for receiving BannerView notifications */
    public interface BannerListener {
        /** Called when a banner becomes visible */
        void onBannerLoaded(BannerView bannerView);

        /** Called after the banner has been cleared */
        void onBannerCleared(BannerView bannerView);

        /** Called after an error has occurred */
        void onBannerError(BannerView bannerView, int errorCode);
    }

    /** @copybrief AdLoader::ERROR_UNKNOWN */
    public static final int ERROR_UNKNOWN = AdLoader.ERROR_UNKNOWN;

    /** @copybrief AdLoader::ERROR_NO_INVENTORY */
    public static final int ERROR_NO_INVENTORY = AdLoader.ERROR_NO_INVENTORY;

    /** @copybrief AdLoader::ERROR_UNKNOWN_HOST */
    public static final int ERROR_UNKNOWN_HOST = AdLoader.ERROR_UNKNOWN_HOST;

    /** @copybrief AdLoader::ERROR_NETWORK_NOT_AVAILABLE */
    public static final int ERROR_NETWORK_NOT_AVAILABLE = AdLoader.ERROR_NETWORK_NOT_AVAILABLE;

    /** setBannerSize(int, int) must be called before loading an ad */
    public static final int ERROR_BANNER_SIZE_NOT_SET = 8012;

    /** Trying to load an ad after release() */
    public static final int ERROR_LOAD_AFTER_RELEASE = 8006;

    /** The loaded ad doesn't have any banner */
    public static final int ERROR_NO_BANNERS = 8010;

    /** The loaded ad doesn't have a banner of the right size */
    public static final int ERROR_NO_BANNER_RIGHT_SIZE = 8011;

    /** The maximum time has been reached */
    public static final int ERROR_TIMEOUT = 8013;


    // General
    protected final String TAG = Log.makeTag("BannerView");
    private static final String ABOUT_BLANK = "about:blank";

    private final float DENSITY_PIXELS_TO_PIXEL_SCALE;

    private BannerListener mListener;
    private int            mErrorCode;
    private boolean        mDestroyed;

    // Ad size
    private int          mBannerWidth;
    private int          mBannerHeight;
    private int          mBannerFallbackWidth;
    private int          mBannerFallbackHeight;
    private LayoutParams mLayoutParams;
    private LayoutParams mFallbackLayoutParams;

    // WebView
    private WebClient mWebViewClient;
    private WebView   mWebView;


    /**
     * Constructor
     */
    public BannerView(Context context) {
        this(context, null);
    }


    /**
     * Constructor
     */
    public BannerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }


    /**
     * Constructor
     */
    public BannerView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        DENSITY_PIXELS_TO_PIXEL_SCALE = isInEditMode() ? 1 : DisplayUtil.getDeviceDensityPixelScale(context);
    }


    ///////////////////////////////////////////////////////////////////////////
    // Life Cycle
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Pauses any extra processing associated with this BannerView.
     *
     * This method should be called in the parent
     * <a href="http://developer.android.com/reference/android/app/Activity.html#onPause()">
     * Activity.onPause()</a> method.
     *
     * \see http://developer.android.com/reference/android/webkit/WebView.html#onPause()
     */
    @TargetApi(11)
    public void onPause() {
        if ((mWebView != null) && (android.os.Build.VERSION.SDK_INT >= 11)) {
            mWebView.onPause();
        }
    }


    /**
     * Resumes an BannerView after a previous call to pause().
     *
     * This method should be called in the parent
     * <a href="http://developer.android.com/reference/android/app/Activity.html#onResume()">
     * Activity.onResume()</a> method.
     *
     * \see http://developer.android.com/reference/android/webkit/WebView.html#onResume()
     */
    @TargetApi(11)
    public void onResume() {
        if ((mWebView != null) && (android.os.Build.VERSION.SDK_INT >= 11)) {
            mWebView.onResume();
        }
    }


    /**
     * Destroys the internal state of this BannerView.
     *
     * This method should be called after this BannerView has been removed
     * from the view system. No other methods should be called this.
     */
    public void release() {
        mDestroyed = true;

        if (mWebView != null) {
            removeView(mWebView);
            mWebView.destroy();
            mWebView = null;
        }
    }


    ///////////////////////////////////////////////////////////////////////////
    // Listener
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Sets the banner listener
     */
    public void setListener(BannerListener listener) {
        mListener = listener;
    }


    /**
     * Returns the banner listener
     */
    public BannerListener getListener() {
        return mListener;
    }


    protected void onError(int error) {
        clearWebView();
        if ((mErrorCode == 0) && (error != 0)) {
            String errorMsg = "BannerView error: " + debugErrorToStr(error);

            if (error == ERROR_NO_BANNER_RIGHT_SIZE) {
                errorMsg += " (" + mBannerWidth + 'x' + mBannerHeight;

                if ((mBannerFallbackWidth != 0) && (mBannerFallbackHeight != 0)) {
                    errorMsg += " or " + mBannerFallbackWidth + 'x' + mBannerFallbackHeight;
                }
                errorMsg += ")";
            }

            Log.w(TAG, errorMsg);

            mErrorCode = error;
            if (mListener != null) {
                mListener.onBannerError(this, error);
            }
        }
    }


    private void onLoaded() {
        if ((mListener != null) && (mErrorCode == 0)) {
            mListener.onBannerLoaded(this);
        }
    }


    private void onCleared() {
        if ((mListener != null) && (mErrorCode == 0)) {
            mListener.onBannerCleared(this);
        }
    }


    ///////////////////////////////////////////////////////////////////////////
    // Banner size
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Returns the banner's width in density-independent pixels.
     */
    public int getBannerWidth() {
        return mBannerWidth;
    }


    /**
     * Returns the banner's height in density-independent pixels.
     */
    public int getBannerHeight() {
        return mBannerHeight;
    }


    /**
     * Returns the banner's fallback width in density-independent pixels.
     */
    public int getBannerFallbackWidth() {
        return mBannerFallbackWidth;
    }


    /**
     * Returns the banner's fallback height in density-independent pixels.
     */
    public int getBannerFallbackHeight() {
        return mBannerFallbackHeight;
    }


    /**
     * @see BannerView#setBannerSize(int, int, int, int)
     */
    public void setBannerSize(int width, int height) {
        setBannerSize(width, height, 0, 0);
    }


    /**
     * Sets the ad size in density-independent pixels.
     *
     * The size change will be effective only on the next ad display.
     * The fallback values will be used if no banner is found with the main size.
     */
    public void setBannerSize(int width, int height, int fallbackWidth, int fallbackHeight) {
        if ((mBannerWidth != width) || (mBannerHeight != height)) {
            mBannerWidth  = width;
            mBannerHeight = height;
            mLayoutParams = createLayoutParams(width, height);
        }

        if ((mBannerFallbackWidth != fallbackWidth) || (mBannerFallbackHeight != fallbackHeight)) {
            mBannerFallbackWidth  = fallbackWidth;
            mBannerFallbackHeight = fallbackHeight;
            mFallbackLayoutParams = (mBannerFallbackWidth > 0) ? createLayoutParams(fallbackWidth, fallbackHeight) : null;
        }
    }


    /**
     * Find the best banner size from an ad bundle which fits a layout container.
     * It returns null if no size fits the container
     *
     * @param ad  The Ad bundle with the banners to search for
     * @param containerWidth  The container width  in dp
     * @param containerHeight The container height in dp
     * @return a Point with Width and Height or null if no size fits the container
     */


    public Point getBestBannerSize(Bundle ad, int containerWidth, int containerHeight)
    {
        ArrayList<Bundle> banners = ad.getParcelableArrayList(Ad.BANNERS);
        if(banners == null || banners.isEmpty())
        {
            return null;
        }

        int maxWidthBetterFit  = 0;
        int maxHeightBetterFit = 0;

        for (Bundle banner : banners) {
            int bannerWidth  = banner.getInt(Ad.WIDTH);
            int bannerHeight = banner.getInt(Ad.HEIGHT);

            if ((bannerWidth <= containerWidth) && (bannerHeight <= containerHeight)) {
                com.tritondigital.util.Log.d("BannersWrapper", "Banner size:  " +  bannerWidth + " , " + bannerHeight);
                if(maxWidthBetterFit <= bannerWidth && maxHeightBetterFit <= bannerHeight)
                {
                    maxWidthBetterFit = bannerWidth;
                    maxHeightBetterFit = bannerHeight;
                }
            }
        }

        if(maxWidthBetterFit > 0 && maxHeightBetterFit > 0)
        {
            return new Point(maxWidthBetterFit,maxHeightBetterFit);
        }


        return null;
    }


    /**
     * Creates a LayoutParams with the provided density-independent pixel size.
     */
    private LayoutParams createLayoutParams(int width, int height) {
        int densityWidth  = DisplayUtil.densityPixelsToPixels(DENSITY_PIXELS_TO_PIXEL_SCALE, width);
        int densityHeight = DisplayUtil.densityPixelsToPixels(DENSITY_PIXELS_TO_PIXEL_SCALE, height);
        return new LayoutParams(densityWidth, densityHeight, Gravity.CENTER);
    }


    ///////////////////////////////////////////////////////////////////////////
    // Ad loading
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Show an ad.
     *
     * This view takes care of taking the right banner from the ad bundle.
     *
     * @param ad The ad to load.
     */
    public void showAd(Bundle ad) {
        // Validate view size
        if ((mBannerWidth <= 0) || (mBannerHeight <= 0)) {
            onError(ERROR_BANNER_SIZE_NOT_SET);
            return;
        }

        // No ad --> clear ad
        if (ad == null) {
            clearBanner();
            return;
        }

        // Get all banners
        ArrayList<Bundle> banners = ad.getParcelableArrayList(Ad.BANNERS);
        if ((banners == null) || (banners.size() == 0)) {
            onError(ERROR_NO_BANNERS);
            return;
        }

        // Find the banner for the right size
        boolean fallbackSize = false;
        Bundle banner = findBanner(banners, mBannerWidth, mBannerHeight);
        if (banner == null) {
            fallbackSize = true;
            banner = findBanner(banners, mBannerFallbackWidth, mBannerFallbackHeight);
        }

        if (banner == null) {
            onError(ERROR_NO_BANNER_RIGHT_SIZE);
            return;
        }

        String bannerUrl = banner.getString(Ad.URL);
        if ( bannerUrl != null )
            loadUrl(bannerUrl);
        else
        {
            String bannerHtml = banner.getString(Ad.HTML);
            if ( bannerHtml != null )
                loadHTML(bannerHtml);

        }

        // Resize the WebView to the ad size
        if (mWebView != null) {
            mWebView.setLayoutParams(fallbackSize ? mFallbackLayoutParams : mLayoutParams);
        }
    }


    private static Bundle findBanner(ArrayList<Bundle> banners, int reqWidth, int reqHeight) {
        for (Bundle banner : banners) {
            int bannerWidth = banner.getInt(Ad.WIDTH);
            if (bannerWidth == reqWidth) {
                int bannerHeight = banner.getInt(Ad.HEIGHT);
                if (bannerHeight == reqHeight) {
                    return banner;
                }
            }
        }

        return null;
    }


    /**
     * Clears the current ad but keep the
     * <a href="https://developer.android.com/reference/android/view/View.html">
     * View</a> in order to reuse it later.
     *
     * This method is usually called at the end of an audio ad with a sync banner.
     */
    public void clearBanner() {
        if (!mDestroyed) {
            hideWebView();
            mErrorCode = 0;
            clearWebView();
        }
    }


    /**
     * Returns true if the an ad is loaded.
     */
    public boolean isBannerLoaded() {
        return (mWebView != null) && (mWebView.getVisibility() == View.VISIBLE);
    }


    ///////////////////////////////////////////////////////////////////////////
    // Web view
    ///////////////////////////////////////////////////////////////////////////

    private void showWebView() {
        if (mWebView != null) {
            mWebView.setVisibility(View.VISIBLE);
        }
    }


    private void hideWebView() {
        if (mWebView != null) {
            mWebView.setVisibility(View.GONE);
        }
    }


    private void clearWebView() {
        if (mWebViewClient == null) {
            onCleared();
        } else {
            mWebViewClient.clearBanner();
        }
    }


    private void loadUrl(String url) {
        createWebViewIfNeeded(url);

        if (mWebViewClient != null) {
            mWebViewClient.loadBanner(url);
        }
    }


    private void loadHTML(String html) {
        // Create the webview
        createWebViewIfNeeded("http");

        if ( mWebViewClient != null )
        {
           // mWebView
            String newHTML;
            int test = html.toLowerCase().indexOf("<html>");
            if ( test == -1 )
            {
                newHTML = "<html><head><style type=\"text/css\">\n" +
                        "html, body {\n" +
                        "width:100%;\n" +
                        "height: 100%;\n" +
                        "margin: 0px;\n" +
                        "padding: 0px;\n" +
                        "}\n" +
                        "</style></head><body>" + html + "</body><html>";
            }
            else
            {
                newHTML = html;
            }
            mWebViewClient.loadHTML(newHTML);

        }
    }


    @TargetApi(11)
    @SuppressLint({"SetJavaScriptEnabled"})
    private void createWebViewIfNeeded(String bannerUrl) {
        if ((mWebView == null) && (bannerUrl != null) && bannerUrl.startsWith("http")) {
            mWebView = new WebView(getContext());
            mWebView.setFocusable(false);
            mWebView.setHorizontalScrollBarEnabled(false);
            mWebView.setOverScrollMode(View.OVER_SCROLL_NEVER);
            mWebView.setScrollBarStyle(WebView.SCROLLBARS_OUTSIDE_OVERLAY);
            mWebView.setVerticalScrollBarEnabled(false);
            mWebView.setVisibility(View.GONE);
            mWebView.setBackgroundColor(Color.TRANSPARENT);

            // WebView client
            mWebViewClient = (android.os.Build.VERSION.SDK_INT >= 11) ? new WebClientV11() : new WebClient();
            mWebView.setWebViewClient(mWebViewClient);

            // Web settings
            WebSettings webSettings = mWebView.getSettings();
            webSettings.setJavaScriptEnabled(true);
            webSettings.setSupportZoom(false);

            if (android.os.Build.VERSION.SDK_INT >= 11) {
                webSettings.setDisplayZoomControls(false);
            }

            addView(mWebView, mLayoutParams);
        }
    }


    /**
     * Converts the error codes to a string.
     *
     * @note Only use for debugging purpose.
     */
    public static String debugErrorToStr(int errorCode) {
        switch (errorCode) {
            case ERROR_BANNER_SIZE_NOT_SET:   return "Banner size not set";
            case ERROR_LOAD_AFTER_RELEASE:    return "Load called after released";
            case ERROR_NETWORK_NOT_AVAILABLE: return "Network not available";
            case ERROR_NO_INVENTORY:          return "No ad inventory";
            case ERROR_NO_BANNERS:            return "No banners";
            case ERROR_NO_BANNER_RIGHT_SIZE:  return "No banner with right size";
            case ERROR_TIMEOUT:               return "Timeout";
            case ERROR_UNKNOWN:               return "Unknown";
            case ERROR_UNKNOWN_HOST:          return "Unknown host";
            case 0:                           return "No error";

            default:
                Assert.failUnhandledValue(Log.makeTag("BannerView"), errorCode, "debugErrorToStr");
                return "Unknown";
        }
    }


    /**
     * Controls the loading of an URL in the web view or launch the external browser.
     */
    private class WebClient extends WebViewClient implements View.OnTouchListener {
        protected boolean mWebViewClicked;


        WebClient() {
            super();

            if (mWebView != null) {
                mWebView.setOnTouchListener(this);
            }
        }


        void clearBanner() {
            loadBanner(ABOUT_BLANK);
        }


        void loadBanner(String url) {
            if (mWebView == null) {
                onError(ERROR_LOAD_AFTER_RELEASE);
            } else {
                if (!ABOUT_BLANK.equals(url) && !ABOUT_BLANK.equals(mWebView.getUrl())) {
                    Log.d(TAG, "Loading banner: " + url);
                }

                mWebViewClicked = false;
                mWebView.loadUrl(url);
            }
        }

        void loadHTML(String html) {
            if (mWebView == null) {
                onError(ERROR_LOAD_AFTER_RELEASE);
            } else {

               Log.d(TAG, "Loading banner html ");


                mWebViewClicked = false;
                mWebView.loadData(html, "text/html", "UTF-8");
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public synchronized boolean onTouch(View webView, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    mWebViewClicked = true;
                    return false;

                case MotionEvent.ACTION_UP:
                    return false;

                default:
                    return true;
            }
        }


        @Override
        public synchronized boolean shouldOverrideKeyEvent(WebView view, KeyEvent event) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_CENTER) {
                mWebViewClicked = true;
                return false;
            } else {
                return true;
            }
        }


        @Override
        public synchronized boolean shouldOverrideUrlLoading(WebView webView, String url) {
            // Because of threading, the clicked banner might not be valid anymore.
            if (mWebViewClicked) {
                onBannerClicked(url);
                return true;
            } else {
                return false;
            }
        }


        @Override
        public synchronized void onReceivedError(WebView webView, int errorCode, String description, String failingUrl) {
            Log.w(TAG, "Banner error: " + failingUrl + " Description:" + description);
            hideWebView();

            int adError = webViewToAdError(errorCode);
            onError(adError);
            clearBanner();
        }


        private int webViewToAdError(int webViewError) {
            switch (webViewError) {
                case WebViewClient.ERROR_HOST_LOOKUP: return ERROR_UNKNOWN_HOST;
                case WebViewClient.ERROR_TIMEOUT:     return ERROR_TIMEOUT;
                default:                              return ERROR_UNKNOWN;
            }
        }


        @Override
        public synchronized void onPageFinished(WebView webView, String url) {
            if ((url == null) || url.equals(ABOUT_BLANK)) {
                hideWebView();
                onCleared();
            } else {
                showWebView();
                onLoaded();
            }
        }


        protected synchronized void onBannerClicked(String externalUrl) {
            // Reset clicked flag
            mWebViewClicked = false;
            launchBrowser(externalUrl);
        }


        private void launchBrowser(String externalUrl) {
            if (externalUrl != null) {
                // Launch the external URI
                Log.i(TAG, "Launching external ad: " + externalUrl);
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(externalUrl));
                getContext().startActivity(intent);
            }
        }
    }


    @TargetApi(11)
    private class WebClientV11 extends WebClient {
        WebClientV11() {
            super();
        }


        /**
         * WARNING: Called by "WebViewCoreThread"
         */
        @Override
        public synchronized WebResourceResponse shouldInterceptRequest(WebView webView, String url) {
            if (mWebViewClicked) {
                onBannerClicked(url);
            }

            return null;
        }
    }
}
