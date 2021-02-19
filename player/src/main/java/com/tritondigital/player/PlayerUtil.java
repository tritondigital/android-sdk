package com.tritondigital.player;


import androidx.mediarouter.media.MediaControlIntent;
import androidx.mediarouter.media.MediaRouteSelector;
import android.text.TextUtils;

import java.util.Locale;

public final class PlayerUtil {

    private PlayerUtil() {}


    /**
     * Remove the ".dev" of ".preprod" suffix
     */
    public static String removeMountSuffix(String mount) {
        int dotIdx = mount.indexOf('.');
        if (dotIdx != -1) {
            mount = mount.substring(0, dotIdx);
        }

        return mount;
    }


    /**
     * Creates a media route selector compatible with our remote player
     */
    public static MediaRouteSelector createRemotePlaybackRouteSelector() {
        return new MediaRouteSelector.Builder()
                .addControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)
                .build();
    }


    /**
     * Returns the best MIME type for the provided filename
     */
    public static String fileNameToMimeType(String fileName) {
        if (!TextUtils.isEmpty(fileName)) {
            fileName = fileName.toLowerCase(Locale.ENGLISH);

            if (fileName.contains(".aac") ||
                    fileName.contains("_aac") ||
                    fileName.contains("aac.") ||
                    fileName.contains("aac_") ||
                    fileName.contains("aac/") ||
                    fileName.contains("aac:") ||
                    fileName.endsWith("aac")) {
                return PlayerConsts.MIME_TYPE_AAC;
            }
        }

        return PlayerConsts.MIME_TYPE_MPEG;
    }


    /**
     * Normalize the MIME type to a known value
     */
    public static String normalizeMimeType(String mimeType) {
        if (!TextUtils.isEmpty(mimeType)) {
            mimeType = mimeType.toLowerCase(Locale.ENGLISH);

            if (mimeType.contains("mp3") || mimeType.contains("mpeg") || mimeType.contains("mpg")) {
                return PlayerConsts.MIME_TYPE_MPEG;
            } else if (mimeType.contains("aac")) {
                return PlayerConsts.MIME_TYPE_AAC;
            }
        }

        return mimeType;
    }


    /**
     * Returns the best transport for the provided filename
     */
    public static String fileNameToTransport(String fileName) {
        if (fileName != null) {
            fileName = fileName.toLowerCase(Locale.ENGLISH);

            if (fileName.contains(".flv")
                    || fileName.contains("_flv")
                    || fileName.endsWith("flv")) {
                return PlayerConsts.TRANSPORT_FLV;

            } else if (fileName.contains(".hls")
                    || fileName.contains("_hls")
                    || fileName.contains(".m3u8")
                    || fileName.contains("hls:")
                    || fileName.endsWith("hls")) {
                return PlayerConsts.TRANSPORT_HLS;
            }
        }

        return PlayerConsts.TRANSPORT_SC;
    }


    /**
     * Normalize the transport to a known value
     */
    public static String normalizeTransport(String transport) {
        if (!TextUtils.isEmpty(transport)) {
            transport = transport.toLowerCase(Locale.ENGLISH);

            if      (transport.contains("flv")) { return PlayerConsts.TRANSPORT_FLV; }
            else if (transport.contains("hls")) { return PlayerConsts.TRANSPORT_HLS; }
            else if (transport.contains("sc"))  { return PlayerConsts.TRANSPORT_SC; }
        }

        return transport;
    }


    /**
     * Returns true if the provided mount has a valid name
     *
     * TODO: what is our mount name limitations? Nobody seems to know!
     */
    static boolean isMountNameValid(String mount) {
        return !TextUtils.isEmpty(mount);
    }
}
