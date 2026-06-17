/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.util

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.lang.management.ManagementFactory
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.ArrayList
import java.util.Arrays
import java.util.Comparator
import java.util.HashMap
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ExecutionException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/**
 * This utility class contains miscellaneous functions.
 */
class Utils private constructor() {

    /**
     * The utility methods will try to use the provided class factories to
     * convert binary name of class to Class object. Used by H2 OSGi Activator
     * in order to provide a class from another bundle ClassLoader.
     */
    interface ClassFactory {

        /**
         * Check whether the factory can return the named class.
         *
         * @param name the binary name of the class
         * @return true if this factory can return a valid class for the
         *         provided class name
         */
        fun match(name: String): Boolean

        /**
         * Load the class.
         *
         * @param name the binary name of the class
         * @return the class object
         * @throws ClassNotFoundException If the class is not handle by this
         *             factory
         */
        @Throws(ClassNotFoundException::class)
        fun loadClass(name: String): Class<*>
    }

    companion object {

        /**
         * An 0-size byte array.
         */
        @JvmField
        val EMPTY_BYTES = ByteArray(0)

        /**
         * An 0-size int array.
         */
        @JvmField
        val EMPTY_INT_ARRAY = kotlin.IntArray(0)

        private val RESOURCES = HashMap<String, ByteArray>()

        @JvmField
        val H2_THREAD_GROUP = ThreadGroup("H2-background")

        /**
         * Calculate the index of the first occurrence of the pattern in the byte
         * array, starting with the given index. This methods returns -1 if the
         * pattern has not been found, and the start position if the pattern is
         * empty.
         *
         * @param bytes the byte array
         * @param pattern the pattern
         * @param start the start index from where to search
         * @return the index
         */
        @JvmStatic
        fun indexOf(bytes: ByteArray, pattern: ByteArray, start: Int): Int {
            var start = start
            if (pattern.size == 0) {
                return start
            }
            if (start > bytes.size) {
                return -1
            }
            val last = bytes.size - pattern.size + 1
            val patternLen = pattern.size
            next@ while (start < last) {
                for (i in 0 until patternLen) {
                    if (bytes[start + i] != pattern[i]) {
                        start++
                        continue@next
                    }
                }
                return start
            }
            return -1
        }

        /**
         * Calculate the hash code of the given byte array.
         *
         * @param value the byte array
         * @return the hash code
         */
        @JvmStatic
        fun getByteArrayHash(value: ByteArray): Int {
            var len = value.size
            var h = len
            if (len < 50) {
                for (i in 0 until len) {
                    h = 31 * h + value[i].toInt()
                }
            } else {
                val step = len / 16
                for (i in 0 until 4) {
                    h = 31 * h + value[i].toInt()
                    h = 31 * h + value[--len].toInt()
                }
                var i = 4 + step
                while (i < len) {
                    h = 31 * h + value[i].toInt()
                    i += step
                }
            }
            return h
        }

        /**
         * Compare two byte arrays. This method will always loop over all bytes and
         * doesn't use conditional operations in the loop to make sure an attacker
         * can not use a timing attack when trying out passwords.
         *
         * @param test the first array
         * @param good the second array
         * @return true if both byte arrays contain the same bytes
         */
        @JvmStatic
        fun compareSecure(test: ByteArray?, good: ByteArray?): Boolean {
            if ((test == null) || (good == null)) {
                return (test == null) && (good == null)
            }
            val len = test.size
            if (len != good.size) {
                return false
            }
            if (len == 0) {
                return true
            }
            // don't use conditional operations inside the loop
            var bits = 0
            for (i in 0 until len) {
                // this will never reset any bits
                bits = bits or (test[i].toInt() xor good[i].toInt())
            }
            return bits == 0
        }

        /**
         * Copy the contents of the source array to the target array. If the size if
         * the target array is too small, a larger array is created.
         *
         * @param source the source array
         * @param target the target array
         * @return the target array or a new one if the target array was too small
         */
        @JvmStatic
        fun copy(source: ByteArray, target: ByteArray): ByteArray {
            var target = target
            val len = source.size
            if (len > target.size) {
                target = ByteArray(len)
            }
            System.arraycopy(source, 0, target, 0, len)
            return target
        }

        /**
         * Create an array of bytes with the given size. If this is not possible
         * because not enough memory is available, an OutOfMemoryError with the
         * requested size in the message is thrown.
         *
         * This method should be used if the size of the array is user defined, or
         * stored in a file, so wrong size data can be distinguished from regular
         * out-of-memory.
         *
         * @param len the number of bytes requested
         * @return the byte array
         * @throws OutOfMemoryError if the allocation was too large
         */
        @JvmStatic
        fun newBytes(len: Int): ByteArray {
            if (len == 0) {
                return EMPTY_BYTES
            }
            try {
                return ByteArray(len)
            } catch (e: OutOfMemoryError) {
                val e2: Error = OutOfMemoryError("Requested memory: $len")
                e2.initCause(e)
                throw e2
            }
        }

        /**
         * Creates a copy of array of bytes with the new size. If this is not possible
         * because not enough memory is available, an OutOfMemoryError with the
         * requested size in the message is thrown.
         *
         * This method should be used if the size of the array is user defined, or
         * stored in a file, so wrong size data can be distinguished from regular
         * out-of-memory.
         *
         * @param bytes source array
         * @param len the number of bytes in the new array
         * @return the byte array
         * @throws OutOfMemoryError if the allocation was too large
         * @see Arrays.copyOf
         */
        @JvmStatic
        fun copyBytes(bytes: ByteArray, len: Int): ByteArray {
            if (len == 0) {
                return EMPTY_BYTES
            }
            try {
                return Arrays.copyOf(bytes, len)
            } catch (e: OutOfMemoryError) {
                val e2: Error = OutOfMemoryError("Requested memory: $len")
                e2.initCause(e)
                throw e2
            }
        }

        /**
         * Create a new byte array and copy all the data. If the size of the byte
         * array is zero, the same array is returned.
         *
         * @param b the byte array (may not be null)
         * @return a new byte array
         */
        @JvmStatic
        fun cloneByteArray(b: ByteArray?): ByteArray? {
            if (b == null) {
                return null
            }
            val len = b.size
            if (len == 0) {
                return EMPTY_BYTES
            }
            return Arrays.copyOf(b, len)
        }

        /**
         * Get the used memory in KB.
         * This method possibly calls System.gc().
         *
         * @return the used memory
         */
        @JvmStatic
        fun getMemoryUsed(): Long {
            collectGarbage()
            val rt = Runtime.getRuntime()
            return rt.totalMemory() - rt.freeMemory() shr 10
        }

        /**
         * Get the free memory in KB.
         * This method possibly calls System.gc().
         *
         * @return the free memory
         */
        @JvmStatic
        fun getMemoryFree(): Long {
            collectGarbage()
            return Runtime.getRuntime().freeMemory() shr 10
        }

        /**
         * Get the maximum memory in KB.
         *
         * @return the maximum memory
         */
        @JvmStatic
        fun getMemoryMax(): Long {
            return Runtime.getRuntime().maxMemory() shr 10
        }

        @JvmStatic
        fun getGarbageCollectionTime(): Long {
            var totalGCTime: Long = 0
            for (gcMXBean in ManagementFactory.getGarbageCollectorMXBeans()) {
                val collectionTime = gcMXBean.collectionTime
                if (collectionTime > 0) {
                    totalGCTime += collectionTime
                }
            }
            return totalGCTime
        }

        @JvmStatic
        fun getGarbageCollectionCount(): Long {
            var totalGCCount: Long = 0
            var poolCount = 0
            for (gcMXBean in ManagementFactory.getGarbageCollectorMXBeans()) {
                val collectionCount = gcMXBean.collectionTime
                if (collectionCount > 0) {
                    totalGCCount += collectionCount
                    poolCount += gcMXBean.memoryPoolNames.size
                }
            }
            poolCount = Math.max(poolCount, 1)
            return (totalGCCount + (poolCount shr 1)) / poolCount
        }

        /**
         * Run Java memory garbage collection.
         */
        @JvmStatic
        @Synchronized
        fun collectGarbage() {
            val runtime = Runtime.getRuntime()
            val garbageCollectionCount = getGarbageCollectionCount()
            while (garbageCollectionCount == getGarbageCollectionCount()) {
                runtime.gc()
                Thread.yield()
            }
        }

        /**
         * Create a new ArrayList with an initial capacity of 4.
         *
         * @param <T> the type
         * @return the object
         */
        @JvmStatic
        fun <T> newSmallArrayList(): ArrayList<T> {
            return ArrayList(4)
        }

        /**
         * Find the top limit values using given comparator and place them as in a
         * full array sort, in descending order.
         *
         * @param <X> the type of elements
         * @param array the array.
         * @param fromInclusive the start index, inclusive
         * @param toExclusive the end index, exclusive
         * @param comp the comparator.
         */
        @JvmStatic
        fun <X> sortTopN(array: Array<X>, fromInclusive: Int, toExclusive: Int, comp: Comparator<in X>) {
            val highInclusive = array.size - 1
            if (highInclusive > 0 && toExclusive > fromInclusive) {
                partialQuickSort(array, 0, highInclusive, comp, fromInclusive, toExclusive - 1)
                Arrays.sort(array, fromInclusive, toExclusive, comp)
            }
        }

        /**
         * Partial quick sort.
         *
         *
         * Works with elements from `low` to `high` indexes, inclusive.
         *
         *
         * Moves smallest elements to `low..start-1` positions and largest
         * elements to `end+1..high` positions. Middle elements are placed
         * into `start..end` positions. All these regions aren't fully sorted.
         *
         *
         * @param <X> the type of elements
         * @param array the array to sort
         * @param low the lower index with data, inclusive
         * @param high the higher index with data, inclusive, `high > low`
         * @param comp the comparator
         * @param start the start index of requested region, inclusive
         * @param end the end index of requested region, inclusive, `end >= start`
         */
        private fun <X> partialQuickSort(
            array: Array<X>, low: Int, high: Int,
            comp: Comparator<in X>, start: Int, end: Int
        ) {
            if (low >= start && high <= end) {
                // Don't sort blocks entirely contained in the middle region
                return
            }
            var i = low
            var j = high
            // use a random pivot to protect against
            // the worst case order
            val p = low + MathUtils.randomInt(high - low)
            val pivot = array[p]
            val m = (low + high) ushr 1
            var temp = array[m]
            array[m] = pivot
            array[p] = temp
            while (i <= j) {
                while (comp.compare(array[i], pivot) < 0) {
                    i++
                }
                while (comp.compare(array[j], pivot) > 0) {
                    j--
                }
                if (i <= j) {
                    temp = array[i]
                    array[i++] = array[j]
                    array[j--] = temp
                }
            }
            if (low < j && /* Intersection with middle region */ start <= j) {
                partialQuickSort(array, low, j, comp, start, end)
            }
            if (i < high && /* Intersection with middle region */ i <= end) {
                partialQuickSort(array, i, high, comp, start, end)
            }
        }

        /**
         * Get a resource from the resource map.
         *
         * @param name the name of the resource
         * @return the resource data
         * @throws IOException on failure
         */
        @JvmStatic
        @Throws(IOException::class)
        fun getResource(name: String): ByteArray? {
            var data = RESOURCES[name]
            if (data == null) {
                data = loadResource(name)
                if (data != null) {
                    RESOURCES[name] = data
                }
            }
            return data
        }

        @Throws(IOException::class)
        private fun loadResource(name: String): ByteArray? {
            var input = Utils::class.java.getResourceAsStream("data.zip")
            if (input == null) {
                input = Utils::class.java.getResourceAsStream(name)
                if (input == null) {
                    return null
                }
                return IOUtils.readBytesAndClose(input, 0)
            }

            try {
                ZipInputStream(input).use { zipIn ->
                    while (true) {
                        val entry = zipIn.nextEntry ?: break
                        var entryName = entry.name
                        if (!entryName.startsWith("/")) {
                            entryName = "/$entryName"
                        }
                        if (entryName == name) {
                            val out = ByteArrayOutputStream()
                            IOUtils.copy(zipIn, out)
                            zipIn.closeEntry()
                            return out.toByteArray()
                        }
                        zipIn.closeEntry()
                    }
                }
            } catch (e: IOException) {
                // if this happens we have a real problem
                e.printStackTrace()
            }
            return null
        }

        /**
         * Calls a static method via reflection. This will try to use the method
         * where the most parameter classes match exactly (this algorithm is simpler
         * than the one in the Java specification, but works well for most cases).
         *
         * @param classAndMethod a string with the entire class and method name, eg.
         *            "java.lang.System.gc"
         * @param params the method parameters
         * @return the return value from this call
         * @throws Exception on failure
         */
        @JvmStatic
        @Throws(Exception::class)
        fun callStaticMethod(classAndMethod: String, vararg params: Any?): Any? {
            val lastDot = classAndMethod.lastIndexOf('.')
            val className = classAndMethod.substring(0, lastDot)
            val methodName = classAndMethod.substring(lastDot + 1)
            return callMethod(null, Class.forName(className), methodName, *params)
        }

        /**
         * Calls an instance method via reflection. This will try to use the method
         * where the most parameter classes match exactly (this algorithm is simpler
         * than the one in the Java specification, but works well for most cases).
         *
         * @param instance the instance on which the call is done
         * @param methodName a string with the method name
         * @param params the method parameters
         * @return the return value from this call
         * @throws Exception on failure
         */
        @JvmStatic
        @Throws(Exception::class)
        fun callMethod(
            instance: Any,
            methodName: String,
            vararg params: Any?
        ): Any? {
            return callMethod(instance, instance.javaClass, methodName, *params)
        }

        @Throws(Exception::class)
        private fun callMethod(
            instance: Any?, clazz: Class<*>,
            methodName: String,
            vararg params: Any?
        ): Any? {
            var best: Method? = null
            var bestMatch = 0
            val isStatic = instance == null
            for (m in clazz.methods) {
                if (Modifier.isStatic(m.modifiers) == isStatic &&
                    m.name == methodName
                ) {
                    val p = match(m.parameterTypes, params)
                    if (p > bestMatch) {
                        bestMatch = p
                        best = m
                    }
                }
            }
            if (best == null) {
                throw NoSuchMethodException(methodName)
            }
            return best.invoke(instance, *params)
        }

        /**
         * Creates a new instance. This will try to use the constructor where the
         * most parameter classes match exactly (this algorithm is simpler than the
         * one in the Java specification, but works well for most cases).
         *
         * @param className a string with the entire class, eg. "java.lang.Integer"
         * @param params the constructor parameters
         * @return the newly created object
         * @throws Exception on failure
         */
        @JvmStatic
        @Throws(Exception::class)
        fun newInstance(className: String, vararg params: Any?): Any? {
            var best: Constructor<*>? = null
            var bestMatch = 0
            for (c in Class.forName(className).constructors) {
                val p = match(c.parameterTypes, params)
                if (p > bestMatch) {
                    bestMatch = p
                    best = c
                }
            }
            if (best == null) {
                throw NoSuchMethodException(className)
            }
            return best.newInstance(*params)
        }

        private fun match(params: Array<Class<*>>, values: Array<out Any?>): Int {
            val len = params.size
            if (len == values.size) {
                var points = 1
                for (i in 0 until len) {
                    val pc = getNonPrimitiveClass(params[i])
                    val v = values[i]
                    val vc = if (v == null) null else v.javaClass
                    if (pc == vc) {
                        points++
                    } else if (vc == null) {
                        // can't verify
                    } else if (!pc.isAssignableFrom(vc)) {
                        return 0
                    }
                }
                return points
            }
            return 0
        }

        /**
         * Convert primitive class names to java.lang.* class names.
         *
         * @param clazz the class (for example: int)
         * @return the non-primitive class (for example: java.lang.Integer)
         */
        @JvmStatic
        fun getNonPrimitiveClass(clazz: Class<*>): Class<*> {
            if (!clazz.isPrimitive) {
                return clazz
            } else if (clazz == java.lang.Boolean.TYPE) {
                return java.lang.Boolean::class.java
            } else if (clazz == java.lang.Byte.TYPE) {
                return java.lang.Byte::class.java
            } else if (clazz == java.lang.Character.TYPE) {
                return java.lang.Character::class.java
            } else if (clazz == java.lang.Double.TYPE) {
                return java.lang.Double::class.java
            } else if (clazz == java.lang.Float.TYPE) {
                return java.lang.Float::class.java
            } else if (clazz == java.lang.Integer.TYPE) {
                return java.lang.Integer::class.java
            } else if (clazz == java.lang.Long.TYPE) {
                return java.lang.Long::class.java
            } else if (clazz == java.lang.Short.TYPE) {
                return java.lang.Short::class.java
            } else if (clazz == java.lang.Void.TYPE) {
                return java.lang.Void::class.java
            }
            return clazz
        }

        /**
         * Parses the specified string to boolean value.
         *
         * @param value
         *            string to parse
         * @param defaultValue
         *            value to return if value is null or on parsing error
         * @param throwException
         *            throw exception on parsing error or return default value instead
         * @return parsed or default value
         * @throws IllegalArgumentException
         *             on parsing error if `throwException` is true
         */
        @JvmStatic
        fun parseBoolean(value: String?, defaultValue: Boolean, throwException: Boolean): Boolean {
            if (value == null) {
                return defaultValue
            }
            when (value.length) {
                1 -> {
                    if (value == "1" || value.equals("t", ignoreCase = true) || value.equals("y", ignoreCase = true)) {
                        return true
                    }
                    if (value == "0" || value.equals("f", ignoreCase = true) || value.equals("n", ignoreCase = true)) {
                        return false
                    }
                }
                2 -> {
                    if (value.equals("no", ignoreCase = true)) {
                        return false
                    }
                }
                3 -> {
                    if (value.equals("yes", ignoreCase = true)) {
                        return true
                    }
                }
                4 -> {
                    if (value.equals("true", ignoreCase = true)) {
                        return true
                    }
                }
                5 -> {
                    if (value.equals("false", ignoreCase = true)) {
                        return false
                    }
                }
            }
            if (throwException) {
                throw IllegalArgumentException(value)
            }
            return defaultValue
        }

        /**
         * Get the system property. If the system property is not set, or if a
         * security exception occurs, the default value is returned.
         *
         * @param key the key
         * @param defaultValue the default value
         * @return the value
         */
        @JvmStatic
        fun getProperty(key: String, defaultValue: String?): String? {
            try {
                return System.getProperty(key, defaultValue)
            } catch (se: SecurityException) {
                return defaultValue
            }
        }

        /**
         * Get the system property. If the system property is not set, or if a
         * security exception occurs, the default value is returned.
         *
         * @param key the key
         * @param defaultValue the default value
         * @return the value
         */
        @JvmStatic
        fun getProperty(key: String, defaultValue: Int): Int {
            val s = getProperty(key, null)
            if (s != null) {
                try {
                    return Integer.decode(s)
                } catch (e: NumberFormatException) {
                    // ignore
                }
            }
            return defaultValue
        }

        /**
         * Get the system property. If the system property is not set, or if a
         * security exception occurs, the default value is returned.
         *
         * @param key the key
         * @param defaultValue the default value
         * @return the value
         */
        @JvmStatic
        fun getProperty(key: String, defaultValue: Boolean): Boolean {
            return parseBoolean(getProperty(key, null), defaultValue, false)
        }

        /**
         * Scale the value with the available memory. If 1 GB of RAM is available,
         * the value is returned, if 2 GB are available, then twice the value, and
         * so on.
         *
         * @param value the value to scale
         * @return the scaled value
         */
        @JvmStatic
        fun scaleForAvailableMemory(value: Int): Int {
            val maxMemory = Runtime.getRuntime().maxMemory()
            if (maxMemory != Long.MAX_VALUE) {
                // we are limited by an -XmX parameter
                return (value * maxMemory / (1024 * 1024 * 1024)).toInt()
            }
            try {
                val mxBean = ManagementFactory.getOperatingSystemMXBean()
                // this method is only available on the class
                // com.sun.management.OperatingSystemMXBean, which mxBean
                // is an instance of under the Oracle JDK, but it is not present on
                // Android and other JDK's
                val method = Class.forName(
                    "com.sun.management.OperatingSystemMXBean"
                ).getMethod("getTotalPhysicalMemorySize")
                val physicalMemorySize = (method.invoke(mxBean) as Number).toLong()
                return (value * physicalMemorySize / (1024 * 1024 * 1024)).toInt()
            } catch (e: Exception) {
                // ignore
            } catch (error: Error) {
                // ignore
            }
            return value
        }

        /**
         * Returns the current value of the high-resolution time source.
         *
         * @return time in nanoseconds, never equal to 0
         * @see System.nanoTime
         */
        @JvmStatic
        fun currentNanoTime(): Long {
            var time = System.nanoTime()
            if (time == 0L) {
                time = 1L
            }
            return time
        }

        /**
         * Returns the current value of the high-resolution time source plus the
         * specified offset.
         *
         * @param ms
         *            additional offset in milliseconds
         * @return time in nanoseconds, never equal to 0
         * @see System.nanoTime
         */
        @JvmStatic
        fun currentNanoTimePlusMillis(ms: Int): Long {
            return nanoTimePlusMillis(System.nanoTime(), ms)
        }

        /**
         * Returns the current value of the high-resolution time source plus the
         * specified offset.
         *
         * @param nanoTime
         *            time in nanoseconds
         * @param ms
         *            additional offset in milliseconds
         * @return time in nanoseconds, never equal to 0
         * @see System.nanoTime
         */
        @JvmStatic
        fun nanoTimePlusMillis(nanoTime: Long, ms: Int): Long {
            var time = nanoTime + ms * 1_000_000L
            if (time == 0L) {
                time = 1L
            }
            return time
        }

        @JvmStatic
        fun createSingleThreadExecutor(threadName: String): ThreadPoolExecutor {
            return createSingleThreadExecutor(threadName, LinkedBlockingQueue())
        }

        @JvmStatic
        fun createSingleThreadExecutor(threadName: String, workQueue: BlockingQueue<Runnable>): ThreadPoolExecutor {
            return ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS, workQueue
            ) { r -> createBackgroundThread(threadName, r) }
        }

