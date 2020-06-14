/**
 * @copyright C-Dot Consultants 2020 - MIT license
 */
package com.cdot.lists;

import org.json.JSONException;
import org.json.JSONObject;

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
    void fromJSON(JSONObject jo) throws JSONException;

    /**
     * Get the JSON object that represents the content of this object
     *
     * @return a JSONObject
     * @throws JSONException if it fails
     */
    JSONObject toJSON() throws JSONException;
}
