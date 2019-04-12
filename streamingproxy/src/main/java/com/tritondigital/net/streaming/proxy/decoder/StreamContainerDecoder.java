package com.tritondigital.net.streaming.proxy.decoder;

import com.tritondigital.net.streaming.proxy.client.Client;
import com.tritondigital.net.streaming.proxy.utils.Log;
import com.tritondigital.net.streaming.proxy.utils.QueueInputStream;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;


/**
 * <p>Base class for the class that decoded a stream transport protocol such as Flv or ShoutCast.
 * The purpose of this class is to take the input data from a connection (through the Client.DataReceivedListener
 * interface), extracts the Metadata and AudioData and send it to a listener.
 *
 * <p>The listener receives the audio data itself, ummodified, and the parameters required to use this data
 * such as the media type or sample rate, which is extracted from the stream container.
 * It can also receive the MetaData contained in the stream, if any.
 *
 * <p>The decoding is done in a background thread. Whenever new data is received, the onDataReceived is invoked on the
 * StreamContainerDeccoder instance, the data is sent to the decoding thread, where is is parsed (the thread might block
 * if the received data is not a complete chunk / tag). The decoding thread starts when the StreamContainerDeccoder instance
 * is created, but is blocked until data arrives (onDataReceived is called)
 *
 * <p>The audio chunks are sent along with their timestamp when they are decoded, to allow the player to buffet them. The player
 * is responsible for player the audio samples at the good time. On the other hand, Metadata listener is notified only when the
 * metadata should be interpreted. This means that if some metadata are received but they reference a date in the future, the
 * metadata listener will only be notified when this future date is reached.
 *
 * <p>If MetaData need to be delayed, in order to make sure that they are processes synchronously with the Audio that is played,
 * a scheduler thread is created. Therefore, this module uses 2 threads, one for scheduling the Metadata processing and one
 * to do the actual decoding.
 */
@SuppressWarnings("ALL")
public abstract class StreamContainerDecoder implements Client.DataReceivedListener
{
    public final String TAG = "StreamContainerDecoder";

    /**
     * The size of the internal buffer that stores the incoming Http chunks until they are read by the decoder.
     * Experience showed that with the current implementations (especially the current Http Client), chunks are around 8 kB, so
     * a buffer that has least 16 kB (2 chunks) seems adequate.
     */
    private static final int QUEUE_BUFFER_SIZE = 16 * 1024;

    private MetaDataDecodedListener  mMetaDataDecodedListener;
    private AudioDataDecodedListener mAudioDataDecodedListener;

    /** Stream where new received bytes are enqueued, read by the decoder. Blocks when full and trying to add new data and when trying to read if empty. */
    protected volatile QueueInputStream   mInputStreamForDecodingThread;

    /** The timestamp of the machine at the moment when the decoding starts. Used to know exactly when to send the Metadata to the listener. */
    protected long mReferenceTimestamp;

    /** Used to delay calls like the MetataReceived calls that are only sent when then associated Audio is playing. */
    ScheduledExecutorService mDelayedCallsScheduler;

    /** Used to indicate if the decoding is in progressed or stopped. */
    private boolean mDecoding;

    /**
     * Interface definition for a callback to be invoked when new metadata is decoded from the stream.
     * Methods on this listener may be invoked from a secondary thread. The class implementing this listener
     * should be thread-safe.
     */
    public interface MetaDataDecodedListener
    {
        /**
         *  Meta data has been decoded from the stream.
         *
         * @param metaData The map containing the MetaData, exactly as extracted from the stream. The listener is responsible for knowing of to interpret it.
         */
        void onMetaDataDecoded(Map<String, Object> metaData);
    }


    /**
     * Interface definition for a callback to be invoked when new metadata is decoded from the stream.
     * Methods on this listener may be invoked from a secondary thread. The class implementing this listener
     * should be thread-safe.
     */
    public interface AudioDataDecodedListener
    {
        /**
         * <p>All parameters in the stream (typically in some headers or in the first packet(s) received after
         * the connection is established) have been decoded. Those parameters are passed by the source of the stream
         * to indicate how to use the stream data.
         *
         * <p>This method is called prior to the first call to onAudioDataDecoded, unless the stream transport protocol
         * does not include any parameters.
         */
        void onAudioConfigDecoded(AudioConfig audioConfig);

        /**
         * Audio data packet has been decoded from the stream. Listener should use the AudioConfig received in the call to
         * onAudioConfigDecoded received before to interpret the audio data correctly.
         *
         * @param audioData       The chunk of data exactly as extracted from the stream (this is a complete chunk that was present in a single packet of the stream container).
         *                        The buffer audioData might be reused later on, so it is the responsibility of the listener to copy it if it needs to keep it for long term.
         * @param audioDataLength The length of the chunk in the audioData bytes array. To minimise the allocation and copy of memory, the decoding buffer might
         *                        be reused to store less data than the previous buffer.
         * @param timestamp       The time stamp at which the data should be played. Typically extracted from the header of the packet containing the audio data.
         *                        Time stamp is in milliseconds, stating at 0 for the first packet.
         */
        void onAudioDataDecoded(byte[] audioData, int audioDataLength, int timestamp);
    }


    /**
     * Creates the internal streams used to transfer the new data received from the socket to
     * a stream that can be used in blocking reading. This allows the decoder to read and block until
     * the correct number of bytes is available. Blocking is passive to allow other threads to run.
     */
    protected StreamContainerDecoder()
    {
    }


