package com.tritondigital.player.exoplayer.extractor.flv;

import androidx.media3.common.C;
import androidx.media3.common.ParserException;
import androidx.media3.extractor.TrackOutput;
import androidx.media3.common.util.ParsableByteArray;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;


/**
 * Created by mkabore on 27/10/2016.
 */

/**
 * Parses Script Data tags from an FLV stream and extracts metadata information.
 */
public final class TdScriptTagPayloadLoader extends TdTagPayloadReader{

    private static final String NAME_CUEPOINT = "onCuePoint";
    private static final String NAME_METADATA = "onMetadata";
    private static final String KEY_DURATION = "duration";

    // AMF object types
    private static final int AMF_TYPE_NUMBER = 0;
    private static final int AMF_TYPE_BOOLEAN = 1;
    private static final int AMF_TYPE_STRING = 2;
    private static final int AMF_TYPE_OBJECT = 3;
    private static final int AMF_TYPE_ECMA_ARRAY = 8;
    private static final int AMF_TYPE_END_MARKER = 9;
    private static final int AMF_TYPE_STRICT_ARRAY = 10;
    private static final int AMF_TYPE_DATE = 11;

    private long durationUs;

    private TdMetaDataListener mMetaDataListener;
    /**
     * @param output A {@link TrackOutput} to which samples should be written.
     */
    public TdScriptTagPayloadLoader(TrackOutput output, TdMetaDataListener listener) {
        super(output);
        durationUs = C.TIME_UNSET;
        mMetaDataListener = listener;
    }

    public long getDurationUs() {
        return durationUs;
    }

    @Override
    public void seek() {
        // Do nothing.
    }

    @Override
    protected boolean parseHeader(ParsableByteArray data) {
        return true;
    }

    @Override
    protected void parsePayload(ParsableByteArray data, long timeUs) throws ParserException {
        int nameType = readAmfType(data);
        if (nameType != AMF_TYPE_STRING) {
            // Should never happen.
            throw ParserException.createForUnsupportedContainerFeature("Wrong Type");
        }
        String name = readAmfString(data);

        int type = readAmfType(data);
        if (type != AMF_TYPE_ECMA_ARRAY) {
            // Should never happen.
            throw ParserException.createForUnsupportedContainerFeature("Wrong Type");
        }

        Map<String, Object> metadata = readAmfEcmaArray(data);

        if (metadata.containsKey(KEY_DURATION)) {
            double durationSeconds = (double) metadata.get(KEY_DURATION);
            if (durationSeconds > 0.0) {
                durationUs = (long) (durationSeconds * C.MICROS_PER_SECOND);
            }
        }

        if(TdMetaDataListener.NAME_METADATA.equalsIgnoreCase(name) ||
                TdMetaDataListener.NAME_CUEPOINT.equalsIgnoreCase(name))
        {
            Map<String, Object> eventObject = new HashMap<>();

            eventObject.put(TdMetaDataListener.KEY_NAME, name);

            // Attach Array with Name to eventObject
            eventObject.put(name,metadata);

            // Add a timeStamp to the object
            eventObject.put(TdMetaDataListener.KEY_TIMESTAMP, timeUs);

            //notify on metadata
            if(mMetaDataListener != null)
            {
                if(timeUs == 0){
                    try{
                        Thread.sleep(100);
                    }catch (InterruptedException ex){ }
                }
                mMetaDataListener.onMetaDataReceived(eventObject);
            }
        }
    }

    private static int readAmfType(ParsableByteArray data) {
        return data.readUnsignedByte();
    }

    /**
     * Read a boolean from an AMF encoded buffer.
     *
     * @param data The buffer from which to read.
     * @return The value read from the buffer.
     */
    private static Boolean readAmfBoolean(ParsableByteArray data) {
        return data.readUnsignedByte() == 1;
    }

    /**
     * Read a double number from an AMF encoded buffer.
     *
     * @param data The buffer from which to read.
     * @return The value read from the buffer.
     */
    private static Double readAmfDouble(ParsableByteArray data) {
        return Double.longBitsToDouble(data.readLong());
    }

    /**
     * Read a string from an AMF encoded buffer.
     *
     * @param data The buffer from which to read.
     * @return The value read from the buffer.
     */
    private static String readAmfString(ParsableByteArray data) {
        int size = data.readUnsignedShort();
        int position = data.getPosition();
        data.skipBytes(size);
        return new String(data.getData(), position, size);
    }

    /**
     * Read an array from an AMF encoded buffer.
     *
     * @param data The buffer from which to read.
     * @return The value read from the buffer.
     */
    private static ArrayList<Object> readAmfStrictArray(ParsableByteArray data) {
        int count = data.readUnsignedIntToInt();
        ArrayList<Object> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int type = readAmfType(data);
            list.add(readAmfData(data, type));
        }
        return list;
    }

    /**
     * Read an object from an AMF encoded buffer.
     *
     * @param data The buffer from which to read.
     * @return The value read from the buffer.
     */
    private static HashMap<String, Object> readAmfObject(ParsableByteArray data) {
        HashMap<String, Object> array = new HashMap<>();
        while (true) {
            String key = readAmfString(data);
            int type = readAmfType(data);
            if (type == AMF_TYPE_END_MARKER) {
                break;
            }
            array.put(key, readAmfData(data, type));
        }
        return array;
    }

    /**
     * Read an ECMA array from an AMF encoded buffer.
     *
     * @param data The buffer from which to read.
     * @return The value read from the buffer.
     */
    private static HashMap<String, Object> readAmfEcmaArray(ParsableByteArray data) {
        int count = data.readUnsignedIntToInt();
        HashMap<String, Object> array = new HashMap<>(count);
        for (int i = 0; i < count; i++) {
            String key = readAmfString(data);
            int type = readAmfType(data);
            array.put(key, readAmfData(data, type));
        }
        return array;
    }

    /**
     * Read a date from an AMF encoded buffer.
     *
     * @param data The buffer from which to read.
     * @return The value read from the buffer.
     */
    private static Date readAmfDate(ParsableByteArray data) {
        Date date = new Date((long) readAmfDouble(data).doubleValue());
        data.skipBytes(2); // Skip reserved bytes.
        return date;
    }

    private static Object readAmfData(ParsableByteArray data, int type) {
        switch (type) {
            case AMF_TYPE_NUMBER:
                return readAmfDouble(data);
            case AMF_TYPE_BOOLEAN:
                return readAmfBoolean(data);
            case AMF_TYPE_STRING:
                return readAmfString(data);
            case AMF_TYPE_OBJECT:
                return readAmfObject(data);
            case AMF_TYPE_ECMA_ARRAY:
                return readAmfEcmaArray(data);
            case AMF_TYPE_STRICT_ARRAY:
                return readAmfStrictArray(data);
            case AMF_TYPE_DATE:
                return readAmfDate(data);
            default:
                return null;
        }
    }
}
