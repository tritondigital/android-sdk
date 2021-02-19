package com.tritondigital.player;

import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Xml;

import com.tritondigital.util.Assert;
import com.tritondigital.util.Debug;
import com.tritondigital.util.Log;
import com.tritondigital.util.XmlPullParserUtil;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;


/**
 * Requests the cue point history from Triton's servers.
 *
 * @par Restrictions
 *  - The same history will be returned for request faster than 15 seconds.
 *  - The data provided by this class is not sync with the stream.
 *  - Some streams are configured to return only CuePoint.CUE_TYPE_VALUE_TRACK.
 *
 * @par Example
 * @code{.java}
 *      public class SongHistoryExample extends Activity implements CuePointHistoryListener
 *      {
 *          private CuePointHistory mCuePointHistory;
 *
 *
 *          protected void onCreate(Bundle savedInstanceState) {
 *              super.onCreate(savedInstanceState);
 *
 *              // Init the cue point history object
 *              mCuePointHistory = new CuePointHistory();
 *              mCuePointHistory.setListener(this);
 *              mCuePointHistory.setCueTypeFilter(CuePoint.CUE_TYPE_VALUE_TRACK);
 *              mCuePointHistory.setMaxItems(10);
 *              mCuePointHistory.setMount("MOBILEFM");
 *
 *              // Request the track history
 *              mCuePointHistory.request();
 *          }
 *
 *
 *          public void onCuePointHistoryReceived(CuePointHistory src, List<> cuePoints) {
 *              // Handle history here
 *          }
 *
 *
 *          public void onCuePointHistoryFailed(CuePointHistory src, int errorCode) {
 *              // Handle errors here
 *          }
 *      }
 * @endcode
 */
@SuppressWarnings("JavaDoc")
public final class CuePointHistory {
    /**
     * Callback for receiving the CuePoint history.
     */
    public interface CuePointHistoryListener {
        /**
         * Called when the CuePoint history has been received.
         *
         * @param src       Source where this event comes from
         * @param cuePoints Cue points
         */
        void onCuePointHistoryReceived(CuePointHistory src, List<Bundle> cuePoints);

        /**
         * Called when the CuePoint history has failed.
         *
         * @param src       Source where this event comes from
         * @param errorCode Error code
         */
        void onCuePointHistoryFailed(CuePointHistory src, int errorCode);
    }


    /** Invalid mount */
    public static final int ERROR_INVALID_MOUNT = 6001;

    /** Unknown error */
    public static final int ERROR_UNKNOWN = 6002;

    /** XML parsing error */
    public static final int ERROR_XML_PARSING = 6003;

    /** Network error */
    public static final int ERROR_NETWORK = 6004;


    private static final String TAG = Log.makeTag("CuePointHistory");
    private static final int MIN_REQUEST_TIME   = 15000;

    private static final String SERVER_PROD    = "https://np.tritondigital.com";
    private static final String SERVER_HTTPS   = "https://np.tritondigital.com";

    // User set values
    private String             mServer = SERVER_PROD;
    private final List<String> mCueTypeFilter = new ArrayList<>();
    private String             mMount;
    private int                mMaxItems;

    // Other
    private List<Bundle>    mCuePoints;
    private int             mErrorCode;
    private long            mLastRequestTime;
    private String          mLastUrl;
    private ListenerHandler mListenerHandler;


    /**
     * Constructor
     */
    public CuePointHistory() {
        super();
    }


    /**
     * Executes a CuePoint history request
     */
    public void request() {
        // Error checking
        if (!PlayerUtil.isMountNameValid(mMount)) {
            Log.e(TAG, "Invalid mount: " + mMount);
            setError(ERROR_INVALID_MOUNT);
            return;
        }

        // Update the URL
        String url = createUrl();

        // Capping.
        // Giving 1 second grace to avoid having polling at 29.9 seconds if user does it every 15 seconds
        long now = SystemClock.elapsedRealtime();
        if (TextUtils.equals(mLastUrl, url) && ((now - mLastRequestTime) < (MIN_REQUEST_TIME - 1000))) {
            if (isParsing()) {
                Log.i(TAG, "Already executing this request.");
            } else {
                Log.w(TAG, "Same request made less than " + (MIN_REQUEST_TIME / 1000) + "s ago.");
                notifyListener();
            }

            return;
        }

        // Service request
        mLastRequestTime = now;
        mLastUrl         = url;
        queryServer(url);
    }


