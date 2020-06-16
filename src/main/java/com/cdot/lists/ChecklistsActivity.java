/**
 * @copyright C-Dot Consultants 2020 - MIT license
 */
package com.cdot.lists;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

/**
 * Activity that displays a list of checklists. The checklists are stored in a paired Checklists
 * object.
 */
public class ChecklistsActivity extends EntryListActivity {
    private static final String TAG = "ChecklistsActivity";

    // Activity request codes
    private static final int REQUEST_IMPORT_LIST = 3;
    private static final int REQUEST_PREFERENCES = 4;

    @Override // EntryListActivity
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        Settings.setContext(this);

        setLayout(R.layout.checklists_activity);

        // onCreate is always followed by onResume, so delay loading the lists
        // until then
        Checklists checklists = new Checklists(this, false);
        setList(checklists);

        if (Settings.getBool(Settings.openLatest)) {
            String currentList = Settings.getString(Settings.currentList);
            if (currentList != null) {
                int idx = checklists.findByText(currentList, true);
                if (idx >= 0) {
                    Log.d(TAG, "launching " + currentList);
                    launchChecklistActivity(idx);
                    return;
                }
            }
            Log.d(TAG, "no list to launch " + currentList);
        }

    }

    @Override // EntryListActivity
    protected EntryListItemView makeMovingView(EntryListItem item) {
        return new ChecklistsItemView(item, true, this);
    }

    @Override // EntryListActivity
    protected String getHelpFile() {
        return "Checklists";
    }

    @Override // AppCompatActivity
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        loadLists();
    }

    @Override // AppCompatActivity
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.checklists, menu);
        return true;
    }

    @Override // AppCompatActivity
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        Intent intent;
        switch (menuItem.getItemId()) {
            default:
                return super.onOptionsItemSelected(menuItem);

            case R.id.action_import_list:
                intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                startActivityForResult(intent, REQUEST_IMPORT_LIST);
                return true;

            case R.id.action_new_list:
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(R.string.create_new_list);
                builder.setMessage(R.string.enter_name_of_new_list);
                final EditText editText = new EditText(this);
                editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
                editText.setSingleLine(true);
                builder.setView(editText);
                builder.setPositiveButton(R.string.ok, (dialogInterface, i) -> {
                    String listname = editText.getText().toString();
                    Checklist newList = new Checklist(listname, mList, ChecklistsActivity.this);
                    mList.add(newList);
                    mList.notifyListChanged(true);
                    launchChecklistActivity(mList.indexOf(newList));
                });
                builder.setNegativeButton(R.string.cancel, null);
                builder.show();
                editText.post(() -> {
                    editText.setFocusable(true);
                    editText.setFocusableInTouchMode(true);
                    editText.requestFocus();
                    ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);
                });
                return true;

            case R.id.action_settings:
                startActivityForResult(new Intent(this, SettingsActivity.class), REQUEST_PREFERENCES);
                return true;
        }
    }

    /**
     * Handle results from activities started with startActivityForResult
     *
     * @param requestCode startActivityForResult
     * @param resultCode  did it work?
     * @param resultData  and what's the data?
     */
    @Override // AppCompatActivity
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        super.onActivityResult(requestCode, resultCode, resultData);
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == REQUEST_IMPORT_LIST && resultData != null) {
                try {
                    Uri uri = resultData.getData();
                    if (uri == null)
                        return;
                    Checklist newList = new Checklist(uri, (Checklists) mList, this);
                    mList.add(newList);
                    mList.notifyListChanged(true);
                    Log.d(TAG, "imported list: " + newList.getText());
                    Toast.makeText(this, getString(R.string.import_report, newList.getText()), Toast.LENGTH_LONG).show();
                    launchChecklistActivity(mList.indexOf(newList));
                } catch (Exception e) {
                    Log.d(TAG, "import failed " + e.getMessage());
                    Toast.makeText(this, R.string.import_failed, Toast.LENGTH_LONG).show();
                }
            } else if (requestCode == REQUEST_PREFERENCES)
                // An OK result from the settings activity means list source has changed
                loadLists();
        }
    }

    /**
     * Launch the checklist reading content from the given private filename
     *
     * @param idx index of Checklist to open
     */
    private void launchChecklistActivity(int idx) {
        Intent intent = new Intent(this, ChecklistActivity.class);
        intent.putExtra("index", idx);
        intent.putExtra("name", mList.get(idx).getText());
        startActivity(intent);
    }

    // Load the list of known lists
    private void loadLists() {
        Log.d(TAG, "Loading lists");
        mList.clear();
        ((Checklists) mList).load(this);
        if (mList.size() == 0)
            Toast.makeText(this, R.string.no_lists, Toast.LENGTH_LONG).show();
    }
}
