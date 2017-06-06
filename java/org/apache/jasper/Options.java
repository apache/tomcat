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
import java.util.Map;

import javax.servlet.jsp.tagext.TagLibraryInfo;

import org.apache.jasper.compiler.JspConfig;
import org.apache.jasper.compiler.TagPluginManager;
import org.apache.jasper.compiler.TldCache;

/**
 * A class to hold all init parameters specific to the JSP engine.
 *
 * @author Anil K. Vijendran
 * @author Hans Bergsten
 * @author Pierre Delisle
 */
public interface Options {

    /**
     * Returns true if Jasper issues a compilation error instead of a runtime
     * Instantiation error if the class attribute specified in useBean action
     * is invalid.
     * @return <code>true</code> to get an error
     */
    public boolean getErrorOnUseBeanInvalidClassAttribute();

    /**
     * @return <code>true</code> to keep the generated source
     */
    public boolean getKeepGenerated();

    /**
     * @return <code>true</code> if tag handler pooling is enabled,
     *  <code>false</code> otherwise.
     */
    public boolean isPoolingEnabled();

    /**
     * @return <code>true</code> if HTML mapped Servlets are supported.
     */
    public boolean getMappedFile();

    /**
     * @return <code>true</code> if debug information in included
     *  in compiled classes.
     */
    public boolean getClassDebugInfo();

    /**
     * @return background compile thread check interval in seconds
     */
    public int getCheckInterval();

    /**
     * Main development flag, which enables detailed error reports with
     *  sources, as well automatic recompilation of JSPs and tag files.
     *  This setting should usually be <code>false</code> when running
     *  in production.
     * @return <code>true</code> if Jasper is in development mode
     */
    public boolean getDevelopment();

    /**
     * @return <code>true</code> to include a source fragment in exception
     *  messages.
     */
    public boolean getDisplaySourceFragment();

    /**
     * @return <code>true</code> to suppress generation of SMAP info for
     *  JSR45 debugging.
     */
    public boolean isSmapSuppressed();

    /**
     * This setting is ignored if suppressSmap() is <code>true</code>.
     * @return <code>true</code> to write SMAP info for JSR45 debugging to a
     * file.
     */
    public boolean isSmapDumped();

    /**
     * @return {@link TrimSpacesOption#TRUE} to remove template text that
     *         consists only of whitespace from the output completely,
     *         {@link TrimSpacesOption#SINGLE} to replace such template text
     *         with a single space or {@link TrimSpacesOption#FALSE} to leave
     *         such template text unchanged
     */
    public TrimSpacesOption getTrimSpaces();

    /**
     * Gets the class-id value that is sent to Internet Explorer when using
     * &lt;jsp:plugin&gt; tags.
     * @return Class-id value
     */
    public String getIeClassId();

    /**
     * @return the work folder
     */
    public File getScratchDir();

    /**
     * @return the classpath used to compile generated Servlets
     */
    public String getClassPath();

    /**
     * Compiler to use.
     *
     * <p>
     * If <code>null</code> (the default), the java compiler from Eclipse JDT
     * project, bundled with Tomcat, will be used. Otherwise, the
     * <code>javac</code> task from Apache Ant will be used to call an external
     * java compiler and the value of this option will be passed to it. See
     * Apache Ant documentation for the possible values.
     * @return the compiler name
     */
    public String getCompiler();

    /**
     * @return the compiler target VM, e.g. 1.8.
     */
    public String getCompilerTargetVM();

    /**
     * @return the compiler source VM, e.g. 1.8.
     */
    public String getCompilerSourceVM();

    /**
     * @return Jasper Java compiler class to use.
     */
    public String getCompilerClassName();

    /**
     * The cache that maps URIs, resource paths and parsed TLD files for the
     * various tag libraries 'exposed' by the web application.
     * A tag library is 'exposed' either explicitly in
     * web.xml or implicitly via the uri tag in the TLD
     * of a taglib deployed in a jar file (WEB-INF/lib).
     *
     * @return the instance of the TldLocationsCache
     *  for the web-application.
     */
    public TldCache getTldCache();

    /**
     * @return Java platform encoding to generate the JSP page servlet.
     */
    public String getJavaEncoding();

    /**
     * The boolean flag to tell Ant whether to fork JSP page compilations.
     *
     * <p>
     * Is used only when Jasper uses an external java compiler (wrapped through
     * a <code>javac</code> Apache Ant task).
     * @return <code>true</code> to fork a process during compilation
     */
    public boolean getFork();

    /**
     * @return JSP configuration information specified in web.xml.
     */
    public JspConfig getJspConfig();

    /**
     * @return <code>true</code> to generate a X-Powered-By response header.
     */
    public boolean isXpoweredBy();

    /**
     * @return a Tag Plugin Manager
     */
    public TagPluginManager getTagPluginManager();

    /**
     * Indicates whether text strings are to be generated as char arrays.
     *
     * @return <code>true</code> if text strings are to be generated as char
     *         arrays, <code>false</code> otherwise
     */
    public boolean genStringAsCharArray();

    /**
     * @return modification test interval.
     */
    public int getModificationTestInterval();


    /**
     * @return <code>true</code> if re-compile will occur on a failure.
     */
    public boolean getRecompileOnFail();

    /**
     * @return <code>true</code> is caching is enabled
     *  (used for precompilation).
     */
    public boolean isCaching();

    /**
     * The web-application wide cache for the TagLibraryInfo tag library
     * descriptors, used if {@link #isCaching()} returns <code>true</code>.
     *
     * <p>
     * Using this cache avoids the cost of repeating the parsing of a tag
     * library descriptor XML file (performed by TagLibraryInfoImpl.parseTLD).
     * </p>
     *
     * @return the Map(String uri, TagLibraryInfo tld) instance.
     */
    public Map<String, TagLibraryInfo> getCache();

    /**
     * The maximum number of loaded jsps per web-application. If there are more
     * jsps loaded, they will be unloaded. If unset or less than 0, no jsps
     * are unloaded.
     * @return The JSP count
     */
    public int getMaxLoadedJsps();

    /**
     * @return the idle time in seconds after which a JSP is unloaded.
     * If unset or less or equal than 0, no jsps are unloaded.
     */
    public int getJspIdleTimeout();

    /**
     * @return {@code true} if the quote escaping required by section JSP.1.6 of
     *         the JSP specification should be applied to scriplet expression.
     */
    public boolean getStrictQuoteEscaping();

    /**
     * @return {@code true} if EL expressions used within attributes should have
     *         the quoting rules in JSP.1.6 applied to the expression.
     */
    public boolean getQuoteAttributeEL();
}
