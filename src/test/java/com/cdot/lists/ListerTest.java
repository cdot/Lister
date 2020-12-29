package com.cdot.lists;

import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import com.cdot.lists.model.Checklist;
import com.cdot.lists.model.ChecklistItem;
import com.cdot.lists.model.Checklists;
import com.cdot.lists.model.EntryListItem;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests for com.cdot.lists.Lister
 */
@RunWith(RobolectricTestRunner.class)
public class ListerTest {

    private static final String TEST_FILE = "file:///tmp/test";
    private static final String TEST_URI = "content://test.json";
    private static final String BAD_URI = "bad_content_provider://test.json";
    
    // When debugging, set this to true to prevent waiter.wait timing out
    private static boolean ALWAYS_WAIT = true;

    class Waiter {
        private int mStep;
        private String mTest;

        Waiter(String t) {
            mTest = t;
            mStep = 0;
        }

        public Uri getFileUri() {
            return Uri.parse("file:///tmp/" + mTest + ".json");
        }

        public void waitForStep(final int waitStep) {
            int tickityBoo = 30; // 3 second timeout waiting for a step
            // Timeout after 10s
            while (mStep != waitStep) {
                if (!ALWAYS_WAIT && tickityBoo-- == 0)
                    fail();
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
            File mph = new File("/tmp/" + mTest + ".json");
            if (mph.exists())
                mph.delete();
        }

        public void waitForSave(Lister lister, Runnable runner) {
            lister.setUri(Lister.PREF_FILE_URI, getFileUri());
            lister.saveLists(lister,
                    o -> {
                        runner.run();
                    },
                    code -> {
                        fail();
                        return false;
                    });
        }
        
        public void nextStep() {
            mStep++;
        }
        
        public int atStep() {
            return mStep;
        }
    }
    
    @Test
    public void prefsTest() {
        Lister lister = (Lister) ApplicationProvider.getApplicationContext();//new Lister();
        lister.setInt("test int pref", 666);
        assertEquals(666, lister.getInt("test int pref"));
        lister.setInt("test int pref", 0);
        assertEquals(0, lister.getInt("test int pref"));
        lister.setBool("test bool pref", false);
        assertFalse(lister.getBool("test bool pref"));
        lister.setBool("test bool pref", true);
        assertTrue(lister.getBool("test bool pref"));
        lister.setUri("test uri pref", Uri.parse("content://blah"));
        assertEquals(Uri.parse("content://blah"), lister.getUri("test uri pref"));
        lister.setUri("test uri pref", Uri.parse("https://flobadob.co.hu/fleegle"));
        assertEquals(Uri.parse("https://flobadob.co.hu/fleegle"), lister.getUri("test uri pref"));
    }

    @Before
    public void before() {
        Lister.FORCE_CACHE_FAIL = 0;
    }

    Lister makeApp() {
        Lister lister = (Lister) ApplicationProvider.getApplicationContext();
        
        Checklists lists = lister.getLists();
        Checklist list = new Checklist("A");

        ChecklistItem item = new ChecklistItem("A0");
        list.addChild(item);

        item = new ChecklistItem("A1");
        list.addChild(item);

        lists.addChild(list);

        list = new Checklist("B");

        item = new ChecklistItem("B0");
        list.addChild(item);

        item = new ChecklistItem( "B1");
        list.addChild(item);

        lists.addChild(list);

        return lister;
    }

    @Test
    public void saveCacheOKStoreNull() {
        Lister lister = makeApp();
        lister.setUri(Lister.PREF_FILE_URI, null);
        final Waiter waiter = new Waiter("saveCacheOKStoreNull");
        lister.saveLists(lister,
                o -> {
                    assertNull(o);
                    waiter.nextStep();
                },
                code -> {
                    fail();
                    return false;
                });
        waiter.waitForStep(1);
    }

    @Test
    public void saveCacheOKStoreOK() {
        Lister lister = makeApp();
        final Waiter waiter = new Waiter("saveCacheOKStoreOK");
        lister.setUri(Lister.PREF_FILE_URI, waiter.getFileUri());
        lister.saveLists(lister,
                o -> {
                    assertNull(o);
                    waiter.nextStep();
                },
                code -> {
                    fail();
                    return false;
                });
        waiter.waitForStep(1);
    }