        @JvmStatic
        fun createBackgroundThread(threadName: String, r: Runnable): Thread {
            val thread = Thread(H2_THREAD_GROUP, r, threadName)
            thread.isDaemon = true
            return thread
        }

        /**
         * Makes sure that all currently submitted tasks are processed before this method returns.
         * It is assumed that there will be no new submissions to this executor, once this method has started.
         * It is assumed that executor is single-threaded, and flush is done by submitting a dummy task
         * and waiting for its completion.
         * @param executor to flush
         */
        @JvmStatic
        fun flushExecutor(executor: ThreadPoolExecutor?) {
            if (executor != null) {
                try {
                    executor.submit { }.get()
                } catch (ignore: InterruptedException) {
                    /**/
                } catch (ex: RejectedExecutionException) {
                    shutdownExecutor(executor)
                } catch (e: ExecutionException) {
                    throw RuntimeException(e)
                }
            }
        }

        @JvmStatic
        fun shutdownExecutor(executor: ThreadPoolExecutor?) {
            if (executor != null) {
                executor.shutdown()
                try {
                    executor.awaitTermination(1, TimeUnit.DAYS)
                } catch (ignore: InterruptedException) {
                    /**/
                }
            }
        }

        @JvmStatic
        fun isBackgroundThread(): Boolean {
            return Thread.currentThread().threadGroup === H2_THREAD_GROUP
        }
    }
}
