package com.tritondigital.player;

/**
 * File used by Doxygen for documentation consistency. You don't need to use this class.
 */
public final class PlayerConsts {

    /** _String_ - Authentication token */
    public static final String AUTH_TOKEN  = "auth_token";
    public static final String AUTH_KEY_ID  = "auth_key_id";
    public static final String AUTH_SECRET_KEY  = "auth_secret_key";
    public static final String AUTH_REGISTERED_USER  = "auth_registered_user";
    public static final String AUTH_USER_ID  = "auth_user_id";

    /**
     * _HashMap<String, String>_ - Advertising targeting parameters.
     *
     * See available keys in StreamUrlBuilder.
     */
    public static final String TARGETING_PARAMS = "targeting_params";

    /** _Boolean_ - Enable the location tracking (Default: false) */
    public static final String TARGETING_LOCATION_TRACKING_ENABLED = "targeting_location_tracking_enabled";

    /** _String_ - Stream URL */
    public static final String STREAM_URL = "stream_url";

    /** _String_ - Stream transport (TRANSPORT_FLV, TRANSPORT_HLS or TRANSPORT_SC) */
    public static final String TRANSPORT  = "transport";

    /** _int_ - Initial playback position */
    static final String POSITION = "position";

    /** _String_ - MIME type */
    public static final String MIME_TYPE  = "mime_type";

    /** _String_ - SideBand Metadata URL */
    public static final String SBM_URL = "sbm_url";

    /** _String_ - Broadcaster (e.g.\ "Triton Digital") */
    public static final String STATION_BROADCASTER = "station_broadcaster";

    /** _String_ - Station unique identifier (e.g.\ "MOBILEFM") */
    public static final String STATION_NAME = "station_name";

    /** _String_ - Station mount (e.g.\ "MOBILEFMAAC") */
    public static final String STATION_MOUNT = "station_mount";

    /** _Bundle_ - <a href="https://developer.android.com/reference/android/support/v7/media/MediaItemMetadata.html">Metadata for Google Cast</a> */
    public static final String MEDIA_ITEM_METADATA = "mediaItemMetadata";

    /** AAC MIME type */
    public static final String MIME_TYPE_AAC  = "audio/aac";

    /** MP3 MIME type */
    public static final String MIME_TYPE_MPEG = "audio/mpeg";

    /** FLV transport */
    public static final String TRANSPORT_FLV = "flv";

    /** HLS transport */
    public static final String TRANSPORT_HLS = "hls";

    /** SHOUTcast transport */
    public static final String TRANSPORT_SC  = "sc";

    /** Low Delay */
    public static final String LOW_DELAY = "low_delay";

    /** TTags */
    public static final String TTAGS = "ttags";

    /** _Boolean_ - Enable timeshift (Default: false) */
    public static final String TIMESHIFT_ENABLED = "timeshift_enabled";

    /** Force the disabling of the ExoPlayer */
    public static final String FORCE_DISABLE_EXOPLAYER = "ForceDisableExoPlayer";

    /** PNAME; used for syndication report */
    public static final String PNAME     = "pname";
    public static final String PNAME_VAL = "TritonMobileSDK_Android";


    /** Prefix used in the playerServices Url to target a specific provisioning service */
    public static final String PLAYER_SERVICES_REGION = "PlayerServicesRegion";

    static final String USER_AGENT = "user_agent";

    private PlayerConsts() {}
}
