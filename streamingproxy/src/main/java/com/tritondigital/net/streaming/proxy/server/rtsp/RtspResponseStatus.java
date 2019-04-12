package com.tritondigital.net.streaming.proxy.server.rtsp;

/**
 * Represents all supported status of RTSP response.
 * Used when building an Rtsp Response.
 *
 * Call toString() to have the exact string that should be put in the response
 * (e.g. '404 Not Found').
 */
@SuppressWarnings("ALL")
class RtspResponseStatus
{
    public enum Status
    {
        OK(200, "OK"),

        BAD_REQUEST(400, "Bad Request"),
        METHOD_NOT_ALLOWED(405, "Method Not Allowed"),
        SESSION_NOT_FOUND(454, "Session Not Found"),
        UNSUPPORTED_TRANSPORT(461, "Unsupported transport"),

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
