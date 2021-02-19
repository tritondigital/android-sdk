package com.tritondigital.ads;

import android.content.Context;
import android.graphics.Point;
import android.os.Bundle;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;


import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;


import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;



@RunWith(AndroidJUnit4.class)
public class BannerViewTest
{
    Context mContext;
    @Before
    public void setUp()
    {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
    }

    @Test
   public void testFindBestBannerSize()
   {
       BannerView bannerView = new BannerView(mContext);
       ArrayList<Bundle> banners = new ArrayList<>();

       Bundle ad = new Bundle();
       ad.putInt(Ad.WIDTH, 480); ad.putInt(Ad.HEIGHT, 480);
       banners.add(ad);

       ad = new Bundle();
       ad.putInt(Ad.WIDTH, 120); ad.putInt(Ad.HEIGHT, 60);
       banners.add(ad);

       ad = new Bundle();
       ad.putInt(Ad.WIDTH, 300); ad.putInt(Ad.HEIGHT, 600);
       banners.add(ad);

       ad = new Bundle();
       ad.putInt(Ad.WIDTH, 728); ad.putInt(Ad.HEIGHT, 90);
       banners.add(ad);

       ad = new Bundle();
       ad.putInt(Ad.WIDTH, 970); ad.putInt(Ad.HEIGHT, 100);
       banners.add(ad);

       ad = new Bundle();
       ad.putInt(Ad.WIDTH, 300); ad.putInt(Ad.HEIGHT, 250);
       banners.add(ad);

       ad = new Bundle();
       ad.putInt(Ad.WIDTH, 88); ad.putInt(Ad.HEIGHT, 31);
       banners.add(ad);

       Bundle ads = new Bundle();
       ads.putParcelableArrayList(Ad.BANNERS, banners);



       // Container size in pixel, expected banner size in pixel
       HashMap<Point, Point> containers = new HashMap<>();
       containers.put(new Point(301,601),new Point(300,600))  ;
       containers.put(new Point(500,480),new Point(480,480))  ;
       containers.put(new Point(360,480),new Point(300,250))  ;

       for (Map.Entry<Point,Point> entry: containers.entrySet()){
           Point container  = entry.getKey();
           Point expected   = entry.getValue();
           Point bannerSize = bannerView.getBestBannerSize(ads, container.x, container.y);


           //Test
           assertTrue(bannerSize != null);
           assertTrue(bannerSize.x == expected.x);
           assertTrue(bannerSize.y == expected.y);
       }
   }
}
