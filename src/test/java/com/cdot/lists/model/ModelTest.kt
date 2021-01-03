package com.cdot.lists.model

import com.opencsv.CSVReader
import com.opencsv.CSVWriter
import org.json.JSONObject
import org.junit.Assert
import org.junit.Test
import org.skyscreamer.jsonassert.JSONAssert
import java.io.StringReader
import java.io.StringWriter

// Tests for classes in com.cdot.lists.model
class ModelTest {
    var called = false

    @Test
    @Throws(Exception::class)
    fun testChecklistItem() {
        val ci = ChecklistItem("Item")
        Assert.assertTrue(ci.sameAs(ci))
        for (fl in ci.flagNames) {
            Assert.assertEquals(ChecklistItem.IS_DONE, fl)
        }
        ci.setFlag(ChecklistItem.IS_DONE)
        Assert.assertTrue(ci.getFlag(ChecklistItem.IS_DONE))
        ci.clearFlag(ChecklistItem.IS_DONE)
        Assert.assertFalse(ci.getFlag(ChecklistItem.IS_DONE))
        var job = JSONObject()
                .put("name", "A")
                .put("done", true)
        ci.fromJSON(job)
        Assert.assertEquals("A", ci.text)
        Assert.assertTrue(ci.getFlag(ChecklistItem.IS_DONE))
        val ear: EntryListItem.ChangeListener = object : EntryListItem.ChangeListener {
            override fun onListChanged(item: EntryListItem) {
                Assert.assertSame(item, ci)
                called = true
            }
        }
        ci.addChangeListener(ear)
        called = false
        ci.notifyChangeListeners()
        Assert.assertTrue(called)
        called = false
        ci.removeChangeListener(ear)
        ci.notifyChangeListeners()
        Assert.assertFalse(called)
        job = ci.toJSON()
        Assert.assertEquals("A", job.getString("name"))
        Assert.assertTrue(job.getBoolean("done"))
        ci.fromCSV(CSVReader(StringReader("List,Item,T")))
        Assert.assertEquals("Item", ci.text)
        Assert.assertTrue(ci.getFlag(ChecklistItem.IS_DONE))
        Assert.assertEquals("Item *", ci.toPlainString(""))
    }

    @Test
    @Throws(Exception::class)
    fun testChecklist() {
        val cli = Checklist("A")
        var seen = "movend;autodel;warndup;sort;"
        for (fl in cli.flagNames) {
            //assertEquals(Checklist.moveCheckedItemsToEnd, fl);
            // autodel sort warndup
            seen = seen.replace("$fl;", "")
        }
        Assert.assertEquals("", seen)
        Assert.assertEquals(0, cli.countFlaggedEntries(ChecklistItem.IS_DONE))
        Assert.assertTrue(cli.childrenAreMoveable)
        cli.setFlag(Checklist.CHECKED_AT_END)
        Assert.assertFalse(cli.childrenAreMoveable)
        Assert.assertEquals(-1, cli.indexOf(ChecklistItem()).toLong())
        var job = JSONObject("{\"name\":\"Love\",\"items\":[{\"name\":\"Rose thorns\"},{\"name\":\"Peppermint\"}]}")
        cli.fromJSON(job)
        Assert.assertEquals(2, cli.size().toLong())
        Assert.assertEquals("Love", cli.text)
        Assert.assertEquals(1, cli.indexOf(cli.children[1]).toLong())
        cli.setFlagOnAll(ChecklistItem.IS_DONE, true)
        Assert.assertTrue(cli.children[0].getFlag(ChecklistItem.IS_DONE))
        Assert.assertTrue(cli.children[1].getFlag(ChecklistItem.IS_DONE))
        cli.setFlagOnAll(ChecklistItem.IS_DONE, false)
        Assert.assertFalse(cli.children[0].getFlag(ChecklistItem.IS_DONE))
        Assert.assertFalse(cli.children[1].getFlag(ChecklistItem.IS_DONE))
        cli.children[1].setFlag(ChecklistItem.IS_DONE)
        cli.deleteAllFlagged(ChecklistItem.IS_DONE)
        Assert.assertEquals(1, cli.size().toLong())
        job = cli.toJSON()
        Assert.assertEquals("Love", job.getString("name"))
        val ja = job.getJSONArray("items")
        Assert.assertEquals(1, ja.length().toLong())
        val cli2 = Checklist("Love")
        cli2.fromCSV(CSVReader(StringReader("Love,Row1,T\nLove,Row2,false")))
        Assert.assertEquals("Love", cli2.text)
        Assert.assertEquals(2, cli2.size().toLong())
        Assert.assertEquals("Row1", cli2.children[0].text)
        Assert.assertTrue(cli2.children[0].getFlag(ChecklistItem.IS_DONE))
        Assert.assertEquals("Row2", cli2.children[1].text)
        Assert.assertFalse(cli2.children[1].getFlag(ChecklistItem.IS_DONE))
    }

