package com.tritondigital.player;

import android.content.Context;
import android.os.Bundle;

import junit.framework.AssertionFailedError;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;

import org.mockito.junit.MockitoJUnitRunner;


import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@RunWith(MockitoJUnitRunner.class)
public class TritonPlayerTest
{

    @Mock
    Context mMockContext;

    TritonPlayer mTritonPlayer;
    Bundle mPlayerSettings;
    static float volume = 0.5f;

    @Before
    public void setUp()
    {

        mPlayerSettings = new Bundle();
        Bundle b = spy(mPlayerSettings);
        doReturn("MOBILEFM_AACV2").when(b).getString(TritonPlayer.SETTINGS_STATION_MOUNT);
        doReturn("Triton Digital").when(b).getString(TritonPlayer.SETTINGS_STATION_BROADCASTER);
        doReturn("Mobile FM").when(b).getString(TritonPlayer.SETTINGS_STATION_NAME);


        mTritonPlayer = mock(TritonPlayer.class);
        mTritonPlayer.setVolume(volume);

        when(mTritonPlayer.getSettings()).thenReturn(mPlayerSettings);

        when(mTritonPlayer.getVolume()).thenReturn(volume);

        assertNotNull(mTritonPlayer.getSettings());
        assertNotNull(b.getString(TritonPlayer.SETTINGS_STATION_MOUNT));
        assertNotNull(b.getString(TritonPlayer.SETTINGS_STATION_BROADCASTER));
        assertNotNull(b.getString(TritonPlayer.SETTINGS_STATION_NAME));

        verify(mTritonPlayer).setVolume(ArgumentMatchers.eq(volume));
    }


    @Test
    public void canPlay()
    {
        try
        {
            mTritonPlayer.play();
        }
        catch(Exception e)
        {
            throw new AssertionFailedError("Should not throw an Exception");
        }
    }

    @Test
    public void canPause()
    {
        try
        {
            mTritonPlayer.pause();
        }
        catch(Exception e)
        {
           throw new AssertionFailedError("Should not throw an Exception");
        }
    }

    @Test
    public void canStop()
    {
        try
        {
            mTritonPlayer.stop();
        }
        catch(Exception e)
        {
            throw new AssertionFailedError("Should not throw an Exception");
        }
    }


    @Test
    public void verifyParams()
    {
      assertTrue(mTritonPlayer.getVolume() > 0);
      assertTrue(mTritonPlayer.getDuration() == 0);
      assertTrue(mTritonPlayer.getPosition() == 0);
    }


}
