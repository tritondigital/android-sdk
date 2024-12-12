package com.tritondigital.player;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import junit.framework.AssertionFailedError;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)

public class CuePointHistoryAndroidTest {
    private CuePointHistory mParser;
    private Handler mHandler;
    private CountDownLatch mLatch = new CountDownLatch(1);
    private Context mContext;


    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mHandler = new Handler(mContext.getMainLooper());
        mParser = new CuePointHistory();
    }


    @After
    public void tearDown() {
        mParser = null;
    }


    @Test
    public void canGetLastCuePoint() {
        Runnable r = new Runnable() {
            public void run() {
                mParser.setMount("TRITONRADIOMUSIC");
                mParser.setMaxItems(1);
                mParser.setListener(new CuePointHistory.CuePointHistoryListener() {
                    @Override
                    public void onCuePointHistoryReceived(CuePointHistory src, List<Bundle> cuePoints) {
                        assertTrue(cuePoints!= null && cuePoints.size() > 0);
                        assertTrue(cuePoints.size() ==1);
                    }

                    @Override
                    public void onCuePointHistoryFailed(CuePointHistory src, int errorCode) {
                        throw new AssertionFailedError("Should not be a fail");
                    }
                });

                mParser.request();
            }
        };

        mHandler.post(r);
        waitFor(3);
    }


    @Test
    public void canGetLast25CuePoints() {
        Runnable r = new Runnable() {
            public void run() {
                mParser.setMount("TRITONRADIOMUSIC");
                mParser.setMaxItems(25);
                mParser.setListener(new CuePointHistory.CuePointHistoryListener() {
                    @Override
                    public void onCuePointHistoryReceived(CuePointHistory src, List<Bundle> cuePoints) {
                        assertTrue(cuePoints!= null && cuePoints.size() > 0);
                        assertTrue(cuePoints.size() ==25);
                    }

                    @Override
                    public void onCuePointHistoryFailed(CuePointHistory src, int errorCode) {
                        throw new AssertionFailedError("Should not be a fail");
                    }
                });

                mParser.request();
            }
        };

        mHandler.post(r);
        waitFor(3);
    }


    @Test
    public void cannotGetLastCuePointWithInvalidMount() {
        Runnable r = new Runnable() {
            public void run() {
                mParser.setMount("");
                mParser.setMaxItems(25);
                mParser.setListener(new CuePointHistory.CuePointHistoryListener() {
                    @Override
                    public void onCuePointHistoryReceived(CuePointHistory src, List<Bundle> cuePoints) {
                        throw new AssertionFailedError("Should not be a success");
                    }

                    @Override
                    public void onCuePointHistoryFailed(CuePointHistory src, int errorCode) {
                        assertTrue(errorCode == CuePointHistory.ERROR_INVALID_MOUNT);
                    }
                });

                mParser.request();
            }
        };

        mHandler.post(r);
        waitFor(3);
    }


    private void waitFor(long seconds) {
        try {
            mLatch.await(seconds, TimeUnit.SECONDS);
        } catch (Exception e) {
        }
    }

}
