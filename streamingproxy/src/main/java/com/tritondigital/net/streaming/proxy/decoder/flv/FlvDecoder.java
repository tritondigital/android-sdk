package com.tritondigital.net.streaming.proxy.decoder.flv;

import com.stw.core.media.format.flv.FlvAudioTag;
import com.stw.core.media.format.flv.FlvInputStream;
import com.stw.core.media.format.flv.FlvMetaTag;
import com.stw.core.media.format.flv.FlvTag;
import com.stw.core.media.format.flv.amf.AmfBoolean;
import com.stw.core.media.format.flv.amf.AmfData;
import com.stw.core.media.format.flv.amf.AmfMixedArray;
import com.stw.core.media.format.flv.amf.AmfNumber;
import com.stw.core.media.format.flv.amf.AmfString;
import com.tritondigital.net.streaming.proxy.decoder.AudioConfig;
import com.tritondigital.net.streaming.proxy.decoder.StreamContainerDecoder;
import com.tritondigital.net.streaming.proxy.utils.Log;

import java.io.EOFException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Decodes an Flv Stream to extract the AudioData and the MetaData.
 * This class uses CoreMedia to parse the Flv.
 * The Flv Decoding respects the format described at http://osflash.org/flv.
 */
public class FlvDecoder extends StreamContainerDecoder
{
    public static final int TAG_TYPE_AUDIO = 0x08;
    public static final int TAG_TYPE_META  = 0x12;


    private AudioConfig mAudioConfig;
    private Thread mBackgroundThread;
    private boolean mDecoding;


    @Override
    protected void startDecodingThread()
    {
        mDecoding = true;

        Runnable runnable = new Runnable()
        {
            @Override
            public void run()
            {
                parse();
                Log.i(TAG, Thread.currentThread().getName() + " exiting.");
            }
        };

        mBackgroundThread = new Thread(runnable, "StreamingProxy " + TAG + " decodingThread");
        mBackgroundThread.start();
    }


    @Override
    public void stopDecodingThread()
    {
        mDecoding = false;

        try
        {
            if (mBackgroundThread != null)
            {
                mBackgroundThread.interrupt();
                mBackgroundThread.join(5000);
            }
        }
        catch (InterruptedException e)
        {
            // Ignored
        }
    }


    /**
     * <p>Parses the input stream until the thread is interrupted. Blocks when tags cannot be read entirely.
     * Notifies listeners when audio config, audio data or meta data is read.
     *
     * <p>This method often ends because of an Interrupted exception. This is normal as it is most of the time
     * blocked on the read of the input stream. The exiting process stops the client and interrupts the thread
     * doing the parsing. When the thread is interrupted, it is most likely blocked on reading the input stream,
     * and therefore throws the Interrupted exception.
     */
    private void parse()
    {
        FlvInputStream flvInputStream = new FlvInputStream(mInputStreamForDecodingThread);
        mAudioConfig = null;

        try
        {
            // Read and discard the Flv Header. Throws an exception if the header is invalid.
            flvInputStream.readFrame();

            while (!Thread.interrupted() && mDecoding)
            {
                FlvTag tag = flvInputStream.getNextTag();
                if (tag == null)
                    break;

                if (tag.getType() == TAG_TYPE_AUDIO)
                {
                    if (mAudioConfig == null)
                    {
                        // Send the AudioConfig, assume that it will not change for the entire stream duration
                        if (createAudioConfig((FlvAudioTag)tag))
                            notifyAudioConfigDecoded(mAudioConfig);
                    }
                    else
                    {
                        // Send the Audio Data. Any data received before the Config is sent is ignored.
                        notifyAudioDataDecoded(tag.getBody(), tag.getBodySize(), tag.getTimeStamp());
                    }
                }
                else if (tag.getType() == TAG_TYPE_META)
                {
                    // Extract the AMF Array (for now, assume that MetaData is always provided as an array)
                    FlvMetaTag metaTag = (FlvMetaTag)tag;
                    AmfMixedArray amfMixedArray = (AmfMixedArray)metaTag.getAmfData();

                    // Convert this to an AMF-agnostic map to pass to the listener
                    Map<String, AmfData> amfMap  = amfMixedArray.getValues();
                    Map<String, Object>  javaMap = new HashMap<>();
                    AmfMapToJavaMap(amfMap, javaMap);

                    // Insert the name of the MetaData into the map as the first key to truly recreate
                    // the original structure as received in the Flv Stream.
                    Map<String, Object> metaDataMap = new HashMap<>();
                    metaDataMap.put(metaTag.getMetaName(), javaMap);

                    // Send the Audio Data. Any data received before the Config is sent is ignored.
                    notifyMetaDataDecoded(metaDataMap, tag.getTimeStamp());
                }
            }
        }
        catch (EOFException e)
        {
            // Stream was closed... Nothing to do but exit the thread
        }
        catch (Exception e)
        {
            Log.e(TAG, "Exception caught " + e);
            e.printStackTrace();
        }
        finally
        {
            if (mInputStreamForDecodingThread != null)
            {
                mInputStreamForDecodingThread.close();
                mInputStreamForDecodingThread = null;
            }
        }
    }


