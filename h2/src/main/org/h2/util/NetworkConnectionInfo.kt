/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.util

import java.net.InetAddress
import java.net.UnknownHostException

/**
 * Network connection information.
 */
class NetworkConnectionInfo(
    private val server: String,
    private val clientAddr: ByteArray,
    private val clientPort: Int,
    private val clientInfo: String?
) {

    /**
     * Creates new instance of network connection information.
     *
     * @param server
     *            the protocol and port of the server
     * @param clientAddr
     *            the client address
     * @param clientPort
     *            the client port
     * @throws UnknownHostException
     *             if clientAddr cannot be resolved
     */
    @Throws(UnknownHostException::class)
    constructor(server: String, clientAddr: String, clientPort: Int) :
        this(server, InetAddress.getByName(clientAddr).address, clientPort, null)

    /**
     * Returns the protocol and port of the server.
     *
     * @return the protocol and port of the server
     */
    fun getServer(): String {
        return server
    }

    /**
     * Returns the client address.
     *
     * @return the client address
     */
    fun getClientAddr(): ByteArray {
        return clientAddr
    }

    /**
     * Returns the client port.
     *
     * @return the client port
     */
    fun getClientPort(): Int {
        return clientPort
    }

    /**
     * Returns additional client information, or {@code null}.
     *
     * @return additional client information, or {@code null}
     */
    fun getClientInfo(): String? {
        return clientInfo
    }

    /**
     * Returns the client address and port.
     *
     * @return the client address and port
     */
    fun getClient(): String {
        return NetUtils.ipToShortForm(StringBuilder(), clientAddr, true).append(':').append(clientPort).toString()
    }

}
