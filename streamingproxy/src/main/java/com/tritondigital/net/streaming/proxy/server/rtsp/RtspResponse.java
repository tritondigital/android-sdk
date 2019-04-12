package com.tritondigital.net.streaming.proxy.server.rtsp;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents an Rtsp Response.
 * Filled when the request is processed, then sent back to the client.
 *
 * Call toString() to have the string that should be sent to the client.
 */

class RtspResponse
{
    protected static final String CRLF      = "\r\n";

    private RtspVersion.Version mVersion;
    private RtspResponseStatus.Status mStatus;
    private final LinkedHashMap<RtspHeaderField.Field, String> mHeader;
    private String mContent;

    public RtspResponse()
    {
        mHeader = new LinkedHashMap<>();
    }


    public void                setVersion(RtspVersion.Version version) { mVersion = version; }

    public void                      setStatus(RtspResponseStatus.Status status) { mStatus = status; }

    public void    setHeader(RtspHeaderField.Field field, String value) { mHeader.put(field, value); }
    public void    setHeader(RtspHeaderField.Field field, int value)    { mHeader.put(field, "" + value); }

    public void   setContent(String content) { mContent = content; }

    @Override
    public String toString()
    {
        // Version + Status
        StringBuilder stringBuffer = new StringBuilder(mVersion + " " + mStatus + CRLF);

        // Headers
        for (Map.Entry<RtspHeaderField.Field, String> curEntry : mHeader.entrySet())
            stringBuffer.append(curEntry.getKey()).append(": ").append(curEntry.getValue()).append(CRLF);

        stringBuffer.append(CRLF);

        // Body
        if (mContent != null)
        {
            stringBuffer.append(mContent);
        }

        // End of request
        if (!stringBuffer.toString().endsWith(CRLF))
            stringBuffer.append(CRLF);

        return stringBuffer.toString();
    }
}
