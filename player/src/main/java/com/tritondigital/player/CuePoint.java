package com.tritondigital.player;

import android.os.Bundle;
import android.text.TextUtils;

import com.tritondigital.util.Log;


/**
 * Cue point keys
 */
@SuppressWarnings("UnusedDeclaration")
public final class CuePoint {

    // ---------------------------------------------------------------------------------------------
    // 2 Metadata Dictionary
    // ---------------------------------------------------------------------------------------------

    /** _String_ - Cue point type */
    public static final String CUE_TYPE = "cue_type";

    /** Music track/song */
    public static final String CUE_TYPE_VALUE_TRACK = "track";

    /** Advertisement / commercial */
    public static final String CUE_TYPE_VALUE_AD = "ad";

    /** Speech segments / news */
    public static final String CUE_TYPE_VALUE_SPEECH = "speech";

    /** Sweeper segment */
    public static final String CUE_TYPE_VALUE_SWEEPER = "sweeper";

    /** Audio stream information */
    public static final String CUE_TYPE_VALUE_AUDIO = "audio";

    /** Sidekick application cue points */
    public static final String CUE_TYPE_VALUE_SIDEKICK = "sidekick";

    /** Media Recorder application cue points */
    public static final String CUE_TYPE_VALUE_RECORDING = "recording";

    /** Profanity removal application cue points */
    public static final String CUE_TYPE_VALUE_PROFANITY = "profanity";

    /** Custom (user-defined) */
    public static final String CUE_TYPE_VALUE_CUSTOM = "custom";

    /** Unknown cue point type */
    public static final String CUE_TYPE_VALUE_UNKNOWN = "unknown";


    // ---------------------------------------------------------------------------------------------
    // 2.1 Common Attributes
    // ---------------------------------------------------------------------------------------------

    /** _String_ - Cue point ID */
    public static final String CUE_ID = "cue_id";

    /** _String_ - Cue point title */
    public static final String CUE_TITLE  = "cue_title";

    /**
     * boolean - Controls whether or not this cue point should be displayed in players.
     *
     * For SHOUTcast V1 metadata, it controls whether or not the MediaGateway
     * generates SHOUTcast V1 metadata.*/
    public static final String CUE_DISPLAY = "cue_display";

    /** _long_ - Cue point start timestamp */
    public static final String CUE_START_TIMESTAMP = "cue_time_start";

    /** _int_ - Cue point duration in milliseconds */
    public static final String CUE_TIME_DURATION = "cue_time_duration";

    /** _int_ - Position in the stream in milliseconds */
    public static final String POSITION_IN_STREAM = "timestamp";

    /** _String_ - Unique program ID */
    public static final String PROGRAM_ID = "program_id";

    /** _String_ - Program title */
    public static final String PROGRAM_TITLE = "program_title";

    /** _String_ - Program homepage URL */
    public static final String PROGRAM_HOMEPAGE_URL = "program_homepage_url";

    /** _String_ - Program image URL */
    public static final String PROGRAM_IMAGE_URL = "program_image_url";

    /** _long_ - Program start timestamp */
    public static final String PROGRAM_START_TIMESTAMP = "program_time_start";

    /** _int_ - Program duration in milliseconds */
    public static final String PROGRAM_TIME_DURATION = "program_time_duration";

    /** _String_ - Host ID */
    public static final String PROGRAM_HOST_1_ID = "program_host_1_id";

    /** @copybrief PROGRAM_HOST_1_ID */
    public static final String PROGRAM_HOST_2_ID = "program_host_2_id";

    /** @copybrief PROGRAM_HOST_1_ID */
    public static final String PROGRAM_HOST_3_ID = "program_host_3_id";

    /** _String_ - Host name */
    public static final String PROGRAM_HOST_1_NAME = "program_host_1_name";

    /** @copybrief PROGRAM_HOST_1_NAME */
    public static final String PROGRAM_HOST_2_NAME = "program_host_2_name";

    /** @copybrief PROGRAM_HOST_1_NAME */
    public static final String PROGRAM_HOST_3_NAME = "program_host_3_name";

    /** _String_ - Host homepage URL */
    public static final String PROGRAM_HOST_1_HOMEPAGE_URL = "program_host_1_homepage_url";

    /** @copybrief PROGRAM_HOST_1_HOMEPAGE_URL */
    public static final String PROGRAM_HOST_2_HOMEPAGE_URL = "program_host_2_homepage_url";

    /** @copybrief PROGRAM_HOST_1_HOMEPAGE_URL */
    public static final String PROGRAM_HOST_3_HOMEPAGE_URL = "program_host_3_homepage_url";

