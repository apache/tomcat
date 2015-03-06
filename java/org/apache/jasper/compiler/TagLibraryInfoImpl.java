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

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.jsp.tagext.FunctionInfo;
import javax.servlet.jsp.tagext.PageData;
import javax.servlet.jsp.tagext.TagAttributeInfo;
import javax.servlet.jsp.tagext.TagExtraInfo;
import javax.servlet.jsp.tagext.TagFileInfo;
import javax.servlet.jsp.tagext.TagInfo;
import javax.servlet.jsp.tagext.TagLibraryInfo;
import javax.servlet.jsp.tagext.TagLibraryValidator;
import javax.servlet.jsp.tagext.TagVariableInfo;
import javax.servlet.jsp.tagext.ValidationMessage;

import org.apache.jasper.JasperException;
import org.apache.jasper.JspCompilationContext;
import org.apache.tomcat.util.descriptor.tld.TagFileXml;
import org.apache.tomcat.util.descriptor.tld.TagXml;
import org.apache.tomcat.util.descriptor.tld.TaglibXml;
import org.apache.tomcat.util.descriptor.tld.TldResourcePath;
import org.apache.tomcat.util.descriptor.tld.ValidatorXml;
import org.apache.tomcat.util.scan.Jar;

/**
 * Implementation of the TagLibraryInfo class from the JSP spec.
 *
 * @author Anil K. Vijendran
 * @author Mandar Raje
 * @author Pierre Delisle
 * @author Kin-man Chung
 * @author Jan Luehe
 */
class TagLibraryInfoImpl extends TagLibraryInfo implements TagConstants {

    private final JspCompilationContext ctxt;

    private final PageInfo pi;

    private final ErrorDispatcher err;

    private final ParserController parserController;

    private static void print(String name, String value, PrintWriter w) {
        if (value != null) {
            w.print(name + " = {\n\t");
            w.print(value);
            w.print("\n}\n");
        }
    }

    @Override
    public String toString() {
        StringWriter sw = new StringWriter();
        PrintWriter out = new PrintWriter(sw);
        print("tlibversion", tlibversion, out);
        print("jspversion", jspversion, out);
        print("shortname", shortname, out);
        print("urn", urn, out);
        print("info", info, out);
        print("uri", uri, out);
        print("tagLibraryValidator", "" + tagLibraryValidator, out);

        for (TagInfo tag : tags) {
            out.println(tag.toString());
        }

        for (TagFileInfo tagFile : tagFiles) {
            out.println(tagFile.toString());
        }

        for (FunctionInfo function : functions) {
            out.println(function.toString());
        }

        return sw.toString();
    }

