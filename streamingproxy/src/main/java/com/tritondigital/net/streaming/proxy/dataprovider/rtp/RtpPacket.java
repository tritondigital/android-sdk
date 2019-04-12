package com.tritondigital.net.streaming.proxy.dataprovider.rtp;

import com.tritondigital.net.streaming.proxy.dataprovider.Packet;

/**
 * <p>Helps creating a RTP packet.
 * The size of the payload needs to be known before creating the packet. Then, each field of the header needs to be set. Each field of
 * the header has a default value that should be appropriate for most cases. The documentation of each field setter tells the default value
 * for this field.
 *
 * <p>The packet is constructed according to the RFC 3550 (http://www.ietf.org/rfc/rfc3550.txt)
 */
public class RtpPacket implements Packet
{
    /** Random payload type, in the range of the dynamic payload type of SDP (RFC 2327, http://www.ietf.org/rfc/rfc2327.txt) */
    public static final int PAYLOAD_TYPE = 97;

    /** Size of RTP header. Not using any extension / CCRS => fixed size */
    public static final int HEADER_SIZE = 12;

    private byte[] mData;
    private static final byte[] sDefaultHeader;
    private int mDataLength;

    /**
     * Static initialiser, create the default header
     */
    static
    {
        // Create a packet with 0 payload,just to have the header
        RtpPacket rtpPacket = new RtpPacket(0, false);

        // Set the default values
        rtpPacket.setVersion(2);
        rtpPacket.setPadding(false);
        rtpPacket.setExtension(false);
        rtpPacket.setCC(0);
        rtpPacket.setMarker(true);
        rtpPacket.setPayloadType(PAYLOAD_TYPE);
        rtpPacket.setSequenceNumber((short) 0);
        rtpPacket.setTimeStamp(0);
        rtpPacket.setSsrc(0);

        // Copy the value of the packet (which only contains a header) to the static default header
        sDefaultHeader = new byte[HEADER_SIZE];
        System.arraycopy(rtpPacket.getData(), 0, sDefaultHeader, 0, HEADER_SIZE);
    }


    /**
     * Constructs a packet with a buffer large enough to contain the given payload size and the header size and fill the header with default values.
     */
    RtpPacket(int payloadSize)
    {
        this(payloadSize, true);
    }


    /**
     * <p>Constructs a packet with a buffer large enough to contain the given payload size and the header size and fill the header with default values if needed.
     *
     * <p><b>Note</b>: The payload size passed as a parameter is a hint to preallocate enough memory. However, setting a bigger payload after this will not result in
     * an error, it will simply reallocate the buffer and copy the header.
     *
     * @param payloadSize   Size of the payload that is to be wrapped in the packet.
     * @param prefillHeader  {@code true} to have default header, {@code false} to have header initialized to null.
     */
    RtpPacket(int payloadSize, boolean prefillHeader)
    {
        setPayloadSize(payloadSize);

        if (prefillHeader)
            System.arraycopy(sDefaultHeader, 0, mData, 0, HEADER_SIZE);
    }


    /**
     * Sets the field that identifies the version of RTP (2 bits).
     * <p>Default value: <b>0x2</b>
     */
    public void setVersion(int version)
    {
        mData[0] = (byte) (mData[0] & 0x3f | (version << 6));
    }


    /**
     * Sets the field that indicates if the packet contains one or more additional padding bytes at the end
     * which are not part of the payload (1 bit)
     * <p>Default value: {@code false} (<b>0x0</b>)
     */
    public void setPadding(boolean padding)
    {
        mData[0] = (byte) (mData[0] & 0xdf | (padding ? 1 << 5: 0));
    }


    /**
     * Sets the field that indicates if there is an extension following the header (1 bit)
     * <p>Default value: {@code false} (<b>0x0</b>)
     */
    public void setExtension(boolean extension)
    {
        mData[0] = (byte) (mData[0] & 0xef | (extension ? 1 << 4: 0));
    }


    /**
     * Sets the fiels that gives the number of CSRC identifiers that follow the fixed header (4 bits).
     * <p>Default value: <b>0x0</b>
     */
    public void setCC(int cc)
    {
        mData[0] = (byte) (mData[0] & 0xf0 | cc);
    }


