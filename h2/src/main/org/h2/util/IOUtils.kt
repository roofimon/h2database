/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.util

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.Reader
import java.io.StringWriter
import java.io.Writer
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets

import org.h2.engine.Constants
import org.h2.engine.SysProperties
import org.h2.mvstore.DataUtils
import org.h2.store.fs.FileUtils

/**
 * This utility class contains input/output functions.
 */
class IOUtils private constructor() {

    companion object {

        /**
         * Close an AutoCloseable without throwing an exception.
         *
         * @param out the AutoCloseable or null
         */
        @JvmStatic
        fun closeSilently(out: AutoCloseable?) {
            if (out != null) {
                try {
                    trace("closeSilently", null, out)
                    out.close()
                } catch (e: Exception) {
                    // ignore
                }
            }
        }

        /**
         * Skip a number of bytes in an input stream.
         *
         * @param in the input stream
         * @param skip the number of bytes to skip
         * @throws EOFException if the end of file has been reached before all bytes
         *             could be skipped
         * @throws IOException if an IO exception occurred while skipping
         */
        @JvmStatic
        @Throws(IOException::class)
        fun skipFully(`in`: InputStream, skip: Long) {
            var skip = skip
            try {
                while (skip > 0) {
                    val skipped = `in`.skip(skip)
                    if (skipped <= 0) {
                        throw EOFException()
                    }
                    skip -= skipped
                }
            } catch (e: Exception) {
                throw DataUtils.convertToIOException(e)
            }
        }

        /**
         * Skip a number of characters in a reader.
         *
         * @param reader the reader
         * @param skip the number of characters to skip
         * @throws EOFException if the end of file has been reached before all
         *             characters could be skipped
         * @throws IOException if an IO exception occurred while skipping
         */
        @JvmStatic
        @Throws(IOException::class)
        fun skipFully(reader: Reader, skip: Long) {
            var skip = skip
            try {
                while (skip > 0) {
                    val skipped = reader.skip(skip)
                    if (skipped <= 0) {
                        throw EOFException()
                    }
                    skip -= skipped
                }
            } catch (e: Exception) {
                throw DataUtils.convertToIOException(e)
            }
        }

        /**
         * Copy all data from the input stream to the output stream and close both
         * streams. Exceptions while closing are ignored.
         *
         * @param in the input stream
         * @param out the output stream
         * @return the number of bytes copied
         * @throws IOException on failure
         */
        @JvmStatic
        @Throws(IOException::class)
        fun copyAndClose(`in`: InputStream, out: OutputStream): Long {
            try {
                val len = copyAndCloseInput(`in`, out)
                out.close()
                return len
            } catch (e: Exception) {
                throw DataUtils.convertToIOException(e)
            } finally {
                closeSilently(out)
            }
        }

        /**
         * Copy all data from the input stream to the output stream and close the
         * input stream. Exceptions while closing are ignored.
         *
         * @param in the input stream
         * @param out the output stream (null if writing is not required)
         * @return the number of bytes copied
         * @throws IOException on failure
         */
        @JvmStatic
        @Throws(IOException::class)
        fun copyAndCloseInput(`in`: InputStream, out: OutputStream?): Long {
            try {
                return copy(`in`, out)
            } catch (e: Exception) {
                throw DataUtils.convertToIOException(e)
            } finally {
                closeSilently(`in`)
            }
        }

        /**
         * Copy all data from the input stream to the output stream. Both streams
         * are kept open.
         *
         * @param in the input stream
         * @param out the output stream (null if writing is not required)
         * @return the number of bytes copied
         * @throws IOException on failure
         */
        @JvmStatic
        @Throws(IOException::class)
        fun copy(`in`: InputStream, out: OutputStream?): Long {
            return copy(`in`, out, Long.MAX_VALUE)
        }

        /**
         * Copy all data from the input stream to the output stream. Both streams
         * are kept open.
         *
         * @param in the input stream
         * @param out the output stream (null if writing is not required)
         * @param length the maximum number of bytes to copy
         * @return the number of bytes copied
         * @throws IOException on failure
         */
        @JvmStatic
        @Throws(IOException::class)
        fun copy(`in`: InputStream, out: OutputStream?, length: Long): Long {
            var length = length
            try {
                var copied: Long = 0
                var len = Math.min(length, Constants.IO_BUFFER_SIZE.toLong()).toInt()
                val buffer = ByteArray(len)
                while (length > 0) {
                    len = `in`.read(buffer, 0, len)
                    if (len < 0) {
                        break
                    }
                    if (out != null) {
                        out.write(buffer, 0, len)
                    }
                    copied += len.toLong()
                    length -= len.toLong()
                    len = Math.min(length, Constants.IO_BUFFER_SIZE.toLong()).toInt()
                }
                return copied
            } catch (e: Exception) {
                throw DataUtils.convertToIOException(e)
            }
        }

        /**
         * Copy all data from the input FileChannel to the output stream. Both source and destination
         * are kept open.
         *
         * @param in the input FileChannel
         * @param out the output stream (null if writing is not required)
         * @return the number of bytes copied
         * @throws IOException on failure
         */
        @JvmStatic
        @Throws(IOException::class)
        fun copy(`in`: FileChannel, out: OutputStream?): Long {
            return copy(`in`, out, Long.MAX_VALUE)
        }

        /**
         * Copy all data from the input FileChannel to the output stream. Both source and destination
         * are kept open.
         *
         * @param in the input FileChannel
         * @param out the output stream (null if writing is not required)
         * @param length the maximum number of bytes to copy
         * @return the number of bytes copied
         * @throws IOException on failure
         */
        @JvmStatic
        @Throws(IOException::class)
        fun copy(`in`: FileChannel, out: OutputStream?, length: Long): Long {
            var length = length
            try {
                var copied: Long = 0
                val buffer = ByteArray(Math.min(length, Constants.IO_BUFFER_SIZE.toLong()).toInt())
                val wrap = ByteBuffer.wrap(buffer)
                while (length > 0) {
                    val len = `in`.read(wrap, copied)
                    if (len < 0) {
                        break
                    }
                    if (out != null) {
                        out.write(buffer, 0, len)
                    }
                    copied += len.toLong()
                    length -= len.toLong()
                    wrap.rewind()
                    if (length < wrap.limit()) {
                        wrap.limit(length.toInt())
                    }
                }
                return copied
            } catch (e: Exception) {
                throw DataUtils.convertToIOException(e)
            }
        }

        /**
         * Copy all data from the reader to the writer and close the reader.
         * Exceptions while closing are ignored.
         *
         * @param in the reader
         * @param out the writer (null if writing is not required)
         * @param length the maximum number of bytes to copy
         * @return the number of characters copied
         * @throws IOException on failure
         */
        @JvmStatic
        @Throws(IOException::class)
        fun copyAndCloseInput(`in`: Reader, out: Writer?, length: Long): Long {
            var length = length
            try {
                var copied: Long = 0
                var len = Math.min(length, Constants.IO_BUFFER_SIZE.toLong()).toInt()
                val buffer = CharArray(len)
                while (length > 0) {
                    len = `in`.read(buffer, 0, len)
                    if (len < 0) {
                        break
                    }
                    if (out != null) {
                        out.write(buffer, 0, len)
                    }
                    copied += len.toLong()
                    length -= len.toLong()
                    len = Math.min(length, Constants.IO_BUFFER_SIZE.toLong()).toInt()
                }
                return copied
            } catch (e: Exception) {
                throw DataUtils.convertToIOException(e)
            } finally {
                `in`.close()
            }
        }

        /**
         * Read a number of bytes from an input stream and close the stream.
         *
         * @param in the input stream
         * @param length the maximum number of bytes to read, or -1 to read until
         *            the end of file
         * @return the bytes read
         * @throws IOException on failure
         */
        @JvmStatic
        @Throws(IOException::class)
        fun readBytesAndClose(`in`: InputStream, length: Int): ByteArray {
            var length = length
            try {
                if (length <= 0) {
                    length = Int.MAX_VALUE
                }
                val block = Math.min(Constants.IO_BUFFER_SIZE, length)
                val out = ByteArrayOutputStream(block)
                copy(`in`, out, length.toLong())
                return out.toByteArray()
            } catch (e: Exception) {
                throw DataUtils.convertToIOException(e)
            } finally {
                `in`.close()
            }
        }

        /**
         * Read a number of characters from a reader and close it.
         *
         * @param in the reader
         * @param length the maximum number of characters to read, or -1 to read
         *            until the end of file
         * @return the string read
         * @throws IOException on failure
         */
        @JvmStatic
        @Throws(IOException::class)
        fun readStringAndClose(`in`: Reader, length: Int): String {
            var length = length
            try {
                if (length <= 0) {
                    length = Int.MAX_VALUE
                }
                val block = Math.min(Constants.IO_BUFFER_SIZE, length)
                val out = StringWriter(block)
                copyAndCloseInput(`in`, out, length.toLong())
                return out.toString()
            } finally {
                `in`.close()
            }
        }

        /**
         * Try to read the given number of bytes to the buffer. This method reads
         * until the maximum number of bytes have been read or until the end of
         * file.
         *
         * @param in the input stream
         * @param buffer the output buffer
         * @param max the number of bytes to read at most
         * @return the number of bytes read, 0 meaning EOF
         * @throws IOException on failure
         */
        @JvmStatic
        @Throws(IOException::class)
        fun readFully(`in`: InputStream, buffer: ByteArray, max: Int): Int {
            try {
                var result = 0
                var len = Math.min(max, buffer.size)
                while (len > 0) {
                    val l = `in`.read(buffer, result, len)
                    if (l < 0) {
                        break
                    }
                    result += l
                    len -= l
                }
                return result
            } catch (e: Exception) {
                throw DataUtils.convertToIOException(e)
            }
        }

        /**
         * Try to read the given number of characters to the buffer. This method
         * reads until the maximum number of characters have been read or until the
         * end of file.
         *
         * @param in the reader
         * @param buffer the output buffer
         * @param max the number of characters to read at most
         * @return the number of characters read, 0 meaning EOF
         * @throws IOException on failure
         */
        @JvmStatic
        @Throws(IOException::class)
        fun readFully(`in`: Reader, buffer: CharArray, max: Int): Int {
            try {
                var result = 0
                var len = Math.min(max, buffer.size)
                while (len > 0) {
                    val l = `in`.read(buffer, result, len)
                    if (l < 0) {
                        break
                    }
                    result += l
                    len -= l
                }
                return result
            } catch (e: Exception) {
                throw DataUtils.convertToIOException(e)
            }
        }

        /**
         * Create a reader to read from an input stream using the UTF-8 format. If
         * the input stream is null, this method returns null. The InputStreamReader
         * that is used here is not exact, that means it may read some additional
         * bytes when buffering.
         *
         * @param in the input stream or null
         * @return the reader
         */
        @JvmStatic
        fun getReader(`in`: InputStream?): Reader? {
            // InputStreamReader may read some more bytes
            return if (`in` == null) null else BufferedReader(
                InputStreamReader(`in`, StandardCharsets.UTF_8)
            )
        }

        /**
         * Create a buffered writer to write to an output stream using the UTF-8
         * format. If the output stream is null, this method returns null.
         *
         * @param out the output stream or null
         * @return the writer
         */
        @JvmStatic
        fun getBufferedWriter(out: OutputStream?): Writer? {
            return if (out == null) null else BufferedWriter(
                OutputStreamWriter(out, StandardCharsets.UTF_8)
            )
        }

        /**
         * Wrap an input stream in a reader. The bytes are converted to characters
         * using the US-ASCII character set.
         *
         * @param in the input stream
         * @return the reader
         */
        @JvmStatic
        fun getAsciiReader(`in`: InputStream?): Reader? {
            return if (`in` == null) null else InputStreamReader(`in`, StandardCharsets.US_ASCII)
        }

        /**
         * Trace input or output operations if enabled.
         *
         * @param method the method from where this method was called
         * @param fileName the file name
         * @param o the object to append to the message
         */
        @JvmStatic
        fun trace(method: String, fileName: String?, o: Any?) {
            if (SysProperties.TRACE_IO) {
                println("IOUtils.$method $fileName $o")
            }
        }

        /**
         * Create an input stream to read from a string. The string is converted to
         * a byte array using UTF-8 encoding.
         * If the string is null, this method returns null.
         *
         * @param s the string
         * @return the input stream
         */
        @JvmStatic
        fun getInputStreamFromString(s: String?): InputStream? {
            if (s == null) {
                return null
            }
            return ByteArrayInputStream(s.toByteArray(StandardCharsets.UTF_8))
        }

        /**
         * Copy a file from one directory to another, or to another file.
         *
         * @param original the original file name
         * @param copy the file name of the copy
         * @throws IOException on failure
         */
        @JvmStatic
        @Throws(IOException::class)
        fun copyFiles(original: String, copy: String) {
            val `in` = FileUtils.newInputStream(original)
            val out = FileUtils.newOutputStream(copy, false)
            copyAndClose(`in`, out)
        }

        /**
         * Converts / and \ name separators in path to native separators.
         *
         * @param path path to convert
         * @return path with converted separators
         */
        @JvmStatic
        fun nameSeparatorsToNative(path: String): String {
            return if (File.separatorChar == '/') path.replace('\\', '/') else path.replace('/', '\\')
        }
    }
}
