package com.tritondigital.util;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;

import com.google.android.gms.ads.identifier.AdvertisingIdClient;
import com.google.android.gms.ads.identifier.AdvertisingIdClient.Info;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.GooglePlayServicesUtil;

import java.lang.ref.WeakReference;
import java.util.UUID;


/**
 * Utility methods to help user tracking.
 */
public final class TrackingUtil {
    private static final String TAG                = Log.makeTag("TrackingUtil");
    private final static String PREFS_GOOGLE_ID    = "GoogleId";
    private final static String PREFS_GENERATED_ID = "GeneratedId";

    private TrackingUtil() {}


    /**
     * Appends the location parameter to the provided uri builder
     */
    public static void appendLocationParams(Context context, Uri.Builder uriBuilder) {
        if ((context == null) || (uriBuilder == null)) {
            return;
        }

        try {
            LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            Location location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);

            if (location != null) {
                double latitude = location.getLatitude();
                uriBuilder.appendQueryParameter("lat", String.valueOf(latitude));

                double longitude = location.getLongitude();
                uriBuilder.appendQueryParameter("long", String.valueOf(longitude));
            }
        } catch (SecurityException e) {
            Log.w(TAG, "Missing ACCESS_COARSE_LOCATION permission.");
        }
    }


    /**
     * Returns the Triton tracking id
     */
    public static String getTrackingId(Context context) {
        int ret = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context.getApplicationContext());
        if ( ret == ConnectionResult.SUCCESS) {
            String id = getPrefetchedGoogleId(context);
            if (id != null) {
                return "gaid:" + id;
            }
        }

        return "app:" + getGeneratedId(context);
    }


    protected static String getGeneratedId(Context context) {
        // Get the saved UUID.
        SharedPreferences sharedPrefs = getSharedPreferences(context);
        String uuid = sharedPrefs.getString(PREFS_GENERATED_ID, null);

        if (uuid == null) {
            // Generate a random UUID.
            uuid = UUID.randomUUID().toString();

            // Save the UUID for the future.
            SharedPreferences.Editor prefEditor = sharedPrefs.edit();
            prefEditor.putString(PREFS_GENERATED_ID, uuid);
            prefEditor.apply();
        }

        return uuid;
    }


    /**
     * Returns the prefetched advertising ID and prefetch it for the next time
     */
    private static String getPrefetchedGoogleId(Context context) {
        String prefetchId = getSharedPreferences(context).getString(PREFS_GOOGLE_ID, null);
        prefetchTrackingId(context);
        return prefetchId;
    }


    /**
     * Prefetch the tracking id in order to increase the chances of having a valid value
     * when "getTrackingId()" gets called
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static void prefetchTrackingId(Context context) {
        PrefetchTrackingIdTask task = new PrefetchTrackingIdTask(context);

        if (android.os.Build.VERSION.SDK_INT > Build.VERSION_CODES.HONEYCOMB) {
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        } else {
            task.execute();
        }
    }


    private static SharedPreferences getSharedPreferences(Context context) {
        return context.getSharedPreferences(TAG, Context.MODE_PRIVATE);
    }


    private static class PrefetchTrackingIdTask extends AsyncTask<Void, Void, Void> {
        private final WeakReference<Context> mContextRef;


        public PrefetchTrackingIdTask(Context context) {
            mContextRef = new WeakReference<>(context);
        }


        @Override
        protected Void doInBackground(Void... params) {
            Debug.renameThread(TAG);
            String id = null;

            Context context = getContext();
            if (context == null) {
                return null;
            }

            try {
                Info adInfo = AdvertisingIdClient.getAdvertisingIdInfo(context);

                if (adInfo != null) {
                    if (adInfo.isLimitAdTrackingEnabled()) {
                        Log.i(TAG, "Tracking opt-out enabled.");
                    } else {
                        id = adInfo.getId();
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "AdvertisingIdClient exception: " + e);
            }


            // Save the ID.
            context = getContext();
            if (context == null) {
                return null;
            }

            SharedPreferences.Editor prefEditor = getSharedPreferences(context).edit();
            prefEditor.putString(PREFS_GOOGLE_ID, id);
            prefEditor.apply();

            return null;
        }


        private Context getContext() {
            return mContextRef.get();
        }
    }
}
