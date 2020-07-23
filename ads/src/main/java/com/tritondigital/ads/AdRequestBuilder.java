package com.tritondigital.ads;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;

import com.tritondigital.util.LocationUtil;
import com.tritondigital.util.Log;
import com.tritondigital.util.SdkUtil;
import com.tritondigital.util.TrackingUtil;

import java.util.HashMap;
import java.util.Locale;


/**
 * Helps create on-demand ad request URLs.
 *
 * This class is based on <i>https://userguides.tritondigital.com/spc/ondemand/</i>.
 * The user tracking ID is automatically added and there is an option to use the
 * device location manager for location targeting.
 *
 * A basic validation is done when adding a query parameter.
 */
@SuppressWarnings("UnusedDeclaration")
public final class AdRequestBuilder {
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // 3.3.1 Station IDs and Names
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * _int_ - Station ID
     *
     * Either AdRequestBuilder.STATION_ID or AdRequestBuilder.STATION_NAME must be specified when calling the
     * On-Demand Ad Request Service. While both are supported, it is strongly recommended that clients
     * use AdRequestBuilder.STATION_NAME. If both are provided, AdRequestBuilder.STATION_NAME is used (there is
     * no validation check that AdRequestBuilder.STATION_ID matches the name).
     *
     * Triton Digital assigns station IDs and names when setting up a station. Station names are
     * case-sensitive.
     */
    public static final String STATION_ID = "stid";

    public static final String BUNDLE_ID = "bundle-id";

    public static final String STORE_ID = "store-id";

    /** URL encoded string
     * */
    public static final String STORE_URL = "store-url";


    /**
     * _String_ - Station Name
     * @copydetails STATION_ID
     */
    public static final String STATION_NAME = "stn";

    /**
     * _String_ - Ad type (default: preroll)
     */
    public static final String TYPE = "type";

    /** Possible AdRequestBuilder.TYPE value */
    public static final String TYPE_VALUE_PREROLL = "preroll";

    /** @copybrief TYPE_VALUE_PREROLL */
    public static final String TYPE_VALUE_MIDROLL = "midroll";


    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // 3.3.4 Rendering Formats (private for now)
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /** Supported render formats for the On-Demand Ad Service */
    public static final String RENDERING_FORMAT = "fmt";

    /** text/xml; charset=UTF-8 DAAST 1.0 */
    public static final String RENDERING_FORMAT_VALUE_DAAST = "daast";

    /** text/xml; charset=UTF-8 VAST 2.0 */
    public static final String RENDERING_FORMAT_VALUE_VAST  = "vast";


    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // 3.3.5 Asset Constraints
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /** _String (comma-separated list)_ - Asset Type */
    public static final String ASSET_TYPE = "at";

    /** Possible AdRequestBuilder.ASSET_TYPE value */
    public static final String ASSET_TYPE_VALUE_AUDIO = "audio";

    /** @copybrief ASSET_TYPE_VALUE_AUDIO */
    public static final String ASSET_TYPE_VALUE_VIDEO = "video";

    /** _int_ - Min file size in KB */
    public static final String MIN_FILE_SIZE = "minsz";

    /** _int_ - Max file size in KB */
    public static final String MAX_FILE_SIZE = "maxsz";


    /** _String (comma-separated list)_ - File Format */
    public static final String FILE_FORMAT = "cntnr";

    /** Possible AdRequestBuilder.FILE_FORMAT value */
    public static final String FILE_FORMAT_VALUE_MP3 = "mp3";

    /** @copybrief FILE_FORMAT_VALUE_MP3*/
    public static final String FILE_FORMAT_VALUE_ADTS = "adts";

    /** @copybrief FILE_FORMAT_VALUE_MP3*/
    public static final String FILE_FORMAT_VALUE_FLV = "flv";

    /** @copybrief FILE_FORMAT_VALUE_MP3*/
    public static final String FILE_FORMAT_VALUE_MP4 = "mp4";


    /** _int_ - Min duration in seconds */
    public static final String MIN_DURATION = "mindur";

    /** _int_ - Max duration in seconds */
    public static final String MAX_DURATION = "maxdur";


    /** _int_ - Min bitrate in kbps */
    public static final String MIN_BITRATE = "mindbr";

    /** _int_ - Max bitrate in kbps */
    public static final String MAX_BITRATE = "maxbr";


    /** _int_ - Video min width in density-independent pixels */
    public static final String VIDEO_MIN_WIDTH = "minw";

