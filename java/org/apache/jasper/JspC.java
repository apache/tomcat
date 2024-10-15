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

import java.io.BufferedReader;
import java.io.CharArrayWriter;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import jakarta.servlet.jsp.JspFactory;
import jakarta.servlet.jsp.tagext.TagLibraryInfo;

import org.apache.jasper.compiler.Compiler;
import org.apache.jasper.compiler.JspConfig;
import org.apache.jasper.compiler.JspRuntimeContext;
import org.apache.jasper.compiler.Localizer;
import org.apache.jasper.compiler.TagPluginManager;
import org.apache.jasper.compiler.TldCache;
import org.apache.jasper.runtime.JspFactoryImpl;
import org.apache.jasper.servlet.JspCServletContext;
import org.apache.jasper.servlet.TldScanner;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tools.ant.AntClassLoader;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.util.FileUtils;
import org.xml.sax.SAXException;

/**
 * Shell for the jspc compiler.  Handles all options associated with the
 * command line and creates compilation contexts which it then compiles
 * according to the specified options.
 *
 * This version can process files from a _single_ webapp at once, i.e.
 * a single docbase can be specified.
 *
 * It can be used as an Ant task using:
 * <pre>
 *   &lt;taskdef classname="org.apache.jasper.JspC" name="jasper" &gt;
 *      &lt;classpath&gt;
 *          &lt;pathelement location="${java.home}/../lib/tools.jar"/&gt;
 *          &lt;fileset dir="${ENV.CATALINA_HOME}/lib"&gt;
 *              &lt;include name="*.jar"/&gt;
 *          &lt;/fileset&gt;
 *          &lt;path refid="myjars"/&gt;
 *       &lt;/classpath&gt;
 *  &lt;/taskdef&gt;
 *
 *  &lt;jasper verbose="0"
 *           package="my.package"
 *           uriroot="${webapps.dir}/${webapp.name}"
 *           webXmlFragment="${build.dir}/generated_web.xml"
 *           outputDir="${webapp.dir}/${webapp.name}/WEB-INF/src/my/package" /&gt;
 * </pre>
 *
 * @author Danno Ferrin
 * @author Pierre Delisle
 * @author Costin Manolache
 * @author Yoav Shapira
 */
public class JspC extends Task implements Options {

    static {
        // the Validator uses this to access the EL ExpressionFactory
        JspFactory.setDefaultFactory(new JspFactoryImpl());
    }

    // Logger
    private static final Log log = LogFactory.getLog(JspC.class);

    protected static final String SWITCH_VERBOSE = "-v";
    protected static final String SWITCH_HELP = "-help";
    protected static final String SWITCH_OUTPUT_DIR = "-d";
    protected static final String SWITCH_PACKAGE_NAME = "-p";
    protected static final String SWITCH_CACHE = "-cache";
    protected static final String SWITCH_CLASS_NAME = "-c";
    protected static final String SWITCH_FULL_STOP = "--";
    protected static final String SWITCH_COMPILE = "-compile";
    protected static final String SWITCH_FAIL_FAST = "-failFast";
    protected static final String SWITCH_SOURCE = "-source";
    protected static final String SWITCH_TARGET = "-target";
    protected static final String SWITCH_URI_BASE = "-uribase";
    protected static final String SWITCH_URI_ROOT = "-uriroot";
    protected static final String SWITCH_FILE_WEBAPP = "-webapp";
    protected static final String SWITCH_WEBAPP_INC = "-webinc";
    protected static final String SWITCH_WEBAPP_FRG = "-webfrg";
    protected static final String SWITCH_WEBAPP_XML = "-webxml";
    protected static final String SWITCH_WEBAPP_XML_ENCODING = "-webxmlencoding";
    protected static final String SWITCH_ADD_WEBAPP_XML_MAPPINGS = "-addwebxmlmappings";
    protected static final String SWITCH_MAPPED = "-mapped";
    protected static final String SWITCH_XPOWERED_BY = "-xpoweredBy";
    protected static final String SWITCH_TRIM_SPACES = "-trimSpaces";
    protected static final String SWITCH_CLASSPATH = "-classpath";
    protected static final String SWITCH_DIE = "-die";
    protected static final String SWITCH_POOLING = "-poolingEnabled";
    protected static final String SWITCH_ENCODING = "-javaEncoding";
    protected static final String SWITCH_SMAP = "-smap";
    protected static final String SWITCH_DUMP_SMAP = "-dumpsmap";
    protected static final String SWITCH_VALIDATE_TLD = "-validateTld";
    protected static final String SWITCH_VALIDATE_XML = "-validateXml";
    protected static final String SWITCH_NO_BLOCK_EXTERNAL = "-no-blockExternal";
    protected static final String SWITCH_NO_STRICT_QUOTE_ESCAPING = "-no-strictQuoteEscaping";
    protected static final String SWITCH_QUOTE_ATTRIBUTE_EL = "-quoteAttributeEL";
    protected static final String SWITCH_NO_QUOTE_ATTRIBUTE_EL = "-no-quoteAttributeEL";
    protected static final String SWITCH_THREAD_COUNT = "-threadCount";
    protected static final String SHOW_SUCCESS ="-s";
    protected static final String LIST_ERRORS = "-l";
    protected static final int INC_WEBXML = 10;
    protected static final int FRG_WEBXML = 15;
    protected static final int ALL_WEBXML = 20;
    protected static final int DEFAULT_DIE_LEVEL = 1;
    protected static final int NO_DIE_LEVEL = 0;
    protected static final Set<String> insertBefore = new HashSet<>();

    static {
        insertBefore.add("</web-app>");
        insertBefore.add("<servlet-mapping>");
        insertBefore.add("<session-config>");
        insertBefore.add("<mime-mapping>");
        insertBefore.add("<welcome-file-list>");
        insertBefore.add("<error-page>");
        insertBefore.add("<taglib>");
        insertBefore.add("<resource-env-ref>");
        insertBefore.add("<resource-ref>");
        insertBefore.add("<security-constraint>");
        insertBefore.add("<login-config>");
        insertBefore.add("<security-role>");
        insertBefore.add("<env-entry>");
        insertBefore.add("<ejb-ref>");
        insertBefore.add("<ejb-local-ref>");
    }

