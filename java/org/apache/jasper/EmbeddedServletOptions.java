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

package org.apache.jasper;

import java.io.File;
import java.util.Enumeration;
import java.util.Map;
import java.util.Properties;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.jsp.tagext.TagLibraryInfo;

import org.apache.jasper.compiler.JspConfig;
import org.apache.jasper.compiler.Localizer;
import org.apache.jasper.compiler.TagPluginManager;
import org.apache.jasper.compiler.TldCache;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * A class to hold all init parameters specific to the JSP engine.
 *
 * @author Anil K. Vijendran
 * @author Hans Bergsten
 * @author Pierre Delisle
 */
public final class EmbeddedServletOptions implements Options {

    // Logger
    private final Log log = LogFactory.getLog(EmbeddedServletOptions.class); // must not be static

    private Properties settings = new Properties();

    /**
     * Is Jasper being used in development mode?
     */
    private boolean development = true;

    /**
     * Should Ant fork its java compiles of JSP pages.
     */
    public boolean fork = true;

    /**
     * Do you want to keep the generated Java files around?
     */
    private boolean keepGenerated = true;

    /**
     * How should template text that consists entirely of whitespace be handled?
     */
    private TrimSpacesOption trimSpaces = TrimSpacesOption.FALSE;

    /**
     * Determines whether tag handler pooling is enabled.
     */
    private boolean isPoolingEnabled = true;

    /**
     * Do you want support for "mapped" files? This will generate
     * servlet that has a print statement per line of the JSP file.
     * This seems like a really nice feature to have for debugging.
     */
    private boolean mappedFile = true;

    /**
     * Do we want to include debugging information in the class file?
     */
    private boolean classDebugInfo = true;

    /**
     * Background compile thread check interval in seconds.
     */
    private int checkInterval = 0;

    /**
     * Is the generation of SMAP info for JSR45 debugging suppressed?
     */
    private boolean isSmapSuppressed = false;

    /**
     * Should SMAP info for JSR45 debugging be dumped to a file?
     */
    private boolean isSmapDumped = false;

    /**
     * Are Text strings to be generated as char arrays?
     */
    private boolean genStringAsCharArray = false;

    private boolean errorOnUseBeanInvalidClassAttribute = true;

    /**
     * I want to see my generated servlets. Which directory are they
     * in?
     */
    private File scratchDir;

    /**
     * Need to have this as is for versions 4 and 5 of IE. Can be set from
     * the initParams so if it changes in the future all that is needed is
     * to have a jsp initParam of type ieClassId="<value>"
     */
    private String ieClassId = "clsid:8AD9C840-044E-11D1-B3E9-00805F499D93";

    /**
     * What classpath should I use while compiling generated servlets?
     */
    private String classpath = null;

    /**
     * Compiler to use.
     */
    private String compiler = null;

    /**
     * Compiler target VM.
     */
    private String compilerTargetVM = "1.8";

    /**
     * The compiler source VM.
     */
    private String compilerSourceVM = "1.8";

    /**
     * The compiler class name.
     */
    private String compilerClassName = null;

    /**
     * Cache for the TLD URIs, resource paths and parsed files.
     */
    private TldCache tldCache = null;

    /**
     * Jsp config information
     */
    private JspConfig jspConfig = null;

    /**
     * TagPluginManager
     */
    private TagPluginManager tagPluginManager = null;

    /**
     * Java platform encoding to generate the JSP
     * page servlet.
     */
    private String javaEncoding = "UTF-8";

    /**
     * Modification test interval.
     */
    private int modificationTestInterval = 4;

    /**
     * Is re-compilation attempted immediately after a failure?
     */
    private boolean recompileOnFail = false;

    /**
     * Is generation of X-Powered-By response header enabled/disabled?
     */
    private boolean xpoweredBy;

    /**
     * Should we include a source fragment in exception messages, which could be displayed
     * to the developer ?
     */
    private boolean displaySourceFragment = true;


    /**
     * The maximum number of loaded jsps per web-application. If there are more
     * jsps loaded, they will be unloaded.
     */
    private int maxLoadedJsps = -1;

