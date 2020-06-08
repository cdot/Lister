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
 * A checklist of checkable items
 */
class Checklist extends Serialisable {
    private static final String TAG = "Checklist";

    private ArrayList<Checklist.ChecklistItem> mUnsorted = new ArrayList<>();
    private ArrayList<Checklist.ChecklistItem> mSorted = new ArrayList<>();

    class ChecklistItem {
        private String mText;
        private boolean mDone;
        private long mDoneAt; // timestamp, only valid if mDone

        ChecklistItem(String str, boolean done) {
            mText = str;
            mDone = done;
            mDoneAt = done ? System.currentTimeMillis() : 0;
        }

        ChecklistItem(JSONObject jo) throws JSONException {
            mText = jo.getString("name");
            mDone = false;
            mDoneAt = 0;
            try {
                mDone = jo.getBoolean("done");
                mDoneAt = jo.getLong("at");
            } catch (JSONException ignored) {
            }
        }

        ChecklistItem(ChecklistItem clone) {
            mText = clone.mText;
            mDone = clone.mDone;
            mDoneAt = clone.mDoneAt;
        }

        Checklist getChecklist() {
            return Checklist.this;
        }

        String getText() {
            return mText;
        }

        boolean isDone() {
            return mDone;
        }

        /**
         * Merge this item's fields with another more recent version of the same item with the
         * same text. The more recent item's status takes precedence over this items
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
         * Set the item's text and trigger a save
         * @param str new text
         */
        void setText(String str) {
            mText = str;
        }

        /**
         * Set the item's done status and trigger a save
         * @param done new done status
         */
        void setDone(boolean done) {
            mDone = done;
            mDoneAt = System.currentTimeMillis();
        }

        Object toJSON() throws JSONException {
            JSONObject iob = new JSONObject();
            iob.put("name", mText);
            iob.put("done", mDone);
            if (mDone)
                iob.put("at", mDoneAt);
            return iob;
        }
    }

    /**
     * Adapter for the array of items in the list
     */
    private class ItemsArrayAdapter extends ArrayAdapter<String> {
        ItemsArrayAdapter(Context cxt) {
            super(cxt, 0, new ArrayList<String>());
        }

