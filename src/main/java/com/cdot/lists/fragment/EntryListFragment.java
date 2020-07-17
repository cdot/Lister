/*
 * Copyright C-Dot Consultants 2020 - MIT license
 */
package com.cdot.lists.fragment;

import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.cdot.lists.view.EntryListItemView;
import com.cdot.lists.MainActivity;
import com.cdot.lists.R;
import com.cdot.lists.Settings;
import com.cdot.lists.model.EntryList;
import com.cdot.lists.model.EntryListItem;

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
    private static final String TAG = "EntryListFragment";

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
                itemView = mList.makeItemView(item, EntryListFragment.this);
            else
                itemView.setItem(item);
            itemView.updateView();
            return itemView;
        }

        @Override
        public int getCount() {
            return mList.size();
        }
    }

    protected EntryListAdapter mArrayAdapter = null;

    // Shortcut to the list we're viewing
    protected EntryList mList;

    protected ListView mListView;
    protected ViewGroup mListLayout;

    private transient EntryListItemView mMovingView;
    public transient EntryListItem mMovingItem;

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
    protected void notifyDataSetChanged() {
        if (mArrayAdapter != null)
            mArrayAdapter.notifyDataSetChanged();
    }

    @Override // Implement EntryListItem.ChangeListener
    public void onListItemChanged(EntryListItem item, boolean doSave) {
        notifyDataSetChanged();
    }

    // Get the controlling activity
    public MainActivity getMainActivity() {
        return (MainActivity)getActivity();
    }

    /**
     * Get the list items in display order
     */
    protected List<EntryListItem> getDisplayOrder() {
        List<EntryListItem> mDisplayed = mList.cloneItemList();
        if (mList.isShownSorted())
            Collections.sort(mDisplayed, (item, item2) -> item.getText().compareToIgnoreCase(item2.getText()));
        return mDisplayed;
    }

    /**
     * Make a view that can be used for dragging an item in the list. This will be the same type
     * as a normal entry in the list but will have no event handlers and may have display differences.
     * @param movingItem the item being moved
     * @return a View that is used to display the dragged item
     */
    protected abstract EntryListItemView makeMovingView(EntryListItem movingItem);

    /**
     * Get the name of an HTML help asset
     * @return basename (without path) of an HTML help asset
     */
    protected abstract String getHelpAsset();

    /**
     * Move the item to a new position in the list. Does not affect the display order.
     *
     * @param item item to move
     * @param i    position to move it to, position in the unsorted list!
     */
    private void moveItemToPosition(EntryListItem item, int i) {
        mList.remove(item, false);
        mList.put(i, item);
        mList.notifyListChanged(true);
    }

    /**
     * Moving items in the list
     * @param motionEvent the event
     * @return true if the event is handled
     */
    public boolean dispatchTouchEvent(MotionEvent motionEvent) {
        EntryListItem movingItem = mMovingItem;
        // Check the item can be moved
        if (movingItem == null || !movingItem.isMoveable() || getMainActivity().getSettings().getBool(Settings.showCheckedAtEnd))
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
        int itemIndex = mList.indexOf(movingItem);
        // Get the index of the moving view in the list of views (should be last?)
        int viewIndex = mListView.getChildCount() - 1;
        while (viewIndex >= 0) {
            if (((EntryListItemView) mListView.getChildAt(viewIndex)).getItem() == movingItem)
                break;
            viewIndex--;
        }
        if (viewIndex < 0)
            throw new RuntimeException("Can't find view for item: " + movingItem);

        int prevBottom = Integer.MIN_VALUE;
        if (viewIndex > 0) // Not first view
            prevBottom = mListView.getChildAt(viewIndex - 1).getBottom();

        int nextTop = Integer.MAX_VALUE;
        if (viewIndex < mListView.getChildCount() - 1) // Not last view
            nextTop = mListView.getChildAt(viewIndex + 1).getTop();

        int halfItemHeight = mListView.getChildAt(viewIndex).getHeight() / 2;
        if (y < prevBottom && itemIndex > 0) {
            moveItemToPosition(movingItem, itemIndex - 1);
        } else if (y > nextTop && itemIndex < mList.size() - 1) {
            moveItemToPosition(movingItem, itemIndex + 1);
        }

        if (motionEvent.getAction() == MotionEvent.ACTION_MOVE) {
            if (mMovingView == null) {
                // Drag is starting
                Log.d(TAG, "dispatchTouchEvent add moving view ");
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
            notifyDataSetChanged();
            mMovingView = null;
        }
        return true;
    }

    // Action menu
    @Override // AppCompatActivity
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        switch (menuItem.getItemId()) {
            case R.id.action_alpha_sort:
                mList.toggleShownSorted();
                mList.notifyListChanged(true);
                getMainActivity().invalidateOptionsMenu();
                return true;
            case R.id.action_help:
                getMainActivity().pushFragment(new HelpFragment(getHelpAsset()));
                return true;
        }
        return false;
    }

    @Override // Fragment
    public void onPrepareOptionsMenu(@NonNull Menu menu) {
        MenuItem menuItem = menu.findItem(R.id.action_alpha_sort);
        if (mList != null && mList.isShownSorted()) {
            menuItem.setIcon(R.drawable.ic_action_alpha_sort_off);
            menuItem.setTitle(R.string.action_alpha_sort_off);
        } else {
            menuItem.setIcon(R.drawable.ic_action_alpha_sort_on);
            menuItem.setTitle(R.string.action_alpha_sort_on);
        }

        super.onPrepareOptionsMenu(menu);
    }
}
