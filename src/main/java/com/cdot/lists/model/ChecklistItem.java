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

import com.cdot.lists.Settings;
import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * An item in a Checklist
 */
public class ChecklistItem extends EntryListItem {
    //private static final String TAG = "ChecklistItem";

    private long mUID;
    private boolean mDone; // has it been checked?

    @Override // implement EntryListItem
    public long getUID() {
        return mUID;
    }

    @Override // implement EntryListItem
    public void notifyChangeListeners() {
        // Listeners are attached to the root item, so pass the signal up
        getContainer().notifyChangeListeners();
    }

    @Override // implement EntryListItem
    public boolean isMoveable() {
        return !mDone;
    }

    @Override // implement EntryListItem
    public boolean equals(EntryListItem ot) {
        return super.equals(ot) && mDone == ((ChecklistItem) ot).mDone;
    }

    @Override // implement EntryListItem
    public void fromJSON(JSONObject jo) throws JSONException {
        mUID = jo.getLong("uid");
        setText(jo.getString("name"));
        mDone = false;
        try {
            mDone = jo.getBoolean("done");
        } catch (JSONException ignored) {
        }
    }

    @Override // implement EntryListItem
    public boolean fromCSV(CSVReader r) throws Exception {
        String[] row = r.readNext();
        if (row == null)
            return false;
        setText(row[0]);
        // "false", "0", and "" are read as false. Any other value is read as true
        setDone(row[1].length() == 0 || row[1].matches("[Ff][Aa][Ll][Ss][Ee]|0"));
        return true;
    }

    @Override // implement EntryListItem
    public JSONObject toJSON() throws JSONException {
        JSONObject iob = new JSONObject();
        iob.put("uid", mUID);
        iob.put("name", getText());
        if (mDone)
            iob.put("done", true);
        return iob;
    }

    @Override // implement EntryListItem
    public void toCSV(CSVWriter w) {
        String[] a = new String[2];
        a[0] = getText();
        a[1] = (mDone ? "TRUE" : "FALSE");
        w.writeNext(a);
    }

    @Override // implement EntryListItem
    public String toPlainString(String tab) {
        StringBuilder sb = new StringBuilder();
        sb.append(tab).append(getText());
        if (mDone)
            sb.append(" *");
        return sb.toString();
    }

    public ChecklistItem(Checklist checklist, String str, boolean done) {
        super(checklist);
        mUID = Settings.makeUID();
        setText(str);
        mDone = done;
    }

    /**
     * Construct by copying the given item into the given checklist
     * @param checklist container for the copied item
     * @param copy item to copy
     */
    ChecklistItem(Checklist checklist, ChecklistItem copy) {
        this(checklist, copy.getText(), copy.mDone);
    }

    /**
     * Get the items done status
     * @return true if the item is marked as done
     */
    public boolean isDone() {
        return mDone;
    }

    /**
     * Set the items done status
     *
     * @param done new done status
     * @return true if the status changed
     */
    public boolean setDone(boolean done) {
        if (mDone != done) {
            mDone = done;
            return true;
        }
        return false;
    }
}
