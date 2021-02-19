package com.tritondigital.ads;

import android.os.Bundle;

import org.junit.Test;


import java.util.ArrayList;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;




public class InterstitialActivityTest {

    @Test
    public void isHttpOrHttpsUrl_validHttp() {
        assertTrue(InterstitialActivity.isHttpOrHttpsUrl("http://foo.bar"));
    }

    @Test
    public void isHttpOrHttpsUrl_validHttps() {
        assertTrue(InterstitialActivity.isHttpOrHttpsUrl("https://foo.bar"));
    }

    @Test
    public void isHttpOrHttpsUrl_empty() {
        assertFalse(InterstitialActivity.isHttpOrHttpsUrl(""));
    }

    @Test
    public void isHttpOrHttpsUrl_null() {
        assertFalse(InterstitialActivity.isHttpOrHttpsUrl(null));
    }

    @Test
    public void isHttpOrHttpsUrl_invalidNoScheme() {
        assertFalse(InterstitialActivity.isHttpOrHttpsUrl("foo.bar"));
    }

    @Test
    public void isHttpOrHttpsUrl_invalidStartingWithHttp() {
        assertFalse(InterstitialActivity.isHttpOrHttpsUrl("http12345"));
    }

    @Test
    public void isHttpOrHttpsUrl_invalidStartingWithHttps() {
        assertFalse(InterstitialActivity.isHttpOrHttpsUrl("https"));
    }

    @Test
    public void hasBannerSize_nullBanners() {
        assertFalse(InterstitialActivity.hasBannerSize(null, 100, 200));
    }

    @Test
    public void hasBannerSize_emptyBanners() {
        assertFalse(InterstitialActivity.hasBannerSize(new ArrayList<Bundle>(), 100, 200));
    }

    Bundle createValidBanner() {
        Bundle banner = new Bundle();
        banner.putInt(Ad.WIDTH,  100);
        banner.putInt(Ad.HEIGHT, 200);
        return banner;
    }

    @Test
    public void hasBannerSize_nullEntry() {

        ArrayList<Bundle> banners = new ArrayList<>();
        banners.add(createValidBanner());
        banners.add(null);
        assertTrue(InterstitialActivity.hasBannerSize(banners, 100, 200));
    }
}
