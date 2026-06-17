/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.util

import java.io.PrintStream
import java.io.PrintWriter
import java.io.StringWriter
import java.lang.management.LockInfo
import java.lang.management.ManagementFactory
import java.lang.management.MonitorInfo
import java.lang.management.ThreadInfo
import java.lang.management.ThreadMXBean
import java.util.ArrayList
import java.util.HashMap
import java.util.Timer
import java.util.TimerTask
import org.h2.engine.SysProperties
import org.h2.mvstore.db.MVTable

/**
 * Detects deadlocks between threads. Prints out data in the same format as the
 * CTRL-BREAK handler, but includes information about table locks.
 */
class ThreadDeadlockDetector private constructor() {

    private val threadBean: ThreadMXBean = ManagementFactory.getThreadMXBean()

    init {
        // a daemon thread
        // delay: 10 ms
        // period: 10000 ms (100 seconds)
        val threadCheck = Timer("ThreadDeadlockDetector", true)
        threadCheck.schedule(object : TimerTask() {
            override fun run() {
                checkForDeadlocks()
            }
        }, 10, 10_000)
    }

    /**
     * Checks if any threads are deadlocked. If any, print the thread dump
     * information.
     */
    fun checkForDeadlocks() {
        val deadlockedThreadIds = threadBean.findDeadlockedThreads() ?: return
        dumpThreadsAndLocks(
            "ThreadDeadlockDetector - deadlock found :",
            threadBean, deadlockedThreadIds, System.out
        )
    }

    companion object {
        private const val INDENT = "    "

        private var detector: ThreadDeadlockDetector? = null

        /**
         * Initialize the detector.
         */
        @JvmStatic
        @Synchronized
        fun init() {
            if (detector == null) {
                detector = ThreadDeadlockDetector()
            }
        }

        /**
         * Dump all deadlocks (if any).
         *
         * @param msg the message
         */
        @JvmStatic
        fun dumpAllThreadsAndLocks(msg: String) {
            dumpAllThreadsAndLocks(msg, System.out)
        }

        /**
         * Dump all deadlocks (if any).
         *
         * @param msg the message
         * @param out the output
         */
        @JvmStatic
        fun dumpAllThreadsAndLocks(msg: String, out: PrintStream) {
            val threadBean = ManagementFactory.getThreadMXBean()
            val allThreadIds = threadBean.allThreadIds
            dumpThreadsAndLocks(msg, threadBean, allThreadIds, out)
        }

        private fun dumpThreadsAndLocks(
            msg: String, threadBean: ThreadMXBean,
            threadIds: LongArray, out: PrintStream,
        ) {
            val stringWriter = StringWriter()
            val print = PrintWriter(stringWriter)

            print.println(msg)

            val tableWaitingForLockMap: HashMap<Long, String>
            val tableExclusiveLocksMap: HashMap<Long, ArrayList<String>>
            val tableSharedLocksMap: HashMap<Long, ArrayList<String>>
            if (SysProperties.THREAD_DEADLOCK_DETECTOR) {
                tableWaitingForLockMap = MVTable.WAITING_FOR_LOCK
                    .getSnapshotOfAllThreads()
                tableExclusiveLocksMap = MVTable.EXCLUSIVE_LOCKS
                    .getSnapshotOfAllThreads()
                tableSharedLocksMap = MVTable.SHARED_LOCKS
                    .getSnapshotOfAllThreads()
            } else {
                tableWaitingForLockMap = HashMap()
                tableExclusiveLocksMap = HashMap()
                tableSharedLocksMap = HashMap()
            }

            val infos = threadBean.getThreadInfo(threadIds, true, true)
            for (ti in infos) {
                printThreadInfo(print, ti)
                printLockInfo(
                    print, ti.lockedSynchronizers,
                    tableWaitingForLockMap[ti.threadId],
                    tableExclusiveLocksMap[ti.threadId],
                    tableSharedLocksMap[ti.threadId]
                )
            }

            print.flush()
            // Dump it to system.out in one block, so it doesn't get mixed up with
            // other stuff when we're using a logging subsystem.
            out.println(stringWriter.buffer)
            out.flush()
        }

        private fun printThreadInfo(print: PrintWriter, ti: ThreadInfo) {
            // print thread information
            printThread(print, ti)

            // print stack trace with locks
            val stackTrace = ti.stackTrace
            val monitors = ti.lockedMonitors
            for (i in stackTrace.indices) {
                val e = stackTrace[i]
                print.println(INDENT + "at " + e.toString())
                for (mi in monitors) {
                    if (mi.lockedStackDepth == i) {
                        print.println(INDENT + "  - locked " + mi)
                    }
                }
            }
            print.println()
        }

        private fun printThread(print: PrintWriter, ti: ThreadInfo) {
            print.print(
                "\"" + ti.threadName + "\"" + " Id="
                    + ti.threadId + " in " + ti.threadState
            )
            if (ti.lockName != null) {
                print.append(" on lock=").append(ti.lockName)
            }
            if (ti.isSuspended) {
                print.append(" (suspended)")
            }
            if (ti.isInNative) {
                print.append(" (running in native)")
            }
            print.println()
            if (ti.lockOwnerName != null) {
                print.println(
                    INDENT + " owned by " + ti.lockOwnerName + " Id="
                        + ti.lockOwnerId
                )
            }
        }

        private fun printLockInfo(
            print: PrintWriter, locks: Array<LockInfo>,
            tableWaitingForLock: String?,
            tableExclusiveLocks: ArrayList<String>?,
            tableSharedLocksMap: ArrayList<String>?,
        ) {
            print.println(INDENT + "Locked synchronizers: count = " + locks.size)
            for (li in locks) {
                print.println(INDENT + "  - " + li)
            }
            if (tableWaitingForLock != null) {
                print.println(INDENT + "Waiting for table: " + tableWaitingForLock)
            }
            if (tableExclusiveLocks != null) {
                print.println(INDENT + "Exclusive table locks: count = " + tableExclusiveLocks.size)
                for (name in tableExclusiveLocks) {
                    print.println(INDENT + "  - " + name)
                }
            }
            if (tableSharedLocksMap != null) {
                print.println(INDENT + "Shared table locks: count = " + tableSharedLocksMap.size)
                for (name in tableSharedLocksMap) {
                    print.println(INDENT + "  - " + name)
                }
            }
            print.println()
        }
    }
}
