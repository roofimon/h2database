/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.util

import java.lang.management.ManagementFactory
import java.lang.management.MonitorInfo
import java.lang.management.ThreadInfo
import java.lang.management.ThreadMXBean
import java.util.ArrayList
import java.util.Arrays
import java.util.HashSet
import java.util.WeakHashMap

/**
 * Utility to detect AB-BA deadlocks.
 */
open class AbbaLockingDetector : Runnable {

    private val tickIntervalMs = 2
    @Volatile
    private var stop = false

    private val threadMXBean: ThreadMXBean =
        ManagementFactory.getThreadMXBean()
    private var thread: Thread? = null

    /**
     * Map of (object A) -> ( map of (object locked before object A) ->
     * (stack trace where locked) )
     */
    private val lockOrdering: MutableMap<String, MutableMap<String, String>> =
        WeakHashMap()
    private val knownDeadlocks: MutableSet<String> = HashSet()

    /**
     * Start collecting locking data.
     *
     * @return this
     */
    open fun startCollecting(): AbbaLockingDetector {
        thread = Thread(this, "AbbaLockingDetector").apply {
            isDaemon = true
            start()
        }
        return this
    }

    /**
     * Reset the state.
     */
    @Synchronized
    open fun reset() {
        lockOrdering.clear()
        knownDeadlocks.clear()
    }

    /**
     * Stop collecting.
     *
     * @return this
     */
    open fun stopCollecting(): AbbaLockingDetector {
        stop = true
        val t = thread
        if (t != null) {
            try {
                t.join()
            } catch (e: InterruptedException) {
                // ignore
            }
            thread = null
        }
        return this
    }

    override fun run() {
        while (!stop) {
            try {
                tick()
            } catch (t: Throwable) {
                break
            }
        }
    }

    private fun tick() {
        if (tickIntervalMs > 0) {
            try {
                Thread.sleep(tickIntervalMs.toLong())
            } catch (ex: InterruptedException) {
                // ignore
            }
        }

        val list = threadMXBean.dumpAllThreads(
            // lockedMonitors
            true,
            // lockedSynchronizers
            false
        )
        processThreadList(list)
    }

    private fun processThreadList(threadInfoList: Array<ThreadInfo>) {
        val lockOrder: MutableList<String> = ArrayList()
        for (threadInfo in threadInfoList) {
            lockOrder.clear()
            generateOrdering(lockOrder, threadInfo)
            if (lockOrder.size > 1) {
                markHigher(lockOrder, threadInfo)
            }
        }
    }

    @Synchronized
    private fun markHigher(
        lockOrder: List<String>,
        threadInfo: ThreadInfo
    ) {
        val topLock = lockOrder[lockOrder.size - 1]
        var map = lockOrdering[topLock]
        if (map == null) {
            map = WeakHashMap()
            lockOrdering[topLock] = map
        }
        var oldException: String? = null
        for (i in 0 until lockOrder.size - 1) {
            val olderLock = lockOrder[i]
            val oldMap = lockOrdering[olderLock]
            var foundDeadLock = false
            if (oldMap != null) {
                val e = oldMap[topLock]
                if (e != null) {
                    foundDeadLock = true
                    val deadlockType = "$topLock $olderLock"
                    if (!knownDeadlocks.contains(deadlockType)) {
                        println(
                            topLock + " synchronized after \n " + olderLock +
                                ", but in the past before\n" + "AFTER\n" +
                                getStackTraceForThread(threadInfo) +
                                "BEFORE\n" + e
                        )
                        knownDeadlocks.add(deadlockType)
                    }
                }
            }
            if (!foundDeadLock && !map.containsKey(olderLock)) {
                if (oldException == null) {
                    oldException = getStackTraceForThread(threadInfo)
                }
                map[olderLock] = oldException
            }
        }
    }

    companion object {
        /**
         * We cannot simply call getLockedMonitors because it is not guaranteed to
         * return the locks in the correct order.
         */
        private fun generateOrdering(lockOrder: MutableList<String>, info: ThreadInfo) {
            val lockedMonitors = info.lockedMonitors
            Arrays.sort(lockedMonitors) { a, b -> b.lockedStackDepth - a.lockedStackDepth }
            for (mi in lockedMonitors) {
                val lockName = getObjectName(mi)
                if (lockName == "sun.misc.Launcher\$AppClassLoader") {
                    // ignore, it shows up everywhere
                    continue
                }
                // Ignore locks which are locked multiple times in
                // succession - Java locks are recursive.
                if (!lockOrder.contains(lockName)) {
                    lockOrder.add(lockName)
                }
            }
        }

        /**
         * Dump data in the same format as [ThreadInfo.toString], but with
         * some modifications (no stack frame limit, and removal of uninteresting
         * stack frames)
         */
        private fun getStackTraceForThread(info: ThreadInfo): String {
            val sb = StringBuilder().append('"')
                .append(info.threadName).append("\"" + " Id=")
                .append(info.threadId).append(' ').append(info.threadState)
            if (info.lockName != null) {
                sb.append(" on ").append(info.lockName)
            }
            if (info.lockOwnerName != null) {
                sb.append(" owned by \"").append(info.lockOwnerName)
                    .append("\" Id=").append(info.lockOwnerId)
            }
            if (info.isSuspended) {
                sb.append(" (suspended)")
            }
            if (info.isInNative) {
                sb.append(" (in native)")
            }
            sb.append('\n')
            val stackTrace = info.stackTrace
            val lockedMonitors = info.lockedMonitors
            var startDumping = false
            for (i in stackTrace.indices) {
                val e = stackTrace[i]
                if (startDumping) {
                    dumpStackTraceElement(info, sb, i, e)
                }

                for (mi in lockedMonitors) {
                    if (mi.lockedStackDepth == i) {
                        // Only start dumping the stack from the first time we lock
                        // something.
                        // Removes a lot of unnecessary noise from the output.
                        if (!startDumping) {
                            dumpStackTraceElement(info, sb, i, e)
                            startDumping = true
                        }
                        sb.append("\t-  locked ").append(mi)
                        sb.append('\n')
                    }
                }
            }
            return sb.toString()
        }

        private fun dumpStackTraceElement(
            info: ThreadInfo,
            sb: StringBuilder, i: Int, e: StackTraceElement
        ) {
            sb.append('\t').append("at ").append(e)
                .append('\n')
            if (i == 0 && info.lockInfo != null) {
                val ts = info.threadState
                when (ts) {
                    Thread.State.BLOCKED -> sb.append("\t-  blocked on ")
                        .append(info.lockInfo)
                        .append('\n')
                    Thread.State.WAITING -> sb.append("\t-  waiting on ")
                        .append(info.lockInfo)
                        .append('\n')
                    Thread.State.TIMED_WAITING -> sb.append("\t-  waiting on ")
                        .append(info.lockInfo)
                        .append('\n')
                    else -> {
                    }
                }
            }
        }

        private fun getObjectName(info: MonitorInfo): String {
            return info.className + "@" +
                Integer.toHexString(info.identityHashCode)
        }
    }
}
