package com.tritondigital.util;

import android.content.Context;


/**
 * Utility methods
 */
public final class DisplayUtil {
    /**
     * Get the phones density pixel to pixel scale.
     */
    public static float getDeviceDensityPixelScale(Context context) {
        return context.getResources().getDisplayMetrics().density;
    }


    /**
     * Converts density pixels to pixels.
     */
    public static int densityPixelsToPixels(float scale, int densityPixels) {
        return (int) (densityPixels * scale + 0.5f);
    }


    private DisplayUtil() {}
}