    /**
     * Create the AudioConfig from the given AudioTag. It extracts the sampling rate and other common specs, plus those specific
     * to the media type such as AAC AudioSpecificConfig.
     *
     * @return true if the AudioConfig was created, false if the given audioTag does not contain enough information to create the AudioConfig.
     */
    private boolean createAudioConfig(FlvAudioTag audioTag)
    {
        // Contains any additional config, depending on the sound format. Null if the format does not require any additional config.
        byte[] additionalConfig = null;

        // AAC has additional AudioSpecificConfig. Wait for it before creating and sending the AudioConfig to the listener.
        if (audioTag.getSoundFormat() == FlvAudioTag.SoundFormat.AAC)
        {
            if (audioTag.getAacPacketType() == FlvAudioTag.AacPacketType.SequenceHeader)
                additionalConfig = audioTag.getBody();
            else
                return false;
        }

        // Determine Media Type
        AudioConfig.MediaType mediaType;
        switch (audioTag.getSoundFormat())
        {
            case AAC: mediaType = AudioConfig.MediaType.AAC;       break;
            case MP3: mediaType = AudioConfig.MediaType.MP3;       break;
            default:  mediaType = AudioConfig.MediaType.UNKNOWN;   break;
        }

        // Determine Sampling Rate
        AudioConfig.SamplingRate samplingRate;
        switch (audioTag.getSoundRate())
        {
            case R22KHz: samplingRate = AudioConfig.SamplingRate.RATE_22K;  break;
            case R44KHz: samplingRate = AudioConfig.SamplingRate.RATE_44K;  break;
            default:     samplingRate = AudioConfig.SamplingRate.UNKNOWN;   break;
        }

        // Determine Channel Count
        AudioConfig.ChannelCount channelCount;
        switch (audioTag.getSoundType())
        {
            case Mono:   channelCount = AudioConfig.ChannelCount.MONO;    break;
            case Stereo: channelCount = AudioConfig.ChannelCount.STEREO;  break;
            default:     channelCount = AudioConfig.ChannelCount.UNKNOWN; break;
        }

        // Create AudioConfig
        mAudioConfig = new AudioConfig(mediaType, samplingRate, channelCount, additionalConfig);

        return true;
    }


    /**
     * <p>Converts a map from Amf Mixed Array format to Java standard objects map.
     * This removes the dependency on Amf to be able to pass a Map that the listener will
     * be able to interpret without knowing about Amf.
     *
     * <p><B>Note</B>: Only the types that are currently used by the server are supported. Support for
     * the other types can be added if needed.
     */
    @SuppressWarnings("unchecked")
    private void AmfMapToJavaMap(Map<String, AmfData> amfMap, Map<String, Object>  javaMap)
    {
        javaMap.clear();

        Set<String> allKeys = amfMap.keySet();
        for (String curKey : allKeys)
        {
            Object curValue = null;
            AmfData curValueAmf = amfMap.get(curKey);

            switch (curValueAmf.getType())
            {
                case AmfData.TYPE_NUMBER:
                    curValue = ((AmfNumber) curValueAmf).getValue();
                    break;

                case AmfData.TYPE_BOOLEAN:
                    curValue = ((AmfBoolean) curValueAmf).getValue();
                    break;

                case AmfData.TYPE_STRING:
                    curValue = String.valueOf(((AmfString)curValueAmf).getValue());
                    break;

                case AmfData.TYPE_NULL:
                    curValue = null;
                    break;

                case AmfData.TYPE_MIXEDARRAY:
                    curValue = new HashMap<String, Object>();
                    AmfMapToJavaMap(((AmfMixedArray)curValueAmf).getValues(), (Map<String, Object>)curValue);
                    break;

                default:
                    Log.w(TAG, "Unhandled Amf Data Type: " + curValueAmf.getType());
            }

            javaMap.put(curKey, curValue);
        }
    }
}
