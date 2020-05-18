package com.cdot.lists;

import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.Stack;

/**
 * A checklist of checkable items
 */
class Checklist extends ArrayList<Checklist.ChecklistItem> {
    private static final String TAG = "Checklist";

    class ChecklistItem {
        boolean mDone;
        String mText;

        ChecklistItem(String str, boolean z) {
            mText = str;
            mDone = z;
        }

        ChecklistItem(ChecklistItem clone) {
            mText = clone.mText;
            mDone = clone.mDone;
        }

        Checklist getChecklist() {
            return Checklist.this;
        }

        private void setDone(boolean z) {
            mDone = z;
            mParent.save();
            mArrayAdapter.notifyDataSetChanged();
        }

        String getText() {
            return mText;
        }

        void setText(String str) {
            mText = str;
            mParent.save();
            mArrayAdapter.notifyDataSetChanged();
        }
    }

    /**
     * Adapter for the array of items in the list
     */
    private class ItemsArrayAdapter extends ArrayAdapter<String> {
        ItemsArrayAdapter() {
            super(mParent.mContext, 0, new ArrayList<String>());
        }

        @Override
        public @NonNull
        View getView(int i, View view, @NonNull ViewGroup viewGroup) {
            ChecklistItem item;
            ChecklistItemView itemView;
            if (Settings.getBool(Settings.moveCheckedItemsToBottom) && !mIsBeingEdited) {
                 if (i < getUncheckedCount())
                    item = getUnchecked(i);
                 else
                    item = getChecked(i - getUncheckedCount());
            } else
                item = Checklist.this.get(i);
            if (view == null) {
                assert item != null;
                itemView = new ChecklistItemView(item, mParent.mContext);
            } else {
                itemView = (ChecklistItemView) view;
                itemView.setItem(item);
            }
            itemView.updateView();
            return itemView;
        }

        @Override
        public int getCount() {
            return Checklist.this.size();
        }
    }
    ItemsArrayAdapter mArrayAdapter;

    String mListName;
    boolean mIsBeingEdited = false;
    long mTimestamp = 0;

    private Stack<ChecklistItem> mRemovedItems;
    private Stack<Integer> mRemovedItemsId;
    private Stack<Integer> mRemovedItemsIndex;
    private Checklists mParent;

    private transient ChecklistItem mMovingItem = null;
    private transient int mRemoveIdCount = 0;

    /**
     * Construct and load from cache
     * @param name private file list is stored in
     * @param parent container
     */
    Checklist(String name, Checklists parent) {
        initMembers(parent);
        mListName = name;
    }

    /**
     * Process the JSON given and load from it
     * @param job the JSON object
     * @throws JSONException if something goes wrong
     */
    Checklist(JSONObject job, Checklists parent) throws JSONException {
        mParent = parent;
        fromJSON(job);
    }

    /**
     * Load from JSON read from the given stream
     * @param stream source of JSON
     * @param parent the container object
     * @throws Exception IOException or JSONException
     */
    Checklist(InputStream stream, Checklists parent) throws IOException, JSONException {
        initMembers(parent);
        fromStream(stream);
    }

    /**
     * Construct by copying an existing list and saving it to a new list
     * @param copy list to clone
     */
    Checklist(Checklist copy, Checklists parent) {
        initMembers(parent);
        mListName = copy.mListName;
        mIsBeingEdited = copy.mIsBeingEdited;
        for (ChecklistItem item : copy) {
            add(new ChecklistItem(item));
        }
    }

    /**
     * Initiailise fields, shared between constructors
     * @param parent
     */
    private void initMembers(Checklists parent) {
        mParent = parent;
        mRemovedItems = new Stack<>();
        mRemovedItemsIndex = new Stack<>();
        mRemovedItemsId = new Stack<>();
        mArrayAdapter = new ItemsArrayAdapter();
        mIsBeingEdited = false;
    }

    /**
     * Get the ith checked item
     * @param i index
     * @return the ith checked item, or null if no items are checked
     */
    private ChecklistItem getChecked(int i) {
        Iterator it = iterator();
        int count = -1;
        while (it.hasNext()) {
            ChecklistItem item = (ChecklistItem) it.next();
            if (item.mDone && ++count == i)
                return item;
        }
        // No checked items
        return null;
    }

    /**
     * Get the ith unchecked item
     * @param i index of unchecked item to get
     * @return the ith unchecked item, or null if no items are unchecked
     */
    private ChecklistItem getUnchecked(int i) {
        Iterator it = iterator();
        int count = -1;
        while (it.hasNext()) {
            ChecklistItem item = (ChecklistItem) it.next();
            if (!item.mDone && ++count == i)
                return item;
        }
        // No items unchecked
        return null;
    }

    /* access modifiers changed from: package-private */
    void setEditMode(boolean z) {
        mIsBeingEdited = z;
        mArrayAdapter.notifyDataSetChanged();
        mParent.save();
    }

    /**
     * Get the number of checked items
     * @return the number of checked items
     */
    private int getCheckedCount() {
        Iterator<ChecklistItem> it = iterator();
        int i = 0;
        while (it.hasNext()) {
            if (it.next().mDone)
                i++;
        }
        return i;
    }

    /**
     * Get the number of unchecked items
     * @return the number of unchecked items
     */
    private int getUncheckedCount() {
        return size() - getCheckedCount();
    }

