/**
 * @copyright C-Dot Consultants 2020 - MIT license
 */
package com.cdot.lists;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;

import androidx.core.view.accessibility.AccessibilityEventCompat;
import androidx.appcompat.app.AlertDialog;

import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.cdot.lists.databinding.ChecklistActivityBinding;

import java.util.Objects;

public class ChecklistActivity extends EntryListActivity {
    private static final String TAG = "ChecklistActivity";

    private static final int REQUEST_EXPORT_LIST = 4;

    private ChecklistActivityBinding mBinding;
    private Checklists mChecklists;
    private boolean mIsBeingEdited;

    /**
     * Construct the checklist name passed in the Intent
     */
    @Override
    protected void onCreate(Bundle bundle) {
        Log.d(TAG, "onCreate");
        super.onCreate(bundle);
        mIsBeingEdited = false;

        mBinding = ChecklistActivityBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());
        setSupportActionBar(mBinding.checklistToolbar);

        EditText editText = mBinding.addItemText;
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
                if (mBinding.addItemText.getText().toString().trim().length() == 0) {
                    mBinding.addItem.setAlpha(0.5f);
                } else {
                    mBinding.addItem.setAlpha(1f);
                }
            }
        });
        editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
        ImageButton imageButton = mBinding.addItem;
        imageButton.setAlpha(0.5f);
        imageButton.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                addNewItem();
                return true;
            }
        });

        // mChecklists is just a context placeholder here
        mChecklists = new Checklists(this, true);

        int idx = getIntent().getIntExtra("index", 0);
        Checklist list = (Checklist)mChecklists.get(idx);

        if (list == null) {
            // Create the list
            String listName = getIntent().getStringExtra("name");
            list = new Checklist(listName, mChecklists, this);
            mChecklists.add(list);
        }

        setList(list);

        Settings.setString(Settings.currentList, list.getName());
        Objects.requireNonNull(getSupportActionBar()).setTitle(list.getName());

        mBinding.checkList.setAdapter(list.mArrayAdapter);

        if (list.size() == 0) {
            enableEditMode(true);
            invalidateOptionsMenu();
        } else
            enableEditMode(false);
    }

    @Override
    protected ListView getListView() {
        return mBinding.checkList;
    }

    @Override
    protected RelativeLayout getListLayout() {
        return mBinding.checklistActivity;
    }

    @Override
    protected EntryListItemView makeMovingView(EntryListItem item) {
        return new ChecklistItemView(item, true, this);
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

    private Checklist getList() {
        return (Checklist)mList;
    }

    // Action menu
    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        Intent intent;
        switch (menuItem.getItemId()) {
            default:
                throw new Error("WTF MENU" + menuItem.getItemId());
            case R.id.action_help:
                Intent hIntent = new Intent(this, HelpActivity.class);
                hIntent.putExtra("page", "Checklist");
                startActivity(hIntent);
                return true;
            case R.id.action_check_all:
                getList().checkAll();
                return true;
            case R.id.action_share:
                shareWithComponent();
                return true;
            case R.id.action_delete_checked:
                Toast.makeText(this, getString(R.string.x_items_deleted, getList().deleteAllChecked()), Toast.LENGTH_SHORT).show();
                if (mList.size() == 0) {
                    enableEditMode(true);
                    getList().notifyListChanged();
                    invalidateOptionsMenu();
                }
                return true;
            case R.id.action_edit:
                enableEditMode(!mIsBeingEdited);
                return true;
            case R.id.action_rename_list:
                showRenameListDialog();
                return true;
            case R.id.action_uncheck_all:
                getList().uncheckAll();
                return true;
            case R.id.action_undo_delete:
                int undone = getList().undoRemove();
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
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem menuItem = menu.findItem(R.id.action_edit);
        if (mIsBeingEdited)
            menuItem.setIcon(R.drawable.ic_action_no_edit);
        else
            menuItem.setIcon(R.drawable.ic_action_edit);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
    }

    @Override
    public void onAttachedToWindow() {
        if (Settings.getBool(Settings.alwaysShow)) {
            getWindow().addFlags(AccessibilityEventCompat.TYPE_GESTURE_DETECTION_END);
        }
    }

    /**
     * Turn editing "mode" on/off
     * @param isOn new state
     */
    private void enableEditMode(boolean isOn) {
        mIsBeingEdited = isOn;
        mBinding.newItemContainer.setVisibility(isOn ? View.VISIBLE : View.INVISIBLE);

        // Show/hide soft keyboard
        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (isOn) {
            mBinding.addItemText.setFocusable(true);
            mBinding.addItemText.setFocusableInTouchMode(true);
            mBinding.addItemText.requestFocus();
            inputMethodManager.showSoftInput(mBinding.addItemText, InputMethodManager.SHOW_IMPLICIT);
        } else {
            View currentFocus = getCurrentFocus();
            if (currentFocus == null)
                currentFocus = mBinding.addItemText;
            inputMethodManager.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
        }
        invalidateOptionsMenu();
    }

    // Invoked from checklist_activity.xml
    public void onClickAddItem(View view) {
        addNewItem();
    }

    private void addNewItem() {
        String obj = mBinding.addItemText.getText().toString();
        if (obj.trim().length() != 0) {
            int find = mList.find(obj, false);
            if (find < 0 || !Settings.getBool(Settings.warnAboutDuplicates))
                addItem(obj);
            else
                promptSimilarItem(obj, mList.get(find).getText());
        }
    }

    private void addItem(String str) {
        int index = mList.add(new ChecklistItem(getList(), str, false));
        mBinding.addItemText.setText("");
        mBinding.checkList.smoothScrollToPosition(index);
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
        editText.setText(mList.getName());
        builder.setView(editText);
        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialogInterface, int i) {
                mList.setName(editText.getText().toString());
                mChecklists.save();
                Objects.requireNonNull(getSupportActionBar()).setTitle(getList().getName());
            }
        });
        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }

    public void shareWithComponent() {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        String n = mList.getName();
        intent.putExtra(Intent.EXTRA_TITLE, n);
        intent.putExtra(Intent.EXTRA_SUBJECT, n);
        intent.putExtra(Intent.EXTRA_TEXT, getList().toPlainString());
        startActivity(Intent.createChooser(intent, n));
    }
}
