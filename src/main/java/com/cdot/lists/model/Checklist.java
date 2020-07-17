/*
 * Copyright C-Dot Consultants 2020 - MIT license
 */
package com.cdot.lists.model;

import com.cdot.lists.fragment.EntryListFragment;
import com.cdot.lists.view.ChecklistItemView;
import com.cdot.lists.view.EntryListItemView;
import com.opencsv.CSVReader;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

/**
 * A checklist of checkable items. Can also be an item in a Checklists
 */
public class Checklist extends EntryList {
    private static final String TAG = "Checklist";

    private long mTimestamp = 0;

    /**
     * Construct and load from cache
     *
     * @param name   private file list is stored in
     * @param parent container
     */
    public Checklist(EntryList parent, String name) {
        super(parent);
        setText(name);
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
        super(parent, copy);
        for (EntryListItem item : copy.getData())
            add(new ChecklistItem(this, (ChecklistItem) item));
    }

    @Override
    public // implement EntryList
    EntryListItemView makeItemView(EntryListItem item, EntryListFragment cxt) {
        return new ChecklistItemView(item, false, cxt);
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

    /**
     * Get the number of checked items
     *
     * @return the number of checked items
     */
    public int getCheckedCount() {
        int i = 0;
        for (EntryListItem item : getData()) {
            if (((ChecklistItem) item).isDone())
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
        for (EntryListItem cacheIt : getData()) {
            EntryListItem backIt = backList.findByUID(cacheIt.getUID());
            if (backIt != null) {
                if (cacheIt.merge(backIt))
                    changed = true;
            } // item not in backing list, only in cache
        }
        for (EntryListItem backIt : backList.getData()) {
            EntryListItem cacheIt = findByUID(backIt.getUID());
            if (cacheIt == null) {
                getData().add(backIt);
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
    public void checkAll(boolean check) {
        boolean changed = false;
        for (EntryListItem item : getData()) {
            ChecklistItem ci = (ChecklistItem) item;
            if (ci.isDone() != check) {
                ci.setDone(check);
                changed = true;
            }
        }
        if (changed)
            notifyListChanged(true);
    }

    /**
     * @return number of items deleted
     */
    public int deleteAllChecked() {
        ArrayList<ChecklistItem> kill = new ArrayList<>();

        for (EntryListItem it : getData()) {
            if (((ChecklistItem) it).isDone())
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
        getData().clear();
        setText(job.getString("name"));
        mTimestamp = job.getLong("time");
        JSONArray items = job.getJSONArray("items");
        for (int i = 0; i < items.length(); i++) {
            ChecklistItem ci = new ChecklistItem(this, null, false);
            ci.fromJSON(items.getJSONObject(i));
            getData().add(ci);
        }
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
            getData().add(ci);
        }
        return true;
    }

    @Override // EntryListItem
    public JSONObject toJSON() throws JSONException {
        JSONObject job = super.toJSON();
        job.put("name", getText());
        job.put("time", mTimestamp);
        JSONArray items = new JSONArray();
        for (EntryListItem item : getData()) {
            items.put(item.toJSON());
        }
        job.put("items", items);
        return job;
    }
}