    protected String classPath = null;
    protected ClassLoader loader = null;
    protected TrimSpacesOption trimSpaces = TrimSpacesOption.FALSE;
    protected boolean genStringAsCharArray = false;
    protected boolean validateTld;
    protected boolean validateXml;
    protected boolean blockExternal = true;
    protected boolean strictQuoteEscaping = true;
    protected boolean quoteAttributeEL = true;
    protected boolean xpoweredBy;
    protected boolean mappedFile = false;
    protected boolean poolingEnabled = true;
    protected File scratchDir;

    protected String targetPackage;
    protected String targetClassName;
    protected String uriBase;
    protected String uriRoot;
    protected int dieLevel;
    protected boolean helpNeeded = false;
    protected boolean compile = false;
    protected boolean failFast = false;
    protected boolean smapSuppressed = true;
    protected boolean smapDumped = false;
    protected boolean caching = true;
    protected final Map<String, TagLibraryInfo> cache = new HashMap<>();

    protected String compiler = null;

    protected String compilerTargetVM = "21";
    protected String compilerSourceVM = "21";

    protected boolean classDebugInfo = true;

    /**
     * Throw an exception if there's a compilation error, or swallow it.
     * Default is true to preserve old behavior.
     */
    protected boolean failOnError = true;

    /**
     * Should a separate process be forked to perform the compilation?
     */
    private boolean fork = false;

    /**
     * The file extensions to be handled as JSP files.
     * Default list is .jsp and .jspx.
     */
    protected List<String> extensions;

    /**
     * The pages.
     */
    protected final List<String> pages = new ArrayList<>();

    /**
     * Needs better documentation, this data member does.
     * True by default.
     */
    protected boolean errorOnUseBeanInvalidClassAttribute = true;

    /**
     * The java file encoding.  Default
     * is UTF-8.  Added per bugzilla 19622.
     */
    protected String javaEncoding = "UTF-8";

    /** The number of threads to use; default is one per core */
    protected int threadCount = Runtime.getRuntime().availableProcessors();

    // Generation of web.xml fragments
    protected String webxmlFile;
    protected int webxmlLevel;
    protected String webxmlEncoding = "UTF-8";
    protected boolean addWebXmlMappings = false;

    protected Writer mapout;
    protected CharArrayWriter servletout;
    protected CharArrayWriter mappingout;

    /**
     * The servlet context.
     */
    protected JspCServletContext context;

    /**
     * The runtime context.
     * Maintain a dummy JspRuntimeContext for compiling tag files.
     */
    protected JspRuntimeContext rctxt;

    /**
     * Cache for the TLD locations
     */
    protected TldCache tldCache = null;

    protected JspConfig jspConfig = null;
    protected TagPluginManager tagPluginManager = null;

    protected TldScanner scanner = null;

    protected boolean verbose = false;
    protected boolean listErrors = false;
    protected boolean showSuccess = false;
    protected int argPos;
    protected boolean fullstop = false;
    protected String args[];

    public static void main(String arg[]) {
        if (arg.length == 0) {
            System.out.println(Localizer.getMessage("jspc.usage"));
        } else {
            JspC jspc = new JspC();
            try {
                jspc.setArgs(arg);
                if (jspc.helpNeeded) {
                    System.out.println(Localizer.getMessage("jspc.usage"));
                } else {
                    jspc.execute();
                }
            } catch (JasperException | BuildException e) {
                System.err.println(e);
                if (jspc.dieLevel != NO_DIE_LEVEL) {
                    System.exit(jspc.dieLevel);
                }
            }
        }
    }

    /**
     * Apply command-line arguments.
     * @param arg The arguments
     * @throws JasperException JSPC error
     */
    public void setArgs(String[] arg) throws JasperException {
        args = arg;
        String tok;

        dieLevel = NO_DIE_LEVEL;

        while ((tok = nextArg()) != null) {
            if (tok.equals(SWITCH_VERBOSE)) {
                verbose = true;
                showSuccess = true;
                listErrors = true;
            } else if (tok.equals(SWITCH_OUTPUT_DIR)) {
                tok = nextArg();
                setOutputDir( tok );
            } else if (tok.equals(SWITCH_PACKAGE_NAME)) {
                targetPackage = nextArg();
            } else if (tok.equals(SWITCH_COMPILE)) {
                compile=true;
            } else if (tok.equals(SWITCH_FAIL_FAST)) {
                failFast = true;
            } else if (tok.equals(SWITCH_CLASS_NAME)) {
                targetClassName = nextArg();
            } else if (tok.equals(SWITCH_URI_BASE)) {
                uriBase=nextArg();
            } else if (tok.equals(SWITCH_URI_ROOT)) {
                setUriroot( nextArg());
            } else if (tok.equals(SWITCH_FILE_WEBAPP)) {
                setUriroot( nextArg());
            } else if ( tok.equals( SHOW_SUCCESS ) ) {
                showSuccess = true;
            } else if ( tok.equals( LIST_ERRORS ) ) {
                listErrors = true;
            } else if (tok.equals(SWITCH_WEBAPP_INC)) {
                webxmlFile = nextArg();
                if (webxmlFile != null) {
                    webxmlLevel = INC_WEBXML;
                }
            } else if (tok.equals(SWITCH_WEBAPP_FRG)) {
                webxmlFile = nextArg();
                if (webxmlFile != null) {
                    webxmlLevel = FRG_WEBXML;
                }
            } else if (tok.equals(SWITCH_WEBAPP_XML)) {
                webxmlFile = nextArg();
                if (webxmlFile != null) {
                    webxmlLevel = ALL_WEBXML;
                }
            } else if (tok.equals(SWITCH_WEBAPP_XML_ENCODING)) {
                setWebXmlEncoding(nextArg());
            } else if (tok.equals(SWITCH_ADD_WEBAPP_XML_MAPPINGS)) {
                setAddWebXmlMappings(true);
            } else if (tok.equals(SWITCH_MAPPED)) {
                mappedFile = true;
            } else if (tok.equals(SWITCH_XPOWERED_BY)) {
                xpoweredBy = true;
            } else if (tok.equals(SWITCH_TRIM_SPACES)) {
                tok = nextArg();
                if (TrimSpacesOption.SINGLE.toString().equalsIgnoreCase(tok)) {
                    setTrimSpaces(TrimSpacesOption.SINGLE);
                } else {
                    setTrimSpaces(TrimSpacesOption.TRUE);
                    argPos--;
                }
            } else if (tok.equals(SWITCH_CACHE)) {
                tok = nextArg();
                if ("false".equals(tok)) {
                    caching = false;
                } else {
                    caching = true;
                }
            } else if (tok.equals(SWITCH_CLASSPATH)) {
                setClassPath(nextArg());
            } else if (tok.startsWith(SWITCH_DIE)) {
                try {
                    dieLevel = Integer.parseInt(
                        tok.substring(SWITCH_DIE.length()));
                } catch (NumberFormatException nfe) {
                    dieLevel = DEFAULT_DIE_LEVEL;
                }
            } else if (tok.equals(SWITCH_HELP)) {
                helpNeeded = true;
            } else if (tok.equals(SWITCH_POOLING)) {
                tok = nextArg();
                if ("false".equals(tok)) {
                    poolingEnabled = false;
                } else {
                    poolingEnabled = true;
                }
            } else if (tok.equals(SWITCH_ENCODING)) {
                setJavaEncoding(nextArg());
            } else if (tok.equals(SWITCH_SOURCE)) {
                setCompilerSourceVM(nextArg());
            } else if (tok.equals(SWITCH_TARGET)) {
                setCompilerTargetVM(nextArg());
            } else if (tok.equals(SWITCH_SMAP)) {
                smapSuppressed = false;
            } else if (tok.equals(SWITCH_DUMP_SMAP)) {
                smapDumped = true;
            } else if (tok.equals(SWITCH_VALIDATE_TLD)) {
                setValidateTld(true);
            } else if (tok.equals(SWITCH_VALIDATE_XML)) {
                setValidateXml(true);
            } else if (tok.equals(SWITCH_NO_BLOCK_EXTERNAL)) {
                setBlockExternal(false);
            } else if (tok.equals(SWITCH_NO_STRICT_QUOTE_ESCAPING)) {
                setStrictQuoteEscaping(false);
            } else if (tok.equals(SWITCH_QUOTE_ATTRIBUTE_EL)) {
                setQuoteAttributeEL(true);
            } else if (tok.equals(SWITCH_NO_QUOTE_ATTRIBUTE_EL)) {
                setQuoteAttributeEL(false);
            } else if (tok.equals(SWITCH_THREAD_COUNT)) {
                setThreadCount(nextArg());
            } else {
                if (tok.startsWith("-")) {
                    throw new JasperException(Localizer.getMessage("jspc.error.unknownOption", tok));
                }
                if (!fullstop) {
                    argPos--;
                }
                // Start treating the rest as JSP Pages
                break;
            }
        }

        // Add all extra arguments to the list of files
        while( true ) {
            String file = nextFile();
            if( file==null ) {
                break;
            }
            pages.add( file );
        }
    }