    /**
     * Set the marker bit that indicates the frame boundaries (1 bit)
     * The interpretation of the marker is defined by a profile.
     * For the supported profiles, {@code true} if packet contains the end of the audio element (e.g. end of audioMuxElement in mp4-latm), {@code false} otherwise.
     * <p>Default value: {@code false} (<b>0x0</b>)
     */
    public void setMarker(boolean marker)
    {
        mData[1] = (byte) (mData[1] & 0x80 | (marker ? 1 << 7: 0));
    }


    /**
     * Set payload type (7 bits)
     * Typically a number in the SDP dynamic profile range, the value does not matter as long as it is used consistently in the rest of the RTSP negotiation process (rtpmap).
     * <p>Default value: PAYLOAD_TYPE (<b>0x61 - 97</b>)
     */
    public void setPayloadType(int payloadType)
    {
        mData[1] = (byte) (mData[1] & 0x480 | payloadType);
    }


    /**
     * Set the sequence number type (16 bits)
     * Increments by 1 for each packet in the same stream.
     * <p>Default value: <b>0x0</b>
     */
    public void setSequenceNumber(short sequenceNumber)
    {
        mData[2] = (byte)(sequenceNumber >> 8);
        mData[3] = (byte)(sequenceNumber & 0xFF);
    }


    /**
     * Get the sequence number type
     */
    public short getSequenceNumber()
    {
        return (short) (((mData[2] & 0xFF) << 8) | (mData[3] & 0xFF));
    }


    /**
     * Set the time stamp type (32 bits)
     * Typically the number of samples between the first packet and the first sample of this one (could be approximated by 'time since beginning of the stream' * 'sample rate').
     * <p>Default value: <b>0x0</b>
     */
    public void setTimeStamp(int timestamp)
    {
        mData[4] = (byte)(timestamp >> 24);
        mData[5] = (byte)(timestamp >> 16);
        mData[6] = (byte)(timestamp >> 8);
        mData[7] = (byte)(timestamp & 0xFF);
    }


    /**
     * Get the sequence number type
     */
    public int getTimeStamp()
    {
        return (short) (((mData[4] & 0xFF) << 24)  | ((mData[5] & 0xFF) << 16) | ((mData[6] & 0xFF) << 8) | (mData[7] & 0xFF));
    }


    /**
     * Set synchronization sources (32 bits)
     * Not used in this type of application where there is only one media (audio), one source and no synchronisation.
     * <p>Default value:<b> 0x0</b>
     */
    public void setSsrc(int ssrc)
    {
        mData[8] = (byte)(ssrc >> 24);
        mData[9] = (byte)(ssrc >> 16);
        mData[10] = (byte)(ssrc >> 8);
        mData[11] = (byte)(ssrc & 0xFF);
    }


    /**
     * Gets the packet raw data.
     * Typically used to send the bytes to a socket after constructing it and setting the various header fields / payload.
     */
    public byte[] getData()
    {
        return mData;
    }


    /**
     * Gets the size of the entire packet (payload size + header size)
     */
    public int getLength()
    {
        return mDataLength;
    }


    /**
     * <p>Sets the payload size. If the current buffer is not large enough, it is reallocated and the header is copied
     * to the new buffer.
     *
     * <p>No assumption can be made regarding the previous payload after a call to this method. It may still be present
     * if no reallocation was necessary, and it may be lost if there was a reallocation (as only the header is copied).
     * It is assumed that changing the size of the payload implicitly implies changing the payload itself.
     */
    public void setPayloadSize(int payloadSize)
    {
        final int newDataLength = HEADER_SIZE + payloadSize;

        // Reallocate if necessary
        if (mData == null || mData.length < newDataLength)
        {
            // Keep the old data to copy the header
            final byte[] oldData = mData;

            // Reallocate with the new size
            mData = new byte[newDataLength];

            // Copy the header from the previous data
            if (oldData != null)
                System.arraycopy(oldData, 0, mData, 0, HEADER_SIZE);
        }

        mDataLength = newDataLength;
    }
}
