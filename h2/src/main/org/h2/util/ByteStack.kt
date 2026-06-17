/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.util

import java.util.Arrays
import java.util.NoSuchElementException

/**
 * The stack of byte values. This class is not synchronized and should not be
 * used by multiple threads concurrently.
 */
class ByteStack {

    private var size = 0

    private var array: ByteArray = Utils.EMPTY_BYTES

    /**
     * Pushes an item onto the top of this stack.
     *
     * @param item
     *            the item to push
     */
    fun push(item: Byte) {
        val index = size
        val oldLength = array.size
        if (index >= oldLength) {
            grow(oldLength)
        }
        array[index] = item
        size = index + 1
    }

    /**
     * Removes the item at the top of this stack and returns that item.
     *
     * @return the item at the top of this stack
     * @throws NoSuchElementException
     *             if stack is empty
     */
    fun pop(): Byte {
        val index = size - 1
        if (index < 0) {
            throw NoSuchElementException()
        }
        size = index
        return array[index]
    }

    /**
     * Removes the item at the top of this stack and returns that item.
     *
     * @param defaultValue
     *            value to return if stack is empty
     * @return the item at the top of this stack, or default value
     */
    fun poll(defaultValue: Int): Int {
        val index = size - 1
        if (index < 0) {
            return defaultValue
        }
        size = index
        return array[index].toInt()
    }

    /**
     * Looks at the item at the top of this stack without removing it.
     *
     * @param defaultValue
     *            value to return if stack is empty
     * @return the item at the top of this stack, or default value
     */
    fun peek(defaultValue: Int): Int {
        val index = size - 1
        if (index < 0) {
            return defaultValue
        }
        return array[index].toInt()
    }

    /**
     * Returns `true` if this stack is empty.
     *
     * @return `true` if this stack is empty
     */
    fun isEmpty(): Boolean {
        return size == 0
    }

    /**
     * Returns the number of items in this stack.
     *
     * @return the number of items in this stack
     */
    fun size(): Int {
        return size
    }

    private fun grow(length: Int) {
        var length = length
        if (length == 0) {
            length = 0x10
        } else if (length >= MAX_ARRAY_SIZE) {
            throw OutOfMemoryError()
        } else if ((length shl 1).also { length = it } < 0) {
            length = MAX_ARRAY_SIZE
        }
        array = Arrays.copyOf(array, length)
    }

    companion object {
        private const val MAX_ARRAY_SIZE = Int.MAX_VALUE - 8
    }
}
