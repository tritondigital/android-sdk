package com.tritondigital.sdksample.player;


import android.media.AudioManager;
import android.os.Bundle;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.MediaRouteActionProvider;
import android.support.v7.media.MediaControlIntent;
import android.support.v7.media.MediaRouteSelector;
import android.support.v7.media.MediaRouter;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.google.android.gms.cast.CastMediaControlIntent;


import com.tritondigital.player.MediaPlayer;
import com.tritondigital.player.TritonPlayer;
import com.tritondigital.sdksample.R;
import com.tritondigital.sdksample.Util;
import com.tritondigital.sdksample.ads.BannersWrapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * Common code for example of Triton Player uses.
 *
 * In a real application, you should use TritonPlayer from an Android service.
 */
public abstract class TritonPlayerActivity extends AppCompatActivity implements
        MediaPlayer.OnCuePointReceivedListener, MediaPlayer.OnStateChangedListener,
        MediaPlayer.OnInfoListener, View.OnClickListener, MediaPlayer.OnMetaDataReceivedListener {

    protected static final String IMAGE_URI = "http://mobileapps.streamtheworld.com/android/tritondigital_tritonradio/icon_512.png";

    private static final String TAG = "TritonSdkSample";

    // UI
    private TextView       mPlayerStateView;
    private TextView       mPlayerInfoView;
    protected TextView       mPlayerLogView;
    private TextView       mPlayerTransportView;
    private ListView       mListView;
    private BannersWrapper mBannersWrapper;

    // Google Cast
    private MediaRouter        mMediaRouter;
    private MediaRouteSelector mMediaRouteSelector;

    private List<String[]> mListItems = new ArrayList<>();
    protected TritonPlayer mTritonPlayer;
    protected StringBuilder mDateFormatBuffer = new StringBuilder();

    protected abstract int getLayout();
    protected abstract Bundle createPlayerSettings();
    protected abstract void setInputEnabled(boolean enabled);

    private CastReceiverSender mCastReceiverSender;
    private boolean  hastCustomCastReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        // Init UI
        setContentView(getLayout());

        mBannersWrapper = new BannersWrapper(findViewById(android.R.id.content));

        findViewById(R.id.button_play).setOnClickListener(this);
        findViewById(R.id.button_stop).setOnClickListener(this);
        findViewById(R.id.button_reset).setOnClickListener(this);

        mPlayerInfoView      = (TextView) findViewById(R.id.textView_playerInfo);
        mPlayerStateView     = (TextView) findViewById(R.id.textView_playerState);
        mPlayerTransportView = (TextView) findViewById(R.id.textView_playerTransport);
        mListView            = (ListView) findViewById(android.R.id.list);
        mListView.setAdapter(new SimpleListItem2Adapter(this, mListItems));
        mPlayerLogView       = (TextView) findViewById(R.id.textView_playerLogs);

        String castApplicationID = getCastApplicationID();

        // Init Google Cast
        mMediaRouter = MediaRouter.getInstance(getApplicationContext());

        hastCustomCastReceiver = !TextUtils.isEmpty(castApplicationID);
        if(hastCustomCastReceiver)
        {
            mCastReceiverSender = new CastReceiverSender(this,castApplicationID);
            mMediaRouteSelector = new MediaRouteSelector.Builder()
                    .addControlCategory(CastMediaControlIntent.categoryForCast( castApplicationID ) )
                    .build();
        }
        else
        {
            mMediaRouteSelector = new MediaRouteSelector.Builder()
                    .addControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)
                    .build();
        }
    }


    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        if (savedInstanceState == null) {
            reset();
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        super.onCreateOptionsMenu(menu);

        getMenuInflater().inflate(R.menu.media_router, menu);
        MenuItem mediaRouteMenuItem = menu.findItem(R.id.menuItem_mediaRoute);
        MediaRouteActionProvider mediaRouteActionProvider =
                (MediaRouteActionProvider) MenuItemCompat.getActionProvider(mediaRouteMenuItem);
        mediaRouteActionProvider.setRouteSelector(mMediaRouteSelector);
        return true;
    }


    @Override
    protected void onResume() {
        super.onResume();
        mMediaRouter.addCallback(mMediaRouteSelector, mMediaRouterCallback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY);
        mBannersWrapper.onResume();
    }


    @Override
    protected void onPause() {
        mMediaRouter.removeCallback(mMediaRouterCallback);
        mBannersWrapper.onPause();
        super.onPause();
    }


    @Override
    protected void onDestroy() {
        releasePlayer();
        mBannersWrapper.release();
        super.onDestroy();
    }


    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.button_play) {
            startPlayer();
        } else if (id == R.id.button_stop) {
            stopPlayer();
        } else if (id == R.id.button_reset) {
            reset();
        }
    }


    @Override
    public void onCuePointReceived(MediaPlayer player, Bundle cuePoint) {
        if (mTritonPlayer == player) {
            // Banners
            mBannersWrapper.loadCuePoint(cuePoint);

            // Refresh ListView
            mListItems.clear();

            if (cuePoint != null) {
                List<String> keys = new ArrayList<>(cuePoint.keySet());
                Collections.sort(keys);

                for (String key : keys) {
                    Object value = cuePoint.get(key);
                    if (value != null) {
                        String valueStr = value.toString();
                        if (!TextUtils.isEmpty(valueStr)) {
                            mListItems.add(new String[]{key, valueStr});
                        }
                    }
                }
            }

            ((ArrayAdapter)mListView.getAdapter()).notifyDataSetChanged();
        }
    }


    @Override
    public void onInfo(MediaPlayer player, int info, int extra) {
        if (mTritonPlayer == player) {
            String text = TritonPlayer.debugInfoToStr(info);

            switch (info) {
                case TritonPlayer.INFO_ALTERNATE_MOUNT:
                    text += ": " + mTritonPlayer.getAlternateMount();
                    break;

                case TritonPlayer.INFO_DURATION_CHANGED:
                    if (extra == TritonPlayer.DURATION_LIVE_STREAM) {
                        text += ": Live stream";
                    } else if (extra == TritonPlayer.DURATION_UNKNOWN) {
                        text += ": Unknown";
                    } else {
                        text += ": " + DateUtils.formatElapsedTime(mDateFormatBuffer, (extra / 1000));
                    }
                    break;

                case TritonPlayer.INFO_SEEK_COMPLETED:
                    text += ": " + DateUtils.formatElapsedTime(mDateFormatBuffer, (extra / 1000));
                    break;

                case TritonPlayer.INFO_SEEKABLE_CHANGED:
                    text += ": " + (extra == 1);
                    break;
            }

            mPlayerInfoView.setText(text);
        }
    }


    @Override
    public void onStateChanged(MediaPlayer player, int state) {
        if (mTritonPlayer == player) {
            String stateStr = MediaPlayer.debugStateToStr(state);

            if (state == MediaPlayer.STATE_ERROR) {
                stateStr += ' ' + MediaPlayer.debugErrorToStr(mTritonPlayer.getErrorCode());
            }

            mPlayerStateView.setText(stateStr);

            setInputEnabled((state == TritonPlayer.STATE_ERROR)
                    || (state == TritonPlayer.STATE_STOPPED)
                    || (state == TritonPlayer.STATE_RELEASED));

            String transport = null;
            if(state == TritonPlayer.STATE_PLAYING)
            {
                transport = player.getSettings().getString(TritonPlayer.SETTINGS_TRANSPORT);
            }
            mPlayerTransportView.setText(transport);
        }
    }


    protected void reset() {
        releasePlayer();
    }


    protected void startPlayer() {
        // Recreate player
        Bundle playerSettings = (mTritonPlayer == null) ? null : mTritonPlayer.getSettings();
        Bundle inputSettings = createPlayerSettings();

        if(mPlayerLogView != null) {
            mPlayerLogView.setText("");
        }

        if (!Util.bundleEquals(inputSettings, playerSettings)) {
            releasePlayer();
            createPlayer(inputSettings);
        }

        // Start the playback
        mTritonPlayer.play();
    }


    protected void stopPlayer() {
        if (mTritonPlayer != null) {
            mTritonPlayer.stop();
        }

        if(mCastReceiverSender != null)
        {
            mCastReceiverSender.stopRemotePlayer();
        }
    }


    private void createPlayer(Bundle settings)
    {
        mTritonPlayer = new TritonPlayer(this, settings);
        //mTritonPlayer.setMediaRoute(mMediaRouter.getSelectedRoute());
        mTritonPlayer.setOnCuePointReceivedListener(this);
        mTritonPlayer.setOnMetaDataReceivedListener(this);
        mTritonPlayer.setOnInfoListener(this);
        mTritonPlayer.setOnStateChangedListener(this);
    }


    protected void releasePlayer() {
        if (mTritonPlayer != null) {
            mTritonPlayer.release();
            mTritonPlayer = null;
        }

        mPlayerInfoView.setText(null);
        mPlayerStateView.setText(null);
    }


    private final MediaRouter.Callback mMediaRouterCallback = new MediaRouter.Callback() {
        @Override
        public void onRouteSelected(MediaRouter router, MediaRouter.RouteInfo route) {
            Log.i(TAG, "Media route selected: " + route);

            if(mCastReceiverSender != null && (mTritonPlayer != null))
            {
                mCastReceiverSender.onRouteSelected(route, mTritonPlayer.getCastStreamingUrl(), mTritonPlayer.getSideBandMetadataUrl());

                if (mTritonPlayer != null) {
                    mTritonPlayer.stop();
                }
            }
            else  setMediaRoute(route);
        }

        @Override
        public void onRouteUnselected(MediaRouter router, MediaRouter.RouteInfo route) {
            Log.i(TAG, "Media route unselected: " + route);
            if(mCastReceiverSender != null)
            {
                mCastReceiverSender.onRouteUnselected(route);
            }
            else setMediaRoute(null);

        }


        private void setMediaRoute(MediaRouter.RouteInfo route) {
            if (mTritonPlayer != null) {
                mTritonPlayer.setMediaRoute(route);
            }
        }
    };


    @Override
    public void onMetaDataReceived(MediaPlayer player, Bundle metadata) {
        if(mPlayerLogView != null) {
            StringBuilder sb = new StringBuilder(mPlayerLogView.getText());
            for(String key: metadata.keySet()){
                Object o = metadata.get(key);
                if(o != null) {
                    sb.append(String.format("%s = %s\n", key,o.toString()));
                }
            }
            mPlayerLogView.setText(sb.toString());
        }
    }


    private String getCastApplicationID()
    {
        return this.getString(R.string.cast_application_id);
    }
}
