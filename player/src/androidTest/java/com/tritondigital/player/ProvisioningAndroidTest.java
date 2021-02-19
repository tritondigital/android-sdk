package com.tritondigital.player;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import junit.framework.AssertionFailedError;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class ProvisioningAndroidTest
{
    private Provisioning mParser;
    private Handler mHandler;
    private CountDownLatch mLatch = new CountDownLatch(1);
    private Context mContext;


    @Before
    public void setUp()
    {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mHandler = new Handler(mContext.getMainLooper());
        mParser = new Provisioning();
    }


    @After
    public void tearDown()
    {
        mParser = null;
    }



    @Test
    public void canDoProvisioningWithValidMount()
    {
        assertNotNull(mParser);
        mParser.setMount("MOBILEFM_AACV2", "FLV");
        mParser.setListener(new Provisioning.Listener()
        {
            @Override
            public void onProvisioningSuccess(Provisioning prov, Bundle result)
            {
                assertNotNull(result);
                ArrayList<Bundle> servers = result.getParcelableArrayList(Provisioning.Result.SERVERS);
                assertTrue(servers!= null && servers.size() > 0);
                Bundle server = servers.get(0);
                ArrayList<String> ports = server.getStringArrayList(Provisioning.Result.Server.PORTS);
                assertTrue(ports!= null && ports.size() > 0);
            }

            @Override
            public void onProvisioningFailed(Provisioning src, int errorCode)
            {
                throw new AssertionFailedError("Should not be a fail");
            }
        });

        requestProvisioning();
    }



    @Test
    public void cannotDoProvisioningWithInvalidMount()
    {
        mParser.setMount("ANDROID_STUDIO", "FLV");
        mParser.setListener(new Provisioning.Listener()
        {
            @Override
            public void onProvisioningSuccess(Provisioning prov, Bundle result)
            {
                throw new AssertionFailedError("Should not be a success");
            }

            @Override
            public void onProvisioningFailed(Provisioning src, int errorCode)
            {
                assertTrue(errorCode == Provisioning.ERROR_NOT_FOUND);
            }
        });

        requestProvisioning();

    }

    @Test
    public void cannotDoProvisioningWithEmptyMount()
    {
        mParser.setMount("", "FLV");
        mParser.setListener(new Provisioning.Listener()
        {
            @Override
            public void onProvisioningSuccess(Provisioning prov, Bundle result)
            {
                throw new AssertionFailedError("Should not be a success");
            }

            @Override
            public void onProvisioningFailed(Provisioning src, int errorCode)
            {
                assertTrue(errorCode == 0);//
            }
        });

        requestProvisioning();

    }


    private void requestProvisioning()
    {
        Runnable r = new Runnable()
        {
            public void run()
            {
                mParser.request();
            }
        };

        mHandler.post(r);
        waitFor(3);
    }

    private void waitFor(long seconds)
    {
        try
        {
            mLatch.await(seconds, TimeUnit.SECONDS);
        }
        catch(Exception e){}
    }
}
