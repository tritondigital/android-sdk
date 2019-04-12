package com.tritondigital.sdksample;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.inputmethod.InputMethodManager;

import java.util.Set;


public class Util {

    public static void hideKeyboard(Activity activity) {
        if (activity != null) {
            InputMethodManager im = (InputMethodManager) activity.getApplicationContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            im.hideSoftInputFromWindow(activity.getWindow().getDecorView().getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
        }
    }


    /**
     * Compares two bundles to see if they have the same content.
     *
     * Warning: Slow!!
     */
    public static boolean bundleEquals(Bundle bundle0, Bundle bundle1) {
        // Quick compare
        if ((bundle0 == null) && (bundle1 == null)) {
            return true;
        }
        else if ((bundle0 == null) || (bundle1 == null)) {
            return false;
        }
        else if (bundle0.size() != bundle1.size()) {
            return false;
        }

        Set<String> setOne = bundle0.keySet();
        Object valueOne;
        Object valueTwo;

        for (String key : setOne) {
            valueOne = bundle0.get(key);
            valueTwo = bundle1.get(key);
            if ((valueOne instanceof Bundle) && (valueTwo instanceof Bundle)) {
                if (!bundleEquals((Bundle) valueOne, (Bundle) valueTwo)) {
                    return false;
                }
            } else if (valueOne == null) {
                if ((valueTwo != null) || !bundle1.containsKey(key)) {
                    return false;
                }
            } else if (!valueOne.equals(valueTwo)) {
                return false;
            }
        }

        return true;
    }
}