    /**
     * In JspC this always returns <code>true</code>.
     * {@inheritDoc}
     */
    @Override
    public boolean getKeepGenerated() {
        // isn't this why we are running jspc?
        return true;
    }

    @Override
    public TrimSpacesOption getTrimSpaces() {
        return trimSpaces;
    }

    public void setTrimSpaces(TrimSpacesOption trimSpaces) {
        this.trimSpaces = trimSpaces;
    }

    /**
     * Sets the option to control handling of template text that consists
     * entirely of whitespace.
     *
     * @param ts New value
     */
    public void setTrimSpaces(String ts) {
        this.trimSpaces = TrimSpacesOption.valueOf(ts);
    }

    /*
     * Backwards compatibility with 8.5.x
     */
    public void setTrimSpaces(boolean trimSpaces) {
        if (trimSpaces) {
            setTrimSpaces(TrimSpacesOption.TRUE);
        } else {
            setTrimSpaces(TrimSpacesOption.FALSE);
        }
    }

    @Override
    public boolean isPoolingEnabled() {
        return poolingEnabled;
    }

    /**
     * Sets the option to enable the tag handler pooling.
     * @param poolingEnabled New value
     */
    public void setPoolingEnabled(boolean poolingEnabled) {
        this.poolingEnabled = poolingEnabled;
    }

    @Override
    public boolean isXpoweredBy() {
        return xpoweredBy;
    }

    /**
     * Sets the option to enable generation of X-Powered-By response header.
     * @param xpoweredBy New value
     */
    public void setXpoweredBy(boolean xpoweredBy) {
        this.xpoweredBy = xpoweredBy;
    }

    /**
     * In JspC this always returns <code>true</code>.
     * {@inheritDoc}
     */
    @Override
    public boolean getDisplaySourceFragment() {
        return true;
    }

    @Override
    public int getMaxLoadedJsps() {
        return -1;
    }

    @Override
    public int getJspIdleTimeout() {
        return -1;
    }

    @Override
    public boolean getErrorOnUseBeanInvalidClassAttribute() {
        return errorOnUseBeanInvalidClassAttribute;
    }

    /**
     * Sets the option to issue a compilation error if the class attribute
     * specified in useBean action is invalid.
     * @param b New value
     */
    public void setErrorOnUseBeanInvalidClassAttribute(boolean b) {
        errorOnUseBeanInvalidClassAttribute = b;
    }

    @Override
    public boolean getMappedFile() {
        return mappedFile;
    }

    public void setMappedFile(boolean b) {
        mappedFile = b;
    }

    /**
     * Sets the option to include debug information in compiled class.
     * @param b New value
     */
    public void setClassDebugInfo( boolean b ) {
        classDebugInfo=b;
    }

    @Override
    public boolean getClassDebugInfo() {
        // compile with debug info
        return classDebugInfo;
    }

    @Override
    public boolean isCaching() {
        return caching;
    }

    /**
     * Sets the option to enable caching.
     * @param caching New value
     *
     * @see Options#isCaching()
     */
    public void setCaching(boolean caching) {
        this.caching = caching;
    }

    @Override
    public Map<String, TagLibraryInfo> getCache() {
        return cache;
    }

    /**
     * In JspC this always returns <code>0</code>.
     * {@inheritDoc}
     */
    @Override
    public int getCheckInterval() {
        return 0;
    }

    /**
     * In JspC this always returns <code>0</code>.
     * {@inheritDoc}
     */
    @Override
    public int getModificationTestInterval() {
        return 0;
    }


    /**
     * In JspC this always returns <code>false</code>.
     * {@inheritDoc}
     */
    @Override
    public boolean getRecompileOnFail() {
        return false;
    }


    /**
     * In JspC this always returns <code>false</code>.
     * {@inheritDoc}
     */
    @Override
    public boolean getDevelopment() {
        return false;
    }

