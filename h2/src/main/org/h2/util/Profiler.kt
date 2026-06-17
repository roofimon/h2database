/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.util

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.LineNumberReader
import java.io.OutputStream
import java.io.Reader
import java.io.StringReader
import java.lang.instrument.Instrumentation
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.ArrayList
import java.util.HashMap
import java.util.concurrent.TimeUnit

/**
 * A simple CPU profiling tool similar to java -Xrunhprof. It can be used
 * in-process (to profile the current application) or as a standalone program
 * (to profile a different process, or files containing full thread dumps).
 */
class Profiler : Runnable {

    @JvmField
    var interval = 2

    @JvmField
    var depth = 48

    @JvmField
    var paused = false

    @JvmField
    var sumClasses = false

    @JvmField
    var sumMethods = false

    private var pid = 0

    private val ignoreLines = (
        "java," +
            "sun," +
            "com.sun.," +
            "com.google.common.," +
            "com.mongodb.," +
            "org.bson.,"
        ).split(",").toTypedArray()
    private val ignorePackages = (
        "java," +
            "sun," +
            "com.sun.," +
            "com.google.common.," +
            "com.mongodb.," +
            "org.bson"
        ).split(",").toTypedArray()
    private val ignoreThreads = (
        "java.lang.Object.wait," +
            "java.lang.Thread.dumpThreads," +
            "java.lang.Thread.getThreads," +
            "java.lang.Thread.sleep," +
            "java.lang.UNIXProcess.waitForProcessExit," +
            "java.net.PlainDatagramSocketImpl.receive0," +
            "java.net.PlainSocketImpl.accept," +
            "java.net.PlainSocketImpl.socketAccept," +
            "java.net.SocketInputStream.socketRead," +
            "java.net.SocketOutputStream.socketWrite," +
            "org.eclipse.jetty.io.nio.SelectorManager\$SelectSet.doSelect," +
            "sun.awt.windows.WToolkit.eventLoop," +
            "sun.misc.Unsafe.park," +
            "sun.nio.ch.EPollArrayWrapper.epollWait," +
            "sun.nio.ch.KQueueArrayWrapper.kevent0," +
            "sun.nio.ch.ServerSocketChannelImpl.accept," +
            "dalvik.system.VMStack.getThreadStackTrace," +
            "dalvik.system.NativeStart.run"
        ).split(",").toTypedArray()

    @Volatile
    private var stop = false
    private val counts = HashMap<String, Int>()

    /**
     * The summary (usually one entry per package, unless sumClasses is enabled,
     * in which case it's one entry per class).
     */
    private val summary = HashMap<String, Int>()
    private var minCount = 1
    private var total = 0
    private var thread: Thread? = null
    private var start: Long = 0
    private var time: Long = 0
    private var threadDumps = 0

