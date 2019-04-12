package com.tritondigital.net.streaming.proxy.dataprovider.rtp;

import com.tritondigital.net.streaming.proxy.dataprovider.DataProvider;
import com.tritondigital.net.streaming.proxy.dataprovider.Packet;
import com.tritondigital.net.streaming.proxy.dataprovider.rtp.RtpPacketProvider.StateChangedListener.ErrorDetail;
import com.tritondigital.net.streaming.proxy.decoder.AudioConfig;
import com.tritondigital.net.streaming.proxy.utils.Log;

/**
 * <p>Base class for different kind of Rtp packets provider. The structure of the Rtp packet is almost identical between each
 * type of provider and follows the RFC 3550 (http://www.ietf.org/rfc/rfc3550.txt), but the payload differs.
 *
 * <p>This class provides Rtp packets, adds functionality that are specific to Rtp streaming such as the SDP Configuration
 * for the RTSP DESCRIBE response.
 *
 * <p>Typically, the subclass only overrides getProfileSpecificSdpConfig to tell the Sdp lines for the particular payload type,
 * getPayloadSize and createPayload to create this payload with the good type and optionally onAudioConfigDecoded if it needs
 * to make checks on the config or convert the config to a specific format.
 */
@SuppressWarnings("ALL")
public abstract class RtpPacketProvider extends DataProvider
{
    public final String TAG = "RtpPacketProvider";

    protected static final String LOCALHOST   = "127.0.0.1";
    protected static final String CRLF        = "\r\n";

    /** Tells if the AudioSpecificConfig is ready, thus if getting the SDPConfig will block or not.*/
    boolean mAudioConfigReady = false;

    /** Used to block the method that returns SDP Config if the AudioSpecificConfig is not ready.*/
    final Object mAudioConfigReadyLock = new Object();

    /** Used to unblock the thread. Set to true to force the blocking loop to break, then reset to true so the next time that getSdpConfig is called, it will block. */
    private volatile boolean     mBlockUntilAudioConfigReadyEnabled = true;


    /**
     * Interface to be implemented in order to be notified about the important changes of state of the server.
     * Calls to this listener may come from any thread, so thread safety should be considered when designing the implementation.
     */
    public interface StateChangedListener
    {
        /**
         * All different reason for error, used when notifying the listener about an error (onServerError).
         */
        enum ErrorDetail
        {
            UNKNOWN,            /** Generic error, cause could not be identified. */
            WRONG_MEDIA_TYPE,   /** The media type is incorrect (currently supported media type is AAC only). */
        }

        /** Provider can produce the SDP Config without blocking, it has received all the necessary data. */
        void onProviderSdpConfigReady();


        /**
         * Provider encountered an unrecoverable error that prevents to continue to run. This is not a punctual error that causes a single packet to
         * be lost (this would instead invoke onProviderPacketLost), but it is an error that causes the provider to stop running.
         */
        void onProviderError(ErrorDetail errorDetail);
    }


    protected AudioConfig mAudioConfig;
    private StateChangedListener mStateChangedListener;


    /** Increments by one for each new packet that is pushed in the queue */
    protected short mNextPacketSequenceNumber = 0;


    /**
     * Save the AudioConfig and notify / unblock the listeners and threads waiting for it to become ready.
     * Subclasses should always call this method on their super class if they override it. It is recommended to call
     * the 'super' version at the end of the overriden method, as it notifies the listeners and unblock threads waiting
     * for the SDPConfig to become available.
     */
    @Override
    public void onAudioConfigDecoded(AudioConfig audioConfig)
    {
        mAudioConfig = audioConfig;

        notifyListenerSdpConfigReady();
    }


    @Override
    public void onAudioDataDecoded(byte[] audioData, int audioDataLength, int timestamp)
    {
        // Proceed with the creation of the RTP packet with the default header and enough room for the payload.
        RtpPacket rtpPacket = (RtpPacket) getFreePacket();
        rtpPacket.setPayloadSize(getPayloadSize(audioDataLength));

        // Fill header with varying fields
        rtpPacket.setSequenceNumber(mNextPacketSequenceNumber++); // Increment the sequence number for the next packet.
        rtpPacket.setTimeStamp(getRtpTimestamp(timestamp));

        // Fill payload by copying directly in the bytes array (do not use setPayload to avoid intermediate copies)
        createPayload(audioData, audioDataLength, rtpPacket.getData(), RtpPacket.HEADER_SIZE);

        // Packet ready, add to queue
        enqueuePacket(rtpPacket);
    }


