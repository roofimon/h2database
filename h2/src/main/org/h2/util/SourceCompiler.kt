/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.util

import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.PrintStream
import java.io.StringReader
import java.io.StringWriter
import java.lang.reflect.Array
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.SecureClassLoader
import java.util.ArrayList
import java.util.HashMap
import java.util.function.Predicate
import javax.script.Bindings
import javax.script.Compilable
import javax.script.CompiledScript
import javax.script.ScriptContext
import javax.script.ScriptEngine
import javax.script.ScriptEngineManager
import javax.script.ScriptException
import javax.tools.FileObject
import javax.tools.ForwardingJavaFileManager
import javax.tools.JavaCompiler
import javax.tools.JavaFileManager
import javax.tools.JavaFileObject
import javax.tools.JavaFileObject.Kind
import javax.tools.SimpleJavaFileObject
import javax.tools.StandardJavaFileManager
import javax.tools.ToolProvider
import org.h2.api.ErrorCode
import org.h2.engine.SysProperties
import org.h2.message.DbException

/**
 * This class allows to convert source code to a class. It uses one class loader
 * per class.
 */
class SourceCompiler {

    /**
     * The class name to source code map.
     */
    val sources = HashMap<String, String>()

    /**
     * The class name to byte code map.
     */
    val compiled = HashMap<String, Class<*>>()

    /**
     * The class name to compiled scripts map.
     */
    val compiledScripts: MutableMap<String, CompiledScript> = HashMap()

    /**
     * Whether to use the ToolProvider.getSystemJavaCompiler().
     */
    var useJavaSystemCompiler = SysProperties.JAVA_SYSTEM_COMPILER

    /**
     * Set the source code for the specified class.
     * This will reset all compiled classes.
     *
     * @param className the class name
     * @param source the source code
     */
    fun setSource(className: String, source: String) {
        sources[className] = source
        compiled.clear()
    }

    /**
     * Enable or disable the usage of the Java system compiler.
     *
     * @param enabled true to enable
     */
    fun setJavaSystemCompiler(enabled: Boolean) {
        this.useJavaSystemCompiler = enabled
    }

    /**
     * Get the class object for the given name.
     *
     * @param packageAndClassName the class name
     * @return the class
     * @throws ClassNotFoundException on failure
     */
    @Throws(ClassNotFoundException::class)
    fun getClass(packageAndClassName: String): Class<*> {

        val compiledClass = compiled[packageAndClassName]
        if (compiledClass != null) {
            return compiledClass
        }
        val source = sources[packageAndClassName]
        if (isGroovySource(source)) {
            val clazz = GroovyCompiler.parseClass(source, packageAndClassName)
            compiled[packageAndClassName] = clazz
            return clazz
        }

        val classLoader: ClassLoader = object : ClassLoader(javaClass.classLoader) {

            @Throws(ClassNotFoundException::class)
            override fun findClass(name: String): Class<*> {
                var classInstance: Class<*>? = compiled[name]
                if (classInstance == null) {
                    val source = sources[name]
                    var packageName: String? = null
                    val idx = name.lastIndexOf('.')
                    val className: String
                    if (idx >= 0) {
                        packageName = name.substring(0, idx)
                        className = name.substring(idx + 1)
                    } else {
                        className = name
                    }
                    val s = getCompleteSourceCode(packageName, className, source)
                    if (JAVA_COMPILER != null && useJavaSystemCompiler) {
                        classInstance = javaxToolsJavac(packageName, className, s)
                    } else {
                        val data = javacCompile(packageName, className, s)
                        classInstance = if (data == null) {
                            findSystemClass(name)
                        } else {
                            defineClass(name, data, 0, data.size)
                        }
                    }
                    compiled[name] = classInstance
                }
                return classInstance
            }
        }
        return classLoader.loadClass(packageAndClassName)
    }

    /**
     * Get the compiled script.
     *
     * @param packageAndClassName the package and class name
     * @return the compiled script
     * @throws ScriptException on failure
     */
    @Throws(ScriptException::class)
    fun getCompiledScript(packageAndClassName: String): CompiledScript {
        var compiledScript = compiledScripts[packageAndClassName]
        if (compiledScript == null) {
            val source = sources[packageAndClassName]
            val lang: String
            if (isJavascriptSource(source)) {
                lang = "javascript"
            } else if (isRubySource(source)) {
                lang = "ruby"
            } else {
                throw IllegalStateException("Unknown language for $source")
            }

            val jsEngine = ScriptEngineManager().getEngineByName(lang)
            if (jsEngine.javaClass.name == "com.oracle.truffle.js.scriptengine.GraalJSScriptEngine") {
                val bindings = jsEngine.getBindings(ScriptContext.ENGINE_SCOPE)
                bindings["polyglot.js.allowHostAccess"] = true
                bindings["polyglot.js.allowHostClassLookup"] = Predicate<String> { s -> true }
            }
            compiledScript = (jsEngine as Compilable).compile(source)
            compiledScripts[packageAndClassName] = compiledScript
        }
        return compiledScript!!
    }

