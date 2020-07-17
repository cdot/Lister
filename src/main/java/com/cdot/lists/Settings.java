/*
 * Copyright C-Dot Consultants 2020 - MIT license
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
    static final String TAG = "Settings";

    static final String UI_PREFERENCES = "UIPreferences";

    // General
    public static final String greyChecked = "greyCheckedItems";
    public static final String strikeThroughChecked = "strikeThroughCheckedItems";
    public static final String leftHandOperation = "checkBoxOnLeftSide";
    public static final String entireRowTogglesItem = "entireRowTogglesItem";
    public static final String alwaysShow = "showListInFrontOfLockScreen";
    public static final String openLatest = "openLatestListAtStartup";
    public static final String warnAboutDuplicates = "warnAboutDuplicates";
    public static final String textSizeIndex = "textSizeIndex";
    public static final String currentList = "currentList";
    public static final String backingStore = "backingStore";
    public static final String showCheckedAtEnd = "moveCheckedItemsToBottom";
    public static final String forceAlphaSort = "forceAlphaSort";
    public static final String autoDeleteChecked = "autoDeleteCheckedItems";

    public static final String cacheFile = "checklists.json";

    // Must match res/values/arrays.xml/text_size_list
    public static final int TEXT_SIZE_DEFAULT = 0;
    public static final int TEXT_SIZE_SMALL = 1;
    public static final int TEXT_SIZE_MEDIUM = 2;
    public static final int TEXT_SIZE_LARGE = 3;

    public static final long INVALID_UID = 0;
    private static long sLastUID = INVALID_UID;

    private SharedPreferences mPrefs;

    private Map<String, Boolean> mBoolPrefs = new HashMap<String, Boolean>() {{
        put(autoDeleteChecked, false);
        put(greyChecked, true);
        put(forceAlphaSort, false);
        put(showCheckedAtEnd, false);
        put(strikeThroughChecked, true);
        put(entireRowTogglesItem, true);

        put(alwaysShow, false);
        put(openLatest, true);
        put(warnAboutDuplicates, true);

        put(leftHandOperation, false);
    }};

    private Map<String, Integer> mIntPrefs = new HashMap<String, Integer>() {{
        put(textSizeIndex, TEXT_SIZE_DEFAULT);
    }};

    private Map<String, Long> mUIDPrefs = new HashMap<String, Long>() {{
        put(currentList, INVALID_UID);
    }};

    private Map<String, Uri> mUriPrefs = new HashMap<String, Uri>() {{
        put(backingStore, null);
    }};

    /**
     * Set the preferences context. Simply used to access preferences.
     *
     * @param cxt the application context, used for all preferences
     */
    Settings(Context cxt) {
        sLastUID = System.currentTimeMillis();
        mPrefs = cxt.getSharedPreferences(UI_PREFERENCES, Context.MODE_PRIVATE);
        for (Map.Entry<String, Boolean> entry : mBoolPrefs.entrySet()) {
            entry.setValue(mPrefs.getBoolean(entry.getKey(), entry.getValue()));
        }
        for (Map.Entry<String, Integer> entry : mIntPrefs.entrySet()) {
            entry.setValue(mPrefs.getInt(entry.getKey(), entry.getValue()));
        }
        for (Map.Entry<String, Long> entry : mUIDPrefs.entrySet()) {
            try {
                entry.setValue(mPrefs.getLong(entry.getKey(), entry.getValue()));
            } catch (ClassCastException ignore) {
            }
        }
        for (Map.Entry<String, Uri> entry : mUriPrefs.entrySet()) {
            Uri ev = entry.getValue();
            String pref = mPrefs.getString(entry.getKey(), ev == null ? null : ev.toString());
            entry.setValue(pref != null ? Uri.parse(pref) : null);
        }
    }

    public int getInt(String name) {
        return mIntPrefs.get(name);
    }

    public void setInt(String name, int value) {
        SharedPreferences.Editor e = mPrefs.edit();
        mIntPrefs.put(name, value);
        e.putInt(name, value);
        e.apply();
    }

    public boolean getBool(String name) {
        return mBoolPrefs.get(name);
    }

    public void setBool(String name, boolean value) {
        SharedPreferences.Editor e = mPrefs.edit();
        mBoolPrefs.put(name, value);
        e.putBoolean(name, value);
        e.apply();
    }

    public long getUID(String name) {
        return mUIDPrefs.get(name);
    }

    public void setUID(String name, long value) {
        SharedPreferences.Editor e = mPrefs.edit();
        mUIDPrefs.put(name, value);
        e.putLong(name, value);
        e.apply();
    }

    public Uri getUri(String name) {
        return mUriPrefs.get(name);
    }

    public void setUri(String name, Uri value) {
        SharedPreferences.Editor e = mPrefs.edit();
        mUriPrefs.put(name, value);
        e.putString(name, value.toString());
        e.apply();
    }

    /**
     * Generate the next unique ID. Being time based, we can be reasonably confident that these
     * are unique, though a simultaneous edit on two platforms might just banjax it.
     *
     * @return a unique ID
     */
    public static long getUID() {
        if (sLastUID == INVALID_UID)
            sLastUID = System.currentTimeMillis();
        return sLastUID++;
    }
}
