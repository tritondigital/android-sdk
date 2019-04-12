package com.tritondigital.net.streaming.proxy.server.http;

/**
 * Represents all supported versions of HTTP.
 * Used when building an Http Response.
 *
 * Call toString() to have the exact string that should be put in the response
 * (e.g. 'HTTP/1.0').
 */
class HttpVersion
{
    public enum Version
    {
        HTTP_1_0("HTTP/1.0"),
        HTTP_1_1("HTTP/1.1");

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
