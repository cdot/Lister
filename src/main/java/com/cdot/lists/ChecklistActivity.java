package com.cdot.lists;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import androidx.core.view.accessibility.AccessibilityEventCompat;
import androidx.customview.widget.ExploreByTouchHelper;
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
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.cdot.lists.databinding.ChecklistActivityBinding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Objects;

public class ChecklistActivity extends AppCompatActivity implements TextView.OnEditorActionListener, TextWatcher, View.OnLongClickListener {
    private static final String TAG = "ChecklistActivity";

    private static final int REQUEST_SAVE_AS = 3;

    private Checklist mList;
    private ListView mView;
    private ChecklistItemView mMovingView;

    @Override
    public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {
    }

    @Override
    public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {
    }

    private ChecklistActivityBinding mBinding;

    /**
     * Construct the checklist name passed in the Intent
     */
    @Override
    protected void onCreate(Bundle bundle) {
        Log.d(TAG, "onCreate");
        super.onCreate(bundle);
        mBinding = ChecklistActivityBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());

        EditText editText = mBinding.AddItemText;
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        editText.setOnEditorActionListener(this);
        editText.addTextChangedListener(this);
        editText.setImeOptions(6);
        ImageButton imageButton = mBinding.AddItemButton;
        imageButton.setAlpha(0.5f);
        imageButton.setOnLongClickListener(this);

        String listName = getIntent().getStringExtra("list");
        mList = new Checklist(listName, this);
        Settings.setString(Settings.currentList, listName);
        Objects.requireNonNull(getSupportActionBar()).setTitle(mList.getListName());

        mView = mBinding.CheckList;
        mView.setAdapter(mList.mArrayAdapter);

        if (mList.size() == 0) {
            showSoftKeyboard(true);
            invalidateOptionsMenu();
        }

        if (Settings.getBool(Settings.darkBackground)) {
            getWindow().getDecorView().setBackgroundColor(Color.BLACK);
            editText.setTextColor(-1);
        }
        setEditMode(false);//mList.isEditMode());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.checklist, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.action_check).setVisible(false);
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
                Log.d(TAG,"WTF MENU" + menuItem.getItemId());
                return super.onOptionsItemSelected(menuItem);
            case R.id.action_delete_checked:
                mList.deleteAllChecked();
                if (mList.size() == 0) {
                    mList.setEditMode(true);
                    setEditMode(mList.isEditMode());
                    invalidateOptionsMenu();
                }
                return true;
            case R.id.action_edit:
                Checklist checkList = mList;
                checkList.setEditMode(!checkList.isEditMode());
                setEditMode(mList.isEditMode());
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
                mList.undoRemove();
                return true;
            case R.id.action_save_list_as:
                intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/json");
                startActivityForResult(intent, REQUEST_SAVE_AS);
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

    private void setEditMode(boolean z) {
        Log.d(TAG, "setEditMode " + z);
        mBinding.NewItemContainer.setVisibility(z ? View.VISIBLE : View.INVISIBLE);
        showSoftKeyboard(z);
    }

    private void showSoftKeyboard(boolean z) {
        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (z) {
            mBinding.AddItemText.setFocusable(true);
            mBinding.AddItemText.setFocusableInTouchMode(true);
            mBinding.AddItemText.requestFocus();
            inputMethodManager.showSoftInput(mBinding.AddItemText, 1);
            return;
        }
        View currentFocus = getCurrentFocus();
        if (currentFocus != null) {
            inputMethodManager.hideSoftInputFromWindow(currentFocus.getWindowToken(), 2);
        }
    }

    @Override
    public boolean onLongClick(View view) {
        addNewItem(false);
        return true;
    }

    // Invoked from checklist_activity.xml
    public void onClickAddItem(View view) {
        addNewItem(true);
    }

    private void addNewItem(boolean z) {
        String obj = mBinding.AddItemText.getText().toString();
        if (obj.trim().length() != 0) {
            String find = mList.find(obj, false);
            if (find == null || !Settings.getBool(Settings.warnAboutDuplicates)) {
                addItem(obj);
                if (z) {
                    new Checklist(Settings.DICTIONARY_LISTNAME, this).add(obj);
                    return;
                }
                return;
            }
            promptSimilarItem(obj, find);
        }
    }

    private void addItem(String str) {
        mList.add(str);
        mBinding.AddItemText.setText("");
        mBinding.SuggestionsContainer.removeAllViews();
        if (Settings.getBool(Settings.addNewItemsAtTopOfList)) {
            mView.smoothScrollToPosition(0);
        } else {
            mView.smoothScrollToPosition(mList.size());
        }
    }

