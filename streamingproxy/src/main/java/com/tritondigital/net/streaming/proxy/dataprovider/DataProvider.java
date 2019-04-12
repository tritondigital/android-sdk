package com.tritondigital.net.streaming.proxy.dataprovider;

import com.tritondigital.net.streaming.proxy.decoder.StreamContainerDecoder;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Base class for different kind of Data packets provider.
 *
 * This class implements the StreamContainerDeccoder.AudioDataDecodedListener interface, it is notified as soon as new data is ready
 * to be packetized, then is creates a new packet from this data and enqueues it. It maintains an internal queue of packets to be able
 * to create multiple packets and keep them until the server transmits them. This queue is thread-safe and has a fixed size. Trying
 * to add new packets when it is full will block the thread, which might cause some packets to drop on the StreamContainerDeccoder side.
 * Trying to get a packet when queue is empty will also block the calling thread until a new packet is available.
 *
 * Ideally, the server will consume / transmit the packets at the same rate as they are reveived from the StreamContainerDeccoder, to
 * avoid buffer underrun (queue is empty) or overrun (queue is full), which is both case will block a thread.
 *
 * A subclass that wants to be efficient should never create Packets directly. Instead, it should request a free packet
 * using getFreePacket, which will take a packet from the free packets pool or create a new one if necessary. Similarily, the
 * class that takes the packets using getPacket should put back the packet in the free packets pool for reuse when it is done
 * with it by calling addFreePacketToPool.
 */
public abstract class DataProvider implements StreamContainerDecoder.AudioDataDecodedListener {
    /*
     * <p>Maximum waiting time when polling a packet from the queue. Returns null when expired.
     */
    private static final int PACKETS_QUEUE_TIMEOUT = 10; // In seconds

    /**
     *Maximum count of packets received by the client and not yet transfered to the server that the queue can contain.
     * Adding packets when the queue is full will block, which will eventually lead to packets being dropped and possibly
     * a disconnection.
     *
     *Note that the current container uses an Array, so this is not only the maximum number of packets, it is actually the real
     * amount of memory that is reserved. Also, since packets are reused, the reserved amount of memory is twice this number, the queue
     * of packets ready reserves enough memory for this count of packets, and the free packets pool too.
     */
    private static final int QUEUE_SIZE = 1024;

    /**
     * List of all packets that were received from the client and not sent to the server yet.
     * This list gets bigger when a burst is received and when the client is connected but not the server.
     * On the contrary, it gets smaller if the client disconnects or if the conversion of the data received
     * to destination packet format (e.g. RTP) takes more than the server's expected bitrate.
     */
    protected final ArrayBlockingQueue<Packet> mPacketsQueue = new ArrayBlockingQueue<>(QUEUE_SIZE);
    protected final ArrayBlockingQueue<Packet> mFreePacketsPool = new ArrayBlockingQueue<>(QUEUE_SIZE);


    /**
     * Interrupts any operation and clear the packets queue.
     * Clears the internal buffer at the same time.
     */
    public void stop() {
        clearInternalBuffer();
    }


    /**
     * Returns the next Packet in the stream. If none are available, blocks until new data arrives and is properly wrapped in the appropriate packet format (e.g. RTP).
     * Returns null if no packets after a given timeout.
     */
    public Packet getPacket() {
        Packet packet = null;
        try {
            packet = mPacketsQueue.poll(PACKETS_QUEUE_TIMEOUT, TimeUnit.SECONDS);
            //Log.e(TAG, "Queue contains " + mPacketsQueue.size() + " packets");
        } catch (InterruptedException e) {
            // Ignored
        }
        return packet;
    }


    /**
     * Puts the given packet back in the free pool packets.
     */
    public void addFreePacketToPool(Packet packet) {
        try {
            if (packet != null) {
                mFreePacketsPool.offer(packet, PACKETS_QUEUE_TIMEOUT, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            // Ignored
        }
    }


    /**
     * Returns a free packet to use for the creation of a new packet. Creates a new one if none are available.
     */
    protected Packet getFreePacket() {
        Packet packet = mFreePacketsPool.poll();

        if (packet == null)
            packet = createEmptyPacket();

        return packet;
    }


    /**
     * Enqueue a Packet to be sent to the server. Blocks if the queue is full.
     */
    protected void enqueuePacket(Packet packet) {
        try {
            mPacketsQueue.offer(packet, PACKETS_QUEUE_TIMEOUT, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            // Ignored
        }
    }

    /**
     * If the provider keeps a buffer of already available packets, clear it immediately.
     * Typically invoked before the proxy starts the client, to remove the old data that could have been
     * still present in the buffer.
     */
    public void clearInternalBuffer() {
        while (!mPacketsQueue.isEmpty()) {
            addFreePacketToPool(mPacketsQueue.poll());
        }
    }


    /**
     * Create an empty packet of the expected type by the subclass.
     */
    protected abstract Packet createEmptyPacket();
}
