/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.store

import java.io.IOException
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.sql.SQLException
import java.util.HashSet

import org.h2.api.ErrorCode
import org.h2.engine.ConnectionInfo
import org.h2.engine.Constants
import org.h2.engine.Database
import org.h2.engine.SessionLocal
import org.h2.message.DbException
import org.h2.store.fs.FileUtils
import org.h2.store.fs.Recorder
import org.h2.store.fs.rec.FilePathRec
import org.h2.tools.Recover
import org.h2.util.IOUtils
import org.h2.util.StringUtils
import org.h2.util.Utils

/**
 * A tool that simulates a crash while writing to the database, and then
 * verifies the database doesn't get corrupt.
 */
class RecoverTester : Recorder {

    private var testDatabase = "memFS:reopen"
    private var writeCount = Utils.getProperty("h2.recoverTestOffset", 0)
    private var testEvery = Utils.getProperty("h2.recoverTest", 64)
    private val maxFileSize = Utils.getProperty(
        "h2.recoverTestMaxFileSize", Integer.MAX_VALUE
    ) * 1024L * 1024
    private var verifyCount = 0
    private val knownErrors = HashSet<String>()
    @Volatile
    private var testing = false

    override fun log(op: Int, fileName: String, data: ByteArray?, x: Long) {
        if (op != Recorder.WRITE && op != Recorder.TRUNCATE) {
            return
        }
        if (!fileName.endsWith(Constants.SUFFIX_MV_FILE)) {
            return
        }
        writeCount++
        if ((writeCount % testEvery) != 0) {
            return
        }
        if (FileUtils.size(fileName) > maxFileSize) {
            // System.out.println(fileName + " " + IOUtils.length(fileName));
            return
        }
        if (testing) {
            // avoid deadlocks
            return
        }
        testing = true
        var out: PrintWriter? = null
        try {
            out = PrintWriter(
                OutputStreamWriter(
                    FileUtils.newOutputStream(fileName + ".log", true)
                )
            )
            testDatabase(fileName, out)
        } catch (e: IOException) {
            throw DbException.convertIOException(e, null)
        } finally {
            IOUtils.closeSilently(out)
            testing = false
        }
    }

    @Synchronized
    private fun testDatabase(fileName: String, out: PrintWriter) {
        out.println("+ write #" + writeCount + " verify #" + verifyCount)
        try {
            IOUtils.copyFiles(fileName, testDatabase + Constants.SUFFIX_MV_FILE)
            verifyCount++
            // avoid using the Engine class to avoid deadlocks
            val ci = ConnectionInfo(
                "jdbc:h2:" + testDatabase +
                        ";FILE_LOCK=NO;TRACE_LEVEL_FILE=0", null, "", ""
            )
            val database = Database(ci, null)
            // close the database
            val sysSession = database.getSystemSession()
            sysSession.prepare("script to '" + testDatabase + ".sql'").query(0)
            sysSession.prepare("shutdown immediately").update()
            database.removeSession(null)
            // everything OK - return
            return
        } catch (e: DbException) {
            val e2 = DbException.toSQLException(e)
            val errorCode = e2.errorCode
            if (errorCode == ErrorCode.WRONG_USER_OR_PASSWORD) {
                return
            } else if (errorCode == ErrorCode.FILE_ENCRYPTION_ERROR_1) {
                return
            }
            e.printStackTrace(System.out)
        } catch (e: Exception) {
            // failed
            var errorCode = 0
            if (e is SQLException) {
                errorCode = e.errorCode
            }
            if (errorCode == ErrorCode.WRONG_USER_OR_PASSWORD) {
                return
            } else if (errorCode == ErrorCode.FILE_ENCRYPTION_ERROR_1) {
                return
            }
            e.printStackTrace(System.out)
        }
        out.println("begin ------------------------------ " + writeCount)
        try {
            Recover.execute(fileName.substring(0, fileName.lastIndexOf('/')), null)
        } catch (e: SQLException) {
            // ignore
        }
        testDatabase += "X"
        try {
            IOUtils.copyFiles(fileName, testDatabase + Constants.SUFFIX_MV_FILE)
            // avoid using the Engine class to avoid deadlocks
            val ci = ConnectionInfo(
                "jdbc:h2:" +
                        testDatabase + ";FILE_LOCK=NO", null, null, null
            )
            val database = Database(ci, null)
            // close the database
            database.removeSession(null)
        } catch (e: Exception) {
            var e = e
            var errorCode = 0
            if (e is DbException) {
                e = e.getSQLException()
                errorCode = (e as SQLException).errorCode
            }
            if (errorCode == ErrorCode.WRONG_USER_OR_PASSWORD) {
                return
            } else if (errorCode == ErrorCode.FILE_ENCRYPTION_ERROR_1) {
                return
            }
            val buff = StringBuilder()
            val list = e.stackTrace
            var i = 0
            while (i < 10 && i < list.size) {
                buff.append(list[i].toString()).append('\n')
                i++
            }
            val s = buff.toString()
            if (!knownErrors.contains(s)) {
                out.println(writeCount.toString() + " code: " + errorCode + " " + e)
                e.printStackTrace(System.out)
                knownErrors.add(s)
            } else {
                out.println(writeCount.toString() + " code: " + errorCode)
            }
        }
    }

    fun setTestEvery(testEvery: Int) {
        this.testEvery = testEvery
    }

    companion object {
        private val instance = RecoverTester()

        /**
         * Initialize the recover test.
         *
         * @param recoverTest the value of the recover test parameter
         */
        @JvmStatic
        @Synchronized
        fun init(recoverTest: String) {
            if (StringUtils.isNumber(recoverTest)) {
                instance.setTestEvery(recoverTest.toInt())
            }
            FilePathRec.setRecorder(instance)
        }
    }

}
