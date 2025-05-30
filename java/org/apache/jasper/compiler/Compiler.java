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
package org.apache.jasper.compiler;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.jasper.JasperException;
import org.apache.jasper.JspCompilationContext;
import org.apache.jasper.Options;
import org.apache.jasper.TrimSpacesOption;
import org.apache.jasper.servlet.JspServletWrapper;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.Jar;
import org.apache.tomcat.util.scan.JarFactory;

/**
 * Main JSP compiler class. This class uses Ant for compiling.
 *
 * @author Anil K. Vijendran
 * @author Mandar Raje
 * @author Pierre Delisle
 * @author Kin-man Chung
 * @author Remy Maucherat
 * @author Mark Roth
 */
public abstract class Compiler {

    private final Log log = LogFactory.getLog(Compiler.class); // must not be static

    // ----------------------------------------------------- Instance Variables

    protected JspCompilationContext ctxt;

    protected ErrorDispatcher errDispatcher;

    protected PageInfo pageInfo;

    protected JspServletWrapper jsw;

    protected TagFileProcessor tfp;

    protected Options options;

    protected Node.Nodes pageNodes;


    // ------------------------------------------------------------ Constructor

    public void init(JspCompilationContext ctxt, JspServletWrapper jsw) {
        this.jsw = jsw;
        this.ctxt = ctxt;
        this.options = ctxt.getOptions();
    }


    // --------------------------------------------------------- Public Methods

    public SmapStratum getSmap(String className) {

        Map<String,SmapStratum> smaps = ctxt.getRuntimeContext().getSmaps();
        SmapStratum smap = smaps.get(className);

        if (smap == null && !options.isSmapSuppressed()) {
            // Tomcat was restarted so cached SMAP has been lost. However, it
            // was written to the class file so it can be recovered.
            smap = SmapUtil.loadSmap(className, ctxt.getJspLoader());
            if (smap != null) {
                smaps.put(className, smap);
            }
        }

        return smap;
    }