    @Override
    public boolean isSmapSuppressed() {
        return smapSuppressed;
    }

    /**
     * Sets smapSuppressed flag.
     * @param smapSuppressed New value
     */
    public void setSmapSuppressed(boolean smapSuppressed) {
        this.smapSuppressed = smapSuppressed;
    }

    @Override
    public boolean isSmapDumped() {
        return smapDumped;
    }

    /**
     * Sets smapDumped flag.
     * @param smapDumped New value
     *
     * @see Options#isSmapDumped()
     */
    public void setSmapDumped(boolean smapDumped) {
        this.smapDumped = smapDumped;
    }


    /**
     * Determines whether text strings are to be generated as char arrays,
     * which improves performance in some cases.
     *
     * @param genStringAsCharArray true if text strings are to be generated as
     * char arrays, false otherwise
     */
    public void setGenStringAsCharArray(boolean genStringAsCharArray) {
        this.genStringAsCharArray = genStringAsCharArray;
    }

    @Override
    public boolean genStringAsCharArray() {
        return genStringAsCharArray;
    }

    @Override
    public File getScratchDir() {
        return scratchDir;
    }

    @Override
    public String getCompiler() {
        return compiler;
    }

    /**
     * Sets the option to determine what compiler to use.
     * @param c New value
     *
     * @see Options#getCompiler()
     */
    public void setCompiler(String c) {
        compiler=c;
    }

    @Override
    public String getCompilerClassName() {
        return null;
    }

    @Override
    public String getCompilerTargetVM() {
        return compilerTargetVM;
    }

    /**
     * Sets the compiler target VM.
     * @param vm New value
     *
     * @see Options#getCompilerTargetVM()
     */
    public void setCompilerTargetVM(String vm) {
        compilerTargetVM = vm;
    }

     @Override
    public String getCompilerSourceVM() {
         return compilerSourceVM;
     }

     /**
      * Sets the compiler source VM.
      * @param vm New value
      *
      * @see Options#getCompilerSourceVM()
      */
    public void setCompilerSourceVM(String vm) {
        compilerSourceVM = vm;
    }

    @Override
    public TldCache getTldCache() {
        return tldCache;
    }

    /**
     * Returns the encoding to use for
     * java files.  The default is UTF-8.
     *
     * @return String The encoding
     */
    @Override
    public String getJavaEncoding() {
        return javaEncoding;
    }

    /**
     * Sets the encoding to use for
     * java files.
     *
     * @param encodingName The name, e.g. "UTF-8"
     */
    public void setJavaEncoding(String encodingName) {
        javaEncoding = encodingName;
    }

    @Override
    public boolean getFork() {
        return fork;
    }

    public void setFork(boolean fork) {
        this.fork = fork;
    }

    @Override
    public String getClassPath() {
        if( classPath != null ) {
            return classPath;
        }
        return System.getProperty("java.class.path");
    }

    /**
     * Sets the classpath used while compiling the servlets generated from JSP
     * files
      * @param s New value
     */
    public void setClassPath(String s) {
        classPath=s;
    }

    /**
     * Returns the list of file extensions
     * that are treated as JSP files.
     *
     * @return The list of extensions
     */
    public List<String> getExtensions() {
        return extensions;
    }

    /**
     * Adds the given file extension to the
     * list of extensions handled as JSP files.
     *
     * @param extension The extension to add, e.g. "myjsp"
     */
    protected void addExtension(final String extension) {
        if(extension != null) {
            if(extensions == null) {
                extensions = new ArrayList<>();
            }

            extensions.add(extension);
        }
    }

    /**
     * Base dir for the webapp. Used to generate class names and resolve
     * includes.
     * @param s New value
     */
    public void setUriroot( String s ) {
        if (s == null) {
            uriRoot = null;
            return;
        }
        try {
            uriRoot = resolveFile(s).getCanonicalPath();
        } catch( Exception ex ) {
            uriRoot = s;
        }
    }

    /**
     * Parses comma-separated list of JSP files to be processed.  If the argument
     * is null, nothing is done.
     *
     * <p>Each file is interpreted relative to uriroot, unless it is absolute,
     * in which case it must start with uriroot.</p>
     *
     * @param jspFiles Comma-separated list of JSP files to be processed
     */
    public void setJspFiles(final String jspFiles) {
        if(jspFiles == null) {
            return;
        }

        StringTokenizer tok = new StringTokenizer(jspFiles, ",");
        while (tok.hasMoreTokens()) {
            pages.add(tok.nextToken());
        }
    }

    /**
     * Sets the compile flag.
     *
     * @param b Flag value
     */
    public void setCompile( final boolean b ) {
        compile = b;
    }

    /**
     * Sets the verbosity level.  The actual number doesn't
     * matter: if it's greater than zero, the verbose flag will
     * be true.
     *
     * @param level Positive means verbose
     */
    public void setVerbose( final int level ) {
        if (level > 0) {
            verbose = true;
            showSuccess = true;
            listErrors = true;
        }
    }

    public void setValidateTld( boolean b ) {
        this.validateTld = b;
    }

    public boolean isValidateTld() {
        return validateTld;
    }

    public void setValidateXml( boolean b ) {
        this.validateXml = b;
    }

    public boolean isValidateXml() {
        return validateXml;
    }

    public void setBlockExternal( boolean b ) {
        this.blockExternal = b;
    }

    public boolean isBlockExternal() {
        return blockExternal;
    }

    public void setStrictQuoteEscaping( boolean b ) {
        this.strictQuoteEscaping = b;
    }

    @Override
    public boolean getStrictQuoteEscaping() {
        return strictQuoteEscaping;
    }

    public void setQuoteAttributeEL(boolean b) {
        quoteAttributeEL = b;
    }

    @Override
    public boolean getQuoteAttributeEL() {
        return quoteAttributeEL;
    }

    public int getThreadCount() {
        return threadCount;
    }

    public void setThreadCount(String threadCount) {
        if (threadCount == null) {
            return;
        }
        int newThreadCount;
        try {
            if (threadCount.endsWith("C")) {
                double factor = Double.parseDouble(threadCount.substring(0, threadCount.length() - 1));
                newThreadCount = (int) (factor * Runtime.getRuntime().availableProcessors());
            } else {
                newThreadCount = Integer.parseInt(threadCount);
            }
        } catch (NumberFormatException e) {
            throw new BuildException(Localizer.getMessage("jspc.error.parseThreadCount", threadCount));
        }
        if (newThreadCount < 1) {
            throw new BuildException(Localizer.getMessage(
                    "jspc.error.minThreadCount", Integer.valueOf(newThreadCount)));
        }
        this.threadCount = newThreadCount;
    }