    @Test
    @Throws(Exception::class)
    fun testChecklists() {
        val clis = Checklists()
        val job = JSONObject("{\"items\":[{\"name\":\"Love\",\"items\":[{\"name\":\"Rose thorns\"},{\"name\":\"Peppermint\"},{\"name\":\"Ashwinder eggs\", \"done\": true},{\"name\":\"Powdered Moonstone\"},{\"name\":\"Pearl Dust\", \"done\": true},{\"name\":\"Rose Petals\", \"done\": true}]},{\"name\": \"Boil Cure\",\"items\":[{\"name\":\"Dried nettles\"},{\"name\":\"6 snake fangs\"},{\"name\":\"4 horned slugs\", \"done\": true},{\"name\":\"2 porcupine quills\"},{\"name\":\"Pungous Onions\"},{\"name\":\"Flobberworm Mucus\", \"done\": true},{\"name\":\"Ginger root\"},{\"name\":\"Shrake spines\"}]},{\"name\":\"Wiggenweld\",\"items\":[{\"name\":\"Wiggentree bark\"},{\"name\":\"Moly\"},{\"name\":\"Dittany\", \"done\": true},{\"name\":\"One pint of Horklump juice\"},{\"name\":\"2 drops of Flobberworm Mucus\"},{\"name\":\"7 Chizpurfle fangs\", \"done\": true},{\"name\":\"Billywig sting slime\"},{\"name\":\"A sprig of mint\", \"done\": true},{\"name\":\"Boom Berry juice\"},{\"name\":\"One stewed Mandrake\"},{\"name\":\"Drops of Honeywater\"},{\"name\":\"Sloth brain Mucus\"},{\"name\":\"Moondew drops\"},{\"name\":\"Salamander blood\"},{\"name\":\"10 Lionfish spines\", \"done\": true},{\"name\":\"Unicorn horn\"},{\"name\":\"Wolfsbane\", \"done\": true}]},{\"name\":\"Fire Breathing\",\"items\":[{\"name\":\"Mint\"},{\"name\":\"Valerian Sprigs\"},{\"name\":\"Fire Seeds\"},{\"name\":\"Powdered dragon horn\"},{\"name\":\"Lavender\"}]}]}")
        clis.fromJSON(job)
        clis.forUri = TMP_FILE
        val csv = StringWriter()
        clis.toCSV(CSVWriter(csv))
        val csvs = csv.toString()
        val clis2 = Checklists()
        clis2.fromCSV(CSVReader(StringReader(csvs)))
        clis2.forUri = TMP_FILE
        JSONAssert.assertEquals(job, clis2.toJSON(), false)
        Assert.assertEquals(TMP_FILE, clis.forUri)
        Assert.assertEquals(TMP_FILE, clis2.forUri)
        Assert.assertNotEquals("", clis.toPlainString(""))
        clis2.notifyChangeListeners()
        Assert.assertTrue(clis2.isMoreRecentVersionOf(clis))
        val ear: EntryListItem.ChangeListener = object : EntryListItem.ChangeListener {
            override fun onListChanged(item: EntryListItem) {
                Assert.assertSame(item, clis)
                called = true
            }
        }
        clis.addChangeListener(ear)
        called = false
        clis.notifyChangeListeners()
        Assert.assertTrue(called)
        called = false
        val cli: EntryListItem = clis.children.get(0)
        cli.notifyChangeListeners()
        Assert.assertTrue(called)
        val ci = (cli as Checklist).children[0]
        called = false
        ci.notifyChangeListeners()
        Assert.assertTrue(called)
        clis.removeChangeListener(ear)
        called = false
        clis.notifyChangeListeners()
        Assert.assertFalse(called)
    }

    companion object {
        const val TMP_FILE = "file:///tmp/checkist.json"
    }
}