    /**
     * Compile the jsp file into equivalent servlet in .java file
     *
     * @return A map of class names to JSR 045 source maps
     *
     * @throws Exception Error generating Java source
     */
    protected Map<String,SmapStratum> generateJava() throws Exception {

        long t1 = 0;
        long t2 = 0;
        long t3 = 0;
        long t4;

        if (log.isDebugEnabled()) {
            t1 = System.currentTimeMillis();
        }

        // Setup page info area
        pageInfo = new PageInfo(new BeanRepository(ctxt.getClassLoader(), errDispatcher), ctxt);

        JspConfig jspConfig = options.getJspConfig();
        JspConfig.JspProperty jspProperty = jspConfig.findJspProperty(ctxt.getJspFile());

        /*
         * If the current uri is matched by a pattern specified in a jsp-property-group in web.xml, initialize pageInfo
         * with those properties.
         */
        if (jspProperty.isELIgnored() != null) {
            pageInfo.setELIgnored(JspUtil.booleanValue(jspProperty.isELIgnored()));
        }
        if (jspProperty.getErrorOnELNotFound() != null) {
            pageInfo.setErrorOnELNotFound(JspUtil.booleanValue(jspProperty.getErrorOnELNotFound()));
        }
        if (jspProperty.isScriptingInvalid() != null) {
            pageInfo.setScriptingInvalid(JspUtil.booleanValue(jspProperty.isScriptingInvalid()));
        }
        if (jspProperty.getIncludePrelude() != null) {
            pageInfo.setIncludePrelude(jspProperty.getIncludePrelude());
        }
        if (jspProperty.getIncludeCoda() != null) {
            pageInfo.setIncludeCoda(jspProperty.getIncludeCoda());
        }
        if (jspProperty.isDeferedSyntaxAllowedAsLiteral() != null) {
            pageInfo.setDeferredSyntaxAllowedAsLiteral(
                    JspUtil.booleanValue(jspProperty.isDeferedSyntaxAllowedAsLiteral()));
        }
        if (jspProperty.isTrimDirectiveWhitespaces() != null) {
            pageInfo.setTrimDirectiveWhitespaces(JspUtil.booleanValue(jspProperty.isTrimDirectiveWhitespaces()));
        }
        // Default ContentType processing is deferred until after the page has
        // been parsed
        if (jspProperty.getBuffer() != null) {
            pageInfo.setBufferValue(jspProperty.getBuffer(), null, errDispatcher);
        }
        if (jspProperty.isErrorOnUndeclaredNamespace() != null) {
            pageInfo.setErrorOnUndeclaredNamespace(JspUtil.booleanValue(jspProperty.isErrorOnUndeclaredNamespace()));
        }
        if (ctxt.isTagFile()) {
            try {
                double libraryVersion = Double.parseDouble(ctxt.getTagInfo().getTagLibrary().getRequiredVersion());
                if (libraryVersion < 2.0) {
                    pageInfo.setIsELIgnored("true", null, errDispatcher, true);
                }
                if (libraryVersion < 2.1) {
                    pageInfo.setDeferredSyntaxAllowedAsLiteral("true", null, errDispatcher, true);
                }
            } catch (NumberFormatException ex) {
                errDispatcher.jspError(ex);
            }
        }

        ctxt.checkOutputDir();
        String javaFileName = ctxt.getServletJavaFileName();

        try {
            /*
             * The setting of isELIgnored changes the behaviour of the parser in subtle ways. To add to the 'fun',
             * isELIgnored can be set in any file that forms part of the translation unit so setting it in a file
             * included towards the end of the translation unit can change how the parser should have behaved when
             * parsing content up to the point where isELIgnored was set. Arghh! Previous attempts to hack around this
             * have only provided partial solutions. We now use two passes to parse the translation unit. The first just
             * parses the directives and the second parses the whole translation unit once we know how isELIgnored has
             * been set. TODO There are some possible optimisations of this process.
             */
            // Parse the file
            ParserController parserCtl = new ParserController(ctxt, this);

            // Pass 1 - the directives
            Node.Nodes directives = parserCtl.parseDirectives(ctxt.getJspFile());
            Validator.validateDirectives(this, directives);

            // Pass 2 - the whole translation unit
            pageNodes = parserCtl.parse(ctxt.getJspFile());

            // Leave this until now since it can only be set once - bug 49726
            if (pageInfo.getContentType() == null && jspProperty.getDefaultContentType() != null) {
                pageInfo.setContentType(jspProperty.getDefaultContentType());
            }

            if (ctxt.isPrototypeMode()) {
                // generate prototype .java file for the tag file
                try (ServletWriter writer = setupContextWriter(javaFileName)) {
                    Generator.generate(writer, this, pageNodes);
                    return null;
                }
            }

            // Validate and process attributes - don't re-validate the
            // directives we validated in pass 1
            Validator.validateExDirectives(this, pageNodes);

            if (log.isDebugEnabled()) {
                t2 = System.currentTimeMillis();
            }

            // Collect page info
            Collector.collect(this, pageNodes);

            // Compile (if necessary) and load the tag files referenced in
            // this compilation unit.
            tfp = new TagFileProcessor();
            tfp.loadTagFiles(this, pageNodes);

            if (log.isDebugEnabled()) {
                t3 = System.currentTimeMillis();
            }

            // Determine which custom tag needs to declare which scripting vars
            ScriptingVariabler.set(pageNodes, errDispatcher);

            // Optimizations by Tag Plugins
            TagPluginManager tagPluginManager = options.getTagPluginManager();
            tagPluginManager.apply(pageNodes, errDispatcher, pageInfo);

            // Optimization: concatenate contiguous template texts.
            TextOptimizer.concatenate(this, pageNodes);

            // Generate static function mapper codes.
            ELFunctionMapper.map(pageNodes);

            // generate servlet .java file
            try (ServletWriter writer = setupContextWriter(javaFileName)) {
                Generator.generate(writer, this, pageNodes);
            }

            // The writer is only used during compile, dereference
            // it in the JspCompilationContext when done to allow it
            // to be GCed and save memory.
            ctxt.setWriter(null);

            if (log.isTraceEnabled()) {
                t4 = System.currentTimeMillis();
                log.trace("Generated " + javaFileName + " total=" + (t4 - t1) + " generate=" + (t4 - t3) +
                        " validate=" + (t2 - t1));
            }

        } catch (RuntimeException e) {
            // Remove the generated .java file
            File file = new File(javaFileName);
            if (file.exists()) {
                if (!file.delete()) {
                    log.warn(Localizer.getMessage("jsp.warning.compiler.javafile.delete.fail", file.getAbsolutePath()));
                }
            }
            throw e;
        }

        Map<String,SmapStratum> smaps = null;

        // JSR45 Support
        if (!options.isSmapSuppressed()) {
            smaps = SmapUtil.generateSmap(ctxt, pageNodes);
            // Add them to the web application wide cache for future lookup in
            // error handling etc.
            ctxt.getRuntimeContext().getSmaps().putAll(smaps);
        }

        // If any prototype .java and .class files was generated,
        // the prototype .java may have been replaced by the current
        // compilation (if the tag file is self referencing), but the
        // .class file need to be removed, to make sure that javac would
        // generate .class again from the new .java file just generated.
        tfp.removeProtoTypeFiles(ctxt.getClassFileName());

        return smaps;
    }