    /**
     * The idle time in seconds after which a JSP is unloaded.
     * If unset or less or equal than 0, no jsps are unloaded.
     */
    private int jspIdleTimeout = -1;

    /**
     * Should JSP.1.6 be applied strictly to attributes defined using scriptlet
     * expressions?
     */
    private boolean strictQuoteEscaping = true;

    /**
     * When EL is used in JSP attribute values, should the rules for quoting of
     * attributes described in JSP.1.6 be applied to the expression?
     */
    private boolean quoteAttributeEL = true;

    public String getProperty(String name ) {
        return settings.getProperty( name );
    }

    public void setProperty(String name, String value ) {
        if (name != null && value != null){
            settings.setProperty( name, value );
        }
    }

    public void setQuoteAttributeEL(boolean b) {
        this.quoteAttributeEL = b;
    }

    @Override
    public boolean getQuoteAttributeEL() {
        return quoteAttributeEL;
    }

    /**
     * Are we keeping generated code around?
     */
    @Override
    public boolean getKeepGenerated() {
        return keepGenerated;
    }

    @Override
    public TrimSpacesOption getTrimSpaces() {
        return trimSpaces;
    }

    @Override
    public boolean isPoolingEnabled() {
        return isPoolingEnabled;
    }

    /**
     * Are we supporting HTML mapped servlets?
     */
    @Override
    public boolean getMappedFile() {
        return mappedFile;
    }

    /**
     * Should class files be compiled with debug information?
     */
    @Override
    public boolean getClassDebugInfo() {
        return classDebugInfo;
    }

    /**
     * Background JSP compile thread check interval
     */
    @Override
    public int getCheckInterval() {
        return checkInterval;
    }

    /**
     * Modification test interval.
     */
    @Override
    public int getModificationTestInterval() {
        return modificationTestInterval;
    }

    /**
     * Re-compile on failure.
     */
    @Override
    public boolean getRecompileOnFail() {
        return recompileOnFail;
    }

    /**
     * Is Jasper being used in development mode?
     */
    @Override
    public boolean getDevelopment() {
        return development;
    }

    /**
     * Is the generation of SMAP info for JSR45 debugging suppressed?
     */
    @Override
    public boolean isSmapSuppressed() {
        return isSmapSuppressed;
    }

    /**
     * Should SMAP info for JSR45 debugging be dumped to a file?
     */
    @Override
    public boolean isSmapDumped() {
        return isSmapDumped;
    }

    /**
     * Are Text strings to be generated as char arrays?
     */
    @Override
    public boolean genStringAsCharArray() {
        return this.genStringAsCharArray;
    }

    /**
     * Class ID for use in the plugin tag when the browser is IE.
     */
    @Override
    public String getIeClassId() {
        return ieClassId;
    }

    /**
     * What is my scratch dir?
     */
    @Override
    public File getScratchDir() {
        return scratchDir;
    }

    /**
     * What classpath should I use while compiling the servlets
     * generated from JSP files?
     */
    @Override
    public String getClassPath() {
        return classpath;
    }

    /**
     * Is generation of X-Powered-By response header enabled/disabled?
     */
    @Override
    public boolean isXpoweredBy() {
        return xpoweredBy;
    }

    /**
     * Compiler to use.
     */
    @Override
    public String getCompiler() {
        return compiler;
    }

    /**
     * @see Options#getCompilerTargetVM
     */
    @Override
    public String getCompilerTargetVM() {
        return compilerTargetVM;
    }

    /**
     * @see Options#getCompilerSourceVM
     */
    @Override
    public String getCompilerSourceVM() {
        return compilerSourceVM;
    }

    /**
     * Java compiler class to use.
     */
    @Override
    public String getCompilerClassName() {
        return compilerClassName;
    }

    @Override
    public boolean getErrorOnUseBeanInvalidClassAttribute() {
        return errorOnUseBeanInvalidClassAttribute;
    }

    public void setErrorOnUseBeanInvalidClassAttribute(boolean b) {
        errorOnUseBeanInvalidClassAttribute = b;
    }

    @Override
    public TldCache getTldCache() {
        return tldCache;
    }

    public void setTldCache(TldCache tldCache) {
        this.tldCache = tldCache;
    }