    public void setListErrors( boolean b ) {
        listErrors = b;
    }

    public void setOutputDir( String s ) {
        if( s!= null ) {
            scratchDir = resolveFile(s).getAbsoluteFile();
        } else {
            scratchDir=null;
        }
    }

    /**
     * Sets the package name to be used for the generated servlet classes.
     * @param p New value
     */
    public void setPackage( String p ) {
        targetPackage=p;
    }

    /**
     * Class name of the generated file ( without package ).
     * Can only be used if a single file is converted.
     * XXX Do we need this feature ?
     * @param p New value
     */
    public void setClassName( String p ) {
        targetClassName=p;
    }

    /**
     * File where we generate configuration with the class definitions to be
     * included in a web.xml file.
     * @param s New value
     */
    public void setWebXmlInclude( String s ) {
        webxmlFile=resolveFile(s).getAbsolutePath();
        webxmlLevel=INC_WEBXML;
    }

    /**
     * File where we generate a complete web-fragment.xml with the class
     * definitions.
     * @param s New value
     */
    public void setWebFragmentXml( String s ) {
        webxmlFile=resolveFile(s).getAbsolutePath();
        webxmlLevel=FRG_WEBXML;
    }

    /**
     * File where we generate a complete web.xml with the class definitions.
     * @param s New value
     */
    public void setWebXml( String s ) {
        webxmlFile=resolveFile(s).getAbsolutePath();
        webxmlLevel=ALL_WEBXML;
    }

    /**
     * Sets the encoding to be used to read and write web.xml files.
     *
     * <p>
     * If not specified, defaults to UTF-8.
     * </p>
     *
     * @param encoding
     *            Encoding, e.g. "UTF-8".
     */
    public void setWebXmlEncoding(String encoding) {
        webxmlEncoding = encoding;
    }

    /**
     * Sets the option to merge generated web.xml fragment into the
     * WEB-INF/web.xml file of the web application that we were processing.
     *
     * @param b
     *            <code>true</code> to merge the fragment into the existing
     *            web.xml file of the processed web application
     *            ({uriroot}/WEB-INF/web.xml), <code>false</code> to keep the
     *            generated web.xml fragment
     */
    public void setAddWebXmlMappings(boolean b) {
        addWebXmlMappings = b;
    }

    /**
     * Sets the option that throws an exception in case of a compilation error.
     * @param b New value
     */
    public void setFailOnError(final boolean b) {
        failOnError = b;
    }

    /**
     * @return <code>true</code> if an exception will be thrown
     *  in case of a compilation error.
     */
    public boolean getFailOnError() {
        return failOnError;
    }

    @Override
    public JspConfig getJspConfig() {
        return jspConfig;
    }

    @Override
    public TagPluginManager getTagPluginManager() {
        return tagPluginManager;
    }


    /**
     * {@inheritDoc}
     * <p>
     * Hard-coded to {@code false} for pre-compiled code to enable repeatable
     * builds.
     */
    @Override
    public boolean getGeneratedJavaAddTimestamp() {
        return false;
    }


    /**
     * Adds servlet declaration and mapping for the JSP page servlet to the
     * generated web.xml fragment.
     *
     * @param file
     *            Context-relative path to the JSP file, e.g.
     *            <code>/index.jsp</code>
     * @param clctxt
     *            Compilation context of the servlet
     * @throws IOException An IO error occurred
     */
    public void generateWebMapping( String file, JspCompilationContext clctxt )
        throws IOException
    {
        if (log.isDebugEnabled()) {
            log.debug(Localizer.getMessage("jspc.generatingMapping", file, clctxt));
        }

        String className = clctxt.getServletClassName();
        String packageName = clctxt.getServletPackageName();

        String thisServletName;
        if  (packageName.isEmpty()) {
            thisServletName = className;
        } else {
            thisServletName = packageName + '.' + className;
        }

        if (servletout != null) {
            synchronized(servletout) {
                servletout.write("\n    <servlet>\n        <servlet-name>");
                servletout.write(thisServletName);
                servletout.write("</servlet-name>\n        <servlet-class>");
                servletout.write(thisServletName);
                servletout.write("</servlet-class>\n    </servlet>\n");
            }
        }
        if (mappingout != null) {
            synchronized(mappingout) {
                mappingout.write("\n    <servlet-mapping>\n        <servlet-name>");
                mappingout.write(thisServletName);
                mappingout.write("</servlet-name>\n        <url-pattern>");
                mappingout.write(file.replace('\\', '/'));
                mappingout.write("</url-pattern>\n    </servlet-mapping>\n");
            }
        }
    }

    /**
     * Include the generated web.xml inside the webapp's web.xml.
     * @throws IOException An IO error occurred
     */
    protected void mergeIntoWebXml() throws IOException {

        File webappBase = new File(uriRoot);
        File webXml = new File(webappBase, "WEB-INF/web.xml");
        File webXml2 = new File(webappBase, "WEB-INF/web2.xml");
        String insertStartMarker =
            Localizer.getMessage("jspc.webinc.insertStart");
        String insertEndMarker =
            Localizer.getMessage("jspc.webinc.insertEnd");

        try (BufferedReader reader = new BufferedReader(openWebxmlReader(webXml));
                BufferedReader fragmentReader =
                        new BufferedReader(openWebxmlReader(new File(webxmlFile)));
                PrintWriter writer = new PrintWriter(openWebxmlWriter(webXml2))) {

            // Insert the <servlet> and <servlet-mapping> declarations
            boolean inserted = false;
            int current = reader.read();
            while (current > -1) {
                if (current == '<') {
                    String element = getElement(reader);
                    if (!inserted && insertBefore.contains(element)) {
                        // Insert generated content here
                        writer.println(insertStartMarker);
                        while (true) {
                            String line = fragmentReader.readLine();
                            if (line == null) {
                                writer.println();
                                break;
                            }
                            writer.println(line);
                        }
                        writer.println(insertEndMarker);
                        writer.println();
                        writer.write(element);
                        inserted = true;
                    } else if (element.equals(insertStartMarker)) {
                        // Skip the previous auto-generated content
                        while (true) {
                            current = reader.read();
                            if (current < 0) {
                                throw new EOFException();
                            }
                            if (current == '<') {
                                element = getElement(reader);
                                if (element.equals(insertEndMarker)) {
                                    break;
                                }
                            }
                        }
                        current = reader.read();
                        while (current == '\n' || current == '\r') {
                            current = reader.read();
                        }
                        continue;
                    } else {
                        writer.write(element);
                    }
                } else {
                    writer.write(current);
                }
                current = reader.read();
            }
        }

        try (FileInputStream fis = new FileInputStream(webXml2);
                FileOutputStream fos = new FileOutputStream(webXml)) {

            byte buf[] = new byte[512];
            while (true) {
                int n = fis.read(buf);
                if (n < 0) {
                    break;
                }
                fos.write(buf, 0, n);
            }
        }

        if(!webXml2.delete() && log.isDebugEnabled()) {
            log.debug(Localizer.getMessage("jspc.delete.fail",
                    webXml2.toString()));
        }

        if (!(new File(webxmlFile)).delete() && log.isDebugEnabled()) {
            log.debug(Localizer.getMessage("jspc.delete.fail", webxmlFile));
        }

    }

