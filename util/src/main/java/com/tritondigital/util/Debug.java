package com.tritondigital.util;


import java.io.*;
import java.lang.reflect.Method;

import android.content.*;
import android.content.pm.*;
import android.os.*;

import java.security.cert.*;

import javax.security.auth.x500.*;

public final class Debug {

    private static boolean sDebugModeReady;
    private static boolean sDebugMode;

    private Debug() {}


    /**
     * See https://code.google.com/p/android/issues/detail?id=52962
     */
    public static boolean isDebugMode() {
        if (!sDebugModeReady) {
            try {
                sDebugModeReady = true;
                final Class<?> activityThread = Class.forName("android.app.ActivityThread");
                final Method currentPackage = activityThread.getMethod("currentPackageName");
                final String packageName = (String) currentPackage.invoke(null, (Object[]) null);
                sDebugMode = "com.tritondigital.testapp".equals(packageName);
            } catch (final Throwable t) {
                sDebugMode = false;
            }
        }
        return sDebugMode;
    }


    public static void renameThread(String name) {
        if (isDebugMode()) {
            String tag = Log.makeTag(name);
            Thread.currentThread().setName(tag);
        }
    }



    private static final X500Principal DEBUG_DN = new X500Principal("CN=Android Debug,O=Android,C=US");

    /**
     * see  http://stackoverflow.com/questions/7085644/how-to-check-if-apk-is-signed-or-debug-build
     */
    public static boolean isAppSignedInDebug(Context context)
    {
        boolean isInDebug = false;

        try
        {
            PackageInfo pinfo = context.getPackageManager().getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNATURES);
            Signature signatures[] = pinfo.signatures;

            CertificateFactory cf = CertificateFactory.getInstance("X.509");

            for ( int i = 0; i < signatures.length;i++)
            {
                ByteArrayInputStream stream = new ByteArrayInputStream(signatures[i].toByteArray());
                X509Certificate cert = (X509Certificate) cf.generateCertificate(stream);
                isInDebug = cert.getSubjectX500Principal().equals(DEBUG_DN);
                if (isInDebug)
                    break;
            }
        }
        catch (PackageManager.NameNotFoundException e)
        {
            //debuggable variable will remain false
        }
        catch (CertificateException e)
        {
            //debuggable variable will remain false
        }
        return isInDebug;
    }



    /**
     * Simple timer class which count up until stopped.
     * Inspired by {@link android.os.CountDownTimer}
     */
    public static class CountUpTimer {

        private static final long INTERVAL = 100; //100ms for the interval
        private long elapsedTime;
        private long base;

        CountUpTimer() {
            this.elapsedTime = 0;
        }

        void start() {
            base = SystemClock.elapsedRealtime();
            handler.sendMessage(handler.obtainMessage(MSG));
        }

        long stop() {
            handler.removeMessages(MSG);
            return elapsedTime;
        }

        void reset() {
            synchronized (this) {
                base = SystemClock.elapsedRealtime();
                elapsedTime = 0;
            }
        }

        void onTick(long elapsedTime){};

        static final int MSG = 1;

        Handler handler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                synchronized (CountUpTimer.this) {
                    elapsedTime = SystemClock.elapsedRealtime() - base;
                    onTick(elapsedTime);
                    sendMessageDelayed(obtainMessage(MSG), INTERVAL);
                }
            }
        };
    }
}