    /** _int_ - Video max width in density-independent pixels */
    public static final String VIDEO_MAX_WIDTH = "maxw";


    /** _int_ - Video min height in density-independent pixels */
    public static final String VIDEO_MIN_HEIGHT = "minh";

    /** _int_ - Video max height in density-independent pixels */
    public static final String VIDEO_MAX_HEIGHT = "maxdh";


    /** _String (comma-separated list)_ - Audio codec */
    public static final String AUDIO_CODEC = "acodec";

    /** Possible AdRequestBuilder.AUDIO_CODEC value */
    public static final String AUDIO_CODEC_VALUE_MP3 = "mp3";

    /** @copybrief AUDIO_CODEC_VALUE_MP3 */
    public static final String AUDIO_CODEC_VALUE_AAC_HEV1 = "aac_hev1";

    /** @copybrief AUDIO_CODEC_VALUE_MP3 */
    public static final String AUDIO_CODEC_VALUE_AAC_HEV2 = "aac_hev2";

    /** @copybrief AUDIO_CODEC_VALUE_MP3 */
    public static final String AUDIO_CODEC_VALUE_AAC_LC = "aac_lc";

    /** _int_ - Audio min channels */
    public static final String AUDIO_MIN_CHANNELS = "minach";

    /** _int_ - Audio max channels */
    public static final String AUDIO_MAX_CHANNELS = "maxach";

    /** _String (comma-separated list)_ - Audio Sample Rates in Hz */
    public static final String AUDIO_SAMPLE_RATE = "asr";


    /** _String (comma-separated list)_ - Video codec */
    public static final String VIDEO_CODEC = "vcodec";

    /** Possible AdRequestBuilder.VIDEO_CODEC value */
    public static final String VIDEO_CODEC_VALUE_H264 = "h264";

    /** @copybrief VIDEO_CODEC_VALUE_H264 */
    public static final String VIDEO_CODEC_VALUE_ON2_VP6 = "on2_vp6";


    /** _String (comma-separated list)_ - Video Aspect Ratio */
    public static final String VIDEO_ASPECT_RATIO = "vaspect";

    /** Possible AdRequestBuilder.VIDEO_ASPECT_RATIO value */
    public static final String VIDEO_ASPECT_RATIO_VALUE_4_3 = "4:3";

    /** @copybrief VIDEO_ASPECT_RATIO_VALUE_4_3 */
    public static final String VIDEO_ASPECT_RATIO_VALUE_16_9  = "16:9";

    /** @copybrief VIDEO_ASPECT_RATIO_VALUE_4_3 */
    public static final String VIDEO_ASPECT_RATIO_VALUE_OTHER = "other";


    /** _float_ - Video min frame rate */
    public static final String VIDEO_MIN_FRAME_RATE = "minfps";

    /** _float_ - Video max frame rate */
    public static final String VIDEO_MAX_FRAME_RATE = "maxfps";


    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // 3.4.1 Location Targeting
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * _float_ - Latitude (-90.0f to 90.0f)
     *
     * Not required individually. If using, you must specify AdRequestBuilder.LONGITUDE.
     */
    public static final String LATITUDE = "lat";

    /**
     * _float_ - Longitude (-180.0f to 180.0f)
     *
     * Not required individually. If using, you must specify AdRequestBuilder.LATITUDE.
     */
    public static final String LONGITUDE = "long";

    /**
     * _String_ - Postal/ZIP code
     *
     * Valid postal or ZIP code, without spaces. E.g., 89040 or H3G1R8.
     *
     * Not required individually. If using, however, we recommend that you
     * specify AdRequestBuilder.COUNTRY_CODE.
     */
    public static final String POSTAL_CODE = "postalcode";

    /**
     * _String_ - Country code
     * (<a href="http://en.wikipedia.org/wiki/ISO_3166-1_alpha-2#Officially_assigned_code_elements">
     * ISO 3166-1 alpha-2</a>)
     *
     * Not required individually. If using, however, we recommend that you
     * specify AdRequestBuilder.POSTAL_CODE.
     */
    public static final String COUNTRY_CODE = "country";


    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // 3.4.2 Demographic Targeting
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * _int_ - Age (1 to 125)
     *
     * Clients/players must specify only one of AdRequestBuilder.AGE,
     * AdRequestBuilder.DATE_OF_BIRTH or AdRequestBuilder.YEAR_OF_BIRTH.
     */
    public static final String AGE = "age";

