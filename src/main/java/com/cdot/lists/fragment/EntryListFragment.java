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
package com.cdot.lists.fragment;

import android.app.Activity;
import android.os.Build;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.cdot.lists.Lister;
import com.cdot.lists.MainActivity;
import com.cdot.lists.R;
import com.cdot.lists.model.EntryList;
import com.cdot.lists.model.EntryListItem;
import com.cdot.lists.view.EntryListItemView;

import java.util.Collections;
import java.util.List;

/**
 * Base class of list view activities. The layouts for these activities are observe a layout template
 * that supplies the following resources:
 * ListView R.id.entry_list_activity_list_view
 * androidx.appcompat.widget.Toolbar R.id.entry_list_activity_toolbar
 * RelativeLayout R.id.entry_list_activity_layout
 * The common code here supports moving items in the list and some base menu functionality.
 */
public abstract class EntryListFragment extends Fragment implements EntryListItem.ChangeListener {
    private static final String TAG = EntryListFragment.class.getSimpleName();
    public transient EntryListItem mMovingItem;
    protected EntryListAdapter mArrayAdapter = null;

    protected ListView mListView;
    protected ViewGroup mListLayout;

    private transient EntryListItemView mMovingView;

    // The list we're viewing
    abstract EntryList getList();

    protected void checkpoint() {
        Activity act = getActivity();
        getLister().saveLists(act,
                okdata -> {
                    Log.d(TAG, "checkpoint save OK");
                },
                code -> {
                    act.runOnUiThread(() ->
                            Toast.makeText(act, code, Toast.LENGTH_SHORT).show());
                });
    }

    // Set the common bindings, obtained from the ViewBinding, and create the array adapter
    protected void setView(ListView listview, ViewGroup listlayout) {
        mListView = listview;
        mListLayout = listlayout;
        mArrayAdapter = new EntryListAdapter(getMainActivity());
        mListView.setAdapter(mArrayAdapter);
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
        super.onResume();
        getList().addChangeListener(this);
        onActivated();
    }

    public void onActivated() {
        String t = getList().getText();
        if (t == null)
            t = getMainActivity().getString(R.string.app_name);
        if (mListView != null)
            notifyAdapter(); // when coming back from settings, re-sort the list
        getMainActivity().getSupportActionBar().setTitle(t);
    }

    @Override // implement EntryListItem.ChangeListener
    public void onListChanged(EntryListItem item) {
        notifyAdapter();
    }

    /**
     * Get the controlling activity
     *
     * @return the main activity, parent of all fragments
     */
    public MainActivity getMainActivity() {
        return (MainActivity) getActivity();
    }

    /**
     * Get the application
     *
     * @return the main activity, parent of all fragments
     */
    public Lister getLister() {
        return (Lister) getActivity().getApplication();
    }

