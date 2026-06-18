/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.store

import java.io.IOException
import java.net.BindException
import java.net.ConnectException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.UnknownHostException
import java.nio.ByteBuffer
import java.util.Properties
import org.h2.Driver
import org.h2.api.ErrorCode
import org.h2.engine.Constants
import org.h2.engine.SessionRemote
import org.h2.message.DbException
import org.h2.message.Trace
import org.h2.message.TraceSystem
import org.h2.store.fs.FilePath
import org.h2.store.fs.FileUtils
import org.h2.util.MathUtils
import org.h2.util.NetUtils
import org.h2.util.SortedProperties
import org.h2.util.StringUtils
import org.h2.value.Transfer

/**
 * The file lock is used to lock a database so that only one process can write
 * to it. It uses a cooperative locking protocol. Usually a .lock.db file is
 * used, but locking by creating a socket is supported as well.
 *
 * Create a new file locking object.
 *
 * @param traceSystem the trace system to use
 * @param fileName the file name
 * @param sleep the number of milliseconds to sleep
 */
class FileLock(traceSystem: TraceSystem?, fileName: String?, sleep: Int) : Runnable {

    /**
     * The lock file name.
     */
    @Volatile
    private var fileName: String? = fileName

    /**
     * The server socket (only used when using the SOCKET mode).
     */
    @Volatile
    private var serverSocket: ServerSocket? = null

    /**
     * Whether the file is locked.
     */
    @Volatile
    private var locked = false

    /**
     * The number of milliseconds to sleep after checking a file.
     */
    private val sleep: Int = sleep

    /**
     * The trace object.
     */
    private val trace: Trace? =
            if (traceSystem == null) null else traceSystem.getTrace(Trace.FILE_LOCK)

    /**
     * The last time the lock file was written.
     */
    private var lastWrite: Long = 0

    private var method: String? = null
    private var properties: Properties? = null
    private var uniqueId: String? = null
    private var watchdog: Thread? = null

    /**
     * Lock the file if possible. A file may only be locked once.
     *
     * @param fileLockMethod the file locking method to use
     * @throws DbException if locking was not successful
     */
    @Synchronized
    fun lock(fileLockMethod: FileLockMethod) {
        checkServer()
        if (locked) {
            throw DbException.getInternalError("already locked")
        }
        when (fileLockMethod) {
            FileLockMethod.FILE -> lockFile()
            FileLockMethod.SOCKET -> lockSocket()
            FileLockMethod.FS, FileLockMethod.NO -> {
            }
        }
        locked = true
    }

    /**
     * Unlock the file. The watchdog thread is stopped. This method does nothing
     * if the file is already unlocked.
     */
    @Synchronized
    fun unlock() {
        if (!locked) {
            return
        }
        locked = false
        try {
            if (watchdog != null) {
                watchdog!!.interrupt()
            }
        } catch (e: Exception) {
            trace!!.debug(e, "unlock")
        }
        try {
            if (fileName != null) {
                if (load() == properties) {
                    FileUtils.delete(fileName)
                }
            }
            if (serverSocket != null) {
                serverSocket!!.close()
            }
        } catch (e: Exception) {
            trace!!.debug(e, "unlock")
        } finally {
            fileName = null
            serverSocket = null
        }
        try {
            if (watchdog != null) {
                watchdog!!.join()
            }
        } catch (e: Exception) {
            trace!!.debug(e, "unlock")
        } finally {
            watchdog = null
        }
    }

    /**
     * Add or change a setting to the properties. This call does not save the
     * file.
     *
     * @param key the key
     * @param value the value
     */
    fun setProperty(key: String?, value: String?) {
        if (value == null) {
            properties!!.remove(key)
        } else {
            properties!!.put(key, value)
        }
    }

    /**
     * Save the lock file.
     *
     * @return the saved properties
     */
    fun save(): Properties? {
        try {
            FileUtils.newOutputStream(fileName, false).use { out ->
                properties!!.store(out, MAGIC)
            }
            lastWrite = aggressiveLastModified(fileName)
            if (trace!!.isDebugEnabled()) {
                trace.debug("save " + properties)
            }
            return properties
        } catch (e: IOException) {
            throw getExceptionFatal("Could not save properties " + fileName, e)
        }
    }

    private fun checkServer() {
        val prop = load()
        val server = prop.getProperty("server") ?: return
        var running = false
        val id = prop.getProperty("id")
        try {
            val socket = NetUtils.createSocket(server,
                    Constants.DEFAULT_TCP_PORT, false)
            val transfer = Transfer(null, socket)
            transfer.init()
            transfer.writeInt(Constants.TCP_PROTOCOL_VERSION_MIN_SUPPORTED)
            transfer.writeInt(Constants.TCP_PROTOCOL_VERSION_MAX_SUPPORTED)
            transfer.writeString(null)
            transfer.writeString(null)
            transfer.writeString(id)
            transfer.writeInt(SessionRemote.SESSION_CHECK_KEY)
            transfer.flush()
            val state = transfer.readInt()
            if (state == SessionRemote.STATUS_OK) {
                running = true
            }
            transfer.close()
            socket.close()
        } catch (e: IOException) {
            return
        }
        if (running) {
            val e = DbException.get(
                    ErrorCode.DATABASE_ALREADY_OPEN_1, "Server is running")
            throw e.addSQL(server + "/" + id)
        }
    }

