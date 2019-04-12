package com.tritondigital.net.streaming.proxy.server.rtsp;


/**
 * Represents all supported versions of RTSP.
 * Used when building an Rtsp Response.
 *
 * Call toString() to have the exact string that should be put in the response
 * (e.g. 'RTSP/1.0').
 */
class RtspVersion
{
    public enum Version
    {
        RTSP_1_0("RTSP/1.0");

        /////////////////////////////

        private final String mVersion;

        Version(String version)
        {
            mVersion = version;
        }

        @Override
        public String toString()
        {
            return mVersion;
        }

        public static Version getEnum(String value)
        {
            for (Version enumValue : Version.values())
            {
                if (enumValue.toString().compareTo(value) == 0)
                {
                    return enumValue;
                }
            }
            throw new IllegalArgumentException("Invalid Version value: " + value);
        }
    }
}
