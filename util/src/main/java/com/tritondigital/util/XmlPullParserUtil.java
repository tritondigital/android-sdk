package com.tritondigital.util;

import android.util.Patterns;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;


@SuppressWarnings("unused")
public final class XmlPullParserUtil {

    private static final String TAG = Log.makeTag("XmlPullParserUtil");

    private XmlPullParserUtil() {}


    public static int readInt(XmlPullParser parser, int defaultValue) throws IOException, XmlPullParserException {
        String text = readText(parser);

        if (text != null) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException e) {
                Log.w(TAG, e, "readInt: \"" + text + "\"");
            }
        }

        return defaultValue;
    }


    public static float readFloat(XmlPullParser parser, float defaultValue) throws IOException, XmlPullParserException {
        String text = readText(parser);

        if (text != null) {
            try {
                return Float.parseFloat(text);
            } catch (NumberFormatException e) {
                Log.w(TAG, e, "readFloat: \"" + text + "\"");
            }
        }

        return defaultValue;
    }


    public static long readLong(XmlPullParser parser, long defaultValue) throws IOException, XmlPullParserException {
        String text = readText(parser);

        if (text != null) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException e) {
                Log.w(TAG, e, "readLong: \"" + text + "\"");
            }
        }

        return defaultValue;
    }


    public static String readText(XmlPullParser parser) throws IOException, XmlPullParserException {
        String text = null;
        if (parser.next() == XmlPullParser.TEXT) {
            text = parser.getText();
            parser.nextTag();
        }

        return (text == null) ? null : text.trim();
    }


    public static String readUrlText(XmlPullParser parser) throws IOException, XmlPullParserException {
        String url = XmlPullParserUtil.readText(parser);
        return ((url != null) && Patterns.WEB_URL.matcher(url).matches()) ? url : null;
    }


    public static void skip(XmlPullParser parser) throws XmlPullParserException, IOException {
        if (parser.getEventType() != XmlPullParser.START_TAG) {
            throw new IllegalStateException();
        }

        int depth = 1;
        while (depth != 0) {
            switch (parser.next()) {
                case XmlPullParser.END_TAG:
                    depth--;
                    break;

                case XmlPullParser.START_TAG:
                    depth++;
                    break;
            }
        }
    }
}
