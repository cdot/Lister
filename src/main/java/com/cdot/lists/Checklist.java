/**
 * @copyright C-Dot Consultants 2020 - MIT license
 */
package com.cdot.lists;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.Stack;

/**
 * A checklist of checkable items. Can also be an item in a Checklists
 */
class Checklist extends EntryList implements EntryListItem, JSONable  {
    private static final String TAG = "Checklist";

    private ArrayList<ChecklistItem> mUnsorted = new ArrayList<>();
    private ArrayList<ChecklistItem> mSorted = new ArrayList<>();

    /**
     * Adapter for the array of items in the list
     */
    private class ItemsArrayAdapter extends ArrayAdapter<String> {
        ItemsArrayAdapter(Context cxt) {
            super(cxt, 0, new ArrayList<String>());
        }

        @Override
        public @NonNull View getView(int i, View view, @NonNull ViewGroup viewGroup) {
            ChecklistItem item;
            if (Settings.getBool(Settings.showCheckedAtEnd)) {
                if (i < getUncheckedCount())
                    item = getUnchecked(i);
                else
                    item = getChecked(i - getUncheckedCount());
            } else if (Settings.getBool(Settings.forceAlphaSort))
                item = mSorted.get(i);
            else
                item = mUnsorted.get(i);

            ChecklistItemView itemView;
            if (view == null) {
                assert item != null;
                itemView = new ChecklistItemView(item, false, getContext());
            } else {
                itemView = (ChecklistItemView) view;
                // TODO: it this really required? Surely the new should have dealt with it
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

    private Stack<ChecklistItem> mRemovedItems = new Stack<>();
    // Each remove operation has a unique ID
    private Stack<Integer> mRemoveOpID = new Stack<>();
    private transient int mNextRemoveOpID = 0;
    private Stack<Integer> mRemovedItemsIndex = new Stack<>();

    /**
     * Construct and load from cache
     *
     * @param name   private file list is stored in
     * @param parent container
     */
    Checklist(String name, Checklists parent, Context cxt) {
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
    Checklist(JSONObject job, Checklists parent, Context cxt) throws JSONException {
        super(parent, cxt);
        fromJSON(job);
        mArrayAdapter = new ItemsArrayAdapter(cxt);
    }

    /**
     * Load from JSON read from the given stream
     *
     * @param stream source of JSON
     * @param parent the container object
     * @throws Exception   IOException or JSONException
     */
    Checklist(InputStream stream, Checklists parent, Context cxt) throws Exception {
        super(parent, cxt);
        fromStream(stream);
        mArrayAdapter = new ItemsArrayAdapter(cxt);
    }

    /**
     * Construct by copying an existing list and saving it to a new list
     *
     * @param copy list to clone
     */
    Checklist(Checklist copy, Checklists parent, Context cxt) {
        super(parent, cxt);
        mListName = copy.mListName;
        for (ChecklistItem item : copy.mUnsorted)
            mUnsorted.add(new ChecklistItem(this, item));
        reSort();
        mArrayAdapter = new ItemsArrayAdapter(cxt);
    }

    private ArrayList<ChecklistItem> getItems() {
        return (Settings.getBool(Settings.forceAlphaSort) ? mSorted : mUnsorted);
    }

    Checklist(Uri uri, Checklists parent, Context cxt) throws Exception {
        super(uri, parent, cxt);
        mArrayAdapter = new ItemsArrayAdapter(cxt);
    }

    // implements EntryList
    @Override
    String getName() {
        return mListName;
    }

    // implements EntryList
    @Override
    void setName(String name) {
        mListName = name;
    }

    // implements EntryList
    @Override
    int size() {
        return mUnsorted.size();
    }

    // implements EntryList
    @Override
    EntryListItem get(int idx) {
        return mUnsorted.get(idx);
    }

    /**
     * Get the index of the item in the base (unsorted) list
     * @param ci item
     * @return index of the item
     */
    // @implements EntryList
    @Override
    int indexOf(EntryListItem ci) {
        return mUnsorted.indexOf(ci);
    }

    // implements EntryListItem
    @Override
    public String getText() { return getName(); }

    // implements EntryListItem
    @Override
    public void setText(String s) {
        setName(s);
    }

    /**
     * Get the ith checked item in the displayed list
     *
     * @param i index
     * @return the ith checked item, or null if no items are checked
     */
    private ChecklistItem getChecked(int i) {
        int count = -1;
        for (ChecklistItem item : getItems()) {
            if (item.mDone && ++count == i)
                return item;
        }
        // No checked items
        return null;
    }

    /**
     * Get the ith unchecked item in the displayed list
     *
     * @param i index of unchecked item to get
     * @return the ith unchecked item, or null if no items are unchecked
     */
    private ChecklistItem getUnchecked(int i) {
        int count = -1;
        for (ChecklistItem item : getItems()) {
            if (!item.mDone && ++count == i)
                return item;
        }
        // No items unchecked
        return null;
    }

    /**
     * Get the number of checked items
     *
     * @return the number of checked items
     */
    private int getCheckedCount() {
        int i = 0;
        for (ChecklistItem it : mUnsorted)
            if (it.mDone)
                i++;
        return i;
    }

    /**
     * Get the number of unchecked items
     *
     * @return the number of unchecked items
     */
    private int getUncheckedCount() {
        return mUnsorted.size() - getCheckedCount();
    }

    // implements EntryList
    @Override
    int find(String str, boolean matchCase) {
        int i = -1;
        for (ChecklistItem next : mUnsorted) {
            i++;
            if (next.mText.equalsIgnoreCase(str))
                return i;
        }
        if (matchCase)
            return -1;
        i = -1;
        for (ChecklistItem next : mUnsorted) {
            i++;
            if (next.mText.toLowerCase().contains(str.toLowerCase()))
                return i;
        }
        return -1;
    }

    /// implements EntryList
    @Override
    void moveItemToPosition(EntryListItem bit, int i) {
        // Operations on mUnsorted, since this can only be invoked when the list is unsorted
        ChecklistItem item = (ChecklistItem)bit;
        if (i >= 0 && i <= mUnsorted.size() - 1) {
            remove(indexOf(item));
            mUnsorted.add(i, item);
            notifyListChanged();
        }
    }

    /// implements EntryList
    @Override
    int add(EntryListItem eit) {
        ChecklistItem item = (ChecklistItem)eit;
        Log.d(TAG, "Add " + item.getText());
        if (Settings.getBool(Settings.addToTop))
            mUnsorted.add(0, item);
        else
            mUnsorted.add(item);
        notifyListChanged();
        return getItems().indexOf(item);
    }

    /// implements EntryList
    @Override
    void remove(int idx) {
        Log.d(TAG, "remove");
        ChecklistItem item = (ChecklistItem)get(idx);
        mRemovedItems.push(item);
        mRemovedItemsIndex.push(mUnsorted.indexOf(item));
        mRemoveOpID.push(mNextRemoveOpID++);
        mUnsorted.remove(item);
        mSorted.remove(item);
        notifyListChanged();
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
        for (ChecklistItem cli : mUnsorted) {
            int ocli = other.find(cli.mText, true);
            if (ocli >= 0) {
                if (cli.merge((ChecklistItem)other.get(ocli)))
                    changed = true;
            } // item not in other list
        }
        for (ChecklistItem ocli : mUnsorted) {
            int cli = find(ocli.mText, true);
            if (cli < 0) {
                mUnsorted.add(ocli);
                changed = true;
            }
        }
        if (changed) {
            reSort();
            mArrayAdapter.notifyDataSetChanged();
        }
        return changed;
    }

    int undoRemove() {
        int intValue = mRemoveOpID.peek();
        int undos = 0;
        while (mRemovedItemsIndex.size() > 0 && intValue == mRemoveOpID.peek()) {
            mUnsorted.add(mRemovedItemsIndex.pop(), mRemovedItems.pop());
            mRemoveOpID.pop();
            undos++;
        }
        if (undos > 0)
            notifyListChanged();
        return undos;
    }

    void checkAll() {
        for (ChecklistItem item : mUnsorted)
            item.setDone(true);
        notifyListChanged();
    }

    void uncheckAll() {
        for (ChecklistItem item : mUnsorted)
            item.setDone(false);
        notifyListChanged();
    }

    private void notifyListRefresh() {
        reSort();
        mArrayAdapter.notifyDataSetChanged();
    }

    /**
     * The "checked" status of an item has changed
     */
    @Override // EntryList
    void notifyListChanged() {
        notifyListRefresh();
        getContainer().save();
    }

    /**
     * @return number of items deleted
     */
    int deleteAllChecked() {
        Iterator<ChecklistItem> it = mUnsorted.iterator();
        int i = 0;
        while (it.hasNext()) {
            ChecklistItem next = it.next();
            if (next.mDone) {
                mRemovedItems.push(next);
                mRemovedItemsIndex.push(mUnsorted.indexOf(next));
                mRemoveOpID.push(mNextRemoveOpID);
                it.remove();
                i++;
            }
        }
        mNextRemoveOpID++;
        mArrayAdapter.notifyDataSetChanged();
        return i;
    }

    private void reSort() {
        mSorted = (ArrayList<ChecklistItem>)mUnsorted.clone();
        Collections.sort(mSorted, new Comparator<ChecklistItem>() {
            public int compare(ChecklistItem item, ChecklistItem item2) {
                return item.getText().compareToIgnoreCase(item2.getText());
            }
        });
    }

    @Override // JSONable
    public void fromJSON(Object jo) throws JSONException {
        JSONObject job = (JSONObject)jo;
        mUnsorted.clear();
        mListName = job.getString("name");
        mTimestamp = job.getLong("time");
        JSONArray items = job.getJSONArray("items");
        for (int i = 0; i < items.length(); i++) {
            ChecklistItem ci = new ChecklistItem(this, items.getJSONObject(i));
            mUnsorted.add(ci);
        }
        reSort();
    }

    @Override // JSONable
    public Object toJSON() throws JSONException {
        JSONObject job = new JSONObject();
        job.put("name", mListName);
        job.put("time", new Date().getTime());
        JSONArray items = new JSONArray();
        for (ChecklistItem item : mUnsorted) {
            items.put(item.toJSON());
        }
        job.put("items", items);
        return job;
    }

    /**
     * Format the list for sending as email
     */
    public String toPlainString() {
        StringBuilder sb = new StringBuilder();
        for (ChecklistItem next : getItems()) {
            if (next.mDone)
                sb.append("* ");
            sb.append(next.mText).append("\n");
        }
        return sb.toString();
    }
}
