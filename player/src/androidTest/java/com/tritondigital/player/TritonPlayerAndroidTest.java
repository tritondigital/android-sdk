package com.tritondigital.player;

import android.content.Context;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.tritondigital.util.Log;

import junit.framework.AssertionFailedError;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;


import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;


import static android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;


@RunWith(AndroidJUnit4.class)
public class TritonPlayerAndroidTest
{
    private final static String TAG = "TritonPlayerAndroidTest";
    private static final int MSG_CREATE_PLAYER        = 101;
    private static final int MSG_RELEASE_PLAYER       = 104;

    private static final String PODCAST_TEST_URL      = "https://storage.googleapis.com/automotive-media/Jazz_In_Paris.mp3";


    static float VOLUME = 0.5f;


    static Context TARGET_CONTEXT;
    static Handler MAIN_LOOP_HANDLER;
    static TritonPlayer TRITON_PLAYER;
    static CountDownLatch LATCH;
    static AudioManager AUDIO_MANAGER;
    static File LOCAL_FILE;

    private String mLiveStreamingUrl;


    @BeforeClass
    public static void setAllUp()
    {
        TARGET_CONTEXT    = InstrumentationRegistry.getInstrumentation().getTargetContext();
        LATCH             = new CountDownLatch(1);
        AUDIO_MANAGER     = (AudioManager)TARGET_CONTEXT.getSystemService(Context.AUDIO_SERVICE);
        MAIN_LOOP_HANDLER = new android.os.Handler(Looper.getMainLooper())
        {
            @Override
            public void handleMessage(Message message)
            {
                int what = message.what;
                Bundle settings = message.getData();
                switch (what)
                {
                    case MSG_CREATE_PLAYER:
                        TRITON_PLAYER = new TritonPlayer(TARGET_CONTEXT, settings);
                        break;
                    case MSG_RELEASE_PLAYER:
                         TRITON_PLAYER.release();
                         break;
                }
            }
        };

        //Verify Network is available
        ConnectivityManager manager = (ConnectivityManager)TARGET_CONTEXT.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info            = manager.getActiveNetworkInfo();
        assertTrue( info != null && info.isConnected() );


        //Wake up the device if it is on sleep mode
        PowerManager pm = (PowerManager) TARGET_CONTEXT.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock = pm.newWakeLock(( FLAG_KEEP_SCREEN_ON  | PowerManager.ACQUIRE_CAUSES_WAKEUP), "TAG");
        wakeLock.acquire(3*60*1000);//3 minutes

        createPodcastLocalFile();
    }

    @AfterClass
    public static void tearAllDown()
    {
        MAIN_LOOP_HANDLER.sendEmptyMessage(MSG_RELEASE_PLAYER);
        TARGET_CONTEXT = null;
        LOCAL_FILE.delete();
        LOCAL_FILE =null;
    }



    @Before
    public void setUp()
    {
       // verifyConstructorParams();
    }

    @After
    public void tearDown()
    {
        //Do nothing
    }


    private void verifyConstructorParams()
    {
        //Validate Context cannot be null
        TritonPlayer aPlayer = null;
        try
        {
            aPlayer = new TritonPlayer(null, new Bundle());
        }
        catch(Exception e)
        {
            assertTrue(e instanceof java.lang.IllegalArgumentException);
        }

        assertTrue(aPlayer ==null);


        //Validate player settings cannot be null
        try
        {
            aPlayer = new TritonPlayer(TARGET_CONTEXT, null);
        }
        catch(Exception e)
        {
            assertTrue(e instanceof java.lang.IllegalArgumentException);
        }
        assertTrue(aPlayer ==null);
    }


    @Test
    public void canPlayStationFlvMount()
    {
        doProvisioning("MOBILEFM_AACV2", TritonPlayer.TRANSPORT_FLV);
        createAndSendMessage(createStationFlvMountPlayerSettings(), MSG_CREATE_PLAYER);
        Runnable r = new Runnable()
        {
            public void run()
            {
                try
                {
                    assertNotNull(TRITON_PLAYER);
                    assertNotNull(TRITON_PLAYER.getSettings());
                    TRITON_PLAYER.setVolume(VOLUME);
                    TRITON_PLAYER.setOnStateChangedListener(new TritonPlayer.OnStateChangedListener()
                    {
                        public void onStateChanged(MediaPlayer player, int state)
                        {
                            assertTrue(player == TRITON_PLAYER);
                            assertTrue(state != TritonPlayer.STATE_ERROR);
                        }
                    });

                    //Play
                    TRITON_PLAYER.play();
                    waitFor(20);
                    int state = TRITON_PLAYER.getState();
                    assertFalse(state == TritonPlayer.STATE_ERROR);
                    //assertTrue(isMusicPlaying());

                    //Stop
                    TRITON_PLAYER.stop();
                    state = TRITON_PLAYER.getState();
                    assertTrue(state == TritonPlayer.STATE_STOPPED);


                    //Release
                    TRITON_PLAYER.release();
                    waitFor(1);
                    state = TRITON_PLAYER.getState();
                    assertTrue(state == TritonPlayer.STATE_RELEASED);
                    //assertFalse(isMusicPlaying());
                }
                catch(Exception e)
                {
                    throw new AssertionFailedError("Should not throw an Exception");
                }
            }
        };
        MAIN_LOOP_HANDLER.post(r);
    }