    /**
     * Constructor.
     */
    public TagLibraryInfoImpl(JspCompilationContext ctxt, ParserController pc,
            PageInfo pi, String prefix, String uriIn,
            TldResourcePath tldResourcePath, ErrorDispatcher err)
            throws JasperException {
        super(prefix, uriIn);

        this.ctxt = ctxt;
        this.parserController = pc;
        this.pi = pi;
        this.err = err;

        if (tldResourcePath == null) {
            // The URI points to the TLD itself or to a JAR file in which the TLD is stored
            tldResourcePath = generateTldResourcePath(uri, ctxt);
        }

        try (Jar jar = tldResourcePath.openJar()) {

            // Add the dependencies on the TLD to the referencing page
            PageInfo pageInfo = ctxt.createCompiler().getPageInfo();
            if (pageInfo != null) {
                // If the TLD is in a JAR, that JAR may not be part of the web
                // application
                String path = tldResourcePath.getWebappPath();
                if (path != null) {
                    // Add TLD (jar==null) / JAR (jar!=null) file to dependency list
                    // 2nd parameter is null since the path is always relative
                    // to the root of the web application even if we are
                    // processing a reference from a tag packaged in a JAR.
                    pageInfo.addDependant(path, ctxt.getLastModified(path, null));
                }
                if (jar != null) {
                    if (path == null) {
                        // JAR not in the web application so add it directly
                        URL jarUrl = jar.getJarFileURL();
                        long lastMod = -1;
                        URLConnection urlConn = null;
                        try {
                            urlConn = jarUrl.openConnection();
                            lastMod = urlConn.getLastModified();
                        } catch (IOException ioe) {
                            throw new JasperException(ioe);
                        } finally {
                            if (urlConn != null) {
                                try {
                                    urlConn.getInputStream().close();
                                } catch (IOException e) {
                                    // Ignore
                                }
                            }
                        }
                        pageInfo.addDependant(jarUrl.toExternalForm(),
                                Long.valueOf(lastMod));
                    }
                    // Add TLD within the JAR to the dependency list
                    String entryName = tldResourcePath.getEntryName();
                    try {
                        pageInfo.addDependant(jar.getURL(entryName),
                                Long.valueOf(jar.getLastModified(entryName)));
                    } catch (IOException ioe) {
                        throw new JasperException(ioe);
                    }
                }
            }

            // Get the representation of the TLD
            TaglibXml taglibXml =
                    ctxt.getOptions().getTldCache().getTaglibXml(tldResourcePath);

            // Populate the TagLibraryInfo attributes
            this.jspversion = taglibXml.getJspVersion();
            this.tlibversion = taglibXml.getTlibVersion();
            this.shortname = taglibXml.getShortName();
            this.urn = taglibXml.getUri();
            this.info = taglibXml.getInfo();

            this.tagLibraryValidator = createValidator(taglibXml.getValidator());

            List<TagInfo> tagInfos = new ArrayList<>();
            for (TagXml tagXml : taglibXml.getTags()) {
                tagInfos.add(createTagInfo(tagXml));
            }

            List<TagFileInfo> tagFileInfos = new ArrayList<>();
            for (TagFileXml tagFileXml : taglibXml.getTagFiles()) {
                tagFileInfos.add(createTagFileInfo(tagFileXml, jar));
            }

            Set<String> names = new HashSet<>();
            List<FunctionInfo> functionInfos = taglibXml.getFunctions();
            // TODO Move this validation to the parsing stage
            for (FunctionInfo functionInfo : functionInfos) {
                String name = functionInfo.getName();
                if (!names.add(name)) {
                    err.jspError("jsp.error.tld.fn.duplicate.name", name, uri);
                }
            }

            if (tlibversion == null) {
                err.jspError("jsp.error.tld.mandatory.element.missing", "tlib-version", uri);
            }
            if (jspversion == null) {
                err.jspError("jsp.error.tld.mandatory.element.missing", "jsp-version", uri);
            }

            this.tags = tagInfos.toArray(new TagInfo[tagInfos.size()]);
            this.tagFiles = tagFileInfos.toArray(new TagFileInfo[tagFileInfos.size()]);
            this.functions = functionInfos.toArray(new FunctionInfo[functionInfos.size()]);
        } catch (IOException ioe) {
            throw new JasperException(ioe);
        }
    }

    @Override
    public TagLibraryInfo[] getTagLibraryInfos() {
        Collection<TagLibraryInfo> coll = pi.getTaglibs();
        return coll.toArray(new TagLibraryInfo[coll.size()]);
    }

    /*
     * @param uri The uri of the TLD
     * @param ctxt The compilation context
     *
     * @return the location of the TLD identified by the uri
     */
    private TldResourcePath generateTldResourcePath(String uri,
            JspCompilationContext ctxt) throws JasperException {

        // TODO: this matches the current implementation but the URL logic looks fishy
        // map URI to location per JSP 7.3.6.2
        if (uri.indexOf(':') != -1) {
            // abs_uri, this was not found in the taglibMap so raise an error
            err.jspError("jsp.error.taglibDirective.absUriCannotBeResolved", uri);
        } else if (uri.charAt(0) != '/') {
            // noroot_rel_uri, resolve against the current JSP page
            uri = ctxt.resolveRelativeUri(uri);
            try {
                // Can't use RequestUtils.normalize since that package is not
                // available to Jasper.
                uri = (new URI(uri)).normalize().toString();
                if (uri.startsWith("../")) {
                    // Trying to go outside context root
                    err.jspError("jsp.error.taglibDirective.uriInvalid", uri);
                }
            } catch (URISyntaxException e) {
                err.jspError("jsp.error.taglibDirective.uriInvalid", uri);
            }
        }

        URL url = null;
        try {
            url = ctxt.getResource(uri);
        } catch (Exception ex) {
            err.jspError("jsp.error.tld.unable_to_get_jar", uri, ex.toString());
        }
        if (uri.endsWith(".jar")) {
            if (url == null) {
                err.jspError("jsp.error.tld.missing_jar", uri);
            }
            return new TldResourcePath(url, uri, "META-INF/taglib.tld");
        } else {
            return new TldResourcePath(url, uri);
        }
    }

