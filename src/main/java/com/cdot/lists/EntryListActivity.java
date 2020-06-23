/*
 * Copyright C-Dot Consultants 2020 - MIT license
 */
package com.cdot.lists;

import android.content.Intent;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.RelativeLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

/**
 * Base class of list view activities. The layouts for these activities are observe a layout template
 * that supplies the following resources:
 * ListView R.id.entry_list_activity_list_view
 * androidx.appcompat.widget.Toolbar R.id.entry_list_activity_toolbar
 * RelativeLayout R.id.entry_list_activity_layout
 * The common code here supports moving items in the list and some base menu functionality.
 */
abstract class EntryListActivity extends AppCompatActivity {
    private static final String TAG = "EntryListActivity";

    protected EntryList mList;
    private EntryListItemView mMovingView;

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
     * Associate an EntryList with this activity
     * @param list the list to associate
     */
    void setList(EntryList list) {
        mList = list;
        EntryList.Adapter adapter = new EntryList.Adapter(list, this);
        // Set the adapter on the ListView
        ListView view = findViewById(R.id.entry_list_activity_list_view);
        view.setAdapter(adapter);
        // Tell the list about the adapter so it can refresh
        list.setArrayAdapter(adapter);
    }

    /**
     * Set the layout resource for this activity
     * @param resId the R.layout
     */
    protected void setLayout(int resId) {
        setContentView(resId);
        Toolbar tb = findViewById(R.id.entry_list_activity_toolbar);
        setSupportActionBar(tb);
    }

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
    @Override
    public boolean dispatchTouchEvent(MotionEvent motionEvent) {
        EntryListItem movingItem = mList.mMovingItem;
        // Check the item can be moved
        if (movingItem == null || !movingItem.isMoveable())
            return super.dispatchTouchEvent(motionEvent);

        // get screen position of the ListView and the Activity
        int[] iArr = new int[2];
        ListView cl = findViewById(R.id.entry_list_activity_list_view);
        RelativeLayout layout = findViewById(R.id.entry_list_activity_layout);

        cl.getLocationOnScreen(iArr);
        int listViewTop = iArr[1];
        layout.getLocationOnScreen(iArr);
        int activityTop = iArr[1];

        // Convert touch location to relative to the ListView
        int y = ((int) motionEvent.getY()) - listViewTop;

        // Get the index of the item being moved in the items list
        int itemIndex = mList.indexOf(movingItem);
        // Get the index of the moving view in the list of views (should be last?)
        int viewIndex = cl.getChildCount() - 1;
        while (viewIndex >= 0) {
            if (((EntryListItemView) cl.getChildAt(viewIndex)).getItem() == movingItem)
                break;
            viewIndex--;
        }
        if (viewIndex < 0)
            throw new RuntimeException("Can't find view for item: " + movingItem);

        int prevBottom = Integer.MIN_VALUE;
        if (viewIndex > 0) // Not first view
            prevBottom = cl.getChildAt(viewIndex - 1).getBottom();

        int nextTop = Integer.MAX_VALUE;
        if (viewIndex < cl.getChildCount() - 1) // Not last view
            nextTop = cl.getChildAt(viewIndex + 1).getTop();

        int halfItemHeight = cl.getChildAt(viewIndex).getHeight() / 2;
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
                layout.addView(mMovingView);
            }

            if (y < halfItemHeight)
                cl.smoothScrollToPosition(itemIndex - 1);
            if (y > cl.getHeight() - halfItemHeight)
                cl.smoothScrollToPosition(itemIndex + 1);
            // Layout params for the parent, not for this view
            RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
            // Set the top margin to move the view to the right place relative to the Activity
            lp.setMargins(0, (listViewTop - activityTop) + y - halfItemHeight, 0, 0);
            mMovingView.setLayoutParams(lp);
        }
        if (motionEvent.getAction() == MotionEvent.ACTION_UP || motionEvent.getAction() == MotionEvent.ACTION_CANCEL) {
            layout.removeView(mMovingView);
            mList.setMovingItem(null);
            mMovingView = null;
        }
        return true;
    }

    // Action menu
    @Override // AppCompatActivity
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        switch (menuItem.getItemId()) {
            default:
                throw new Error("WTF MENU" + menuItem.getItemId());
            case R.id.action_alpha_sort:
                mList.mShowSorted = !mList.mShowSorted;
                mList.notifyListChanged(true);
                invalidateOptionsMenu();
                return true;
            case R.id.action_help:
                Intent hIntent = new Intent(this, HelpActivity.class);
                hIntent.putExtra("page", getHelpAsset());
                startActivity(hIntent);
                return true;
        }
    }

    @Override // AppCompatActivity
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem menuItem = menu.findItem(R.id.action_alpha_sort);
        if (mList.mShowSorted) {
            menuItem.setIcon(R.drawable.ic_action_alpha_sort_off);
            menuItem.setTitle(R.string.action_alpha_sort_off);
        } else {
            menuItem.setIcon(R.drawable.ic_action_alpha_sort_on);
            menuItem.setTitle(R.string.action_alpha_sort_on);
        }

        return super.onPrepareOptionsMenu(menu);
    }
}
