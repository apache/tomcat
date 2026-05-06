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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.util.Set;
import java.util.jar.JarEntry;

import jakarta.servlet.ServletContext;
import jakarta.servlet.jsp.tagext.TagInfo;

import org.apache.jasper.compiler.Compiler;
import org.apache.jasper.compiler.JspRuntimeContext;
import org.apache.jasper.compiler.JspUtil;
import org.apache.jasper.compiler.Localizer;
import org.apache.jasper.compiler.ServletWriter;
import org.apache.jasper.servlet.JasperLoader;
import org.apache.jasper.servlet.JspServletWrapper;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.Jar;
import org.apache.tomcat.util.descriptor.tld.TldResourcePath;

/**
 * A placeholder for various things that are used throughout the JSP engine. This is a per-request/per-context data
 * structure. Some of the instance variables are set at different points. Most of the path-related stuff is here -
 * mangling names, versions, dirs, loading resources and dealing with uris.
 */
public class JspCompilationContext {

    private final Log log = LogFactory.getLog(JspCompilationContext.class); // must not be static

    private String className;
    private final String jspUri;
    private String basePackageName;
    private String derivedPackageName;
    private String servletJavaFileName;
    private String javaPath;
    private String classFileName;
    private ServletWriter writer;
    private final Options options;
    private final JspServletWrapper jsw;
    private Compiler jspCompiler;
    private String classPath;

    private final String baseURI;
    private String outputDir;
    private final ServletContext context;
    private ClassLoader loader;

    private final JspRuntimeContext rctxt;

    private volatile boolean removed = false;

    // volatile so changes are visible when multiple threads request a JSP file
    // that has been modified
    private volatile URLClassLoader jspLoader;
    private URL baseUrl;
    private Class<?> servletClass;

    private final boolean isTagFile;
    private boolean protoTypeMode;
    private TagInfo tagInfo;
    private Jar tagJar;

    // jspURI _must_ be relative to the context
    /**
     * Creates a compilation context for a JSP page.
     *
     * @param jspUri The URI of the JSP page relative to the context
     * @param options The compilation options
     * @param context The servlet context
     * @param jsw The JSP servlet wrapper
     * @param rctxt The JSP runtime context
     */
    public JspCompilationContext(String jspUri, Options options, ServletContext context, JspServletWrapper jsw,
            JspRuntimeContext rctxt) {
        this(jspUri, null, options, context, jsw, rctxt, null, false);
    }

    /**
     * Creates a compilation context for a tag file.
     *
     * @param tagfile The URI of the tag file
     * @param tagInfo The tag information
     * @param options The compilation options
     * @param context The servlet context
     * @param jsw The JSP servlet wrapper
     * @param rctxt The JSP runtime context
     * @param tagJar The JAR containing the tag file
     */
    public JspCompilationContext(String tagfile, TagInfo tagInfo, Options options, ServletContext context,
            JspServletWrapper jsw, JspRuntimeContext rctxt, Jar tagJar) {
        this(tagfile, tagInfo, options, context, jsw, rctxt, tagJar, true);
    }

    private JspCompilationContext(String jspUri, TagInfo tagInfo, Options options, ServletContext context,
            JspServletWrapper jsw, JspRuntimeContext rctxt, Jar tagJar, boolean isTagFile) {

        this.jspUri = canonicalURI(jspUri);
        this.options = options;
        this.jsw = jsw;
        this.context = context;

        String baseURI = jspUri.substring(0, jspUri.lastIndexOf('/') + 1);
        // hack fix for resolveRelativeURI
        if (baseURI.isEmpty()) {
            baseURI = "/";
        } else if (baseURI.charAt(0) != '/') {
            // strip the base slash since it will be combined with the
            // uriBase to generate a file
            baseURI = "/" + baseURI;
        }
        if (baseURI.charAt(baseURI.length() - 1) != '/') {
            baseURI += '/';
        }
        this.baseURI = baseURI;

        this.rctxt = rctxt;
        this.basePackageName = options.getGeneratedJspPackageName();

        this.tagInfo = tagInfo;
        this.tagJar = tagJar;
        this.isTagFile = isTagFile;
    }


