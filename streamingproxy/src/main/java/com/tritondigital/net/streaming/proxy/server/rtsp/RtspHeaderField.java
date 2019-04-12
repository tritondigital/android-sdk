package com.tritondigital.net.streaming.proxy.server.rtsp;

/**
 * Represents all supported headers of RTSP, including request and response.
 * Used when processing an Rtsp request and when building an Rtsp Response.
 *
 * Call toString() to have the exact string that is expected in the request and
 * that should be put in the response (e.g. 'Content-Length').
 */
class RtspHeaderField
{
    public enum Field
    {
        ALLOW("Allow"),
        CONTENT_BASE("Content-Base"),
        CONTENT_LENGTH("Content-Length"),
        CONTENT_TYPE("Content-Type"),
        DATE("Date"),
        CSEQ("CSeq"),
        LAST_MODIFIED("Last-Modified"),
        PUBLIC("Public"),
        RANGE("Range"),
        RTP_INFO("RTP-Info"),
        SERVER("Server"),
        SESSION("Session"),
        TRANSPORT("Transport");

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
