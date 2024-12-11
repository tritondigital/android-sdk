package com.tritondigital.player;

import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.content.res.Resources;
import junit.framework.AssertionFailedError;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.tritondigital.player.exoplayer.extractor.flv.TdScriptTagPayloadLoader;
import com.tritondigital.player.exoplayer.extractor.flv.TdMetaDataListener;
import androidx.media3.common.util.ParsableByteArray;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Map;
import java.io.File;
import java.io.FileInputStream;


@RunWith(MockitoJUnitRunner.class)
public class TdExoPlayerTest
{
    @Mock
    Context mMockContext;

    TdExoPlayer mTdExoPlayer;
    Bundle mPlayerSettings;

    @Before
    public void setUp()
    {
        mPlayerSettings = new Bundle();
        mTdExoPlayer = mock(TdExoPlayer.class);

        when(mTdExoPlayer.getSettings()).thenReturn(mPlayerSettings);
        assertNotNull(mTdExoPlayer.getSettings());
    }

    @Test
    public void canConstruct()
    {
        assertNotNull(mTdExoPlayer);
    }

    @Test
    public void testPlay()
    {
        try
        {
            mTdExoPlayer.play();
        }
        catch(Exception e)
        {
            throw new AssertionFailedError("Should not throw an Exception");
        }

    }

    @Test
    public void testPause()
    {
        try
        {
            mTdExoPlayer.pause();
        }
        catch(Exception e)
        {
            throw new AssertionFailedError("Should not throw an Exception");
        }
    }

    @Test
    public void testStop()
    {
        try
        {
            mTdExoPlayer.stop();
        }
        catch(Exception e)
        {
            throw new AssertionFailedError("Should not throw an Exception");
        }
    }

    private ParsableByteArray loadData(String name) throws IOException {
        URL resource = getClass().getClassLoader().getResource(name);
        File file = new File(resource.getPath());
        FileInputStream inputStream = new FileInputStream(file);

        byte fileContent[] = new byte[(int)file.length()];
        inputStream.read(fileContent);
        inputStream.close();
        return new ParsableByteArray(fileContent);
    }

    @Test
    public void testFlvMetadataParser()
    {
        class TestListener implements TdMetaDataListener{
            public boolean called = false;
            public Map<String, Object> metadata = null;

            @Override
            public void onMetaDataReceived(Map<String, Object> metadata) {
                called = true;
                this.metadata = metadata;
            }

            public void reset(){
                called = false;
                metadata = null;
            }
        }

        try
        {
            TestListener listener = new TestListener();
            TdScriptTagPayloadLoader loader = new TdScriptTagPayloadLoader(null, listener);

            ParsableByteArray onCuePoint = loadData("raw/flvTag");
            ParsableByteArray onMetaDataEmpty = loadData("raw/WO_onMetaData_StreamTitle_Empty");
            ParsableByteArray onMetaDataNotEmpty = loadData("raw/WO_onMetaData_StreamTitle_NotEmpty");

            listener.reset();
            loader.consume(onCuePoint, 0);
            assertTrue(listener.called);
            assertTrue(listener.metadata.containsKey(TdMetaDataListener.NAME_CUEPOINT));

            listener.reset();
            loader.consume(onMetaDataEmpty, 0);
            assertTrue(listener.called);
            assertTrue(listener.metadata.containsKey(TdMetaDataListener.NAME_METADATA));

            listener.reset();
            loader.consume(onMetaDataNotEmpty, 0);
            assertTrue(listener.metadata.containsKey(TdMetaDataListener.NAME_METADATA));
            Map<String, Object> metadata = (Map<String, Object>)listener.metadata.get(TdMetaDataListener.NAME_METADATA);
            String streamTitle = (String)metadata.get("StreamTitle");
            assertTrue(streamTitle.contains("SAM SMITH"));
        }
        catch(Exception e)
        {
            throw new AssertionFailedError("Should not throw an Exception");
        }
    }

}