    /**
     * _String_ - Date of birth formatted as "YYYY-MM-DD"
     * @copydetails AGE
     */
    public static final String DATE_OF_BIRTH = "dob";

    /**
     * _int_ - Year of birth (1900 to 2005)
     * @copydetails AGE
     */
    public static final String YEAR_OF_BIRTH = "yob";

    /** _char_ - Gender ('m' or 'f') */
    public static final String GENDER = "gender";

    /** Possible AdRequestBuilder.GENDER value */
    public static final char GENDER_VALUE_FEMALE = 'f';

    /** @copybrief GENDER_VALUE_FEMALE */
    public static final char GENDER_VALUE_MALE = 'm';

    /**
     * _int_ - Custom segment ID (1 to 1000000)
     *
     * Broadcasters that want to differentiate their listeners into custom
     * broadcaster-specific segments may use the Custom Segment Targeting
     * capability of Tap.
     *
     * @note Before use by players, please contact the Triton Digital Support
     * Team to enable Custom Segment ID Targeting for your broadcaster.
     * Currently, Custom Segment ID Targeting only works with Tap advertising.
     */
    public static final String CUSTOM_SEGMENT_ID = "csegid";


    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // 3.5.1 Banner Capabilities
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * _String (comma-separated list)_ - Banner capabilities
     *
     * Players can provide details on their level of support for banners, such
     * as banner sizes and formats.
     *
     * The ordering of the capability formats is not important.
     *
     * @note Before attempting to use player capability targeting, please contact
     * the Triton Digital Support Team to enable Player Capability Targeting for
     * your broadcaster. Currently, Player Capability Targeting only works with
     * Tap advertising.
     *
     * @par Supported Formats
     *
     * <table>
     *      <tr><th>Capability</th><th>Description</th></tr>
     *      <tr><td>970x250</td>   <td>IAB Billboard (970x250)</td></tr>
     *      <tr><td>120x60</td>    <td>IAB Button 2 (120x60)</td></tr>
     *      <tr><td>300x600</td>   <td>IAB Half Page/Filmstrip (300x600)</td></tr>
     *      <tr><td>728x90</td>    <td>IAB Leaderboard (728x90)</td></tr>
     *      <tr><td>970x100</td>   <td>IAB Leaderboard (970x100)</td></tr>
     *      <tr><td>300x250</td>   <td>IAB Medium Rectangle (300x250)</td></tr>
     *      <tr><td>88x31</td>     <td>IAB Microbar (88x31)</td></tr>
     *      <tr><td>300x1050</td>  <td>IAB Portrait (300x1050)</td></tr>
     *      <tr><td>970x90</td>    <td>IAB Pushdown (970x90)</td></tr>
     *      <tr><td>180x150</td>   <td>IAB Rectangle (180x150)</td></tr>
     *      <tr><td>320x480</td>   <td>IAB Smartphone Portrait (320x480)</td></tr>
     *      <tr><td>300x50</td>    <td>IAB Smartphone Static Banner (300x50)</td></tr>
     *      <tr><td>320x50</td>    <td>IAB Smartphone Static Wide Banner (320x50)</td></tr>
     *      <tr><td>300x300</td>   <td>IAB Square (300x300)</td></tr>
     *      <tr><td>970x66</td>    <td>IAB Super Leaderboard (970x66)</td></tr>
     *      <tr><td>160x600</td>   <td>IAB Wide Skyscraper (160x600)</td></tr>
     *      <tr><td>Client-defined (w x h)</td><td>Custom banner size</td></tr>
     * </table>
     */
    public static final String BANNERS = "banners";



    //TD Advertisement Guide  version
    public static final String ADS_GUIDE_VERSION_KEY   = "version";
    public static final String ADS_GUIDE_VERSION_VALUE = "1.5.1";


    private static final String TAG = Log.makeTag("AdRequestBuilder");

    private HashMap<String, String> mQueryParams = new HashMap<>();
    private Context mContext;
    private boolean mLocationTrackingEnabled;
    private String[] mTtags = null;


    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // 3.2 On-Demand Ad Service Host
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private Uri mHostUri;

    /**
     * Returns the ad server host
     */
    public String getHost()
    {
        return (mHostUri == null) ? null : mHostUri.toString();
    }


    /**
     * Sets ad server host
     *
     * @note Please contact Triton Digital Customer Support to obtain the correct On-Demand Ad Service
     * Host name to use  for on-demand ad requests for pre-production and production uses.
     */
    public AdRequestBuilder setHost(String host) {
        host = normalizeHost(host);
        mHostUri = Uri.parse(host);
        return this;
    }


