package com.cdot.lists

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.cdot.lists.Lister.FailCallback
import com.cdot.lists.Lister.SuccessCallback
import com.cdot.lists.model.Checklist
import com.cdot.lists.model.ChecklistItem
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Tests for com.cdot.lists.Lister
 */
@Config(sdk = [28])
@RunWith(RobolectricTestRunner::class)
class ListerTest {
    internal inner class Waiter(private val mTest: String) {
        private var mStep = 0
        val fileUri: Uri
            get() = Uri.parse("file:///tmp/$mTest.json")

        fun waitForStep(waitStep: Int) {
            var tickityBoo = 30 // 3 second timeout waiting for a step
            // Timeout after 10s
            while (mStep != waitStep) {
                if (!ALWAYS_WAIT && tickityBoo-- == 0) fail()
                try {
                    Thread.sleep(100)
                } catch (ex: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
            val mph = File("/tmp/" + mTest + ".json")
            if (mph.exists()) mph.delete()
        }

        fun waitForSave(lister: Lister, runner: Runnable) {
            lister.setUri(Lister.PREF_FILE_URI, fileUri)
            lister.saveLists(lister,
                    object : SuccessCallback {
                        override fun succeeded(data: Any?) {
                            runner.run()
                        }
                    },
                    object : FailCallback {
                        override fun failed(code: Int, vararg args: Any): Boolean {
                            fail()
                            return false
                        }
                    })
        }

        fun nextStep() {
            mStep++
        }

        fun atStep(): Int {
            return mStep
        }

    }

    @Test
    fun prefsTest() {
        val lister = ApplicationProvider.getApplicationContext<Context>() as Lister //new Lister();
        lister.setInt("test int pref", 666)
        assertEquals(666, lister.getInt("test int pref").toLong())
        lister.setInt("test int pref", 0)
        assertEquals(0, lister.getInt("test int pref").toLong())
        lister.setUri("test uri pref", Uri.parse("content://blah"))
        assertEquals(Uri.parse("content://blah"), lister.getUri("test uri pref"))
        lister.setUri("test uri pref", Uri.parse("https://flobadob.co.hu/fleegle"))
        assertEquals(Uri.parse("https://flobadob.co.hu/fleegle"), lister.getUri("test uri pref"))
        lister.setBool("test bool pref", false)
        assertFalse(lister.getBool("test bool pref"))
        lister.setBool("test bool pref", true)
        assertTrue(lister.getBool("test bool pref"))
    }

    @Before
    fun before() {
        Lister.FORCE_CACHE_FAIL = 0
    }

    private fun makeApp(): Lister {
        val lister = ApplicationProvider.getApplicationContext<Context>() as Lister
        val lists = lister.lists
        var list = Checklist("A")
        var item = ChecklistItem("A0")
        list.addChild(item)
        item = ChecklistItem("A1")
        list.addChild(item)
        lists.addChild(list)
        list = Checklist("B")
        item = ChecklistItem("B0")
        list.addChild(item)
        item = ChecklistItem("B1")
        list.addChild(item)
        lists.addChild(list)
        return lister
    }

    @Test
    fun saveCacheOKStoreNull() {
        val lister = makeApp()
        lister.setUri(Lister.PREF_FILE_URI, null)
        val waiter = Waiter("saveCacheOKStoreNull")
        lister.saveLists(lister,
                object : SuccessCallback {
                    override fun succeeded(data: Any?) {
                        assertNull(data)
                        waiter.nextStep()
                    }
                },
                object : FailCallback {
                    override fun failed(code: Int, vararg args: Any): Boolean {
                        fail()
                        return false
                    }
                })
        waiter.waitForStep(1)
    }

    @Test
    fun saveCacheOKStoreOK() {
        val lister = makeApp()
        val waiter = Waiter("saveCacheOKStoreOK")
        lister.setUri(Lister.PREF_FILE_URI, waiter.fileUri)
        lister.saveLists(lister,
                object : SuccessCallback {
                    override fun succeeded(data: Any?) {
                        assertNull(data)
                        waiter.nextStep()
                    }
                },
                object : FailCallback {
                    override fun failed(code: Int, vararg args: Any): Boolean {
                        fail()
                        return false
                    }
                })
        waiter.waitForStep(1)
    }

    @Test
    fun saveCacheOKStoreFail() {
        val lister = makeApp()
        lister.setUri(Lister.PREF_FILE_URI, Uri.parse(BAD_URI))
        val wait = Waiter("saveCacheOKStoreFail")
        lister.saveLists(lister,
                object : SuccessCallback {
                    override fun succeeded(data: Any?) {
                        assertNull(data)
                        wait.nextStep()
                    }
                },
                object : FailCallback {
                    override fun failed(code: Int, vararg args: Any): Boolean {
                        assertEquals(R.string.failed_save_to_uri.toLong(), code.toLong())
                        wait.nextStep()
                        return true
                    }
                })
        wait.waitForStep(2)
    }

    @Test
    fun saveCacheNoneStoreNull() {
        val lister = makeApp()
        lister.setUri(Lister.PREF_FILE_URI, null)
        val waiter = Waiter("saveCacheNoneStoreNull")
        Lister.FORCE_CACHE_FAIL = R.string.snack_no_cache
        lister.saveLists(lister,
                object : SuccessCallback {
                    override fun succeeded(data: Any?) {
                        assertNull(data)
                        waiter.nextStep()
                    }
                },
                object : FailCallback {
                    override fun failed(code: Int, vararg args: Any): Boolean {
                        // this ought to be called!
                        when (waiter.atStep()) {
                            0 -> assertEquals(R.string.failed_save_to_cache.toLong(), code.toLong())
                            1 -> assertEquals(R.string.failed_save_to_cache_and_file.toLong(), code.toLong())
                            else -> fail()
                        }
                        waiter.nextStep()
                        return true
                    }
                })
        waiter.waitForStep(2)
    }

    @Test
    fun saveCacheFailStoreFail() {
        val lister = makeApp()
        // Both failed
        lister.setUri(Lister.PREF_FILE_URI, Uri.parse(BAD_URI))
        val waiter = Waiter("saveCacheFailStoreFail")
        Lister.FORCE_CACHE_FAIL = R.string.snack_no_cache
        lister.saveLists(lister,
                object : SuccessCallback {
                    override fun succeeded(data: Any?) {
                        fail()
                    }
                },
                object : FailCallback {
                    override fun failed(code: Int, vararg args: Any): Boolean {
                        // this ought to be called!
                        if (waiter.atStep() == 0) assertEquals(R.string.failed_save_to_cache.toLong(), code.toLong()) else if (waiter.atStep() == 1) assertEquals(R.string.failed_save_to_uri.toLong(), code.toLong()) else fail()
                        waiter.nextStep()
                        return true
                    }
                })
        waiter.waitForStep(2)
    }

    @Test
    fun saveCacheFailStoreOK() {
        val lister = makeApp()
        // Both failed
        val waiter = Waiter("saveCacheFailStoreOK")
        lister.setUri(Lister.PREF_FILE_URI, waiter.fileUri)
        Lister.FORCE_CACHE_FAIL = R.string.snack_no_cache
        lister.saveLists(lister,
                object : SuccessCallback {
                    override fun succeeded(data: Any?) {
                        assertNull(data)
                        waiter.nextStep()
                    }
                },
                object : FailCallback {
                    override fun failed(code: Int, vararg args: Any): Boolean {
                        // this ought to be called!
                        if (waiter.atStep() == 0) assertEquals(R.string.failed_save_to_cache.toLong(), code.toLong()) else fail()
                        waiter.nextStep()
                        return true
                    }
                })
        waiter.waitForStep(2)
    }

    @Test
    fun saveCacheOKStoreFile() {
        val lister = makeApp()
        val waiter = Waiter("saveCacheOKStoreFile")
        lister.setUri(Lister.PREF_FILE_URI, waiter.fileUri)
        lister.saveLists(lister,
                object : SuccessCallback {
                    override fun succeeded(data: Any?) {
                        assertNull(data)
                        waiter.nextStep()
                    }
                },
                object : FailCallback {
                    override fun failed(code: Int, vararg args: Any): Boolean {
                        fail()
                        return false
                    }
                })
        waiter.waitForStep(1)
    }

    @Test
    fun loadCacheOKStoreNull() {
        val lister = makeApp()
        val waiter = Waiter("loadCacheOKStoreNull")
        waiter.waitForSave(lister) {
            lister.setUri(Lister.PREF_FILE_URI, null)
            lister.loadLists(lister,
                    object : SuccessCallback {
                        override fun succeeded(data: Any?) {
                            assertSame(data, lister.lists)
                            waiter.nextStep()
                        }
                    },
                    object : FailCallback {
                        override fun failed(code: Int, vararg args: Any): Boolean {
                            assertEquals(R.string.failed_no_file.toLong(), code.toLong())
                            waiter.nextStep()
                            return true
                        }
                    })
        }
        waiter.waitForStep(2)
    }

    @Test
    fun loadCacheOKStoreOK() {
        val lister = makeApp()
        val waiter = Waiter("loadCacheOKStoreOK")
        waiter.waitForSave(lister) {
            lister.setUri(Lister.PREF_FILE_URI, waiter.fileUri)
            lister.loadLists(lister,
                    object : SuccessCallback {
                        override fun succeeded(data: Any?) {
                            assertSame(data, lister.lists)
                            waiter.nextStep()
                        }
                    },
                    object : FailCallback {
                        override fun failed(code: Int, vararg args: Any): Boolean {
                            fail()
                            return false
                        }
                    })
        }
        waiter.waitForStep(1)
    }

    @Test
    fun loadCacheOKStoreFail() {
        val lister = makeApp()
        val waiter = Waiter("loadCacheOKStoreFail")
        waiter.waitForSave(lister) {
            lister.setUri(Lister.PREF_FILE_URI, Uri.parse(BAD_URI + "loadCacheOKStoreFail"))
            lister.loadLists(lister,
                    object : SuccessCallback {
                        override fun succeeded(data: Any?) {
                            assertSame(data, lister.lists)
                            waiter.nextStep()
                        }
                    },
                    object : FailCallback {
                        override fun failed(code: Int, vararg args: Any): Boolean {
                            assertEquals(R.string.failed_file_load.toLong(), code.toLong())
                            waiter.nextStep()
                            return true
                        }
                    })
        }
        waiter.waitForStep(2)
    }

    @Test
    fun loadCacheFailStoreNull() {
        val lister = makeApp()
        val waiter = Waiter("loadCacheFailStoreNull")
        waiter.waitForSave(lister) {
            Lister.FORCE_CACHE_FAIL = R.string.failed_cache_load
            lister.setUri(Lister.PREF_FILE_URI, null)
            lister.loadLists(lister,
                    object : SuccessCallback {
                        override fun succeeded(data: Any?) {
                            fail()
                        }
                    },
                    object : FailCallback {
                        override fun failed(code: Int, vararg args: Any): Boolean {
                            // this ought to be called!
                            if (waiter.atStep() == 0) assertEquals(R.string.failed_no_file.toLong(), code.toLong()) else if (waiter.atStep() == 1) assertEquals(R.string.failed_cache_load.toLong(), code.toLong())
                            waiter.nextStep()
                            return true
                        }
                    })
        }
        waiter.waitForStep(2)
    }

    @Test
    fun loadCacheFailStoreFail() {
        val lister = makeApp()
        val waiter = Waiter("loadCacheFailStoreFail")
        waiter.waitForSave(lister) {
            Lister.FORCE_CACHE_FAIL = R.string.failed_cache_load
            lister.setUri(Lister.PREF_FILE_URI, Uri.parse(BAD_URI))
            lister.loadLists(lister,
                    object : SuccessCallback {
                        override fun succeeded(data: Any?) {
                            assertSame(data, lister.lists)
                            waiter.nextStep()
                        }
                    },
                    object : FailCallback {
                        override fun failed(code: Int, vararg args: Any): Boolean {
                            if (waiter.atStep() == 0) assertEquals(R.string.failed_file_load.toLong(), code.toLong()) else if (waiter.atStep() == 1) assertEquals(R.string.failed_cache_load.toLong(), code.toLong()) else fail()
                            waiter.nextStep()
                            return true
                        }
                    })
        }
        waiter.waitForStep(2)
    }

    @Test
    fun loadCacheFailStoreOK() {
        val lister = makeApp()
        val waiter = Waiter("loadCacheFailStoreOK")
        waiter.waitForSave(lister) {
            Lister.FORCE_CACHE_FAIL = R.string.failed_cache_load
            lister.loadLists(lister,
                    object : SuccessCallback {
                        override fun succeeded(data: Any?) {
                            assertEquals(1, waiter.atStep().toLong())
                            waiter.nextStep()
                        }
                    },
                    object : FailCallback {
                        override fun failed(code: Int, vararg args: Any): Boolean {
                            assertEquals(0, waiter.atStep())
                            assertEquals(R.string.failed_cache_load, code)
                            waiter.nextStep()
                            return true
                        }
                    })
        }
        waiter.waitForStep(2)
    }

    @Test
    fun loadImportOK() {
        val lister = makeApp()
        val waiter = Waiter("loadImportOK")
        waiter.waitForSave(lister) {
            lister.lists.remove(lister.lists.children[0], false)
            lister.importList(waiter.fileUri, lister,
                    object : SuccessCallback {
                        override fun succeeded(data: Any?) {
                            val el = data as List<String>
                            assertEquals(1, el.size.toLong())
                            assertEquals("A", el[0])
                            waiter.nextStep()
                        }
                    },
                    object : FailCallback {
                        override fun failed(code: Int, vararg args: Any): Boolean {
                            assertEquals(R.string.failed_duplicate_list, code)
                            assertEquals(1, args.size)
                            assertEquals("B", args[0])
                            return false
                        }
                    })
        }
        waiter.waitForStep(1)
        assertEquals(2, lister.lists.size().toLong())
    }

    companion object {
        private const val TEST_FILE = "file:///tmp/test"
        private const val TEST_URI = "content://test.json"
        private const val BAD_URI = "bad_content_provider://test.json"

        // When debugging, set this to true to prevent waiter.wait timing out
        private const val ALWAYS_WAIT = true
    }
}