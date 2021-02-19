package com.tritondigital.util;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;


@RunWith(AndroidJUnit4.class)
public class DisplayUtilTest {

    final static float SCALE_LDPI   = 0.75f;
    final static float SCALE_MDPI   = 1.0f;
    final static float SCALE_HDPI   = 1.5f;
    final static float SCALE_XHDPI  = 2.0f;
    final static float SCALE_XXHDPI = 3.0f;


    private void testDensityPixelsToPixels(float scale, float expected) {
        float actual = DisplayUtil.densityPixelsToPixels(scale, 160);
        assertEquals("Desity pixels to pixels conversion failed", expected, actual, 0.01);
    }

    @Test
    public void densityPixelsToLdpiPixels() {
        testDensityPixelsToPixels(SCALE_LDPI, 120);
    }

    @Test
    public void densityPixelsToMdpiPixels() {
        testDensityPixelsToPixels(SCALE_MDPI, 160);
    }

    @Test
    public void densityPixelsToHdpiPixels() {
        testDensityPixelsToPixels(SCALE_HDPI, 240);
    }

    @Test
    public void densityPixelsToXhdpiPixels() {
        testDensityPixelsToPixels(SCALE_XHDPI, 320);
    }

    @Test
    public void densityPixelsToXxhdpiPixels() {
        testDensityPixelsToPixels(SCALE_XXHDPI, 480);
    }
}