    @Test
    public void saveCacheOKStoreFail() {
        Lister lister = makeApp();
        lister.setUri(Lister.PREF_FILE_URI, Uri.parse(BAD_URI));
        final Waiter wait = new Waiter("saveCacheOKStoreFail");
        lister.saveLists(lister,
                o -> {
                    assertNull(o);
                    wait.nextStep();
                },
                code -> {
                    assertEquals(R.string.failed_save_to_uri, code);
                    wait.nextStep();
                    return true;
                });
        wait.waitForStep(2);
    }


    @Test
    public void saveCacheNoneStoreNull() {
        Lister lister = makeApp();
        lister.setUri(Lister.PREF_FILE_URI, null);
        final Waiter waiter = new Waiter("saveCacheNoneStoreNull");
        Lister.FORCE_CACHE_FAIL = R.string.snack_no_cache;
        lister.saveLists(lister,
                o -> {
                    assertNull(o);
                    waiter.nextStep();
                },
                code -> {
                    // this ought to be called!
                    if (waiter.atStep() == 0)
                        assertEquals(R.string.failed_save_to_cache, code);
                    else if (waiter.atStep() == 1)
                        assertEquals(R.string.failed_save_to_cache_and_file, code);
                    else
                        fail();
                    waiter.nextStep();
                    return true;
                });
        waiter.waitForStep(2);
    }

    @Test
    public void saveCacheFailStoreFail() {
        Lister lister = makeApp();
        // Both failed
        lister.setUri(Lister.PREF_FILE_URI, Uri.parse(BAD_URI));
        final Waiter waiter = new Waiter("saveCacheFailStoreFail");
        Lister.FORCE_CACHE_FAIL = R.string.snack_no_cache;
        lister.saveLists(lister,
                o -> {
                    fail();
                },
                code -> {
                    // this ought to be called!
                    if (waiter.atStep() == 0)
                        assertEquals(R.string.failed_save_to_cache, code);
                    else if (waiter.atStep() == 1)
                        assertEquals(R.string.failed_save_to_uri, code);
                    else
                        fail();
                    waiter.nextStep();
                    return true;
                });
        waiter.waitForStep(2);
    }

    @Test
    public void saveCacheFailStoreOK() {
        Lister lister = makeApp();
        // Both failed
        final Waiter waiter = new Waiter("saveCacheFailStoreOK");
        lister.setUri(Lister.PREF_FILE_URI, waiter.getFileUri());
        Lister.FORCE_CACHE_FAIL = R.string.snack_no_cache;
        lister.saveLists(lister,
                o -> {
                    assertNull(o);
                    waiter.nextStep();
                },
                code -> {
                    // this ought to be called!
                    if (waiter.atStep() == 0)
                        assertEquals(R.string.failed_save_to_cache, code);
                    else
                        fail();
                    waiter.nextStep();
                    return true;
                });
        waiter.waitForStep(2);
    }

    @Test
    public void saveCacheOKStoreFile() {
        Lister lister = makeApp();
        final Waiter waiter = new Waiter("saveCacheOKStoreFile");
        lister.setUri(Lister.PREF_FILE_URI, waiter.getFileUri());
        lister.saveLists(lister,
                o -> {
                    assertNull(o);
                    waiter.nextStep();
                },
                code -> {
                    fail();
                    return false;
                });
        waiter.waitForStep(1);
    }

    @Test
    public void loadCacheOKStoreNull() {
        Lister lister = makeApp();
        final Waiter waiter = new Waiter("loadCacheOKStoreNull");
        waiter.waitForSave(lister, () -> {
            lister.setUri(Lister.PREF_FILE_URI, null);
            lister.loadLists(lister,
                    o -> {
                        assertSame(o, lister.getLists());
                        waiter.nextStep();
                    },
                    code -> {
                        assertEquals(R.string.failed_no_file, code);
                        waiter.nextStep();
                        return true;
                    });
        });
        waiter.waitForStep(2);
    }

    @Test
    public void loadCacheOKStoreOK() {
        Lister lister = makeApp();
        final Waiter waiter = new Waiter("loadCacheOKStoreOK");
        waiter.waitForSave(lister, () -> {
            lister.setUri(Lister.PREF_FILE_URI, waiter.getFileUri());
            lister.loadLists(lister,
                    o -> {
                        assertSame(o, lister.getLists());
                        waiter.nextStep();
                    },
                    code -> {
                        fail();
                        return false;
                    });
        });
        waiter.waitForStep(1);
    }