    @Override
    public String getJavaEncoding() {
        return javaEncoding;
    }

    @Override
    public boolean getFork() {
        return fork;
    }

    @Override
    public JspConfig getJspConfig() {
        return jspConfig;
    }

    @Override
    public TagPluginManager getTagPluginManager() {
        return tagPluginManager;
    }

    @Override
    public boolean isCaching() {
        return false;
    }

    @Override
    public Map<String, TagLibraryInfo> getCache() {
        return null;
    }

    /**
     * Should we include a source fragment in exception messages, which could be displayed
     * to the developer ?
     */
    @Override
    public boolean getDisplaySourceFragment() {
        return displaySourceFragment;
    }

    /**
     * Should jsps be unloaded if to many are loaded?
     * If set to a value greater than 0 eviction of jsps is started. Default: -1
     */
    @Override
    public int getMaxLoadedJsps() {
        return maxLoadedJsps;
    }

    /**
     * Should any jsps be unloaded when being idle for this time in seconds?
     * If set to a value greater than 0 eviction of jsps is started. Default: -1
     */
    @Override
    public int getJspIdleTimeout() {
        return jspIdleTimeout;
    }

    @Override
    public boolean getStrictQuoteEscaping() {
        return strictQuoteEscaping;
    }

    /**
     * Create an EmbeddedServletOptions object using data available from
     * ServletConfig and ServletContext.
     * @param config The Servlet config
     * @param context The Servlet context
     */
    public EmbeddedServletOptions(ServletConfig config, ServletContext context) {
        Enumeration<String> enumeration = config.getInitParameterNames();

        while (enumeration.hasMoreElements()) {
            String key = enumeration.nextElement();
            String value = config.getInitParameter(key);
            setProperty(key, value);
        }

        setKeepGenerated(config);
        setTrimSpaces(config);
        setPoolingEnabled(config);
        setMappedFile(config);
        setDebugInfo(config);
        setCheckInterval(config);
        setModificationTestInterval(config);
        setRecompileInFail(config);
        setDevelopment(config);
        setSuppressMap(config);
        setDumpSmap(config);
        setGenStingAsArray(config);
        setErrorOnUseBeanInvalidClassAttribute(config);
        setIeClassId(config);
        setClasspath(config);

        setScratchDir(config, context);

        if (this.scratchDir == null) {
            log.fatal(Localizer.getMessage("jsp.error.no.scratch.dir"));
            return;
        }

        if (!(scratchDir.exists() && scratchDir.canRead() &&
                scratchDir.canWrite() && scratchDir.isDirectory())) {
            log.fatal(Localizer.getMessage("jsp.error.bad.scratch.dir",
                    scratchDir.getAbsolutePath()));
        }

        this.compiler = config.getInitParameter("compiler");

        setCompilerTargetVM(config);
        setCompilerSourceVM(config);
        setJavaEncoding(config);
        setCompilerClassName(config);
        setFork(config);
        setXpoweredBy(config);
        setDisplaySourceFragment(config);
        setMaxLoadedJsps(config);
        setJspIdleTimeout(config);
        setStrictQuoteEscaping(config);
        setQuoteAttributeEL(config);

        // Setup the global Tag Libraries location cache for this
        // web-application.
        tldCache = TldCache.getInstance(context);

        // Setup the jsp config info for this web app.
        jspConfig = new JspConfig(context);

        // Create a Tag plugin instance
        tagPluginManager = new TagPluginManager(context);
    }

    private void setQuoteAttributeEL(ServletConfig config) {
        String quoteAttributeElValue = config.getInitParameter("quoteAttributeEL");

        if (quoteAttributeElValue != null) {
            if (quoteAttributeElValue.equalsIgnoreCase("true")) {
                this.quoteAttributeEL = true;
            } else if (quoteAttributeElValue.equalsIgnoreCase("false")) {
                this.quoteAttributeEL = false;
            } else {
                if (log.isWarnEnabled()) {
                    log.warn(Localizer.getMessage("jsp.warning.quoteAttributeEL"));
                }
            }
        }
    }

