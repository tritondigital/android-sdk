package com.tritondigital.net.streaming.proxy.server.rtsp;

/**
 * Represents all supported methods of RTSP.
 * Used when processing an Rtsp Request.
 *
 * Call toString() to have the exact string that is expected in the request
 * (e.g. 'OPTIONS').
 */
class RtspMethod
{
    public enum Method
    {
        ANNOUNCE("ANNOUNCE"),
        DESCRIBE("DESCRIBE"),
        GET_PARAMETER("GET_PARAMETER"),
        OPTIONS("OPTIONS"),
        SETUP("SETUP"),
        PAUSE("PAUSE"),
        PLAY("PLAY"),
        RECORD("RECORD"),
        REDIRECT("REDIRECT"),
        SET_PARAMETER("GET_PARAMETER"),
        TEARDOWN("TEARDOWN");

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