    /* ==================== Methods to override ==================== */

    // ---------- Class path and loader ----------

    /**
     * Returns the classpath used by the Java compiler for this JSP.
     *
     * @return the classpath that is passed off to the Java compiler
     */
    public String getClassPath() {
        if (classPath != null) {
            return classPath;
        }
        return rctxt.getClassPath();
    }

    /**
     * The classpath that is passed off to the Java compiler.
     *
     * @param classPath The class path to use
     */
    public void setClassPath(String classPath) {
        this.classPath = classPath;
    }

    /**
     * What class loader to use for loading classes while compiling this JSP?
     *
     * @return the class loader used to load all compiled classes
     */
    public ClassLoader getClassLoader() {
        if (loader != null) {
            return loader;
        }
        return rctxt.getParentClassLoader();
    }

    /**
     * Sets the class loader to use for loading classes while compiling this JSP.
     *
     * @param loader The class loader
     */
    public void setClassLoader(ClassLoader loader) {
        this.loader = loader;
    }

    /**
     * Returns the JSP-specific class loader, creating it if necessary.
     *
     * @return the JSP class loader
     */
    public ClassLoader getJspLoader() {
        if (jspLoader == null) {
            jspLoader = new JasperLoader(new URL[] { baseUrl }, getClassLoader(), basePackageName,
                    rctxt.getPermissionCollection());
        }
        return jspLoader;
    }

    /**
     * Clears the JSP class loader so it will be recreated on next access.
     */
    public void clearJspLoader() {
        jspLoader = null;
    }


    // ---------- Input/Output ----------

    /**
     * The output directory to generate code into. The output directory is make up of the scratch directory, which is
     * provided in Options, plus the directory derived from the package name.
     *
     * @return the output directory in which the generated sources are placed
     */
    public String getOutputDir() {
        if (outputDir == null) {
            createOutputDir();
        }

        return outputDir;
    }

    /**
     * Create a "Compiler" object based on some init param data. This is not done yet. Right now we're just hardcoding
     * the actual compilers that are created.
     *
     * @return the Java compiler wrapper
     */
    public Compiler createCompiler() {
        if (jspCompiler != null) {
            return jspCompiler;
        }
        if (options.getCompilerClassName() != null) {
            jspCompiler = createCompiler(options.getCompilerClassName());
        } else {
            if (options.getCompiler() == null) {
                jspCompiler = createCompiler("org.apache.jasper.compiler.JDTCompiler");
                if (jspCompiler == null) {
                    jspCompiler = createCompiler("org.apache.jasper.compiler.AntCompiler");
                }
            } else {
                jspCompiler = createCompiler("org.apache.jasper.compiler.AntCompiler");
                if (jspCompiler == null) {
                    jspCompiler = createCompiler("org.apache.jasper.compiler.JDTCompiler");
                }
            }
        }
        if (jspCompiler == null) {
            throw new IllegalStateException(Localizer.getMessage("jsp.error.compiler.config",
                    options.getCompilerClassName(), options.getCompiler()));
        }
        jspCompiler.init(this, jsw);
        return jspCompiler;
    }

    /**
     * Creates a compiler instance by class name using reflection.
     *
     * @param className The fully qualified compiler class name
     *
     * @return the compiler instance, or {@code null} if the class cannot be loaded
     */
    protected Compiler createCompiler(String className) {
        Compiler compiler = null;
        try {
            compiler = (Compiler) Class.forName(className).getConstructor().newInstance();
        } catch (NoClassDefFoundError | ClassNotFoundException e) {
            if (log.isDebugEnabled()) {
                log.debug(Localizer.getMessage("jsp.error.compiler"), e);
            }
        } catch (ReflectiveOperationException e) {
            log.warn(Localizer.getMessage("jsp.error.compiler"), e);
        }
        return compiler;
    }

    /**
     * Returns the compiler instance for this compilation context.
     *
     * @return the compiler instance
     */
    public Compiler getCompiler() {
        return jspCompiler;
    }

    // ---------- Access resources in the webapp ----------

