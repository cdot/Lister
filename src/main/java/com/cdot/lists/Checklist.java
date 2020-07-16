/*
 * Copyright C-Dot Consultants 2020 - MIT license
 */
package com.cdot.lists;

import android.content.Context;

import com.opencsv.CSVReader;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

/**
 * A checklist of checkable items. Can also be an item in a Checklists
 */
class Checklist extends EntryList {
    private static final String TAG = "Checklist";

    private String mListName;
    long mTimestamp = 0;

    /**
     * Construct and load from cache
     *
     * @param name   private file list is stored in
     * @param parent container
     */
    Checklist(EntryList parent, String name) {
        super(parent);
        mListName = name;
    }

    /**
     * Process the JSON given and load from it
     *
     * @param job the JSON object
     * @param parent new container list
     * @throws JSONException if something goes wrong
     */
    Checklist(EntryList parent, JSONObject job) throws JSONException {
        super(parent);
        fromJSON(job);
    }

    /**
     * Construct by copying an existing list and saving it to a new list
     *
     * @param copy list to clone
     * @param parent new container list
     */
    Checklist(EntryList parent, Checklist copy) {
        super(parent);
        mUID = copy.mUID;
        mListName = copy.mListName;
        mShowSorted = copy.mShowSorted;
        for (EntryListItem item : copy.mUnsorted)
            add(new ChecklistItem(this, (ChecklistItem) item));
        updateDisplayOrder();
    }

    @Override // implement EntryList
    EntryListItemView makeItemView(EntryListItem item, Context cxt) {
        return new ChecklistItemView(item, false, cxt);
    }

    @Override // implement EntryList
    protected void updateDisplayOrder() {
        super.updateDisplayOrder();
        if (!mInEditMode && Settings.getBool(Settings.showCheckedAtEnd)) {
            int top = size();
            int i = 0;
            while (i < top) {
                ChecklistItem item = (ChecklistItem) mDisplayed.get(i);
                if (item.mDone) {
                    mDisplayed.add(mDisplayed.remove(i));
                    top--;
                } else
                    i++;
            }
        }
    }

    @Override // implement EntryListItem
    public String getText() {
        return mListName;
    }

    @Override // implement EntryListItem
    public void setText(String name) {
        mListName = name;
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
    int indexOf(EntryListItem ci) {
        return mUnsorted.indexOf(ci);
    }

    /**
     * Get the number of checked items
     *
     * @return the number of checked items
     */
    int getCheckedCount() {
        int i = 0;
        for (EntryListItem item : mUnsorted) {
            if (((ChecklistItem) item).mDone)
                i++;
        }
        return i;
    }

    // Called on the cache to merge the backing list
    @Override // implement EntryListItem
    public boolean merge(EntryListItem backing) {
        boolean changed = false;
        if (!getText().equals(backing.getText())) {
            setText(backing.getText());
            changed = true;
        }
        EntryList backList = (EntryList)backing;
        for (EntryListItem cacheIt : mUnsorted) {
            EntryListItem backIt = backList.findByUID(cacheIt.getUID());
            if (backIt != null) {
                if (cacheIt.merge(backIt))
                    changed = true;
            } // item not in backing list, only in cache
        }
        for (EntryListItem backIt : backList.mUnsorted) {
            EntryListItem cacheIt = findByUID(backIt.getUID());
            if (cacheIt == null) {
                mUnsorted.add(backIt);
                changed = true;
            }
        }
        return changed;
    }

    /**
     * Make a global change to the "checked" status of all items in the list
     *
     * @param check true to set items as checked, false to set as unchecked
     */
    void checkAll(boolean check) {
        boolean changed = false;
        for (EntryListItem item : mUnsorted) {
            ChecklistItem ci = (ChecklistItem) item;
            if (ci.mDone != check) {
                ci.setDone(check);
                changed = true;
            }
        }
        if (changed)
            notifyListChanged(true);
    }

    @Override // EntryList
    void setEditMode(boolean on) {
        if (on != mInEditMode)
            notifyListChanged(false);
        super.setEditMode(on);
    }

    @Override // EntryList
    public void notifyListChanged(boolean doSave) {
        super.notifyListChanged(doSave);
        getContainer().notifyListChanged(doSave);
        updateDisplayOrder();
    }

    /**
     * @return number of items deleted
     */
    int deleteAllChecked() {
        ArrayList<ChecklistItem> kill = new ArrayList<>();

        for (EntryListItem it : mUnsorted) {
            if (((ChecklistItem) it).mDone)
                kill.add((ChecklistItem) it);
        }

        newUndoSet();
        for (ChecklistItem dead : kill) {
            remove(dead, true);
        }
        return kill.size();
    }

    @Override // EntryListItem
    public void fromJSON(JSONObject job) throws JSONException {
        super.fromJSON(job);
        mUnsorted.clear();
        mListName = job.getString("name");
        mTimestamp = job.getLong("time");
        JSONArray items = job.getJSONArray("items");
        for (int i = 0; i < items.length(); i++) {
            ChecklistItem ci = new ChecklistItem(this, null, false);
            ci.fromJSON(items.getJSONObject(i));
            mUnsorted.add(ci);
        }
        updateDisplayOrder();
    }

    @Override // EntryListItem
    public boolean fromCSV(CSVReader r) throws Exception {
        setText("CSV");
        String[] row = r.readNext();
        if (row == null || !row[0].equals("Item"))
            return false;
        while (true) {
            ChecklistItem ci = new ChecklistItem(this, null, false);
            if (!ci.fromCSV(r))
                break;
            mUnsorted.add(ci);
        }
        updateDisplayOrder();
        return true;
    }

    @Override // EntryListItem
    public JSONObject toJSON() throws JSONException {
        JSONObject job = super.toJSON();
        job.put("name", mListName);
        job.put("time", mTimestamp);
        JSONArray items = new JSONArray();
        for (EntryListItem item : mUnsorted) {
            items.put(item.toJSON());
        }
        job.put("items", items);
        return job;
    }
}
