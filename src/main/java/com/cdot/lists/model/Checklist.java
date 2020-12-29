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

import com.cdot.lists.Lister;
import com.opencsv.CSVReader;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Set;

/**
 * A checklist of checkable items. Can also be an item in a Checklists
 */
public class Checklist extends EntryList {
    private static final String TAG = Checklist.class.getSimpleName();

    public static final String moveCheckedItemsToEnd = "movend";
    public static final String autoDeleteChecked = "autodel";

    /**
     * Constructor
     */
    public Checklist() {
    }

    /**
     * Constructor
     */
    public Checklist(String name) {
        setText(name);
    }

    /**
     * Process the JSON given and load from it
     *
     * @param job    the JSON object
     * @throws JSONException if something goes wrong
     */
    public Checklist(JSONObject job) throws JSONException {
        fromJSON(job);
    }

    /**
     * Construct by copying an existing list and saving it to a new list
     *
     * @param copy   list to clone
     */
    public Checklist(Checklist copy) {
        super(copy);
        for (EntryListItem item : copy.getData())
            addChild(new ChecklistItem((ChecklistItem) item));
    }

    @Override // EntryList
    public Set<String> getFlagNames() {
        Set<String> s = super.getFlagNames();
        s.add(moveCheckedItemsToEnd);
        s.add(autoDeleteChecked);
        return s;
    }

    @Override // implement EntryListItem
    public boolean isMoveable() {
        return true;
    }

    /**
     * Get the index of the item in the base (unsorted) list
     *
     * @param ci item
     * @return index of the item
     */
    @Override // implement EntryList
    public int indexOf(EntryListItem ci) {
        return getData().indexOf(ci);
    }

    @Override // EntryListItem
    public void fromJSON(JSONObject job) throws JSONException {
        super.fromJSON(job);
        getData().clear();
        setText(job.getString("name"));
        JSONArray items = job.getJSONArray("items");
        for (int i = 0; i < items.length(); i++) {
            ChecklistItem ci = new ChecklistItem((String)null);
            ci.fromJSON(items.getJSONObject(i));
            addChild(ci);
        }
    }

    // CSV lists continue of a set of rows, each with the list name in the first column,
    // the item name in the second column, and the done status in the third column
    @Override // EntryListItem
    public boolean fromCSV(CSVReader r) throws Exception {
        if (r.peek() == null)
            return false;
        if (getText() == null || r.peek()[0].equals(getText())) {
            // recognised header row
            if (getText() == null)
                setText(r.peek()[0]);
            while (r.peek() != null && r.peek()[0].equals(getText())) {
                ChecklistItem ci = new ChecklistItem();
                if (!ci.fromCSV(r))
                    break;
                addChild(ci);
            }
        }
        return true;
    }

    @Override // EntryListItem
    public JSONObject toJSON() {
        JSONObject job = super.toJSON();
        try {
            job.put("name", getText());
            JSONArray items = new JSONArray();
            for (EntryListItem item : getData()) {
                items.put(item.toJSON());
            }
            job.put("items", items);
        } catch (JSONException je) {
            Log.e(TAG, Lister.stringifyException(je));
        }
        return job;
    }

    /**
     * Get the number of checked items
     *
     * @return the number of checked items
     */
    public int getCheckedCount() {
        int i = 0;
        for (EntryListItem item : getData()) {
            if (item.getFlag(ChecklistItem.isDone))
                i++;
        }
        return i;
    }

    /**
     * Make a global change to the "checked" status of all items in the list
     *
     * @param check true to set items as checked, false to set as unchecked
     */
    public boolean checkAll(boolean check) {
        boolean changed = false;
        for (EntryListItem item : getData()) {
            ChecklistItem ci = (ChecklistItem) item;
            if (ci.getFlag(ChecklistItem.isDone) != check) {
                if (check)
                    ci.setFlag(ChecklistItem.isDone);
                else
                    ci.clearFlag(ChecklistItem.isDone);
                changed = true;
            }
        }
        return changed;
    }

    /**
     * Delete all the checked items in the list
     *
     * @return number of items deleted
     */
    public int deleteAllChecked() {
        ArrayList<ChecklistItem> kill = new ArrayList<>();

        for (EntryListItem it : getData()) {
            if (it.getFlag(ChecklistItem.isDone))
                kill.add((ChecklistItem) it);
        }

        newUndoSet();
        for (ChecklistItem dead : kill) {
            remove(dead, true);
        }
        return kill.size();
    }
}
