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
package com.cdot.lists;

import android.content.Intent;
import android.os.Build;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.cdot.lists.model.EntryList;
import com.cdot.lists.model.EntryListItem;
import com.cdot.lists.view.EntryListItemView;

import java.util.Collections;
import java.util.List;

/**
 * Base class of list view activities.
 * The common code here supports moving items in the list and some base menu functionality.
 */
public abstract class EntryListActivity extends ListerActivity implements EntryListItem.ChangeListener {
    private static final String TAG = EntryListActivity.class.getSimpleName();

    public transient EntryListItem mMovingItem;
    protected EntryListAdapter mArrayAdapter = null;

    private transient EntryListItemView mMovingView;

    // The list we're viewing
    abstract EntryList getList();

    abstract ListView getListView();

    // Set the common bindings, obtained from the ViewBinding, and create the array adapter
    protected void makeAdapter() {
        if (mArrayAdapter == null) {
            mArrayAdapter = new EntryListAdapter(this);
            getListView().setAdapter(mArrayAdapter);
        }
    }

    @Override // ListerActivity
    public void onListsLoaded() {
        super.onListsLoaded();
        EntryList list = getList();
        Log.d(TAG, "onListsLoaded list " + list);
        list.notifyChangeListeners();
        String t = list.getText();
        if (t == null)
            t = getString(R.string.app_name);
        getSupportActionBar().setTitle(t);
    }

    /**
     * List contents have changed; notify the adapter
     */
    protected void notifyAdapter() {
        if (mArrayAdapter != null)
            mArrayAdapter.notifyDataSetChanged();
    }

    @Override
    public void onPause() {
        getList().removeChangeListener(this);
        super.onPause();
    }

    @Override
    public void onResume() {
        Log.d(TAG, "onResume");
        super.onResume();
        EntryList ls = getList();
        ls.addChangeListener(this);
        ls.notifyChangeListeners();
    }

    @Override // implement EntryListItem.ChangeListener
    public void onListChanged(EntryListItem item) {
        runOnUiThread(this::notifyAdapter);
    }

    /**
     * Get the list items in display order. Override to change the order of items in the list.
     *
     * @return the list. May be modified, but entries point to data source
     */
    protected List<EntryListItem> getDisplayOrder() {
        EntryList ls = getList();
        List<EntryListItem> mDisplayed = ls.cloneItemList();
        if (ls.getFlag(EntryList.displaySorted)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                mDisplayed.sort((item, item2) -> item.getText().compareToIgnoreCase(item2.getText()));
            else
                Collections.sort(mDisplayed, (item, item2) -> item.getText().compareToIgnoreCase(item2.getText()));
        }
        return mDisplayed;
    }

    /**
     * Used in ItemView to determine if list manual sort controls should be shown
     *
     * @return true if the list can be manually sorted
     */
    public boolean canManualSort() {
        return !getList().getFlag(EntryList.displaySorted) && !getList().getFlag("moveCheckedToEnd");
    }

    /**
     * Make a view that can be used to display an item in the list.
     *
     * @param movingItem the item being moved
     * @param drag true to make it a moving view. This will be the same type
     * as a normal entry in the list but will have no event handlers and may have display differences.
     * @return a View that is used to display the dragged item
     */
    protected abstract EntryListItemView makeItemView(EntryListItem movingItem, boolean drag);

    /**
     * Get the name of an HTML help asset appropriate for this fragment
     *
     * @return resource id for a raw html asset
     */
    protected abstract int getHelpAsset();

