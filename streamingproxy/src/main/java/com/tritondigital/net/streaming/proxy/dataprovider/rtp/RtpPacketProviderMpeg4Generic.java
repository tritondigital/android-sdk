package com.tritondigital.net.streaming.proxy.dataprovider.rtp;

import com.tritondigital.net.streaming.proxy.dataprovider.rtp.RtpPacketProvider.StateChangedListener.ErrorDetail;
import com.tritondigital.net.streaming.proxy.decoder.AudioConfig;
import com.tritondigital.net.streaming.proxy.utils.Log;
import com.tritondigital.net.streaming.proxy.utils.StringUtils;

/**
 * A class to create RTP Packet using the MPEG4-GENERIC profile, based on RFC 3640 (http://www.rfc-editor.org/rfc/rfc3640.txt)
 */
public class RtpPacketProviderMpeg4Generic extends RtpPacketProvider
{
    /** Audio Specific Config, extracted when the AAC header packet (typically the first AAC Packet) is received and passed using the AudioConfig */
    private String mAudioSpecificConfig;


    @Override
    protected String getProfileSpecificSdpConfig()
    {
        return
            "a=rtpmap:" + RtpPacket.PAYLOAD_TYPE + " mpeg4-generic/" + mAudioConfig.getSamplingRate().getValueHz() + "/" + mAudioConfig.getChannelCount().getValue() + CRLF +                                           // RTPMap map PAYLOAD_TYPE to mp4-latm / Sample Rate.
            "a=fmtp:" + RtpPacket.PAYLOAD_TYPE + " streamtype=5;profile-level-id=1;mode=AAC-hbr;config=" + mAudioSpecificConfig + ";sizeLength=13;indexLength=3;indexDeltaLength=3" + CRLF +                            // AudioSpecificConfig is extracted when the AAC Header packet is received.
            "";
    }


    @Override
    public void onAudioConfigDecoded(AudioConfig audioConfig)
    {
        if (audioConfig.getMediaType() != AudioConfig.MediaType.AAC)
        {
            Log.e(TAG, "Wrong Media Type: " + audioConfig.getMediaType() + " when expecting " + AudioConfig.MediaType.AAC);
            notifyListenerError(ErrorDetail.WRONG_MEDIA_TYPE);
            return;
        }

        // Extract AudioSpecificConfig
        byte[] audioSpecificConfig = audioConfig.getAdditionalConfig();

        // Save this Audio Specific Config for reuse
        mAudioSpecificConfig = StringUtils.byteArrayToString(audioSpecificConfig);
        Log.i(TAG, "AudioSpecificConfig: " + mAudioSpecificConfig);

        // Called last as it notifies the listeners.
        super.onAudioConfigDecoded(audioConfig);
    }


    @Override
    protected int getPayloadSize(int audioDataLength)
    {
        return audioDataLength + 4;
    }


    /**
     * Create the payload for RTP Packet with the MPEG4-GENERIC profile.
     *
     * Puts the content of the payload in the given preallocated output buffer and the given offset.
     * This helps preventing new buffer copy by reusing the same bytes array for the output.
     *
     * @param audioData         The data to put in the payload
     * @param audioDataLength   The part of the AudioData buffer to use (buffer might be bigger if is is reused)
     * @param outPayload        Reference to a byte array preallocated with enough memory (at least the size returned by getPayloadSize)
     * @param outPayloadOffset  Offset at which the payload will be written in outPayload, allowing to create a header before the payload.
     */
    @Override
    protected void createPayload(byte[] audioData, int audioDataLength, byte[] outPayload, int outPayloadOffset)
    {
        final short auHeadersLength = 16;
        final short auHeader0 = (short)(audioDataLength << 3);

        outPayload[outPayloadOffset++] = (byte)(0);
        outPayload[outPayloadOffset++] = (byte)((auHeadersLength) & 0xFF);
        outPayload[outPayloadOffset++] = (byte)((auHeader0 >> 8) & 0xFF);
        outPayload[outPayloadOffset++] = (byte)((auHeader0) & 0xFF);

        System.arraycopy(audioData, 0, outPayload, outPayloadOffset, audioDataLength);
    }
}
