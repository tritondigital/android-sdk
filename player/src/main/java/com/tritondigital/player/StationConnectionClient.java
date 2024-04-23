package com.tritondigital.player;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;

import com.tritondigital.util.Assert;
import com.tritondigital.util.Log;

import java.util.ArrayList;
import java.util.Random;


class StationConnectionClient {

    /**
     * @copybrief PlayerConsts.ERROR_CONNECTION_FAILED
     */
    public static final int ERROR_CONNECTION_FAILED = MediaPlayer.ERROR_CONNECTION_FAILED;

    /**
     * @copybrief PlayerConsts.ERROR_CONNECTION_TIMEOUT
     */
    public static final int ERROR_CONNECTION_TIMEOUT = MediaPlayer.ERROR_CONNECTION_TIMEOUT;

    /**
     * @copybrief MediaPlayer.ERROR_GEOBLOCKED
     */
    public static final int ERROR_GEOBLOCKED = MediaPlayer.ERROR_GEOBLOCKED;

    /**
     * @copybrief MediaPlayer.ERROR_NOT_FOUND
     */
    public static final int ERROR_NOT_FOUND = MediaPlayer.ERROR_NOT_FOUND;

    /**
     * @copybrief PlayerConsts.ERROR_SERVICE_UNAVAILABLE
     */
    public static final int ERROR_SERVICE_UNAVAILABLE = MediaPlayer.ERROR_SERVICE_UNAVAILABLE;

    /**
     * @copybrief PlayerConsts.STATION_MOUNT
     */
    public static final String SETTINGS_STATION_MOUNT = PlayerConsts.STATION_MOUNT;

    /**
     * @copybrief PlayerConsts::PLAYER_SERVICES_REGION
     */
    public static final String SETTINGS_PLAYER_SERVICES_REGION = PlayerConsts.PLAYER_SERVICES_REGION;

    /**
     * @copybrief PlayerConsts.TRANSPORT
     * Default: flv
     */
    public static final String SETTINGS_TRANSPORT = PlayerConsts.TRANSPORT;

    public static final String SETTINGS_USER_AGENT = PlayerConsts.USER_AGENT;

    private static final int MIN_RETRY_DELAY = 1000;
    private static final int MAX_RETRY_DELAY = 5000;
    private static final int MAX_TOTAL_DELAY = 30000;


    private final Handler      mHandler = new Handler();
    private final Random       mRandom;
    private final Provisioning mProvisioningParser;
    private final Listener     mListener;
    private final String       mStationMount;
    private String TAG = Log.makeTag("StationConnectionClient");
    private int    mProvisioningRetryDelay;
    private Bundle mProvisioningResult;
    private int    mPortIdx;
    private int    mServerIdx;
    private String programId;
    private int originalSeekValue;
    private final Runnable mFetchProvisioningRunnable = new Runnable() {
        @Override
        public void run() {
            fetchProvisioning();
        }
    };
    private String mSbmUrl;
    final Provisioning.Listener mProvisioningListener = new Provisioning.Listener() {
        @Override
        public void onProvisioningSuccess(Provisioning src, Bundle result) {
            mProvisioningResult = result;
            connectToStream();
        }


        @Override
        public void onProvisioningFailed(Provisioning src, int errorCode) {
            switch (errorCode) {
                case Provisioning.ERROR_GEOBLOCK:
                    // Stream redirection has already been done.
                    onError(ERROR_GEOBLOCKED);
                    break;

                case Provisioning.ERROR_SERVICE_UNAVAILABLE:
                    onError(ERROR_SERVICE_UNAVAILABLE);
                    break;

                case Provisioning.ERROR_UNKNOWN_HOST:
                    onError(ERROR_CONNECTION_FAILED);
                    break;

                case Provisioning.ERROR_NOT_FOUND:
                    onError(ERROR_NOT_FOUND);
                    break;

                case Provisioning.ERROR_REQUEST_TIMEOUT:
                    onError(ERROR_CONNECTION_TIMEOUT);
                    break;

                default:
                    delayProvisioning();
                    break;
            }
        }
    };


    public StationConnectionClient(Context context, Bundle settings, Listener listener) {
        if ((context == null) || (listener == null) || (settings == null)) {
            throw new IllegalArgumentException();
        }

        mRandom       = new Random();
        mListener     = listener;
        mStationMount = settings.getString(SETTINGS_STATION_MOUNT);
        originalSeekValue = settings.getInt(PlayerConsts.ORIGINAL_SEEK_VALUE);

        String transport = settings.getString(SETTINGS_TRANSPORT);
        String userAgent = settings.getString(SETTINGS_USER_AGENT);
        mProvisioningParser = new Provisioning();
        mProvisioningParser.setMount(mStationMount, transport);
        mProvisioningParser.setUserAgent(userAgent);
        mProvisioningParser.setListener(mProvisioningListener);

        String psPrefix  = settings.getString(SETTINGS_PLAYER_SERVICES_REGION);
        if (!TextUtils.isEmpty(psPrefix)) {
          mProvisioningParser.setPlayerServicesPrefix(psPrefix);
        }
    }

