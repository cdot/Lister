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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.cdot.lists.databinding.ChecklistsActivityBinding;
import com.cdot.lists.model.Checklist;
import com.cdot.lists.model.EntryList;
import com.cdot.lists.model.EntryListItem;
import com.cdot.lists.preferences.PreferencesActivity;
import com.cdot.lists.view.ChecklistsItemView;
import com.cdot.lists.view.EntryListItemView;

/**
 * Activity that displays a list of checklists. The checklists are stored in the MainActivity.
 */
public class ChecklistsActivity extends EntryListActivity {
    private static final String TAG = ChecklistsActivity.class.getSimpleName();

    private ChecklistsActivityBinding mBinding;

    public ChecklistsActivity() {
    }

    @Override
        // EntryListActivity
    EntryList getList() {
        return getLister().getLists();
    }

    @Override // AppCompatActivity
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        mBinding = ChecklistsActivityBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());
    }

    @Override // EntryListActivity
    protected EntryListItemView makeMovingView(EntryListItem item) {
        return new ChecklistsItemView(item, true, this);
    }

    @Override // EntryListActivity
    protected String getHelpAsset() {
        return "Checklists";
    }

    @Override // EntryListActivity
    public void onListChanged(EntryListItem item) {
        super.onListChanged(item);
        int sz = getList().size();
        mBinding.listsMessage.setVisibility(sz == 0 ? View.VISIBLE : View.GONE);
        mBinding.itemListView.setVisibility(sz == 0 ? View.GONE : View.VISIBLE);
    }

    @Override // EntryListActivity
    public void onListsLoaded() {
        makeAdapter(mBinding.itemListView);
        super.onListsLoaded();
    }

    @Override // AppCompatActivity
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        Log.d(TAG, "onCreateOptionsMenu");
        getMenuInflater().inflate(R.menu.checklists, menu);
        return true;
    }

    @Override // AppCompatActivity
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        Log.d(TAG, "onOptionsItemSelected");
        if (super.onOptionsItemSelected(menuItem))
            return true;

        int it = menuItem.getItemId();

        if (it == R.id.action_import_list) {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            startActivityForResult(intent, Lister.REQUEST_IMPORT_LIST);

        } else if (it == R.id.action_new_list) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.create_new_list);
            builder.setMessage(R.string.enter_name_of_new_list);
            final EditText editText = new EditText(this);
            editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            editText.setSingleLine(true);
            builder.setView(editText);
            builder.setPositiveButton(R.string.ok, (dialogInterface, i) -> {
                String listname = editText.getText().toString();
                EntryList ls = getList();
                Checklist newList = new Checklist(ls, listname);
                ls.add(newList);
                Log.d(TAG, "created list: " + newList.getText());
                ls.notifyChangeListeners();
                getLister().saveLists(this,
                        okdata -> {
                        },
                        code -> Toast.makeText(this, code, Toast.LENGTH_LONG).show());
                Intent intent = new Intent(this, ChecklistActivity.class);
                intent.putExtra(UID_EXTRA, newList.getSessionUID());
                startActivity(intent);
            });
            builder.setNegativeButton(R.string.cancel, null);
            builder.show();
            editText.post(() -> {
                editText.setFocusable(true);
                editText.setFocusableInTouchMode(true);
                editText.requestFocus();
                ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);
            });

        } else if (it == R.id.action_settings) {
            Intent sint = new Intent(this, PreferencesActivity.class);
            startActivityForResult(sint, REQUEST_PREFERENCES);
        } else
            return super.onOptionsItemSelected(menuItem);

        return true;
    }

    @Override // AppCompatActivity
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        super.onActivityResult(requestCode, resultCode, resultData);
        Log.d(TAG, "onActivityResult");
        if (resultCode != Activity.RESULT_OK || resultData == null)
            return;

        if (requestCode == Lister.REQUEST_IMPORT_LIST) {
            getLister().importList(resultData.getData(), this,
                    data -> {
                        Checklist newList = (Checklist) data;
                        invalidateOptionsMenu(); // update menu items
                        Toast.makeText(this, getString(R.string.import_report, newList.getText()), Toast.LENGTH_LONG).show();
                        Intent intent = new Intent(this, ChecklistActivity.class);
                        intent.putExtra(UID_EXTRA, newList.getSessionUID());
                        startActivity(intent);
                    },
                    resource -> Toast.makeText(this, resource, Toast.LENGTH_LONG).show());
        } else if (requestCode == ListerActivity.REQUEST_CHANGE_STORE || requestCode == ListerActivity.REQUEST_CREATE_STORE)
            getLister().handleChangeStore(resultData, this);
    }
}