    /**
     * Get the first public static method of the given class.
     *
     * @param className the class name
     * @return the method name
     * @throws ClassNotFoundException on failure
     */
    @Throws(ClassNotFoundException::class)
    fun getMethod(className: String): Method? {
        val clazz = getClass(className)
        val methods = clazz.declaredMethods
        for (m in methods) {
            val modifiers = m.modifiers
            if (Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers)) {
                val name = m.name
                if (!name.startsWith("_") && m.name != "main") {
                    return m
                }
            }
        }
        return null
    }

    /**
     * Compile the given class. This method tries to use the class
     * "com.sun.tools.javac.Main" if available. If not, it tries to run "javac"
     * in a separate process.
     *
     * @param packageName the package name
     * @param className the class name
     * @param source the source code
     * @return the class file
     */
    fun javacCompile(packageName: String?, className: String, source: String): ByteArray? {
        var dir = Paths.get(COMPILE_DIR)
        if (packageName != null) {
            dir = dir.resolve(packageName.replace('.', '/'))
            try {
                Files.createDirectories(dir)
            } catch (e: Exception) {
                throw DbException.convert(e)
            }
        }
        val javaFile = dir.resolve("$className.java")
        val classFile = dir.resolve("$className.class")
        try {
            Files.write(javaFile, source.toByteArray(StandardCharsets.UTF_8))
            Files.deleteIfExists(classFile)
            if (JAVAC_SUN != null) {
                javacSun(javaFile)
            } else {
                javacProcess(javaFile)
            }
            return Files.readAllBytes(classFile)
        } catch (e: Exception) {
            throw DbException.convert(e)
        } finally {
            try {
                Files.deleteIfExists(javaFile)
            } catch (e: IOException) { /**/
            }
            try {
                Files.deleteIfExists(classFile)
            } catch (e: IOException) { /**/
            }
        }
    }

    /**
     * Compile using the standard java compiler.
     *
     * @param packageName the package name
     * @param className the class name
     * @param source the source code
     * @return the class
     */
    fun javaxToolsJavac(packageName: String?, className: String, source: String): Class<*> {
        val fullClassName = "$packageName.$className"
        val writer = StringWriter()
        try {
            ClassFileManager(
                JAVA_COMPILER!!.getStandardFileManager(null, null, null)
            ).use { fileManager ->
                val compilationUnits = ArrayList<JavaFileObject>()
                compilationUnits.add(StringJavaFileObject(fullClassName, source))
                // cannot concurrently compile
                val ok: Boolean
                synchronized(JAVA_COMPILER) {
                    ok = JAVA_COMPILER.getTask(
                        writer, fileManager, null, null,
                        null, compilationUnits
                    ).call()
                }
                val output = writer.toString()
                handleSyntaxError(output, if (ok) 0 else 1)
                return fileManager.getClassLoader(null).loadClass(fullClassName)
            }
        } catch (e: ClassNotFoundException) {
            throw DbException.convert(e)
        } catch (e: IOException) {
            throw DbException.convert(e)
        }
    }

    /**
     * Access the Groovy compiler using reflection, so that we do not gain a
     * compile-time dependency unnecessarily.
     */
    private object GroovyCompiler {

        private val LOADER: Any?
        private val INIT_FAIL_EXCEPTION: Throwable?

        init {
            var loader: Any? = null
            var initFailException: Throwable? = null
            try {
                // Create an instance of ImportCustomizer
                val importCustomizerClass = Class.forName(
                    "org.codehaus.groovy.control.customizers.ImportCustomizer"
                )
                val importCustomizer = Utils.newInstance(
                    "org.codehaus.groovy.control.customizers.ImportCustomizer"
                )!!
                // Call the method ImportCustomizer.addImports(String[])
                val importsArray = arrayOf(
                    "java.sql.Connection",
                    "java.sql.Types",
                    "java.sql.ResultSet",
                    "groovy.sql.Sql",
                    "org.h2.tools.SimpleResultSet"
                )
                Utils.callMethod(importCustomizer, "addImports", arrayOf<Any>(importsArray))

                // Call the method
                // CompilerConfiguration.addCompilationCustomizers(
                //         ImportCustomizer...)
                val importCustomizerArray = Array.newInstance(importCustomizerClass, 1)
                Array.set(importCustomizerArray, 0, importCustomizer)
                val configuration = Utils.newInstance(
                    "org.codehaus.groovy.control.CompilerConfiguration"
                )!!
                Utils.callMethod(
                    configuration,
                    "addCompilationCustomizers", importCustomizerArray
                )

                val parent = GroovyCompiler::class.java.classLoader
                loader = Utils.newInstance(
                    "groovy.lang.GroovyClassLoader", parent, configuration
                )
            } catch (ex: Exception) {
                initFailException = ex
            }
            LOADER = loader
            INIT_FAIL_EXCEPTION = initFailException
        }

        fun parseClass(source: String?, packageAndClassName: String): Class<*> {
            if (LOADER == null) {
                throw RuntimeException(
                    "Compile fail: no Groovy jar in the classpath", INIT_FAIL_EXCEPTION
                )
            }
            try {
                val codeSource = Utils.newInstance(
                    "groovy.lang.GroovyCodeSource",
                    source, "$packageAndClassName.groovy", "UTF-8"
                )!!
                Utils.callMethod(codeSource, "setCachable", false)
                return Utils.callMethod(LOADER, "parseClass", codeSource) as Class<*>
            } catch (e: Exception) {
                throw RuntimeException(e)
            }
        }
    }

    /**
     * An in-memory java source file object.
     */
    internal open class StringJavaFileObject(className: String, private val sourceCode: String) :
        SimpleJavaFileObject(
            URI.create(
                "string:///" + className.replace('.', '/') + Kind.SOURCE.extension
            ),
            Kind.SOURCE
        ) {

        override fun getCharContent(ignoreEncodingErrors: Boolean): CharSequence {
            return sourceCode
        }
    }

    /**
     * An in-memory java class object.
     */
    internal open class JavaClassObject(name: String, kind: Kind) :
        SimpleJavaFileObject(
            URI.create(
                "string:///" + name.replace('.', '/') + kind.extension
            ),
            kind
        ) {

        private val out = ByteArrayOutputStream()

        fun getBytes(): ByteArray {
            return out.toByteArray()
        }

        @Throws(IOException::class)
        override fun openOutputStream(): OutputStream {
            return out
        }
    }

    /**
     * An in-memory class file manager.
     */
    internal open class ClassFileManager(standardManager: StandardJavaFileManager) :
        ForwardingJavaFileManager<StandardJavaFileManager>(standardManager) {

        /**
         * We use map because there can be nested, anonymous etc. classes.
         */
        var classObjectsByName: MutableMap<String, JavaClassObject> = HashMap()

        private val classLoader: SecureClassLoader = object : SecureClassLoader() {

            @Throws(ClassNotFoundException::class)
            override fun findClass(name: String): Class<*> {
                val bytes = classObjectsByName[name]!!.getBytes()
                return super.defineClass(
                    name, bytes, 0,
                    bytes.size
                )
            }
        }

        override fun getClassLoader(location: JavaFileManager.Location?): ClassLoader {
            return this.classLoader
        }

        @Throws(IOException::class)
        override fun getJavaFileForOutput(
            location: JavaFileManager.Location?,
            className: String,
            kind: Kind,
            sibling: FileObject?
        ): JavaFileObject {
            val classObject = JavaClassObject(className, kind)
            classObjectsByName[className] = classObject
            return classObject
        }
    }

    companion object {
        /**
         * The "com.sun.tools.javac.Main" (if available).
         */
        @JvmField
        val JAVA_COMPILER: JavaCompiler?

        private val JAVAC_SUN: Class<*>?

        private val COMPILE_DIR = Utils.getProperty("java.io.tmpdir", ".")!!

        init {
            var c: JavaCompiler?
            try {
                c = ToolProvider.getSystemJavaCompiler()
            } catch (e: Exception) {
                // ignore
                c = null
            }
            JAVA_COMPILER = c
            var clazz: Class<*>?
            try {
                clazz = Class.forName("com.sun.tools.javac.Main")
            } catch (e: Exception) {
                clazz = null
            }
            JAVAC_SUN = clazz
        }

        private fun isGroovySource(source: String?): Boolean {
            return source!!.startsWith("//groovy") || source.startsWith("@groovy")
        }

        private fun isJavascriptSource(source: String?): Boolean {
            return source!!.startsWith("//javascript")
        }

        private fun isRubySource(source: String?): Boolean {
            return source!!.startsWith("#ruby")
        }

        /**
         * Whether the passed source can be compiled using [javax.script.ScriptEngineManager].
         *
         * @param source the source to test.
         * @return `true` if [.getCompiledScript] can be called.
         */
        @JvmStatic
        fun isJavaxScriptSource(source: String?): Boolean {
            return isJavascriptSource(source) || isRubySource(source)
        }

        /**
         * Get the complete source code (including package name, imports, and so
         * on).
         *
         * @param packageName the package name
         * @param className the class name
         * @param source the (possibly shortened) source code
         * @return the full source code
         */
        @JvmStatic
        fun getCompleteSourceCode(packageName: String?, className: String, source: String?): String {
            var source = source
            if (source!!.startsWith("package ")) {
                return source
            }
            val buff = StringBuilder()
            if (packageName != null) {
                buff.append("package ").append(packageName).append(";\n")
            }
            val endImport = source.indexOf("@CODE")
            var importCode = (
                "import java.util.*;\n" +
                    "import java.math.*;\n" +
                    "import java.sql.*;\n"
                )
            if (endImport >= 0) {
                importCode = source.substring(0, endImport)
                source = source.substring("@CODE".length + endImport)
            }
            buff.append(importCode)
            buff.append("public class ").append(className).append(
                " {\n" +
                    "    public static "
            ).append(source).append(
                "\n" +
                    "}\n"
            )
            return buff.toString()
        }

        private fun javacProcess(javaFile: Path) {
            exec(
                "javac",
                "-sourcepath", COMPILE_DIR,
                "-d", COMPILE_DIR,
                "-encoding", "UTF-8",
                javaFile.toAbsolutePath().toString()
            )
        }

        private fun exec(vararg args: String): Int {
            val buff = ByteArrayOutputStream()
            try {
                val builder = ProcessBuilder()
                // The javac executable allows some of its flags
                // to be smuggled in via environment variables.
                // But if it sees those flags, it will write out a message
                // to stderr, which messes up our parsing of the output.
                builder.environment().remove("JAVA_TOOL_OPTIONS")
                builder.command(*args)

                val p = builder.start()
                copyInThread(p.inputStream, buff)
                copyInThread(p.errorStream, buff)
                p.waitFor()
                val output = buff.toString(StandardCharsets.UTF_8)
                handleSyntaxError(output, p.exitValue())
                return p.exitValue()
            } catch (e: Exception) {
                throw DbException.convert(e)
            }
        }

        private fun copyInThread(`in`: InputStream, out: OutputStream) {
            object : Task() {
                @Throws(IOException::class)
                override fun call() {
                    IOUtils.copy(`in`, out)
                }
            }.execute()
        }

        @Synchronized
        private fun javacSun(javaFile: Path) {
            val old = System.err
            val buff = ByteArrayOutputStream()
            try {
                System.setErr(PrintStream(buff, false, StandardCharsets.UTF_8))
                val compile: Method = JAVAC_SUN!!.getMethod("compile", kotlin.Array<String>::class.java)
                val javac = JAVAC_SUN.getDeclaredConstructor().newInstance()
                // Bugfix: Here we should check exit status value instead of parsing javac output text.
                // Because of the output text is different in different locale environment.
                // @since 2018-07-20 little-pan
                val status = compile.invoke(
                    javac,
                    arrayOf(
                        "-sourcepath", COMPILE_DIR,
                        // "-Xlint:unchecked",
                        "-d", COMPILE_DIR,
                        "-encoding", "UTF-8",
                        javaFile.toAbsolutePath().toString()
                    ) as Any
                ) as Int
                val output = buff.toString(StandardCharsets.UTF_8)
                handleSyntaxError(output, status)
            } catch (e: Exception) {
                throw DbException.convert(e)
            } finally {
                System.setErr(old)
            }
        }

        private fun handleSyntaxError(output: String, exitStatus: Int) {
            var output = output
            if (0 == exitStatus) {
                return
            }
            var syntaxError = false
            val reader = BufferedReader(StringReader(output))
            try {
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (line!!.endsWith("warning") || line!!.endsWith("warnings")) {
                        // ignore summary line
                    } else if (line!!.startsWith("Note:") ||
                        line!!.startsWith("warning:")
                    ) {
                        // just a warning (e.g. unchecked or unsafe operations)
                    } else {
                        syntaxError = true
                        break
                    }
                }
            } catch (ignored: IOException) {
                // exception ignored
            }

            if (syntaxError) {
                output = StringUtils.replaceAll(output, COMPILE_DIR, "")
                throw DbException.get(ErrorCode.SYNTAX_ERROR_1, output)
            }
        }
    }
}
