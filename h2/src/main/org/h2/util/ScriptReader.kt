/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.util

import java.io.Closeable
import java.io.IOException
import java.io.Reader
import java.util.Arrays
import org.h2.engine.Constants
import org.h2.message.DbException

/**
 * This class can split SQL scripts to single SQL statements.
 * Each SQL statement ends with the character ';', however it is ignored
 * in comments and quotes.
 */
class ScriptReader(private val reader: Reader) : Closeable {

    private var buffer: CharArray = CharArray(Constants.IO_BUFFER_SIZE * 2)

    /**
     * The position in the buffer of the next char to be read
     */
    private var bufferPos = 0

    /**
     * The position in the buffer of the statement start
     */
    private var bufferStart = -1

    /**
     * The position in the buffer of the last available char
     */
    private var bufferEnd = 0

    /**
     * True if we have read past the end of file
     */
    private var endOfFile = false

    /**
     * True if we are inside a comment
     */
    private var insideRemark = false

    /**
     * Only valid if insideRemark is true. True if we are inside a block
     * comment, false if we are inside a line comment
     */
    private var blockRemark = false

    /**
     * True if comments should be skipped completely by this reader.
     */
    private var skipRemarks = false

    /**
     * The position in buffer of start of comment
     */
    private var remarkStart = 0

    /**
     * Close the underlying reader.
     */
    override fun close() {
        try {
            reader.close()
        } catch (e: IOException) {
            throw DbException.convertIOException(e, null)
        }
    }

    /**
     * Read a statement from the reader. This method returns null if the end has
     * been reached.
     *
     * @return the SQL statement or null
     */
    fun readStatement(): String? {
        if (endOfFile) {
            return null
        }
        try {
            return readStatementLoop()
        } catch (e: IOException) {
            throw DbException.convertIOException(e, null)
        }
    }

    @Throws(IOException::class)
    private fun readStatementLoop(): String? {
        bufferStart = bufferPos
        var c = read()
        while (true) {
            if (c < 0) {
                endOfFile = true
                if (bufferPos - 1 == bufferStart) {
                    return null
                }
                break
            } else if (c == ';'.code) {
                break
            }
            when (c) {
                '$'.code -> {
                    c = read()
                    if (c == '$'.code && (bufferPos - bufferStart < 3 || buffer[bufferPos - 3] <= ' ')) {
                        // dollar quoted string
                        while (true) {
                            c = read()
                            if (c < 0) {
                                break
                            }
                            if (c == '$'.code) {
                                c = read()
                                if (c < 0) {
                                    break
                                }
                                if (c == '$'.code) {
                                    break
                                }
                            }
                        }
                        c = read()
                    }
                }
                '\''.code -> {
                    while (true) {
                        c = read()
                        if (c < 0) {
                            break
                        }
                        if (c == '\''.code) {
                            break
                        }
                    }
                    c = read()
                }
                '"'.code -> {
                    while (true) {
                        c = read()
                        if (c < 0) {
                            break
                        }
                        if (c == '\"'.code) {
                            break
                        }
                    }
                    c = read()
                }
                '/'.code -> {
                    c = read()
                    if (c == '*'.code) {
                        // block comment
                        startRemark(true)
                        var level = 1
                        while (true) {
                            c = read()
                            if (c < 0) {
                                break
                            }
                            if (c == '*'.code) {
                                c = read()
                                if (c < 0) {
                                    clearRemark()
                                    break
                                }
                                if (c == '/'.code) {
                                    if (--level == 0) {
                                        endRemark()
                                        break
                                    }
                                }
                            } else if (c == '/'.code) {
                                c = read()
                                if (c < 0) {
                                    clearRemark()
                                    break
                                }
                                if (c == '*'.code) {
                                    level++
                                }
                            }
                        }
                        c = read()
                    } else if (c == '/'.code) {
                        // single line comment
                        startRemark(false)
                        while (true) {
                            c = read()
                            if (c < 0) {
                                clearRemark()
                                break
                            }
                            if (c == '\r'.code || c == '\n'.code) {
                                endRemark()
                                break
                            }
                        }
                        c = read()
                    }
                }
                '-'.code -> {
                    c = read()
                    if (c == '-'.code) {
                        // single line comment
                        startRemark(false)
                        while (true) {
                            c = read()
                            if (c < 0) {
                                clearRemark()
                                break
                            }
                            if (c == '\r'.code || c == '\n'.code) {
                                endRemark()
                                break
                            }
                        }
                        c = read()
                    }
                }
                else -> {
                    c = read()
                }
            }
        }
        return String(buffer, bufferStart, bufferPos - 1 - bufferStart)
    }

    private fun startRemark(block: Boolean) {
        blockRemark = block
        remarkStart = bufferPos - 2
        insideRemark = true
    }

    private fun endRemark() {
        clearRemark()
        insideRemark = false
    }

    private fun clearRemark() {
        if (skipRemarks) {
            Arrays.fill(buffer, remarkStart, bufferPos, ' ')
        }
    }

    @Throws(IOException::class)
    private fun read(): Int {
        if (bufferPos >= bufferEnd) {
            return readBuffer()
        }
        return buffer[bufferPos++].code
    }

    @Throws(IOException::class)
    private fun readBuffer(): Int {
        if (endOfFile) {
            return -1
        }
        val keep = bufferPos - bufferStart
        if (keep > 0) {
            val src = buffer
            if (keep + Constants.IO_BUFFER_SIZE > src.size) {
                // protect against NegativeArraySizeException
                if (src.size >= Integer.MAX_VALUE / 2) {
                    throw IOException(
                        "Error in parsing script, " +
                            "statement size exceeds 1G, " +
                            "first 80 characters of statement looks like: " +
                            String(buffer, bufferStart, 80)
                    )
                }
                buffer = CharArray(src.size * 2)
            }
            System.arraycopy(src, bufferStart, buffer, 0, keep)
        }
        remarkStart -= bufferStart
        bufferStart = 0
        bufferPos = keep
        val len = reader.read(buffer, keep, Constants.IO_BUFFER_SIZE)
        if (len == -1) {
            // ensure bufferPos > bufferEnd
            bufferEnd = -1024
            endOfFile = true
            // ensure the right number of characters are read
            // in case the input buffer is still used
            bufferPos++
            return -1
        }
        bufferEnd = keep + len
        return buffer[bufferPos++].code
    }

    /**
     * Check if this is the last statement, and if the single line or block
     * comment is not finished yet.
     *
     * @return true if the current position is inside a remark
     */
    fun isInsideRemark(): Boolean {
        return insideRemark
    }

    /**
     * If currently inside a remark, this method tells if it is a block comment
     * (true) or single line comment (false)
     *
     * @return true if inside a block comment
     */
    fun isBlockRemark(): Boolean {
        return blockRemark
    }

    /**
     * If comments should be skipped completely by this reader.
     *
     * @param skipRemarks true if comments should be skipped
     */
    fun setSkipRemarks(skipRemarks: Boolean) {
        this.skipRemarks = skipRemarks
    }
}