    private void promptSimilarItem(final String str, String str2) {
        androidx.appcompat.app.AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.similar_item_already_in_list);
        builder.setMessage(getString(R.string.similar_item_x_already_in_list, str2, str));
        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialogInterface, int i) {
                addItem(str);
                Checklist dict = new Checklist(Settings.DICTIONARY_LISTNAME, ChecklistActivity.this);
                dict.add(str);
                dict.saveToCache();
            }
        });
        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }

    private void addSuggestions(String str) {
        Log.d(TAG, "addSuggestions " + str);
        Checklist dict = new Checklist(Settings.DICTIONARY_LISTNAME, ChecklistActivity.this);
        if (dict.size() > 0) {
            mBinding.SuggestionsContainer.removeAllViews();
            if (str.length() != 0) {
                ArrayList<String> arrayList = new ArrayList<>();
                for (Checklist.ChecklistItem next : dict) {
                    Log.d(TAG, "item " + next.getText());
                    if (next.getText().toLowerCase().indexOf(str.toLowerCase()) == 0 && mList.find(next.getText(), true) == null) {
                        arrayList.add(next.getText());
                        if (arrayList.size() == 5) {
                            break;
                        }
                    }
                }
                Collections.sort(arrayList);
                for (String s : arrayList) {
                    addSuggestion(s);
                }
            }
        }
    }

    private void addSuggestion(CharSequence charSequence) {
        TextView textView = new TextView(this);
        textView.setText(charSequence);
        textView.setGravity(17);
        textView.setPadding(0, 10, 0, 10);
        textView.setBackgroundColor(-1);
        textView.setMinimumWidth(300);
        textView.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                addItem(((TextView) view).getText().toString());
            }
        });
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-2, -2);
        layoutParams.topMargin = 3;
        mBinding.SuggestionsContainer.addView(textView, layoutParams);
    }

    private void showRenameListDialog() {
        AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle(R.string.action_rename_list);
        builder.setMessage(R.string.enter_new_name_of_list);
        final EditText editText = new EditText(this);
        editText.setText(mList.getListName());
        builder.setView(editText);
        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialogInterface, int i) {
                mList.setListName(editText.getText().toString());
                Objects.requireNonNull(getSupportActionBar()).setTitle(mList.getListName());
            }
        });
        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }

    @Override
    public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
        if (i != 6) {
            return false;
        }
        addNewItem(true);
        return true;
    }

    int getIndexOfMovingItemView(Checklist.ChecklistItem item) {
        for (int i = 0; i < mView.getChildCount(); i++) {
            if (((ChecklistItemView) mView.getChildAt(i)).getItem() == item) {
                return i;
            }
        }
        throw new RuntimeException("Can't find view for item: " + item.getText());
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent motionEvent) {
        int[] iArr = new int[2];
        mView.getLocationOnScreen(iArr);
        int y = ((int) motionEvent.getY()) - iArr[1];
        Checklist.ChecklistItem movingItem = mList.getMovingItem();
        if (movingItem == null) {
            return super.dispatchTouchEvent(motionEvent);
        }
        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        if (mMovingView == null) {
            Log.d(TAG, "dispatchTouchEvent add moving view ");
            ChecklistItemView checkListItemView = new ChecklistItemView(movingItem, true, this);
            mMovingView = checkListItemView;
            checkListItemView.setLayoutParams(lp);
            RelativeLayout layout = mBinding.ActivityChecklist;
            layout.addView(checkListItemView);
        }
        int itemPosition = mList.indexOf(movingItem);
        int indexOfMovingItemView = getIndexOfMovingItemView(movingItem);
        int height = mView.getChildAt(indexOfMovingItemView).getHeight();
        int i = ExploreByTouchHelper.INVALID_ID;
        int i2 = Integer.MAX_VALUE;
        if (indexOfMovingItemView > 0) {
            i = mView.getChildAt(indexOfMovingItemView - 1).getBottom();
        }
        if (indexOfMovingItemView < mView.getChildCount() - 1) {
            i2 = mView.getChildAt(indexOfMovingItemView + 1).getTop();
        }
        int i3 = height / 2;
        int height2 = mView.getHeight() - i3;
        if (y < i) {
            mList.moveItemToPosition(movingItem, itemPosition - 1);
        } else if (y > i2) {
            mList.moveItemToPosition(movingItem, itemPosition + 1);
        }
        if (motionEvent.getAction() == 2) {
            if (y < i3) {
                mView.smoothScrollToPosition(itemPosition - 1);
            }
            if (y > height2) {
                mView.smoothScrollToPosition(itemPosition + 1);
            }
            lp.setMargins(0, y - i3, 0, 0);
            mMovingView.setLayoutParams(lp);
        }
        if (motionEvent.getAction() == 1 || motionEvent.getAction() == 3) {
            Log.d(TAG, "ChecklistActivity Up ");
            RelativeLayout layout = mBinding.ActivityChecklist;
            layout.removeView(mMovingView);
            mList.setMovingItem(null);
            mMovingView = null;
        }
        return true;
    }

    @Override
    public void afterTextChanged(Editable editable) {
        if (mBinding.AddItemText.getText().toString().trim().length() == 0) {
            mBinding.AddItemButton.setAlpha(0.5f);
        } else {
            mBinding.AddItemButton.setAlpha(1f);
        }
        addSuggestions(mBinding.AddItemText.getText().toString());
    }
}
