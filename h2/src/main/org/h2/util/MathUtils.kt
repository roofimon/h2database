/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.util

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException
import java.lang.reflect.Method
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.concurrent.ThreadLocalRandom

/**
 * This is a utility class with mathematical helper functions.
 */
class MathUtils private constructor() {

    companion object {

        /**
         * The secure random object.
         */
        @JvmField
        var secureRandom: SecureRandom? = null

        /**
         * True if the secure random object is seeded.
         */
        @JvmField
        @Volatile
        var seeded: Boolean = false

        /**
         * Round the value up to the next block size. The block size must be a power
         * of two. As an example, using the block size of 8, the following rounding
         * operations are done: 0 stays 0; values 1..8 results in 8, 9..16 results
         * in 16, and so on.
         *
         * @param x the value to be rounded
         * @param blockSizePowerOf2 the block size
         * @return the rounded value
         */
        @JvmStatic
        fun roundUpInt(x: Int, blockSizePowerOf2: Int): Int {
            return (x + blockSizePowerOf2 - 1) and -blockSizePowerOf2
        }

        /**
         * Round the value up to the next block size. The block size must be a power
         * of two. As an example, using the block size of 8, the following rounding
         * operations are done: 0 stays 0; values 1..8 results in 8, 9..16 results
         * in 16, and so on.
         *
         * @param x the value to be rounded
         * @param blockSizePowerOf2 the block size
         * @return the rounded value
         */
        @JvmStatic
        fun roundUpLong(x: Long, blockSizePowerOf2: Long): Long {
            return (x + blockSizePowerOf2 - 1) and -blockSizePowerOf2
        }

        @Synchronized
        private fun getSecureRandom(): SecureRandom {
            if (secureRandom != null) {
                return secureRandom!!
            }
            // Workaround for SecureRandom problem as described in
            // https://bugs.openjdk.java.net/browse/JDK-6202721
            // Can not do that in a static initializer block, because
            // threads are not started until after the initializer block exits
            try {
                secureRandom = SecureRandom.getInstance("SHA1PRNG")
                // On some systems, secureRandom.generateSeed() is very slow.
                // In this case it is initialized using our own seed implementation
                // and afterward (in the thread) using the regular algorithm.
                val runnable = Runnable {
                    try {
                        val sr = SecureRandom.getInstance("SHA1PRNG")
                        val seed = sr.generateSeed(20)
                        synchronized(secureRandom!!) {
                            secureRandom!!.setSeed(seed)
                            seeded = true
                        }
                    } catch (e: Exception) {
                        // NoSuchAlgorithmException
                        warn("SecureRandom", e)
                    }
                }

                try {
                    val t = Thread(runnable, "Generate Seed")
                    // let the process terminate even if generating the seed is
                    // really slow
                    t.isDaemon = true
                    t.start()
                    Thread.yield()
                    try {
                        // normally, generateSeed takes less than 200 ms
                        t.join(400)
                    } catch (e: InterruptedException) {
                        warn("InterruptedException", e)
                    }
                    if (!seeded) {
                        val seed = generateAlternativeSeed()
                        // this never reduces randomness
                        synchronized(secureRandom!!) {
                            secureRandom!!.setSeed(seed)
                        }
                    }
                } catch (e: SecurityException) {
                    // workaround for the Google App Engine: don't use a thread
                    runnable.run()
                    generateAlternativeSeed()
                }
            } catch (e: Exception) {
                // NoSuchAlgorithmException
                warn("SecureRandom", e)
                secureRandom = SecureRandom()
            }
            return secureRandom!!
        }

        /**
         * Generate a seed value, using as much unpredictable data as possible.
         *
         * @return the seed
         */
        @JvmStatic
        fun generateAlternativeSeed(): ByteArray {
            try {
                val bout = ByteArrayOutputStream()
                val out = DataOutputStream(bout)

                // milliseconds and nanoseconds
                out.writeLong(System.currentTimeMillis())
                out.writeLong(System.nanoTime())

                // memory
                out.writeInt(Any().hashCode())
                val runtime = Runtime.getRuntime()
                out.writeLong(runtime.freeMemory())
                out.writeLong(runtime.maxMemory())
                out.writeLong(runtime.totalMemory())

                // environment
                try {
                    val s = System.getProperties().toString()
                    // can't use writeUTF, as the string
                    // might be larger than 64 KB
                    out.writeInt(s.length)
                    out.write(s.toByteArray(StandardCharsets.UTF_8))
                } catch (e: Exception) {
                    warn("generateAlternativeSeed", e)
                }

                // host name and ip addresses (if any)
                try {
                    // workaround for the Google App Engine: don't use InetAddress
                    val inetAddressClass = Class.forName(
                        "java.net.InetAddress"
                    )
                    val localHost = inetAddressClass.getMethod(
                        "getLocalHost"
                    ).invoke(null)
                    val hostName = inetAddressClass.getMethod(
                        "getHostName"
                    ).invoke(localHost).toString()
                    out.writeUTF(hostName)
                    val list = inetAddressClass.getMethod(
                        "getAllByName", String::class.java
                    ).invoke(null, hostName) as Array<*>
                    val getAddress = inetAddressClass.getMethod(
                        "getAddress"
                    )
                    for (o in list) {
                        out.write(getAddress.invoke(o) as ByteArray)
                    }
                } catch (e: Throwable) {
                    // on some system, InetAddress is not supported
                    // on some system, InetAddress.getLocalHost() doesn't work
                    // for some reason (incorrect configuration)
                }

                // timing (a second thread is already running usually)
                for (j in 0 until 16) {
                    var i = 0
                    val end = System.currentTimeMillis()
                    while (end == System.currentTimeMillis()) {
                        i++
                    }
                    out.writeInt(i)
                }

                out.close()
                return bout.toByteArray()
            } catch (e: IOException) {
                warn("generateAlternativeSeed", e)
                return ByteArray(1)
            }
        }

        /**
         * Print a message to system output if there was a problem initializing the
         * random number generator.
         *
         * @param s the message to print
         * @param t the stack trace
         */
        @JvmStatic
        fun warn(s: String, t: Throwable?) {
            // not a fatal problem, but maybe reduced security
            println("Warning: $s")
            t?.printStackTrace()
        }

        /**
         * Get the value that is equal to or higher than this value, and that is a
         * power of two.
         *
         * @param x the original value
         * @return the next power of two value
         * @throws IllegalArgumentException if x &lt; 0 or x &gt; 0x40000000
         */
        @JvmStatic
        @Throws(IllegalArgumentException::class)
        fun nextPowerOf2(x: Int): Int {
            if (x + Integer.MIN_VALUE > (0x4000_0000 + Integer.MIN_VALUE)) {
                throw IllegalArgumentException(
                    "Argument out of range" +
                            " [0x0-0x40000000]. Argument was: " + x
                )
            }
            return if (x <= 1) 1 else (-1 ushr Integer.numberOfLeadingZeros(x - 1)) + 1
        }

        /**
         * Convert a long value to an int value. Values larger than the biggest int
         * value are converted to the biggest int value, and values smaller than the
         * smallest int value are converted to the smallest int value.
         *
         * @param l the value to convert
         * @return the converted int value
         */
        @JvmStatic
        fun convertLongToInt(l: Long): Int {
            return if (l <= Integer.MIN_VALUE) {
                Integer.MIN_VALUE
            } else if (l >= Integer.MAX_VALUE) {
                Integer.MAX_VALUE
            } else {
                l.toInt()
            }
        }

        /**
         * Convert an int value to a short value. Values larger than the biggest
         * short value are converted to the biggest short value, and values smaller
         * than the smallest short value are converted to the smallest short value.
         *
         * @param i the value to convert
         * @return the converted short value
         */
        @JvmStatic
        fun convertIntToShort(i: Int): Short {
            return if (i <= Short.MIN_VALUE) {
                Short.MIN_VALUE
            } else if (i >= Short.MAX_VALUE) {
                Short.MAX_VALUE
            } else {
                i.toShort()
            }
        }

        /**
         * Get a cryptographically secure pseudo random long value.
         *
         * @return the random long value
         */
        @JvmStatic
        fun secureRandomLong(): Long {
            return getSecureRandom().nextLong()
        }

        /**
         * Get a number of pseudo random bytes.
         *
         * @param bytes the target array
         */
        @JvmStatic
        fun randomBytes(bytes: ByteArray) {
            ThreadLocalRandom.current().nextBytes(bytes)
        }

        /**
         * Get a number of cryptographically secure pseudo random bytes.
         *
         * @param len the number of bytes
         * @return the random bytes
         */
        @JvmStatic
        fun secureRandomBytes(len: Int): ByteArray {
            var len = len
            if (len <= 0) {
                len = 1
            }
            val buff = ByteArray(len)
            getSecureRandom().nextBytes(buff)
            return buff
        }

        /**
         * Get a pseudo random int value between 0 (including) and the given value
         * (excluding). The value is not cryptographically secure.
         *
         * @param lowerThan the value returned will be lower than this value
         * @return the random long value
         */
        @JvmStatic
        fun randomInt(lowerThan: Int): Int {
            return ThreadLocalRandom.current().nextInt(lowerThan)
        }

        /**
         * Get a cryptographically secure pseudo random int value between 0
         * (including) and the given value (excluding).
         *
         * @param lowerThan the value returned will be lower than this value
         * @return the random long value
         */
        @JvmStatic
        fun secureRandomInt(lowerThan: Int): Int {
            return getSecureRandom().nextInt(lowerThan)
        }
    }
}
