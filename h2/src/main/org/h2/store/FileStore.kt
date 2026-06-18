/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.store

import java.io.IOException
import java.lang.ref.Reference
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.Arrays
import org.h2.api.ErrorCode
import org.h2.engine.Constants
import org.h2.engine.SysProperties
import org.h2.message.DbException
import org.h2.security.SecureFileStore
import org.h2.store.fs.FileUtils

/**
 * This class is an abstraction of a random access file.
 * Each file contains a magic header, and reading / writing is done in blocks.
 * See also [SecureFileStore]
 */
open class FileStore
/**
 * Create a new file using the given settings.
 *
 * @param handler the callback object
 * @param name the file name
 * @param mode the access mode ("r", "rw", "rws", "rwd")
 */
protected constructor(
    /**
     * The callback object is responsible to check access rights, and free up
     * disk space if required.
     */
    private val handler: DataHandler?,
    /**
     * The file name. Public (Java's `protected` was package-accessible, which
     * Kotlin's protected is not) so same-package classes like
     * FileStoreInputStream can read it.
     */
    var name: String?,
    mode: String
) {

    private var file: java.nio.channels.FileChannel? = null
    private var filePos: Long = 0
    private var fileLength: Long = 0
    private var autoDeleteReference: Reference<*>? = null
    private var checkedWriting = true
    private val mode: String
    private var lock: java.nio.channels.FileLock? = null

    init {
        var mode = mode
        try {
            val exists = FileUtils.exists(name)
            if (exists && !FileUtils.canWrite(name)) {
                mode = "r"
            } else {
                FileUtils.createDirectories(FileUtils.getParent(name))
            }
            file = FileUtils.open(name, mode)
            if (exists) {
                fileLength = file!!.size()
            }
        } catch (e: IOException) {
            throw DbException.convertIOException(
                e, "name: " + name + " mode: " + mode
            )
        }
        this.mode = mode
    }

    /**
     * Generate the random salt bytes if required.
     *
     * @return the random salt or the magic
     */
    protected open fun generateSalt(): ByteArray {
        return HEADER.toByteArray(StandardCharsets.UTF_8)
    }

    /**
     * Initialize the key using the given salt.
     *
     * @param salt the salt
     */
    protected open fun initKey(salt: ByteArray) {
        // do nothing
    }

    fun setCheckedWriting(value: Boolean) {
        this.checkedWriting = value
    }

    private fun checkWritingAllowed() {
        if (handler != null && checkedWriting) {
            handler.checkWritingAllowed()
        }
    }

    private fun checkPowerOff() {
        if (handler != null) {
            handler.checkPowerOff()
        }
    }

    /**
     * Initialize the file. This method will write or check the file header if
     * required.
     */
    fun init() {
        val len = Constants.FILE_BLOCK_SIZE
        val salt: ByteArray
        val magic = HEADER.toByteArray(StandardCharsets.UTF_8)
        if (length() < HEADER_LENGTH) {
            // write unencrypted
            checkedWriting = false
            writeDirect(magic, 0, len)
            salt = generateSalt()
            writeDirect(salt, 0, len)
            initKey(salt)
            // write (maybe) encrypted
            write(magic, 0, len)
            checkedWriting = true
        } else {
            // read unencrypted
            seek(0)
            val buff = ByteArray(len)
            readFullyDirect(buff, 0, len)
            if (!Arrays.equals(buff, magic)) {
                throw DbException.get(ErrorCode.FILE_VERSION_ERROR_1, name)
            }
            salt = ByteArray(len)
            readFullyDirect(salt, 0, len)
            initKey(salt)
            // read (maybe) encrypted
            readFully(buff, 0, Constants.FILE_BLOCK_SIZE)
            if (!Arrays.equals(buff, magic)) {
                throw DbException.get(ErrorCode.FILE_ENCRYPTION_ERROR_1, name)
            }
        }
    }

    /**
     * Close the file.
     */
    fun close() {
        if (file != null) {
            try {
                trace("close", name, file)
                file!!.close()
            } catch (e: IOException) {
                throw DbException.convertIOException(e, name)
            } finally {
                file = null
            }
        }
    }

    /**
     * Close the file without throwing any exceptions. Exceptions are simply
     * ignored.
     */
    fun closeSilently() {
        try {
            close()
        } catch (e: Exception) {
            // ignore
        }
    }

    /**
     * Close the file (ignoring exceptions) and delete the file.
     */
    fun closeAndDeleteSilently() {
        if (file != null) {
            closeSilently()
            handler!!.tempFileDeleter!!.deleteFile(autoDeleteReference, name)
            name = null
        }
    }

    /**
     * Read a number of bytes without decrypting.
     *
     * @param b the target buffer
     * @param off the offset
     * @param len the number of bytes to read
     */
    open fun readFullyDirect(b: ByteArray, off: Int, len: Int) {
        readFully(b, off, len)
    }

    /**
     * Read a number of bytes.
     *
     * @param b the target buffer
     * @param off the offset
     * @param len the number of bytes to read
     */
    open fun readFully(b: ByteArray, off: Int, len: Int) {
        if (len < 0 || len % Constants.FILE_BLOCK_SIZE != 0) {
            throw DbException.getInternalError("unaligned read " + name + " len " + len)
        }
        checkPowerOff()
        try {
            FileUtils.readFully(file, ByteBuffer.wrap(b, off, len))
        } catch (e: IOException) {
            throw DbException.convertIOException(e, name)
        }
        filePos += len.toLong()
    }

    /**
     * Go to the specified file location.
     *
     * @param pos the location
     */
    open fun seek(pos: Long) {
        if (pos % Constants.FILE_BLOCK_SIZE != 0L) {
            throw DbException.getInternalError("unaligned seek " + name + " pos " + pos)
        }
        try {
            if (pos != filePos) {
                file!!.position(pos)
                filePos = pos
            }
        } catch (e: IOException) {
            throw DbException.convertIOException(e, name)
        }
    }

    /**
     * Write a number of bytes without encrypting.
     *
     * @param b the source buffer
     * @param off the offset
     * @param len the number of bytes to write
     */
    protected open fun writeDirect(b: ByteArray, off: Int, len: Int) {
        write(b, off, len)
    }

    /**
     * Write a number of bytes.
     *
     * @param b the source buffer
     * @param off the offset
     * @param len the number of bytes to write
     */
    open fun write(b: ByteArray, off: Int, len: Int) {
        if (len < 0 || len % Constants.FILE_BLOCK_SIZE != 0) {
            throw DbException.getInternalError("unaligned write " + name + " len " + len)
        }
        checkWritingAllowed()
        checkPowerOff()
        try {
            FileUtils.writeFully(file, ByteBuffer.wrap(b, off, len))
        } catch (e: IOException) {
            closeFileSilently()
            throw DbException.convertIOException(e, name)
        }
        filePos += len.toLong()
        fileLength = Math.max(filePos, fileLength)
    }

    /**
     * Set the length of the file. This will expand or shrink the file.
     *
     * @param newLength the new file size
     */
    fun setLength(newLength: Long) {
        if (newLength % Constants.FILE_BLOCK_SIZE != 0L) {
            throw DbException.getInternalError("unaligned setLength " + name + " pos " + newLength)
        }
        checkPowerOff()
        checkWritingAllowed()
        try {
            if (newLength > fileLength) {
                val pos = filePos
                file!!.position(newLength - 1)
                FileUtils.writeFully(file, ByteBuffer.wrap(ByteArray(1)))
                file!!.position(pos)
            } else {
                file!!.truncate(newLength)
            }
            fileLength = newLength
        } catch (e: IOException) {
            closeFileSilently()
            throw DbException.convertIOException(e, name)
        }
    }

    /**
     * Get the file size in bytes.
     *
     * @return the file size
     */
    fun length(): Long {
        var len = fileLength
        if (ASSERT) {
            try {
                len = file!!.size()
                if (len != fileLength) {
                    throw DbException.getInternalError("file " + name + " length " + len + " expected " + fileLength)
                }
                if (len % Constants.FILE_BLOCK_SIZE != 0L) {
                    val newLength = len + Constants.FILE_BLOCK_SIZE -
                        (len % Constants.FILE_BLOCK_SIZE)
                    file!!.truncate(newLength)
                    fileLength = newLength
                    throw DbException.getInternalError("unaligned file length " + name + " len " + len)
                }
            } catch (e: IOException) {
                throw DbException.convertIOException(e, name)
            }
        }
        return len
    }

    /**
     * Get the current location of the file pointer.
     *
     * @return the location
     */
    fun getFilePointer(): Long {
        if (ASSERT) {
            try {
                if (file!!.position() != filePos) {
                    throw DbException.getInternalError(file!!.position().toString() + " " + filePos)
                }
            } catch (e: IOException) {
                throw DbException.convertIOException(e, name)
            }
        }
        return filePos
    }

    /**
     * Call fsync. Depending on the operating system and hardware, this may or
     * may not in fact write the changes.
     */
    fun sync() {
        try {
            file!!.force(true)
        } catch (e: IOException) {
            closeFileSilently()
            throw DbException.convertIOException(e, name)
        }
    }

    /**
     * Automatically delete the file once it is no longer in use.
     */
    fun autoDelete() {
        if (autoDeleteReference == null) {
            autoDeleteReference = handler!!.tempFileDeleter!!.addFile(name!!, this)
        }
    }

    /**
     * No longer automatically delete the file once it is no longer in use.
     */
    fun stopAutoDelete() {
        handler!!.tempFileDeleter!!.stopAutoDelete(autoDeleteReference, name)
        autoDeleteReference = null
    }

    /**
     * Close the file. The file may later be re-opened using openFile.
     * @throws IOException on failure
     */
    @Throws(IOException::class)
    fun closeFile() {
        file!!.close()
        file = null
    }

    /**
     * Just close the file, without setting the reference to null. This method
     * is called when writing failed. The reference is not set to null so that
     * there are no NullPointerExceptions later on.
     */
    private fun closeFileSilently() {
        try {
            file!!.close()
        } catch (e: IOException) {
            // ignore
        }
    }

    /**
     * Re-open the file. The file pointer will be reset to the previous
     * location.
     * @throws IOException on failure
     */
    @Throws(IOException::class)
    fun openFile() {
        if (file == null) {
            file = FileUtils.open(name, mode)
            file!!.position(filePos)
        }
    }

    /**
     * Try to lock the file.
     *
     * @return true if successful
     */
    @Synchronized
    fun tryLock(): Boolean {
        return try {
            lock = file!!.tryLock()
            lock != null
        } catch (e: Exception) {
            // ignore OverlappingFileLockException
            false
        }
    }

    /**
     * Release the file lock.
     */
    @Synchronized
    fun releaseLock() {
        if (file != null && lock != null) {
            try {
                lock!!.release()
            } catch (e: Exception) {
                // ignore
            }
            lock = null
        }
    }

    companion object {
        /**
         * The size of the file header in bytes.
         */
        const val HEADER_LENGTH = 3 * Constants.FILE_BLOCK_SIZE

        /**
         * The magic file header.
         */
        private val HEADER: String =
            "-- H2 0.5/B --      ".substring(0, Constants.FILE_BLOCK_SIZE - 1) + "\n"

        private val ASSERT: Boolean

        init {
            var a = false
            // Intentional side-effect
            assert(run { a = true; a })
            ASSERT = a
        }

        /**
         * Open a non encrypted file store with the given settings.
         *
         * @param handler the data handler
         * @param name the file name
         * @param mode the access mode (r, rw, rws, rwd)
         * @return the created object
         */
        @JvmStatic
        fun open(handler: DataHandler?, name: String?, mode: String): FileStore {
            return open(handler, name, mode, null, null, 0)
        }

        /**
         * Open an encrypted file store with the given settings.
         *
         * @param handler the data handler
         * @param name the file name
         * @param mode the access mode (r, rw, rws, rwd)
         * @param cipher the name of the cipher algorithm
         * @param key the encryption key
         * @return the created object
         */
        @JvmStatic
        fun open(
            handler: DataHandler?, name: String?, mode: String,
            cipher: String?, key: ByteArray?
        ): FileStore {
            return open(
                handler, name, mode, cipher, key,
                Constants.ENCRYPTION_KEY_HASH_ITERATIONS
            )
        }

        /**
         * Open an encrypted file store with the given settings.
         *
         * @param handler the data handler
         * @param name the file name
         * @param mode the access mode (r, rw, rws, rwd)
         * @param cipher the name of the cipher algorithm
         * @param key the encryption key
         * @param keyIterations the number of iterations the key should be hashed
         * @return the created object
         */
        @JvmStatic
        fun open(
            handler: DataHandler?, name: String?, mode: String,
            cipher: String?, key: ByteArray?, keyIterations: Int
        ): FileStore {
            val store: FileStore
            if (cipher == null) {
                store = FileStore(handler, name, mode)
            } else {
                store = SecureFileStore(
                    handler!!, name!!, mode,
                    cipher, key!!, keyIterations
                )
            }
            return store
        }

        private fun trace(method: String, fileName: String?, o: Any?) {
            if (SysProperties.TRACE_IO) {
                println("FileStore." + method + " " + fileName + " " + o)
            }
        }
    }
}
