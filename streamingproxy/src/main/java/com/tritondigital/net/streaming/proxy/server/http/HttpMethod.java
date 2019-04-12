package com.tritondigital.net.streaming.proxy.server.http;

/**
 * Represents all supported methods of HTTP.
 * Used when processing an Http Request.
 *
 * Call toString() to have the exact string that is expected in the request
 * (e.g. 'OPTIONS').
 */
class HttpMethod
{
    public enum Method
    {
        GET("GET"),
        HEAD("HEAD");

        /////////////////////////////

        private final String mMethod;

        Method(String method)
        {
            mMethod = method;
        }

        @Override
        public String toString()
        {
            return mMethod;
        }
    }
}