    /** _String_ - Host picture URL */
    public static final String PROGRAM_HOST_1_PICTURE_URL = "program_host_1_picture_url";

    /** @copybrief PROGRAM_HOST_1_PICTURE_URL */
    public static final String PROGRAM_HOST_2_PICTURE_URL = "program_host_2_picture_url";

    /** @copybrief PROGRAM_HOST_1_PICTURE_URL */
    public static final String PROGRAM_HOST_3_PICTURE_URL = "program_host_3_picture_url";

    /** _String_ - Guest ID */
    public static final String PROGRAM_GUEST_1_ID = "program_guest_1_id";

    /** @copybrief PROGRAM_GUEST_1_ID */
    public static final String PROGRAM_GUEST_2_ID = "program_guest_2_id";

    /** @copybrief PROGRAM_GUEST_1_ID */
    public static final String PROGRAM_GUEST_3_ID = "program_guest_3_id";

    /** _String_ - Guest name */
    public static final String PROGRAM_GUEST_1_NAME = "program_guest_1_name";

    /** @copybrief PROGRAM_GUEST_1_NAME */
    public static final String PROGRAM_GUEST_2_NAME = "program_guest_2_name";

    /** @copybrief PROGRAM_GUEST_1_NAME */
    public static final String PROGRAM_GUEST_3_NAME = "program_guest_3_name";

    /** _String_ - Guest homepage URL */
    public static final String PROGRAM_GUEST_1_HOMEPAGE_URL = "program_guest_1_homepage_url";

    /** @copybrief PROGRAM_GUEST_1_HOMEPAGE_URL */
    public static final String PROGRAM_GUEST_2_HOMEPAGE_URL = "program_guest_2_homepage_url";

    /** @copybrief PROGRAM_GUEST_1_HOMEPAGE_URL */
    public static final String PROGRAM_GUEST_3_HOMEPAGE_URL = "program_guest_3_homepage_url";

    /** _String_ - Guest picture URL */
    public static final String PROGRAM_GUEST_1_PICTURE_URL = "program_guest_1_picture_url";

    /** @copybrief PROGRAM_GUEST_1_PICTURE_URL */
    public static final String PROGRAM_GUEST_2_PICTURE_URL = "program_guest_2_picture_url";

    /** @copybrief PROGRAM_GUEST_1_PICTURE_URL */
    public static final String PROGRAM_GUEST_3_PICTURE_URL = "program_guest_3_picture_url";


    // ---------------------------------------------------------------------------------------------
    // 2.2 Track Attributes
    // ---------------------------------------------------------------------------------------------

    /** _String_ - Unique track ID */
    public static final String TRACK_ID = "track_id";

    /** _String_ - Track artist name */
    public static final String TRACK_ARTIST_NAME = "track_artist_name";

    /** _String_ - Album name */
    public static final String TRACK_ALBUM_NAME = "track_album_name";

    /** _int_ - Year of album published */
    public static final String TRACK_ALBUM_YEAR = "track_album_year";

    /** _String_ - Name of album publisher (label) */
    public static final String TRACK_ALBUM_PUBLISHER = "track_album_publisher";

    /** _String_ - Track genre */
    public static final String TRACK_GENRE = "track_genre";

    /** _String_ - Track cover image URL */
    public static final String TRACK_COVER_URL = "track_cover_url";

    /** _String_ - Track product page URL */
    public static final String TRACK_PRODUCT_URL = "track_product_url";

    /** _String_ - Link to XML containing Enhanced Now Playing information */
    public static final String TRACK_NOWPLAYING_URL = "track_nowplaying_url";

    /** _String_ - Track ISRC code (12 characters, without the hyphens) */
    public static final String TRACK_ISRC = "track_isrc";

    /** _String_ - Track format */
    public static final String TRACK_FORMAT = "track_format";

    /** Possible CuePoint.TRACK_FORMAT value */
    public static final String TRACK_FORMAT_VALUE_AUDIO = "audio";

    /** @copybrief TRACK_FORMAT_VALUE_AUDIO */
    public static final String TRACK_FORMAT_VALUE_AUDIO_VIDEO = "audio+video";

    /** @copybrief TRACK_FORMAT_VALUE_AUDIO */
    public static final String TRACK_FORMAT_VALUE_VIDEO = "video";


    // ---------------------------------------------------------------------------------------------
    // 2.3 Ad Attributes
    // ---------------------------------------------------------------------------------------------

    /** _String_ - Ad unique ID */
    public static final String AD_ID = "ad_id";

    /** _String_ - Ad type */
    public static final String AD_TYPE = "ad_type";

    /** Possible CuePoint.AD_TYPE value */
    public static final String AD_TYPE_VALUE_BREAK = "break";

