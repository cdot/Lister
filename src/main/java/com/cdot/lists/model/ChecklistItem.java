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
import com.opencsv.CSVWriter;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Set;

/**
 * An item in a Checklist
 */
public class ChecklistItem extends EntryListItem {
    public static final String isDone = "done";
    private static final String TAG = ChecklistItem.class.getSimpleName();

    public ChecklistItem() {
    }

    public ChecklistItem(String str) {
        setText(str);
    }

    /**
     * Construct by copying the given item into the given checklist
     *
     * @param copy      item to copy
     */
    public ChecklistItem(ChecklistItem copy) {
        this(copy.getText());
        for (String f : getFlagNames()) {
            if (copy.getFlag(f))
                setFlag(f);
            else
                clearFlag(f);
        }
    }

    @Override // EntryListItem
    public Set<String> getFlagNames() {
        Set<String> s = super.getFlagNames();
        s.add(isDone);
        return s;
    }

    @Override // implement EntryListItem
    public boolean isMoveable() {
        return getParent() == null || getParent().itemsAreMoveable();
    }

    @Override // implement EntryListItem
    public boolean equals(EntryListItem ot) {
        return super.equals(ot) && getFlag(isDone) == ot.getFlag(isDone);
    }

    @Override // implement EntryListItem
    public void fromJSON(JSONObject jo) throws JSONException {
        super.fromJSON(jo);
        setText(jo.getString("name"));
    }

    @Override // implement EntryListItem
    public boolean fromCSV(CSVReader r) throws Exception {
        String[] row = r.readNext();
        if (row == null)
            return false;
        setText(row[1]);
        // "f", "false", "0", and "" are read as false. Any other value is read as true
        if (row[2].length() == 0 || row[2].matches("[Ff]([Aa][Ll][Ss][Ee])?|0"))
            clearFlag(isDone);
        else
            setFlag(isDone);
        return true;
    }

    @Override // implement EntryListItem
    public JSONObject toJSON() {
        JSONObject iob = super.toJSON();
        try {
            iob.put("name", getText());
        } catch (JSONException je) {
            Log.e(TAG, "" + je);
        }
        return iob;
    }

    @Override // implement EntryListItem
    public void toCSV(CSVWriter w) {
        String[] a = new String[3];
        a[0] = getParent().getText();
        a[1] = getText();
        a[2] = (getFlag(isDone) ? "T" : "F");
        w.writeNext(a);
    }

    @Override // implement EntryListItem
    public String toPlainString(String tab) {
        StringBuilder sb = new StringBuilder();
        sb.append(tab).append(getText());
        if (getFlag(isDone))
            sb.append(" *");
        return sb.toString();
    }
}
