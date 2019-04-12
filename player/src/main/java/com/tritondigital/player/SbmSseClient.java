package com.tritondigital.player;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.text.TextUtils;

import com.tritondigital.util.Assert;
import com.tritondigital.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;


/**
 * Connects to a Side-Band Metadata SSE server.
 *
 * Limitations:
 *      - Supposes the stream only send "data" fields.
 */
class SbmSseClient extends Handler {
    interface SseClientListener {
        void onSbmSseClientCuePointReceived(SbmSseClient sseClient, Bundle cuePoint);
        void onSbmSseClientStateChanged(SbmSseClient sseClient, int state);
    }


    public static final int STATE_CONNECTING   = 5201;
    public static final int STATE_CONNECTED    = 5202;
    public static final int STATE_DISCONNECTED = 5203;
    public static final int STATE_ERROR        = 5204;

    private static final int ACTION_STATE_CHANGED     = 5250;
    private static final int ACTION_CUEPOINT_RECEIVED = 5251;
    private static final String TAG = Log.makeTag("SbmPlayerSseClient");

    private final long              mInitUptimeMilis;
    private final String            mUrl;
    private final SseClientListener mListener;
    private Thread                  mSseClientThread;
    private volatile int            mOffset;


    SbmSseClient(final String url, final SseClientListener listener) {
        mInitUptimeMilis = SystemClock.uptimeMillis();
        mUrl = url;
        mListener = listener;

        mSseClientThread = new Thread(TAG) {
            @Override
            public void run() {
                super.run();
                connect();
            }
        };
        mSseClientThread.start();
    }


    public void release() {
        if (mSseClientThread != null) {
            try {
                mSseClientThread.interrupt();
                mSseClientThread.join(500);
                mSseClientThread = null;
            } catch (Exception e) {
                Log.e(TAG, e, "release()");
            }

            removeMessages(SbmSseClient.ACTION_CUEPOINT_RECEIVED);
        }
    }


    @Override
    public void handleMessage(Message msg) {
        if (mListener != null) {
            switch (msg.what) {
                case ACTION_CUEPOINT_RECEIVED:
                    mListener.onSbmSseClientCuePointReceived(SbmSseClient.this, (Bundle) msg.obj);
                    break;

                case ACTION_STATE_CHANGED:
                    mListener.onSbmSseClientStateChanged(this, msg.arg1);
                    break;

                default:
                    Assert.failUnhandledValue(TAG, msg.what, "handleMessage");
                    break;
            }
        }
    }


    public int getOffset() {
        return mOffset;
    }


    public void setOffset(int offset) {
        if (offset < 0) {
            mOffset = offset;
        } else {
            mOffset = 0;
        }

        Log.i(TAG, "SBM offset: " + mOffset);
    }


    private void connect() {
        notifyStateChanged(STATE_CONNECTING);
        InputStream is = null;

        try {
            Log.i(TAG, "Connecting to " + mUrl);

            URL url = new URL(mUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("Cache-Control", "no-cache");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(7200000);
            conn.setRequestMethod("GET");
            conn.setDoInput(true);
            conn.connect();

            is = conn.getInputStream();
            InputStreamReader streamReader = new InputStreamReader(is, "UTF-8");
            BufferedReader bufferedReader = new BufferedReader(streamReader);
            StringBuilder sb = new StringBuilder(1024);

            notifyStateChanged(STATE_CONNECTED);

            while (!Thread.interrupted()) {
                String line = bufferedReader.readLine();

                if (TextUtils.isEmpty(line)) {
                    // Cue point end
                    String str = sb.toString();

                    // Don't decode HLS segment cue points
                    if (!str.contains("\"hls_segment_id\"")) {
                        Bundle cuePoint = decodeCuePoint(str);
                        notifyCuePointReceived(cuePoint);
                    }

                    // Reset the string buffer
                    sb.setLength(0);
                } else {
                    // Starting at index 5 to ignore the "data:" prefix.
                    if(line.length() > 5){
                        sb.append(line.substring(5));
                    }
                }
            }

            notifyStateChanged(STATE_DISCONNECTED);
        } catch (IOException e) {
            Log.e(TAG, e, "connect() exception");
            notifyStateChanged(STATE_ERROR);
        } finally {
            try {
                if (is != null) {
                    is.close();
                }
            } catch (IOException e) {
                Log.d(TAG, e, "Ignored exception");
            }
        }
    }


    private void notifyCuePointReceived(Bundle cuePoint) {
        if (cuePoint != null) {
            int cuePointPosition = cuePoint.getInt(CuePoint.POSITION_IN_STREAM);
            long messageTime = mInitUptimeMilis + cuePointPosition + mOffset;

            Message msg = SbmSseClient.this.obtainMessage();
            msg.what = SbmSseClient.ACTION_CUEPOINT_RECEIVED;
            msg.obj = cuePoint;
            SbmSseClient.this.sendMessageAtTime(msg, messageTime);
        }
    }


    private void notifyStateChanged(int state) {
        Message msg = SbmSseClient.this.obtainMessage();
        msg.what = SbmSseClient.ACTION_STATE_CHANGED;
        msg.arg1 = state;
        SbmSseClient.this.sendMessage(msg);
    }


    private static Bundle decodeCuePoint(String jsonCuePoint) {
        if (TextUtils.isEmpty(jsonCuePoint)) {
            return null;
        }

        try {
            JSONObject rootJsonObj = new JSONObject(jsonCuePoint);
            String dataEventType = rootJsonObj.getString("type");

            // Handling only "onCuePoint" data.
            if (!TextUtils.equals(dataEventType, "onCuePoint")) {
                return null;
            }

            Bundle cuePoint = new Bundle();
            String cuePointType = rootJsonObj.getString("name");
            cuePoint.putString(CuePoint.CUE_TYPE, cuePointType);
            cuePoint.putInt(CuePoint.POSITION_IN_STREAM, rootJsonObj.getInt("timestamp"));

            JSONObject paramsJsonObj = rootJsonObj.getJSONObject("parameters");
            JSONArray paramKeys = paramsJsonObj.names();
            final int paramKeysCount = paramKeys.length();

            for (int i = 0; i < paramKeysCount; i++) {
                String key = (String) paramKeys.get(i);
                String value = paramsJsonObj.getString(key);
                CuePoint.addCuePointAttribute(cuePoint, cuePointType, key, value);
            }

            return cuePoint;

        } catch (JSONException e) {
            Log.e(TAG, e, "JSON exception");
        }

        return null;
    }
}