    private void setStrictQuoteEscaping(ServletConfig config) {
        String strictQuoteEscapingValue = config.getInitParameter("strictQuoteEscaping");

        if (strictQuoteEscapingValue != null) {
            if (strictQuoteEscapingValue.equalsIgnoreCase("true")) {
                this.strictQuoteEscaping = true;
            } else if (strictQuoteEscapingValue.equalsIgnoreCase("false")) {
                this.strictQuoteEscaping = false;
            } else {
                if (log.isWarnEnabled()) {
                    log.warn(Localizer.getMessage("jsp.warning.strictQuoteEscaping"));
                }
            }
        }
    }

    private void setJspIdleTimeout(ServletConfig config) {
        String jspIdleTimeoutValue = config.getInitParameter("jspIdleTimeout");

        if (jspIdleTimeoutValue != null) {
            try {
                this.jspIdleTimeout = Integer.parseInt(jspIdleTimeoutValue);
            } catch(NumberFormatException ex) {
                if (log.isWarnEnabled()) {
                    log.warn(Localizer.getMessage("jsp.warning.jspIdleTimeout", ""+this.jspIdleTimeout));
                }
            }
        }
    }

    private void setMaxLoadedJsps(ServletConfig config) {
        String maxLoadedJspsValue = config.getInitParameter("maxLoadedJsps");

        if (maxLoadedJspsValue != null) {
            try {
                this.maxLoadedJsps = Integer.parseInt(maxLoadedJspsValue);
            } catch(NumberFormatException ex) {
                if (log.isWarnEnabled()) {
                    log.warn(Localizer.getMessage("jsp.warning.maxLoadedJsps", ""+this.maxLoadedJsps));
                }
            }
        }
    }

    private void setDisplaySourceFragment(ServletConfig config) {
        String displaySourceFragmentValue = config.getInitParameter("displaySourceFragment");

        if (displaySourceFragmentValue != null) {
            if (displaySourceFragmentValue.equalsIgnoreCase("true")) {
                this.displaySourceFragment = true;
            } else if (displaySourceFragmentValue.equalsIgnoreCase("false")) {
                this.displaySourceFragment = false;
            } else {
                if (log.isWarnEnabled()) {
                    log.warn(Localizer.getMessage("jsp.warning.displaySourceFragment"));
                }
            }
        }
    }

    private void setXpoweredBy(ServletConfig config) {
        String xpoweredByValue = config.getInitParameter("xpoweredBy");

        if (xpoweredByValue != null) {
            if (xpoweredByValue.equalsIgnoreCase("true")) {
                this.xpoweredBy = true;
            } else if (xpoweredByValue.equalsIgnoreCase("false")) {
                this.xpoweredBy = false;
            } else {
                if (log.isWarnEnabled()) {
                    log.warn(Localizer.getMessage("jsp.warning.xpoweredBy"));
                }
            }
        }
    }

    private void setFork(ServletConfig config) {
        String forkValue = config.getInitParameter("fork");

        if (forkValue != null) {
            if (forkValue.equalsIgnoreCase("true")) {
                this.fork = true;
            } else if (forkValue.equalsIgnoreCase("false")) {
                this.fork = false;
            } else {
                if (log.isWarnEnabled()) {
                    log.warn(Localizer.getMessage("jsp.warning.fork"));
                }
            }
        }
    }

    private void setCompilerClassName(ServletConfig config) {
        String compilerClassNameValue = config.getInitParameter("compilerClassName");

        if (compilerClassNameValue != null) {
            this.compilerClassName = compilerClassNameValue;
        }
    }

    private void setJavaEncoding(ServletConfig config) {
        String javaEncodingValue = config.getInitParameter("javaEncoding");

        if (javaEncodingValue != null) {
            this.javaEncoding = javaEncodingValue;
        }
    }

    private void setCompilerSourceVM(ServletConfig config) {
        String compilerSourceVmValue = config.getInitParameter("compilerSourceVM");

        if(compilerSourceVmValue != null) {
            this.compilerSourceVM = compilerSourceVmValue;
        }
    }

    private void setCompilerTargetVM(ServletConfig config) {
        String compilerTargetVmValue = config.getInitParameter("compilerTargetVM");

        if(compilerTargetVmValue != null) {
            this.compilerTargetVM = compilerTargetVmValue;
        }
    }

