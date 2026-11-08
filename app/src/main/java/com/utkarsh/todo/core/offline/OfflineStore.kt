package com.utkarsh.todo.core.offline

import java.io.File
import java.security.MessageDigest

/** A saved server answer. */
class StoredResponse(val body: ByteArray, val contentType: String?)

/** Remembers the last good answer for each URL, so screens can still be read when the server can't be reached. */
interface OfflineStore {
    fun read(key: String): StoredResponse?
    fun write(key: String, body: ByteArray, contentType: String?)
}

/**
 * One small file per URL in [dir] (first line: content type, rest: the body). Past [maxEntries] files the oldest are
 * deleted, so a long-running app does not grow without limit. Every operation swallows I/O trouble: a broken cache
 * must never break a request that would otherwise succeed.
 */
class FileOfflineStore(private val dir: File, private val maxEntries: Int = 300) : OfflineStore {
    private val lock = Any()

    private fun fileFor(key: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
        return File(dir, digest.joinToString("") { "%02x".format(it) })
    }

    override fun read(key: String): StoredResponse? = synchronized(lock) {
        runCatching {
            val bytes = fileFor(key).takeIf { it.isFile }?.readBytes() ?: return null
            val newline = bytes.indexOf('\n'.code.toByte())
            if (newline < 0) return null
            StoredResponse(bytes.copyOfRange(newline + 1, bytes.size), String(bytes, 0, newline).ifEmpty { null })
        }.getOrNull()
    }

    override fun write(key: String, body: ByteArray, contentType: String?) {
        synchronized(lock) {
            runCatching {
                dir.mkdirs()
                val file = fileFor(key)
                val temp = File(dir, file.name + ".tmp")
                temp.writeBytes((contentType.orEmpty() + "\n").toByteArray() + body)
                if (!temp.renameTo(file)) { file.delete(); temp.renameTo(file) }
                prune()
            }
        }
    }

    private fun prune() {
        val files = dir.listFiles { f -> f.isFile && !f.name.endsWith(".tmp") } ?: return
        if (files.size <= maxEntries) return
        files.sortedBy { it.lastModified() }.take(files.size - maxEntries).forEach { it.delete() }
    }
}
