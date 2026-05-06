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

import jakarta.servlet.jsp.tagext.TagLibraryInfo;

import org.apache.jasper.compiler.JspConfig;
import org.apache.jasper.compiler.TagPluginManager;
import org.apache.jasper.compiler.TldCache;

/**
 * A class to hold all init parameters specific to the JSP engine.
 */
public interface Options {

    /**
     * Returns true if Jasper issues a compilation error instead of a runtime Instantiation error if the class attribute
     * specified in useBean action is invalid.
     *
     * @return <code>true</code> to get an error
     */
    boolean getErrorOnUseBeanInvalidClassAttribute();

    /**
     * Returns whether the generated Java source files should be kept after compilation.
     *
     * @return <code>true</code> to keep the generated source
     */
    boolean getKeepGenerated();

    /**
     * Returns whether tag handler pooling is enabled for improved performance.
     *
     * @return <code>true</code> if tag handler pooling is enabled, <code>false</code> otherwise.
     */
    boolean isPoolingEnabled();

    /**
     * Returns whether HTML mapped Servlets are supported.
     *
     * @return <code>true</code> if HTML mapped Servlets are supported.
     */
    boolean getMappedFile();

    /**
     * Returns whether debug information is included in compiled classes.
     *
     * @return <code>true</code> if debug information in included in compiled classes.
     */
    boolean getClassDebugInfo();

    /**
     * Returns the interval in seconds at which the background compile thread checks for modified JSP files.
     *
     * @return background compile thread check interval in seconds
     */
    int getCheckInterval();

    /**
     * Main development flag, which enables detailed error reports with sources, as well automatic recompilation of JSPs
     * and tag files. This setting should usually be <code>false</code> when running in production.
     *
     * @return <code>true</code> if Jasper is in development mode
     */
    boolean getDevelopment();

    /**
     * Returns whether a source fragment should be included in exception messages for debugging.
     *
     * @return <code>true</code> to include a source fragment in exception messages.
     */
    boolean getDisplaySourceFragment();

    /**
     * Returns whether generation of SMAP (Source Map for Debugging) information is suppressed.
     *
     * @return <code>true</code> to suppress generation of SMAP info for JSR45 debugging.
     */
    boolean isSmapSuppressed();

    /**
     * This setting is ignored if suppressSmap() is <code>true</code>.
     *
     * @return <code>true</code> to write SMAP info for JSR45 debugging to a file.
     */
    boolean isSmapDumped();

    /**
     * Returns the option for trimming whitespace-only template text.
     *
     * @return {@link TrimSpacesOption#TRUE} to remove template text that consists only of whitespace from the output
     *             completely, {@link TrimSpacesOption#SINGLE} to replace such template text with a single space,
     *             {@link TrimSpacesOption#FALSE} to leave such template text unchanged or
     *             {@link TrimSpacesOption#EXTENDED} to remove template text that consists only of whitespace and to
     *             replace any sequence of whitespace and new lines within template text with a single new line.
     */
    TrimSpacesOption getTrimSpaces();

    /**
     * Returns the scratch directory used as the work folder for temporary compilation files.
     *
     * @return the work folder
     */
    File getScratchDir();

    /**
     * Returns the classpath used by the compiler when compiling generated Servlets.
     *
     * @return the classpath used to compile generated Servlets
     */
    String getClassPath();

    /**
     * Compiler to use.
     * <p>
     * If <code>null</code> (the default), the java compiler from Eclipse JDT project, bundled with Tomcat, will be
     * used. Otherwise, the <code>javac</code> task from Apache Ant will be used to call an external java compiler and
     * the value of this option will be passed to it. See Apache Ant documentation for the possible values.
     *
     * @return the compiler name
     */
    String getCompiler();

    /**
     * Returns the target Java VM version for the compiler, such as 1.8.
     *
     * @return the compiler target VM, e.g. 1.8.
     */
    String getCompilerTargetVM();

