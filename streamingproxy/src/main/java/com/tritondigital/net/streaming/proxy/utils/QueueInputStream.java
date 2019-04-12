package com.tritondigital.net.streaming.proxy.utils;

import java.io.IOException;
import java.io.InputStream;


/**
 * <p>Replacement for the PpipedInputStream / PipedOutputStream pair for the decoder.
 *
 * <p>On some platforms such as Android Api Level < 9, PipedInputStream are slow and have an unsettable buffer size.
 *
 *  <p>This InputStream has the same functionalities:
 *      <ol>
 *      <li>Add data to the stream (acts like an OutputStream converted to an InputStream).</li>
 *      <li>Thread safe, can write from and thread and read from an y thread. The thread will however block if there is an operation already in progress.</li>
 *      <li>Blocks when trying to read from an empty stream or add to a full stream.</li>
 *      <li>Uses an internal circular buffer for good performances an no allocations.</li>
 *      </ol>
 *
 * <p>It has the minimal code to do this, it may not be very scalable for now but it has everything needed for the client to put data in the queue and for
 * the decoder to read it efficiently, with passive wait when the client cannot put more data because the queue is full or when the decoder cannot read
 * data because the queue is empty. It has been tester with 2 threads and both a small 64B and a large 16kB buffers.
 *
 */
public class QueueInputStream extends InputStream
{
    /** The circular buffer where all data is stored. */
    private final byte[] mBuffer;

    /** The index of the start of the data (including the first byte to be read), used when reading. */
    private int mStartIdx = -1;

    /** The index of the end of the data (right after the last byte of the data), used when adding new data. This is actually the index where the first byte of the new data is set */
    private int mEndIdx = 0;

    /** Tells if the stream is opened or closed. Used to quit writing and reading early if not open. */
    private boolean mOpened = false;

    /**
     * Constructor, set the size of the circular buffer.
     */
    public QueueInputStream(int bufferSize)
    {
        mBuffer = new byte[bufferSize];

        mOpened = true;
    }


    /**
     * Adds new data at the end of the buffer.
     * Convenience method to add all the data from newBuf.
     */
    public void put(byte[] newBuf)
    {
        put(newBuf, newBuf.length);
    }


    /**
     * <p>Adds new data at the end of the buffer.
     * Adds the given length of the given buffer.
     * If there is not enough room for the entire new data, the maximum data is written to the circular buffer, then the thread blocks until
     * there is some data that is read from the stream, which would free room for additional bytes of the new data.
     *
     * <p>Returns when all the new data has been written to the buffer, which may mean multiple blocks if the consumer is slower or if the new
     * data is significantly larger than internal circular buffer.
     *
     * <p>If there is a thread blocked in reading, it is unblocked (only one thread is unblocked at a time).
     */
    public void put(byte[] newBuf, int newBufLen)
    {
        // This method loops (an potentially blocks) until all the new data has been added to the buffer.
        // The offset in the new data is updated at each iteration and is actually the count of the new bytes that were added to the buffer.
        int off = 0;
        while (off < newBufLen)
        {
            // Block if queue full.
            synchronized (this)
            {
                try
                {
                    while (mOpened && mStartIdx == mEndIdx)
                    {
                        wait();
                    }
                }
                catch (InterruptedException e)
                {
                    return;
                }

                // Return error on closed stream
                if (!mOpened)
                    return;

                // Copy the maximum data that can be inserted between the current end position and the end of the buffer of the beginning of the data,
                // depending where start and end are located on the circular buffer.
                int copyLength = newBufLen - off;

                // Used to set the start index if it was negative (queue was empty).
                // If the queue was empty, start reading from the index where the new data is added.
                int previousEndIdx = mEndIdx;

                // Determine which is the limit for determining the maximum data that cvan be inserted, based on start and end positions.
                int limit = mEndIdx > mStartIdx ? mBuffer.length : mStartIdx;

                // Determine maximum size that can be added to the buffer
                if (mEndIdx + copyLength >= limit)
                    copyLength = limit - mEndIdx;

                // Copy the data
                System.arraycopy(newBuf, off, mBuffer, mEndIdx, copyLength);
                mEndIdx += copyLength;

                // Wrap if the end index has reached the end of the buffer (cannot be greater than the buffer length because the size to copy was capped)
                if (mEndIdx == mBuffer.length)
                    mEndIdx = 0;

                // If the stream was empty, set the new position for reading and unblock the thread.
                if (mStartIdx < 0)
                {
                    mStartIdx = previousEndIdx;
                    notify();
                }

                // Next iteration will insert the next bytes.
                off += copyLength;
            }
        }
    }


