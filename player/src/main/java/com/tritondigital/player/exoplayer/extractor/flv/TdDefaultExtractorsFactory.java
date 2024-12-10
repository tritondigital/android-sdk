package com.tritondigital.player.exoplayer.extractor.flv;


import androidx.media3.extractor.DefaultExtractorsFactory;
import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.ExtractorsFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by mkabore on 31/10/2016.
 */

/**
 * An {@link ExtractorsFactory} that provides an array of extractors for the following formats:
 *
 * <ul>
 * <li>MP4, including M4A ({@link com.google.android.exoplayer2.extractor.mp4.Mp4Extractor})</li>
 * <li>fMP4 ({@link com.google.android.exoplayer2.extractor.mp4.FragmentedMp4Extractor})</li>
 * <li>Matroska and WebM ({@link com.google.android.exoplayer2.extractor.mkv.MatroskaExtractor})
 * </li>
 * <li>Ogg Vorbis/FLAC ({@link com.google.android.exoplayer2.extractor.ogg.OggExtractor}</li>
 * <li>MP3 ({@link com.google.android.exoplayer2.extractor.mp3.Mp3Extractor})</li>
 * <li>AAC ({@link com.google.android.exoplayer2.extractor.ts.AdtsExtractor})</li>
 * <li>MPEG TS ({@link com.google.android.exoplayer2.extractor.ts.TsExtractor})</li>
 * <li>MPEG PS ({@link com.google.android.exoplayer2.extractor.ts.PsExtractor})</li>
 * <li>FLV ({@link com.tritondigital.player.exoplayer.extractor.flv.TdFlvExtractor})</li>
 * <li>WAV ({@link com.google.android.exoplayer2.extractor.wav.WavExtractor})</li>
 * <li>FLAC (only available if the FLAC extension is built and included)</li>
 * </ul>
 */
public class TdDefaultExtractorsFactory implements ExtractorsFactory {

    // Lazily initialized default extractor classes in priority order.
    private static List<Class<? extends Extractor>> defaultExtractorClasses;

    private TdMetaDataListener mTdMetaDataListener;
    /**
     * Creates a new factory for the default extractors.
     */
    public TdDefaultExtractorsFactory(TdMetaDataListener listener) {
        mTdMetaDataListener = listener;
        synchronized (DefaultExtractorsFactory.class) {
            if (defaultExtractorClasses == null) {
                // Lazily initialize defaultExtractorClasses.
                List<Class<? extends Extractor>> extractorClasses = new ArrayList<>();
                // We reference extractors using reflection so that they can be deleted cleanly.
                // Class.forName is used so that automated tools like proguard can detect the use of
                // reflection (see http://proguard.sourceforge.net/FAQ.html#forname).
                try {
                    extractorClasses.add(
                            Class.forName("androidx.media3.extractor.mkv.MatroskaExtractor")
                                    .asSubclass(Extractor.class));
                } catch (ClassNotFoundException e) {
                    // Extractor not found.
                }
                try {
                    extractorClasses.add(
                            Class.forName("androidx.media3.extractor.mp4.FragmentedMp4Extractor")
                                    .asSubclass(Extractor.class));
                } catch (ClassNotFoundException e) {
                    // Extractor not found.
                }
                try {
                    extractorClasses.add(
                            Class.forName("androidx.media3.extractor.mp4.Mp4Extractor")
                                    .asSubclass(Extractor.class));
                } catch (ClassNotFoundException e) {
                    // Extractor not found.
                }
                try {
                    extractorClasses.add(
                            Class.forName("androidx.media3.extractor.mp3.Mp3Extractor")
                                    .asSubclass(Extractor.class));
                } catch (ClassNotFoundException e) {
                    // Extractor not found.
                }
                try {
                    extractorClasses.add(
                            Class.forName("androidx.media3.extractor.ts.AdtsExtractor")
                                    .asSubclass(Extractor.class));
                } catch (ClassNotFoundException e) {
                    // Extractor not found.
                }
                try {
                    extractorClasses.add(
                            Class.forName("androidx.media3.extractor.ts.Ac3Extractor")
                                    .asSubclass(Extractor.class));
                } catch (ClassNotFoundException e) {
                    // Extractor not found.
                }
                try {
                    extractorClasses.add(
                            Class.forName("androidx.media3.extractor.ts.TsExtractor")
                                    .asSubclass(Extractor.class));
                } catch (ClassNotFoundException e) {
                    // Extractor not found.
                }
                try {
                    extractorClasses.add(
                            Class.forName("com.tritondigital.player.exoplayer.extractor.flv.TdFlvExtractor")
                                    .asSubclass(Extractor.class));
                } catch (ClassNotFoundException e) {
                    // Extractor not found.
                }
                try {
                    extractorClasses.add(
                            Class.forName("androidx.media3.extractor.ogg.OggExtractor")
                                    .asSubclass(Extractor.class));
                } catch (ClassNotFoundException e) {
                    // Extractor not found.
                }
                try {
                    extractorClasses.add(
                            Class.forName("androidx.media3.extractor.ts.PsExtractor")
                                    .asSubclass(Extractor.class));
                } catch (ClassNotFoundException e) {
                    // Extractor not found.
                }
                try {
                    extractorClasses.add(
                            Class.forName("androidx.media3.extractor.wav.WavExtractor")
                                    .asSubclass(Extractor.class));
                } catch (ClassNotFoundException e) {
                    // Extractor not found.
                }
                try {
                    extractorClasses.add(
                            Class.forName("androidx.media3.decoder.flac.FlacExtractor")
                                    .asSubclass(Extractor.class));
                } catch (ClassNotFoundException e) {
                    // Extractor not found.
                }
                defaultExtractorClasses = extractorClasses;
            }
        }
    }

    @Override
    public Extractor[] createExtractors() {
        Extractor[] extractors = new Extractor[defaultExtractorClasses.size()];
        for (int i = 0; i < extractors.length; i++) {
            try {
                extractors[i] = defaultExtractorClasses.get(i).getConstructor().newInstance();
                if((extractors[i] instanceof  TdFlvExtractor) &&  mTdMetaDataListener!= null)
                {
                    ((TdFlvExtractor)extractors[i]).setMetaDataListener(mTdMetaDataListener);
                }
            } catch (Exception e) {
                // Should never happen.
                throw new IllegalStateException("Unexpected error creating default extractor", e);
            }
        }
        return extractors;
    }
}