    @Override
    protected Packet createEmptyPacket()
    {
        return new RtpPacket(512);
    }

    /**
     * Gets the minimal size that the bytes array containing the payload should have.
     */
    protected abstract int getPayloadSize(int audioDataLength);


    /**
     * <p>Create the payload for RTP Packet with the appropriate profile.
     *
     * <p>Puts the content of the payload in the given preallocated output buffer and the given offset.
     * This helps preventing new buffer copy by reusing the same bytes array for the output.
     *
     * @param audioData         The data to put in the payload
     * @param audioDataLength   The part of the AudioData buffer to use (buffer might be bigger if is is reused)
     * @param outPayload        Reference to a byte array preallocated with enough memory (at least the size returned by getPayloadSize)
     * @param outPayloadOffset  Offset at which the payload will be written in outPayload, allowing to create a header before the payload.
     */
    protected abstract void createPayload(byte[] audioData, int audioDataLength, byte[] outPayload, int outPayloadOffset);


    /**
     * Returns the SDP string to be sent by the RTSP server in response to a Describe request. It includes common configurations
     * from the RFC 2327 (http://www.ietf.org/rfc/rfc2327.txt) added to those specific to the profile used to form the RTP Packets payload.
     * The returned string is ready to be sent to the RTSP client (correctly formatted, contains CR + LF for end of line characters, etc.)
     */
    public String getSdpConfig(String absControlUrl)
    {
        // Block until AudioConfig is ready.
        synchronized (mAudioConfigReadyLock)
        {
            mBlockUntilAudioConfigReadyEnabled = true; // Set to true, may be set to false on a secondary thread to unblock this one.

            try
            {
                while (mBlockUntilAudioConfigReadyEnabled && !mAudioConfigReady)
                {
                    Log.i(TAG, "Waiting for AudioConfig to be received");
                    mAudioConfigReadyLock.wait();
                }
            }
            catch (InterruptedException e)
            {
                return null;
            }

        }

        return getCommonSdpConfig(absControlUrl) + getProfileSpecificSdpConfig();
    }


    /**
     * Returns the SDP string lines that are not related to the profile itself.
     * Currently hard-coded based on http://tools.ietf.org/html/rfc4566#section-5.1.
     */
    private String getCommonSdpConfig(String absControlUrl)
    {
        return
                // Session level (rfc2327 appendix-B: The session-level part starts with a `v=' line and continues to the first media-level section.)
                "v=0"+ CRLF +                                                   // Version 0
                "o=- 1376540094 9376540094 IN IP4 " + LOCALHOST + CRLF +        // Origin - (no username) 1376540094 (random sess-id) 9376540094 (random sess-version) IN (internet nettype) IP4 (IP v4 addrtype)
                "s= " + CRLF +                                                  // Session name (If a session has no meaningful name, the value "s= " SHOULD be used)
                "c=IN IP4 0.0.0.0" + CRLF +                                     // Connection Data (IN IPV4, matches the Origin), 0.0.0.0 seems to do the job fine and is almost always used when sniffing external RTSP streams
                "a=control:*" + CRLF +                                          // Control for the default block
                "a=range:npt=0-86400" + CRLF +                                  // Normally optional (rfc2326 Appendix C) Some Android devices (e.g. LG VM670 on Firmware 2.2.2) fail to play streams with no range. Therefore we put a very large range to simulate a live stream.
                "t=0 0" + CRLF +                                                // Timing (session start time 0, session stop time 0 => permanent session)

                // Media level (rfc2327 appendix-B: The media description starts with an `m=' line and continues to the next media description or end of the whole session description.)
                "m=audio 0 RTP/AVP " + RtpPacket.PAYLOAD_TYPE + CRLF +          // Media Description - audio (Media Type) 0 (destination port, server has no preference) RTP/AVP (protocol) PAYLOAD_TYPE (payload type a=rtpmap:PAYLOAD_TYPE see rtpmap line of the profile specific config. Also used in RTP packet header)
                "b=RR:0" + CRLF +                                               // Bandwidth reserved for RTCP in Receiver (rfc4571 set to 0 to disable RTCP. Everything is local so disable RTCP even if discouraged, does not seem to be a problem for now.)
                "b=RS:0" + CRLF +                                               // Bandwidth reserved for RTCP in Sender (rfc4571 set to 0 to disable RTCP. Everything is local so disable RTCP even if discouraged, does not seem to be a problem for now.)
                "a=control:" + absControlUrl + CRLF +                           // Controls is the absolute URL. Normally it could be either relative or absolute (rfc2326 appendix C) .
                "";
    }