    @Test
    public void canPlayStationHlsMount()
    {
        doProvisioning("TRITONRADIOMUSICAAC", TritonPlayer.TRANSPORT_HLS);
        createAndSendMessage(createStationHlsMountPlayerSettings(), MSG_CREATE_PLAYER);
        Runnable r = new Runnable()
        {
            public void run()
            {
                try
                {
                    assertNotNull(TRITON_PLAYER);
                    assertNotNull(TRITON_PLAYER.getSettings());
                    TRITON_PLAYER.setVolume(VOLUME);
                    TRITON_PLAYER.setOnStateChangedListener(new TritonPlayer.OnStateChangedListener()
                    {
                        public void onStateChanged(MediaPlayer player, int state)
                        {
                            assertTrue(player == TRITON_PLAYER);
                            assertTrue(state != TritonPlayer.STATE_ERROR);
                        }
                    });

                    //Play
                    TRITON_PLAYER.play();
                    waitFor(20);
                    int state = TRITON_PLAYER.getState();
                    assertFalse(state == TritonPlayer.STATE_ERROR);
                    //assertTrue(isMusicPlaying());

                    //Stop
                    TRITON_PLAYER.stop();
                    state = TRITON_PLAYER.getState();
                    assertTrue(state == TritonPlayer.STATE_STOPPED);


                    //Release
                    TRITON_PLAYER.release();
                    waitFor(1);
                    state = TRITON_PLAYER.getState();
                    assertTrue(state == TritonPlayer.STATE_RELEASED);
                    //assertFalse(isMusicPlaying());
                }
                catch(Exception e)
                {
                    throw new AssertionFailedError("Should not throw an Exception");
                }
            }
        };
        MAIN_LOOP_HANDLER.post(r);
    }

    @Test
    public void canPlayOnDemandStream()
    {
        createAndSendMessage(createOnDemandStreamPlayerSettings(), MSG_CREATE_PLAYER);
        Runnable r = new Runnable()
        {
            public void run()
            {
                try
                {
                    assertNotNull(TRITON_PLAYER);
                    assertNotNull(TRITON_PLAYER.getSettings());
                    TRITON_PLAYER.setVolume(VOLUME);
                    TRITON_PLAYER.setOnStateChangedListener(new TritonPlayer.OnStateChangedListener()
                    {
                        public void onStateChanged(MediaPlayer player, int state)
                        {
                             assertTrue(player == TRITON_PLAYER);
                             assertTrue(state != TritonPlayer.STATE_ERROR);
                        }
                    });

                    //Play
                    TRITON_PLAYER.play();
                    waitFor(10);
                    int state = TRITON_PLAYER.getState();
                    assertFalse(state == TritonPlayer.STATE_ERROR);
                   // assertTrue(isMusicPlaying());

                    //Pause
                    TRITON_PLAYER.pause();
                    waitFor(1);
                    state = TRITON_PLAYER.getState();
                    //assertTrue(state == TritonPlayer.STATE_PAUSED);


                    //Resume playing
                    TRITON_PLAYER.play();
                    waitFor(2);
                    state = TRITON_PLAYER.getState();
                    assertFalse(state == TritonPlayer.STATE_ERROR);

                    //Change volume
                    float volume = TRITON_PLAYER.getVolume();
                    TRITON_PLAYER.setVolume(volume+ 0.3f);
                    waitFor(1);
                    //assertTrue(TRITON_PLAYER.getVolume() == (volume+ 0.3f));

                    //Seek
                    /*int duration = TRITON_PLAYER.getDuration();
                    assertTrue(duration>0);
                    int seekToPosition = duration/2;
                    TRITON_PLAYER.seekTo(seekToPosition);
                    waitFor(1);
                    int position = TRITON_PLAYER.getPosition();
                    assertTrue(position >= seekToPosition);*/

                    //Stop
                    TRITON_PLAYER.stop();
                    state = TRITON_PLAYER.getState();
                    assertTrue(state == TritonPlayer.STATE_STOPPED);


                    //Release
                    TRITON_PLAYER.release();
                    waitFor(1);
                    state = TRITON_PLAYER.getState();
                    assertTrue(state == TritonPlayer.STATE_RELEASED);
                    //assertFalse(isMusicPlaying());
                }
                catch(Exception e)
                {
                    throw new AssertionFailedError("Should not throw an Exception");
                }
            }
        };
        MAIN_LOOP_HANDLER.post(r);
    }


