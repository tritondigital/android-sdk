package com.tritondigital.net.streaming.proxy.server.http;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents an Http Response.
 * Filled when the request is processed, then sent back to the client.
 *
 * Call toString() to have the string that should be sent to the client.
 */

@SuppressWarnings("ALL")
class HttpResponse
{
    protected static final String CRLF      = "\r\n";

    private HttpVersion.Version mVersion;
    private HttpResponseStatus.Status mStatus;
    private LinkedHashMap<HttpHeaderField.Field, String> mHeader;
    private String mContent;

    public HttpResponse()
    {
        mHeader = new LinkedHashMap<>();
    }


    public void                setVersion(HttpVersion.Version version) { mVersion = version; }
    public HttpVersion.Version getVersion()                            { return mVersion; }

    public HttpResponseStatus.Status getStatus()                                 { return mStatus; }
    public void                      setStatus(HttpResponseStatus.Status status) { mStatus = status; }

    public void   setHeader(HttpHeaderField.Field field, String value) { mHeader.put(field, value); }
    public String getContent()               { return mContent; }
    public void   setContent(String content) { mContent = content; }

    @Override
    public String toString()
    {
        // Version + Status
        StringBuilder stringBuffer = new StringBuilder(mVersion + " " + mStatus + CRLF);

        // Headers
        for (Map.Entry<HttpHeaderField.Field, String> curEntry : mHeader.entrySet())
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
