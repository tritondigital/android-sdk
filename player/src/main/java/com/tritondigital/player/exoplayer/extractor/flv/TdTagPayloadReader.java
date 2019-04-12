package com.tritondigital.player.exoplayer.extractor.flv;

import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.util.ParsableByteArray;

/**
 * Created by mkabore on 27/10/2016.
 */

/**
 * Extracts individual samples from FLV tags, preserving original order.
 */
abstract class TdTagPayloadReader {

    /**
     * Thrown when the format is not supported.
     */
    public static final class UnsupportedFormatException extends ParserException {

        public UnsupportedFormatException(String msg) {
            super(msg);
        }

    }

    protected final TrackOutput output;

    /**
     * @param output A {@link TrackOutput} to which samples should be written.
     */
    protected TdTagPayloadReader(TrackOutput output) {
        this.output = output;
    }

    /**
     * Notifies the reader that a seek has occurred.
     * <p>
     * Following a call to this method, the data passed to the next invocation of
     * {@link #consume(ParsableByteArray, long)} will not be a continuation of the data that
     * was previously passed. Hence the reader should reset any internal state.
     */
    public abstract void seek();

    /**
     * Consumes payload data.
     *
     * @param data The payload data to consume.
     * @param timeUs The timestamp associated with the payload.
     * @throws ParserException If an error occurs parsing the data.
     */
    public final void consume(ParsableByteArray data, long timeUs) throws ParserException {
        if (parseHeader(data)) {
            parsePayload(data, timeUs);
        }
    }

    /**
     * Parses tag header.
     *
     * @param data Buffer where the tag header is stored.
     * @return Whether the header was parsed successfully.
     * @throws ParserException If an error occurs parsing the header.
     */
    protected abstract boolean parseHeader(ParsableByteArray data) throws ParserException;

    /**
     * Parses tag payload.
     *
     * @param data Buffer where tag payload is stored
     * @param timeUs Time position of the frame
     * @throws ParserException If an error occurs parsing the payload.
     */
    protected abstract void parsePayload(ParsableByteArray data, long timeUs) throws ParserException;
}
