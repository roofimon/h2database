/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.util

import java.util.concurrent.atomic.AtomicLong
import org.h2.engine.Constants.MEMORY_POINTER
import org.h2.mvstore.type.DataType

/**
 * Class MemoryEstimator.
 *
 * Calculation of the amount of memory occupied by keys, values and pages of the MVTable
 * may become expensive operation for complex data types like Row.
 * On the other hand, result of the calculation is used by page cache to limit it's size
 * and determine when eviction is needed. Another usage is to trigger auto commit,
 * based on amount of unsaved changes. In both cases reasonable (lets say ~30%) approximation
 * would be good enough and will do the job.
 * This class replaces exact calculation with an estimate based on
 * a sliding window average of last 256 values.
 * If estimation gets close to the exact value, then next N calculations are skipped
 * and replaced with the estimate, where N depends on the estimation error.
 *
 * @author <a href='mailto:andrei.tokar@gmail.com'>Andrei Tokar</a>
 */
class MemoryEstimator private constructor() {

    companion object {
        // Structure of statsData long value:
        // 0 - 7   skip counter (how many more requests will skip calculation and use an estimate instead)
        // 8 - 23  total number of skips between last 256 calculations
        //         (used for sampling percentage calculation only)
        // 24      bit is 0 when window is not completely filled yet, 1 once it become full
        // 25 - 31 unused
        // 32 - 63 sliding window sum of estimated values

        private const val SKIP_SUM_SHIFT = 8
        private const val COUNTER_MASK = (1 shl SKIP_SUM_SHIFT) - 1
        private const val SKIP_SUM_MASK = 0xFFFF
        private const val INIT_BIT_SHIFT = 24
        private const val INIT_BIT = 1 shl INIT_BIT_SHIFT
        private const val WINDOW_SHIFT = 8
        private const val MAGNITUDE_LIMIT = WINDOW_SHIFT - 1
        private const val WINDOW_SIZE = 1 shl WINDOW_SHIFT
        private const val WINDOW_HALF_SIZE = WINDOW_SIZE shr 1
        private const val SUM_SHIFT = 32

        /**
         * Estimates memory size of the data based on previous values.
         * @param stats AtomicLong holding statistical data about the estimated sequence
         * @param dataType used for calculation of the next sequence value, if necessary
         * @param data which size is to be calculated as the next sequence value, if necessary
         * @param <T> type of the data
         * @return next estimated or calculated value of the sequence
         */
        @JvmStatic
        fun <T> estimateMemory(stats: AtomicLong, dataType: DataType<T>, data: T?): Int {
            val statsData = stats.get()
            var counter = getCounter(statsData)
            var skipSum = getSkipSum(statsData)
            var initialized = statsData and INIT_BIT.toLong()
            var sum = statsData ushr SUM_SHIFT
            var mem = 0
            var cnt = 0
            if (initialized == 0L || counter-- == 0) {
                cnt = 1
                mem = if (data == null) 0 else dataType.getMemory(data)
                var delta = (mem.toLong() shl WINDOW_SHIFT) - sum
                if (initialized == 0L) {
                    if (++counter == WINDOW_SIZE) {
                        initialized = INIT_BIT.toLong()
                    }
                    sum = (sum * counter + delta + (counter shr 1)) / counter
                } else {
                    val absDelta = if (delta >= 0) delta else -delta
                    val magnitude = calculateMagnitude(sum, absDelta)
                    sum += ((delta shr (MAGNITUDE_LIMIT - magnitude)) + 1) shr 1
                    counter = ((1 shl magnitude) - 1) and COUNTER_MASK

                    delta = ((counter shl WINDOW_SHIFT) - skipSum).toLong()
                    skipSum += ((delta + WINDOW_HALF_SIZE) shr WINDOW_SHIFT).toInt()
                }
            }
            val updatedStatsData = updateStatsData(stats, statsData, counter, skipSum, initialized, sum, cnt, mem)
            return getAverage(updatedStatsData)
        }

        /**
         * Estimates memory size of the data set based on previous values.
         * @param stats AtomicLong holding statistical data about the estimated sequence
         * @param dataType used for calculation of the next sequence value, if necessary
         * @param storage of the data set, which size is to be calculated
         * @param count number of data items in the storage
         * @param <T> type of the data in the storage
         * @return next estimated or calculated size of the storage
         */
        @JvmStatic
        fun <T> estimateMemory(stats: AtomicLong, dataType: DataType<T>, storage: Array<T>, count: Int): Int {
            val statsData = stats.get()
            var counter = getCounter(statsData)
            var skipSum = getSkipSum(statsData)
            var initialized = statsData and INIT_BIT.toLong()
            var sum = statsData ushr SUM_SHIFT
            var index = 0
            var memSum = 0
            if (initialized != 0L && counter >= count) {
                counter -= count
            } else {
                var cnt = count
                while (cnt-- > 0) {
                    val data = storage[index++]
                    val mem = if (data == null) 0 else dataType.getMemory(data)
                    memSum += mem
                    var delta = (mem.toLong() shl WINDOW_SHIFT) - sum
                    if (initialized == 0L) {
                        if (++counter == WINDOW_SIZE) {
                            initialized = INIT_BIT.toLong()
                        }
                        sum = (sum * counter + delta + (counter shr 1)) / counter
                    } else {
                        cnt -= counter
                        val absDelta = if (delta >= 0) delta else -delta
                        val magnitude = calculateMagnitude(sum, absDelta)
                        sum += ((delta shr (MAGNITUDE_LIMIT - magnitude)) + 1) shr 1
                        counter += ((1 shl magnitude) - 1) and COUNTER_MASK

                        delta = (counter.toLong() shl WINDOW_SHIFT) - skipSum
                        skipSum += ((delta + WINDOW_HALF_SIZE) shr WINDOW_SHIFT).toInt()
                    }
                }
            }
            val updatedStatsData = updateStatsData(stats, statsData, counter, skipSum, initialized, sum, index, memSum)
            return (getAverage(updatedStatsData) + MEMORY_POINTER) * count
        }

        /**
         * Calculates percentage of how many times actual calculation happened (vs. estimation)
         * @param stats AtomicLong holding statistical data about the estimated sequence
         * @return sampling percentage in range 0 - 100
         */
        @JvmStatic
        fun samplingPct(stats: AtomicLong): Int {
            val statsData = stats.get()
            val count = if (statsData and INIT_BIT.toLong() == 0L) getCounter(statsData) else WINDOW_SIZE
            val total = getSkipSum(statsData) + count
            return (count * 100 + (total shr 1)) / total
        }

        private fun calculateMagnitude(sum: Long, absDelta: Long): Int {
            var ad = absDelta
            var magnitude = 0
            while (ad < sum && magnitude < MAGNITUDE_LIMIT) {
                ++magnitude
                ad = ad shl 1
            }
            return magnitude
        }

        private fun updateStatsData(
            stats: AtomicLong, statsData: Long,
            counter: Int, skipSum: Int, initialized: Long, sum: Long,
            itemsCount: Int, itemsMem: Int,
        ): Long {
            return updateStatsData(
                stats, statsData,
                constructStatsData(sum, initialized, skipSum, counter), itemsCount, itemsMem
            )
        }

        private fun constructStatsData(sum: Long, initialized: Long, skipSum: Int, counter: Int): Long {
            return (sum shl SUM_SHIFT) or initialized or (skipSum.toLong() shl SKIP_SUM_SHIFT) or counter.toLong()
        }

        private fun updateStatsData(
            stats: AtomicLong, statsData: Long, updatedStatsData: Long,
            itemsCount: Int, itemsMem: Int,
        ): Long {
            var sd = statsData
            var updated = updatedStatsData
            while (!stats.compareAndSet(sd, updated)) {
                sd = stats.get()
                var sum = sd ushr SUM_SHIFT
                if (itemsCount > 0) {
                    sum += itemsMem - ((sum * itemsCount + WINDOW_HALF_SIZE) shr WINDOW_SHIFT)
                }
                updated = (sum shl SUM_SHIFT) or (sd and (INIT_BIT or SKIP_SUM_MASK or COUNTER_MASK).toLong())
            }
            return updated
        }

        private fun getCounter(statsData: Long): Int {
            return (statsData and COUNTER_MASK.toLong()).toInt()
        }

        private fun getSkipSum(statsData: Long): Int {
            return ((statsData shr SKIP_SUM_SHIFT) and SKIP_SUM_MASK.toLong()).toInt()
        }

        private fun getAverage(updatedStatsData: Long): Int {
            return (updatedStatsData ushr (SUM_SHIFT + WINDOW_SHIFT)).toInt()
        }
    }
}