    @Test
    public void canPlayLocalFile()
    {
        createAndSendMessage(createLocalFilePlayerSettings(), MSG_CREATE_PLAYER);
        Runnable r = new Runnable()
        {
            public void run()
            {
                try
                {
                    assertTrue(LOCAL_FILE.exists());
                    assertNotNull(TRITON_PLAYER);
                    assertNotNull(TRITON_PLAYER.getSettings());
                    Bundle set = TRITON_PLAYER.getSettings();
                    TRITON_PLAYER.setVolume(VOLUME);
                    TRITON_PLAYER.setOnStateChangedListener(new TritonPlayer.OnStateChangedListener()
                    {
                        public void onStateChanged(MediaPlayer player, int state)
                        {
                            assertTrue(player == TRITON_PLAYER);
                            assertTrue(state != TritonPlayer.STATE_ERROR);
                        }
                    });

                    //Play
                    TRITON_PLAYER.play();
                    waitFor(2);
                    int state = TRITON_PLAYER.getState();
                    assertFalse(state == TritonPlayer.STATE_ERROR);
                    //assertTrue(isMusicPlaying());

                    //Pause
                    TRITON_PLAYER.pause();
                    waitFor(1);
                    state = TRITON_PLAYER.getState();
                    //assertTrue(state == TritonPlayer.STATE_PAUSED);


                    //Resume playing
                    TRITON_PLAYER.play();
                    waitFor(2);
                    state = TRITON_PLAYER.getState();
                    assertFalse(state == TritonPlayer.STATE_ERROR);

                    //Change volume
                    float volume = TRITON_PLAYER.getVolume();
                    TRITON_PLAYER.setVolume(volume+ 0.3f);
                    waitFor(1);
                    //assertTrue(TRITON_PLAYER.getVolume() == (volume+ 0.3f));

                    //Seek
                   /* int duration = TRITON_PLAYER.getDuration();
                    assertTrue(duration>0);
                    int seekToPosition = duration/2;
                    TRITON_PLAYER.seekTo(seekToPosition);
                    waitFor(1);
                    int position = TRITON_PLAYER.getPosition();
                    assertTrue(position >= seekToPosition);*/

                    //Stop
                    TRITON_PLAYER.stop();
                    state = TRITON_PLAYER.getState();
                    assertTrue(state == TritonPlayer.STATE_STOPPED);


                    //Release
                    TRITON_PLAYER.release();
                    waitFor(1);
                    state = TRITON_PLAYER.getState();
                    assertTrue(state == TritonPlayer.STATE_RELEASED);
                    //assertFalse(isMusicPlaying());
                }
                catch(Exception e)
                {
                    throw new AssertionFailedError("Should not throw an Exception");
                }
            }
        };
        MAIN_LOOP_HANDLER.post(r);
    }



    private Bundle createStationFlvMountPlayerSettings()
    {
        Bundle b = new Bundle();
        b.putString(TritonPlayer.SETTINGS_STATION_BROADCASTER,"Triton Digital");
        b.putString(TritonPlayer.SETTINGS_STATION_NAME,"Mobile FM");

        if(mLiveStreamingUrl != null)
            b.putString(TritonPlayer.SETTINGS_STREAM_URL, mLiveStreamingUrl);
        else
            b.putString(TritonPlayer.SETTINGS_STATION_MOUNT, "MOBILEFM_AACV2");

        return b;
    }


    private Bundle createStationHlsMountPlayerSettings()
    {
        Bundle b = new Bundle();
        b.putString(TritonPlayer.SETTINGS_STATION_BROADCASTER,"Triton Digital");
        b.putString(TritonPlayer.SETTINGS_STATION_NAME,"Mobile FM");
        b.putString(TritonPlayer.SETTINGS_TRANSPORT, TritonPlayer.TRANSPORT_HLS);

        if(mLiveStreamingUrl != null)
            b.putString(TritonPlayer.SETTINGS_STREAM_URL, mLiveStreamingUrl);
        else
            b.putString(TritonPlayer.SETTINGS_STATION_MOUNT, "TRITONRADIOMUSICAAC");
        return b;
    }


    private Bundle createOnDemandStreamPlayerSettings()
    {
        Bundle b = new Bundle();
        b.putString(TritonPlayer.SETTINGS_STATION_BROADCASTER,"Triton Digital");
        b.putString(TritonPlayer.SETTINGS_STATION_NAME,"Mobile FM");
        b.putString(TritonPlayer.SETTINGS_STREAM_URL,PODCAST_TEST_URL);

        return b;
    }

