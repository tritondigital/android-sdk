package com.tritondigital.util;

import android.text.TextUtils;
import android.util.Base64;

import org.json.JSONException;
import org.json.JSONObject;

import java.security.Key;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;


/**
 * Utility to create an authentication token
 *
 * @par Example - Token Authorization (self signed)
 * @code{.java}
 *     // Create the targeting parameters
 *     HashMap<String, String> targetingParams = new HashMap();
 *     targetingParams.put(StreamUrlBuilder.COUNTRY_CODE,  "US");
 *     targetingParams.put(StreamUrlBuilder.POSTAL_CODE,   "12345");
 *     targetingParams.put(StreamUrlBuilder.GENDER,        "m");
 *     targetingParams.put(StreamUrlBuilder.YEAR_OF_BIRTH, "1990");
 *
 *     // Create the authentication token
 *     String token = AuthUtil.createJwtToken("MySecretKey", "MySecretKeyId", true, "foo@bar.com", targetingParams);
 *
 *     // Create the player settings.
 *     Bundle settings = new Bundle();
 *     settings.putBoolean(TritonPlayer.SETTINGS_TARGETING_LOCATION_TRACKING_ENABLED, true);
 *     settings.putSerializable(TritonPlayer.SETTINGS_TARGETING_PARAMS, targetingParams);
 *     settings.putString(TritonPlayer.SETTINGS_AUTH_TOKEN,             token);
 *     settings.putString(TritonPlayer.SETTINGS_STATION_BROADCASTER,    "Triton Digital");
 *     settings.putString(TritonPlayer.SETTINGS_STATION_NAME,           "MOBILEFM");
 *     settings.putString(TritonPlayer.SETTINGS_STATION_MOUNT,          "MOBILEFM_AACV2");
 *
 *     // Create the player.
 *     TritonPlayer player = new TritonPlayer(this, settings);
 *     player.play();
 * @endcode
 */
public final class AuthUtil {

    private static final String TAG = Log.makeTag("AuthUtil");

    private AuthUtil() {}


    /**
     * Creates a JWT header
     *
     * @param secretKeyId   The Secret Key's ID or "null" (provided by Triton)
     */
    static String createJwtHeader(String secretKeyId) {
        String header = "{\"typ\":\"JWT\",\"alg\":\"HS256\"";

        if (!TextUtils.isEmpty(secretKeyId)) {
            header += ",\"kid\":\"" + secretKeyId + "\"";
        }

        return header + "}";
    }


    /**
     * Creates a JWT payload
     *
     * @param userId            User ID (from authentication). Can be null
     * @param registeredUser    True if the user is considered registered
     * @param targetingParams   Triton targeting params (see TritonPlayer::SETTINGS_TARGETING_PARAMS). Can be null
     */
    private static String createJwtPayload(String userId, boolean registeredUser, Map<String, String> targetingParams) {
        JSONObject jsonObj = new JSONObject();

        // Create Json Object using Facebook Data
        try {
            jsonObj.put("iss",    "TdSdk");
            jsonObj.put("aud",    "td");
            jsonObj.put("iat",    getUnixTimestamp());
            jsonObj.put("td-reg", registeredUser);

            if (!TextUtils.isEmpty(userId)) {
                jsonObj.put("sub", userId);
            }

            // Targeting params
            if (targetingParams != null) {
                for (Map.Entry<String, String> entry : targetingParams.entrySet()) {
                    String value = entry.getValue();
                    if (!TextUtils.isEmpty(value)) {
                        jsonObj.put("td-" + entry.getKey(), value);
                    }
                }
            }
        } catch (JSONException e) {
            Assert.fail(TAG, e);
        }

        return jsonObj.toString();
    }


    /**
     * Creates a JWT signature
     *
     * @param encodedJwtHeader   Encoded JWT header
     * @param encodedJwtPayload  Encoded JWT payload
     * @param secretKey          HMAC secret key (provided by Triton)
     *
     * @throws NullPointerException if one of the parameters is null
     */
    static String createEncodedJwsSignature(String encodedJwtHeader, String encodedJwtPayload, String secretKey) {
        if (encodedJwtHeader == null)  { throw new NullPointerException("encodedJwtHeader");}
        if (encodedJwtPayload == null) { throw new NullPointerException("encodedJwtPayload");}
        if (secretKey == null)         { throw new NullPointerException("secretKey");}

        return base64UrlEncode(hashMac((encodedJwtHeader + '.' + encodedJwtPayload), secretKey));
    }


    /**
     * Creates a JWT token
     *
     * @param secretKey       HMAC secret key (provided by Triton)
     * @param secretKeyId     The Secret Key's ID or "null" (provided by Triton)
     * @param registeredUser  True if the user is considered registered
     * @param userId          User ID (from authentication). Can be null
     * @param targetingParams Triton targeting params (see TritonPlayer::SETTINGS_TARGETING_PARAMS). Can be null
     */
    public static String createJwtToken(String secretKey, String secretKeyId, boolean registeredUser, String userId, Map<String, String> targetingParams) {
        // Input message
        String header  = createJwtHeader(secretKeyId);
        String payload = createJwtPayload(userId, registeredUser, targetingParams);
        Log.d(TAG, "Message: " + header + '.' + payload);

        // Signature
        String encodedHeader  = base64UrlEncode(header);
        String encodedPayload = base64UrlEncode(payload);

        // Final token
        String signature = createEncodedJwsSignature(encodedHeader, encodedPayload, secretKey);
        String token = encodedHeader + '.' + encodedPayload + '.' + signature;
        Log.d(TAG, "Token: " + token);

        return token;
    }


    /**
     * Encode to a Base64Url string
     */
    static String base64UrlEncode(String input) {
        return base64UrlEncode(input.getBytes());
    }


    /**
     * Encode to a Base64Url string
     */
    static String base64UrlEncode(byte[] input) {
        return Base64.encodeToString(input, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
    }


    private static int getUnixTimestamp() {
        return (int) (System.currentTimeMillis() / 1000L);
    }


    /**
     * Encrypt with hash mac 256
     */
    private static byte[] hashMac(String text, String secretKey) {

        final String HASH_ALGORITHM = "HmacSHA256";

        try {
            Key sk = new SecretKeySpec(secretKey.getBytes(), HASH_ALGORITHM);
            Mac mac = Mac.getInstance(sk.getAlgorithm());
            mac.init(sk);
            return mac.doFinal(text.getBytes());
        } catch (Exception e) {
            Assert.fail(TAG, e);
            return null;
        }
    }
}
