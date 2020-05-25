package com.cdot.lists;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import java.util.HashMap;
import java.util.Map;

class Settings {
    static final String UI_PREFERENCES = "UIPreferences";

    static String greyCheckedItems = "greyCheckedItems";
    static String strikeThroughCheckedItems = "strikeThroughCheckedItems";
    static String moveCheckedItemsToBottom = "moveCheckedItemsToBottom";
    static String checkBoxOnLeftSide = "checkBoxOnLeftSide";
    static String autoDeleteCheckedItems = "autoDeleteCheckedItems";
    static String entireRowTogglesItem = "entireRowTogglesItem";
    static String addNewItemsAtTopOfList = "addNewItemsAtTopOfList";
    static String showListInFrontOfLockScreen = "showListInFrontOfLockScreen";
    static String openLatestListAtStartup = "openLatestListAtStartup";
    static String warnAboutDuplicates = "warnAboutDuplicates";
    static String textSizeIndex = "textSizeIndex";
    static String currentList = "currentList";
    static String backingStore = "backingStore";

    static String cacheFile = "checklists.json";

    // Must match res/values/arrays.xml/text_size_list
    static final int TEXT_SIZE_DEFAULT = 0;
    static final int TEXT_SIZE_SMALL = 1;
    static final int TEXT_SIZE_MEDIUM = 2;
    static final int TEXT_SIZE_LARGE = 3;

    private static SharedPreferences mPrefs;

    private static Map<String, Boolean> mBoolPrefs = new HashMap<String, Boolean>() {{
        put(autoDeleteCheckedItems, false);
        put(greyCheckedItems, true);
        put(moveCheckedItemsToBottom, false);
        put(strikeThroughCheckedItems, true);
        put(entireRowTogglesItem, true);

        put(addNewItemsAtTopOfList, false);
        put(showListInFrontOfLockScreen, false);
        put(openLatestListAtStartup, true);
        put(warnAboutDuplicates, true);

        put(checkBoxOnLeftSide, false);
    }};

    private static Map<String, Integer> mIntPrefs = new HashMap<String, Integer>() {{
        put(textSizeIndex, TEXT_SIZE_DEFAULT);
    }};

    private static Map<String, String> mStringPrefs = new HashMap<String, String>() {{
        put(currentList, null);
    }};

    private static Map<String, Uri> mUriPrefs = new HashMap<String, Uri>() {{
        put(backingStore, null);
    }};

    /**
     * Set the preferences context. Simply used to access preferences.
     *
     * @param cxt the application context, used for all preferences
     */
    static void setContext(Context cxt) {
        mPrefs = cxt.getSharedPreferences(UI_PREFERENCES, Context.MODE_PRIVATE);
        for (Map.Entry<String, Boolean> entry : mBoolPrefs.entrySet()) {
            entry.setValue(mPrefs.getBoolean(entry.getKey(), entry.getValue()));
        }
        for (Map.Entry<String, Integer> entry : mIntPrefs.entrySet()) {
            entry.setValue(mPrefs.getInt(entry.getKey(), entry.getValue()));
        }
        for (Map.Entry<String, String> entry : mStringPrefs.entrySet()) {
            entry.setValue(mPrefs.getString(entry.getKey(), entry.getValue()));
        }
        for (Map.Entry<String, Uri> entry : mUriPrefs.entrySet()) {
            Uri ev = entry.getValue();
            String pref = mPrefs.getString(entry.getKey(), ev == null ? null : ev.toString());
            entry.setValue(pref != null ? Uri.parse(pref) : null);
        }
    }

    static int getInt(String name) {
        return mIntPrefs.get(name);
    }

    static void setInt(String name, int value) {
        SharedPreferences.Editor e = mPrefs.edit();
        mIntPrefs.put(name, value);
        e.putInt(name, value);
        e.apply();
    }

    static boolean getBool(String name) {
        return mBoolPrefs.get(name);
    }

    static void setBool(String name, boolean value) {
        SharedPreferences.Editor e = mPrefs.edit();
        mBoolPrefs.put(name, value);
        e.putBoolean(name, value);
        e.apply();
    }

    static String getString(String name) {
        return mStringPrefs.get(name);
    }

    static void setString(String name, String value) {
        SharedPreferences.Editor e = mPrefs.edit();
        mStringPrefs.put(name, value);
        e.putString(name, value);
        e.apply();
    }

    static Uri getUri(String name) {
        return mUriPrefs.get(name);
    }

    static void setUri(String name, Uri value) {
        SharedPreferences.Editor e = mPrefs.edit();
        mUriPrefs.put(name, value);
        e.putString(name, value.toString());
        e.apply();
    }
}
