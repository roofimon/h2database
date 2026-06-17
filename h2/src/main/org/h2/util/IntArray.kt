/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.util

import java.util.Arrays

/**
 * An array with integer element.
 */
class IntArray {

    private var data: kotlin.IntArray
    private var size = 0
    private var hash = 0

    /**
     * Create an int array with specified initial capacity.
     *
     * @param capacity the initial capacity
     */
    constructor(capacity: Int) {
        data = kotlin.IntArray(capacity)
    }

    /**
     * Create an int array with the default initial capacity.
     */
    constructor() : this(10)

    /**
     * Create an int array with the given values and size.
     *
     * @param data the int array
     */
    constructor(data: kotlin.IntArray) {
        this.data = data
        size = data.size
    }

    /**
     * Append a value.
     *
     * @param value the value to append
     */
    fun add(value: Int) {
        if (size >= data.size) {
            ensureCapacity(size + size)
        }
        data[size++] = value
    }

    /**
     * Get the value at the given index.
     *
     * @param index the index
     * @return the value
     */
    fun get(index: Int): Int {
        if (index >= size) {
            throw ArrayIndexOutOfBoundsException("i=$index size=$size")
        }
        return data[index]
    }

    /**
     * Remove the value at the given index.
     *
     * @param index the index
     */
    fun remove(index: Int) {
        if (index >= size) {
            throw ArrayIndexOutOfBoundsException("i=$index size=$size")
        }
        System.arraycopy(data, index + 1, data, index, size - index - 1)
        size--
    }

    /**
     * Ensure the underlying array is large enough for the given number of
     * entries.
     *
     * @param minCapacity the minimum capacity
     */
    fun ensureCapacity(minCapacity: Int) {
        val cap = Math.max(4, minCapacity)
        if (cap >= data.size) {
            data = Arrays.copyOf(data, cap)
        }
    }

    override fun equals(obj: Any?): Boolean {
        if (obj !is IntArray) {
            return false
        }
        val other = obj
        if (hashCode() != other.hashCode() || size != other.size) {
            return false
        }
        for (i in 0 until size) {
            if (data[i] != other.data[i]) {
                return false
            }
        }
        return true
    }

    override fun hashCode(): Int {
        if (hash != 0) {
            return hash
        }
        var h = size + 1
        for (i in 0 until size) {
            h = h * 31 + data[i]
        }
        hash = h
        return h
    }

    /**
     * Get the size of the list.
     *
     * @return the size
     */
    fun size(): Int {
        return size
    }

    /**
     * Convert this list to an array. The target array must be big enough.
     *
     * @param array the target array
     */
    fun toArray(array: kotlin.IntArray) {
        System.arraycopy(data, 0, array, 0, size)
    }

    override fun toString(): String {
        val builder = StringBuilder("{")
        for (i in 0 until size) {
            if (i > 0) {
                builder.append(", ")
            }
            builder.append(data[i])
        }
        return builder.append('}').toString()
    }

    /**
     * Remove a number of elements.
     *
     * @param fromIndex the index of the first item to remove
     * @param toIndex upper bound (exclusive)
     */
    fun removeRange(fromIndex: Int, toIndex: Int) {
        if (fromIndex > toIndex || toIndex > size) {
            throw ArrayIndexOutOfBoundsException("from=$fromIndex to=$toIndex size=$size")
        }
        System.arraycopy(data, toIndex, data, fromIndex, size - toIndex)
        size -= toIndex - fromIndex
    }
}