    /**
     * Load the properties file.
     *
     * @return the properties
     */
    fun load(): Properties {
        var lastException: IOException? = null
        for (i in 0 until 5) {
            try {
                val p2 = SortedProperties.loadProperties(fileName!!)
                if (trace!!.isDebugEnabled()) {
                    trace.debug("load " + p2)
                }
                return p2
            } catch (e: IOException) {
                lastException = e
            }
        }
        throw getExceptionFatal(
                "Could not load properties " + fileName, lastException)
    }

    private fun waitUntilOld() {
        for (i in 0 until 2 * TIME_GRANULARITY / SLEEP_GAP) {
            val last = aggressiveLastModified(fileName)
            val dist = System.currentTimeMillis() - last
            if (dist < -TIME_GRANULARITY) {
                // lock file modified in the future -
                // wait for a bit longer than usual
                try {
                    Thread.sleep(2 * sleep.toLong())
                } catch (e: Exception) {
                    trace!!.debug(e, "sleep")
                }
                return
            } else if (dist > TIME_GRANULARITY) {
                return
            }
            try {
                Thread.sleep(SLEEP_GAP.toLong())
            } catch (e: Exception) {
                trace!!.debug(e, "sleep")
            }
        }
        throw getExceptionFatal("Lock file recently modified", null)
    }

    private fun setUniqueId() {
        val bytes = MathUtils.secureRandomBytes(RANDOM_BYTES)
        val random = StringUtils.convertBytesToHex(bytes)
        uniqueId = java.lang.Long.toHexString(System.currentTimeMillis()) + random
        properties!!.setProperty("id", uniqueId)
    }

    private fun lockFile() {
        method = FILE
        properties = SortedProperties()
        properties!!.setProperty("method", method.toString())
        setUniqueId()
        FileUtils.createDirectories(FileUtils.getParent(fileName))
        if (!FileUtils.createFile(fileName)) {
            waitUntilOld()
            val m2 = load().getProperty("method", FILE)
            if (m2 != FILE) {
                throw getExceptionFatal("Unsupported lock method " + m2, null)
            }
            save()
            sleep(2 * sleep)
            if (load() != properties) {
                throw getExceptionAlreadyInUse("Locked by another process: " + fileName)
            }
            FileUtils.delete(fileName)
            if (!FileUtils.createFile(fileName)) {
                throw getExceptionFatal("Another process was faster", null)
            }
        }
        save()
        sleep(SLEEP_GAP)
        if (load() != properties) {
            fileName = null
            throw getExceptionFatal("Concurrent update", null)
        }
        locked = true
        watchdog = Thread(this, "H2 File Lock Watchdog " + fileName)
        Driver.setThreadContextClassLoader(watchdog)
        watchdog!!.isDaemon = true
        watchdog!!.priority = Thread.MAX_PRIORITY - 1
        watchdog!!.start()
    }

    private fun lockSocket() {
        method = SOCKET
        properties = SortedProperties()
        properties!!.setProperty("method", method.toString())
        setUniqueId()
        // if this returns 127.0.0.1,
        // the computer is probably not networked
        val ipAddress = NetUtils.getLocalAddress()
        FileUtils.createDirectories(FileUtils.getParent(fileName))
        if (!FileUtils.createFile(fileName)) {
            waitUntilOld()
            val read = aggressiveLastModified(fileName)
            val p2 = load()
            val m2 = p2.getProperty("method", SOCKET)
            if (m2 == FILE) {
                lockFile()
                return
            } else if (m2 != SOCKET) {
                throw getExceptionFatal("Unsupported lock method " + m2, null)
            }
            val ip = p2.getProperty("ipAddress", ipAddress)
            if (ipAddress != ip) {
                throw getExceptionAlreadyInUse("Locked by another computer: " + ip)
            }
            val port = p2.getProperty("port", "0")
            val portId = port.toInt()
            val address: InetAddress
            try {
                address = InetAddress.getByName(ip)
            } catch (e: UnknownHostException) {
                throw getExceptionFatal("Unknown host " + ip, e)
            }
            for (i in 0 until 3) {
                try {
                    val s = Socket(address, portId)
                    s.close()
                    throw getExceptionAlreadyInUse("Locked by another process")
                } catch (e: BindException) {
                    throw getExceptionFatal("Bind Exception", null)
                } catch (e: ConnectException) {
                    trace!!.debug(e, "socket not connected to port " + port)
                } catch (e: IOException) {
                    throw getExceptionFatal("IOException", null)
                }
            }
            if (read != aggressiveLastModified(fileName)) {
                throw getExceptionFatal("Concurrent update", null)
            }
            FileUtils.delete(fileName)
            if (!FileUtils.createFile(fileName)) {
                throw getExceptionFatal("Another process was faster", null)
            }
        }
        try {
            // 0 to use any free port
            serverSocket = NetUtils.createServerSocket(0, false)
            val port = serverSocket!!.localPort
            properties!!.setProperty("ipAddress", ipAddress)
            properties!!.setProperty("port", Integer.toString(port))
        } catch (e: Exception) {
            trace!!.debug(e, "lock")
            serverSocket = null
            lockFile()
            return
        }
        save()
        locked = true
        watchdog = Thread(this,
                "H2 File Lock Watchdog (Socket) " + fileName)
        watchdog!!.isDaemon = true
        watchdog!!.start()
    }