    @Override
    public boolean onDataReceived(byte[] data, int dataLength)
    {
        if (mInputStreamForDecodingThread == null)
            return false;

        mInputStreamForDecodingThread.put(data, dataLength);
        return true;
    }


    /**
     * Sets the listener to be notified when new meta data is decoded
     */
    public void setMetaDataDecodedListener(MetaDataDecodedListener metaDataDecodedListener)
    {
        mMetaDataDecodedListener = metaDataDecodedListener;
    }


    /**
     * Notifies the MetaDataListener that a MetaData was decoded.
     * Note that this does not directly notify the listener. Instead, it waits for the timestamp to
     * be reached before notifying. Timestamps here are is the same unit and have the same start than
     * the audio data.
     */
    protected void notifyMetaDataDecoded(final Map<String, Object> metaData, int timestamp)
    {
        int nowTimeStamp = (int)(System.currentTimeMillis() - mReferenceTimestamp);
        int delay = timestamp - nowTimeStamp;
        if (delay <= 0)
        {
            if (mMetaDataDecodedListener != null)
                mMetaDataDecodedListener.onMetaDataDecoded(metaData);
        }
        else
        {
            Runnable task = new Runnable()
            {
                @Override
                public void run()
                {
                    if (mDecoding && (mMetaDataDecodedListener != null))
                    {
                        mMetaDataDecodedListener.onMetaDataDecoded(metaData);
                    }
                }
            };

            mDelayedCallsScheduler.schedule(task, delay, TimeUnit.MILLISECONDS);
        }
    }


    /**
     * Sets the listener to be notified when new audio data is decoded
     */
    public void setAudioDataDecodedListener(AudioDataDecodedListener audioDataDecodedListener)
    {
        mAudioDataDecodedListener = audioDataDecodedListener;
    }


    /**
     * Notifies the AudioDataListener that the AudioConfig was decoded.
     */
    protected void notifyAudioConfigDecoded(AudioConfig audioConfig)
    {
        if (mAudioDataDecodedListener != null)
            mAudioDataDecodedListener.onAudioConfigDecoded(audioConfig);
    }


    /**
     * Notifies the AudioDataListener that an AudioData was decoded.
     */
    protected void notifyAudioDataDecoded(byte[] audioData, int audioDataLength, int timestamp)
    {
        // Send the Audio Data. Any data received before the Config is sent is ignored.
        if (mAudioDataDecodedListener != null)
            mAudioDataDecodedListener.onAudioDataDecoded(audioData, audioDataLength, timestamp);
    }


    /**
     * Creates and InputStream and starts a thread that decodes it and does the actual job of extracting the audio data and parameters and the meta data.
     * Typically, this thread loops until the streaming stops, it reads chunk by chunk from the stream using DataInputStream.readFully to block until
     * a chunk can be read entirely, and notifies its listener about each decoded chunk.
     */
    public void startDecodingInBackground()
    {
        mDecoding = true;
        mDelayedCallsScheduler = Executors.newSingleThreadScheduledExecutor(mThreadFactory);

        // This is used as the source of reading in the decoding thread, and blocks if there is no data.
        mInputStreamForDecodingThread = new QueueInputStream(QUEUE_BUFFER_SIZE);

        setReferenceTimestampToNow();
        startDecodingThread();
    }


    /**
     * Starts the thread that does the decoding.
     */
    protected abstract void startDecodingThread();


    /**
     * Stops the decoding thread and close the streams.
     */
    public void stop()
    {
        mDecoding = false;

        if (mDelayedCallsScheduler != null)
        {
            mDelayedCallsScheduler.shutdownNow();
        }

        try
        {
            if (mInputStreamForDecodingThread != null)
            {
                Log.i(TAG, "Stopping");
                mInputStreamForDecodingThread.close();
                mInputStreamForDecodingThread = null;
            }
        }
        catch (Exception e)
        {
            // Empty
        }

        stopDecodingThread();
    }


    /**
     * Stops decoding the input stream passed in startDecodingInBackground. Interrupts the background thread that does the decoding itself
     * and free resources that are no longer needed.
     */
    public abstract void stopDecodingThread();


    /**
     * <p>Affects the reference timestamp used to delay the sending of the Metadata until the metadata timestamp has been reached.
     *
     * <p>The stream is often sent by burst instead of in real time. This means that when the Metadata is received, it may reference
     * a time in the future. To make sure that the Metadata is only treated when it is supposed to, its timestamp is used (each
     * Metadata has a timestamp associated to, sent by the server when it sends the Metadata) to create a timer that will notify
     * the listener after the good amount of time. Concretely, this means that the metadata is treated approximately when the audio
     * data tag next to it it played, since Metadata and Audio share the same timestamps reference.
     *
     * <p>For this technique to work, it is important to consider that time 0 is when the audio data actually starts. The setReferenceTimestampToNow
     * method needs to be called when when the audio actually starts playing.
     */
    public void setReferenceTimestampToNow()
    {
        mReferenceTimestamp = System.currentTimeMillis();
    }

    /**
     * Used to create and name the threads for the delayed call to process Metadata
     */
    private ThreadFactory mThreadFactory = new ThreadFactory()
    {
        private int mThreadCount = 0;

        @Override
        public Thread newThread(Runnable runnable)
        {
            return new Thread(runnable, "StreamingProxy " + TAG + " delayedMetadataThread-" + mThreadCount++);
        }
    };
}
