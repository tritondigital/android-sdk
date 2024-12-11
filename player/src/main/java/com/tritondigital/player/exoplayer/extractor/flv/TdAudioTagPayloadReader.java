package com.tritondigital.player.exoplayer.extractor.flv;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.ParserException;
import androidx.media3.extractor.AacUtil;
import androidx.media3.extractor.MpegAudioUtil;
import androidx.media3.extractor.TrackOutput;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.ParsableByteArray;
import java.util.Collections;

/**
 * Created by mkabore on 27/10/2016.
 */

/**
 * Parses audio tags from an FLV stream and extracts AAC frames.
 */
final class TdAudioTagPayloadReader extends TdTagPayloadReader{


    // Audio format
    private static final int AUDIO_FORMAT_AAC = 10;
    private static final int AUDIO_FORMAT_MP3 = 2;

    // AAC PACKET TYPE
    private static final int AAC_PACKET_TYPE_SEQUENCE_HEADER = 0;
    private static final int AAC_PACKET_TYPE_AAC_RAW = 1;

    // SAMPLING RATES
    private static final int[] AUDIO_SAMPLING_RATE_TABLE = new int[] {
            5500, 11000, 22000, 44000
    };

    // SAMPLING RATES FOR MP3
    private static final int[] AUDIO_MP3_SAMPLING_RATE_TABLE = new int[] {
        8000, 11025, 12000, 16000, 22050, 24000, 32000, 44100, 48000 };

    // State variables
    private boolean hasParsedAudioDataHeader;
    private boolean hasOutputFormat;


    // Needed for MP3 support
    private boolean isMP3;
    private int     sampleRateIndex;
    private int     channels;

    private MpegAudioUtil.Header mMpegAudioHeader;

    public TdAudioTagPayloadReader(TrackOutput output) {
        super(output);
    }

    @Override
    public void seek() {
        // Do nothing.
    }

    @Override
    protected boolean parseHeader(ParsableByteArray data) throws UnsupportedFormatException {
        if (!hasParsedAudioDataHeader) {
            int header = data.readUnsignedByte();
            int audioFormat = (header >> 4) & 0x0F;
            sampleRateIndex = (header >> 2) & 0x03;
            channels        = (header & 0x01) + 1;
            if (sampleRateIndex < 0 || sampleRateIndex >= AUDIO_SAMPLING_RATE_TABLE.length) {
                throw new UnsupportedFormatException("Invalid sample rate index: " + sampleRateIndex);
            }
            // TODO: Add support for PCM.
            if ( (audioFormat != AUDIO_FORMAT_AAC) && (audioFormat!= AUDIO_FORMAT_MP3) ) {
                throw new UnsupportedFormatException("Audio format not supported: " + audioFormat);
            }

            if(audioFormat == AUDIO_FORMAT_MP3)
            {
                isMP3 = true;
                mMpegAudioHeader = new MpegAudioUtil.Header();
                mMpegAudioHeader.setForHeaderData(header);
            }

            hasParsedAudioDataHeader = true;
        } else {
            // Skip header if it was parsed previously.
            data.skipBytes(1);
        }
        return true;
    }

    @Override
    protected void parsePayload(ParsableByteArray data, long timeUs) throws ParserException {
        if (!isMP3) {
            int packetType = data.readUnsignedByte();
            // Parse sequence header just in case it was not done before.
            if (packetType == AAC_PACKET_TYPE_SEQUENCE_HEADER && !hasOutputFormat) {
                byte[] audioSpecifiConfig = new byte[data.bytesLeft()];
                data.readBytes(audioSpecifiConfig, 0, audioSpecifiConfig.length);
                AacUtil.Config config = AacUtil.parseAudioSpecificConfig(audioSpecifiConfig);


                Format format = new Format.Builder().setId(null)
                        .setLanguage(null)
                        .setSelectionFlags(0)
                        .setAverageBitrate(Format.NO_VALUE)
                        .setPeakBitrate(Format.NO_VALUE)
                        .setCodecs(null)
                        .setSampleMimeType(MimeTypes.AUDIO_AAC)
                        .setMaxInputSize(Format.NO_VALUE)
                        .setInitializationData(Collections.singletonList(audioSpecifiConfig))
                        .setDrmInitData(null)
                        .setChannelCount(config.channelCount)
                        .setSampleRate(config.sampleRateHz)
                        .build();

                output.format(format);
                hasOutputFormat = true;
            } else if (packetType == AAC_PACKET_TYPE_AAC_RAW) {
                // Sample audio AAC frames
                int bytesToWrite = data.bytesLeft();
                output.sampleData(data, bytesToWrite);
                output.sampleMetadata(timeUs, C.BUFFER_FLAG_KEY_FRAME, bytesToWrite, 0, null);
            }
        }
        else
        {
            if ( !hasOutputFormat )
            {

                // It's an MP3, set the media format once.
                Format format = new Format.Builder().setId(null)
                        .setLanguage(null)
                        .setSelectionFlags(0)
                        .setAverageBitrate(Format.NO_VALUE)
                        .setPeakBitrate(Format.NO_VALUE)
                        .setCodecs("MP3")
                        .setSampleMimeType(MimeTypes.AUDIO_MPEG)
                        .setMaxInputSize(Format.NO_VALUE)
                        .setInitializationData(Collections.EMPTY_LIST)
                        .setDrmInitData(null)
                        .setChannelCount(channels)
                        .setSampleRate(AUDIO_MP3_SAMPLING_RATE_TABLE[sampleRateIndex])
                        .build();

                output.format(format);

                hasOutputFormat = true;
            }

            // Sample audio MP3 frames
            int bytesToWrite = data.bytesLeft();
            output.sampleData(data, bytesToWrite);
            output.sampleMetadata(timeUs, C.BUFFER_FLAG_KEY_FRAME, bytesToWrite, 0, null);
        }
    }
}