    /**
     * Returns the SDP string lines that are related to the profile itself.
     * This is guaranteed to be only called after the AudioConfig has been received, the calling thread will be blocked until
     * it is received.
     */
    protected abstract String getProfileSpecificSdpConfig();


    /**
     * In addition to the default stop,
     * Resets the Sdp Config state to make sure to block the next time that this provider is used, until the SdpConfig
     * is ready again.
     */
    @Override
    public void stop()
    {
        super.stop();

        stopBlockingUntilAudioConfigReady();
        synchronized (mAudioConfigReadyLock)
        {
            mAudioConfigReady = false;
        }
    }


    /**
     * Unblock the thread waiting on a call to blockUntilReady.
     */
    public void stopBlockingUntilAudioConfigReady()
    {
        synchronized (mAudioConfigReadyLock)
        {
            mBlockUntilAudioConfigReadyEnabled = false;
            mAudioConfigReadyLock.notify();
        }

    }


    /**
     * Tells if the SDP strings can be retrieved without blocking.
     * It is assumed that once the AudioConfig has been received, all mandatory information
     * are available. Thus, this method returns false until the AudioConfig has been received from the
     * protocol container decoder.
     */
    public boolean isSdpConfigReady()
    {
        synchronized (mAudioConfigReadyLock)
        {
            return mAudioConfigReady;
        }
    }


    /**
     * Gets the sequence number that the first packet in the queue has (or will have when it is inserted).
     * This is typically used for the RTP server to fill the RTP-INFO line of the PLAY response.
     */
    public short getFirstPacketSequenceNumber()
    {
        RtpPacket firstPacket = (RtpPacket) mPacketsQueue.peek();
        if (firstPacket != null)
            return firstPacket.getSequenceNumber();

        return mNextPacketSequenceNumber;
    }


    /**
     * Gets the timestamp that the first packet in the queue has (or will have when it is inserted).
     * This is typically used for the RTP server to fill the RTP-INFO line of the PLAY response.
     */
    public int getFirstPacketTimestamp()
    {
        RtpPacket firstPacket = (RtpPacket) mPacketsQueue.peek();
        if (firstPacket != null && firstPacket.getTimeStamp() >= 0)
            return firstPacket.getTimeStamp();

        // For now, there is not constant factor added to the timestamp, but in case there is one, it should be returned here.
        return 0;
    }


    /**
     * Sets the listener to be notified by any state change on this provider.
     */
    public void setStateChangedListener(StateChangedListener stateChangedListener)
    {
        mStateChangedListener = stateChangedListener;
    }

    /**
     * Compute the RTP timestamp, which is the number of samples since the beginning of the stream. Multiply timestamp by the sampling rate to have a good approximation.
     *
     * @param timestampMS The timestamp in ms of the first sample in the packet.
     * @return The number of samples played before the first sample of the packet, based on the time and the sampling rate.
     */
    protected int getRtpTimestamp(int timestampMS)
    {
        // miliseconds * kilo hertz gives a number of samples
        return (int)(timestampMS * mAudioConfig.getSamplingRate().getValueKHz());
    }


    /**
     * Notifies listener that the SDP Config can now be produced without blocking.
     * Called by the subclasses to indicate that the provider has received enough data and is ready to be used as the source for streaming.
     */
    protected void notifyListenerSdpConfigReady()
    {
        // Unblock SDP config getter
        synchronized (mAudioConfigReadyLock)
        {
            mAudioConfigReady = true;
            mAudioConfigReadyLock.notify();
        }

        if (mStateChangedListener != null)
            mStateChangedListener.onProviderSdpConfigReady();
    }



    /**
     * Notifies listener that an unrecoverable error has been encountered.
     * Called by the subclasses to indicate that the provider has encountered an error that forces it to interrupt its processing.
     */
    protected void notifyListenerError(ErrorDetail errorDetail)
    {
        if (mStateChangedListener != null)
            mStateChangedListener.onProviderError(errorDetail);
    }
}
