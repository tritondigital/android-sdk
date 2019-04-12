package com.tritondigital.net.streaming.proxy.decoder;


/**
 * Stores all parameters needed to interpret the Audio Data, such as which type of media it is.
 */
public class AudioConfig
{
    private final MediaType    mMediaType;
    private final SamplingRate mSamplingRate;
    private final ChannelCount mChannelCount;
    private final byte[]       mAdditionalConfig;

    /**
     * All supported media types.
     */
    public enum MediaType
    {
        UNKNOWN,
        MP3,
        AAC,
    }


    /**
     * The supported sampling rate. Use getValueHz to have the value in Hz as an integer and getValueKhz to have the value in kHz as a float (cached value, no division made).
     */
    public enum SamplingRate
    {
        UNKNOWN(0),
        RATE_22K(22050),
        RATE_44K(44100);

        private final int mValueHz;
        private final float mValueKHz;
        SamplingRate(int valueHz)
        {
            mValueHz = valueHz;
            mValueKHz = valueHz / 1000;
        }

        public int   getValueHz()  { return mValueHz;  }
        public float getValueKHz() { return mValueKHz; }
    }


    /**
     * The supported number of channels per stream. Use getValue to have the number of channels as an integer.
     */
    public enum ChannelCount
    {
        UNKNOWN(0),
        MONO(1),
        STEREO(2);

        private final int mValue;
        ChannelCount(int value)
        {
            mValue = value;
        }

        public int   getValue()  { return mValue;  }
    }


    /**
     * Constructs an AudioConfig from all its parameters, which become read-only and cannot be changed without creating
     * a new AudioConfig. Changing the config after audio data was decoded is currently not supported, but it could eventually be,
     * if there is a need for it.
     */
    public AudioConfig(MediaType mediaType, SamplingRate samplingRate, ChannelCount channelCount, byte[] additionalConfig)
    {
        mMediaType = mediaType;
        mSamplingRate = samplingRate;
        mChannelCount = channelCount;
        mAdditionalConfig = additionalConfig;
    }


    /**
     * Gets the type of media being streamed.
     */
    public MediaType getMediaType()
    {
        return mMediaType;
    }

    /**
     * Gets the sample rate of the media being streamed.
     */
    public SamplingRate getSamplingRate()
    {
        return mSamplingRate;
    }

    /**
     * Gets the number of channels of the media being streamed (mono / stereo).
     */
    public ChannelCount getChannelCount()
    {
        return mChannelCount;
    }

    /**
     * Gets the Additional configuration, that are specific to the media type (e.g. 2 bytes AudioSpecificConfig in AAC-lbr).
     */
    public byte[] getAdditionalConfig()
    {
        return mAdditionalConfig;
    }
}
