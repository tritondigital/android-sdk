package com.tritondigital.net.streaming.proxy.utils;


/**
 * Class to facilitate logging. Ideally, this class would use different logging mechanism, based on the platform on which the code is executed.
 * However, it currently only uses System.out.print.
 */
@SuppressWarnings({"FieldCanBeLocal", "PointlessBooleanExpression", "ConstantConditions"})
public class Log
{
    private static final boolean GLOBAL_LOG_ENABLED = false;
    private static boolean       mUserLogEnabled    = true;

    public static void v(String tag, String msg) { if (GLOBAL_LOG_ENABLED && mUserLogEnabled) multilineLog(tag, msg); }
    public static void i(String tag, String msg) { if (GLOBAL_LOG_ENABLED && mUserLogEnabled) multilineLog(tag, msg); }
    public static void d(String tag, String msg) { if (GLOBAL_LOG_ENABLED && mUserLogEnabled) multilineLog(tag, msg); }
    public static void w(String tag, String msg) { if (GLOBAL_LOG_ENABLED && mUserLogEnabled) multilineLog(tag, msg); }
    public static void e(String tag, String msg) { if (GLOBAL_LOG_ENABLED && mUserLogEnabled) multilineLog(tag, msg); }

    public static void setEnabled(boolean enabled) { mUserLogEnabled = enabled; }

    /**
     * Logs a multiline message line by line and append the tag as a prefix to each line.
     */
    private static void multilineLog(String tag, String msg)
    {
        for (String curLine : msg.split("\n"))
            System.out.println("[" + tag + "]    " +  curLine);
    }
}
