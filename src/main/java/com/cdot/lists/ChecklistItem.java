/*
 * @copyright C-Dot Consultants 2020 - MIT license
 */
package com.cdot.lists;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/**
 * An item in a Checklist
 */
class ChecklistItem implements EntryListItem {
    private final Checklist mList;
    private long mUID;
    String mText;
    boolean mDone;
    private long mDoneAt; // timestamp, only valid if mDone

    ChecklistItem(Checklist checklist, String str, boolean done) {
        mUID = System.currentTimeMillis();
        mList = checklist;
        mText = str;
        mDone = done;
        mDoneAt = done ? mUID : 0;
    }

    ChecklistItem(Checklist checklist, ChecklistItem clone) {
        mList = checklist;
        mText = clone.mText;
        mDone = clone.mDone;
        mDoneAt = clone.mDoneAt;
    }

    @Override // implement EntryListItem
    public long getUID() {
        return mUID;
    }

    @Override // EntryListItem
    public EntryList getContainer() {
        return mList;
    }

    @Override // EntryListItem
    public String getText() {
        return mText;
    }

    @Override // EntryListItem
    public void setText(String str) {
        mText = str;
    }

    @Override // implement EntryListItem
    public void notifyListChanged(boolean save) {
        getContainer().notifyListChanged(save);
    }

    boolean isDone() {
        return mDone;
    }

    @Override // implement EntryListItem
    public boolean equals(EntryListItem ot) {
        if (!getText().equals(ot.getText()))
            return false;
        if (mDone != ((ChecklistItem)ot).mDone)
            return false;
        return true;
    }

    @Override // implement EntryListItem
    public boolean merge(EntryListItem other) {
        if (other.getUID() != getUID())
            return false;
        ChecklistItem ocli = (ChecklistItem)other;
        mDoneAt = Math.max(mDoneAt, ocli.mDoneAt);
        if (mDone == ocli.mDone) // no changes
            return false;
        mDone = ocli.mDone;
        return true;
    }

    /**
     * Set the item's done status and trigger a save
     *
     * @param done new done status
     */
    void setDone(boolean done) {
        mDone = done;
        if (done)
            mDoneAt = System.currentTimeMillis();
    }

    @Override // implement EntryListItem
    public void fromJSON(JSONObject jo) throws JSONException {
        try {
            mUID = jo.getLong("at");
        } catch (JSONException ignored) {
        }
        mText = jo.getString("name");
        mDone = false;
        mDoneAt = 0;
        try {
            mDone = jo.getBoolean("done");
            mDoneAt = jo.getLong("at");
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
        iob.put("name", mText);
        iob.put("done", mDone);
        if (mDone)
            iob.put("at", mDoneAt);
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
}
