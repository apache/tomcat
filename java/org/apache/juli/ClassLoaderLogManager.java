/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.juli;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;


/**
 * Per classloader LogManager implementation. For light debugging, set the system property
 * <code>org.apache.juli.ClassLoaderLogManager.debug=true</code>. Short configuration information will be sent to
 * <code>System.err</code>.
 */
public class ClassLoaderLogManager extends LogManager {

    private static ThreadLocal<Boolean> addingLocalRootLogger = ThreadLocal.withInitial(() -> Boolean.FALSE);

    public static final String DEBUG_PROPERTY = ClassLoaderLogManager.class.getName() + ".debug";

    private final class Cleaner extends Thread {

        @Override
        public void run() {
            if (useShutdownHook) {
                shutdown();
            }
        }

    }


    // ------------------------------------------------------------Constructors

    public ClassLoaderLogManager() {
        super();
        try {
            Runtime.getRuntime().addShutdownHook(new Cleaner());
        } catch (IllegalStateException ise) {
            // We are probably already being shutdown. Ignore this error.
        }
    }


    // -------------------------------------------------------------- Variables


    /**
     * Map containing the classloader information, keyed per classloader. A weak hashmap is used to ensure no
     * classloader reference is leaked from application redeployment.
     */
    protected final Map<ClassLoader, ClassLoaderLogInfo> classLoaderLoggers = new WeakHashMap<>(); // Guarded by this


    /**
     * This prefix is used to allow using prefixes for the properties names of handlers and their subcomponents.
     */
    protected final ThreadLocal<String> prefix = new ThreadLocal<>();


    /**
     * Determines if the shutdown hook is used to perform any necessary clean-up such as flushing buffered handlers on
     * JVM shutdown. Defaults to <code>true</code> but may be set to false if another component ensures that
     * {@link #shutdown()} is called.
     */
    protected volatile boolean useShutdownHook = true;


    // ------------------------------------------------------------- Properties


    public boolean isUseShutdownHook() {
        return useShutdownHook;
    }