    /**
     * Get the full value of a URI relative to this compilations context uses current file as the base.
     *
     * @param uri The relative URI
     *
     * @return absolute URI
     */
    public String resolveRelativeUri(String uri) {
        // sometimes we get uri's massaged from File(String), so check for
        // a root directory separator char
        if (uri.startsWith("/") || uri.startsWith(File.separator)) {
            return uri;
        } else {
            return baseURI + uri;
        }
    }

    /**
     * Gets a resource as a stream, relative to the meanings of this context's implementation.
     *
     * @param res the resource to look for
     *
     * @return a null if the resource cannot be found or represented as an InputStream.
     */
    public java.io.InputStream getResourceAsStream(String res) {
        return context.getResourceAsStream(canonicalURI(res));
    }


    /**
     * Gets a resource as a URL, relative to the context of this compilation.
     *
     * @param res The resource path
     *
     * @return the resource URL
     *
     * @throws MalformedURLException If the resource URL is malformed
     */
    public URL getResource(String res) throws MalformedURLException {
        return context.getResource(canonicalURI(res));
    }


    /**
     * Gets the resource paths beneath the given path, relative to the context.
     *
     * @param path The path to list resources for
     *
     * @return the set of resource paths
     */
    public Set<String> getResourcePaths(String path) {
        return context.getResourcePaths(canonicalURI(path));
    }

    /**
     * Gets the actual path of a URI relative to the context of the compilation.
     *
     * @param path The webapp path
     *
     * @return the corresponding path in the filesystem
     */
    public String getRealPath(String path) {
        if (context != null) {
            return context.getRealPath(path);
        }
        return path;
    }

    /**
     * Returns the JAR file in which the tag file for which this JspCompilationContext was created is packaged, or null
     * if this JspCompilationContext does not correspond to a tag file, or if the corresponding tag file is not packaged
     * in a JAR.
     *
     * @return a JAR file
     */
    public Jar getTagFileJar() {
        return this.tagJar;
    }

    /**
     * Sets the JAR file in which the tag file is packaged.
     *
     * @param tagJar The JAR file
     */
    public void setTagFileJar(Jar tagJar) {
        this.tagJar = tagJar;
    }

    /* ==================== Common implementation ==================== */

    /**
     * Just the class name (does not include package name) of the generated class.
     *
     * @return the class name
     */
    public String getServletClassName() {

        if (className != null) {
            return className;
        }

        if (isTagFile) {
            className = tagInfo.getTagClassName();
            int lastIndex = className.lastIndexOf('.');
            if (lastIndex != -1) {
                className = className.substring(lastIndex + 1);
            }
        } else {
            int iSep = jspUri.lastIndexOf('/') + 1;
            className = JspUtil.makeJavaIdentifier(jspUri.substring(iSep));
        }
        return className;
    }

    /**
     * Sets the class name for the generated servlet.
     *
     * @param className The class name
     */
    public void setServletClassName(String className) {
        this.className = className;
    }

    /**
     * Path of the JSP URI. Note that this is not a file name. This is the context rooted URI of the JSP file.
     *
     * @return the path to the JSP
     */
    public String getJspFile() {
        return jspUri;
    }


    /**
     * Returns the last modified time of the given resource using the default tag JAR.
     *
     * @param resource The resource path
     *
     * @return the last modified time in milliseconds, or -1 if not available
     */
    public Long getLastModified(String resource) {
        return getLastModified(resource, tagJar);
    }


    /**
     * Returns the last modified time of the given resource.
     *
     * @param resource The resource path
     * @param tagJar The JAR file containing the resource, or {@code null} for webapp resources
     *
     * @return the last modified time in milliseconds, or -1 if not available
     */
    public Long getLastModified(String resource, Jar tagJar) {
        long result = -1;
        URLConnection uc = null;
        try {
            if (tagJar != null) {
                if (resource.startsWith("/")) {
                    resource = resource.substring(1);
                }
                result = tagJar.getLastModified(resource);
            } else {
                URL jspUrl = getResource(resource);
                if (jspUrl == null) {
                    incrementRemoved();
                    return Long.valueOf(result);
                }
                uc = jspUrl.openConnection();
                if (uc instanceof JarURLConnection) {
                    JarEntry jarEntry = ((JarURLConnection) uc).getJarEntry();
                    if (jarEntry != null) {
                        result = jarEntry.getTime();
                    } else {
                        result = uc.getLastModified();
                    }
                } else {
                    result = uc.getLastModified();
                }
            }
        } catch (IOException ioe) {
            if (log.isDebugEnabled()) {
                log.debug(Localizer.getMessage("jsp.error.lastModified", getJspFile()), ioe);
            }
        } finally {
            if (uc != null) {
                try {
                    uc.getInputStream().close();
                } catch (IOException ioe) {
                    if (log.isDebugEnabled()) {
                        log.debug(Localizer.getMessage("jsp.error.lastModified", getJspFile()), ioe);
                    }
                    result = -1;
                }
            }
        }
        return Long.valueOf(result);
    }