    /**
     * Find an item in the list
     * @param str item to find  
     * @param matchCase true to match case
     * @return item or null if not found
     */
    ChecklistItem find(String str, boolean matchCase) {
        for (ChecklistItem next : this) {
            if (next.mText.equalsIgnoreCase(str))
                return next;
        }
        if (matchCase)
            return null;
        for (ChecklistItem next2 : this) {
            if (next2.mText.toLowerCase().contains(str.toLowerCase()))
                return next2;
        }
        return null;
    }

    void moveItemToPosition(ChecklistItem item, int i) {
        if (i >= 0 && i <= size() - 1) {
            remove(item);
            add(i, item);
            mArrayAdapter.notifyDataSetChanged();
            mParent.save();
        }
    }

    void add(String str) {
        Log.d(TAG, "Add " + str);
        if (Settings.getBool(Settings.addNewItemsAtTopOfList)) {
            add(0, new ChecklistItem(str, false));
        } else {
            add(new ChecklistItem(str, false));
        }
        mArrayAdapter.notifyDataSetChanged();
        mParent.save();
    }

    void remove(ChecklistItem item) {
        Log.d(TAG, "remove");
        mRemovedItems.push(item);
        mRemovedItemsIndex.push(indexOf(item));
        mRemovedItemsId.push(mRemoveIdCount);
        super.remove(item);
        mRemoveIdCount++;
        mArrayAdapter.notifyDataSetChanged();
        mParent.save();
    }

    void undoRemove() {
        if (mRemovedItemsIndex.size() == 0) {
            Toast.makeText(mParent.mContext, R.string.no_deleted_items, Toast.LENGTH_SHORT).show();
            return;
        }
        int intValue = mRemovedItemsId.peek();
        int i = 0;
        while (mRemovedItemsIndex.size() != 0 && intValue == mRemovedItemsId.peek()) {
            add(mRemovedItemsIndex.pop(), mRemovedItems.pop());
            mRemovedItemsId.pop();
            i++;
        }
        mArrayAdapter.notifyDataSetChanged();
        mParent.save();
        Toast.makeText(mParent.mContext, mParent.mContext.getString(R.string.x_items_restored, i), Toast.LENGTH_SHORT).show();
    }

    void checkAll() {
        for (ChecklistItem item : this) {
            item.setDone(true);
        }
        mArrayAdapter.notifyDataSetChanged();
        mParent.save();
    }

    void uncheckAll() {
        for (ChecklistItem item : this) {
            item.setDone(false);
        }
        mArrayAdapter.notifyDataSetChanged();
        mParent.save();
    }

    /**
     * The "checked" status of an item has changed
     */
    void notifyItemChecked() {
        mArrayAdapter.notifyDataSetChanged();
        mParent.save();
    }

    void deleteAllChecked() {
        Iterator<ChecklistItem> it = iterator();
        int i = 0;
        while (it.hasNext()) {
            ChecklistItem next = it.next();
            if (next.mDone) {
                mRemovedItems.push(next);
                mRemovedItemsIndex.push(indexOf(next));
                mRemovedItemsId.push(mRemoveIdCount);
                it.remove();
                i++;
            }
        }
        mRemoveIdCount++;
        mArrayAdapter.notifyDataSetChanged();
        Toast.makeText(mParent.mContext, mParent.mContext.getString(R.string.x_items_deleted, i), Toast.LENGTH_SHORT).show();
    }

    void sort() {
        Collections.sort(this, new Comparator<ChecklistItem>() {
            public int compare(ChecklistItem item, ChecklistItem item2) {
                return item.getText().compareToIgnoreCase(item2.getText());
            }
        });
        mArrayAdapter.notifyDataSetChanged();
    }

    ChecklistItem getMovingItem() {
        return mMovingItem;
    }

    void setMovingItem(ChecklistItem item) {
        if (mMovingItem == null || item == null) {
            mMovingItem = item;
        }
        mArrayAdapter.notifyDataSetChanged();
    }

    /**
     * Load from a JSON object
     * @param job JSON object
     * @throws JSONException if anything goes wrong
     */
    private void fromJSON(JSONObject job) throws JSONException {
        clear();
        mListName = job.getString("name");
        mTimestamp = job.getLong("time");
        mIsBeingEdited = job.getBoolean("editmode");
        JSONArray items = job.getJSONArray("items");
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            add(new ChecklistItem(item.getString("name"), item.getBoolean("done")));
        }
    }

    void fromStream(InputStream stream) throws IOException, JSONException {
        StringBuilder sb = new StringBuilder();
        String line;
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(stream));
        while ((line = bufferedReader.readLine()) != null)
            sb.append(line);
        fromJSON(new JSONObject(sb.toString()));
    }

    /**
     * Save the checklist to a json object
     * @return a String
     * @throws JSONException if something goes wrong
     */
    JSONObject toJSON() throws JSONException {
        JSONObject job = new JSONObject();
        job.put("name", mListName);
        job.put("time", new Date().getTime());
        job.put("editmode", mIsBeingEdited);
        JSONArray items = new JSONArray();
        for (ChecklistItem item : this) {
            JSONObject iob = new JSONObject();
            iob.put("name", item.mText);
            iob.put("done", item.mDone);
            items.put(iob);
        }
        job.put("items", items);
        return job;
    }

    // Format the list for sending as email

    public String toPlainString() {
        StringBuilder sb = new StringBuilder();
        sb.append("List: \"").append(mListName).append("\":\n\n");
        for (ChecklistItem next : this) {
            if (next.mDone)
                sb.append("* ");
            sb.append(next.mText).append("\n");
        }
        return sb.toString();
    }
}