    public void setUseShutdownHook(boolean useShutdownHook) {
        this.useShutdownHook = useShutdownHook;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * Add the specified logger to the classloader local configuration.
     *
     * @param logger The logger to be added
     */
    @Override
    public synchronized boolean addLogger(final Logger logger) {

        final String loggerName = logger.getName();

        ClassLoader classLoader = getClassLoader();
        ClassLoaderLogInfo info = getClassLoaderInfo(classLoader);
        if (info.loggers.containsKey(loggerName)) {
            return false;
        }
        info.loggers.put(loggerName, logger);

        // Apply initial level for new logger
        final String levelString = getProperty(loggerName + ".level");
        if (levelString != null) {
            logger.setLevel(Level.parse(levelString.trim()));
        }

        // Always instantiate parent loggers so that
        // we can control log categories even during runtime
        int dotIndex = loggerName.lastIndexOf('.');
        if (dotIndex >= 0) {
            final String parentName = loggerName.substring(0, dotIndex);
            Logger.getLogger(parentName);
        }

        // Find associated node
        LogNode node = info.rootNode.findNode(loggerName);
        node.logger = logger;

        // Set parent logger
        Logger parentLogger = node.findParentLogger();
        if (parentLogger != null) {
            logger.setParent(parentLogger);
        }

        // Tell children we are their new parent
        node.setParentLogger(logger);

        // Add associated handlers, if any are defined using the .handlers property.
        // In this case, handlers of the parent logger(s) will not be used
        String handlers = getProperty(loggerName + ".handlers");
        if (handlers != null) {
            logger.setUseParentHandlers(false);
            StringTokenizer tok = new StringTokenizer(handlers, ",");
            while (tok.hasMoreTokens()) {
                String handlerName = (tok.nextToken().trim());
                Handler handler = null;
                ClassLoader current = classLoader;
                while (current != null) {
                    info = classLoaderLoggers.get(current);
                    if (info != null) {
                        handler = info.handlers.get(handlerName);
                        if (handler != null) {
                            break;
                        }
                    }
                    current = current.getParent();
                }
                if (handler != null) {
                    logger.addHandler(handler);
                }
            }
        }

        // Parse useParentHandlers to set if the logger should delegate to its parent.
        // Unlike java.util.logging, the default is to not delegate if a list of handlers
        // has been specified for the logger.
        String useParentHandlersString = getProperty(loggerName + ".useParentHandlers");
        if (Boolean.parseBoolean(useParentHandlersString)) {
            logger.setUseParentHandlers(true);
        }

        return true;
    }


    /**
     * Get the logger associated with the specified name inside the classloader local configuration. If this returns
     * null, and the call originated for Logger.getLogger, a new logger with the specified name will be instantiated and
     * added using addLogger.
     *
     * @param name The name of the logger to retrieve
     */
    @Override
    public synchronized Logger getLogger(final String name) {
        ClassLoader classLoader = getClassLoader();
        return getClassLoaderInfo(classLoader).loggers.get(name);
    }


    /**
     * Get an enumeration of the logger names currently defined in the classloader local configuration.
     */
    @Override
    public synchronized Enumeration<String> getLoggerNames() {
        ClassLoader classLoader = getClassLoader();
        return Collections.enumeration(getClassLoaderInfo(classLoader).loggers.keySet());
    }


    /**
     * Get the value of the specified property in the classloader local configuration.
     *
     * @param name The property name
     */
    @Override
    public String getProperty(String name) {

        // Use a ThreadLocal to work around
        // https://bugs.openjdk.java.net/browse/JDK-8195096
        if (".handlers".equals(name) && !addingLocalRootLogger.get().booleanValue()) {
            return null;
        }

        String prefix = this.prefix.get();
        String result = null;

        // If a prefix is defined look for a prefixed property first
        if (prefix != null) {
            result = findProperty(prefix + name);
        }

        // If there is no prefix or no property match with the prefix try just
        // the name
        if (result == null) {
            result = findProperty(name);
        }

        // Simple property replacement (mostly for folder names)
        if (result != null) {
            result = replace(result);
        }
        return result;
    }


    private synchronized String findProperty(String name) {
        ClassLoader classLoader = getClassLoader();
        ClassLoaderLogInfo info = getClassLoaderInfo(classLoader);
        String result = info.props.getProperty(name);
        // If the property was not found, and the current classloader had no
        // configuration (property list is empty), look for the parent classloader
        // properties.
        if ((result == null) && (info.props.isEmpty())) {
            if (classLoader != null) {
                ClassLoader current = classLoader.getParent();
                while (current != null) {
                    info = classLoaderLoggers.get(current);
                    if (info != null) {
                        result = info.props.getProperty(name);
                        if ((result != null) || (!info.props.isEmpty())) {
                            break;
                        }
                    }
                    current = current.getParent();
                }
            }
            if (result == null) {
                result = super.getProperty(name);
            }
        }
        return result;
    }

    @Override
    public void readConfiguration() throws IOException, SecurityException {
        readConfiguration(getClassLoader());
    }

    @Override
    public void readConfiguration(InputStream is) throws IOException, SecurityException {
        reset();
        readConfiguration(is, getClassLoader());
    }

    @Override
    public synchronized void reset() throws SecurityException {
        Thread thread = Thread.currentThread();
        if (thread.getClass().getName().startsWith("java.util.logging.LogManager$")) {
            // Ignore the call from java.util.logging.LogManager.Cleaner,
            // because we have our own shutdown hook
            return;
        }
        ClassLoader classLoader = getClassLoader();
        ClassLoaderLogInfo clLogInfo = getClassLoaderInfo(classLoader);
        resetLoggers(clLogInfo);
        // Do not call super.reset(). It should be a NO-OP as all loggers should
        // have been registered via this manager. Very rarely a
        // ConcurrentModificationException has been seen in the unit tests when
        // calling super.reset() and that exception could cause the stop of a
        // web application to fail.
    }

    /**
     * Shuts down the logging system.
     */
    public synchronized void shutdown() {
        // The JVM is being shutdown. Make sure all loggers for all class
        // loaders are shutdown
        for (ClassLoaderLogInfo clLogInfo : classLoaderLoggers.values()) {
            resetLoggers(clLogInfo);
        }
    }

    // -------------------------------------------------------- Private Methods
    private void resetLoggers(ClassLoaderLogInfo clLogInfo) {
        // This differs from LogManager#resetLogger() in that we close not all
        // handlers of all loggers, but only those that are present in our
        // ClassLoaderLogInfo#handlers list. That is because our #addLogger(..)
        // method can use handlers from the parent class loaders, and closing
        // handlers that the current class loader does not own would be not
        // good.
        for (Logger logger : clLogInfo.loggers.values()) {
            Handler[] handlers = logger.getHandlers();
            for (Handler handler : handlers) {
                logger.removeHandler(handler);
            }
        }
        for (Handler handler : clLogInfo.handlers.values()) {
            try {
                handler.close();
            } catch (Exception e) {
                // Ignore
            }
        }
        clLogInfo.handlers.clear();
    }

    // ------------------------------------------------------ Protected Methods


    /**
     * Retrieve the configuration associated with the specified classloader. If it does not exist, it will be created.
     * If no class loader is specified, the class loader used to load this class is used.
     *
     * @param classLoader The class loader for which we will retrieve or build the configuration
     *
     * @return the log configuration
     */
    protected synchronized ClassLoaderLogInfo getClassLoaderInfo(ClassLoader classLoader) {

        if (classLoader == null) {
            classLoader = this.getClass().getClassLoader();
        }
        ClassLoaderLogInfo info = classLoaderLoggers.get(classLoader);
        if (info == null) {
            try {
                readConfiguration(classLoader);
            } catch (IOException e) {
                // Ignore
            }
            info = classLoaderLoggers.get(classLoader);
        }
        return info;
    }


    /**
     * Read configuration for the specified classloader.
     *
     * @param classLoader The classloader
     *
     * @throws IOException Error reading configuration
     */
    protected synchronized void readConfiguration(ClassLoader classLoader) throws IOException {

        InputStream is = null;
        // Special case for URL classloaders which are used in containers:
        // only look in the local repositories to avoid redefining loggers 20 times
        if (classLoader instanceof URLClassLoader) {
            URL logConfig = ((URLClassLoader) classLoader).findResource("logging.properties");

            if (null != logConfig) {
                if (Boolean.getBoolean(DEBUG_PROPERTY)) {
                    System.err.println(getClass().getName() + ".readConfiguration(): " +
                            "Found logging.properties at " + logConfig);
                }

                is = classLoader.getResourceAsStream("logging.properties");
            } else {
                if (Boolean.getBoolean(DEBUG_PROPERTY)) {
                    System.err.println(getClass().getName() + ".readConfiguration(): " + "Found no logging.properties");
                }
            }
        }
        if ((is == null) && (classLoader == ClassLoader.getSystemClassLoader())) {
            String configFileStr = System.getProperty("java.util.logging.config.file");
            if (configFileStr != null) {
                try {
                    is = new FileInputStream(replace(configFileStr));
                } catch (IOException e) {
                    System.err.println("Configuration error");
                    e.printStackTrace();
                }
            }
            // Try the default JVM configuration
            if (is == null) {
                File defaultFile = new File(new File(System.getProperty("java.home"), "conf"), "logging.properties");
                try {
                    is = new FileInputStream(defaultFile);
                } catch (IOException e) {
                    System.err.println("Configuration error");
                    e.printStackTrace();
                }
            }
        }

        Logger localRootLogger = new RootLogger();
        if (is == null) {
            // Retrieve the root logger of the parent classloader instead
            ClassLoader current = classLoader.getParent();
            ClassLoaderLogInfo info = null;
            while (current != null && info == null) {
                info = getClassLoaderInfo(current);
                current = current.getParent();
            }
            if (info != null) {
                localRootLogger.setParent(info.rootNode.logger);
            }
        }
        ClassLoaderLogInfo info = new ClassLoaderLogInfo(new LogNode(null, localRootLogger));
        classLoaderLoggers.put(classLoader, info);

        if (is != null) {
            readConfiguration(is, classLoader);
        }

        if (localRootLogger.getParent() == null && localRootLogger.getLevel() == null) {
            localRootLogger.setLevel(Level.INFO);
        }
        try {
            // Use a ThreadLocal to work around
            // https://bugs.openjdk.java.net/browse/JDK-8195096
            addingLocalRootLogger.set(Boolean.TRUE);
            addLogger(localRootLogger);
        } finally {
            addingLocalRootLogger.set(Boolean.FALSE);
        }
    }


    /**
     * Load specified configuration.
     *
     * @param is          InputStream to the properties file
     * @param classLoader for which the configuration will be loaded
     *
     * @throws IOException If something wrong happens during loading
     */
    protected synchronized void readConfiguration(InputStream is, ClassLoader classLoader) throws IOException {

        ClassLoaderLogInfo info = classLoaderLoggers.get(classLoader);

        try {
            info.props.load(is);
        } catch (IOException e) {
            // Report error
            System.err.println("Configuration error");
            e.printStackTrace();
        } finally {
            try {
                is.close();
            } catch (IOException ioe) {
                // Ignore
            }
        }

        // Create handlers for the root logger of this classloader
        String rootHandlers = info.props.getProperty(".handlers");
        String handlers = info.props.getProperty("handlers");
        Logger localRootLogger = info.rootNode.logger;
        if (handlers != null) {
            StringTokenizer tok = new StringTokenizer(handlers, ",");
            while (tok.hasMoreTokens()) {
                String handlerName = (tok.nextToken().trim());
                String handlerClassName = handlerName;
                String prefix = "";
                if (handlerClassName.length() <= 0) {
                    continue;
                }
                // Parse and remove a prefix (prefix start with a digit, such as
                // "10WebappFooHandler.")
                if (Character.isDigit(handlerClassName.charAt(0))) {
                    int pos = handlerClassName.indexOf('.');
                    if (pos >= 0) {
                        prefix = handlerClassName.substring(0, pos + 1);
                        handlerClassName = handlerClassName.substring(pos + 1);
                    }
                }
                try {
                    this.prefix.set(prefix);
                    Handler handler = (Handler) classLoader.loadClass(handlerClassName).getConstructor().newInstance();
                    // The specification strongly implies all configuration should be done
                    // during the creation of the handler object.
                    // This includes setting level, filter, formatter and encoding.
                    this.prefix.set(null);
                    info.handlers.put(handlerName, handler);
                    if (rootHandlers == null) {
                        localRootLogger.addHandler(handler);
                    }
                } catch (Exception e) {
                    // Report error
                    System.err.println("Handler error");
                    e.printStackTrace();
                }
            }

        }

    }


    /**
     * System property replacement in the given string.
     *
     * @param str The original string
     *
     * @return the modified string
     */
    protected String replace(String str) {
        String result = str;
        int pos_start = str.indexOf("${");
        if (pos_start >= 0) {
            StringBuilder builder = new StringBuilder();
            int pos_end = -1;
            while (pos_start >= 0) {
                builder.append(str, pos_end + 1, pos_start);
                pos_end = str.indexOf('}', pos_start + 2);
                if (pos_end < 0) {
                    pos_end = pos_start - 1;
                    break;
                }
                String propName = str.substring(pos_start + 2, pos_end);

                String replacement = replaceWebApplicationProperties(propName);
                if (replacement == null) {
                    replacement = propName.length() > 0 ? System.getProperty(propName) : null;
                }
                if (replacement != null) {
                    builder.append(replacement);
                } else {
                    builder.append(str, pos_start, pos_end + 1);
                }
                pos_start = str.indexOf("${", pos_end + 1);
            }
            builder.append(str, pos_end + 1, str.length());
            result = builder.toString();
        }
        return result;
    }


    private String replaceWebApplicationProperties(String propName) {
        ClassLoader cl = getClassLoader();
        if (cl instanceof WebappProperties) {
            WebappProperties wProps = (WebappProperties) cl;
            if ("classloader.webappName".equals(propName)) {
                return wProps.getWebappName();
            } else if ("classloader.hostName".equals(propName)) {
                return wProps.getHostName();
            } else if ("classloader.serviceName".equals(propName)) {
                return wProps.getServiceName();
            } else {
                return null;
            }
        } else {
            return null;
        }
    }


    /**
     * Obtain the class loader to use to lookup loggers, obtain configuration etc. The search order is:
     * <ol>
     * <li>Thread.currentThread().getContextClassLoader()</li>
     * <li>The class laoder of this class</li>
     * </ol>
     *
     * @return The class loader to use to lookup loggers, obtain configuration etc.
     */
    static ClassLoader getClassLoader() {
        ClassLoader result = Thread.currentThread().getContextClassLoader();
        if (result == null) {
            result = ClassLoaderLogManager.class.getClassLoader();
        }
        return result;
    }


    // ---------------------------------------------------- LogNode Inner Class

    protected static final class LogNode {
        Logger logger;

        final Map<String, LogNode> children = new HashMap<>();

        final LogNode parent;

        LogNode(final LogNode parent, final Logger logger) {
            this.parent = parent;
            this.logger = logger;
        }

        LogNode(final LogNode parent) {
            this(parent, null);
        }

        LogNode findNode(String name) {
            LogNode currentNode = this;
            if (logger.getName().equals(name)) {
                return this;
            }
            while (name != null) {
                final int dotIndex = name.indexOf('.');
                final String nextName;
                if (dotIndex < 0) {
                    nextName = name;
                    name = null;
                } else {
                    nextName = name.substring(0, dotIndex);
                    name = name.substring(dotIndex + 1);
                }
                LogNode childNode = currentNode.children.get(nextName);
                if (childNode == null) {
                    childNode = new LogNode(currentNode);
                    currentNode.children.put(nextName, childNode);
                }
                currentNode = childNode;
            }
            return currentNode;
        }

        Logger findParentLogger() {
            Logger logger = null;
            LogNode node = parent;
            while (node != null && logger == null) {
                logger = node.logger;
                node = node.parent;
            }
            return logger;
        }

        void setParentLogger(final Logger parent) {
            for (final LogNode childNode : children.values()) {
                if (childNode.logger == null) {
                    childNode.setParentLogger(parent);
                } else {
                    childNode.logger.setParent(parent);
                }
            }
        }

    }


    // -------------------------------------------- ClassLoaderInfo Inner Class


    protected static final class ClassLoaderLogInfo {
        final LogNode rootNode;
        final Map<String, Logger> loggers = new ConcurrentHashMap<>();
        final Map<String, Handler> handlers = new HashMap<>();
        final Properties props = new Properties();

        ClassLoaderLogInfo(final LogNode rootNode) {
            this.rootNode = rootNode;
        }

    }


    // ------------------------------------------------- RootLogger Inner Class


    /**
     * This class is needed to instantiate the root of each per classloader hierarchy.
     */
    protected static class RootLogger extends Logger {
        public RootLogger() {
            super("", null);
        }
    }


}