    /**
     * Returns whether this compilation context corresponds to a tag file.
     *
     * @return {@code true} if this is a tag file
     */
    public boolean isTagFile() {
        return isTagFile;
    }

    /**
     * Returns the tag information for this compilation context.
     *
     * @return the tag info, or {@code null} if not a tag file
     */
    public TagInfo getTagInfo() {
        return tagInfo;
    }

    /**
     * Sets the tag information for this compilation context.
     *
     * @param tagi The tag info
     */
    public void setTagInfo(TagInfo tagi) {
        tagInfo = tagi;
    }

    /**
     * Returns whether we are compiling a tag file in prototype mode, i.e., generating
     * code with class for the tag handler with empty method bodies.
     *
     * @return {@code true} if we are compiling a tag file in prototype mode
     */
    public boolean isPrototypeMode() {
        return protoTypeMode;
    }

    /**
     * Sets the prototype mode for tag file compilation.
     *
     * @param pm The prototype mode flag
     */
    public void setPrototypeMode(boolean pm) {
        protoTypeMode = pm;
    }

    /**
     * Package name for the generated class is made up of the base package name, which is user settable, and the derived
     * package name. The derived package name directly mirrors the file hierarchy of the JSP page.
     *
     * @return the package name
     */
    public String getServletPackageName() {
        if (isTagFile()) {
            String className = tagInfo.getTagClassName();
            int lastIndex = className.lastIndexOf('.');
            String packageName = "";
            if (lastIndex != -1) {
                packageName = className.substring(0, lastIndex);
            }
            return packageName;
        } else {
            String dPackageName = getDerivedPackageName();
            if (dPackageName.isEmpty()) {
                return basePackageName;
            }
            return basePackageName + '.' + getDerivedPackageName();
        }
    }

    /**
     * Returns the derived package name based on the JSP URI path hierarchy.
     *
     * @return the derived package name
     */
    protected String getDerivedPackageName() {
        if (derivedPackageName == null) {
            int iSep = jspUri.lastIndexOf('/');
            derivedPackageName = (iSep > 0) ? JspUtil.makeJavaPackage(jspUri.substring(1, iSep)) : "";
        }
        return derivedPackageName;
    }

    /**
     * Returns the base package name into which all servlet and associated code is generated.
     *
     * @return the base package name
     */
    public String getBasePackageName() {
        return basePackageName;
    }

    /**
     * The package name into which the servlet class is generated.
     *
     * @param basePackageName The package name to use
     */
    public void setBasePackageName(String basePackageName) {
        this.basePackageName = basePackageName;
    }

    /**
     * Returns the full path name of the Java file into which the servlet is being generated.
     *
     * @return the full path of the generated Java file
     */
    public String getServletJavaFileName() {
        if (servletJavaFileName == null) {
            servletJavaFileName = getOutputDir() + getServletClassName() + ".java";
        }
        return servletJavaFileName;
    }

    /**
     * Returns the options object for this compilation context.
     *
     * @return the options object
     */
    public Options getOptions() {
        return options;
    }

    /**
     * Returns the servlet context for this compilation context.
     *
     * @return the servlet context
     */
    public ServletContext getServletContext() {
        return context;
    }

    /**
     * Returns the JSP runtime context for this compilation context.
     *
     * @return the runtime context
     */
    public JspRuntimeContext getRuntimeContext() {
        return rctxt;
    }