    public void cancelRequest() {
        if (mParserTask != null) {
            mParserTask.cancel(true);
            mParserTask = null;
        }
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Result
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private void setCuePoints(List<Bundle> cuePoints) {
        mCuePoints = (cuePoints == null) ? null : Collections.unmodifiableList(cuePoints);
        mErrorCode = 0;
        notifyListener();
    }


    private void setError(int errorCode) {
        mCuePoints = null;
        mErrorCode = errorCode;
        notifyListener();
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    // URL
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Sets the station's mount
     */
    public void setMount(String mount) {
        mServer = SERVER_PROD;

        if (mount == null) {
            mMount = null;

        } else {
            int dotIdx = mount.indexOf('.');
            if (dotIdx == -1) {
                mMount = mount;

            } else {
                mMount = mount.substring(0, dotIdx);
                String suffix = mount.substring(dotIdx, mount.length()).toLowerCase(Locale.ENGLISH);

                switch (suffix) {
                    case ".https":   mServer = SERVER_HTTPS;   break;
                    default:         mServer = SERVER_PROD;    break;
                }
            }
        }
    }


    /**
     * Sets the maximum number of CuePoint to retrieve.
     * Requesting lots of items can slow down your request.
     *
     * @param maxItems  Maximum number of items (0 for all)
     */
    public void setMaxItems(int maxItems) {
        // No need to di a "min()" and "max()" since the server is taking care of it.
        mMaxItems = maxItems;
    }


    /**
     * Sets a CuePoint.CUE_TYPE filter for the next request
     */
    public void setCueTypeFilter(String cueType) {
        mCueTypeFilter.clear();
        mCueTypeFilter.add(cueType);
    }


    /**
     * @copybrief setCueTypeFilter(String)
     */
    public void setCueTypeFilter(List<String> cueTypes) {
        mCueTypeFilter.clear();

        if (cueTypes != null) {
            for (String type : cueTypes) {
                mCueTypeFilter.add(type);
            }
        }
    }


    /**
     * Clears the CuePoint.CUE_TYPE filter.
     */
    @SuppressWarnings("unused")
    public void clearCueTypeFilter() {
        mCueTypeFilter.clear();
    }


    private String createUrl() {
        String url = mServer + "/public/nowplaying?mountName=" + mMount + "&numberToFetch=" + mMaxItems;

        // Our service isn't a standard URL. The query parameter "eventType" can be there more than once.
        for (String cueType : mCueTypeFilter) {
            url += "&eventType=" + cueType;
        }

        return url;
    }


    /**
     * Utility method to convert an error code to a debug string.
     *
     * @note To be used only for debugging purpose.
     */
    public static String debugErrorToStr(int errorCode) {
        switch (errorCode) {
            case ERROR_INVALID_MOUNT: return "Invalid mount";
            case ERROR_UNKNOWN:       return "Unknown";
            case ERROR_XML_PARSING:   return "XML parsing";
            case ERROR_NETWORK:       return "Network";
            default:
                Assert.failUnhandledValue(TAG, errorCode, "debugErrorToStr");
                return "Unknown";
        }
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Listener
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private void notifyListener() {
        if (mListenerHandler != null) {
            mListenerHandler.removeCallbacksAndMessages(null);

            Message msg = (mErrorCode == 0)
                    ? mListenerHandler.obtainMessage(ListenerHandler.MSG_SUCCESS, mCuePoints)
                    : mListenerHandler.obtainMessage(ListenerHandler.MSG_ERROR, mErrorCode, 0);

            mListenerHandler.sendMessage(msg);
        }
    }


    /**
     * Returns the cue point history listener
     */
    private CuePointHistoryListener getListener() {
        return (mListenerHandler == null) ? null : mListenerHandler.getParserListener();
    }


    /**
     * Sets the result listener
     */
    public void setListener(CuePointHistoryListener listener) {
        if (getListener() != listener) {
            // Release previous listener handler.
            releaseListenerHandler();

            if (listener != null) {
                mListenerHandler = new ListenerHandler(this, listener);
            }
        }
    }


    private void releaseListenerHandler() {
        if (mListenerHandler != null) {
            mListenerHandler.removeCallbacksAndMessages(null);
            mListenerHandler = null;
        }
    }


    // Using a handler so the listener gets called on the next loop if we request
    // a listener notification directly from the history request method
    private static class ListenerHandler extends Handler {
        static final int MSG_ERROR   = 660;
        static final int MSG_SUCCESS = 661;

        final WeakReference<CuePointHistory> mSrcRef;
        final CuePointHistoryListener mListener;


        ListenerHandler(CuePointHistory src, CuePointHistoryListener listener) {
            Assert.assertNotNull(TAG, src);
            Assert.assertNotNull(TAG, listener);
            mSrcRef = new WeakReference<>(src);
            mListener = listener;
        }


        @Override
        @SuppressWarnings("unchecked")
        public void handleMessage(Message msg) {
            CuePointHistory src = mSrcRef.get();
            if ((src != null) && (mListener != null)) {
                switch (msg.what) {
                    case MSG_SUCCESS:
                        mListener.onCuePointHistoryReceived(src, (List<Bundle>) msg.obj);
                        break;

                    case MSG_ERROR:
                        mListener.onCuePointHistoryFailed(src, msg.arg1);
                        break;

                    default:
                        Assert.failUnhandledValue(TAG, msg.what, "handleMessage");
                        break;
                }
            }
        }


        public CuePointHistoryListener getParserListener() {
            return mListener;
        }
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Parser
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private ParserTask mParserTask;

    private boolean isParsing() {
        return mParserTask != null;
    }


    private void queryServer(String url) {
        if (mParserTask != null) {
            mParserTask.cancel(true);
        }

        mParserTask = new ParserTask(this);

        if (android.os.Build.VERSION.SDK_INT < 11) {
            mParserTask.execute(url);
        } else {
            mParserTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, url);
        }
    }


    private void onParseSuccess(ParserTask parserTask, List<Bundle> cuePoints) {
        if (mParserTask == parserTask) {
            mParserTask = null;
            setCuePoints(cuePoints);
        }
    }


    private void onParseFailed(ParserTask parserTask, int errorCode) {
        if (mParserTask == parserTask) {
            mParserTask = null;
            setError(errorCode);
        }
    }


    private static class ParserTask extends AsyncTask<String, Void, List<Bundle>> {
        private final WeakReference<CuePointHistory> mSrcRef;
        private volatile int mErrorCode;


        ParserTask(CuePointHistory src) {
            Assert.assertNotNull(TAG, src);
            mSrcRef = new WeakReference<>(src);
        }


        @Override
        protected List<Bundle> doInBackground(String... urls) {
            Debug.renameThread(TAG);

            String urlString = urls[0];
            Log.i(TAG, "History file: " + urlString);

            InputStream inputStream = null;

            try {
                URL url = new URL(urlString);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setReadTimeout(12000);
                conn.setConnectTimeout(15000);
                conn.setRequestMethod("GET");
                conn.setDoInput(true);
                conn.connect();

                inputStream = conn.getInputStream();

                XmlPullParser xmlParser = Xml.newPullParser();
                xmlParser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
                xmlParser.setInput(inputStream, null);
                xmlParser.nextTag();

                return readNowPlayingInfoList(xmlParser);

            } catch (java.io.FileNotFoundException e )
            {
                mErrorCode = ERROR_NETWORK;
            }
            catch (java.net.UnknownHostException e) {
                mErrorCode = ERROR_NETWORK;

            } catch (XmlPullParserException e) {
                Assert.fail(TAG, e);
                mErrorCode = ERROR_XML_PARSING;

            } catch (Exception e) {
                Log.e(TAG, e);
                mErrorCode = ERROR_UNKNOWN;

            } finally {
                if (inputStream != null) {
                    try {
                        inputStream.close();
                    } catch (IOException e) {
                        Log.v(TAG, e, "Ignored exception");
                    }
                }
            }

            return null;
        }


        @Override
        protected void onPostExecute(List<Bundle> cuePoints) {
            CuePointHistory src = mSrcRef.get();
            if (src != null) {
                if (mErrorCode == 0) {
                    src.onParseSuccess(this, cuePoints);
                } else {
                    src.onParseFailed(this, mErrorCode);
                }
            }
        }


        private ArrayList<Bundle> readNowPlayingInfoList(XmlPullParser parser) throws XmlPullParserException, IOException {
            ArrayList<Bundle> cuePointList = new ArrayList<>();
            parser.require(XmlPullParser.START_TAG, null, "nowplaying-info-list");

            while (parser.next() != XmlPullParser.END_TAG) {
                if (parser.getEventType() == XmlPullParser.START_TAG) {
                    String elementName = parser.getName();
                    if ("nowplaying-info".equals(elementName)) {
                        Bundle cuePoint = readNowPlayingInfo(parser);
                        cuePointList.add(cuePoint);
                    } else {
                        XmlPullParserUtil.skip(parser);
                    }
                }
            }

            return cuePointList;
        }


        private Bundle readNowPlayingInfo(XmlPullParser parser) throws XmlPullParserException, IOException {
            Bundle cuePoint = new Bundle();
            parser.require(XmlPullParser.START_TAG, null, "nowplaying-info");

            // Timestamp
            String timestampStr = parser.getAttributeValue(null, "timestamp");
            if (!TextUtils.isEmpty(timestampStr)) {
                try {
                    long timestamp = Long.parseLong(timestampStr) * 1000;
                    cuePoint.putLong(CuePoint.CUE_START_TIMESTAMP, timestamp);
                } catch (NumberFormatException e) {
                    Assert.fail(TAG, e);
                }
            }

            // Type
            String cueType = parser.getAttributeValue(null, "type");
            Assert.assertNotNull("\"type\" must be set", cueType);
            cuePoint.putString(CuePoint.CUE_TYPE, cueType);

            // Parse the "property" elements.
            while (parser.next() != XmlPullParser.END_TAG) {
                if (parser.getEventType() == XmlPullParser.START_TAG) {
                    String elementName = parser.getName();
                    if ("property".equals(elementName)) {
                        String key = parser.getAttributeValue(null, "name");
                        String value = XmlPullParserUtil.readText(parser);
                        CuePoint.addCuePointAttribute(cuePoint, cueType, key, value);
                    }
                } else {
                    XmlPullParserUtil.skip(parser);
                }
            }

            return cuePoint;
        }
    }
}