        @Override
        public @NonNull
        View getView(int i, View view, @NonNull ViewGroup viewGroup) {
            ChecklistItem item;
            ChecklistItemView itemView;
            if (Settings.getBool(Settings.showCheckedAtEnd)) {
                if (i < getUncheckedCount())
                    item = getUnchecked(i);
                else
                    item = getChecked(i - getUncheckedCount());
            } else if (Settings.getBool(Settings.forceAlphaSort))
                item = mSorted.get(i);
            else
                item = mUnsorted.get(i);
            if (view == null) {
                assert item != null;
                itemView = new ChecklistItemView(item, false, mParent.mContext);
            } else {
                itemView = (ChecklistItemView) view;
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

    ArrayAdapter mArrayAdapter;

    private String mListName;
    boolean mIsBeingEdited = false;
    long mTimestamp = 0;

    private Stack<ChecklistItem> mRemovedItems = new Stack<>();
    private Stack<Integer> mRemovedItemsId = new Stack<>();
    private Stack<Integer> mRemovedItemsIndex = new Stack<>();
    private Checklists mParent;

    transient ChecklistItem mMovingItem = null;
    private transient int mRemoveIdCount = 0;

    /**
     * Construct and load from cache
     *
     * @param name   private file list is stored in
     * @param parent container
     */
    Checklist(String name, Checklists parent) {
        super(parent.mContext);
        mParent = parent;
        mListName = name;
        mArrayAdapter = new ItemsArrayAdapter(mParent.mContext);
    }

    /**
     * Process the JSON given and load from it
     *
     * @param job the JSON object
     * @throws JSONException if something goes wrong
     */
    Checklist(JSONObject job, Checklists parent) throws JSONException {
        super(parent.mContext);
        mParent = parent;
        fromJSON(job);
        mArrayAdapter = new ItemsArrayAdapter(mParent.mContext);
    }

    /**
     * Load from JSON read from the given stream
     *
     * @param stream source of JSON
     * @param parent the container object
     * @throws Exception   IOException or JSONException
     */
    Checklist(InputStream stream, Checklists parent) throws Exception {
        super(parent.mContext);
        mParent = parent;
        fromStream(stream);
        mArrayAdapter = new ItemsArrayAdapter(mParent.mContext);
    }

    /**
     * Construct by copying an existing list and saving it to a new list
     *
     * @param copy list to clone
     */
    Checklist(Checklist copy, Checklists parent) {
        super(parent.mContext);
        mParent = parent;
        mListName = copy.mListName;
        for (ChecklistItem item : copy.mUnsorted)
            mUnsorted.add(new ChecklistItem(item));
        reSort();
        mArrayAdapter = new ItemsArrayAdapter(mParent.mContext);
    }

    private ArrayList<Checklist.ChecklistItem> getItems() {
        return (Settings.getBool(Settings.forceAlphaSort) ? mSorted : mUnsorted);
    }

    Checklist(Uri uri, Checklists parent) throws Exception {
        super(uri, parent.mContext);
        mParent = parent;
        mArrayAdapter = new ItemsArrayAdapter(mParent.mContext);
    }

    String getName() {
        return mListName;
    }

    void setName(String name) {
        mListName = name;
    }

    int size() {
        return mUnsorted.size();
    }

    /**
     * Get the index of the item in the base (unsorted) list
     * @param ci item
     * @return index of the item
     */
    int indexOf(ChecklistItem ci) {
        return mUnsorted.indexOf(ci);
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

    void setEditMode(boolean editing) {
        Log.d(TAG, "setEditMode " + editing);
        mIsBeingEdited = editing;
        mArrayAdapter.notifyDataSetChanged();
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

    /**
     * Find an item in the list
     *
     * @param str       item to find
     * @param matchCase true to match case
     * @return item or null if not found
     */
    ChecklistItem find(String str, boolean matchCase) {
        for (ChecklistItem next : mUnsorted) {
            if (next.mText.equalsIgnoreCase(str))
                return next;
        }
        if (matchCase)
            return null;
        for (ChecklistItem next2 : mUnsorted) {
            if (next2.mText.toLowerCase().contains(str.toLowerCase()))
                return next2;
        }
        return null;
    }

    /**
     * Operations on mUnsorted, since this can only be invoked when the list is unsorted
     * @param item
     * @param i
     */
    void moveItemToPosition(ChecklistItem item, int i) {
        if (i >= 0 && i <= mUnsorted.size() - 1) {
            remove(item);
            mUnsorted.add(i, item);
            notifyListChanged();
        }
    }

    int add(String str) {
        Log.d(TAG, "Add " + str);
        ChecklistItem item = new ChecklistItem(str, false);
        if (Settings.getBool(Settings.addToTop))
            mUnsorted.add(0, item);
        else
            mUnsorted.add(item);
        notifyListChanged();
        return getItems().indexOf(item);
    }

    void remove(ChecklistItem item) {
        Log.d(TAG, "remove");
        mRemovedItems.push(item);
        mRemovedItemsIndex.push(mUnsorted.indexOf(item));
        mRemovedItemsId.push(mRemoveIdCount);
        mUnsorted.remove(item);
        mSorted.remove(item);
        mRemoveIdCount++;
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
            ChecklistItem ocli = other.find(cli.mText, true);
            if (ocli != null) {
                if (cli.merge(ocli))
                    changed = true;
            } else
                this.remove(ocli);
        }
        for (ChecklistItem ocli : mUnsorted) {
            ChecklistItem cli = find(ocli.mText, true);
            if (cli == null) {
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
        int intValue = mRemovedItemsId.peek();
        int undos = 0;
        while (mRemovedItemsIndex.size() != 0 && intValue == mRemovedItemsId.peek()) {
            mUnsorted.add(mRemovedItemsIndex.pop(), mRemovedItems.pop());
            mRemovedItemsId.pop();
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

    /**
     * The "checked" status of an item has changed
     */
    void notifyListChanged() {
        reSort();
        mArrayAdapter.notifyDataSetChanged();
        mParent.save();
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
                mRemovedItemsId.push(mRemoveIdCount);
                it.remove();
                i++;
            }
        }
        mRemoveIdCount++;
        mArrayAdapter.notifyDataSetChanged();
        return i;
    }

    private void reSort() {
        mSorted = (ArrayList<Checklist.ChecklistItem>)mUnsorted.clone();
        Collections.sort(mSorted, new Comparator<ChecklistItem>() {
            public int compare(ChecklistItem item, ChecklistItem item2) {
                return item.getText().compareToIgnoreCase(item2.getText());
            }
        });
    }

    void setMovingItem(ChecklistItem item) {
        if (mMovingItem == null || item == null)
            mMovingItem = item;
        mArrayAdapter.notifyDataSetChanged();
    }

    /**
     * Load from a JSON object
     *
     * @param jo JSON object
     * @throws JSONException if anything goes wrong
     */
    void fromJSON(Object jo) throws JSONException {
        JSONObject job = (JSONObject)jo;
        mUnsorted.clear();
        mListName = job.getString("name");
        mTimestamp = job.getLong("time");
        JSONArray items = job.getJSONArray("items");
        for (int i = 0; i < items.length(); i++) {
            ChecklistItem ci = new ChecklistItem(items.getJSONObject(i));
            mUnsorted.add(ci);
        }
        reSort();
    }

    /**
     * Save the checklist to a json object
     *
     * @return a String
     */
    Object toJSON() throws JSONException {
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
