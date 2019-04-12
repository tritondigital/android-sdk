package com.tritondigital.net.streaming.proxy.dataprovider;

/**
 * Interface for all types of Packets. Contains minimal methods needed to send those packets as an array of bytes.
 *
 * It is important to understand that the bytes array of the packet is only valid from by 0 to byte 'getLength() -1'. To minimise memory allocation,
 * if a packet is reused but has a different payload size, the same buffer is reused. It is essential to use 'getLength' to know the actual size of
 * the packet.
 */
public interface Packet {
    /**
     * Gets the packet raw data.
     * Typically used to send the bytes to a socket after constructing it and setting the various header fields / payload.
     */
    byte[] getData();


    /**
     * Gets the size of the entire packet (payload size + header size)
     */
    int getLength();
}
