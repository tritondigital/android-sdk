package com.tritondigital.player.exoplayer.extractor.flv;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.ParserException;
import androidx.media3.extractor.TrackOutput;
import androidx.media3.common.MimeTypes;
import androidx.media3.container.NalUnitUtil;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.extractor.AvcConfig;



/**
 * Created by mkabore on 27/10/2016.
 */

/**
 * Parses video tags from an FLV stream and extracts H.264 nal units.
 */
class TdVideoTagPayloadReader  extends TdTagPayloadReader{

    // Video codec.
    private static final int VIDEO_CODEC_AVC = 7;

    // Frame types.
    private static final int VIDEO_FRAME_KEYFRAME = 1;
    private static final int VIDEO_FRAME_VIDEO_INFO = 5;

    // Packet types.
    private static final int AVC_PACKET_TYPE_SEQUENCE_HEADER = 0;
    private static final int AVC_PACKET_TYPE_AVC_NALU = 1;

    // Temporary arrays.
    private final ParsableByteArray nalStartCode;
    private final ParsableByteArray nalLength;
    private int nalUnitLengthFieldLength;

    // State variables.
    private boolean hasOutputFormat;
    private int frameType;

    /**
     * @param output A {@link TrackOutput} to which samples should be written.
     */
    public TdVideoTagPayloadReader(TrackOutput output) {
        super(output);
        nalStartCode = new ParsableByteArray(NalUnitUtil.NAL_START_CODE);
        nalLength = new ParsableByteArray(4);
    }

    @Override
    public void seek() {
        // Do nothing.
    }

    @Override
    protected boolean parseHeader(ParsableByteArray data) throws UnsupportedFormatException {
        int header = data.readUnsignedByte();
        int frameType = (header >> 4) & 0x0F;
        int videoCodec = (header & 0x0F);
        // Support just H.264 encoded content.
        if (videoCodec != VIDEO_CODEC_AVC) {
            throw new UnsupportedFormatException("Video format not supported: " + videoCodec);
        }
        this.frameType = frameType;
        return (frameType != VIDEO_FRAME_VIDEO_INFO);
    }

    @Override
    protected void parsePayload(ParsableByteArray data, long timeUs) throws ParserException {
        int packetType = data.readUnsignedByte();
        int compositionTimeMs = data.readUnsignedInt24();
        timeUs += compositionTimeMs * 1000L;
        // Parse avc sequence header in case this was not done before.
        if (packetType == AVC_PACKET_TYPE_SEQUENCE_HEADER && !hasOutputFormat) {
            ParsableByteArray videoSequence = new ParsableByteArray(new byte[data.bytesLeft()]);
            data.readBytes(videoSequence.getData(), 0, data.bytesLeft());
            AvcConfig avcConfig = AvcConfig.parse(videoSequence);
            nalUnitLengthFieldLength = avcConfig.nalUnitLengthFieldLength;
            // Construct and output the format.
            Format.Builder builder = new Format.Builder()
                    .setContainerMimeType(MimeTypes.VIDEO_H264)
                    .setWidth(avcConfig.width)
                    .setHeight(avcConfig.height)
                    .setInitializationData(avcConfig.initializationData)
                    .setPixelWidthHeightRatio(avcConfig.pixelWidthHeightRatio);

//            Format format = Format.createVideoSampleFormat(null, MimeTypes.VIDEO_H264, null,
//                    Format.NO_VALUE, Format.NO_VALUE, avcConfig.width, avcConfig.height, Format.NO_VALUE,
//                    avcConfig.initializationData, Format.NO_VALUE, avcConfig.pixelWidthAspectRatio, null);
            output.format(builder.build());
            hasOutputFormat = true;
        } else if (packetType == AVC_PACKET_TYPE_AVC_NALU) {
            // TODO: Deduplicate with Mp4Extractor.
            // Zero the top three bytes of the array that we'll use to decode nal unit lengths, in case
            // they're only 1 or 2 bytes long.
            byte[] nalLengthData = nalLength.getData();
            nalLengthData[0] = 0;
            nalLengthData[1] = 0;
            nalLengthData[2] = 0;
            int nalUnitLengthFieldLengthDiff = 4 - nalUnitLengthFieldLength;
            // NAL units are length delimited, but the decoder requires start code delimited units.
            // Loop until we've written the sample to the track output, replacing length delimiters with
            // start codes as we encounter them.
            int bytesWritten = 0;
            int bytesToWrite;
            while (data.bytesLeft() > 0) {
                // Read the NAL length so that we know where we find the next one.
                data.readBytes(nalLength.getData(), nalUnitLengthFieldLengthDiff, nalUnitLengthFieldLength);
                nalLength.setPosition(0);
                bytesToWrite = nalLength.readUnsignedIntToInt();

                // Write a start code for the current NAL unit.
                nalStartCode.setPosition(0);
                output.sampleData(nalStartCode, 4);
                bytesWritten += 4;

                // Write the payload of the NAL unit.
                output.sampleData(data, bytesToWrite);
                bytesWritten += bytesToWrite;
            }
            output.sampleMetadata(timeUs, frameType == VIDEO_FRAME_KEYFRAME ? C.BUFFER_FLAG_KEY_FRAME : 0,
                    bytesWritten, 0, null);
        }
    }

}
