/**
 * @copyright C-Dot Consultants 2020 - MIT license
 */
package com.cdot.lists;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * An item in a Checklist
 */
class ChecklistItem implements EntryListItem {
    private final Checklist mList;
    String mText;
    boolean mDone;
    long mDoneAt; // timestamp, only valid if mDone

    ChecklistItem(Checklist checklist, String str, boolean done) {
        mList = checklist;
        mText = str;
        mDone = done;
        mDoneAt = done ? System.currentTimeMillis() : 0;
    }

    ChecklistItem(Checklist checklist, JSONObject jo) throws JSONException {
        mList = checklist;
        mText = jo.getString("name");
        mDone = false;
        mDoneAt = 0;
        try {
            mDone = jo.getBoolean("done");
            mDoneAt = jo.getLong("at");
        } catch (JSONException ignored) {
        }
    }

    ChecklistItem(Checklist checklist, ChecklistItem clone) {
        mList = checklist;
        mText = clone.mText;
        mDone = clone.mDone;
        mDoneAt = clone.mDoneAt;
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

    boolean isDone() {
        return mDone;
    }

    @Override // implement EntryListItem
    public void notifyListChanged(boolean save) {
        getContainer().notifyListChanged(save);
    }

    /**
     * Merge this item's fields with another more recent version of the same item with the
     * same text. The more recent item's status takes precedence over this items
     *
     * @param ocli the more recent item to merge
     * @return true if there were changes
     */
    boolean merge(ChecklistItem ocli) {
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
        mDoneAt = System.currentTimeMillis();
    }

    public JSONObject toJSON() throws JSONException {
        JSONObject iob = new JSONObject();
        iob.put("name", mText);
        iob.put("done", mDone);
        if (mDone)
            iob.put("at", mDoneAt);
        return iob;
    }
}