    /*
     * Assumes valid xml
     */
    private String getElement(Reader reader) throws IOException {
        StringBuilder result = new StringBuilder();
        result.append('<');

        boolean done = false;

        while (!done) {
            int current = reader.read();
            while (current != '>') {
                if (current < 0) {
                    throw new EOFException();
                }
                result.append((char) current);
                current = reader.read();
            }
            result.append((char) current);

            int len = result.length();
            if (len > 4 && result.substring(0, 4).equals("<!--")) {
                // This is a comment - make sure we are at the end
                if (len >= 7 && result.substring(len - 3, len).equals("-->")) {
                    done = true;
                }
            } else {
                done = true;
            }
        }


        return result.toString();
    }

    protected void processFile(String file) throws JasperException {

        if (log.isDebugEnabled()) {
            log.debug(Localizer.getMessage("jspc.processing", file));
        }

        ClassLoader originalClassLoader = null;
        Thread currentThread = Thread.currentThread();

        try {
            // set up a scratch/output dir if none is provided
            if (scratchDir == null) {
                String temp = System.getProperty("java.io.tmpdir");
                if (temp == null) {
                    temp = "";
                }
                scratchDir = new File(temp).getAbsoluteFile();
            }

            String jspUri=file.replace('\\','/');
            JspCompilationContext clctxt = new JspCompilationContext
                ( jspUri, this, context, null, rctxt );

            /* Override the defaults */
            if ((targetClassName != null) && (targetClassName.length() > 0)) {
                clctxt.setServletClassName(targetClassName);
                targetClassName = null;
            }
            if (targetPackage != null) {
                clctxt.setBasePackageName(targetPackage);
            }

            originalClassLoader = currentThread.getContextClassLoader();
            currentThread.setContextClassLoader(loader);

            clctxt.setClassLoader(loader);
            clctxt.setClassPath(classPath);

            Compiler clc = clctxt.createCompiler();

            // If compile is set, generate both .java and .class, if
            // .jsp file is newer than .class file;
            // Otherwise only generate .java, if .jsp file is newer than
            // the .java file
            if( clc.isOutDated(compile) ) {
                if (log.isDebugEnabled()) {
                    log.debug(Localizer.getMessage("jspc.outdated", jspUri));
                }

                clc.compile(compile, true);
            }

            // Generate mapping
            generateWebMapping( file, clctxt );
            if ( showSuccess ) {
                log.info(Localizer.getMessage("jspc.built", file));
            }

        } catch (JasperException je) {
            Throwable rootCause = je;
            while (rootCause instanceof JasperException
                    && ((JasperException) rootCause).getRootCause() != null) {
                rootCause = ((JasperException) rootCause).getRootCause();
            }
            if (rootCause != je) {
                log.error(Localizer.getMessage("jspc.error.generalException",
                                               file),
                          rootCause);
            }
            throw je;
        } catch (Exception e) {
            if ((e instanceof FileNotFoundException) && log.isWarnEnabled()) {
                log.warn(Localizer.getMessage("jspc.error.fileDoesNotExist",
                                              e.getMessage()));
            }
            throw new JasperException(e);
        } finally {
            if (originalClassLoader != null) {
                currentThread.setContextClassLoader(originalClassLoader);
            }
        }
    }


    /**
     * Locate all jsp files in the webapp. Used if no explicit jsps are
     * specified. Scan is performed via the ServletContext and will include any
     * JSPs located in resource JARs.
     */
    public void scanFiles() {
        // Make sure default extensions are always included
        if ((getExtensions() == null) || (getExtensions().size() < 2)) {
            addExtension("jsp");
            addExtension("jspx");
        }

        scanFilesInternal("/");
    }


    private void scanFilesInternal(String input) {
        Set<String> paths = context.getResourcePaths(input);
        for (String path : paths) {
            if (path.endsWith("/")) {
                scanFilesInternal(path);
            } else if (jspConfig.isJspPage(path)) {
                pages.add(path);
            } else {
                String ext = path.substring(path.lastIndexOf('.') + 1);
                if (extensions.contains(ext)) {
                    pages.add(path);
                }
            }
        }
    }


