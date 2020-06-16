/**
 * @copyright C-Dot Consultants 2020 - MIT license
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
     * Get the unique integer that idenitifies this item
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
     * Get the item's text
     *
     * @return a text string representing the item in the list
     */
    String getText();

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
     * Merge this item with another item. Changes are selected based on timestamps.
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
