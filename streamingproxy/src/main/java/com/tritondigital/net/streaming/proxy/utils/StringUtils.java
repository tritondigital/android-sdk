package com.tritondigital.net.streaming.proxy.utils;


public class StringUtils
{
    public static String byteArrayToString(byte[] data)
    {
        StringBuilder sb = new StringBuilder();
        for (byte aData : data) {
            sb.append(getHexByte(aData));
        }
        return sb.toString();
    }

    public static String getHexByte(byte aByte)
    {
        int x = aByte;
        if (x<0) x=x+256;
        String s = Integer.toHexString(x).toUpperCase();
        while (s.length()<2) s="0"+s;
        return s;
    }
}
