package com.tritondigital.sdksample.player;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import androidx.mediarouter.media.MediaRouter;
import android.util.Log;

import com.google.android.gms.cast.ApplicationMetadata;
import com.google.android.gms.cast.Cast;
import com.google.android.gms.cast.CastDevice;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.MediaStatus;
import com.google.android.gms.cast.RemoteMediaPlayer;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;


import org.json.JSONObject;

import java.io.IOException;

/***This is a sample example to show how to connect to the custom cast receiver.
 * TODO: It is recommended to migrate Cast V3*/
public final class CastReceiverSender
{
    private static final String TAG = "TritonSdkSample";

    private CastDevice mCastDevice;
    private String mApplicationId;
    private boolean isStreamLoaded;

    private GoogleApiClient mApiClient;
    private Cast.Listener mCastListener;
    private ConnectionCallbacks mConnectionCallbacks;
    private ConnectionFailedListener mConnectionFailedListener;
    private boolean mApplicationStarted;
    private boolean mWaitingForReconnect;
    private String mSessionId;

    private Context mContext;
    private String mStreamUrl;
    private String mSbmUrl;

    public CastReceiverSender(Context context, String appId)
    {
        mContext = context;
        mApplicationId = appId;
    }

    /**
     * Start the receiver app
     */
    public void launchReceiver(String streamUrl, String sbmUrl) {
        mStreamUrl = streamUrl;
        mSbmUrl = sbmUrl;
        try {
            mCastListener = new Cast.Listener() {

                @Override
                public void onApplicationDisconnected(int errorCode) {
                    Log.d(TAG, "application has stopped");
                     teardown();
                }
            };
            // Connect to Google Play services
            mConnectionCallbacks = new ConnectionCallbacks();
            mConnectionFailedListener = new ConnectionFailedListener();
            Cast.CastOptions.Builder apiOptionsBuilder = Cast.CastOptions.builder(mCastDevice, mCastListener);
            mApiClient = new GoogleApiClient.Builder(mContext)
                    .addApi(Cast.API, apiOptionsBuilder.build())
                    .addConnectionCallbacks(mConnectionCallbacks)
                    .addOnConnectionFailedListener(mConnectionFailedListener)
                    .build();

            mApiClient.connect();
        } catch (Exception e) {
            Log.e(TAG, "Failed launchReceiver", e);
        }
    }

    public void onRouteSelected(MediaRouter.RouteInfo route, String streamUrl, String sbmUrl)
    {
        mCastDevice =  CastDevice.getFromBundle(route.getExtras());
        initCastClientListener();
        initRemoteMediaPlayer();

        launchReceiver(streamUrl,sbmUrl);
    }

    public void onRouteUnselected(MediaRouter.RouteInfo route)
    {
        mCastDevice = null;
        teardown();
        mCastDevice = null;
        isStreamLoaded = false;
    }



    public  void stopRemotePlayer()
    {
        if(mRemoteMediaPlayer != null)
        {
            mRemoteMediaPlayer.stop(mApiClient);
        }
    }



