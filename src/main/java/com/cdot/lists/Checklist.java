package com.cdot.lists;

import android.content.Context;
import android.net.Uri;
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
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Objects;
import java.util.Stack;

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

        /* access modifiers changed from: package-private */
        Checklist getChecklist() {
            return Checklist.this;
        }

        private void setDone(boolean z) {
            mDone = z;
            saveToCache();
            mArrayAdapter.notifyDataSetChanged();
        }

        /* access modifiers changed from: package-private */
        String getText() {
            return mText;
        }

        /* access modifiers changed from: package-private */
        void setText(String str) {
            mText = str;
            saveToCache();
            mArrayAdapter.notifyDataSetChanged();
        }
    }

    /**
     * Adapter for the array of items in the list
     */
    private class ItemsArrayAdapter extends ArrayAdapter<String> {
        ItemsArrayAdapter() {
            super(mContext, 0, new ArrayList<String>());
        }

        @Override
        public @NonNull
        View getView(int i, View view, @NonNull ViewGroup viewGroup) {
            ChecklistItem item;
            ChecklistItemView itemView;
            if (Settings.getBool(Settings.moveCheckedItemsToBottom) && !isEditMode()) {
                 if (i < getUncheckedCount())
                    item = getUnchecked(i);
                 else
                    item = getChecked(i - getUncheckedCount());
            } else
                item = Checklist.this.get(i);
            if (view == null) {
                assert item != null;
                itemView = new ChecklistItemView(item, mContext);
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

    private Context mContext;
    private boolean mIsEditMode = false;
    private String mListName;
    private transient ChecklistItem mMovingItem = null;
    private transient int mRemoveIdCount = 0;
    private Stack<ChecklistItem> mRemovedItems;
    private Stack<Integer> mRemovedItemsId;
    private Stack<Integer> mRemovedItemsIndex;

    /**
     * Construct and load from cache
     * @param name private file list is stored in
     * @param cxt context
     */
    Checklist(String name, Context cxt) {
        initMembers(cxt);
        mListName = name;
        loadFromCache();
    }

    Checklist(Uri uri, Context cxt) throws Exception {
        initMembers(cxt);
        InputStream stream;
        if (Objects.equals(uri.getScheme(), "file")) {
            stream = new FileInputStream(new File(uri.getPath()));
        } else if (Objects.equals(uri.getScheme(), "content")) {
            stream = mContext.getContentResolver().openInputStream(uri);
        } else {
            throw new IOException("Failed to load external list. Unknown uri scheme: " + uri.getScheme());
        }
        loadFromStream(stream);
    }

    /**
     * Parse the JSON given and load from it
     * @param job the JSON object
     * @throws JSONException if something goes wrong
     */
    Checklist(JSONObject job) throws JSONException {
        loadFromJSON(job);
    }

    /**
     * Construct by copying an existing list and saving it to a new list
     * @param copy list to clone
     */
    Checklist(Checklist copy) {
        initMembers(copy.mContext);
        mListName = copy.getListName() + " (copy)";
        mIsEditMode = copy.isEditMode();
        for (ChecklistItem item : copy) {
            add(new ChecklistItem(item));
        }
    }

    private void initMembers(Context cxt) {
        mContext = cxt;
        mRemovedItems = new Stack<>();
        mRemovedItemsIndex = new Stack<>();
        mRemovedItemsId = new Stack<>();
        mArrayAdapter = new ItemsArrayAdapter();
        mIsEditMode = false;
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

    String getListName() {
        return mListName;
    }

    /* access modifiers changed from: package-private */
    void setListName(String str) {
        mListName = str;
    }

    /* access modifiers changed from: package-private */
    boolean isEditMode() {
        return mIsEditMode;
    }

    /* access modifiers changed from: package-private */
    void setEditMode(boolean z) {
        mIsEditMode = z;
        mArrayAdapter.notifyDataSetChanged();
        saveToCache();
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

    String find(String str, boolean z) {
        for (ChecklistItem next : this) {
            if (next.mText.equalsIgnoreCase(str)) {
                return next.mText;
            }
        }
        if (z) {
            return null;
        }
        for (ChecklistItem next2 : this) {
            if (next2.mText.toLowerCase().contains(str.toLowerCase())) {
                return next2.mText;
            }
        }
        return null;
    }

    void moveItemToPosition(ChecklistItem item, int i) {
        if (i >= 0 && i <= size() - 1) {
            remove(item);
            add(i, item);
            mArrayAdapter.notifyDataSetChanged();
            saveToCache();
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
        saveToCache();
    }

    void remove(ChecklistItem item) {
        Log.d(TAG, "remove");
        mRemovedItems.push(item);
        mRemovedItemsIndex.push(indexOf(item));
        mRemovedItemsId.push(mRemoveIdCount);
        super.remove(item);
        mRemoveIdCount++;
        mArrayAdapter.notifyDataSetChanged();
        saveToCache();
    }

    void undoRemove() {
        if (mRemovedItemsIndex.size() == 0) {
            Toast.makeText(mContext, R.string.no_deleted_items, Toast.LENGTH_SHORT).show();
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
        saveToCache();
        Toast.makeText(mContext, mContext.getString(R.string.x_items_restored, i), Toast.LENGTH_SHORT).show();
    }

    void checkAll() {
        for (ChecklistItem item : this) {
            item.setDone(true);
        }
        mArrayAdapter.notifyDataSetChanged();
        saveToCache();
    }

    void uncheckAll() {
        for (ChecklistItem item : this) {
            item.setDone(false);
        }
        mArrayAdapter.notifyDataSetChanged();
        saveToCache();
    }

    /**
     * The "checked" status of an item has changed
     */
    void notifyItemChecked() {
        mArrayAdapter.notifyDataSetChanged();
        saveToCache();
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
        saveToCache();
        Toast.makeText(mContext, mContext.getString(R.string.x_items_deleted, i), Toast.LENGTH_SHORT).show();
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

    void saveToCache() {
        try {
            FileOutputStream stream = mContext.openFileOutput(URLEncoder.encode(mListName, "UTF-8"), Context.MODE_PRIVATE);
            stream.write(this.toJSON().toString().getBytes());
            stream.close();
            Log.d(TAG, "Save list " + mListName + " " + size());
        } catch (Exception e) {
            Log.d(TAG, "Save list exception " + e.getMessage());
            e.printStackTrace();
        }
        // TODO: signal the backup store to queue a save
    }

    private void loadFromCache() {
        try {
            FileInputStream stream = mContext.openFileInput(URLEncoder.encode(mListName, "UTF-8"));
            loadFromStream(stream);
            Log.d(TAG, "Load list " + mListName + " " + size());
        } catch (Exception e) {
            Log.e(TAG, mListName + " could not be loaded from cache: " + e.getMessage());
        }
    }

    private void loadFromStream(InputStream stream) throws Exception {
        StringBuilder sb = new StringBuilder();
        String line;
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(stream));
        while ((line = bufferedReader.readLine()) != null)
            sb.append(line);
        loadFromJSON(new JSONObject(sb.toString()));
    }

    // JSON handling
    private void loadFromJSON(JSONObject job) throws JSONException {
        clear();
        mListName = job.getString("name");
        mIsEditMode = job.getBoolean("editmode");
        JSONArray items = job.getJSONArray("items");
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            add(new ChecklistItem(item.getString("name"), item.getBoolean("done")));
        }
    }

    /**
     * Save the checklist to a json string
     * @return a String
     * @throws JSONException if something goes wrong
     */
    JSONObject toJSON() throws JSONException {
        JSONObject job = new JSONObject();
        job.put("name", mListName);
        job.put("editmode", mIsEditMode);
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