    /**
     * Returns the path of the Java file relative to the work directory.
     *
     * @return the relative path of the Java file
     */
    public String getJavaPath() {

        if (javaPath != null) {
            return javaPath;
        }

        if (isTagFile()) {
            String tagName = tagInfo.getTagClassName();
            javaPath = tagName.replace('.', '/') + ".java";
        } else {
            javaPath = getServletPackageName().replace('.', '/') + '/' + getServletClassName() + ".java";
        }
        return javaPath;
    }

    /**
     * Returns the full path name of the compiled class file.
     *
     * @return the class file path
     */
    public String getClassFileName() {
        if (classFileName == null) {
            classFileName = getOutputDir() + getServletClassName() + ".class";
        }
        return classFileName;
    }

    /**
     * Returns the writer used to write the generated Servlet source code.
     *
     * @return the servlet writer
     */
    public ServletWriter getWriter() {
        return writer;
    }

    /**
     * Sets the writer used to write the generated Servlet source code.
     *
     * @param writer The servlet writer
     */
    public void setWriter(ServletWriter writer) {
        this.writer = writer;
    }

    /**
     * Gets the 'location' of the TLD associated with the given taglib 'uri'.
     *
     * @param uri The taglib URI
     *
     * @return An array of two Strings: The first element denotes the real path to the TLD. If the path to the TLD
     *             points to a jar file, then the second element denotes the name of the TLD entry in the jar file.
     *             Returns null if the given uri is not associated with any tag library 'exposed' in the web
     *             application.
     */
    public TldResourcePath getTldResourcePath(String uri) {
        return getOptions().getTldCache().getTldResourcePath(uri);
    }

    /**
     * Returns whether the generated code is kept after compilation.
     *
     * @return {@code true} if generated code is kept
     */
    public boolean keepGenerated() {
        return getOptions().getKeepGenerated();
    }

    // ==================== Removal ====================

    /**
     * Increments the removed counter and removes the wrapper from the runtime context.
     */
    public void incrementRemoved() {
        if (!removed && rctxt != null) {
            rctxt.removeWrapper(jspUri);
        }
        removed = true;
    }

    /**
     * Returns whether this JSP has been removed.
     *
     * @return {@code true} if this JSP has been removed
     */
    public boolean isRemoved() {
        return removed;
    }

    // ==================== Compile and reload ====================

    /**
     * Compiles the JSP if it is out of date with respect to the generated class file.
     *
     * @throws JasperException If a compilation error occurs
     * @throws FileNotFoundException If the JSP file has been removed
     */
    public void compile() throws JasperException, FileNotFoundException {
        createCompiler();
        if (jspCompiler.isOutDated()) {
            if (isRemoved()) {
                throw new FileNotFoundException(jspUri);
            }
            try {
                jspCompiler.removeGeneratedFiles();
                jspLoader = null;
                jspCompiler.compile();
                jsw.setCompilationException(null);
            } catch (JasperException ex) {
                // Cache compilation exception
                jsw.setCompilationException(ex);
                if (options.getDevelopment() && options.getRecompileOnFail()) {
                    // Force a recompilation attempt on next access
                    jsw.setLastModificationTest(-1);
                }
                throw ex;
            } catch (FileNotFoundException fnfe) {
                // Re-throw to let caller handle this - will result in a 404
                throw fnfe;
            } catch (Exception e) {
                JasperException je = new JasperException(Localizer.getMessage("jsp.error.unable.compile"), e);
                // Cache compilation exception
                jsw.setCompilationException(je);
                throw je;
            } finally {
                jsw.setReload(true);
            }
        }
    }

    // ==================== Manipulating the class ====================

    /**
     * Loads the compiled servlet class using the JSP class loader.
     *
     * @return the loaded servlet class
     *
     * @throws JasperException If the class cannot be loaded
     */
    public Class<?> load() throws JasperException {
        try {
            getJspLoader();

            String name = getFQCN();
            servletClass = jspLoader.loadClass(name);
        } catch (ClassNotFoundException cex) {
            throw new JasperException(Localizer.getMessage("jsp.error.unable.load"), cex);
        } catch (Exception e) {
            throw new JasperException(Localizer.getMessage("jsp.error.unable.compile"), e);
        }
        removed = false;
        return servletClass;
    }