    private void setScratchDir(ServletConfig config, ServletContext context) {
        String dir = config.getInitParameter("scratchdir");

        if (dir != null && Constants.IS_SECURITY_ENABLED) {
            log.info(Localizer.getMessage("jsp.info.ignoreSetting", "scratchdir", dir));
            dir = null;
        }

        if (dir != null) {
            scratchDir = new File(dir);
        } else {
            // First try the Servlet 2.2 javax.servlet.context.tempdir property
            scratchDir = (File) context.getAttribute(ServletContext.TEMPDIR);
            if (scratchDir == null) {
                // Not running in a Servlet 2.2 container.
                // Try to get the JDK 1.2 java.io.tmpdir property
                dir = System.getProperty("java.io.tmpdir");
                if (dir != null)
                    scratchDir = new File(dir);
            }
        }
    }

    private void setClasspath(ServletConfig config) {
        String classpathValue = config.getInitParameter("classpath");

        if (classpathValue != null) {
            this.classpath = classpathValue;
        }
    }

    private void setIeClassId(ServletConfig config) {
        String ieClassIdValue = config.getInitParameter("ieClassId");

        if (ieClassIdValue != null) {
            this.ieClassId = ieClassIdValue;
        }
    }

    private void setErrorOnUseBeanInvalidClassAttribute(ServletConfig config) {
        String errorOnUseBeanInvalidClassAttributeValue = config.getInitParameter("errorOnUseBeanInvalidClassAttribute");

        if (errorOnUseBeanInvalidClassAttributeValue != null) {
            if (errorOnUseBeanInvalidClassAttributeValue.equalsIgnoreCase("true")) {
                this.errorOnUseBeanInvalidClassAttribute = true;
            } else if (errorOnUseBeanInvalidClassAttributeValue.equalsIgnoreCase("false")) {
                this.errorOnUseBeanInvalidClassAttribute = false;
            } else {
                if (log.isWarnEnabled()) {
                    log.warn(Localizer.getMessage("jsp.warning.errBean"));
                }
            }
        }
    }

    private void setGenStingAsArray(ServletConfig config) {
        String genCharArrayValue = config.getInitParameter("genStringAsCharArray");

        if (genCharArrayValue != null) {
            if (genCharArrayValue.equalsIgnoreCase("true")) {
                genStringAsCharArray = true;
            } else if (genCharArrayValue.equalsIgnoreCase("false")) {
                genStringAsCharArray = false;
            } else {
                if (log.isWarnEnabled()) {
                    log.warn(Localizer.getMessage("jsp.warning.genchararray"));
                }
            }
        }
    }

    private void setDumpSmap(ServletConfig config) {
        String dumpSmapValue = config.getInitParameter("dumpSmap");

        if (dumpSmapValue != null) {
            if (dumpSmapValue.equalsIgnoreCase("true")) {
                isSmapDumped = true;
            } else if (dumpSmapValue.equalsIgnoreCase("false")) {
                isSmapDumped = false;
            } else {
                if (log.isWarnEnabled()) {
                    log.warn(Localizer.getMessage("jsp.warning.dumpSmap"));
                }
            }
        }
    }

    private void setSuppressMap(ServletConfig config) {
        String suppressSmapValue = config.getInitParameter("suppressSmap");

        if (suppressSmapValue != null) {
            if (suppressSmapValue.equalsIgnoreCase("true")) {
                isSmapSuppressed = true;
            } else if (suppressSmapValue.equalsIgnoreCase("false")) {
                isSmapSuppressed = false;
            } else {
                if (log.isWarnEnabled()) {
                    log.warn(Localizer.getMessage("jsp.warning.suppressSmap"));
                }
            }
        }
    }

    private void setDevelopment(ServletConfig config) {
        String developmentValue = config.getInitParameter("development");

        if (developmentValue != null) {
            if (developmentValue.equalsIgnoreCase("true")) {
                this.development = true;
            } else if (developmentValue.equalsIgnoreCase("false")) {
                this.development = false;
            } else {
                if (log.isWarnEnabled()) {
                    log.warn(Localizer.getMessage("jsp.warning.development"));
                }
            }
        }
    }

