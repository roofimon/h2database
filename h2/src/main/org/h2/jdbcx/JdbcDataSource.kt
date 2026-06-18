/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.jdbcx

import java.io.IOException
import java.io.ObjectInputStream
import java.io.PrintWriter
import java.io.Serializable
import java.sql.Connection
import java.sql.SQLException
import java.util.logging.Logger
import javax.naming.Reference
import javax.naming.Referenceable
import javax.naming.StringRefAddr
import javax.sql.ConnectionPoolDataSource
import javax.sql.DataSource
import javax.sql.PooledConnection
import javax.sql.XAConnection
import javax.sql.XADataSource
import org.h2.jdbc.JdbcConnection
import org.h2.message.DbException
import org.h2.message.TraceObject
import org.h2.util.StringUtils

/**
 * A data source for H2 database connections. It is a factory for XAConnection
 * and Connection objects. This class is usually registered in a JNDI naming
 * service. To create a data source object and register it with a JNDI service,
 * use the following code:
 *
 * <pre>
 * import org.h2.jdbcx.JdbcDataSource;
 * import javax.naming.Context;
 * import javax.naming.InitialContext;
 * JdbcDataSource ds = new JdbcDataSource();
 * ds.setURL(&quot;jdbc:h2:&tilde;/test&quot;);
 * ds.setUser(&quot;sa&quot;);
 * ds.setPassword(&quot;sa&quot;);
 * Context ctx = new InitialContext();
 * ctx.bind(&quot;jdbc/dsName&quot;, ds);
 * </pre>
 *
 * To use a data source that is already registered, use the following code:
 *
 * <pre>
 * import java.sql.Connection;
 * import javax.sql.DataSource;
 * import javax.naming.Context;
 * import javax.naming.InitialContext;
 * Context ctx = new InitialContext();
 * DataSource ds = (DataSource) ctx.lookup(&quot;jdbc/dsName&quot;);
 * Connection conn = ds.getConnection();
 * </pre>
 *
 * In this example the user name and password are serialized as
 * well; this may be a security problem in some cases.
 */