    /**
     * Moving items in the list
     *
     * @param motionEvent the event
     * @return true if the event is handled
     */
    synchronized public boolean dispatchTouchEvent(MotionEvent motionEvent) {
        // Check the item can be moved
        if (mMovingItem == null || !mMovingItem.isMoveable())
            return super.dispatchTouchEvent(motionEvent);

        // get screen position of the ListView and the Activity
        int[] iArr = new int[2];

        ListView lv = getListView();
        lv.getLocationOnScreen(iArr);
        int listViewTop = iArr[1];
        ViewParent vp = lv.getParent();
        ViewGroup listLayout = (ViewGroup)vp;
        listLayout.getLocationOnScreen(iArr);
        int activityTop = iArr[1];

        // Convert touch location to relative to the ListView
        int y = ((int) motionEvent.getY()) - listViewTop;
        EntryList list = getList();

        // Get the index of the item being moved in the items list
        int itemIndex = list.indexOf(mMovingItem);

        // Get the index of the moving view in the list of views. It will stay there until
        // the drag is released.
        int viewIndex = lv.getChildCount() - 1;
        while (viewIndex >= 0) {
            if (((EntryListItemView) lv.getChildAt(viewIndex)).getItem() == mMovingItem)
                break;
            viewIndex--;
        }
        if (viewIndex < 0)
            throw new RuntimeException("Can't find view for item: " + mMovingItem);
        //Log.d(TAG, "Moving item at " + itemIndex + " viewIndex " + viewIndex);
        int prevBottom = Integer.MIN_VALUE;
        if (viewIndex > 0) // Not first view
            prevBottom = lv.getChildAt(viewIndex - 1).getBottom();

        int nextTop = Integer.MAX_VALUE;
        if (viewIndex < lv.getChildCount() - 1) // Not last view
            nextTop = lv.getChildAt(viewIndex + 1).getTop();

        int halfItemHeight = lv.getChildAt(viewIndex).getHeight() / 2;
        int moveTo = itemIndex;
        if (y < prevBottom)
            moveTo--;
        else if (y > nextTop)
            moveTo++;

        //Log.d(TAG, "Compare " + y + " with " + prevBottom + " and " + nextTop + " moveTo " + moveTo);
        if (moveTo != itemIndex && moveTo >= 0 && moveTo < list.size()) {
            Log.d(TAG, "Moved " + mMovingItem.getText() + " from " + itemIndex + " to " + moveTo);
            list.remove(mMovingItem, false);
            list.put(moveTo, mMovingItem);
            list.notifyChangeListeners();
            checkpoint();
        }

        if (motionEvent.getAction() == MotionEvent.ACTION_MOVE) {
            if (mMovingView == null) {
                // Drag is starting
                //Log.d(TAG, "dispatchTouchEvent adding moving view ");
                mMovingView = makeItemView(mMovingItem, true);
                // addView is not supported in AdapterView, so can't add the movingView there.
                // Instead have to add to the activity and adjust margins accordingly
                listLayout.addView(mMovingView);
            }

            if (y < halfItemHeight)
                lv.smoothScrollToPosition(itemIndex - 1);
            if (y > lv.getHeight() - halfItemHeight)
                lv.smoothScrollToPosition(itemIndex + 1);
            // Layout params for the parent, not for this view
            EntryListItemView.LayoutParams lp = new EntryListItemView.LayoutParams(EntryListItemView.LayoutParams.MATCH_PARENT, EntryListItemView.LayoutParams.WRAP_CONTENT);
            // Set the top margin to move the view to the right place relative to the Activity
            lp.setMargins(0, (listViewTop - activityTop) + y - halfItemHeight, 0, 0);
            mMovingView.setLayoutParams(lp);
        }
        if (motionEvent.getAction() == MotionEvent.ACTION_UP || motionEvent.getAction() == MotionEvent.ACTION_CANCEL) {
            listLayout.removeView(mMovingView);
            this.mMovingItem = null;
            notifyAdapter();
            mMovingView = null;
        }
        return true;
    }

    @Override // Activity
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        int it = menuItem.getItemId();
        if (it == R.id.action_alpha_sort) {
            Log.d(TAG, "alpha sort option selected");
            EntryList ls = getList();
            if (ls.getFlag(EntryList.displaySorted))
                ls.clearFlag(EntryList.displaySorted);
            else
                ls.setFlag(EntryList.displaySorted);
            ls.notifyChangeListeners();
            invalidateOptionsMenu();
            checkpoint();
        } else if (it == R.id.action_help) {
            Intent hint = new Intent(this, HelpActivity.class);
            hint.putExtra(HelpActivity.ASSET_EXTRA, getHelpAsset());
            startActivity(hint);
        } else
            return false;

        return true;
    }

    @Override // AppCompatActivity
    public boolean onPrepareOptionsMenu(@NonNull Menu menu) {
        super.onPrepareOptionsMenu(menu);

        MenuItem menuItem = menu.findItem(R.id.action_alpha_sort);
        EntryList ls = getList();
        if (ls != null && ls.getFlag(EntryList.displaySorted)) {
            menuItem.setIcon(R.drawable.ic_action_alpha_sort_off);
            menuItem.setTitle(R.string.action_alpha_sort_off);
        } else {
            menuItem.setIcon(R.drawable.ic_action_alpha_sort_on);
            menuItem.setTitle(R.string.action_alpha_sort_on);
        }
        menuItem = menu.findItem(R.id.action_save);
        menuItem.setVisible(getLister().getBool(Lister.PREF_LAST_STORE_FAILED));

        return true;
    }

    @Override // AppCompatActivity
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        if (requestCode == REQUEST_PREFERENCES) {
            // Preferences may have changed; redraw the list
            notifyAdapter();
        } else
            super.onActivityResult(requestCode, resultCode, resultData);
    }

    /**
     * Adapter for the list. This is only created when the list is actually displayed.
     */
    private class EntryListAdapter extends ArrayAdapter<EntryListItem> {

        EntryListAdapter(AppCompatActivity act) {
            super(act, 0);
        }

        @Override // ArrayAdapter
        public @NonNull
        View getView(int i, View convertView, @NonNull ViewGroup viewGroup) {
            EntryListItem item = getDisplayOrder().get(i);
            EntryListItemView itemView = (EntryListItemView) convertView;
            if (itemView == null)
                itemView = makeItemView(item, false);
            else
                itemView.setItem(item);
            itemView.updateView();
            return itemView;
        }

        @Override
        public int getCount() {
            return getList().size();
        }
    }
}
