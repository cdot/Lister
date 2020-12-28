package com.cdot.lists.model;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.skyscreamer.jsonassert.JSONAssert;

import java.io.StringReader;
import java.io.StringWriter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

// Tests for classes in com.cdot.lists.model
public class ModelTest {

    static final String TMP_FILE = "file:///tmp/checkist.json";
    boolean called = false;

    @Test
    public void ChecklistItem() throws Exception {
        ChecklistItem ci = new ChecklistItem();
        ci.setText("Item");
        assertTrue(ci.equals(ci));
        for (String fl : ci.getFlagNames()) {
            assertEquals(ChecklistItem.isDone, fl);
        }
        ci.setFlag(ChecklistItem.isDone);
        assertTrue(ci.getFlag(ChecklistItem.isDone));
        ci.clearFlag(ChecklistItem.isDone);
        assertFalse(ci.getFlag(ChecklistItem.isDone));
        assertTrue(ci.isMoveable());
        JSONObject job = new JSONObject()
                .put("name", "A")
                .put("done", true);
        ci.fromJSON(job);
        assertEquals("A", ci.getText());
        assertTrue(ci.getFlag(ChecklistItem.isDone));
        assertFalse(ci.isMoveable());
        EntryListItem.ChangeListener ear = item -> {
            assertSame(item, ci);
            called = true;
        };
        ci.addChangeListener(ear);
        called = false;
        ci.notifyChangeListeners();
        assertTrue(called);
        called = false;
        ci.removeChangeListener(ear);
        ci.notifyChangeListeners();
        assertFalse(called);
        job = ci.toJSON();
        assertEquals("A", job.getString("name"));
        assertTrue(job.getBoolean("done"));

        ci.fromCSV(new CSVReader(new StringReader("List,Item,T")));
        assertEquals("Item", ci.getText());
        assertTrue(ci.getFlag(ChecklistItem.isDone));

        assertEquals("Item *", ci.toPlainString(""));
    }

    @Test
    public void Checklist() throws Exception {
        Checklist cli = new Checklist("A");
        String seen = "movend;autodel;warndup;sort;";
        for (String fl : cli.getFlagNames()) {
            //assertEquals(Checklist.moveCheckedItemsToEnd, fl);
            // autodel sort warndup
            seen = seen.replace(fl + ";", "");
        }
        assertEquals("", seen);
        assertEquals(0, cli.getCheckedCount());
        assertTrue(cli.itemsAreMoveable());
        assertTrue(cli.isMoveable());
        cli.setFlag(Checklist.moveCheckedItemsToEnd);
        assertFalse(cli.itemsAreMoveable());
        assertTrue(cli.isMoveable());
        assertEquals(-1, cli.indexOf(new ChecklistItem()));
        JSONObject job = new JSONObject("{\"name\":\"Love\",\"items\":[{\"name\":\"Rose thorns\"},{\"name\":\"Peppermint\"}]}");
        cli.fromJSON(job);
        assertEquals(2, cli.size());
        assertEquals("Love", cli.getText());
        assertEquals(1, cli.indexOf(cli.getData().get(1)));
        cli.checkAll(true);
        assertTrue(cli.getData().get(0).getFlag(ChecklistItem.isDone));
        assertTrue(cli.getData().get(1).getFlag(ChecklistItem.isDone));
        cli.checkAll(false);
        assertFalse(cli.getData().get(0).getFlag(ChecklistItem.isDone));
        assertFalse(cli.getData().get(1).getFlag(ChecklistItem.isDone));
        cli.getData().get(1).setFlag(ChecklistItem.isDone);
        cli.deleteAllChecked();
        assertEquals(1, cli.size());
        job = cli.toJSON();
        assertEquals("Love", job.getString("name"));
        JSONArray ja = job.getJSONArray("items");
        assertEquals(1, ja.length());

        cli.clear();
        cli.fromCSV(new CSVReader(new StringReader("Love,Row1,T\nLove,Row2,false")));
        assertEquals("Love", cli.getText());
        assertEquals(2, cli.size());
        assertEquals("Row1", cli.getData().get(0).getText());
        assertTrue(cli.getData().get(0).getFlag(ChecklistItem.isDone));
        assertEquals("Row2", cli.getData().get(1).getText());
        assertFalse(cli.getData().get(1).getFlag(ChecklistItem.isDone));
    }