    /**
     * Get the list items in display order
     *
     * @return the list. May be modified, but entries point to data source
     */
    protected List<EntryListItem> getDisplayOrder() {
        List<EntryListItem> mDisplayed = getList().cloneItemList();
        if (getList().sort) {
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
        return !getList().sort;
    }

    /**
     * Make a view that can be used for dragging an item in the list. This will be the same type
     * as a normal entry in the list but will have no event handlers and may have display differences.
     *
     * @param movingItem the item being moved
     * @return a View that is used to display the dragged item
     */
    protected abstract EntryListItemView makeMovingView(EntryListItem movingItem);

    /**
     * Get the name of an HTML help asset appropriate for this fragment
     *
     * @return basename (without path) of an HTML help asset
     */
    protected abstract String getHelpAsset();

    /**
     * Moving items in the list
     *
     * @param motionEvent the event
     * @return true if the event is handled
     */
    public boolean dispatchTouchEvent(MotionEvent motionEvent) {
        EntryListItem movingItem = mMovingItem;
        // Check the item can be moved
        if (movingItem == null || !movingItem.isMoveable())
            return false;

        // get screen position of the ListView and the Activity
        int[] iArr = new int[2];

        mListView.getLocationOnScreen(iArr);
        int listViewTop = iArr[1];
        mListLayout.getLocationOnScreen(iArr);
        int activityTop = iArr[1];

        // Convert touch location to relative to the ListView
        int y = ((int) motionEvent.getY()) - listViewTop;

        // Get the index of the item being moved in the items list
        int itemIndex = getList().indexOf(movingItem);

        // Get the index of the moving view in the list of views. It will stay there until
        // the drag is released.
        int viewIndex = mListView.getChildCount() - 1;
        while (viewIndex >= 0) {
            if (((EntryListItemView) mListView.getChildAt(viewIndex)).getItem() == movingItem)
                break;
            viewIndex--;
        }
        if (viewIndex < 0)
            throw new RuntimeException("Can't find view for item: " + movingItem);
        //Log.d(TAG, "Moving item at " + itemIndex + " viewIndex " + viewIndex);
        int prevBottom = Integer.MIN_VALUE;
        if (viewIndex > 0) // Not first view
            prevBottom = mListView.getChildAt(viewIndex - 1).getBottom();

        int nextTop = Integer.MAX_VALUE;
        if (viewIndex < mListView.getChildCount() - 1) // Not last view
            nextTop = mListView.getChildAt(viewIndex + 1).getTop();

        int halfItemHeight = mListView.getChildAt(viewIndex).getHeight() / 2;
        int moveTo = itemIndex;
        if (y < prevBottom)
            moveTo--;
        else if (y > nextTop)
            moveTo++;

        //Log.d(TAG, "Compare " + y + " with " + prevBottom + " and " + nextTop + " moveTo " + moveTo);
        if (moveTo != itemIndex && moveTo >= 0 && moveTo < getList().size()) {
            Log.d(TAG, "Moved from " + itemIndex + " to " + moveTo);
            getList().remove(movingItem, false);
            getList().put(moveTo, movingItem);
            getList().notifyChangeListeners();
            checkpoint();
        }

        if (motionEvent.getAction() == MotionEvent.ACTION_MOVE) {
            if (mMovingView == null) {
                // Drag is starting
                //Log.d(TAG, "dispatchTouchEvent adding moving view ");
                mMovingView = makeMovingView(movingItem);
                // addView is not supported in AdapterView, so can't add the movingView there.
                // Instead have to add to the activity and adjust margins accordingly
                mListLayout.addView(mMovingView);
            }

            if (y < halfItemHeight)
                mListView.smoothScrollToPosition(itemIndex - 1);
            if (y > mListView.getHeight() - halfItemHeight)
                mListView.smoothScrollToPosition(itemIndex + 1);
            // Layout params for the parent, not for this view
            RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
            // Set the top margin to move the view to the right place relative to the Activity
            lp.setMargins(0, (listViewTop - activityTop) + y - halfItemHeight, 0, 0);
            mMovingView.setLayoutParams(lp);
        }
        if (motionEvent.getAction() == MotionEvent.ACTION_UP || motionEvent.getAction() == MotionEvent.ACTION_CANCEL) {
            mListLayout.removeView(mMovingView);
            mMovingItem = null;
            notifyAdapter();
            mMovingView = null;
        }
        return true;
    }

    @Override // Fragment
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        int it = menuItem.getItemId();
        if (it == R.id.action_alpha_sort) {
            Log.d(TAG, "alpha sort option selected");
            getList().sort = !getList().sort;
            getList().notifyChangeListeners();
            ViewGroup vg = getActivity().findViewById(R.id.main_activity);
            if (vg != null) vg.invalidate();
            checkpoint();
        } else if (it == R.id.action_help)
            getMainActivity().pushFragment(new HelpFragment(getHelpAsset()));
        else
            return false;

        return true;
    }

    @Override // Fragment
    public void onPrepareOptionsMenu(@NonNull Menu menu) {
        //super.onPrepareOptionsMenu(menu);
        MenuItem menuItem = menu.findItem(R.id.action_alpha_sort);
        if (getList() != null && getList().sort) {
            menuItem.setIcon(R.drawable.ic_action_alpha_sort_off);
            menuItem.setTitle(R.string.action_alpha_sort_off);
        } else {
            menuItem.setIcon(R.drawable.ic_action_alpha_sort_on);
            menuItem.setTitle(R.string.action_alpha_sort_on);
        }
        menuItem = menu.findItem(R.id.action_save);
        menuItem.setVisible(getLister().getBool(Lister.PREF_LAST_STORE_FAILED));
    }

    /**
     * Adapter for the list. This is only created when the list is actually displayed.
     */
    private class EntryListAdapter extends ArrayAdapter<EntryListItem> {

        EntryListAdapter(MainActivity act) {
            super(act, 0);
        }

        @Override // ArrayAdapter
        public @NonNull
        View getView(int i, View convertView, @NonNull ViewGroup viewGroup) {
            EntryListItem item = getDisplayOrder().get(i);
            EntryListItemView itemView = (EntryListItemView) convertView;
            if (itemView == null)
                itemView = getList().makeItemView(item, EntryListFragment.this);
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