    /**
     * Executes the compilation.
     */
    @Override
    public void execute() {
        if(log.isDebugEnabled()) {
            log.debug(Localizer.getMessage("jspc.start", Integer.toString(pages.size())));
        }

        try {
            if (uriRoot == null) {
                if (pages.size() == 0) {
                    throw new JasperException(Localizer.getMessage("jsp.error.jspc.missingTarget"));
                }
                String firstJsp = pages.get(0);
                File firstJspF = new File(firstJsp);
                if (!firstJspF.exists()) {
                    throw new JasperException(Localizer.getMessage(
                            "jspc.error.fileDoesNotExist", firstJsp));
                }
                locateUriRoot(firstJspF);
            }

            if (uriRoot == null) {
                throw new JasperException(Localizer.getMessage("jsp.error.jspc.no_uriroot"));
            }

            File uriRootF = new File(uriRoot);
            if (!uriRootF.isDirectory()) {
                throw new JasperException(Localizer.getMessage("jsp.error.jspc.uriroot_not_dir"));
            }

            if (loader == null) {
                loader = initClassLoader();
            }
            if (context == null) {
                initServletContext(loader);
            }

            // No explicit pages, we'll process all .jsp in the webapp
            if (pages.size() == 0) {
                scanFiles();
            } else {
                // Ensure pages are all relative to the uriRoot.
                // Those that are not will trigger an error later. The error
                // could be detected earlier but isn't to support the use case
                // when failFast is not used.
                for (int i = 0; i < pages.size(); i++) {
                    String nextjsp = pages.get(i);

                    File fjsp = new File(nextjsp);
                    if (!fjsp.isAbsolute()) {
                        fjsp = new File(uriRootF, nextjsp);
                    }
                    if (!fjsp.exists()) {
                        if (log.isWarnEnabled()) {
                            log.warn(Localizer.getMessage(
                                    "jspc.error.fileDoesNotExist", fjsp.toString()));
                        }
                        continue;
                    }
                    String s = fjsp.getAbsolutePath();
                    if (s.startsWith(uriRoot)) {
                        nextjsp = s.substring(uriRoot.length());
                    }
                    if (nextjsp.startsWith("." + File.separatorChar)) {
                        nextjsp = nextjsp.substring(2);
                    }
                    pages.set(i, nextjsp);
                }
            }

            initWebXml();

            int errorCount = 0;
            long start = System.currentTimeMillis();

            ExecutorService threadPool = Executors.newFixedThreadPool(threadCount);
            ExecutorCompletionService<Void> service = new ExecutorCompletionService<>(threadPool);
            try {
                int pageCount = pages.size();
                for (String nextjsp : pages) {
                    service.submit(new ProcessFile(nextjsp));
                }
                JasperException reportableError = null;
                for (int i = 0; i < pageCount; i++) {
                    try {
                        service.take().get();
                    } catch (ExecutionException e) {
                        if (failFast) {
                            // Generation is not interruptible so any tasks that
                            // have started will complete.
                            List<Runnable> notExecuted = threadPool.shutdownNow();
                            i += notExecuted.size();
                            Throwable t = e.getCause();
                            if (t instanceof JasperException) {
                                reportableError = (JasperException) t;
                            } else {
                                reportableError = new JasperException(t);
                            }
                        } else {
                            errorCount++;
                            log.error(Localizer.getMessage("jspc.error.compilation"), e);
                        }
                    } catch (InterruptedException e) {
                        // Ignore
                    }
                }
                if (reportableError != null) {
                    throw reportableError;
                }
            } finally {
                threadPool.shutdown();
            }

            long time = System.currentTimeMillis() - start;
            String msg = Localizer.getMessage("jspc.generation.result",
                    Integer.toString(errorCount), Long.toString(time));
            if (failOnError && errorCount > 0) {
                System.out.println(Localizer.getMessage(
                        "jspc.errorCount", Integer.valueOf(errorCount)));
                throw new BuildException(msg);
            } else {
                log.info(msg);
            }

            completeWebXml();

            if (addWebXmlMappings) {
                mergeIntoWebXml();
            }

        } catch (IOException ioe) {
            throw new BuildException(ioe);

        } catch (JasperException je) {
            if (failOnError) {
                throw new BuildException(je);
            }
        } finally {
            if (loader != null) {
                LogFactory.release(loader);
            }
        }
    }

    // ==================== protected utility methods ====================

    protected String nextArg() {
        if ((argPos >= args.length)
            || (fullstop = SWITCH_FULL_STOP.equals(args[argPos]))) {
            return null;
        } else {
            return args[argPos++];
        }
    }

    protected String nextFile() {
        if (fullstop) {
            argPos++;
        }
        if (argPos >= args.length) {
            return null;
        } else {
            return args[argPos++];
        }
    }

    protected void initWebXml() throws JasperException {
        try {
            if (webxmlLevel >= INC_WEBXML) {
                mapout = openWebxmlWriter(new File(webxmlFile));
                servletout = new CharArrayWriter();
                mappingout = new CharArrayWriter();
            } else {
                mapout = null;
                servletout = null;
                mappingout = null;
            }
            if (webxmlLevel >= ALL_WEBXML) {
                mapout.write(Localizer.getMessage("jspc.webxml.header", webxmlEncoding));
                mapout.flush();
            } else if (webxmlLevel >= FRG_WEBXML) {
                    mapout.write(Localizer.getMessage("jspc.webfrg.header", webxmlEncoding));
                    mapout.flush();
            } else if ((webxmlLevel>= INC_WEBXML) && !addWebXmlMappings) {
                mapout.write(Localizer.getMessage("jspc.webinc.header"));
                mapout.flush();
            }
        } catch (IOException ioe) {
            mapout = null;
            servletout = null;
            mappingout = null;
            throw new JasperException(ioe);
        }
    }

    protected void completeWebXml() {
        if (mapout != null) {
            try {
                servletout.writeTo(mapout);
                mappingout.writeTo(mapout);
                if (webxmlLevel >= ALL_WEBXML) {
                    mapout.write(Localizer.getMessage("jspc.webxml.footer"));
                } else if (webxmlLevel >= FRG_WEBXML) {
                        mapout.write(Localizer.getMessage("jspc.webfrg.footer"));
                } else if ((webxmlLevel >= INC_WEBXML) && !addWebXmlMappings) {
                    mapout.write(Localizer.getMessage("jspc.webinc.footer"));
                }
                mapout.close();
            } catch (IOException ioe) {
                // nothing to do if it fails since we are done with it
            }
        }
    }


    protected void initTldScanner(JspCServletContext context, ClassLoader classLoader) {
        if (scanner != null) {
            return;
        }

        scanner = newTldScanner(context, true, isValidateTld(), isBlockExternal());
        scanner.setClassLoader(classLoader);
    }


    protected TldScanner newTldScanner(JspCServletContext context, boolean namespaceAware,
            boolean validate, boolean blockExternal) {
        return new TldScanner(context, namespaceAware, validate, blockExternal);
    }