    /**
     * Returns the fully qualified class name of the generated servlet or tag handler.
     *
     * @return the fully qualified class name
     */
    public String getFQCN() {
        String name;
        if (isTagFile()) {
            name = tagInfo.getTagClassName();
        } else {
            name = getServletPackageName() + "." + getServletClassName();
        }
        return name;
    }

    // ==================== protected methods ====================

    private static final Object outputDirLock = new Object();

    /**
     * Checks and creates the output directory if it does not exist.
     */
    public void checkOutputDir() {
        if (outputDir != null) {
            if (!(new File(outputDir)).exists()) {
                makeOutputDir();
            }
        } else {
            createOutputDir();
        }
    }

    /**
     * Creates the output directory for generated files.
     *
     * @return {@code true} if the directory was created or already exists
     */
    protected boolean makeOutputDir() {
        synchronized (outputDirLock) {
            File outDirFile = new File(outputDir);
            return (outDirFile.mkdirs() || outDirFile.isDirectory());
        }
    }

    /**
     * Creates the output directory path based on the package name and scratch directory.
     */
    protected void createOutputDir() {
        String path;
        if (isTagFile()) {
            String tagName = tagInfo.getTagClassName();
            path = tagName.replace('.', File.separatorChar);
            path = path.substring(0, path.lastIndexOf(File.separatorChar));
        } else {
            path = getServletPackageName().replace('.', File.separatorChar);
        }

        // Append servlet or tag handler path to scratch dir
        try {
            File base = options.getScratchDir();
            baseUrl = base.toURI().toURL();
            outputDir = base.getAbsolutePath() + File.separator + path + File.separator;
            if (!makeOutputDir()) {
                log.error(Localizer.getMessage("jsp.error.outputfolder.detail", outputDir));
                throw new IllegalStateException(Localizer.getMessage("jsp.error.outputfolder"));
            }
        } catch (MalformedURLException e) {
            throw new IllegalStateException(Localizer.getMessage("jsp.error.outputfolder"), e);
        }
    }

    /**
     * Checks whether the given character is a path separator.
     *
     * @param c The character to check
     *
     * @return {@code true} if the character is '/' or '\\'
     */
    protected static boolean isPathSeparator(char c) {
        return (c == '/' || c == '\\');
    }

    /**
     * Canonicalizes a URI string by resolving relative path components like '.' and '..'
     * and collapsing multiple separators.
     *
     * @param s The URI string to canonicalize
     *
     * @return the canonical URI, or {@code null} if the input is {@code null}
     */
    protected static String canonicalURI(String s) {
        if (s == null) {
            return null;
        }
        StringBuilder result = new StringBuilder();
        final int len = s.length();
        int pos = 0;
        while (pos < len) {
            char c = s.charAt(pos);
            if (isPathSeparator(c)) {
                /*
                 * multiple path separators. 'foo///bar' -> 'foo/bar'
                 */
                while (pos + 1 < len && isPathSeparator(s.charAt(pos + 1))) {
                    ++pos;
                }

                if (pos + 1 < len && s.charAt(pos + 1) == '.') {
                    /*
                     * a single dot at the end of the path - we are done.
                     */
                    if (pos + 2 >= len) {
                        break;
                    }

                    switch (s.charAt(pos + 2)) {
                        /*
                         * self directory in path foo/./bar -> foo/bar
                         */
                        case '/':
                        case '\\':
                            pos += 2;
                            continue;

                        /*
                         * two dots in a path: go back one hierarchy. foo/bar/../baz -> foo/baz
                         */
                        case '.':
                            // only if we have exactly _two_ dots.
                            if (pos + 3 < len && isPathSeparator(s.charAt(pos + 3))) {
                                pos += 3;
                                int separatorPos = result.length() - 1;
                                while (separatorPos >= 0 && !isPathSeparator(result.charAt(separatorPos))) {
                                    --separatorPos;
                                }
                                if (separatorPos >= 0) {
                                    result.setLength(separatorPos);
                                }
                                continue;
                            }
                    }
                }
            }
            result.append(c);
            ++pos;
        }
        return result.toString();
    }
}
