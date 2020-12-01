/*
 * Copyright © 2020 C-Dot Consultants
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software
 * and associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING
 * BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.cdot.lists;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import java.util.HashMap;
import java.util.Map;

/**
 * Simpleton settings. Some settings are repeated in individual lists, where they override the
 * defaults.
 */
public class Settings {
    //private static final String TAG = "Settings";

    static final String UI_PREFERENCES = "UIPreferences";

    // Options
    public static final String alwaysShow = "showListInFrontOfLockScreen";
    public static final String autoDeleteChecked = "autoDeleteCheckedItems";
    public static final String currentListUID = "currentList";
    public static final String debug = "debug";
    public static final String defaultAlphaSort = "forceAlphaSort";
    public static final String dimChecked = "greyCheckedItems";
    public static final String entireRowToggles = "entireRowTogglesItem";
    public static final String lastStoreSaveFailed = "lastStoreSaveFailed";
    public static final String leftHandOperation = "checkBoxOnLeftSide";
    public static final String showCheckedAtEnd = "moveCheckedItemsToBottom";
    public static final String strikeChecked = "strikeThroughCheckedItems";
    public static final String textSizeIndex = "textSizeIndex";
    public static final String uri = "backingStore";
    public static final String warnAboutDuplicates = "warnAboutDuplicates";

    public static final String cacheFile = "checklists.json";

    // Must match res/values/strings.xml/text_size_list
    public static final int TEXT_SIZE_DEFAULT = 0;
    public static final int TEXT_SIZE_SMALL = 1;
    public static final int TEXT_SIZE_MEDIUM = 2;
    public static final int TEXT_SIZE_LARGE = 3;

    public static final long INVALID_UID = 0;
    private static long sLastUID = INVALID_UID;

    private static SharedPreferences sPrefs;

    private static Map<String, Boolean> sBoolPrefs = new HashMap<String, Boolean>() {{
        put(alwaysShow, false);
        put(autoDeleteChecked, false);
        put(debug, false);
        put(dimChecked, true);
        put(defaultAlphaSort, false);
        put(entireRowToggles, true);
        put(lastStoreSaveFailed, false);
        put(leftHandOperation, false);
        put(showCheckedAtEnd, false);
        put(strikeChecked, true);
        put(warnAboutDuplicates, true);
    }};

    private static Map<String, Integer> sIntPrefs = new HashMap<String, Integer>() {{
        put(textSizeIndex, TEXT_SIZE_DEFAULT);
    }};

    private static Map<String, Long> sLongPrefs = new HashMap<String, Long>() {{
        put(currentListUID, INVALID_UID);
    }};

    private static Map<String, Uri> sUriPrefs = new HashMap<String, Uri>() {{
        put(uri, null);
    }};

    /**
     * Set the preferences context. Simply used to access preferences.
     *
     * @param cxt the application context, used for all preferences
     */
    public static void setContext(Context cxt) {
        sLastUID = System.currentTimeMillis();
        sPrefs = cxt.getSharedPreferences(UI_PREFERENCES, Context.MODE_PRIVATE);
        for (Map.Entry<String, Boolean> entry : sBoolPrefs.entrySet()) {
            entry.setValue(sPrefs.getBoolean(entry.getKey(), entry.getValue()));
        }
        for (Map.Entry<String, Integer> entry : sIntPrefs.entrySet()) {
            entry.setValue(sPrefs.getInt(entry.getKey(), entry.getValue()));
        }
        for (Map.Entry<String, Long> entry : sLongPrefs.entrySet()) {
            try {
                entry.setValue(sPrefs.getLong(entry.getKey(), entry.getValue()));
            } catch (ClassCastException ignore) {
            }
        }
        for (Map.Entry<String, Uri> entry : sUriPrefs.entrySet()) {
            Uri ev = entry.getValue();
            String pref = sPrefs.getString(entry.getKey(), ev == null ? null : ev.toString());
            entry.setValue(pref != null ? Uri.parse(pref) : null);
        }
    }

    public static int getInt(String name) {
        return sIntPrefs.get(name);
    }

    public static void setInt(String name, int value) {
        SharedPreferences.Editor e = sPrefs.edit();
        sIntPrefs.put(name, value);
        e.putInt(name, value);
        e.apply();
    }

    public static boolean getBool(String name) {
        return sBoolPrefs.get(name);
    }

    public static void setBool(String name, boolean value) {
        SharedPreferences.Editor e = sPrefs.edit();
        sBoolPrefs.put(name, value);
        e.putBoolean(name, value);
        e.apply();
    }

    public static long getLong(String name) {
        return sLongPrefs.get(name);
    }

    public static void setLong(String name, long value) {
        SharedPreferences.Editor e = sPrefs.edit();
        sLongPrefs.put(name, value);
        e.putLong(name, value);
        e.apply();
    }

    public static Uri getUri(String name) {
        return sUriPrefs.get(name);
    }

    public static void setUri(String name, Uri value) {
        SharedPreferences.Editor e = sPrefs.edit();
        sUriPrefs.put(name, value);
        e.putString(name, value.toString());
        e.apply();
    }
}