    private TagInfo createTagInfo(TagXml tagXml) throws JasperException {

        String teiClassName = tagXml.getTeiClass();
        TagExtraInfo tei = null;
        if (teiClassName != null && !teiClassName.isEmpty()) {
            try {
                Class<?> teiClass = ctxt.getClassLoader().loadClass(teiClassName);
                tei = (TagExtraInfo) teiClass.newInstance();
            } catch (Exception e) {
                err.jspError(e, "jsp.error.teiclass.instantiation", teiClassName);
            }
        }

        List<TagAttributeInfo> attributeInfos = tagXml.getAttributes();
        List<TagVariableInfo> variableInfos = tagXml.getVariables();

        return new TagInfo(tagXml.getName(),
                tagXml.getTagClass(),
                tagXml.getBodyContent(),
                tagXml.getInfo(),
                this,
                tei,
                attributeInfos.toArray(new TagAttributeInfo[attributeInfos.size()]),
                tagXml.getDisplayName(),
                tagXml.getSmallIcon(),
                tagXml.getLargeIcon(),
                variableInfos.toArray(new TagVariableInfo[variableInfos.size()]),
                tagXml.hasDynamicAttributes());
    }

    private TagFileInfo createTagFileInfo(TagFileXml tagFileXml, Jar jar) throws JasperException {

        String name = tagFileXml.getName();
        String path = tagFileXml.getPath();

        if (path == null) {
            // path is required
            err.jspError("jsp.error.tagfile.missingPath");
        } else if (!path.startsWith("/META-INF/tags") && !path.startsWith("/WEB-INF/tags")) {
            err.jspError("jsp.error.tagfile.illegalPath", path);
        }

        TagInfo tagInfo =
                TagFileProcessor.parseTagFileDirectives(parserController, name, path, jar, this);
        return new TagFileInfo(name, path, tagInfo);
    }

    private TagLibraryValidator createValidator(ValidatorXml validatorXml) throws JasperException {

        if (validatorXml == null) {
            return null;
        }

        String validatorClass = validatorXml.getValidatorClass();
        if (validatorClass == null || validatorClass.isEmpty()) {
            return null;
        }

        Map<String,Object> initParams = new Hashtable<>();
        initParams.putAll(validatorXml.getInitParams());

        try {
            Class<?> tlvClass = ctxt.getClassLoader().loadClass(validatorClass);
            TagLibraryValidator tlv = (TagLibraryValidator) tlvClass.newInstance();
            tlv.setInitParameters(initParams);
            return tlv;
        } catch (Exception e) {
            err.jspError(e, "jsp.error.tlvclass.instantiation", validatorClass);
            return null;
        }
    }

    // *********************************************************************
    // Until javax.servlet.jsp.tagext.TagLibraryInfo is fixed

    /**
     * The instance (if any) for the TagLibraryValidator class.
     *
     * @return The TagLibraryValidator instance, if any.
     */
    public TagLibraryValidator getTagLibraryValidator() {
        return tagLibraryValidator;
    }

    /**
     * Translation-time validation of the XML document associated with the JSP
     * page. This is a convenience method on the associated TagLibraryValidator
     * class.
     *
     * @param thePage
     *            The JSP page object
     * @return A string indicating whether the page is valid or not.
     */
    public ValidationMessage[] validate(PageData thePage) {
        TagLibraryValidator tlv = getTagLibraryValidator();
        if (tlv == null)
            return null;

        String uri = getURI();
        if (uri.startsWith("/")) {
            uri = URN_JSPTLD + uri;
        }

        return tlv.validate(getPrefixString(), uri, thePage);
    }

    private TagLibraryValidator tagLibraryValidator;
}
