package com.tritondigital.util;


/**
 * Basic log wrapper
 */
public final class Log {
    private static final String TAG_PREFIX = "Td";
    private static final int TAG_PREFIX_LENGTH = TAG_PREFIX.length();
    private static final int MAX_TAG_LENGTH = 23;


    private Log() {}

    // Based on Android Universal Music Player
    public static String makeTag(String str) {
        if (str.length() > MAX_TAG_LENGTH - TAG_PREFIX_LENGTH) {
            return TAG_PREFIX + str.substring(0, MAX_TAG_LENGTH - TAG_PREFIX_LENGTH - 1);
        }

        return TAG_PREFIX + str;
    }


    public static void v(String tag, Object... messages) {
        log(tag, android.util.Log.VERBOSE, null, messages);
    }


    public static void d(String tag, Object... messages) {
        log(tag, android.util.Log.DEBUG, null, messages);
    }


    public static void i(String tag, Object... messages) {
        log(tag, android.util.Log.INFO, null, messages);
    }


    public static void w(String tag, Object... messages) {
        log(tag, android.util.Log.WARN, null, messages);
    }


    public static void w(String tag, Throwable t, Object... messages) {
        log(tag, android.util.Log.WARN, t, messages);
    }


    public static void e(String tag, Object... messages) {
        log(tag, android.util.Log.ERROR, null, messages);
    }


    public static void e(String tag, Throwable t, Object... messages) {
        log(tag, android.util.Log.ERROR, t, messages);
    }


    public static void log(String tag, int level, Throwable t, Object... messages) {
        if (Debug.isDebugMode() || android.util.Log.isLoggable(tag, level)) {
            String message;
            if (t == null && messages != null && messages.length == 1) {
                // handle this common case without the extra cost of creating a string buffer:
                message = messages[0].toString();
            } else {
                StringBuilder sb = new StringBuilder();
                if (messages != null) for (Object m : messages) {
                    sb.append(m);
                }
                if (t != null) {
                    sb.append("\n").append(android.util.Log.getStackTraceString(t));
                }
                message = sb.toString();
            }
            android.util.Log.println(level, tag, message);
        }
    }
}