    /**
     * Returns the source Java VM version for the compiler, such as 1.8.
     *
     * @return the compiler source VM, e.g. 1.8.
     */
    String getCompilerSourceVM();

    /**
     * Returns the fully qualified class name of the Jasper Java compiler implementation to use.
     *
     * @return Jasper Java compiler class to use.
     */
    String getCompilerClassName();

    /**
     * The cache that maps URIs, resource paths and parsed TLD files for the various tag libraries 'exposed' by the web
     * application. A tag library is 'exposed' either explicitly in web.xml or implicitly via the uri tag in the TLD of
     * a taglib deployed in a jar file (WEB-INF/lib).
     *
     * @return the instance of the TldCache for the web-application.
     */
    TldCache getTldCache();

    /**
     * Returns the Java platform character encoding used when generating the JSP page servlet source.
     *
     * @return Java platform encoding to generate the JSP page servlet.
     */
    String getJavaEncoding();

    /**
     * The boolean flag to tell Ant whether to fork JSP page compilations.
     * <p>
     * Is used only when Jasper uses an external java compiler (wrapped through a <code>javac</code> Apache Ant task).
     *
     * @return <code>true</code> to fork a process during compilation
     */
    boolean getFork();

    /**
     * Returns the JSP configuration information as specified in the web.xml deployment descriptor.
     *
     * @return JSP configuration information specified in web.xml.
     */
    JspConfig getJspConfig();

    /**
     * Returns whether the X-Powered-By response header should be generated in HTTP responses.
     *
     * @return <code>true</code> to generate a X-Powered-By response header.
     */
    boolean isXpoweredBy();

    /**
     * Returns the Tag Plugin Manager used for applying tag plugins during JSP compilation.
     *
     * @return a Tag Plugin Manager
     */
    TagPluginManager getTagPluginManager();

    /**
     * Indicates whether text strings are to be generated as char arrays.
     *
     * @return <code>true</code> if text strings are to be generated as char arrays, <code>false</code> otherwise
     */
    boolean genStringAsCharArray();

    /**
     * Returns the interval in seconds between modification tests for JSP files.
     *
     * @return modification test interval in seconds.
     */
    int getModificationTestInterval();


    /**
     * Returns whether the JSP engine should attempt re-compilation when an initial compilation fails.
     *
     * @return <code>true</code> if re-compile will occur on a failure.
     */
    boolean getRecompileOnFail();

    /**
     * Returns whether caching is enabled for compiled JSP pages, used for precompilation scenarios.
     *
     * @return <code>true</code> if caching is enabled (used for precompilation).
     */
    boolean isCaching();

    /**
     * The web-application wide cache for the TagLibraryInfo tag library descriptors, used if {@link #isCaching()}
     * returns <code>true</code>.
     * <p>
     * Using this cache avoids the cost of repeating the parsing of a tag library descriptor XML file (performed by
     * TagLibraryInfoImpl.parseTLD).
     * </p>
     *
     * @return the Map(String uri, TagLibraryInfo tld) instance.
     */
    Map<String,TagLibraryInfo> getCache();

    /**
     * The maximum number of loaded jsps per web-application. If there are more jsps loaded, they will be unloaded. If
     * unset or less than 0, no jsps are unloaded.
     *
     * @return The JSP count
     */
    int getMaxLoadedJsps();

    /**
     * Returns the idle timeout for JSP unloading.
     *
     * @return the idle time in seconds after which a JSP is unloaded. If unset or less or equal than 0, no jsps are
     *             unloaded.
     */
    int getJspIdleTimeout();

    /**
     * Returns whether strict quote escaping is enabled for scriplet expressions.
     *
     * @return {@code true} if the quote escaping required by section JSP.1.6 of the JSP specification should be applied
     *             to scriplet expression.
     */
    boolean getStrictQuoteEscaping();

    /**
     * Returns whether quoting rules from JSP.1.6 are applied to EL expressions in attributes.
     *
     * @return {@code true} if EL expressions used within attributes should have the quoting rules in JSP.1.6 applied to
     *             the expression.
     */
    boolean getQuoteAttributeEL();