    private ServletWriter setupContextWriter(String javaFileName) throws FileNotFoundException, JasperException {
        ServletWriter writer;
        // Set up the ServletWriter
        String javaEncoding = ctxt.getOptions().getJavaEncoding();
        OutputStreamWriter osw = null;

        try {
            osw = new OutputStreamWriter(new FileOutputStream(javaFileName), javaEncoding);
        } catch (UnsupportedEncodingException ex) {
            errDispatcher.jspError("jsp.error.needAlternateJavaEncoding", javaEncoding);
        }

        if (ctxt.getOptions().getTrimSpaces().equals(TrimSpacesOption.EXTENDED)) {
            writer = new NewlineReductionServletWriter(new PrintWriter(osw));
        } else {
            writer = new ServletWriter(new PrintWriter(osw));
        }

        ctxt.setWriter(writer);
        return writer;
    }

    /**
     * Servlet compilation. This compiles the generated sources into Servlets.
     *
     * @param smaps The source maps for the class(es) generated from the source file
     *
     * @throws FileNotFoundException Source files not found
     * @throws JasperException       Compilation error
     * @throws Exception             Some other error
     */
    protected abstract void generateClass(Map<String,SmapStratum> smaps)
            throws FileNotFoundException, JasperException, Exception;

    /**
     * Compile the jsp file from the current engine context.
     *
     * @throws FileNotFoundException Source files not found
     * @throws JasperException       Compilation error
     * @throws Exception             Some other error
     */
    public void compile() throws FileNotFoundException, JasperException, Exception {
        compile(true);
    }

    /**
     * Compile the jsp file from the current engine context. As a side effect, tag files that are referenced by this
     * page are also compiled.
     *
     * @param compileClass If true, generate both .java and .class file If false, generate only .java file
     *
     * @throws FileNotFoundException Source files not found
     * @throws JasperException       Compilation error
     * @throws Exception             Some other error
     */
    public void compile(boolean compileClass) throws FileNotFoundException, JasperException, Exception {
        compile(compileClass, false);
    }

    /**
     * Compile the jsp file from the current engine context. As a side effect, tag files that are referenced by this
     * page are also compiled.
     *
     * @param compileClass If true, generate both .java and .class file If false, generate only .java file
     * @param jspcMode     true if invoked from JspC, false otherwise
     *
     * @throws FileNotFoundException Source files not found
     * @throws JasperException       Compilation error
     * @throws Exception             Some other error
     */
    public void compile(boolean compileClass, boolean jspcMode)
            throws FileNotFoundException, JasperException, Exception {
        if (errDispatcher == null) {
            this.errDispatcher = new ErrorDispatcher(jspcMode);
        }

        try {
            final Long jspLastModified = ctxt.getLastModified(ctxt.getJspFile());
            Map<String,SmapStratum> smaps = generateJava();
            File javaFile = new File(ctxt.getServletJavaFileName());
            if (!javaFile.setLastModified(jspLastModified.longValue())) {
                throw new JasperException(Localizer.getMessage("jsp.error.setLastModified", javaFile));
            }
            if (compileClass) {
                generateClass(smaps);
                // Fix for bugzilla 41606
                // Set JspServletWrapper.servletClassLastModifiedTime after successful compile
                File targetFile = new File(ctxt.getClassFileName());
                if (targetFile.exists()) {
                    if (!targetFile.setLastModified(jspLastModified.longValue())) {
                        throw new JasperException(Localizer.getMessage("jsp.error.setLastModified", targetFile));
                    }
                    if (jsw != null) {
                        jsw.setServletClassLastModifiedTime(jspLastModified.longValue());
                    }
                }
            }
        } finally {
            if (tfp != null && ctxt.isPrototypeMode()) {
                tfp.removeProtoTypeFiles(null);
            }
            // Make sure these object which are only used during the
            // generation and compilation of the JSP page get
            // dereferenced so that they can be GCed and reduce the
            // memory footprint.
            tfp = null;
            errDispatcher = null;
            pageInfo = null;
            pageNodes = null;

            if (ctxt.getWriter() != null) {
                ctxt.getWriter().close();
                ctxt.setWriter(null);
            }
        }
    }

    /**
     * This is a protected method intended to be overridden by subclasses of Compiler. This is used by the compile
     * method to do all the compilation.
     *
     * @return <code>true</code> if the source generation and compilation should occur
     */
    public boolean isOutDated() {
        return isOutDated(true);
    }