class JdbcDataSource : TraceObject(), XADataSource, DataSource, ConnectionPoolDataSource,
    Serializable, Referenceable, JdbcDataSourceBackwardsCompat {

    @Transient
    private var factory: JdbcDataSourceFactory? = null

    @Transient
    private var logWriter: PrintWriter? = null
    private var loginTimeout = 0
    private var userName: String? = ""
    private var passwordChars: CharArray? = CharArray(0)
    private var url: String? = ""
    private var description: String? = null

    init {
        initFactory()
        val id = getNextId(TraceObject.DATA_SOURCE)
        setTrace(factory!!.getTrace(), TraceObject.DATA_SOURCE, id)
    }

    /**
     * Called when de-serializing the object.
     *
     * @param in the input stream
     * @throws IOException on failure
     * @throws ClassNotFoundException on failure
     */
    @Throws(IOException::class, ClassNotFoundException::class)
    private fun readObject(`in`: ObjectInputStream) {
        initFactory()
        `in`.defaultReadObject()
    }

    private fun initFactory() {
        factory = JdbcDataSourceFactory()
    }

    /**
     * Get the login timeout in seconds, 0 meaning no timeout.
     *
     * @return the timeout in seconds
     */
    override fun getLoginTimeout(): Int {
        debugCodeCall("getLoginTimeout")
        return loginTimeout
    }

    /**
     * Set the login timeout in seconds, 0 meaning no timeout.
     * The default value is 0.
     * This value is ignored by this database.
     *
     * @param timeout the timeout in seconds
     */
    override fun setLoginTimeout(timeout: Int) {
        debugCodeCall("setLoginTimeout", timeout.toLong())
        this.loginTimeout = timeout
    }

    /**
     * Get the current log writer for this object.
     *
     * @return the log writer
     */
    override fun getLogWriter(): PrintWriter? {
        debugCodeCall("getLogWriter")
        return logWriter
    }

    /**
     * Set the current log writer for this object.
     * This value is ignored by this database.
     *
     * @param out the log writer
     */
    override fun setLogWriter(out: PrintWriter?) {
        debugCodeCall("setLogWriter(out)")
        logWriter = out
    }

    /**
     * Open a new connection using the current URL, user name and password.
     *
     * @return the connection
     */
    @Throws(SQLException::class)
    override fun getConnection(): Connection {
        debugCodeCall("getConnection")
        return JdbcConnection(url, null, userName, StringUtils.cloneCharArray(passwordChars), false)
    }

    /**
     * Open a new connection using the current URL and the specified user name
     * and password.
     *
     * @param user the user name
     * @param password the password
     * @return the connection
     */
    @Throws(SQLException::class)
    override fun getConnection(user: String?, password: String?): Connection {
        if (isDebugEnabled()) {
            debugCode("getConnection(" + quote(user) + ", \"\")")
        }
        return JdbcConnection(url, null, user, password, false)
    }

    /**
     * Get the current URL.
     *
     * @return the URL
     */
    fun getURL(): String? {
        debugCodeCall("getURL")
        return url
    }

    /**
     * Set the current URL.
     *
     * @param url the new URL
     */
    fun setURL(url: String?) {
        debugCodeCall("setURL", url)
        this.url = url
    }

    /**
     * Get the current URL.
     * This method does the same as getURL, but this methods signature conforms
     * the JavaBean naming convention.
     *
     * @return the URL
     */
    fun getUrl(): String? {
        debugCodeCall("getUrl")
        return url
    }

    /**
     * Set the current URL.
     * This method does the same as setURL, but this methods signature conforms
     * the JavaBean naming convention.
     *
     * @param url the new URL
     */
    fun setUrl(url: String?) {
        debugCodeCall("setUrl", url)
        this.url = url
    }

    /**
     * Set the current password.
     *
     * @param password the new password.
     */
    fun setPassword(password: String?) {
        debugCodeCall("setPassword", "")
        this.passwordChars = password?.toCharArray()
    }

    /**
     * Set the current password in the form of a char array.
     *
     * @param password the new password in the form of a char array.
     */
    fun setPasswordChars(password: CharArray?) {
        if (isDebugEnabled()) {
            debugCode("setPasswordChars(new char[0])")
        }
        this.passwordChars = password
    }

    /**
     * Get the current password.
     *
     * @return the password
     */
    fun getPassword(): String? {
        debugCodeCall("getPassword")
        return convertToString(passwordChars)
    }

    /**
     * Get the current user name.
     *
     * @return the user name
     */
    fun getUser(): String? {
        debugCodeCall("getUser")
        return userName
    }

    /**
     * Set the current user name.
     *
     * @param user the new user name
     */
    fun setUser(user: String?) {
        debugCodeCall("setUser", user)
        this.userName = user
    }

    /**
     * Get the current description.
     *
     * @return the description
     */
    fun getDescription(): String? {
        debugCodeCall("getDescription")
        return description
    }

    /**
     * Set the description.
     *
     * @param description the new description
     */
    fun setDescription(description: String?) {
        debugCodeCall("getDescription", description)
        this.description = description
    }

    /**
     * Get a new reference for this object, using the current settings.
     *
     * @return the new reference
     */
    override fun getReference(): Reference {
        debugCodeCall("getReference")
        val factoryClassName = JdbcDataSourceFactory::class.java.name
        val ref = Reference(javaClass.name, factoryClassName, null)
        ref.add(StringRefAddr("url", url))
        ref.add(StringRefAddr("user", userName))
        ref.add(StringRefAddr("password", convertToString(passwordChars)))
        ref.add(StringRefAddr("loginTimeout", Integer.toString(loginTimeout)))
        ref.add(StringRefAddr("description", description))
        return ref
    }

    /**
     * Open a new XA connection using the current URL, user name and password.
     *
     * @return the connection
     */
    @Throws(SQLException::class)
    override fun getXAConnection(): XAConnection {
        debugCodeCall("getXAConnection")
        return JdbcXAConnection(factory!!, getNextId(XA_DATA_SOURCE),
            JdbcConnection(url, null, userName, StringUtils.cloneCharArray(passwordChars), false))
    }

    /**
     * Open a new XA connection using the current URL and the specified user
     * name and password.
     *
     * @param user the user name
     * @param password the password
     * @return the connection
     */
    @Throws(SQLException::class)
    override fun getXAConnection(user: String?, password: String?): XAConnection {
        if (isDebugEnabled()) {
            debugCode("getXAConnection(" + quote(user) + ", \"\")")
        }
        return JdbcXAConnection(factory!!, getNextId(XA_DATA_SOURCE),
            JdbcConnection(url, null, user, password, false))
    }

    /**
     * Open a new pooled connection using the current URL, user name and
     * password.
     *
     * @return the connection
     */
    @Throws(SQLException::class)
    override fun getPooledConnection(): PooledConnection {
        debugCodeCall("getPooledConnection")
        return getXAConnection()
    }

    /**
     * Open a new pooled connection using the current URL and the specified user
     * name and password.
     *
     * @param user the user name
     * @param password the password
     * @return the connection
     */
    @Throws(SQLException::class)
    override fun getPooledConnection(user: String?, password: String?): PooledConnection {
        if (isDebugEnabled()) {
            debugCode("getPooledConnection(" + quote(user) + ", \"\")")
        }
        return getXAConnection(user, password)
    }

    /**
     * Return an object of this class if possible.
     *
     * @param iface the class
     * @return this
     */
    @Throws(SQLException::class)
    @Suppress("UNCHECKED_CAST")
    override fun <T> unwrap(iface: Class<T>): T {
        try {
            if (isWrapperFor(iface)) {
                return this as T
            }
            throw DbException.getInvalidValueException("iface", iface)
        } catch (e: Exception) {
            throw logAndConvert(e)
        }
    }

    /**
     * Checks if unwrap can return an object of this class.
     *
     * @param iface the class
     * @return whether or not the interface is assignable from this class
     */
    @Throws(SQLException::class)
    override fun isWrapperFor(iface: Class<*>?): Boolean {
        return iface != null && iface.isAssignableFrom(javaClass)
    }

    /**
     * [Not supported]
     */
    override fun getParentLogger(): Logger? {
        return null
    }

    /**
     * INTERNAL
     */
    override fun toString(): String {
        return getTraceObjectName() + ": url=" + url + " user=" + userName
    }

    companion object {

        private const val serialVersionUID = 1288136338451857771L

        private fun convertToString(a: CharArray?): String? {
            return if (a == null) null else String(a)
        }
    }
}