    /**
     * Returns the variable name used in the generated JSP servlet code to reference the EL expression factory.
     *
     * @return the name of the variable that will be used in the generated JSP code for the expression factory
     */
    default String getVariableForExpressionFactory() {
        return "_el_expressionfactory";
    }

    /**
     * Returns the variable name used in the generated JSP servlet code to reference the instance manager.
     *
     * @return the name of the variable that will be used in the generated JSP code for the instance manager
     */
    default String getVariableForInstanceManager() {
        return "_jsp_instancemanager";
    }

    /**
     * Returns whether tag pooling is allowed on JSP pages that use the extends directive.
     *
     * @return {@code true} if tag pooling is disabled with page that uses extends.
     */
    default boolean getPoolTagsWithExtends() {
        return false;
    }

    /**
     * Returns whether strict enforcement of the JSP.5.3 requirement is applied, requiring objects used in
     * jsp:getProperty to be previously introduced to the JSP processor.
     *
     * @return {@code true} if the requirement to have the object used in jsp:getProperty action to be previously
     *             "introduced" to the JSP processor (see JSP.5.3) is enforced.
     */
    default boolean getStrictGetProperty() {
        return true;
    }

    /**
     * Returns whether strict whitespace handling rules are applied during JSP compilation.
     *
     * @return {@code true} if the strict white space rules are applied.
     */
    default boolean getStrictWhitespace() {
        return true;
    }

    /**
     * Returns the default base class for generated JSP servlets.
     *
     * @return the default base class for generated JSP Servlets
     */
    default String getJspServletBase() {
        return "org.apache.jasper.runtime.HttpJspBase";
    }

    /**
     * _jspService is the name of the method that is called by HttpJspBase.service(). This is where most of the code
     * generated from JSPs go.
     *
     * @return the method name
     */
    default String getServiceMethodName() {
        return "_jspService";
    }

    /**
     * Returns the ServletContext attribute name used for the classpath.
     *
     * @return ServletContext attribute for classpath. This is tomcat specific. Other servlet engines may choose to
     *             support this attribute if they want to have this JSP engine running on them.
     */
    default String getServletClasspathAttribute() {
        return "org.apache.catalina.jsp_classpath";
    }

    /**
     * Returns the query parameter name that triggers JSP pre-compilation.
     *
     * @return The query parameter that causes the JSP engine to just pregenerated the servlet but not invoke it.
     */
    default String getJspPrecompilationQueryParameter() {
        return "jsp_precompile";
    }

    /**
     * Returns the default package name for compiled JSP pages.
     *
     * @return The default package name for compiled jsp pages.
     */
    default String getGeneratedJspPackageName() {
        return "org.apache.jsp";
    }

    /**
     * Returns the default package name for tag handlers generated from tag files.
     *
     * @return The default package name for tag handlers generated from tag files.
     */
    default String getGeneratedTagFilePackageName() {
        return "org.apache.jsp.tag";
    }

    /**
     * Returns the prefix used for generated temporary variable names.
     *
     * @return Prefix to use for generated temporary variable names
     */
    default String getTempVariableNamePrefix() {
        return "_jspx_temp";
    }

    /**
     * Returns whether the container instance manager is used to create tag bean instances.
     *
     * @return {@code true} if the container instance manager will be used to create the bean instances
     */
    default boolean getUseInstanceManagerForTags() {
        return false;
    }


    /**
     * Should the container include the time the file was generated in the comments at the start of a Java file
     * generated from a JSP or tag. Defaults to {@code true}.
     *
     * @return {@code true} to include the timestamp, otherwise don't include it
     */
    default boolean getGeneratedJavaAddTimestamp() {
        return true;
    }

    /**
     * A string containing a comma-separated list of names to which custom tag implementations should be applied.
     * Unknown or unused tag entries are harmless. Generally defined via an init parameter on the JspServlet.
     *
     * @return which tags to use
     */
    String getUseNonstandardTagOptimizations();
}
