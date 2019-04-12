package com.tritondigital.player;

import android.content.Context;
import android.net.Uri;

import com.tritondigital.util.LocationUtil;
import com.tritondigital.util.Log;
import com.tritondigital.util.SdkUtil;
import com.tritondigital.util.TrackingUtil;

import java.util.HashMap;
import java.util.Locale;


/**
 * Helps create station stream URLs.
 *
 * This class helps you create a stream URL with tracking parameters. The user
 * tracking ID and location is automatically added when calling build(). This
 * class can also be used to create the stream parameters for TritonPlayer.
 *
 * A basic validation is done when adding a query parameter.
 */
@SuppressWarnings("UnusedDeclaration")
public final class StreamUrlBuilder {
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // 5.2.1 Location Targeting
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * _float_ - Latitude (-90.0f to 90.0f)
     *
     * Not required individually. If using, you must specify StreamUrlBuilder.LONGITUDE.
     */
    public static final String LATITUDE = "lat";

    /**
     * _float_ - Longitude (-180.0f to 180.0f)
     *
     * Not required individually. If using, you must specify StreamUrlBuilder.LATITUDE.
     */
    public static final String LONGITUDE = "long";

    /**
     * _String_ - Postal/ZIP code
     *
     * Valid postal or ZIP code, without spaces. E.g., 89040 or H3G1R8.
     *
     * Not required individually. If using, however, we recommend that you
     * specify StreamUrlBuilder.COUNTRY_CODE.
     */
    public static final String POSTAL_CODE = "postalcode";

    /**
     * _String_ - Country code
     * (<a href="http://en.wikipedia.org/wiki/ISO_3166-1_alpha-2#Officially_assigned_code_elements">
     * ISO 3166-1 alpha-2</a>)
     *
     * Not required individually. If using, however, we recommend that you
     * specify StreamUrlBuilder.POSTAL_CODE.
     */
    public static final String COUNTRY_CODE = "country";


    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // 5.2.2 Demographic Targeting
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * _int_ - Age (1 to 125)
     *
     * Clients/players must specify only one of StreamUrlBuilder.AGE,
     * StreamUrlBuilder.DATE_OF_BIRTH or StreamUrlBuilder.YEAR_OF_BIRTH.
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

    /** Possible StreamUrlBuilder.GENDER value */
    public static final char GENDER_VALUE_FEMALE = 'f';

    /** @copybrief GENDER_VALUE_FEMALE */
    public static final char GENDER_VALUE_MALE = 'm';


    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // 5.2.3 Custom Segment ID Targeting
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////

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
    // 5.3.1 Banner Capabilities
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


    /*
     * _String_ - Authorization Token (BETA)
     */
    public static final String AUTH_TOKEN = "tdtok";


    private static final String TAG = Log.makeTag("StreamUrlBuilder");

    private HashMap<String, String> mQueryParams = new HashMap<>();
    private Context mContext;
    private boolean mLocationTrackingEnabled;


    /**
     * Constructor
     */
    public StreamUrlBuilder(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("context cannot be null");
        }

        mContext = context;
        TrackingUtil.prefetchTrackingId(context);
    }


    /**
     * Adds a key/value pair to the URL query parameters.
     *
     * The key and value will be encoded.
     */
    public StreamUrlBuilder addQueryParameter(String key, String value) {
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
        }

        mQueryParams.put(key, value);
        return this;
    }


    /**
     * /copydoc addQueryParameter(String, String)
     */
    public StreamUrlBuilder addQueryParameter(String key, boolean value) {
        return addQueryParameter(key, String.valueOf(value));
    }


    /**
     * /copydoc addQueryParameter(String, String)
     */
    public StreamUrlBuilder addQueryParameter(String key, char value) {
        if (key.equals(GENDER) && (value != GENDER_VALUE_FEMALE) && (value != GENDER_VALUE_MALE)) {
            Log.w(TAG, "Invalid \"" + key + "\" value: Can only be 'm' or 'f'.");
        }

        return addQueryParameter(key, String.valueOf(value));
    }


    /**
     * /copydoc addQueryParameter(String, String)
     */
    public StreamUrlBuilder addQueryParameter(String key, int value) {
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
    public StreamUrlBuilder addQueryParameter(String key, double value) {
        return addQueryParameter(key, String.valueOf(value));
    }


    /**
     * /copydoc addQueryParameter(String, String)
     */
    public StreamUrlBuilder addQueryParameter(String key, float value) {
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
    public StreamUrlBuilder addQueryParameter(String key, long value) {
        return addQueryParameter(key, String.valueOf(value));
    }


    /**
     * Clears the the previously set query
     */
    public StreamUrlBuilder resetQueryParameters() {
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
    StreamUrlBuilder setQueryParameters(HashMap<String, String> queryParams) {
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
     * Enabling this feature will overwrite the StreamUrlBuilder.LATITUDE
     * and StreamUrlBuilder.LONGITUDE query parameters.
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
    public StreamUrlBuilder enableLocationTracking(boolean enable) {
        if (mLocationTrackingEnabled != enable) {
            mLocationTrackingEnabled = enable;

            if (enable) {
                LocationUtil.prefetchNetworkLocation(mContext);
            }
        }

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

        Uri.Builder uriBuilder = mHostUri.buildUpon();

        // Append the query parameter to the URL
        if (!mQueryParams.isEmpty()) {
            for (HashMap.Entry<String, String> entry : mQueryParams.entrySet()) {
                uriBuilder.appendQueryParameter(entry.getKey(), entry.getValue());
            }
        }

        // Location tracking
        if (mLocationTrackingEnabled) {
            TrackingUtil.appendLocationParams(mContext, uriBuilder);
        }


        if (mQueryParams.get(BANNERS) == null)
        {
            uriBuilder.appendQueryParameter(BANNERS, "none");
        }

        //SDK Version
        uriBuilder.appendQueryParameter("tdsdk", "android-" + SdkUtil.VERSION);

        // Listener ID
        uriBuilder.appendQueryParameter("uuid", TrackingUtil.getTrackingId(mContext));


        //Add pname
        uriBuilder.appendQueryParameter(PlayerConsts.PNAME, PlayerConsts.PNAME_VAL);


        String streamUrl = uriBuilder.build().toString();
        Log.d(TAG, "Stream URL built: " + streamUrl);
        return streamUrl;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Server URL
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private Uri mHostUri;

    /**
     * Returns the player host
     */
    public String getHost() {
        return (mHostUri == null) ? null : mHostUri.toString();
    }


    /**
     * Sets server host with the mount
     */
    public StreamUrlBuilder setHost(String host) {
        mHostUri = Uri.parse(host);
        return this;
    }
}