    private fun getExceptionAlreadyInUse(reason: String): DbException {
        var e = DbException.get(
                ErrorCode.DATABASE_ALREADY_OPEN_1, reason)
        if (fileName != null) {
            try {
                val prop = load()
                val server = prop.getProperty("server")
                if (server != null) {
                    val serverId = server + "/" + prop.getProperty("id")
                    e = e.addSQL(serverId)
                }
            } catch (e2: DbException) {
                // ignore
            }
        }
        return e
    }

    fun getUniqueId(): String? {
        return uniqueId
    }

    override fun run() {
        try {
            while (locked && fileName != null) {
                // trace.debug("watchdog check");
                try {
                    if (!FileUtils.exists(fileName) ||
                            aggressiveLastModified(fileName) != lastWrite) {
                        save()
                    }
                    Thread.sleep(sleep.toLong())
                } catch (e: OutOfMemoryError) {
                    // ignore
                } catch (e: NullPointerException) {
                    // ignore
                } catch (e: InterruptedException) {
                    // ignore
                } catch (e: Exception) {
                    trace!!.debug(e, "watchdog")
                }
            }
            while (true) {
                // take a copy so we don't get an NPE between checking it and using it
                val local = serverSocket ?: break
                try {
                    trace!!.debug("watchdog accept")
                    val s = local.accept()
                    s.close()
                } catch (e: Exception) {
                    trace!!.debug(e, "watchdog")
                }
            }
        } catch (e: Exception) {
            trace!!.debug(e, "watchdog")
        }
        trace!!.debug("watchdog end")
    }

    companion object {

        private const val MAGIC = "FileLock"
        private const val FILE = "file"
        private const val SOCKET = "socket"
        private const val RANDOM_BYTES = 16
        private const val SLEEP_GAP = 25
        private const val TIME_GRANULARITY = 2000

        /**
         * Aggressively read last modified time, to work-around remote filesystems.
         *
         * @param fileName file name to check
         * @return last modified date/time in milliseconds UTC
         */
        @JvmStatic
        private fun aggressiveLastModified(fileName: String?): Long {
            /*
             * Some remote filesystem, e.g. SMB on Windows, can cache metadata for
             * 5-10 seconds. To work around that, do a one-byte read from the
             * underlying file, which has the effect of invalidating the metadata
             * cache.
             */
            try {
                FilePath.get(fileName).open("rws").use { f ->
                    val b = ByteBuffer.wrap(ByteArray(1))
                    f.read(b)
                }
            } catch (ignoreEx: IOException) {
            }
            return FileUtils.lastModified(fileName)
        }

        @JvmStatic
        private fun sleep(time: Int) {
            try {
                Thread.sleep(time.toLong())
            } catch (e: InterruptedException) {
                throw getExceptionFatal("Sleep interrupted", e)
            }
        }

        @JvmStatic
        private fun getExceptionFatal(reason: String, t: Throwable?): DbException {
            return DbException.get(
                    ErrorCode.ERROR_OPENING_DATABASE_1, t, reason)
        }

        /**
         * Get the file locking method type given a method name.
         *
         * @param method the method name
         * @return the method type
         * @throws DbException if the method name is unknown
         */
        @JvmStatic
        fun getFileLockMethod(method: String?): FileLockMethod {
            return if (method == null || method.equals("FILE", ignoreCase = true)) {
                FileLockMethod.FILE
            } else if (method.equals("NO", ignoreCase = true)) {
                FileLockMethod.NO
            } else if (method.equals("SOCKET", ignoreCase = true)) {
                FileLockMethod.SOCKET
            } else if (method.equals("FS", ignoreCase = true)) {
                FileLockMethod.FS
            } else {
                throw DbException.get(ErrorCode.UNSUPPORTED_LOCK_METHOD_1, method)
            }
        }
    }
}