    private void setRecompileInFail(ServletConfig config) {
        String recompileOnFailValue = config.getInitParameter("recompileOnFail");

        if (recompileOnFailValue != null) {
            if (recompileOnFailValue.equalsIgnoreCase("true")) {
                this.recompileOnFail = true;
            } else if (recompileOnFailValue.equalsIgnoreCase("false")) {
                this.recompileOnFail = false;
            } else {
                if (log.isWarnEnabled()) {
                    log.warn(Localizer.getMessage("jsp.warning.recompileOnFail"));
                }
            }
        }
    }

    private void setModificationTestInterval(ServletConfig config) {
        String modificationTestIntervalValue = config.getInitParameter("modificationTestInterval");

        if (modificationTestIntervalValue != null) {
            try {
                this.modificationTestInterval = Integer.parseInt(modificationTestIntervalValue);
            } catch(NumberFormatException ex) {
                if (log.isWarnEnabled()) {
                    log.warn(Localizer.getMessage("jsp.warning.modificationTestInterval"));
                }
            }
        }
    }

    private void setCheckInterval(ServletConfig config) {
        String checkIntervalValue = config.getInitParameter("checkInterval");

        if (checkIntervalValue != null) {
            try {
                this.checkInterval = Integer.parseInt(checkIntervalValue);
            } catch(NumberFormatException ex) {
                if (log.isWarnEnabled()) {
                    log.warn(Localizer.getMessage("jsp.warning.checkInterval"));
                }
            }
        }
    }

    private void setDebugInfo(ServletConfig config) {
        String debugInfoValue = config.getInitParameter("classdebuginfo");

        if (debugInfoValue != null) {
            if (debugInfoValue.equalsIgnoreCase("true")) {
                this.classDebugInfo  = true;
            } else if (debugInfoValue.equalsIgnoreCase("false")) {
                this.classDebugInfo  = false;
            } else {
                if (log.isWarnEnabled()) {
                    log.warn(Localizer.getMessage("jsp.warning.classDebugInfo"));
                }
            }
        }
    }

    private void setMappedFile(ServletConfig config) {
        String mappedfileValue = config.getInitParameter("mappedfile");

        if (mappedfileValue != null) {
            if (mappedfileValue.equalsIgnoreCase("true")) {
                this.mappedFile = true;
            } else if (mappedfileValue.equalsIgnoreCase("false")) {
                this.mappedFile = false;
            } else {
                if (log.isWarnEnabled()) {
                    log.warn(Localizer.getMessage("jsp.warning.mappedFile"));
                }
            }
        }
    }

    private void setPoolingEnabled(ServletConfig config) {
        this.isPoolingEnabled = true;

        String poolingEnabledValue = config.getInitParameter("enablePooling");

        if (poolingEnabledValue != null
                && !poolingEnabledValue.equalsIgnoreCase("true")) {
            if (poolingEnabledValue.equalsIgnoreCase("false")) {
                this.isPoolingEnabled = false;
            } else {
                if (log.isWarnEnabled()) {
                    log.warn(Localizer.getMessage("jsp.warning.enablePooling"));
                }
            }
        }
    }

    private void setTrimSpaces(ServletConfig config) {
        String trimSpacesValue = config.getInitParameter("trimSpaces");

        if (trimSpacesValue != null) {
            try {
                this.trimSpaces = TrimSpacesOption.valueOf(trimSpacesValue.toUpperCase());
            } catch (IllegalArgumentException iae) {
                if (log.isWarnEnabled()) {
                    log.warn(Localizer.getMessage("jsp.warning.trimspaces"), iae);
                }
            }
        }
    }

    private void setKeepGenerated(ServletConfig config) {
        String keepgeneratedValue = config.getInitParameter("keepgenerated");

        if (keepgeneratedValue != null) {
            if (keepgeneratedValue.equalsIgnoreCase("true")) {
                this.keepGenerated = true;
            } else if (keepgeneratedValue.equalsIgnoreCase("false")) {
                this.keepGenerated = false;
            } else {
                if (log.isWarnEnabled()) {
                    log.warn(Localizer.getMessage("jsp.warning.keepgenerated"));
                }
            }
        }
    }

}