    public void start() {
        cancel();
        resetProvisioningRetryDelay();
        fetchProvisioning();
    }

    public void cancel() {
        mProvisioningParser.cancelRequest();
        mHandler.removeCallbacks(mFetchProvisioningRunnable);
    }

    public void notifyConnectionFailed() {
        Log.i(TAG, "Connect to stream -> FAILED");

        ArrayList<Bundle> serverList = getServerList();
        if (serverList != null && serverList.size() < mServerIdx) {
            Bundle server = serverList.get(mServerIdx);
            ArrayList<String> portList = server.getStringArrayList(Provisioning.Result.Server.PORTS);

            if (useNextPort(portList)) {
                return;
            }

            if (useNextServer(serverList)) {
                return;
            }
        }

        delayProvisioning();
    }

    public void setTag(String msg) {
        TAG = msg;
    }

    public String getAlternateMount() {
        if (mProvisioningResult == null) {
            return null;
        }

        String provisioningMount = mProvisioningResult.getString(Provisioning.Result.MOUNT);
        String settingsMount = PlayerUtil.removeMountSuffix(mStationMount);

        return TextUtils.equals(settingsMount, provisioningMount) ? null : provisioningMount;
    }

    public String getSideBandMetadataUrl() {
        return mSbmUrl;
    }

    public String getCastStreamingUrl(){
        if (mProvisioningResult != null) {
            ArrayList<Bundle> servers = mProvisioningResult.getParcelableArrayList(Provisioning.Result.SERVERS);
            Bundle server = servers.get(mServerIdx);
            ArrayList<String> ports = server.getStringArrayList(Provisioning.Result.Server.PORTS);

            //return Shoutcast url to cast to Google Cast devices
            return  "http://" + server.getString(Provisioning.Result.Server.HOST) + ':'
                    + ports.get(mPortIdx) + '/' + mProvisioningResult.getString(Provisioning.Result.MOUNT)+ "_SC";
        }

        return null;
    }

    public void setProgramId(String programId) {
        this.programId = programId;
    }

    private void onError(int errorCode) {
        mListener.onStationConnectionError(this, errorCode);
    }

    private void resetProvisioningRetryDelay() {
        mProvisioningRetryDelay = mRandom.nextInt(MAX_RETRY_DELAY - MIN_RETRY_DELAY + 1) + MIN_RETRY_DELAY;
        Log.d(TAG, "Reset retry delay to " + mProvisioningRetryDelay + "ms.");
    }

    private ArrayList<Bundle> getServerList() {
        if (mProvisioningResult != null) {
            ArrayList<Bundle> serverList = mProvisioningResult.getParcelableArrayList(Provisioning.Result.SERVERS);
            return (serverList == null || serverList.isEmpty()) ? null : serverList;
        }

        return null;
    }

    /**
     * Connect to next port.
     *
     * @return true if a port is available
     */
    private boolean useNextPort(ArrayList<String> portList) {
        if (portList != null) {
            if (++mPortIdx < portList.size()) {
                connectToStream();
                return true;
            }
        }

        Log.d(TAG, "No more ports on current server.");
        return false;
    }

    /**
     * Connect to next server.
     *
     * @return true if a server is available
     */
    private boolean useNextServer(ArrayList<Bundle> serverList) {
        if (serverList != null) {
            mPortIdx = 0;
            if (++mServerIdx < serverList.size()) {
                connectToStream();
                return true;
            }

        }

        Log.d(TAG, "No more servers to connect to.");
        return false;
    }

