package com.tritondigital.net.streaming.proxy.server.rtsp;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents an Rtsp Request.
 * Filled when each line is read from the server.
 *
 * Call toString() to have the string representing the original request.
 * Note that only fields that are actually used are in the map (and therefore are present in the string version
 * obtained by toString). Supported fields are all the values in the RtspHeaderField enum.
 */
class RtspRequest
{
    protected static final String CRLF      = "\r\n";

    private RtspMethod.Method mMethod;
    private String mUri;
    private RtspVersion.Version mVersion;
    private final LinkedHashMap<RtspHeaderField.Field, String> mHeader;

    public RtspRequest()
    {
        mHeader = new LinkedHashMap<>();
    }

    public void              setMethod(RtspMethod.Method method) { mMethod = method; }
    public RtspMethod.Method getMethod()                         { return mMethod; }

    public void   setUri(String uri) { mUri = uri; }
    public String getUri()           { return mUri; }

    public void                setVersion(RtspVersion.Version version) { mVersion = version; }

    public void    setHeader(RtspHeaderField.Field field, String value) { mHeader.put(field, value); }
    public String  getHeader(RtspHeaderField.Field field)               { return mHeader.get(field); }
    public boolean containsHeader(RtspHeaderField.Field field)          { return mHeader.containsKey(field); }

    @Override
    public String toString()
    {
        // Method
        StringBuilder stringBuffer = new StringBuilder(mMethod + " " +  mUri + " " + mVersion + CRLF);

        // Headers
        for (Map.Entry<RtspHeaderField.Field, String> curEntry : mHeader.entrySet())
            stringBuffer.append(curEntry.getKey()).append(": ").append(curEntry.getValue()).append(CRLF);

        // End of request
        stringBuffer.append(CRLF);

        return stringBuffer.toString();
    }
}
