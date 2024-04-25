package com.tritondigital.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;


/**
 * Network utility methods.
 */
public final class NetworkUtil {

    private NetworkUtil(){}

    public static boolean isNetworkConnected(Context context) {
        if (context == null) return false;

        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (connectivityManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.getActiveNetwork());
                if (capabilities != null) {
                   return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
                } else {
                    return false;
                }
            } else {
                NetworkInfo info = connectivityManager.getActiveNetworkInfo();
                return info != null && info.isConnected();
            }
        }
        return false;
    }
}
