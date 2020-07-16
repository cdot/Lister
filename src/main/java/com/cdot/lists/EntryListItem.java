/*
 * Copyright C-Dot Consultants 2020 - MIT license
 */
package com.cdot.lists;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Interface to items in an EntryList
 */
interface EntryListItem {
    /**
     * Get a unique integer that identifies this item. We use getTimeMillis to generate this UID,
     * as the probability of a clash is infinitesmial.
     */
     long getUID();

    /**
     * Get the list that contains this item
     * @return the containing list, or null for the root
     */
    EntryList getContainer();

    /**
     * Set the item's text
     *
     * @param str new text
     */
    void setText(String str);

    /**
     * Return false if the item is not moveable in the current list view
     */
    boolean isMoveable();

    /**
     * Get the item's text
     *
     * @return a text string representing the item in the list
     */
    String getText();

    /**
     * Notify any views of this list that the list contents have changed and redisplay is required.
     * @param save whether to save
     */
    void notifyListChanged(boolean save);

    /**
     * Load from a JSON object
     *
     * @param jo JSON object
     * @throws JSONException if anything goes wrong
     */
    void fromJSON(JSONObject jo) throws JSONException;

    /**
     * Load from a Comma-Separated Value object
     *
     * @param r a reader
     */
    boolean fromCSV(CSVReader r) throws Exception;

    /**
     * Get the JSON object that represents the content of this object
     *
     * @return a JSONObject
     * @throws JSONException if it fails
     */
    JSONObject toJSON() throws JSONException;

    /**
     * Write the CSV that represents the content of this object
     *
     * @param w a CSV writer
     */
    void toCSV(CSVWriter w);

    /**
     * Format the item for inclusion in a text representation
     */
    String toPlainString(String tab);

    /**
     * Approach 1:
     * Assume that the backing store is private to one user.
     * So if the local cache is more recent than the backing store, use it.
     * Otherwise use the backing store.
     * Approach 2:
     * As approach 1, but prompt if the backing store is more recent than the cache
     * Approach 3:
     * Merge. Cases to consider:
     * List exists in cache but not in backing store
     *      If the list uid is more recent than the backing store root timestamp, keep it
     *      Otherwise discard it
     * List exists in backing store but not in cache
     * List exists in both backing store and cache
     *      Cache version is more recent than backing store version
     *      Backing store version is more recent than cache
     * Where a list exists in both the cache and the backing store,
     * it is merged at an item level. Where it only exists in the cache, then the time is considered;
     * if the time on the list is more recent than the backing store timestamp, it is retained.
     *
     * @param other the other item
     */
    boolean merge(EntryListItem other);

    /**
     * Deep equality test. This is a comparison of text only; UIDs are ignored
     *
     * @param other the other item
     */
    boolean equals(EntryListItem other);
}
