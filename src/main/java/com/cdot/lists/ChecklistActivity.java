/**
 * @copyright C-Dot Consultants 2020 - MIT license
 */
package com.cdot.lists;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.view.accessibility.AccessibilityEventCompat;

import java.util.Objects;

public class ChecklistActivity extends EntryListActivity {
    private static final String TAG = "ChecklistActivity";

    private static final int REQUEST_EXPORT_LIST = 4;

    private Checklists mChecklists;
    private boolean mIsBeingEdited;

    private EditText mAddItemText;

    /**
     * Construct the checklist (index passed in the Intent)
     */
    @Override // AppCompatActivity
    protected void onCreate(Bundle bundle) {
        Log.d(TAG, "onCreate");
        super.onCreate(bundle);

        setLayout(R.layout.checklist_activity);

        mIsBeingEdited = false;

        mAddItemText = findViewById(R.id.add_item_text);
        mAddItemText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        mAddItemText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                if (i == EditorInfo.IME_ACTION_DONE) {
                    addNewItem();
                    return true;
                }
                return false;
            }
        });

        mAddItemText.setImeOptions(EditorInfo.IME_ACTION_DONE);

        // mChecklists is just a context placeholder here
        mChecklists = new Checklists(this, true);

        int idx = getIntent().getIntExtra("index", 0);
        Checklist list = (Checklist) mChecklists.get(idx);

        if (list == null) {
            // Create the list
            String listName = getIntent().getStringExtra("name");
            list = new Checklist(listName, mChecklists, this);
            mChecklists.add(list);
        }

        setList(list);

        Settings.setString(Settings.currentList, list.getText());
        Objects.requireNonNull(getSupportActionBar()).setTitle(list.getText());

        enableEditMode(list.size() == 0);
    }

    @Override // EntryListActivity
    protected String getHelpFile() {
        return "Checklist";
    }

    @Override // EntryListActivity
    protected EntryListItemView makeMovingView(EntryListItem item) {
        return new ChecklistItemView(item, true, this);
    }

    @Override // AppCompatActivity
    protected void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        super.onActivityResult(requestCode, resultCode, resultData);
        if (requestCode == REQUEST_EXPORT_LIST && resultCode == Activity.RESULT_OK && resultData != null)
            mList.saveToUri(resultData.getData());
    }

    @Override // AppCompatActivity
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.checklist, menu);
        return true;
    }

    private Checklist getList() {
        return (Checklist) mList;
    }

    @Override // EntryListActivity
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        Intent intent;
        switch (menuItem.getItemId()) {
            default:
                return super.onOptionsItemSelected(menuItem);
            case R.id.action_check_all:
                getList().checkAll(true);
                return true;
            case R.id.action_share:
                shareWithComponent();
                return true;
            case R.id.action_delete_checked:
                Toast.makeText(this, getString(R.string.x_items_deleted, getList().deleteAllChecked()), Toast.LENGTH_SHORT).show();
                if (mList.size() == 0) {
                    enableEditMode(true);
                    getList().notifyListChanged(true);
                    invalidateOptionsMenu();
                }
                return true;
            case R.id.action_edit:
                enableEditMode(!mIsBeingEdited);
                return true;
            case R.id.action_rename_list:
                AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
                builder.setTitle(R.string.rename_list);
                builder.setMessage(R.string.enter_new_name_of_list);
                final EditText editText = new EditText(this);
                editText.setSingleLine(true);
                editText.setText(mList.getText());
                builder.setView(editText);
                builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialogInterface, int i) {
                        mList.setText(editText.getText().toString());
                        mChecklists.notifyListChanged(true);
                        Objects.requireNonNull(getSupportActionBar()).setTitle(getList().getText());
                    }
                });
                builder.setNegativeButton(R.string.cancel, null);
                builder.show();
                return true;
            case R.id.action_uncheck_all:
                getList().checkAll(false);
                return true;
            case R.id.action_undo_delete:
                int undone = getList().undoRemove();
                getList().notifyListChanged(true);
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

    @Override // EntryListActivity
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem menuItem = menu.findItem(R.id.action_edit);
        if (mIsBeingEdited) {
            menuItem.setIcon(R.drawable.ic_action_item_add_off);
            menuItem.setTitle(R.string.action_item_add_off);
        } else {
            menuItem.setIcon(R.drawable.ic_action_item_add_on);
            menuItem.setTitle(R.string.action_item_add_on);
        }
        menuItem = menu.findItem(R.id.action_undo_delete);
        menuItem.setEnabled(((Checklist) mList).mRemoves.size() > 0);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override // AppCompatActivity
    public void onAttachedToWindow() {
        if (Settings.getBool(Settings.alwaysShow)) {
            getWindow().addFlags(AccessibilityEventCompat.TYPE_GESTURE_DETECTION_END);
        }
    }

    /**
     * Turn editing "mode" on/off
     *
     * @param isOn new state
     */
    private void enableEditMode(boolean isOn) {
        mIsBeingEdited = isOn;
        View nic = findViewById(R.id.new_item_container);
        nic.setVisibility(isOn ? View.VISIBLE : View.INVISIBLE);

        // Show/hide soft keyboard
        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (isOn) {
            mAddItemText.setFocusable(true);
            mAddItemText.setFocusableInTouchMode(true);
            mAddItemText.requestFocus();
            inputMethodManager.showSoftInput(mAddItemText, InputMethodManager.SHOW_IMPLICIT);
        } else {
            View currentFocus = getCurrentFocus();
            if (currentFocus == null)
                currentFocus = mAddItemText;
            inputMethodManager.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
        }
        invalidateOptionsMenu();
    }

    private void addNewItem() {
        String obj = mAddItemText.getText().toString();
        if (obj.trim().length() != 0) {
            int find = mList.find(obj, false);
            if (find < 0 || !Settings.getBool(Settings.warnAboutDuplicates))
                addItem(obj);
            else
                promptSimilarItem(obj, mList.get(find).getText());
        }
    }

    private void addItem(String str) {
        ChecklistItem item = new ChecklistItem(getList(), str, false);
        mList.add(item);
        mList.notifyListChanged(true);
        mAddItemText.setText("");
        ListView lv = findViewById(R.id.entry_list_activity_list_view);
        lv.smoothScrollToPosition(mList.sortedIndexOf(item));
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

    public void shareWithComponent() {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        String n = mList.getText();
        intent.putExtra(Intent.EXTRA_TITLE, n);
        intent.putExtra(Intent.EXTRA_SUBJECT, n);
        intent.putExtra(Intent.EXTRA_TEXT, getList().toPlainString());
        startActivity(Intent.createChooser(intent, n));
    }
}
