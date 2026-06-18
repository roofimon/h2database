/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.jmx

import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.Properties
import javax.management.MBeanAttributeInfo
import javax.management.MBeanInfo
import javax.management.MBeanOperationInfo
import javax.management.NotCompliantMBeanException
import javax.management.StandardMBean
import org.h2.util.Utils

/**
 * An MBean that reads the documentation from a resource file.
 *
 * Constructor
 * @param impl bean implementation
 * @param mbeanInterface bean interface class
 * @param <T> bean type
 * @throws NotCompliantMBeanException if the mbeanInterface does not follow JMX design patterns
 * for Management Interfaces, or if the given implementation does not implement the specified interface.
 */
class DocumentedMBean<T> @Throws(NotCompliantMBeanException::class) constructor(
    impl: T,
    mbeanInterface: Class<T>,
) : StandardMBean(impl, mbeanInterface) {

    private val interfaceName: String = impl!!::class.java.name + "MBean"
    private var resources: Properties? = null

    private fun getResources(): Properties {
        var resources = this.resources
        if (resources == null) {
            resources = Properties()
            val resourceName = "/org/h2/res/javadoc.properties"
            try {
                val buff = Utils.getResource(resourceName)
                if (buff != null) {
                    resources.load(ByteArrayInputStream(buff))
                }
            } catch (e: IOException) {
                // ignore
            }
            this.resources = resources
        }
        return resources
    }

    override fun getDescription(info: MBeanInfo): String {
        val s = getResources().getProperty(interfaceName)
        return s ?: super.getDescription(info)
    }

    override fun getDescription(op: MBeanOperationInfo): String {
        val s = getResources().getProperty(interfaceName + "." + op.name)
        return s ?: super.getDescription(op)
    }

    override fun getDescription(info: MBeanAttributeInfo): String {
        val prefix = if (info.isIs) "is" else "get"
        val s = getResources().getProperty(
            interfaceName + "." + prefix + info.name
        )
        return s ?: super.getDescription(info)
    }

    override fun getImpact(info: MBeanOperationInfo): Int {
        if (info.name.startsWith("list")) {
            return MBeanOperationInfo.INFO
        }
        return MBeanOperationInfo.ACTION
    }
}