    /**
     * <p>Reads a single byte of data from the beginning of the buffer.
     * If the stream is empty, the thread blocks until new data is added to the queue, then the byte is read.
     *
     * <p>If there is a thread blocked in adding new data to a full queue, it is unblocked (only one thread is unblocked at a time).
     */
    @Override
    public int read() throws IOException
    {
        // Block if queue empty
        synchronized (this)
        {
            try
            {
                while (mOpened && mStartIdx < 0)
                {
                    wait();
                }
            }
            catch (InterruptedException e)
            {
                return -1;
            }

            // Return error on closed stream
            if (!mOpened)
                return -1;

            // Read one byte and increment the start pointer
            byte readByte = mBuffer[mStartIdx];
            mStartIdx++;

            // Wrap if read the last byte of the buffer
            if (mStartIdx == mBuffer.length)
                mStartIdx = 0;

            // Read the entire stream?
            if (mStartIdx == mEndIdx)
                mStartIdx = -1;

            notify();

            return readByte;
        }
    }


    /**
     * <p>Reads data from the beginning of the buffer.
     * If there is less data than requested, the maximum data is read from the circular buffer and this count is returned.
     * If the stream is empty, the thread blocks until new data is added to the queue, then the minimum count between the
     * newly added data and the requested size is read.
     *
     * <p>If there is a thread blocked in adding new data to a full queue, it is unblocked (only one thread is unblocked at a time).
     *
     * @return The number of bytes read or -1 on error.
     */
    @SuppressWarnings("NullableProblems")
    @Override
    public int read(byte[] b, int off, int len) throws IOException
    {
        // Block if queue is empty
        synchronized (this)
        {
            try
            {
                while (mOpened && mStartIdx < 0)
                {
                    wait();
                }
            }
            catch (InterruptedException e)
            {
                return -1;
            }

            // Return error on closed stream
            if (!mOpened)
                return -1;

            // Read in 1 or 2 operations:
            // 1 - Read From start index to end index
            // 2 - Read From start index to end of buffer and from 0 to end index

            // Choose where to stop reading for the first sequence (end of buffer or end index)
            int limit = mEndIdx > mStartIdx ? mEndIdx : mBuffer.length;

            // Truncate the read length if the limit is lower than the requested size
            int copyLength = len;
            if (mStartIdx + copyLength >= limit)
                copyLength = limit - mStartIdx;

            // Read this part
            System.arraycopy(mBuffer, mStartIdx, b, off, copyLength);
            mStartIdx += copyLength;

            // If there is more to read (will only happen if bytes were read from start index to end of buffer, case 2 explained above), start from 0
            // and copy the remaining bytes
            int remainingLength = len - copyLength;
            int remainingCopyLength = 0;
            if (remainingLength > 0 && mStartIdx == mBuffer.length)
            {
                // Start reading at index 0
                mStartIdx = 0;

                // Truncate if the remaining bytes are still not available
                remainingCopyLength = remainingLength;
                if (mStartIdx + remainingCopyLength >= mEndIdx)
                    remainingCopyLength = mEndIdx - mStartIdx;

                // Do the reading
                System.arraycopy(mBuffer, mStartIdx, b, off + copyLength, remainingCopyLength);
                mStartIdx += remainingCopyLength;
            }

            // Wrap if read the last byte of the buffer
            if (mStartIdx == mBuffer.length)
                mStartIdx = 0;

            // Read the entire stream?
            if (mStartIdx == mEndIdx)
                mStartIdx = -1;

            notify();

            return copyLength + remainingCopyLength;
        }
    }


    @Override
    public void close()
    {
        synchronized (this)
        {
            mStartIdx = -1;
            mEndIdx = 0;

            mOpened = false;

            //Release any waiting thread
            notifyAll();
        }
    }
}
