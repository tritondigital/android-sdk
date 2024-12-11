package com.tritondigital.player;

import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
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
import java.util.Locale;


/**
 * Provisioning Parser
 *
 * This class should be used only for special cases. Most users should use TritonPlayer.
 *
 * Fallback:
 *      - TRANSPORT_FLV is the default. It will be returned if another transport is selected
 *        but not available.
 *
 * Limitations:
 *      - Reading only the first "mountpoint".
 *      - Not supporting provisioning from a station name yet.
 */
class Provisioning
{
    interface Listener
    {
        void onProvisioningSuccess(Provisioning src, Bundle result);
        void onProvisioningFailed(Provisioning src, int errorCode);
    }


    abstract class Result
    {
        public static final String SERVERS      = "servers";
        public static final String STATUS       = "status";
        public static final String MIME_TYPE    = PlayerConsts.MIME_TYPE;
        public static final String MOUNT        = PlayerConsts.STATION_MOUNT;
        public static final String MOUNT_SUFFIX = "mount_suffix";
        public static final String TIMESHIFT_MOUNT_SUFFIX     = "timeshift_mount_suffix";
        public static final String SBM_SUFFIX   = "sbm_suffix";
        public static final String TRANSPORT    = PlayerConsts.TRANSPORT;
        public static final String ALTERNATE_URL    = "alternate_url";


        abstract class Server
        {
            public static final String HOST  = "host";
            public static final String PORTS = "ports";
        }
    }

    private static final String DOMAIN_NAME_PROD = "playerservices.streamtheworld.com";
    private static final String SERVER_PROD      = String.format("https://%s/api/livestream",DOMAIN_NAME_PROD);
    private static final String SERVER_HTTPS     = String.format("https://%s/api/livestream",DOMAIN_NAME_PROD);
    private static final String VERSION          = "1.10";
    private static final String TAG              = Log.makeTag("Provisioning");

    public static final int ERROR_GEOBLOCK            = 453;
    public static final int ERROR_NOT_FOUND           = 404;
    public static final int ERROR_REQUEST_TIMEOUT     = 408;
    public static final int ERROR_SERVICE_UNAVAILABLE = 503;
    public static final int ERROR_UNKNOWN_HOST        = 9001;

    private Listener   mListener;
    private String     mTransport = PlayerConsts.TRANSPORT_FLV;
    private String     mMount;
    private String     mUserAgent;
    private String     mPlayerServicesPrefix;
    private ParserTask mParserTask;
    private boolean     cloudStreaming = false;

    /**
     * Sets the transport. Fallback to TRANSPORT_HLS if the provided transport isn't available
     * on the current device.
     */
    public void setMount(String mount, String transport) {
        mMount = mount;
        setPreferedTransport(transport);
    }

    /**
     * Sets the transport. Fallback to TRANSPORT_HLS if the provided transport isn't available
     * on the current device.
     */
    public void setCloudStreaming(boolean cloudStreaming) {
        this.cloudStreaming = cloudStreaming;
    }


    public void setUserAgent(String userAgent)
    {
        mUserAgent = userAgent;
    }

    public void setPlayerServicesPrefix(String psPrefix)
    {
        mPlayerServicesPrefix = psPrefix;
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }


