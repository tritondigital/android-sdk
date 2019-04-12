package com.tritondigital.net.streaming.proxy.server.http;

/**
 * Represents all supported headers of RTSP, including request and response.
 * Used when processing an Http request and when building an Http Response.
 *
 * Call toString() to have the exact string that is expected in the request and
 * that should be put in the response (e.g. 'Content-Length').
 */
class HttpHeaderField
{
    public enum Field
    {
        CACHE_CONTROL("Cache-Control"),
        CONTENT_LENGTH("Content-Length"),
        CONTENT_RANGE("Content-Range"),
        CONTENT_TYPE("Content-Type"),
        DATE("Date"),
        EXPIRES("Expires"),
        IF_RANGE("If-Range"),
        PRAGMA("Pragma"),
        RANGE("Range"),
        SERVER("Server");

        /////////////////////////////

        private final String mName;

        Field(String name)
        {
            mName = name;
        }

        @Override
        public String toString()
        {
            return mName;
        }
    }
}
