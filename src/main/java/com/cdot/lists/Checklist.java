/**
 * @copyright C-Dot Consultants 2020 - MIT license
 */
package com.cdot.lists;

import android.content.Context;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * A checklist of checkable items. Can also be an item in a Checklists
 */
class Checklist extends EntryList implements EntryListItem {
    private static final String TAG = "Checklist";

    /**
     * Adapter for the array of items in the list
     */
    private class ItemsArrayAdapter extends ArrayAdapter<String> {
        ItemsArrayAdapter(Context cxt) {
            super(cxt, 0);
        }

        @Override // ArrayAdapter
        public @NonNull
        View getView(int i, View convertView, @NonNull ViewGroup viewGroup) {
            ChecklistItem item;
            if (Settings.getBool(Settings.showCheckedAtEnd)) {
                int uc = mUnsorted.size() - getCheckedCount();
                if (i < uc)
                    // Get ith unchecked
                    item = getIthInState(i, false);
                else
                    // Get ith checked
                    item = getIthInState(i - uc, true);
            } else
                item = (ChecklistItem) getSorted().get(i);

            ChecklistItemView itemView;
            if (convertView == null) {
                assert item != null;
                itemView = new ChecklistItemView(item, false, getContext());
            } else {
                itemView = (ChecklistItemView) convertView;
                itemView.setItem(item);
            }
            itemView.updateView();
            return itemView;
        }

        @Override
        public int getCount() {
            return mUnsorted.size();
        }
    }

    private String mListName;
    long mTimestamp = 0;

    /**
     * Construct and load from cache
     *
     * @param name   private file list is stored in
     * @param parent container
     */
    Checklist(String name, EntryList parent, Context cxt) {
        super(parent, cxt);
        mListName = name;
        mArrayAdapter = new ItemsArrayAdapter(cxt);
    }

    /**
     * Process the JSON given and load from it
     *
     * @param job the JSON object
     * @throws JSONException if something goes wrong
     */
    Checklist(JSONObject job, EntryList parent, Context cxt) throws JSONException {
        super(parent, cxt);
        fromJSON(job);
        mArrayAdapter = new ItemsArrayAdapter(cxt);
    }

    /**
     * Load from JSON read from the given stream
     *
     * @param stream source of JSON
     * @param parent the container object
     * @throws Exception IOException or JSONException
     */
    Checklist(InputStream stream, EntryList parent, Context cxt) throws Exception {
        super(parent, cxt);
        fromStream(stream);
        mArrayAdapter = new ItemsArrayAdapter(cxt);
    }

    /**
     * Construct by copying an existing list and saving it to a new list
     *
     * @param copy list to clone
     */
    Checklist(Checklist copy, EntryList parent, Context cxt) {
        super(parent, cxt);
        mListName = copy.mListName;
        mShowSorted = copy.mShowSorted;
        for (EntryListItem item : copy.mUnsorted)
            add(new ChecklistItem(this, (ChecklistItem) item));
        reSort();
        mArrayAdapter = new ItemsArrayAdapter(cxt);
    }

    Checklist(Uri uri, Checklists parent, Context cxt) throws Exception {
        super(uri, parent, cxt);
        mArrayAdapter = new ItemsArrayAdapter(cxt);
    }

    // implements EntryListItem
    @Override
    public String getText() {
        return mListName;
    }

    // implements EntryListItem
    @Override
    public void setText(String name) {
        mListName = name;
    }

    /**
     * Get the index of the item in the base (unsorted) list
     *
     * @param ci item
     * @return index of the item
     */
    // @implements EntryList
    @Override
    int indexOf(EntryListItem ci) {
        return mUnsorted.indexOf(ci);
    }

    /**
     * Get the ith checked/nuchecked item in the displayed list
     *
     * @param i index
     * @param state true to get ith checked item, false to get ith unchecked item
     * @return the ith checked item, or null if no items are checked
     */
    private ChecklistItem getIthInState(int i, boolean state) {
        int count = -1;
        for (EntryListItem item : getSorted()) {
            if (((ChecklistItem) item).mDone == state && ++count == i)
                return (ChecklistItem) item;
        }
        // No checked items
        return null;
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

    /**
     * Merge items from another list into this list. Changes to item status in the other
     * list only happen if the timestamp on the other list is more recent than the timestamp
     * on this list.
     *
     * @param other the other list
     */
    boolean merge(Checklist other) {
        boolean changed = false;
        for (EntryListItem it : mUnsorted) {
            ChecklistItem cli = (ChecklistItem) it;
            int ocli = other.find(cli.mText, true);
            if (ocli >= 0) {
                if (cli.merge((ChecklistItem) other.get(ocli)))
                    changed = true;
            } // item not in other list
        }
        for (EntryListItem it : mUnsorted) {
            ChecklistItem ocli = (ChecklistItem) it;
            int cli = find(ocli.mText, true);
            if (cli < 0) {
                mUnsorted.add(ocli);
                changed = true;
            }
        }
        return changed;
    }

    /**
     * Make a global change to the "checked" status of all items in the list
     *
     * @param check
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
    public void notifyListChanged(boolean doSave) {
        reSort();
        mArrayAdapter.notifyDataSetChanged();
        if (doSave)
            getContainer().save(getContext());
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
        mArrayAdapter.notifyDataSetChanged();
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
        reSort();
    }

    @Override // EntryListItem
    public JSONObject toJSON() throws JSONException {
        JSONObject job = super.toJSON();
        job.put("name", mListName);
        job.put("time", new Date().getTime());
        JSONArray items = new JSONArray();
        for (EntryListItem item : mUnsorted) {
            items.put(item.toJSON());
        }
        job.put("items", items);
        return job;
    }

    /**
     * Load from a CSV object
     *
     *         CSVReader reader = new CSVReader(new FileReader("yourfile.csv"));
     *         List<String[]> myEntries = reader.readAll();
     *
     * @param rows CSV object
     */
    public void fromCSV(List<String[]> rows) {
        for (String[] row : rows) {
            ChecklistItem it = new ChecklistItem(this, row[0], row[1].equals("TRUE"));
            mUnsorted.add(it);
        }
        reSort();
    }
}