    /**
     * Google Play services callbacks
     */
    private class ConnectionCallbacks implements
            GoogleApiClient.ConnectionCallbacks {

        @Override
        public void onConnected(Bundle connectionHint) {
            Log.d(TAG, "onConnected");

            if (mApiClient == null) {
                // We got disconnected while this runnable was pending execution.
                return;
            }

            try {
                if (mWaitingForReconnect) {
                    mWaitingForReconnect = false;

                    // Check if the receiver app is still running
                    if ((connectionHint != null)
                            && connectionHint.getBoolean(Cast.EXTRA_APP_NO_LONGER_RUNNING)) {
                        Log.d(TAG, "App  is no longer running");
                        teardown();
                    } else {
                        // Re-create the custom message channel
                        reconnectChannels(connectionHint);
                    }
                } else {
                    // Launch the receiver app
                    Cast.CastApi.launchApplication(mApiClient, mApplicationId, true)
                            .setResultCallback(
                                    new ResultCallback<Cast.ApplicationConnectionResult>() {
                                        @Override
                                        public void onResult(Cast.ApplicationConnectionResult result) {
                                            Status status = result.getStatus();
                                            Log.d(TAG,
                                                    "ApplicationConnectionResultCallback.onResult:"
                                                            + status.getStatusCode());
                                            if (status.isSuccess()) {
                                                ApplicationMetadata applicationMetadata = result
                                                        .getApplicationMetadata();
                                                mSessionId = result.getSessionId();
                                                String applicationStatus = result
                                                        .getApplicationStatus();
                                                boolean wasLaunched = result.getWasLaunched();
                                                Log.d(TAG, "application name: "
                                                        + applicationMetadata.getName()
                                                        + ", status: " + applicationStatus
                                                        + ", sessionId: " + mSessionId
                                                        + ", wasLaunched: " + wasLaunched);
                                                mApplicationStarted = true;

                                                if(!isStreamLoaded)
                                                {
                                                    startPlaying();
                                                }
                                                else { reconnectChannels( null ); }

                                            } else {
                                                Log.e(TAG, "application could not launch");
                                                teardown();
                                            }
                                        }
                                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to launch application", e);
            }
        }

        @Override
        public void onConnectionSuspended(int cause) {
            Log.d(TAG, "onConnectionSuspended");
            mWaitingForReconnect = true;
        }
    }

    /**
     * Google Play services callbacks
     */
    private class ConnectionFailedListener implements
            GoogleApiClient.OnConnectionFailedListener {

        @Override
        public void onConnectionFailed(ConnectionResult result) {
            Log.e(TAG, "onConnectionFailed ");

            teardown();
        }
    }


    private void reconnectChannels( Bundle hint ) {
        if( ( hint != null ) && hint.getBoolean( Cast.EXTRA_APP_NO_LONGER_RUNNING ) ) {
            Log.e( TAG, "App is no longer running" );
            teardown();
        } else {
            try {
                Cast.CastApi.setMessageReceivedCallbacks( mApiClient, mRemoteMediaPlayer.getNamespace(), mRemoteMediaPlayer );
                if(mRemoteMediaPlayer != null)
                {
                    mRemoteMediaPlayer.stop(mApiClient);
                }
            } catch( IOException e ) {
                Log.e( TAG, "Exception while creating media channel ", e );
            } catch( NullPointerException e ) {
                Log.e( TAG, "Something wasn't reinitialized for reconnectChannels" );
            }
        }
    }


    RemoteMediaPlayer mRemoteMediaPlayer;
    boolean mIsPlaying;
    private void initRemoteMediaPlayer() {
        mRemoteMediaPlayer = new RemoteMediaPlayer();
        mRemoteMediaPlayer.setOnStatusUpdatedListener( new RemoteMediaPlayer.OnStatusUpdatedListener() {
            @Override
            public void onStatusUpdated() {
                MediaStatus mediaStatus = mRemoteMediaPlayer.getMediaStatus();
                int state = mediaStatus.getPlayerState();
                mIsPlaying = state == MediaStatus.PLAYER_STATE_PLAYING;
                Log.d( TAG, "setOnStatusUpdatedListener onStatusUpdated ---state: " + stateToString(state) );
                if(mIsPlaying && state!=MediaStatus.PLAYER_STATE_PLAYING)
                {
                    mRemoteMediaPlayer.play(mApiClient);
                }
            }

            private String stateToString(int state)
            {
                switch (state)
                {
                    case MediaStatus.PLAYER_STATE_BUFFERING: return "Buffering";
                    case MediaStatus.PLAYER_STATE_IDLE:      return "Idle";
                    case MediaStatus.PLAYER_STATE_PAUSED:    return "Paused";
                    case MediaStatus.PLAYER_STATE_PLAYING:   return "Playing";
                }
                return "Unknown";
            }
        });

        mRemoteMediaPlayer.setOnMetadataUpdatedListener( new RemoteMediaPlayer.OnMetadataUpdatedListener() {
            @Override
            public void onMetadataUpdated() {
                Log.d( TAG, "OnMetadataUpdatedListener ---"  );
            }
        });


    }

    private void startPlaying() {
        MediaMetadata mediaMetadata = new MediaMetadata( MediaMetadata.MEDIA_TYPE_GENERIC);
        mediaMetadata.putString( MediaMetadata.KEY_TITLE, "Triton Player Sample" );
        mediaMetadata.putString( MediaMetadata.KEY_ARTIST, "TD Bravo Team" );
        mediaMetadata.putString( MediaMetadata.KEY_ALBUM_ARTIST, "Triton Digital Inc." );
        mediaMetadata.putString( MediaMetadata.KEY_STUDIO, "Bravo Team" );
        mediaMetadata.addImage(new com.google.android.gms.common.images.WebImage(Uri.parse("http://mobileapps.streamtheworld.com/android/tritondigital/tritonradio/icon_512.png")));

        JSONObject customData = new JSONObject();
        try
        {
            customData.put(com.tritondigital.player.PlayerConsts.SBM_URL, mSbmUrl) ;
        }
        catch (Exception e){}



        MediaInfo mediaInfo = new MediaInfo.Builder(mStreamUrl )
                .setContentType( "audio/aac" )
                .setStreamType( MediaInfo.STREAM_TYPE_LIVE )
                .setCustomData(customData)
                .setMetadata( mediaMetadata )
                .setStreamDuration(MediaInfo.UNKNOWN_DURATION)
                .build();
        try {
            mRemoteMediaPlayer.load( mApiClient, mediaInfo, true )
                    .setResultCallback( new ResultCallback<RemoteMediaPlayer.MediaChannelResult>() {
                        @Override
                        public void onResult( RemoteMediaPlayer.MediaChannelResult mediaChannelResult ) {
                            if( mediaChannelResult.getStatus().isSuccess() ) {
                                isStreamLoaded = true;
                            }
                        }
                    } );

            mRemoteMediaPlayer.play(mApiClient);

        } catch( Exception e ) {
        }
    }


    Cast.Listener mCastClientListener;
    private void initCastClientListener() {
        mCastClientListener = new Cast.Listener() {
            @Override
            public void onApplicationStatusChanged() {
            }

            @Override
            public void onVolumeChanged() {
            }

            @Override
            public void onApplicationDisconnected( int statusCode ) {
                teardown();
            }
        };
    }


    private void teardown() {
        if( mApiClient != null ) {
            if( mApplicationStarted ) {
                try {
                    Cast.CastApi.stopApplication( mApiClient );
                    if( mRemoteMediaPlayer != null ) {
                        Cast.CastApi.removeMessageReceivedCallbacks( mApiClient, mRemoteMediaPlayer.getNamespace() );
                        mRemoteMediaPlayer = null;
                    }
                } catch( IOException e ) {
                    Log.e( TAG, "Exception while removing application " + e );
                }
                mApplicationStarted = false;
            }
            if( mApiClient.isConnected() )
                mApiClient.disconnect();
            mApiClient = null;
        }
        mCastDevice = null;
        isStreamLoaded = false;
    }
}
