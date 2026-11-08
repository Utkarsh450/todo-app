package com.utkarsh.todo.core.offline

import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileOfflineStoreTest {
    @get:Rule
    val temp = TemporaryFolder()

    private fun store(max: Int = 300) = FileOfflineStore(File(temp.root, "cache"), max)

    @Test
    fun `reads back what was written, bytes and content type`() {
        val s = store()
        val body = byteArrayOf(0, 10, 13, -1, 65) // includes a newline byte and non-text bytes
        s.write("http://x/api/tasks", body, "application/json")
        val saved = s.read("http://x/api/tasks")!!
        assertArrayEquals(body, saved.body)
        assertEquals("application/json", saved.contentType)
    }

    @Test
    fun `an unknown key reads as nothing`() = assertNull(store().read("nope"))

    @Test
    fun `a missing content type comes back as null`() {
        val s = store()
        s.write("k", "x".toByteArray(), null)
        assertNull(s.read("k")!!.contentType)
    }

    @Test
    fun `the cache survives being opened again`() {
        store().write("k", "hello".toByteArray(), "text/plain")
        assertEquals("hello", String(store().read("k")!!.body))
    }

    @Test
    fun `past the limit the oldest entries are dropped`() {
        val s = store(max = 3)
        (1..5).forEach { i ->
            s.write("k$i", "v$i".toByteArray(), null)
            File(temp.root, "cache").listFiles()!!.maxByOrNull { it.lastModified() }!!.setLastModified(1_000L * i)
        }
        assertNull(s.read("k1"))
        assertNull(s.read("k2"))
        assertNotNull(s.read("k3"))
        assertNotNull(s.read("k5"))
    }

    @Test
    fun `a cache folder that cannot be created does not throw`() {
        val blocker = temp.newFile("blocker")
        val s = FileOfflineStore(File(blocker, "inside"))
        s.write("k", "v".toByteArray(), null)
        assertNull(s.read("k"))
    }
}
