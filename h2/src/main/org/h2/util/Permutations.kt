/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 *
 * According to a mail from Alan Tucker to Chris H Miller from IBM,
 * the algorithm is in the public domain:
 *
 * Date: 2010-07-15 15:57
 * Subject: Re: Applied Combinatorics Code
 *
 * Chris,
 * The combinatorics algorithms in my textbook are all not under patent
 * or copyright. They are as much in the public domain as the solution to any
 * common question in an undergraduate mathematics course, e.g., in my
 * combinatorics course, the solution to the problem of how many arrangements
 * there are of the letters in the word MATHEMATICS. I appreciate your due
 * diligence.
 * -Alan
 */
package org.h2.util

import org.h2.message.DbException

/**
 * A class to iterate over all permutations of an array.
 * The algorithm is from Applied Combinatorics, by Alan Tucker as implemented in
 * http://www.koders.com/java/fidD3445CD11B1DC687F6B8911075E7F01E23171553.aspx
 *
 * @param <T> the element type
 */
class Permutations<T> private constructor(
    private val `in`: Array<T>,
    private val out: Array<T>,
    private val m: Int,
) {

    private val n: Int = `in`.size
    private val index: kotlin.IntArray
    private var hasNext = true

    init {
        if (n < m || m < 0) {
            throw DbException.getInternalError("n < m or m < 0")
        }
        index = kotlin.IntArray(n)
        for (i in 0 until n) {
            index[i] = i
        }

        // The elements from m to n are always kept ascending right to left.
        // This keeps the dip in the interesting region.
        reverseAfter(m - 1)
    }

    /**
     * Move the index forward a notch. The algorithm first finds the rightmost
     * index that is less than its neighbor to the right. This is the dip point.
     * The algorithm next finds the least element to the right of the dip that
     * is greater than the dip. That element is switched with the dip. Finally,
     * the list of elements to the right of the dip is reversed.
     * For example, in a permutation of 5 items, the index may be {1, 2, 4, 3,
     * 0}. The dip is 2 the rightmost element less than its neighbor on its
     * right. The least element to the right of 2 that is greater than 2 is 3.
     * These elements are swapped, yielding {1, 3, 4, 2, 0}, and the list right
     * of the dip point is reversed, yielding {1, 3, 0, 2, 4}.
     */
    private fun moveIndex() {
        // find the index of the first element that dips
        val i = rightmostDip()
        if (i < 0) {
            hasNext = false
            return
        }

        // find the least great element to the right of the dip
        var leastToRightIndex = i + 1
        for (j in i + 2 until n) {
            if (index[j] < index[leastToRightIndex] && index[j] > index[i]) {
                leastToRightIndex = j
            }
        }

        // switch dip element with the least great element to its right
        val t = index[i]
        index[i] = index[leastToRightIndex]
        index[leastToRightIndex] = t

        if (m - 1 > i) {
            // reverse the elements to the right of the dip
            reverseAfter(i)

            // reverse the elements to the right of m - 1
            reverseAfter(m - 1)
        }
    }

    /**
     * Get the index of the first element from the right that is less
     * than its neighbor on the right.
     *
     * @return the index or -1 if non is found
     */
    private fun rightmostDip(): Int {
        for (i in n - 2 downTo 0) {
            if (index[i] < index[i + 1]) {
                return i
            }
        }
        return -1
    }

    /**
     * Reverse the elements to the right of the specified index.
     *
     * @param i the index
     */
    private fun reverseAfter(i: Int) {
        var start = i + 1
        var end = n - 1
        while (start < end) {
            val t = index[start]
            index[start] = index[end]
            index[end] = t
            start++
            end--
        }
    }

    /**
     * Go to the next lineup, and if available, fill the target array.
     *
     * @return if a new lineup is available
     */
    fun next(): Boolean {
        if (!hasNext) {
            return false
        }
        for (i in 0 until m) {
            out[i] = `in`[index[i]]
        }
        moveIndex()
        return true
    }

    companion object {
        /**
         * Create a new permutations object.
         *
         * @param <T> the type
         * @param in the source array
         * @param out the target array
         * @return the generated permutations object
         */
        @JvmStatic
        fun <T> create(`in`: Array<T>, out: Array<T>): Permutations<T> {
            return Permutations(`in`, out, `in`.size)
        }

        /**
         * Create a new permutations object.
         *
         * @param <T> the type
         * @param in the source array
         * @param out the target array
         * @param m the number of output elements to generate
         * @return the generated permutations object
         */
        @JvmStatic
        fun <T> create(`in`: Array<T>, out: Array<T>, m: Int): Permutations<T> {
            return Permutations(`in`, out, m)
        }
    }
}