    /**
     * Determine if a compilation is necessary by checking the time stamp of the JSP page with that of the corresponding
     * .class or .java file. If the page has dependencies, the check is also extended to its dependents, and so on. This
     * method can be overridden by a subclasses of Compiler.
     *
     * @param checkClass If true, check against .class file, if false, check against .java file.
     *
     * @return <code>true</code> if the source generation and compilation should occur
     */
    public boolean isOutDated(boolean checkClass) {

        if (jsw != null && (ctxt.getOptions().getModificationTestInterval() > 0)) {

            if (jsw.getLastModificationTest() +
                (ctxt.getOptions().getModificationTestInterval() * 1000L) > System.currentTimeMillis()) {
                return false;
            }
            jsw.setLastModificationTest(System.currentTimeMillis());
        }

        // Test the target file first. Unless there is an error checking the
        // last modified time of the source (unlikely) the target is going to
        // have to be checked anyway. If the target doesn't exist (likely during
        // startup) this saves an unnecessary check of the source.
        File targetFile;
        if (checkClass) {
            targetFile = new File(ctxt.getClassFileName());
        } else {
            targetFile = new File(ctxt.getServletJavaFileName());
        }
        if (!targetFile.exists()) {
            return true;
        }
        long targetLastModified = targetFile.lastModified();
        if (checkClass && jsw != null) {
            jsw.setServletClassLastModifiedTime(targetLastModified);
        }

        Long jspRealLastModified = ctxt.getLastModified(ctxt.getJspFile());
        if (jspRealLastModified.longValue() < 0) {
            // Something went wrong - assume modification
            return true;
        }

        if (targetLastModified != jspRealLastModified.longValue()) {
            if (log.isTraceEnabled()) {
                log.trace("Compiler: outdated: " + targetFile + " " + targetLastModified);
            }
            return true;
        }

        // determine if source dependent files (e.g. includes using include
        // directives) have been changed.
        if (jsw == null) {
            return false;
        }

        Map<String,Long> depends = jsw.getDependants();
        if (depends == null) {
            return false;
        }

        for (Entry<String,Long> include : depends.entrySet()) {
            try {
                String key = include.getKey();
                URL includeUrl;
                long includeLastModified;
                if (key.startsWith("jar:jar:")) {
                    // Assume we constructed this correctly
                    int entryStart = key.lastIndexOf("!/");
                    String entry = key.substring(entryStart + 2);
                    try (Jar jar = JarFactory.newInstance(new URI(key.substring(4, entryStart)).toURL())) {
                        includeLastModified = jar.getLastModified(entry);
                    }
                } else {
                    if (key.startsWith("jar:") || key.startsWith("file:")) {
                        includeUrl = new URI(key).toURL();
                    } else {
                        includeUrl = ctxt.getResource(include.getKey());
                    }
                    if (includeUrl == null) {
                        return true;
                    }
                    URLConnection iuc = includeUrl.openConnection();
                    if (iuc instanceof JarURLConnection) {
                        includeLastModified = ((JarURLConnection) iuc).getJarEntry().getTime();
                    } else {
                        includeLastModified = iuc.getLastModified();
                    }
                    iuc.getInputStream().close();
                }

                if (includeLastModified != include.getValue().longValue()) {
                    return true;
                }
            } catch (Exception e) {
                if (log.isDebugEnabled()) {
                    log.debug(Localizer.getMessage("jsp.error.compiler.missingResource"), e);
                }
                return true;
            }
        }

        return false;

    }

    /**
     * @return the error dispatcher.
     */
    public ErrorDispatcher getErrorDispatcher() {
        return errDispatcher;
    }

    /**
     * @return the info about the page under compilation
     */
    public PageInfo getPageInfo() {
        return pageInfo;
    }

    public JspCompilationContext getCompilationContext() {
        return ctxt;
    }

    /**
     * Remove generated files
     */
    public void removeGeneratedFiles() {
        removeGeneratedClassFiles();

        try {
            File javaFile = new File(ctxt.getServletJavaFileName());
            if (log.isTraceEnabled()) {
                log.trace("Deleting " + javaFile);
            }
            if (javaFile.exists()) {
                if (!javaFile.delete()) {
                    log.warn(Localizer.getMessage("jsp.warning.compiler.javafile.delete.fail",
                            javaFile.getAbsolutePath()));
                }
            }
        } catch (Exception e) {
            // Remove as much as possible, log possible exceptions
            log.warn(Localizer.getMessage("jsp.warning.compiler.classfile.delete.fail.unknown"), e);
        }
    }

    public void removeGeneratedClassFiles() {
        try {
            File classFile = new File(ctxt.getClassFileName());
            if (log.isTraceEnabled()) {
                log.trace("Deleting " + classFile);
            }
            if (classFile.exists()) {
                if (!classFile.delete()) {
                    log.warn(Localizer.getMessage("jsp.warning.compiler.classfile.delete.fail",
                            classFile.getAbsolutePath()));
                }
            }
        } catch (Exception e) {
            // Remove as much as possible, log possible exceptions
            log.warn(Localizer.getMessage("jsp.warning.compiler.classfile.delete.fail.unknown"), e);
        }
    }
}
