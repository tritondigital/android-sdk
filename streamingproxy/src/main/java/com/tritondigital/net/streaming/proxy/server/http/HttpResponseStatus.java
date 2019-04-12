package com.tritondigital.net.streaming.proxy.server.http;

/**
 * Represents all supported status of HTTP response.
 * Used when building an Http Response.
 *
 * Call toString() to have the exact string that should be put in the response
 * (e.g. '404 Not Found').
 */
@SuppressWarnings("ALL")
class HttpResponseStatus
{
    public enum Status
    {
        OK(200, "OK"),
        PARTIAL_CONTENT(206, "Partial Content"),

        METHOD_NOT_ALLOWED(405, "Method Not Allowed"),

        INTERNAL_SERVER_ERROR(500, "Internal Server Error");

        /////////////////////////////

        private int     mCode;
        private String  mMsg;

        Status(int code, String msg)
        {
            mCode = code;
            mMsg = msg;
        }

        @Override
        public String toString()
        {
            return "" + mCode + " " + mMsg;
        }
    }
}
