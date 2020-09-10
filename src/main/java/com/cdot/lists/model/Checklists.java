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

import com.cdot.lists.fragment.EntryListFragment;
import com.cdot.lists.view.ChecklistsItemView;
import com.cdot.lists.view.EntryListItemView;
import com.opencsv.CSVReader;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * A list of Checklist.
 * Constructed by reading a JSON file.
 */
public class Checklists extends EntryList {
    private static final String TAG = "Checklists";

    public long mTimestamp; // time it was last saved
    private String mURI; // URI it was loaded from (or is a cache for)

    /**
     * Constructor
     */
    public Checklists() {
        super(null);
    }

    @Override // EntryList
    public EntryListItemView makeItemView(EntryListItem item, EntryListFragment frag) {
        return new ChecklistsItemView(item, false, frag);
    }

    @Override // implement EntryListItem
    public void setText(String s) {
        throw new Error("Unexpected setText in " + TAG);
    }

    @Override // implement EntryListItem
    public String getText() { return null; }

    @Override // implement EntryListItem
    public boolean isMoveable() {
        return false;
    }

    @Override // EntryList
    public JSONObject toJSON() throws JSONException {
        JSONObject job = super.toJSON();
        job.put("timestamp", System.currentTimeMillis());
        job.put("uri", mURI);
        return job;
    }

    @Override // EntryList
    public void fromJSON(JSONObject job) throws JSONException {
        try {
            super.fromJSON(job);
            mTimestamp = job.has("timestamp") ? job.getLong("timestamp") :  0; // 0=unknown
            mURI = job.has("uri") ? job.getString("uri") : "";
            JSONArray lists = job.getJSONArray("items");
            for (int i = 0; i < lists.length(); i++)
                add(new Checklist(this, lists.getJSONObject(i)));
            Log.d(TAG, "Extracted " + lists.length() + " lists from JSON");
        } catch (JSONException je) {
            // Only one list
            Log.d(TAG, "Could not get lists from JSON, assume one list only");
            add(new Checklist(this, job));
        }
    }

    @Override // EntryListItem
    public boolean fromCSV(CSVReader r) throws Exception {
        throw new Exception("Unable to read multiple lists from CSV");
    }

    @Override // EntryList
    public String toPlainString(String tab) {
        StringBuilder sb = new StringBuilder();
        for (EntryListItem item : getData()) {
            sb.append(tab).append(item.getText()).append(":\n");
            sb.append(item.toPlainString(tab + "\t")).append("\n");
        }
        return sb.toString();
    }

    // Record the URI this was loaded from
    public void setURI(String uri) {
        mURI = uri;
    }

    // Record the URI this was loaded from
    public String getURI() {
        return mURI;
    }

    /**
     * Determine if this is more recent than another set of checklists
     * @return false if the two lists don't come from the same URI, or if the other list's
     * time stamp is more recent than this list.
     */
    public boolean isMoreRecentThan(Checklists other) {
        return other.mURI.equals(mURI) && mTimestamp > other.mTimestamp;
    }

    /**
     * Make a copy of the list at the given index
     *
     * @param i index of the list to clone
     */
    public void copyList(EntryListItem i) {
        Checklist checklist = new Checklist(this, (Checklist) i);
        String newname = checklist.getText() + " (copy)";
        checklist.setText(newname);
        add(checklist);
        notifyListeners();
    }

    // DEBUG ONLY
    /*
    private void removeDuplicates() {
        for (int i = 0; i < size(); i++) {
            EntryListItem ei = get(i);
            for (int j = i + 1; j < size(); ) {
                if (get(j).equals(ei)) {
                    Log.d(TAG, "REMOVE DUPLICATE " + get(j).getText());
                    remove(get(j), false);
                } else
                    j++;
            }
        }
    }*/
}