    private fun run(vararg args: String) {
        if (args.isEmpty()) {
            println("Show profiling data")
            println(
                "Usage: java " + javaClass.name +
                    " <pid> | <stackTraceFileNames>"
            )
            println("Processes:")
            val processes = exec("jps", "-l")
            println(processes)
            return
        }
        start = System.nanoTime()
        if (args[0].matches(Regex("\\d+"))) {
            pid = args[0].toInt()
            var last: Long = 0
            while (true) {
                tick()
                val t = System.nanoTime()
                if (t - last > TimeUnit.SECONDS.toNanos(5)) {
                    time = System.nanoTime() - start
                    println(getTopTraces(3))
                    last = t
                }
            }
        }
        try {
            for (arg in args) {
                if (arg.startsWith("-")) {
                    if ("-classes" == arg) {
                        sumClasses = true
                    } else if ("-methods" == arg) {
                        sumMethods = true
                    } else if ("-packages" == arg) {
                        sumClasses = false
                        sumMethods = false
                    } else {
                        throw IllegalArgumentException(arg)
                    }
                    continue
                }
                val file = Paths.get(arg)
                Files.newBufferedReader(file).use { reader ->
                    val r = LineNumberReader(reader)
                    var line: String?
                    while (r.readLine().also { line = it } != null) {
                        if (line!!.startsWith("Full thread dump")) {
                            threadDumps++
                        }
                    }
                }
                Files.newBufferedReader(file).use { reader ->
                    processList(readStackTrace(LineNumberReader(reader)))
                }
            }
            println(getTopTraces(5))
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    /**
     * Start collecting profiling data.
     *
     * @return this
     */
    fun startCollecting(): Profiler {
        thread = Thread(this, "Profiler")
        thread!!.isDaemon = true
        thread!!.start()
        return this
    }

    /**
     * Stop collecting.
     *
     * @return this
     */
    fun stopCollecting(): Profiler {
        stop = true
        if (thread != null) {
            try {
                thread!!.join()
            } catch (e: InterruptedException) {
                // ignore
            }
            thread = null
        }
        return this
    }

    override fun run() {
        start = System.nanoTime()
        while (!stop) {
            try {
                tick()
            } catch (t: Throwable) {
                break
            }
        }
        time = System.nanoTime() - start
    }

    private fun tick() {
        if (interval > 0) {
            if (paused) {
                return
            }
            try {
                Thread.sleep(interval.toLong(), 0)
            } catch (e: Exception) {
                // ignore
            }
        }

        val list: List<Array<Any>>
        list = if (pid != 0) {
            readRunnableStackTraces(pid)
        } else {
            getRunnableStackTraces()
        }
        threadDumps++
        processList(list)
    }

    private fun processList(list: List<Array<Any>>) {
        for (dump in list) {
            if (startsWithAny(dump[0].toString(), ignoreThreads)) {
                continue
            }
            val buff = StringBuilder()
            // simple recursive calls are ignored
            var last: String? = null
            var packageCounts = false
            var j = 0
            var i = 0
            while (i < dump.size && j < depth) {
                val el = dump[i].toString()
                if (el != last && !startsWithAny(el, ignoreLines)) {
                    last = el
                    buff.append("at ").append(el).append(LINE_SEPARATOR)
                    if (!packageCounts && !startsWithAny(el, ignorePackages)) {
                        packageCounts = true
                        var index = 0
                        while (index < el.length) {
                            val c = el[index]
                            if (c == '(' || Character.isUpperCase(c)) {
                                break
                            }
                            index++
                        }
                        if (index > 0 && el[index - 1] == '.') {
                            index--
                        }
                        if (sumClasses) {
                            val m = el.indexOf('.', index + 1)
                            index = if (m >= 0) m else index
                        }
                        if (sumMethods) {
                            val m = el.indexOf('(', index + 1)
                            index = if (m >= 0) m else index
                        }
                        val groupName = el.substring(0, index)
                        increment(summary, groupName, 0)
                    }
                    j++
                }
                i++
            }
            if (buff.length > 0) {
                minCount = increment(counts, buff.toString().trim(), minCount)
                total++
            }
        }
    }

    /**
     * Get the top stack traces.
     *
     * @param count the maximum number of stack traces
     * @return the stack traces.
     */
    fun getTop(count: Int): String {
        stopCollecting()
        return getTopTraces(count)
    }

    private fun getTopTraces(count: Int): String {
        val buff = StringBuilder()
        buff.append("Profiler: top ").append(count).append(" stack trace(s) of ")
        if (time > 0) {
            buff.append(" of ").append(TimeUnit.NANOSECONDS.toMillis(time)).append(" ms")
        }
        if (threadDumps > 0) {
            buff.append(" of ").append(threadDumps).append(" thread dumps")
        }
        buff.append(":").append(LINE_SEPARATOR)
        if (counts.isEmpty()) {
            buff.append("(none)").append(LINE_SEPARATOR)
        }
        var copy = HashMap(counts)
        appendTop(buff, copy, count, total, false)
        buff.append("summary:").append(LINE_SEPARATOR)
        copy = HashMap(summary)
        appendTop(buff, copy, count, total, true)
        buff.append('.')
        return buff.toString()
    }

    companion object {
        private var instrumentation: Instrumentation? = null
        private val LINE_SEPARATOR = System.getProperty("line.separator", "\n")
        private const val MAX_ELEMENTS = 1000

        /**
         * This method is called when the agent is installed.
         *
         * @param agentArgs the agent arguments
         * @param inst the instrumentation object
         */
        @JvmStatic
        fun premain(agentArgs: String?, inst: Instrumentation) {
            instrumentation = inst
        }

        /**
         * Get the instrumentation object if started as an agent.
         *
         * @return the instrumentation, or null
         */
        @JvmStatic
        fun getInstrumentation(): Instrumentation? {
            return instrumentation
        }

        /**
         * Run the command line version of the profiler. The JDK (jps and jstack)
         * need to be in the path.
         *
         * @param args the process id of the process - if not set the java processes
         *        are listed
         */
        @JvmStatic
        fun main(vararg args: String) {
            Profiler().run(*args)
        }

        private fun getRunnableStackTraces(): List<Array<Any>> {
            val list = ArrayList<Array<Any>>()
            val map = Thread.getAllStackTraces()
            for (entry in map.entries) {
                val t = entry.key
                if (t.state != Thread.State.RUNNABLE) {
                    continue
                }
                val dump = entry.value
                if (dump == null || dump.isEmpty()) {
                    continue
                }
                list.add(Array<Any>(dump.size) { dump[it] })
            }
            return list
        }

        private fun readRunnableStackTraces(pid: Int): List<Array<Any>> {
            try {
                val jstack = exec("jstack", Integer.toString(pid))
                val r = LineNumberReader(StringReader(jstack))
                return readStackTrace(r)
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
        }

        @Throws(IOException::class)
        private fun readStackTrace(r: LineNumberReader): List<Array<Any>> {
            val list = ArrayList<Array<Any>>()
            while (true) {
                var line = r.readLine()
                if (line == null) {
                    break
                }
                if (!line.startsWith("\"")) {
                    // not a thread
                    continue
                }
                line = r.readLine()
                if (line == null) {
                    break
                }
                line = line.trim()
                if (!line.startsWith("java.lang.Thread.State: RUNNABLE")) {
                    continue
                }
                val stack = ArrayList<String>()
                while (true) {
                    line = r.readLine()
                    if (line == null) {
                        break
                    }
                    line = line.trim()
                    if (line.startsWith("- ")) {
                        continue
                    }
                    if (!line.startsWith("at ")) {
                        break
                    }
                    line = StringUtils.trimSubstring(line, 3)
                    stack.add(line)
                }
                if (!stack.isEmpty()) {
                    val s = Array<Any>(stack.size) { stack[it] }
                    list.add(s)
                }
            }
            return list
        }

        private fun exec(vararg args: String): String {
            val err = ByteArrayOutputStream()
            val out = ByteArrayOutputStream()
            try {
                val p = Runtime.getRuntime().exec(args)
                copyInThread(p.inputStream, out)
                copyInThread(p.errorStream, err)
                p.waitFor()
                val e = err.toString(StandardCharsets.UTF_8)
                if (!e.isEmpty()) {
                    throw RuntimeException(e)
                }
                return out.toString(StandardCharsets.UTF_8)
            } catch (e: Exception) {
                throw RuntimeException(e)
            }
        }

        private fun copyInThread(`in`: InputStream, out: OutputStream) {
            object : Thread("Profiler stream copy") {
                override fun run() {
                    val buffer = ByteArray(4096)
                    try {
                        while (true) {
                            val len = `in`.read(buffer, 0, buffer.size)
                            if (len < 0) {
                                break
                            }
                            out.write(buffer, 0, len)
                        }
                    } catch (e: Exception) {
                        throw RuntimeException(e)
                    }
                }
            }.start()
        }

        private fun startsWithAny(s: String, prefixes: Array<String>): Boolean {
            for (p in prefixes) {
                if (!p.isEmpty() && s.startsWith(p)) {
                    return true
                }
            }
            return false
        }

        private fun increment(map: HashMap<String, Int>, trace: String, minCount: Int): Int {
            var minCount = minCount
            map.merge(trace, 1) { a, b -> a + b }
            while (map.size > MAX_ELEMENTS) {
                val ei = map.entries.iterator()
                while (ei.hasNext()) {
                    val e = ei.next()
                    if (e.value <= minCount) {
                        ei.remove()
                    }
                }
                if (map.size > MAX_ELEMENTS) {
                    minCount++
                }
            }
            return minCount
        }

        private fun appendTop(buff: StringBuilder, map: HashMap<String, Int>, count: Int, total: Int, table: Boolean) {
            var x = 0
            var min = 0
            while (true) {
                var highest = 0
                var best: Map.Entry<String, Int>? = null
                for (el in map.entries) {
                    if (el.value > highest) {
                        best = el
                        highest = el.value
                    }
                }
                if (best == null) {
                    break
                }
                map.remove(best.key)
                if (++x >= count) {
                    if (best.value < min) {
                        break
                    }
                    min = best.value
                }
                val c = best.value
                val percent = 100 * c / Math.max(total, 1)
                if (table) {
                    if (percent > 1) {
                        buff.append(percent)
                            .append("%: ").append(best.key)
                            .append(LINE_SEPARATOR)
                    }
                } else {
                    buff.append(c).append('/').append(total).append(" (")
                        .append(percent)
                        .append("%):").append(LINE_SEPARATOR)
                        .append(best.key)
                        .append(LINE_SEPARATOR)
                }
            }
        }
    }
}
