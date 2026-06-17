/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.util

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.nio.ByteBuffer

import org.h2.engine.SysProperties

/**
 * Unsafe memory unmapper.
 *
 * @see SysProperties.NIO_CLEANER_HACK
 */
class MemoryUnmapper private constructor() {

    companion object {

        private val ENABLED: Boolean

        private val UNSAFE: Any?

        private val INVOKE_CLEANER: Method?

        init {
            var enabled = SysProperties.NIO_CLEANER_HACK
            var unsafe: Any? = null
            var invokeCleaner: Method? = null
            if (enabled) {
                try {
                    val clazz = Class.forName("sun.misc.Unsafe")
                    val field: Field = clazz.getDeclaredField("theUnsafe")
                    field.isAccessible = true
                    unsafe = field.get(null)
                    // This method exists only on Java 9 and later versions
                    invokeCleaner = clazz.getMethod("invokeCleaner", ByteBuffer::class.java)
                } catch (e: ReflectiveOperationException) {
                    // Java 8
                    unsafe = null
                    // invokeCleaner can be only null here
                } catch (e: Throwable) {
                    // Should be a SecurityException, but catch everything to be
                    // safe
                    enabled = false
                    unsafe = null
                    // invokeCleaner can be only null here
                }
            }
            ENABLED = enabled
            UNSAFE = unsafe
            INVOKE_CLEANER = invokeCleaner
        }

        /**
         * Tries to unmap memory for the specified byte buffer using Java internals
         * in unsafe way if [SysProperties.NIO_CLEANER_HACK] is enabled and
         * access is not denied by a security manager.
         *
         * @param buffer
         *            mapped byte buffer
         * @return whether operation was successful
         */
        @JvmStatic
        fun unmap(buffer: ByteBuffer): Boolean {
            if (!ENABLED) {
                return false
            }
            try {
                if (INVOKE_CLEANER != null) {
                    // Java 9 or later
                    INVOKE_CLEANER.invoke(UNSAFE, buffer)
                    return true
                }
                // Java 8
                val cleanerMethod = buffer.javaClass.getMethod("cleaner")
                cleanerMethod.isAccessible = true
                val cleaner = cleanerMethod.invoke(buffer)
                if (cleaner != null) {
                    val clearMethod = cleaner.javaClass.getMethod("clean")
                    clearMethod.invoke(cleaner)
                }
                return true
            } catch (e: Throwable) {
                return false
            }
        }
    }

}
