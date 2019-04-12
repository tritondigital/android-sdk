package com.tritondigital.util;

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;


/**
 * Contains utility methods to help use the location manager in an app.
 */
public final class LocationUtil {

    private LocationUtil() {}

    private static final String TAG = Log.makeTag("LocationUtil");
    private static final String ACCESS_COARSE_LOCATION_MSG = "Missing ACCESS_COARSE_LOCATION permission.";


    /**
     * Prefetch the network provider location in order to increase the chances of having
     * a valid location when "getLastKnownLocation()" will get called.
     */
    public static void prefetchNetworkLocation(Context context) {
        if (context == null) {
            return;
        }

        final LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if ((locationManager != null) && locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            try {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, new LocationListener() {
                    @Override
                    public void onStatusChanged(String provider, int status, Bundle extras) {}

                    @Override
                    public void onProviderEnabled(String provider) {}

                    @Override
                    public void onProviderDisabled(String provider) {
                        locationManager.removeUpdates(this);
                    }

                    @Override
                    public void onLocationChanged(Location location) {
                        locationManager.removeUpdates(this);
                    }
                });
            } catch (SecurityException e) {
                Log.w(TAG, ACCESS_COARSE_LOCATION_MSG);
            } catch (Exception e) {
                Log.w(TAG, "Prefetch network provider location exception: " + e);
            }
        }
    }


    /**
     * Returns the last known network provider location.
     */
    public static Location getLastKnownNetworkLocation(Context context) {
        if (context == null) {
            return null;
        }

        try {
            LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            return locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        } catch (SecurityException e) {
            Log.w(TAG, ACCESS_COARSE_LOCATION_MSG);
        }

        return null;
    }


    /**
     * Tells if any location provider is enabled. Ignores security exceptions.
     */
    public static boolean isAnyLocationProviderEnabled(Context context) {
        if (context == null) {
            return false;
        }

        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);

        // GPS
        boolean gpsProviderEnabled = false;
        try {
            gpsProviderEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        } catch (SecurityException e) {
            Log.d(TAG, "GPS permission exception: " + e);
        }

        // Network
        boolean networkProviderEnabled = false;
        try {
            networkProviderEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch (SecurityException e) {
            Log.d(TAG, "Network location permission exception: " + e);
        }

        return gpsProviderEnabled || networkProviderEnabled;
    }
}