    private void connectToStream() {
        String transport = "";
        String mimeType = "" ;
        String streamUrl = "";
        String timeshiftStreamUrl = "";
        String timeshiftProgramUrl = "";
        String sbmUrl = "";

        String alternateUrl = mProvisioningResult.getString(Provisioning.Result.ALTERNATE_URL);
        if(!TextUtils.isEmpty(alternateUrl)){
            Bundle streamSettings = new Bundle();
            streamSettings.putString(TritonPlayer.SETTINGS_STREAM_URL,  alternateUrl);
            Log.i(TAG, "Connection to alternate media url: " + alternateUrl);
            mListener.onStationConnectionNextStream(this, streamSettings);
            return;
        }

        Log.d(TAG, "Creating stream URL for serverIdx:" + mServerIdx + " portIdx:" + mPortIdx);
        ArrayList<String> ports = null;
        try {
            ArrayList<Bundle> servers = mProvisioningResult.getParcelableArrayList(Provisioning.Result.SERVERS);
            Bundle server = servers.get(mServerIdx);
            ports = server.getStringArrayList(Provisioning.Result.Server.PORTS);

                final String baseUrl = "https://" + server.getString(Provisioning.Result.Server.HOST) + ':'
                    + ports.get(mPortIdx) + '/' + mProvisioningResult.getString(Provisioning.Result.MOUNT);

            //
            // Stream URL
            //
            String streamSuffix = mProvisioningResult.getString(Provisioning.Result.MOUNT_SUFFIX);
            String timeshiftStreamSuffix  = mProvisioningResult.getString(Provisioning.Result.TIMESHIFT_MOUNT_SUFFIX);


                streamUrl = (streamSuffix == null) ? baseUrl : (baseUrl + streamSuffix);
            timeshiftStreamUrl = (timeshiftStreamSuffix == null) ? streamUrl : (baseUrl + timeshiftStreamSuffix);
            if(programId != null && !programId.isEmpty()){
                timeshiftProgramUrl = (timeshiftStreamSuffix == null) ? "" : (baseUrl + "/CLOUD/HLS/program/" + programId + "/playlist.m3u8");
            }

            //
            // Stream info
            //
                transport = mProvisioningResult.getString(Provisioning.Result.TRANSPORT);
                mimeType = mProvisioningResult.getString(Provisioning.Result.MIME_TYPE);

            //
            // Side-Band Metadata
            //
            String sbmSuffix = mProvisioningResult.getString(Provisioning.Result.SBM_SUFFIX);
                sbmUrl = (sbmSuffix == null) ? null : (baseUrl + sbmSuffix);
                if (sbmUrl != null) {
                char argPrefix = sbmUrl.contains("?") ? '&' : '?';
                String sbmId = SbmPlayer.generateSbmId();
                sbmUrl += argPrefix + "sbmid=" + sbmId;
                mSbmUrl = sbmUrl;
            }


            //Normalize transport and sbm; if streamSuffix is null, we will stream FLV
                if (streamSuffix == null) {
                transport = PlayerConsts.TRANSPORT_FLV;
                sbmUrl    = null;
                }
            } catch (IndexOutOfBoundsException ex) {
                Log.i(TAG, "Connection client stream failed with port index: " + mPortIdx + " and port size:" + ((ports != null) ? ports.size() : "NULL"));
                Assert.fail(TAG, "Stream settings connection error: " + ex);
                notifyConnectionFailed();

            } catch (NullPointerException e) {
                Assert.fail(TAG, "Stream settings creation error: " + e);
                notifyConnectionFailed();
            }


            // Create the stream settings
            Bundle streamSettings = new Bundle();
            streamSettings.putString(StreamPlayer.SETTINGS_STREAM_URL,       streamUrl);
            streamSettings.putString(StreamPlayer.SETTINGS_TIMESHIFT_STREAM_URL, timeshiftStreamUrl);
            if(programId != null && !programId.isEmpty()){
                 streamSettings.putString(StreamPlayer.SETTINGS_TIMESHIFT_PROGRAM_URL, timeshiftProgramUrl);
            }
            streamSettings.putString(StreamPlayer.SETTINGS_TRANSPORT,        transport);
            streamSettings.putString(StreamPlayer.SETTINGS_STREAM_MIME_TYPE, mimeType);
            streamSettings.putString(StreamPlayer.SETTINGS_SBM_URL,          sbmUrl);
        streamSettings.putInt(PlayerConsts.ORIGINAL_SEEK_VALUE, this.originalSeekValue);

            // Ask the listener owner to try the next URL
            Log.i(TAG, "Connection client stream: " + streamUrl);
            mListener.onStationConnectionNextStream(this, streamSettings);     
    }

    private void fetchProvisioning() {
        mProvisioningResult = null;
        mServerIdx = 0;
        mPortIdx = 0;

        Log.d(TAG, "Fetch provisioning information.");
        mProvisioningParser.request();
    }

    private void delayProvisioning() {
        mProvisioningRetryDelay *= 2;
        Log.d(TAG, "Increment retry delay to " + mProvisioningRetryDelay + "ms.");

        if (mProvisioningRetryDelay >= MAX_TOTAL_DELAY) {
            onError(ERROR_CONNECTION_TIMEOUT);
        } else {
            mHandler.postDelayed(mFetchProvisioningRunnable, mProvisioningRetryDelay);
        }
    }


    interface Listener {
        void onStationConnectionError(StationConnectionClient src, int errorCode);

        void onStationConnectionNextStream(StationConnectionClient src, Bundle streamSettings);
        }
}