    protected void initServletContext(ClassLoader classLoader)
            throws IOException, JasperException {
        // TODO: should we use the Ant Project's log?
        PrintWriter log = new PrintWriter(System.out);
        URL resourceBase = new File(uriRoot).getCanonicalFile().toURI().toURL();

        context = new JspCServletContext(log, resourceBase, classLoader,
                isValidateXml(), isBlockExternal());
        if (isValidateTld()) {
            context.setInitParameter(Constants.XML_VALIDATION_TLD_INIT_PARAM, "true");
        }


        initTldScanner(context, classLoader);

        try {
            scanner.scan();
        } catch (SAXException e) {
            throw new JasperException(e);
        }
        tldCache = new TldCache(context, scanner.getUriTldResourcePathMap(),
                scanner.getTldResourcePathTaglibXmlMap());
        context.setAttribute(TldCache.SERVLET_CONTEXT_ATTRIBUTE_NAME, tldCache);
        rctxt = new JspRuntimeContext(context, this);
        jspConfig = new JspConfig(context);
        tagPluginManager = new TagPluginManager(context);
    }

    /**
     * Initializes the classloader as/if needed for the given
     * compilation context.
     * @return the classloader that will be used
     * @throws IOException If an error occurs
     */
    protected ClassLoader initClassLoader() throws IOException {

        classPath = getClassPath();

        ClassLoader jspcLoader = getClass().getClassLoader();
        if (jspcLoader instanceof AntClassLoader) {
            classPath += File.pathSeparator
                + ((AntClassLoader) jspcLoader).getClasspath();
        }

        // Turn the classPath into URLs
        List<URL> urls = new ArrayList<>();
        StringTokenizer tokenizer = new StringTokenizer(classPath,
                                                        File.pathSeparator);
        while (tokenizer.hasMoreTokens()) {
            String path = tokenizer.nextToken();
            try {
                File libFile = new File(path);
                urls.add(libFile.toURI().toURL());
            } catch (IOException ioe) {
                // Failing a toCanonicalPath on a file that
                // exists() should be a JVM regression test,
                // therefore we have permission to freak uot
                throw new RuntimeException(ioe.toString());
            }
        }

        File webappBase = new File(uriRoot);
        if (webappBase.exists()) {
            File classes = new File(webappBase, "/WEB-INF/classes");
            try {
                if (classes.exists()) {
                    classPath = classPath + File.pathSeparator
                        + classes.getCanonicalPath();
                    urls.add(classes.getCanonicalFile().toURI().toURL());
                }
            } catch (IOException ioe) {
                // failing a toCanonicalPath on a file that
                // exists() should be a JVM regression test,
                // therefore we have permission to freak out
                throw new RuntimeException(ioe.toString());
            }
            File webinfLib = new File(webappBase, "/WEB-INF/lib");
            if (webinfLib.exists() && webinfLib.isDirectory()) {
                String[] libs = webinfLib.list();
                if (libs != null) {
                    for (String lib : libs) {
                        if (lib.length() < 5) {
                            continue;
                        }
                        String ext = lib.substring(lib.length() - 4);
                        if (!".jar".equalsIgnoreCase(ext)) {
                            if (".tld".equalsIgnoreCase(ext)) {
                                log.warn(Localizer.getMessage("jspc.warning.tldInWebInfLib"));
                            }
                            continue;
                        }
                        try {
                            File libFile = new File(webinfLib, lib);
                            classPath = classPath + File.pathSeparator + libFile.getAbsolutePath();
                            urls.add(libFile.getAbsoluteFile().toURI().toURL());
                        } catch (IOException ioe) {
                            // failing a toCanonicalPath on a file that
                            // exists() should be a JVM regression test,
                            // therefore we have permission to freak out
                            throw new RuntimeException(ioe.toString());
                        }
                    }
                }
            }
        }

        URL[] urlsA = urls.toArray(new URL[0]);
        loader = new URLClassLoader(urlsA, this.getClass().getClassLoader());
        return loader;
    }

    /**
     * Find the WEB-INF dir by looking up in the directory tree.
     * This is used if no explicit docbase is set, but only files.
     *
     * @param f The path from which it will start looking
     */
    protected void locateUriRoot( File f ) {
        String tUriBase = uriBase;
        if (tUriBase == null) {
            tUriBase = "/";
        }
        try {
            if (f.exists()) {
                f = new File(f.getAbsolutePath());
                while (true) {
                    File g = new File(f, "WEB-INF");
                    if (g.exists() && g.isDirectory()) {
                        uriRoot = f.getCanonicalPath();
                        uriBase = tUriBase;
                        if (log.isInfoEnabled()) {
                            log.info(Localizer.getMessage(
                                        "jspc.implicit.uriRoot",
                                        uriRoot));
                        }
                        break;
                    }
                    if (f.exists() && f.isDirectory()) {
                        tUriBase = "/" + f.getName() + "/" + tUriBase;
                    }

                    String fParent = f.getParent();
                    if (fParent == null) {
                        break;
                    } else {
                        f = new File(fParent);
                    }

                    // If there is no acceptable candidate, uriRoot will
                    // remain null.
                }

                if (uriRoot != null) {
                    File froot = new File(uriRoot);
                    uriRoot = froot.getCanonicalPath();
                }
            }
        } catch (IOException ioe) {
            // Missing uriRoot will be handled in the caller.
        }
    }

    /**
     * Resolves the relative or absolute pathname correctly
     * in both Ant and command-line situations.  If Ant launched
     * us, we should use the basedir of the current project
     * to resolve relative paths.
     *
     * See Bugzilla 35571.
     *
     * @param s The file
     * @return The file resolved
     */
     protected File resolveFile(final String s) {
         if(getProject() == null) {
             // Note FileUtils.getFileUtils replaces FileUtils.newFileUtils in Ant 1.6.3
             return FileUtils.getFileUtils().resolveFile(null, s);
         } else {
             return FileUtils.getFileUtils().resolveFile(getProject().getBaseDir(), s);
         }
     }

    private Reader openWebxmlReader(File file) throws IOException {
        FileInputStream fis = new FileInputStream(file);
        try {
            return webxmlEncoding != null ? new InputStreamReader(fis,
                    webxmlEncoding) : new InputStreamReader(fis);
        } catch (IOException ex) {
            fis.close();
            throw ex;
        }
    }

    private Writer openWebxmlWriter(File file) throws IOException {
        FileOutputStream fos = new FileOutputStream(file);
        try {
            return webxmlEncoding != null ? new OutputStreamWriter(fos,
                    webxmlEncoding) : new OutputStreamWriter(fos);
        } catch (IOException ex) {
            fos.close();
            throw ex;
        }
    }


    private class ProcessFile implements Callable<Void> {
        private final String file;

        private ProcessFile(String file) {
            this.file = file;
        }

        @Override
        public Void call() throws Exception {
            processFile(file);
            return null;
        }
    }
}
