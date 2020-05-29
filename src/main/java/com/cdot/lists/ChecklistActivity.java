package com.cdot.lists;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;

import androidx.core.view.accessibility.AccessibilityEventCompat;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.cdot.lists.databinding.ChecklistActivityBinding;

import java.util.Objects;

public class ChecklistActivity extends AppCompatActivity {
    private static final String TAG = "ChecklistActivity";

    private static final int REQUEST_EXPORT_LIST = 4;

    private Checklist mList;
    private ChecklistItemView mMovingView;

    private ChecklistActivityBinding mBinding;

    private Checklists mChecklists;

    /**
     * Construct the checklist name passed in the Intent
     */
    @Override
    protected void onCreate(Bundle bundle) {
        Log.d(TAG, "onCreate");
        super.onCreate(bundle);
        //getDelegate().setLocalNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        mBinding = ChecklistActivityBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());
        setSupportActionBar(mBinding.checklistToolbar);

        EditText editText = mBinding.AddItemText;
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        editText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                if (i == EditorInfo.IME_ACTION_DONE) {
                    addNewItem();
                    return true;
                }
                return false;
            }
        });
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (mBinding.AddItemText.getText().toString().trim().length() == 0) {
                    mBinding.AddItemButton.setAlpha(0.5f);
                } else {
                    mBinding.AddItemButton.setAlpha(1f);
                }
            }
        });
        editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
        ImageButton imageButton = mBinding.AddItemButton;
        imageButton.setAlpha(0.5f);
        imageButton.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                addNewItem();
                return true;
            }
        });

        mChecklists = new Checklists(this, true);

        String listName = getIntent().getStringExtra("list");
        mList = mChecklists.findListByName(listName);

        if (mList == null) {
            // Create the list
            mList = new Checklist(listName, mChecklists);
            mChecklists.addList(mList);
        }

        Settings.setString(Settings.currentList, listName);
        Objects.requireNonNull(getSupportActionBar()).setTitle(mList.mListName);

        mBinding.CheckList.setAdapter(mList.mArrayAdapter);

        if (mList.size() == 0) {
            showNewItemPrompt(true);
            invalidateOptionsMenu();
        } else
            showNewItemPrompt(false);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        super.onActivityResult(requestCode, resultCode, resultData);
        if (requestCode == REQUEST_EXPORT_LIST && resultCode == Activity.RESULT_OK && resultData != null)
            mList.saveToUri(resultData.getData());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.checklist, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        Intent intent;
        switch (menuItem.getItemId()) {
            case R.id.action_check_all:
                mList.checkAll();
                return true;
            case R.id.action_copy:
            case R.id.action_delete:
            case R.id.action_help:
            case R.id.action_new_list:
            case R.id.action_rename:
            case R.id.action_settings:
            default:
                Log.d(TAG, "WTF MENU" + menuItem.getItemId());
                return super.onOptionsItemSelected(menuItem);
            case R.id.action_delete_checked:
                Toast.makeText(this, getString(R.string.x_items_deleted, mList.deleteAllChecked()), Toast.LENGTH_SHORT).show();
                if (mList.size() == 0) {
                    mList.setEditMode(true);
                    showNewItemPrompt(mList.mIsBeingEdited);
                    invalidateOptionsMenu();
                }
                return true;
            case R.id.action_edit:
                Checklist checkList = mList;
                checkList.setEditMode(!checkList.mIsBeingEdited);
                showNewItemPrompt(mList.mIsBeingEdited);
                invalidateOptionsMenu();
                return true;
            case R.id.action_rename_list:
                showRenameListDialog();
                return true;
            case R.id.action_sort_list:
                mList.sort();
                return true;
            case R.id.action_uncheck_all:
                mList.uncheckAll();
                return true;
            case R.id.action_undo_delete:
                int undone = mList.undoRemove();
                if (undone == 0)
                    Toast.makeText(this, R.string.no_deleted_items, Toast.LENGTH_SHORT).show();
                else
                    Toast.makeText(this, getString(R.string.x_items_restored, undone), Toast.LENGTH_SHORT).show();
                return true;
            case R.id.action_save_list_as:
                intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/json");
                startActivityForResult(intent, REQUEST_EXPORT_LIST);
                return true;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
    }

    @Override
    public void onAttachedToWindow() {
        if (Settings.getBool(Settings.showListInFrontOfLockScreen)) {
            getWindow().addFlags(AccessibilityEventCompat.TYPE_GESTURE_DETECTION_END);
        }
    }

    private void showNewItemPrompt(boolean z) {
        Log.d(TAG, "showNewItemPrompt " + z);
        mBinding.NewItemContainer.setVisibility(z ? View.VISIBLE : View.INVISIBLE);

        // Show/hide soft keyboard
        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (z) {
            mBinding.AddItemText.setFocusable(true);
            mBinding.AddItemText.setFocusableInTouchMode(true);
            mBinding.AddItemText.requestFocus();
            inputMethodManager.showSoftInput(mBinding.AddItemText, InputMethodManager.SHOW_IMPLICIT);
        } else {
            View currentFocus = getCurrentFocus();
            if (currentFocus == null)
                currentFocus = mBinding.AddItemText;
            inputMethodManager.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
        }
    }

    // Invoked from checklist_activity.xml
    public void onClickAddItem(View view) {
        addNewItem();
    }

    private void addNewItem() {
        String obj = mBinding.AddItemText.getText().toString();
        if (obj.trim().length() != 0) {
            Checklist.ChecklistItem find = mList.find(obj, false);
            if (find == null || !Settings.getBool(Settings.warnAboutDuplicates))
                addItem(obj);
            else
                promptSimilarItem(obj, find.getText());
        }
    }

    private void addItem(String str) {
        mList.add(str);
        mBinding.AddItemText.setText("");
        if (Settings.getBool(Settings.addNewItemsAtTopOfList)) {
            mBinding.CheckList.smoothScrollToPosition(0);
        } else {
            mBinding.CheckList.smoothScrollToPosition(mList.size());
        }
    }

    private void promptSimilarItem(final String str, String str2) {
        androidx.appcompat.app.AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.similar_item_already_in_list);
        builder.setMessage(getString(R.string.similar_item_x_already_in_list, str2, str));
        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialogInterface, int i) {
                addItem(str);
            }
        });
        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }

    private void showRenameListDialog() {
        AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle(R.string.action_rename_list);
        builder.setMessage(R.string.enter_new_name_of_list);
        final EditText editText = new EditText(this);
        editText.setText(mList.mListName);
        builder.setView(editText);
        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialogInterface, int i) {
                mList.mListName = editText.getText().toString();
                mChecklists.save();
                Objects.requireNonNull(getSupportActionBar()).setTitle(mList.mListName);
            }
        });
        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent motionEvent) {
        Checklist.ChecklistItem movingItem = mList.mMovingItem;
        if (movingItem == null)
            return super.dispatchTouchEvent(motionEvent);

        // get screen position of the ListView and the Activity
        int[] iArr = new int[2];
        mBinding.CheckList.getLocationOnScreen(iArr);
        int listViewTop = iArr[1];
        mBinding.ActivityChecklist.getLocationOnScreen(iArr);
        int activityTop = iArr[1];

        // Convert touch location to relative to the ListView
        int y = ((int) motionEvent.getY()) - listViewTop;

        // Get the index of the item being moved in the items list
        int itemIndex = mList.indexOf(movingItem);
        // Get the index of the moving view in the list of views (should be last?)
        int viewIndex = mBinding.CheckList.getChildCount() - 1;
        while (viewIndex >= 0) {
            if (((ChecklistItemView) mBinding.CheckList.getChildAt(viewIndex)).getItem() == movingItem)
                break;
            viewIndex--;
        }
        if (viewIndex < 0)
            throw new RuntimeException("Can't find view for item: " + movingItem.getText());

        int prevBottom = Integer.MIN_VALUE;
        if (viewIndex > 0) // Not first view
            prevBottom = mBinding.CheckList.getChildAt(viewIndex - 1).getBottom();

        int nextTop = Integer.MAX_VALUE;
        if (viewIndex < mBinding.CheckList.getChildCount() - 1) // Not last view
            nextTop = mBinding.CheckList.getChildAt(viewIndex + 1).getTop();

        int halfItemHeight = mBinding.CheckList.getChildAt(viewIndex).getHeight() / 2;
        if (y < prevBottom) {
            mList.moveItemToPosition(movingItem, itemIndex - 1);
        } else if (y > nextTop) {
            mList.moveItemToPosition(movingItem, itemIndex + 1);
        }

        if (motionEvent.getAction() == MotionEvent.ACTION_MOVE) {
            if (mMovingView == null) {
                // Drag is starting
                Log.d(TAG, "dispatchTouchEvent add moving view ");
                mMovingView = new ChecklistItemView(movingItem, true, this);
                // addView is not supported in AdapterView, so can't add the movingView there.
                // Instead have to add to the activity and adjust margins accordingly
                mBinding.ActivityChecklist.addView(mMovingView);
            }

            if (y < halfItemHeight)
                mBinding.CheckList.smoothScrollToPosition(itemIndex - 1);
            if (y > mBinding.CheckList.getHeight() - halfItemHeight)
                mBinding.CheckList.smoothScrollToPosition(itemIndex + 1);
            // Layout params for the parent, not for this view
            RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
            // Set the top margin to move the view to the right place relative to the Activity
            lp.setMargins(0, (listViewTop - activityTop) + y - halfItemHeight, 0, 0);
            mMovingView.setLayoutParams(lp);
        }
        if (motionEvent.getAction() == MotionEvent.ACTION_UP || motionEvent.getAction() == MotionEvent.ACTION_CANCEL) {
            Log.d(TAG, "ChecklistActivity Up ");
            mBinding.ActivityChecklist.removeView(mMovingView);
            mList.setMovingItem(null);
            mMovingView = null;
        }
        return true;
    }
}
