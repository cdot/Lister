/**
 * @copyright C-Dot Consultants 2020 - MIT license
 */
package com.cdot.lists;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Interface to items in an EntryList
 */
interface EntryListItem {
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
     * Get the JSON object that represents the content of this object
     *
     * @return a JSONObject
     * @throws JSONException if it fails
     */
    JSONObject toJSON() throws JSONException;

    /**
     * Get the CSV object that represents the content of this object
     *
     * @return a String containing CSV
     */
    String toCSV();

    /**
     * Format the item for inclusion in a text representation
     */
    String toPlainString(String tab);
}
