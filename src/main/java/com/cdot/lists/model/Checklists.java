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
package com.cdot.lists.model;

import android.util.Log;

import com.opencsv.CSVReader;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * A list of Checklist.
 * Constructed by reading a JSON file.
 */
public class Checklists extends EntryList {
    private static final String TAG = Checklists.class.getSimpleName();

    private long mTimestamp; // time it was last changed
    private String mURI; // URI it was loaded from (or is a cache for)

    public Checklists() {
    }

    @Override // implement EntryListItem
    public String getText() {
        return null;
    }

    @Override // implement EntryListItem
    public void setText(String s) {
        throw new Error("Unexpected setText in " + TAG);
    }

    @Override // implement EntryListItem
    public boolean isMoveable() {
        return false;
    }

    @Override // EntryList
    public void notifyChangeListeners() {
        mTimestamp = System.currentTimeMillis();
        super.notifyChangeListeners();
    }

    @Override // EntryList
    public JSONObject toJSON() {
        JSONObject job = super.toJSON();
        try {
            job.put("timestamp", mTimestamp);
            job.put("uri", mURI);
        } catch (JSONException je) {
            Log.e(TAG, "" + je);
        }
        return job;
    }

    @Override // EntryList
    public void fromJSON(JSONObject job) throws JSONException {
        try {
            super.fromJSON(job);
            mTimestamp = job.has("timestamp") ? job.getLong("timestamp") : 0; // 0=unknown
            mURI = job.has("uri") ? job.getString("uri") : "";
            JSONArray lists = job.getJSONArray("items");
            for (int i = 0; i < lists.length(); i++)
                addChild(new Checklist(lists.getJSONObject(i)));
            Log.d(TAG, "Extracted " + lists.length() + " lists from JSON");
        } catch (JSONException je) {
            // Only one list
            Log.d(TAG, "Could not get lists from JSON, assume one list only");
            addChild(new Checklist(job));
        }
    }

    @Override // EntryListItem
    public boolean fromCSV(CSVReader r) throws Exception {
        while (r.peek() != null) {
            Checklist list = new Checklist();
            list.fromCSV(r);
            addChild(list);
        }
        return true;
    }

    @Override // EntryList
    public String toPlainString(String tab) {
        StringBuilder sb = new StringBuilder();
        for (EntryListItem item : getData()) {
            sb.append(item.toPlainString(tab)).append("\n");
        }
        return sb.toString();
    }

    /**
     * Get the store URI this was loaded from (may be null)
     */
    public String getURI() {
        return mURI;
    }

    /**
     * Record the store URI this was loaded from
     */
    public void setURI(String uri) {
        mURI = uri;
    }

    /**
     * Determine if this is a more recent version of another set of checklists, as determined by the
     * timestamp that is set whenever anything in the lists changes.
     *
     * @return false if the two lists don't come from the same URI, or if the other list's
     * time stamp is more recent than this list.
     */
    public boolean isMoreRecentVersionOf(Checklists other) {
        return other.mURI.equals(mURI) && mTimestamp > other.mTimestamp;
    }
}
