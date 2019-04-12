package com.tritondigital.net.streaming.proxy.dataprovider.raw;

import com.tritondigital.net.streaming.proxy.dataprovider.Packet;

/**
 * <p>Represents the most simple form of a packet, no headers, simply a payload.
 * To be optimal, the size of the payload needs to be known before creating the packet.
 * Otherwise, the packet size is readjusted to be large enough for the payload.
 *
 * This is typically used when receiving packets and transfering to the server without any modification.
 */
public class RawPacket implements Packet
{
    private byte[] mData;
    private int mDataLength;

    /**
     * Constructs a packet with a buffer large enough to contain the given payload size and the header size and fill the header with default values.
     */
    RawPacket(int payloadSize)
    {
        setPayloadSize(payloadSize);
    }


    /**
     * Sets the payload to be wrapped in the packet. Payload is copied after the header.
     * To help reusing buffers, the size of the payload inside the bytes array is passed as a parameter.
     * This allows passing a larger buffer (that would be reused for the generation of multiple packets).
     * Only the good part of this bytes array is copied, not the entire bytes array.
     *
     */
    public void setPayload(byte[] payload, int payloadSize)
    {
        setPayloadSize(payloadSize);
        System.arraycopy(payload, 0, mData, 0, payloadSize);
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
        // Reallocate if necessary
        if (mData == null || mData.length < payloadSize)
        {
            // Reallocate with the new size
            mData = new byte[payloadSize];
        }

        mDataLength = payloadSize;
    }
}