    @Test
    public void Checklists() throws Exception {
        Checklists clis = new Checklists();
        JSONObject job = new JSONObject("{\"items\":[{\"name\":\"Love\",\"items\":[{\"name\":\"Rose thorns\"},{\"name\":\"Peppermint\"},{\"name\":\"Ashwinder eggs\", \"done\": true},{\"name\":\"Powdered Moonstone\"},{\"name\":\"Pearl Dust\", \"done\": true},{\"name\":\"Rose Petals\", \"done\": true}]},{\"name\": \"Boil Cure\",\"items\":[{\"name\":\"Dried nettles\"},{\"name\":\"6 snake fangs\"},{\"name\":\"4 horned slugs\", \"done\": true},{\"name\":\"2 porcupine quills\"},{\"name\":\"Pungous Onions\"},{\"name\":\"Flobberworm Mucus\", \"done\": true},{\"name\":\"Ginger root\"},{\"name\":\"Shrake spines\"}]},{\"name\":\"Wiggenweld\",\"items\":[{\"name\":\"Wiggentree bark\"},{\"name\":\"Moly\"},{\"name\":\"Dittany\", \"done\": true},{\"name\":\"One pint of Horklump juice\"},{\"name\":\"2 drops of Flobberworm Mucus\"},{\"name\":\"7 Chizpurfle fangs\", \"done\": true},{\"name\":\"Billywig sting slime\"},{\"name\":\"A sprig of mint\", \"done\": true},{\"name\":\"Boom Berry juice\"},{\"name\":\"One stewed Mandrake\"},{\"name\":\"Drops of Honeywater\"},{\"name\":\"Sloth brain Mucus\"},{\"name\":\"Moondew drops\"},{\"name\":\"Salamander blood\"},{\"name\":\"10 Lionfish spines\", \"done\": true},{\"name\":\"Unicorn horn\"},{\"name\":\"Wolfsbane\", \"done\": true}]},{\"name\":\"Fire Breathing\",\"items\":[{\"name\":\"Mint\"},{\"name\":\"Valerian Sprigs\"},{\"name\":\"Fire Seeds\"},{\"name\":\"Powdered dragon horn\"},{\"name\":\"Lavender\"}]}]}");
        clis.fromJSON(job);
        clis.setURI(TMP_FILE);
        StringWriter csv = new StringWriter();
        clis.toCSV(new CSVWriter(csv));
        String csvs = csv.toString();
        Checklists clis2 = new Checklists();
        clis2.fromCSV(new CSVReader(new StringReader(csvs)));
        clis2.setURI(TMP_FILE);
        JSONAssert.assertEquals(job, clis2.toJSON(), false);

        assertEquals(TMP_FILE, clis.getURI());
        assertEquals(TMP_FILE, clis2.getURI());
        assertNotEquals("", clis.toPlainString(""));

        clis2.notifyChangeListeners();
        assertTrue(clis2.isMoreRecentVersionOf(clis));

        EntryListItem.ChangeListener ear = item -> {
            assertSame(item, clis);
            called = true;
        };
        clis.addChangeListener(ear);
        called = false;
        clis.notifyChangeListeners();
        assertTrue(called);
        called = false;

        EntryListItem cli = clis.getData().get(0);
        cli.notifyChangeListeners();
        assertTrue(called);

        EntryListItem ci = ((Checklist)cli).getData().get(0);
        called = false;
        ci.notifyChangeListeners();
        assertTrue(called);

        clis.removeChangeListener(ear);
        called = false;
        clis.notifyChangeListeners();
        assertFalse(called);

        // TODO: test copy constructor
    }
}
