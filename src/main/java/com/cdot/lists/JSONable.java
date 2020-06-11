/**
 * @copyright C-Dot Consultants 2020 - MIT license
 */
package com.cdot.lists;

import org.json.JSONException;

/**
 * Interface for objects that can be converted from/to JSON
 */
interface JSONable {

    /**
     * Load from a JSON object
     *
     * @param jo JSON object
     * @throws JSONException if anything goes wrong
     */
    void fromJSON(Object jo) throws JSONException;

    /**
     * Get the JSON object that represents the content of this object
     *
     * @return a JSONObject or JSONArray
     * @throws JSONException if it fails
     */
    Object toJSON() throws JSONException;
}