    private Bundle createOnDemandStreamPlayerSettingsWo()
    {
        Bundle b = new Bundle();
        b.putString(TritonPlayer.SETTINGS_STATION_BROADCASTER,"Triton Digital");
        b.putString(TritonPlayer.SETTINGS_STREAM_URL,"Mobile FM");
        b.putString(TritonPlayer.SETTINGS_STREAM_URL,PODCAST_TEST_URL);

        return b;
    }


    private Bundle createLocalFilePlayerSettings()
    {
        Bundle b = new Bundle();
        b.putString(TritonPlayer.SETTINGS_STATION_BROADCASTER,"Triton Digital");
        b.putString(TritonPlayer.SETTINGS_STATION_NAME,"Mobile FM");
        b.putString(TritonPlayer.SETTINGS_STREAM_URL,LOCAL_FILE.getPath());
        return b;
    }


    private void doProvisioning(final String mount, final String transport)
    {
        mLiveStreamingUrl = null;
        Runnable r = new Runnable()
        {
            public void run()
            {
                Provisioning parser = new Provisioning();
                parser.setMount(mount, transport);
                parser.setListener(new Provisioning.Listener()
                {
                    @Override
                    public void onProvisioningSuccess(Provisioning prov, Bundle result)
                    {
                        assertNotNull(result);
                        ArrayList<Bundle> servers = result.getParcelableArrayList(Provisioning.Result.SERVERS);
                        assertTrue(servers!= null && servers.size() > 0);
                        Bundle server = servers.get(0);
                        ArrayList<String> ports = server.getStringArrayList(Provisioning.Result.Server.PORTS);

                        String baseUrl = "http://" + server.getString(Provisioning.Result.Server.HOST) + ':'
                                + ports.get(0) + '/' + result.getString(Provisioning.Result.MOUNT);

                        String streamSuffix = result.getString(Provisioning.Result.MOUNT_SUFFIX);
                        mLiveStreamingUrl    = (streamSuffix == null) ? baseUrl : (baseUrl + streamSuffix);
                    }

                    @Override
                    public void onProvisioningFailed(Provisioning src, int errorCode)
                    {

                    }
                });

                parser.request();
            }
        };

        MAIN_LOOP_HANDLER.post(r);
        waitFor(3);
    }


    private boolean isMusicPlaying()
    {
        return AUDIO_MANAGER.isMusicActive();
    }

    private void createAndSendMessage(Bundle playerSettings, int what)
    {
        Message msg = MAIN_LOOP_HANDLER.obtainMessage(what);
        msg.setData(playerSettings);

        boolean b = MAIN_LOOP_HANDLER.sendMessage(msg);
        assertTrue(b);
        waitFor(1);
    }


    private static void waitFor(long seconds)
    {
        try
        {
            LATCH.await(seconds, TimeUnit.SECONDS);
        }
        catch(Exception e){}
    }


    private static void createPodcastLocalFile()
    {
       Runnable r = new Runnable()
       {
           public void run()
           {
               InputStream is = null;
               HttpURLConnection conn = null;

               try
               {
                   LOCAL_FILE        = File.createTempFile("podcast", "mp3");
                   LOCAL_FILE.deleteOnExit();

                   URL url = new URL(PODCAST_TEST_URL);


                   conn = (HttpURLConnection) url.openConnection();
                   conn.setUseCaches(true);
                   conn.setConnectTimeout(12000);
                   conn.setReadTimeout(15000);
                   conn.setRequestMethod("GET");
                   conn.setDoInput(true);
                   conn.connect();

                   int responseCode = conn.getResponseCode();
                   if(responseCode == HttpURLConnection.HTTP_OK)
                   {
                       is = conn.getInputStream();

                       FileOutputStream fos  = new FileOutputStream(LOCAL_FILE);

                       byte[] buffer = new byte[1024];
                       int buffLength;
                       long downloadedLength = 0;

                       while ((buffLength = is.read(buffer)) != -1) {
                           downloadedLength += buffLength;
                           fos.write(buffer, 0, buffLength);
                       }
                       fos.close();
                       is.close();
                   }
               }
               catch(Exception e)
               {
                   Log.w(TAG, e, "Download exception for: " + PODCAST_TEST_URL);
               }
               finally {
                   if (conn != null) {
                       conn.disconnect();
                   }

                   try {
                       if (is != null) {
                           is.close();
                       }
                   } catch (final IOException e) {}


               }
           }
       };

        new Thread(r).start();
        waitFor(3);
    }


}
