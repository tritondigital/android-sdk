package com.tritondigital.net.streaming.proxy.server.http;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents an Http Request.
 * Filled when each line is read from the server.
 *
 * Call toString() to have the string representing the original request.
 * Note that only fields that are actually used are in the map (and therefore are present in the string version
 * obtained by toString). Supported fields are all the values in the HttpHeaderField enum.
 */
class HttpRequest
{
    protected static final String CRLF      = "\r\n";

    private HttpMethod.Method mMethod;
    private String mUri;
    private HttpVersion.Version mVersion;
    private final LinkedHashMap<HttpHeaderField.Field, String> mHeader;

    public HttpRequest()
    {
        mHeader = new LinkedHashMap<>();
    }

    public void              setMethod(HttpMethod.Method method) { mMethod = method; }
    public HttpMethod.Method getMethod()                         { return mMethod; }

    public void   setUri(String uri) { mUri = uri; }

    public void                setVersion(HttpVersion.Version version) { mVersion = version; }

    public void    setHeader(HttpHeaderField.Field field, String value) { mHeader.put(field, value); }


    @Override
    public String toString()
    {
        // Method
        StringBuilder stringBuffer = new StringBuilder(mMethod + " " +  mUri + " " + mVersion + CRLF);

        // Headers
        for (Map.Entry<HttpHeaderField.Field, String> curEntry : mHeader.entrySet())
            stringBuffer.append(curEntry.getKey()).append(": ").append(curEntry.getValue()).append(CRLF);

        // End of request
        stringBuffer.append(CRLF);

        return stringBuffer.toString();
    }
}