    /**
     * Request a provisioning
     */
    public void request() {
        if (mParserTask != null) {
            mParserTask.cancel(true);
        }

        mParserTask = new ParserTask(this, mTransport);

        if (android.os.Build.VERSION.SDK_INT < 11) {
            mParserTask.execute(mMount,mUserAgent, mPlayerServicesPrefix);
        } else {
            mParserTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, mMount,mUserAgent,mPlayerServicesPrefix);
        }
    }


    public void cancelRequest() {
        if (mParserTask != null) {
            mParserTask.cancel(true);
            mParserTask = null;
        }
    }


    private void onParseSuccess(ParserTask parserTask, Bundle result) {
        if (mParserTask == parserTask) {
            Log.i(TAG, "Success");
            mParserTask = null;
            mListener.onProvisioningSuccess(this, result);
        }
    }


    private void onParseFailed(ParserTask parserTask, int errorCode) {
        if (mParserTask == parserTask) {
            Log.e(TAG, "FAILED: " + errorCode);
            mParserTask = null;
            mListener.onProvisioningFailed(this, errorCode);
        }
    }


    private void setPreferedTransport(String transport) {
        if (transport == null) {
            mTransport = PlayerConsts.TRANSPORT_FLV;
        } else if (PlayerConsts.TRANSPORT_HLS.equals(transport) && !isHlsSupported()) {
            mTransport = PlayerConsts.TRANSPORT_FLV;
        } else {
            mTransport = transport;
        }
    }


    private static boolean isHlsSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH;
    }


    private static String createUrl(String mount, String suffix, String userAgent, String psPrefix) {
        // Get the provisioning URL
        String serverUrl = SERVER_PROD;

        if (suffix != null) {
            switch (suffix) {
                case ".https":   serverUrl = SERVER_HTTPS;   break;
                default:         serverUrl = SERVER_PROD;    break;
            }
        }

        if(!TextUtils.isEmpty(psPrefix))
        {
            String targetDomain = (psPrefix.toLowerCase() +"-" +DOMAIN_NAME_PROD);
            serverUrl = serverUrl.replace(DOMAIN_NAME_PROD, targetDomain);
        }

        Uri.Builder builder = Uri.parse(serverUrl).buildUpon();
        builder.appendQueryParameter("version",  VERSION);
        builder.appendQueryParameter("callsign", mount);

        if(!TextUtils.isEmpty(userAgent))
        {
            builder.appendQueryParameter("User-Agent", userAgent);
        }

        return builder.build().toString();
    }


    private static class ParserTask extends AsyncTask<String, Void, Bundle> {
        private final WeakReference<Provisioning> mSrcRef;
        private final String mTransport;
        private boolean mGeoblocked;
        private String mAlternateMount;
        private String mAlternateType;


        ParserTask(Provisioning src, String transport) {
            Assert.assertNotNull(TAG, src);
            mSrcRef = new WeakReference<>(src);
            mTransport = transport;
        }


        @Override
        protected Bundle doInBackground(String... mountArg) {
            Debug.renameThread("Provisioning");

            try {
                String mount = mountArg[0];
                String suffix = null;

                // Handle ".dev" and ".preprod" suffix
                int dotIdx = mountArg[0].indexOf('.');
                if (dotIdx != -1) {
                    mount = mountArg[0].substring(0, dotIdx);
                    suffix = mountArg[0].substring(dotIdx, mountArg[0].length()).toLowerCase(Locale.ENGLISH);
                }

                String userAgent = mountArg[1];
                String psPrefix  = mountArg[2];

                // Parse main XML
                String url = createUrl(mount, suffix, userAgent,psPrefix);
                Bundle result = parseXml(url);

                // Alternate mount XML
                int status = result.getInt(Result.STATUS);
                if ((status == ERROR_GEOBLOCK) && !TextUtils.isEmpty(mAlternateMount)) {
                    mGeoblocked = false;
                    url = createUrl(mAlternateMount, suffix, userAgent,psPrefix);
                    result = parseXml(url);
                }

                return result;
            } catch (java.net.SocketTimeoutException e) {
                Log.e(TAG, e, "Error");
                Bundle result = new Bundle();
                result.putInt(Result.STATUS, ERROR_REQUEST_TIMEOUT);
                return result;
            } catch (java.net.UnknownHostException e) {
                Log.e(TAG, e, "Error");
                Bundle result = new Bundle();
                result.putInt(Result.STATUS, ERROR_UNKNOWN_HOST);
                return result;
            } catch (Exception e) {
                Log.e(TAG, e, "Error");
            }

            return null;
        }


        @Override
        protected void onPostExecute(Bundle result) {
            Provisioning src = mSrcRef.get();
            if (src != null) {
                int status = (result == null) ? 0 : result.getInt(Result.STATUS);
                switch (status) {
                    case 200:
                        src.onParseSuccess(this, result);
                        break;

                    default:
                        src.onParseFailed(this, status);
                        break;
                }
            }
        }


        private InputStream createInputStream(String urlString) throws IOException {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setReadTimeout(12000);
            conn.setConnectTimeout(15000);
            conn.setRequestMethod("GET");
            conn.setDoInput(true);

            // Starts the query
            conn.connect();
            return conn.getInputStream();
        }


        private Bundle parseXml(String url) throws XmlPullParserException, IOException {
            Log.i(TAG, "Do provisioning: " + url);
            InputStream inputStream = null;
            Bundle provisioning = null;

            try {
                inputStream = createInputStream(url);
                provisioning = parse(inputStream);
            } finally {
                if (inputStream != null) {
                    inputStream.close();
                }
            }

            return provisioning;
        }


        public Bundle parse(InputStream in) throws XmlPullParserException, IOException {
            try {
                XmlPullParser parser = Xml.newPullParser();
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
                parser.setInput(in, null);
                parser.nextTag();
                return readLiveStreamConfig(parser);
            } finally {
                in.close();
            }
        }


        private Bundle readLiveStreamConfig(XmlPullParser parser) throws XmlPullParserException, IOException, NumberFormatException {
            Bundle provisioning = null;

            parser.require(XmlPullParser.START_TAG, null, "live_stream_config");
            while (parser.next() != XmlPullParser.END_TAG) {
                if (parser.getEventType() == XmlPullParser.START_TAG) {
                    String elementName = parser.getName();
                    if ("mountpoints".equals(elementName)) {
                        provisioning = readMountpoints(parser);
                        break;
                    } else {
                        XmlPullParserUtil.skip(parser);
                    }
                }
            }

            return provisioning;
        }


        private Bundle readMountpoints(XmlPullParser parser) throws XmlPullParserException, IOException, NumberFormatException {
            parser.require(XmlPullParser.START_TAG, null, "mountpoints");

            while (parser.next() != XmlPullParser.END_TAG) {
                if (parser.getEventType() == XmlPullParser.START_TAG) {
                    String elementName = parser.getName();
                    if ("mountpoint".equals(elementName)) {
                        return readMountpoint(parser);
                    } else {
                        XmlPullParserUtil.skip(parser);
                    }
                }
            }

            return null;
        }


        private Bundle readMountpoint(XmlPullParser parser) throws XmlPullParserException, IOException, NumberFormatException {
            Bundle provisioningResult = new Bundle();
            parser.require(XmlPullParser.START_TAG, null, "mountpoint");

            while (parser.next() != XmlPullParser.END_TAG) {
                if (parser.getEventType() == XmlPullParser.START_TAG) {
                    String elementName = parser.getName();
                    if (mGeoblocked) {
                        if ("alternate-content".equals(elementName)) {
                            String alternate = readAlternateContent(parser);
                            if (alternate != null) {
                                if(mAlternateType == "mount") {
                                    mAlternateMount = alternate;
                                return provisioningResult;
                                } else if (mAlternateType == "url") {
                                    mGeoblocked = false;
                                    provisioningResult.putInt(Result.STATUS, 200);
                                    provisioningResult.putString(Result.ALTERNATE_URL, alternate);
                                }
                            }
                        } else {
                            XmlPullParserUtil.skip(parser);
                        }
                    } else {
                        switch (elementName) {
                            case "status":
                                int status = readStatus(parser);
                                provisioningResult.putInt(Result.STATUS, status);
                                mGeoblocked = (status == ERROR_GEOBLOCK);
                                break;

                            case "transports":
                                readTransports(parser, provisioningResult);
                                break;

                            case "metadata":
                                readMetadata(parser, provisioningResult);
                                break;

                            case "servers":
                                ArrayList<Bundle> servers = readServers(parser);
                                if ((servers == null) || servers.isEmpty()) {
                                    provisioningResult.putInt(Result.STATUS, ERROR_SERVICE_UNAVAILABLE);
                                } else {
                                    provisioningResult.putParcelableArrayList(Result.SERVERS, servers);
                                }
                                break;

                            case "mount":
                                String mount = XmlPullParserUtil.readText(parser);
                                provisioningResult.putString(Result.MOUNT, mount);
                                break;

                            case "media-format":
                                readMediaFormat(parser, provisioningResult);
                                break;

                            default:
                                XmlPullParserUtil.skip(parser);
                                break;
                        }
                    }
                }
            }

            return provisioningResult;
        }


        private String readAlternateContent(XmlPullParser parser) throws XmlPullParserException, IOException {
            String alternate = null;
            parser.require(XmlPullParser.START_TAG, null, "alternate-content");

            while (parser.next() != XmlPullParser.END_TAG) {
                if (parser.getEventType() == XmlPullParser.START_TAG) {
                    String elementName = parser.getName();
                    if ("mount".equals(elementName)) {
                        alternate = XmlPullParserUtil.readText(parser);
                        mAlternateType = "mount";
                    } else if ("url".equals(elementName)) {
                         alternate = XmlPullParserUtil.readText(parser);
                         mAlternateType = "url";
                    }
                    else {
                        XmlPullParserUtil.skip(parser);
                    }
                }
            }

            return alternate;
        }


        private String codecToMimeType(String codec) {
            if (codec != null) {
                if (codec.equals("mp3")) {
                    return PlayerConsts.MIME_TYPE_MPEG;
                } else if (codec.contains("aac")) {
                    return PlayerConsts.MIME_TYPE_AAC;
                }
            }

            Log.e(TAG, "Unable to convert the codec to a MIME type: " + codec);
            return null;
        }


        private void readMediaFormat(XmlPullParser parser, Bundle outProvisioningBundle) throws XmlPullParserException, IOException {
            parser.require(XmlPullParser.START_TAG, null, "media-format");

            while (parser.next() != XmlPullParser.END_TAG) {
                if (parser.getEventType() == XmlPullParser.START_TAG) {
                    String elementName = parser.getName();
                    if ("audio".equals(elementName)) {
                        String codec = parser.getAttributeValue(null, "codec");
                        String mimeType = codecToMimeType(codec);
                        outProvisioningBundle.putString(Result.MIME_TYPE, mimeType);
                    } else {
                        XmlPullParserUtil.skip(parser);
                    }
                }
            }
        }


        private int readStatus(XmlPullParser parser) throws XmlPullParserException, IOException, NumberFormatException {
            int status = 0;
            parser.require(XmlPullParser.START_TAG, null, "status");

            while (parser.next() != XmlPullParser.END_TAG) {
                if (parser.getEventType() == XmlPullParser.START_TAG) {
                    String elementName = parser.getName();
                    if ("status-code".equals(elementName)) {
                        status = XmlPullParserUtil.readInt(parser, 0);
                    } else {
                        XmlPullParserUtil.skip(parser);
                    }
                }
            }

            return status;
        }


        private void readTransports(XmlPullParser parser, Bundle outProvisioningBundle) throws XmlPullParserException, IOException {
            parser.require(XmlPullParser.START_TAG, null, "transports");

            // Default value
            outProvisioningBundle.putString(Result.TRANSPORT, mTransport);

            if (PlayerConsts.TRANSPORT_HLS.equals(mTransport)) {
                while (parser.next() != XmlPullParser.END_TAG) {
                    if (parser.getEventType() == XmlPullParser.START_TAG) {
                        String elementName = parser.getName();
                        if ("transport".equals(elementName)) {
                            String mountSuffix = parser.getAttributeValue(null, "mountSuffix");
                            String timeshift = parser.getAttributeValue(null, "timeshift");
                            if (mountSuffix != null) {
                                String transport = XmlPullParserUtil.readText(parser);
                                if( timeshift != null && timeshift.equalsIgnoreCase("true")){
                                    outProvisioningBundle.putString(Result.TRANSPORT, "hls");
                                    outProvisioningBundle.putString(Result.TIMESHIFT_MOUNT_SUFFIX, mountSuffix);
                                }else{
                                    if ("hls".equals(transport)) {
                                        outProvisioningBundle.putString(Result.TRANSPORT, transport);
                                        outProvisioningBundle.putString(Result.MOUNT_SUFFIX, mountSuffix);
                                    }
                                }
                            } else {
                                XmlPullParserUtil.skip(parser);
                            }
                        } else {
                            XmlPullParserUtil.skip(parser);
                        }
                    }
                }
            } else {
                XmlPullParserUtil.skip(parser);
            }
        }

        private void readMetadata(XmlPullParser parser, Bundle outProvisioningBundle) throws XmlPullParserException, IOException {
            parser.require(XmlPullParser.START_TAG, null, "metadata");

            while (parser.next() != XmlPullParser.END_TAG) {
                if (parser.getEventType() == XmlPullParser.START_TAG) {
                    String elementName = parser.getName();
                    if ("sse-sideband".equals(elementName)) {
                        String enabled = parser.getAttributeValue(null, "enabled");
                        if ("true".equals(enabled)) {
                            String sbmSuffix = parser.getAttributeValue(null, "metadataSuffix");
                            outProvisioningBundle.putString(Result.SBM_SUFFIX, sbmSuffix);
                        }
                    }

                    if (PlayerConsts.TRANSPORT_SC.equals(mTransport)) {
                        if ("shoutcast-v1".equals(elementName) || "shoutcast-v2".equals(elementName)) {
                            String enabled = parser.getAttributeValue(null, "enabled");
                            if ("true".equals(enabled)) {
                                String mountSuffix = parser.getAttributeValue(null, "mountSuffix");
                                outProvisioningBundle.putString(Result.MOUNT_SUFFIX, mountSuffix);
                            }
                        }
                    }

                    XmlPullParserUtil.skip(parser);
                }
            }
        }


        private ArrayList<Bundle> readServers(XmlPullParser parser) throws XmlPullParserException, IOException {
            parser.require(XmlPullParser.START_TAG, null, "servers");
            ArrayList<Bundle> servers = new ArrayList<>();

            while (parser.next() != XmlPullParser.END_TAG) {
                if (parser.getEventType() == XmlPullParser.START_TAG) {
                    String elementName = parser.getName();
                    if ("server".equals(elementName)) {
                        Bundle server = readServer(parser);
                        servers.add(server);
                    } else {
                        XmlPullParserUtil.skip(parser);
                    }
                }
            }

            return servers;
        }


        private Bundle readServer(XmlPullParser parser) throws XmlPullParserException, IOException {
            parser.require(XmlPullParser.START_TAG, null, "server");
            Bundle server = new Bundle();

            while (parser.next() != XmlPullParser.END_TAG) {
                if (parser.getEventType() == XmlPullParser.START_TAG) {
                    switch (parser.getName()) {
                        case "ip":
                            String host = XmlPullParserUtil.readText(parser);
                            server.putString(Result.Server.HOST, host);
                            break;

                        case "ports":
                            ArrayList<String> ports = readPorts(parser);
                            server.putStringArrayList(Result.Server.PORTS, ports);
                            break;

                        default:
                            XmlPullParserUtil.skip(parser);
                            break;
                    }
                }
            }

            return server;
        }


        private ArrayList<String> readPorts(XmlPullParser parser) throws XmlPullParserException, IOException {
            parser.require(XmlPullParser.START_TAG, null, "ports");
            ArrayList<String> ports = new ArrayList<>();

            while (parser.next() != XmlPullParser.END_TAG) {
                if (parser.getEventType() == XmlPullParser.START_TAG) {
                    String elementName = parser.getName();
                    if ("port".equals(elementName)) {
                        // Ignoring non HTTP ports
                        String type = parser.getAttributeValue(null, "type");
                        String port = XmlPullParserUtil.readText(parser);
                        if ("https".equals(type)) {
                            ports.add(port);
                        }
                    } else {
                        XmlPullParserUtil.skip(parser);
                    }
                }
            }

            return ports;
        }
    }
}
