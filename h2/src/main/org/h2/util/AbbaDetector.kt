/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.util

import java.util.ArrayDeque
import java.util.Deque
import java.util.HashSet
import java.util.WeakHashMap

/**
 * Utility to detect AB-BA deadlocks.
 */
class AbbaDetector {

    companion object {
        private const val TRACE = false

        private val STACK: ThreadLocal<Deque<Any>> = ThreadLocal.withInitial { ArrayDeque() }

        private val STACK_WALKER =
            StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)

        /**
         * Map of (object A) -> (
         *      map of (object locked before object A) ->
         *      (stack trace where locked) )
         */
        private val LOCK_ORDERING: MutableMap<Any, MutableMap<Any, Exception>> = WeakHashMap()

        private val KNOWN_DEADLOCKS: MutableSet<String> = HashSet()

        /**
         * This method is called just before or just after an object is
         * synchronized.
         *
         * @param o the object, or null for the current class
         * @return the object that was passed
         */
        @JvmStatic
        fun begin(o: Any?): Any {
            var o: Any = o ?: STACK_WALKER.callerClass
            val stack = STACK.get()
            if (!stack.isEmpty()) {
                // Ignore locks which are locked multiple times in succession -
                // Java locks are recursive
                if (stack.contains(o)) {
                    // already synchronized on this
                    return o
                }
                while (!stack.isEmpty()) {
                    val last = stack.peek()
                    if (Thread.holdsLock(last)) {
                        break
                    }
                    stack.pop()
                }
            }
            if (TRACE) {
                val thread = "[thread " + Thread.currentThread().id + "]"
                val indent = String(CharArray(stack.size * 2)).replace(0.toChar(), ' ')
                println(thread + " " + indent + "sync " + getObjectName(o))
            }
            if (!stack.isEmpty()) {
                markHigher(o, stack)
            }
            stack.push(o)
            return o
        }

        private fun getTest(o: Any): Any {
            // return o.getClass();
            return o
        }

        private fun getObjectName(o: Any): String {
            return o.javaClass.simpleName + "@" + System.identityHashCode(o)
        }

        @JvmStatic
        @Synchronized
        private fun markHigher(o: Any, older: Deque<Any>) {
            val test = getTest(o)
            var map = LOCK_ORDERING[test]
            if (map == null) {
                map = WeakHashMap()
                LOCK_ORDERING[test] = map
            }
            var oldException: Exception? = null
            for (old in older) {
                val oldTest = getTest(old)
                if (oldTest === test) {
                    continue
                }
                val oldMap = LOCK_ORDERING[oldTest]
                if (oldMap != null) {
                    val e = oldMap[test]
                    if (e != null) {
                        val deadlockType = test.javaClass.toString() + " " + oldTest.javaClass
                        if (!KNOWN_DEADLOCKS.contains(deadlockType)) {
                            val message = getObjectName(test) +
                                    " synchronized after \n " + getObjectName(oldTest) +
                                    ", but in the past before"
                            val ex = RuntimeException(message)
                            ex.initCause(e)
                            ex.printStackTrace(System.out)
                            // throw ex;
                            KNOWN_DEADLOCKS.add(deadlockType)
                        }
                    }
                }
                if (!map.containsKey(oldTest)) {
                    if (oldException == null) {
                        oldException = Exception("Before")
                    }
                    map[oldTest] = oldException
                }
            }
        }
    }
}