    /** @copybrief AD_TYPE_VALUE_BREAK */
    public static final String AD_TYPE_VALUE_ENDPOINT = "endbreak";

    /** @copybrief AD_TYPE_VALUE_BREAK */
    public static final String AD_TYPE_VALUE_TARGETSPOT = "targetspot";

    /** _String_ - Ad content URL (player-dependent). Depending on the player, this URL
     * can be used as a link to an IFRAME, a simple image, etc. */
    public static final String AD_URL = "ad_url";

    /** _String_ - Additional content URL (player-dependent) */
    public static final String AD_URL_1 = "ad_url_1";

    /** @copybrief AD_URL_1 */
    public static final String AD_URL_2 = "ad_url_2";

    /** @copybrief AD_URL_1 */
    public static final String AD_URL_3 = "ad_url_3";

    /** @copybrief AD_URL_1 */
    public static final String AD_REPLACE = "ad_replace";

    /** _String_ - Inlined VAST xml */
    public static final String AD_VAST = "ad_vast";

    /** _String_ - Ad request URL */
    public static final String AD_VAST_URL = "ad_vast_url";


    // ---------------------------------------------------------------------------------------------
    // 2.5 Radio Sweeper (sweeper)
    // ---------------------------------------------------------------------------------------------

    /** _String_ - A unique ID for this sweeper */
    public static final String SWEEPER_ID   = "sweeper_id";

    /** _String_ - Sweeper type identifier */
    public static final String SWEEPER_TYPE = "sweeper_type";

    /** Possible CuePoint.SWEEPER_TYPE value */
    public static final String SWEEPER_TYPE_VALUE_SWEEPER = "sweeper";

    /** @copybrief SWEEPER_TYPE_VALUE_SWEEPER */
    public static final String SWEEPER_TYPE_VALUE_BUMPER = "bumper";

    /** @copybrief SWEEPER_TYPE_VALUE_SWEEPER */
    public static final String SWEEPER_TYPE_VALUE_PROMO = "promo";

    /** @copybrief SWEEPER_TYPE_VALUE_SWEEPER */
    public static final String SWEEPER_TYPE_VALUE_STATION_ID = "stationid";

    /** @copybrief SWEEPER_TYPE_VALUE_SWEEPER */
    public static final String SWEEPER_TYPE_VALUE_LINER = "liner";

    /** @copybrief SWEEPER_TYPE_VALUE_SWEEPER */
    public static final String SWEEPER_TYPE_VALUE_JINGLE = "jingle";


    // --------------------------------------------------------------------------------------------
    // Legacy
    // --------------------------------------------------------------------------------------------

    /** _String_ - Deprecated key */
    public static final String LEGACY_AD_IMG_URL = "legacy_ad_image_url";

    /** _String_ - Deprecated key */
    public static final String LEGACY_BUY_URL = "legacy_buy_url";

    /** _String_ - Deprecated key */
    public static final String LEGACY_TYPE = "legacy_type";


    private CuePoint(){}


    /**
     * Utility method to add attributes in the right format in a cue point.
     */
    static void addCuePointAttribute(Bundle cuePoint, String type, String key, String value) {
        try {
            ///////////////////////////
            // Common
            ///////////////////////////
            if (key != null) {
                switch (key) {
                    case CUE_START_TIMESTAMP:
                    case PROGRAM_START_TIMESTAMP:
                        cuePoint.putLong(key, Long.parseLong(value));
                        return;

                    case CUE_TIME_DURATION:
                    case PROGRAM_TIME_DURATION:
                    case POSITION_IN_STREAM:
                        cuePoint.putInt(key, Integer.parseInt(value));
                        return;

                    case CUE_DISPLAY:
                        cuePoint.putBoolean(key, Boolean.parseBoolean(value));
                        return;
                }
            }

            ///////////////////////////
            // Ads
            ///////////////////////////
            if (CUE_TYPE_VALUE_AD.equals(type)) {
                if (AD_REPLACE.equals(key)) {
                    // Booleans
                    cuePoint.putBoolean(key, Boolean.parseBoolean(value));
                    return;
                }
            }

            ///////////////////////////
            // Tracks
            ///////////////////////////
            else if (CUE_TYPE_VALUE_TRACK.equals(type)) {

                if (TRACK_ALBUM_YEAR.equals(key)) {
                    // Integers
                    cuePoint.putInt(key, Integer.parseInt(value));
                    return;
                }
            }

        } catch (NumberFormatException e) {
            if (!TextUtils.isEmpty(value)) {
                Log.w("CuePoint", e, "Key:\"" + key + "\"  Value:\"" + value + '"');
            }
            return;
        }

        // Fallback to string
        cuePoint.putString(key, value);
    }
}