    private static String normalizeHost(String host) {
        if (!TextUtils.isEmpty(host)) {
            // No trailing spaces
            host = host.trim();

            //
            // Prefix. Use "http" if no scheme is provided
            //
            if (!host.startsWith("http")) {
                host = "http://" + host;
            }

            // Remove the last character if it is '/'
            int strLength = host.length();
            if (host.charAt(strLength - 1) == '/') {
                host = host.substring(0, strLength - 1);
            }

            // Missing "/ars" at the end
            if (host.endsWith("/ondemand")) {
                host += "/ars";
            }

            // Missing "/ondemand/ars" at the end
            if (!host.endsWith("/ondemand/ars")) {
                if (!host.endsWith("/")) {
                    host += "/";
                }

                host += "ondemand/ars";
            }
        }

        return host;
    }


    /**
     * Constructor
     */
    public AdRequestBuilder(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("\"context\" cannot be null");
        }

        mContext = context;
        TrackingUtil.prefetchTrackingId(context);
        resetQueryParameters();
    }


    /**
     * Adds a key/value pair to the URL query parameters.
     *
     * The key and value will be encoded.
     */
    public AdRequestBuilder addQueryParameter(String key, String value) {
        if (value == null) {
            mQueryParams.remove(key);
            return this;

        } else if (key.equals(DATE_OF_BIRTH)) {
            if ((value.length() != 10) || (value.charAt(4) != '-') || (value.charAt(7) != '-')) {
                Log.w(TAG, "Invalid \"" + key + "\" value. " +
                        "Must be in format YYYY-MM-DD: " + value);
            }

        } else if (key.equals(COUNTRY_CODE)) {
            if ((value.length() != 2)) {
                Log.w(TAG, "Invalid \"" + key + "\" value: " + value);
            } else {
                value = value.toUpperCase(Locale.ENGLISH);
            }

        } else if (key.equals(STATION_ID) && !TextUtils.isDigitsOnly(value)) {
            // The station IDs can only contain digits.
            key = STATION_NAME;

        } else if (key.equals(STATION_NAME) && TextUtils.isDigitsOnly(value)) {
            // The station name can't be only digits.
            key = STATION_ID;

        }

        mQueryParams.put(key, value);
        return this;
    }


    /**
     * /copydoc addQueryParameter(String, String)
     */
    public AdRequestBuilder addQueryParameter(String key, boolean value) {
        return addQueryParameter(key, String.valueOf(value));
    }


    /**
     * /copydoc addQueryParameter(String, String)
     */
    public AdRequestBuilder addQueryParameter(String key, char value) {
        if (key.equals(GENDER) && (value != GENDER_VALUE_FEMALE) && (value != GENDER_VALUE_MALE)) {
            Log.w(TAG, "Invalid \"" + key + "\" value: Can only be 'm' or 'f'.");
        }

        return addQueryParameter(key, String.valueOf(value));
    }


    /**
     * /copydoc addQueryParameter(String, String)
     */
    public AdRequestBuilder addQueryParameter(String key, int value) {
        int min = Integer.MIN_VALUE;
        int max = Integer.MAX_VALUE;

        switch (key) {
            case AGE:
                min = 1;
                max = 125;
                break;

            case CUSTOM_SEGMENT_ID:
                min = 1;
                max = 1000000;
                break;

            case YEAR_OF_BIRTH:
                min = 1900;
                max = 2020;
                break;

            case STATION_NAME:
                // The station name can't be only digits.
                key = STATION_ID;
                break;

            default:
                break;
        }

        // Validate value
        if ((value < min) || (value > max)) {
            Log.w(TAG, "Invalid \"" + key + "\" value. \""
                    + value + "\" not in range [\"" + min + "\", \"" + max + "\"]");
        }

        return addQueryParameter(key, String.valueOf(value));
    }


    /**
     * /copydoc addQueryParameter(String, String)
     */
    public AdRequestBuilder addQueryParameter(String key, double value) {
        return addQueryParameter(key, String.valueOf(value));
    }


    /**
     * /copydoc addQueryParameter(String, String)
     */
    public AdRequestBuilder addQueryParameter(String key, float value) {
        float min = Float.MIN_VALUE;
        float max = Float.MAX_VALUE;

        if (key.equals(LATITUDE)) {
            min = -90.0f;
            max = 90.0f;

        } else if (key.equals(LONGITUDE)) {
            min = -180.0f;
            max = 180.0f;
        }

        // Validate value
        if ((value < min) || (value > max)) {
            Log.w(TAG, "Invalid \"" + key + "\" value. \""
                    + value + "\" not in range [\"" + min + "\", \"" + max + "\"]");
        }

        return addQueryParameter(key, String.valueOf(value));
    }


    /**
     * /copydoc addQueryParameter(String, String)
     */
    public AdRequestBuilder addQueryParameter(String key, long value) {
        return addQueryParameter(key, String.valueOf(value));
    }


    /**
     * Clears the the previously set query
     */
    public AdRequestBuilder resetQueryParameters() {
        mQueryParams.clear();
        return this;
    }


    /**
     * Returns the query parameters
     */
    public HashMap<String, String> getQueryParameters() {
        return mQueryParams;
    }


    /**
     * Sets the query parameters
     */
    AdRequestBuilder setQueryParameters(HashMap<String, String> queryParams) {
        if (queryParams == null) {
            mQueryParams.clear();
        } else {
            mQueryParams = queryParams;
        }

        return this;
    }


    /**
     * Enables the location tracking using the device's location manager.
     *
     * Enabling this feature will overwrite the AdRequestBuilder.LATITUDE
     * and AdRequestBuilder.LONGITUDE query parameters.
     *
     * See <a href="http://developer.android.com/reference/android/location/LocationManager.html">
     * Android location manager</a>.
     *
     * @par AndroidManifest.xml Permissions
     * @code{.xml}
     *      <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
     * @endcode
     */
    @SuppressWarnings("JavaDoc")
    public AdRequestBuilder enableLocationTracking(boolean enable) {
        if (mLocationTrackingEnabled != enable) {
            mLocationTrackingEnabled = enable;

            if (enable) {
                LocationUtil.prefetchNetworkLocation(mContext);
            }
        }

        return this;
    }


    /**
     * Set the Ttags for the Requested URL
     */
    public AdRequestBuilder addTtags( String[] ttags )
    {
        mTtags = ttags.clone();
        return this;
    }

    /**
     * Returns an URL from the previously set data.
     *
     * This method also refreshes the user tracking id and the location.
     */
    public String build() {
        // Validate station name and id
        if (mHostUri == null) {
            throw new IllegalArgumentException("The host must be set.");
        }

        if ((mQueryParams.get(STATION_NAME) == null) && (mQueryParams.get(STATION_ID) == null)) {
            throw new IllegalArgumentException("STATION_NAME or STATION_ID must be set.");
        }

        Uri.Builder uriBuilder = mHostUri.buildUpon();

        // Append the query parameter to the URL
        if (!mQueryParams.isEmpty()) {
            for (HashMap.Entry<String, String> entry : mQueryParams.entrySet()) {
                uriBuilder.appendQueryParameter(entry.getKey(), entry.getValue());
            }
        }

        // Default type: preroll
        if (mQueryParams.get(TYPE) == null) {
            uriBuilder.appendQueryParameter(TYPE, TYPE_VALUE_PREROLL);
        }

        // Default rendering format: VAST
        String renderingFormat = mQueryParams.get(RENDERING_FORMAT);
        if (renderingFormat == null) {
            uriBuilder.appendQueryParameter(RENDERING_FORMAT, RENDERING_FORMAT_VALUE_VAST);
        }

        // Location tracking
        if (mLocationTrackingEnabled) {
            TrackingUtil.appendLocationParams(mContext, uriBuilder);
        }

        // Mandatory fields
        if (mQueryParams.get(BANNERS) == null)
        {
            uriBuilder.appendQueryParameter(BANNERS, "none");
        }
        uriBuilder.appendQueryParameter("tdsdk", "android-" + SdkUtil.VERSION);
        uriBuilder.appendQueryParameter("lsid",  TrackingUtil.getTrackingId(mContext));
        uriBuilder.appendQueryParameter(ADS_GUIDE_VERSION_KEY, ADS_GUIDE_VERSION_VALUE);





        String adRequest = uriBuilder.build().toString();

        if ( mTtags != null && mTtags.length >0)
        {
            String allTtags  = TextUtils.join(",",mTtags);
            adRequest = adRequest + "&ttag=" + allTtags;
        }

        Log.d(TAG, "Ad request built: " + adRequest);

        return adRequest;
    }
}