    @Test
    public void loadCacheOKStoreFail() {
        Lister lister = makeApp();
        final Waiter waiter = new Waiter("loadCacheOKStoreFail");
        waiter.waitForSave(lister, () -> {
            lister.setUri(Lister.PREF_FILE_URI, Uri.parse(BAD_URI + "loadCacheOKStoreFail"));
            lister.loadLists(lister,
                    o -> {
                        assertSame(o, lister.getLists());
                        waiter.nextStep();
                    },
                    code -> {
                        assertEquals(R.string.failed_file_load, code);
                        waiter.nextStep();
                        return true;
                    });
        });
        waiter.waitForStep(2);
    }


    @Test
    public void loadCacheFailStoreNull() {
        Lister lister = makeApp();
        final Waiter waiter = new Waiter("loadCacheFailStoreNull");
        waiter.waitForSave(lister, () -> {
            Lister.FORCE_CACHE_FAIL = R.string.failed_cache_load;
            lister.setUri(Lister.PREF_FILE_URI, null);
            lister.loadLists(lister,
                    o -> {
                        fail();
                    },
                    code -> {
                        // this ought to be called!
                        if (waiter.atStep() == 0)
                            assertEquals(R.string.failed_no_file, code);
                        else if (waiter.atStep() == 1)
                            assertEquals(R.string.failed_cache_load, code);
                        waiter.nextStep();
                        return true;
                    });
        });
        waiter.waitForStep(2);
    }

    @Test
    public void loadCacheFailStoreFail() {
        Lister lister = makeApp();
        final Waiter waiter = new Waiter("loadCacheFailStoreFail");
        waiter.waitForSave(lister, () -> {
            Lister.FORCE_CACHE_FAIL = R.string.failed_cache_load;
            lister.setUri(Lister.PREF_FILE_URI, Uri.parse(BAD_URI));
            lister.loadLists(lister,
                    o -> {
                        assertSame(o, lister.getLists());
                        waiter.nextStep();
                    },
                    code -> {
                        if (waiter.atStep() == 0)
                            assertEquals(R.string.failed_file_load, code);
                        else if (waiter.atStep() == 1)
                            assertEquals(R.string.failed_cache_load, code);
                        else
                            fail();
                        waiter.nextStep();
                        return true;
                    });
        });
        waiter.waitForStep(2);
    }

    @Test
    public void loadCacheFailStoreOK() {
        Lister lister = makeApp();
        final Waiter waiter = new Waiter("loadCacheFailStoreOK");
        waiter.waitForSave(lister, () -> {
            Lister.FORCE_CACHE_FAIL = R.string.failed_cache_load;
            lister.loadLists(lister,
                    o -> {
                        assertEquals(1, waiter.atStep());
                        waiter.nextStep();
                    },
                    code -> {
                        assertEquals(0, waiter.atStep());
                        assertEquals(R.string.failed_cache_load, code);
                        waiter.nextStep();
                        return true;
                    });
        });
        waiter.waitForStep(2);
    }

    @Test
    public void loadImportOK() {
        Lister lister = makeApp();
        final Waiter waiter = new Waiter("loadImportOK");
        waiter.waitForSave(lister, () -> {
            lister.loadLists(lister,
                    ol -> {
                        lister.importList(waiter.getFileUri(), lister,
                                o -> {
                                    List<EntryListItem> el = (List<EntryListItem>)o;
                                    assertEquals(2, el.size());
                                    assertEquals("A", el.get(0).getText());
                                    assertEquals("B", el.get(1).getText());
                                    waiter.nextStep();
                                },
                                code -> {
                                    fail();
                                    return false;
                                });
                    },
                    code -> {
                        fail();
                        return false;
                    });
        });
        waiter.waitForStep(1);
        assertEquals(4, lister.getLists().size());
    }

    /*
        lister.handleChangeStore(Context cxt, Intent intent, com.cdot.lists.Lister.SuccessCallback onOK, com.cdot.lists.Lister.FailCallback onFail);
    */
}
