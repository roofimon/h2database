/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.util

import java.io.IOException
import java.net.BindException
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.UnknownHostException
import org.h2.api.ErrorCode
import org.h2.engine.SysProperties
import org.h2.message.DbException
import org.h2.security.CipherFactory

/**
 * This utility class contains socket helper functions.
 */
class NetUtils private constructor() {
    // utility class

    companion object {
        private const val CACHE_MILLIS = 1000
        private var cachedBindAddress: InetAddress? = null
        private var cachedLocalAddress: String? = null
        private var cachedLocalAddressTime: Long = 0

        /**
         * Create a loopback socket (a socket that is connected to localhost) on
         * this port.
         *
         * @param port the port
         * @param ssl if SSL should be used
         * @return the socket
         * @throws IOException on failure
         */
        @JvmStatic
        @Throws(IOException::class)
        fun createLoopbackSocket(port: Int, ssl: Boolean): Socket {
            val local = getLocalAddress()
            try {
                return createSocket(local, port, ssl)
            } catch (e: IOException) {
                try {
                    return createSocket("localhost", port, ssl)
                } catch (e2: IOException) {
                    // throw the original exception
                    throw e
                }
            }
        }

        /**
         * Create a client socket that is connected to the given address and port.
         *
         * @param server to connect to (including an optional port)
         * @param defaultPort the default port (if not specified in the server
         *            address)
         * @param ssl if SSL should be used
         * @return the socket
         * @throws IOException on failure
         */
        @JvmStatic
        @Throws(IOException::class)
        fun createSocket(server: String, defaultPort: Int, ssl: Boolean): Socket {
            return createSocket(server, defaultPort, ssl, 0)
        }

        /**
         * Create a client socket that is connected to the given address and port.
         *
         * @param server to connect to (including an optional port)
         * @param defaultPort the default port (if not specified in the server
         *            address)
         * @param ssl if SSL should be used
         * @param networkTimeout socket so timeout
         * @return the socket
         * @throws IOException on failure
         */
        @JvmStatic
        @Throws(IOException::class)
        fun createSocket(server: String, defaultPort: Int, ssl: Boolean, networkTimeout: Int): Socket {
            var server = server
            var port = defaultPort
            // IPv6: RFC 2732 format is '[a:b:c:d:e:f:g:h]' or
            // '[a:b:c:d:e:f:g:h]:port'
            // RFC 2396 format is 'a.b.c.d' or 'a.b.c.d:port' or 'hostname' or
            // 'hostname:port'
            val startIndex = if (server.startsWith("[")) server.indexOf(']') else 0
            val idx = server.indexOf(':', startIndex)
            if (idx >= 0) {
                port = Integer.decode(server.substring(idx + 1))
                server = server.substring(0, idx)
            }
            val address = InetAddress.getByName(server)
            return createSocket(address, port, ssl, networkTimeout)
        }

        /**
         * Create a client socket that is connected to the given address and port.
         *
         * @param address the address to connect to
         * @param port the port
         * @param ssl if SSL should be used
         * @return the socket
         * @throws IOException on failure
         */
        @JvmStatic
        @Throws(IOException::class)
        fun createSocket(address: InetAddress, port: Int, ssl: Boolean): Socket {
            return createSocket(address, port, ssl, 0)
        }

        /**
         * Create a client socket that is connected to the given address and port.
         *
         * @param address the address to connect to
         * @param port the port
         * @param ssl if SSL should be used
         * @param networkTimeout socket so timeout
         * @return the socket
         * @throws IOException on failure
         */
        @JvmStatic
        @Throws(IOException::class)
        fun createSocket(address: InetAddress, port: Int, ssl: Boolean, networkTimeout: Int): Socket {
            val start = System.nanoTime()
            var i = 0
            while (true) {
                try {
                    if (ssl) {
                        return CipherFactory.createSocket(address, port)
                    }
                    val socket = Socket()
                    socket.soTimeout = networkTimeout
                    socket.connect(
                        InetSocketAddress(address, port),
                        SysProperties.SOCKET_CONNECT_TIMEOUT
                    )
                    return socket
                } catch (e: IOException) {
                    if (System.nanoTime() - start >= SysProperties.SOCKET_CONNECT_TIMEOUT * 1_000_000L) {
                        // either it was a connect timeout,
                        // or list of different exceptions
                        throw e
                    }
                    if (i >= SysProperties.SOCKET_CONNECT_RETRY) {
                        throw e
                    }
                    // wait a bit and retry
                    try {
                        // sleep at most 256 ms
                        val sleep = Math.min(256L, (i * i).toLong())
                        Thread.sleep(sleep)
                    } catch (e2: InterruptedException) {
                        // ignore
                    }
                }
                i++
            }
        }

        /**
         * Create a server socket. The system property h2.bindAddress is used if
         * set.
         *
         * @param port the port to listen on
         * @param ssl if SSL should be used
         * @return the server socket
         */
        @JvmStatic
        fun createServerSocket(port: Int, ssl: Boolean): ServerSocket {
            return try {
                createServerSocketTry(port, ssl)
            } catch (e: Exception) {
                // try again
                createServerSocketTry(port, ssl)
            }
        }

        /**
         * Get the bind address if the system property h2.bindAddress is set, or
         * null if not.
         *
         * @return the bind address
         */
        @Throws(UnknownHostException::class)
        private fun getBindAddress(): InetAddress? {
            val host = SysProperties.BIND_ADDRESS
            if (host == null || host.isEmpty()) {
                return null
            }
            synchronized(NetUtils::class.java) {
                if (cachedBindAddress == null) {
                    cachedBindAddress = InetAddress.getByName(host)
                }
            }
            return cachedBindAddress
        }

        private fun createServerSocketTry(port: Int, ssl: Boolean): ServerSocket {
            try {
                val bindAddress = getBindAddress()
                if (ssl) {
                    return CipherFactory.createServerSocket(port, bindAddress)
                }
                if (bindAddress == null) {
                    return ServerSocket(port)
                }
                return ServerSocket(port, 0, bindAddress)
            } catch (be: BindException) {
                throw DbException.get(
                    ErrorCode.EXCEPTION_OPENING_PORT_2,
                    be, Integer.toString(port), be.toString()
                )
            } catch (e: IOException) {
                throw DbException.convertIOException(e, "port: $port ssl: $ssl")
            }
        }

        /**
         * Check if a socket is connected to a local address.
         *
         * @param socket the socket
         * @return true if it is
         * @throws UnknownHostException on failure
         */
        @JvmStatic
        @Throws(UnknownHostException::class)
        fun isLocalAddress(socket: Socket): Boolean {
            val test = socket.inetAddress
            if (test.isLoopbackAddress) {
                return true
            }
            val localhost = InetAddress.getLocalHost()
            // localhost.getCanonicalHostName() is very slow
            val host = localhost.hostAddress
            for (addr in InetAddress.getAllByName(host)) {
                if (test == addr) {
                    return true
                }
            }
            return false
        }

        /**
         * Close a server socket and ignore any exceptions.
         *
         * @param socket the socket
         * @return null
         */
        @JvmStatic
        fun closeSilently(socket: ServerSocket?): ServerSocket? {
            if (socket != null) {
                try {
                    socket.close()
                } catch (e: IOException) {
                    // ignore
                }
            }
            return null
        }

        /**
         * Get the local host address as a string.
         * For performance, the result is cached for one second.
         *
         * @return the local host address
         */
        @JvmStatic
        @Synchronized
        fun getLocalAddress(): String {
            val now = System.nanoTime()
            val cached = cachedLocalAddress
            if (cached != null && now - cachedLocalAddressTime < CACHE_MILLIS * 1_000_000L) {
                return cached
            }
            var bind: InetAddress? = null
            var useLocalhost = false
            try {
                bind = getBindAddress()
                if (bind == null) {
                    useLocalhost = true
                }
            } catch (e: UnknownHostException) {
                // ignore
            }
            if (useLocalhost) {
                try {
                    bind = InetAddress.getLocalHost()
                } catch (e: UnknownHostException) {
                    throw DbException.convert(e)
                }
            }
            var address: String
            if (bind == null) {
                address = "localhost"
            } else {
                address = bind.hostAddress
                if (bind is Inet6Address) {
                    if (address.indexOf('%') >= 0) {
                        address = "localhost"
                    } else if (address.indexOf(':') >= 0 && !address.startsWith("[")) {
                        // adds'[' and ']' if required for
                        // Inet6Address that contain a ':'.
                        address = "[$address]"
                    }
                }
            }
            if (address == "127.0.0.1") {
                address = "localhost"
            }
            cachedLocalAddress = address
            cachedLocalAddressTime = now
            return address
        }

        /**
         * Get the host name of a local address, if available.
         *
         * @param localAddress the local address
         * @return the host name, or another text if not available
         */
        @JvmStatic
        fun getHostName(localAddress: String): String {
            return try {
                val addr = InetAddress.getByName(localAddress)
                addr.hostName
            } catch (e: Exception) {
                "unknown"
            }
        }

        /**
         * Appends short representation of the specified IP address to the string
         * builder.
         *
         * @param builder
         *            string builder to append to, or `null`
         * @param address
         *            IP address
         * @param addBrackets
         *            if (`true`, add brackets around IPv6 addresses
         * @return the specified or the new string builder with short representation
         *         of specified address
         */
        @JvmStatic
        fun ipToShortForm(builder: StringBuilder?, address: ByteArray, addBrackets: Boolean): StringBuilder {
            var builder = builder
            when (address.size) {
                4 -> {
                    if (builder == null) {
                        builder = StringBuilder(15)
                    }
                    builder //
                        .append(address[0].toInt() and 0xff).append('.') //
                        .append(address[1].toInt() and 0xff).append('.') //
                        .append(address[2].toInt() and 0xff).append('.') //
                        .append(address[3].toInt() and 0xff)
                }
                16 -> {
                    val a = ShortArray(8)
                    var maxStart = 0
                    var maxLen = 0
                    var currentLen = 0
                    var offset = 0
                    for (i in 0 until 8) {
                        a[i] = (((address[offset++].toInt() and 0xff) shl 8) or (address[offset++].toInt() and 0xff)).toShort()
                        if (a[i].toInt() == 0) {
                            currentLen++
                            if (currentLen > maxLen) {
                                maxLen = currentLen
                                maxStart = i - currentLen + 1
                            }
                        } else {
                            currentLen = 0
                        }
                    }
                    if (builder == null) {
                        builder = StringBuilder(if (addBrackets) 41 else 39)
                    }
                    if (addBrackets) {
                        builder.append('[')
                    }
                    val start: Int
                    if (maxLen > 1) {
                        for (i in 0 until maxStart) {
                            builder.append(Integer.toHexString(a[i].toInt() and 0xffff)).append(':')
                        }
                        if (maxStart == 0) {
                            builder.append(':')
                        }
                        builder.append(':')
                        start = maxStart + maxLen
                    } else {
                        start = 0
                    }
                    for (i in start until 8) {
                        builder.append(Integer.toHexString(a[i].toInt() and 0xffff))
                        if (i < 7) {
                            builder.append(':')
                        }
                    }
                    if (addBrackets) {
                        builder.append(']')
                    }
                }
                else -> {
                    if (builder == null) {
                        builder = StringBuilder()
                    }
                    StringUtils.convertBytesToHex(builder, address)
                }
            }
            return builder!!
        }
    }
}
