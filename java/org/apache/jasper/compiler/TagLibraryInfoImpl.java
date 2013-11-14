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
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
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
import javax.servlet.jsp.tagext.VariableInfo;

import org.apache.jasper.JasperException;
import org.apache.jasper.JspCompilationContext;
import org.apache.jasper.xmlparser.TreeNode;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
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

    // Logger
    private final Log log = LogFactory.getLog(TagLibraryInfoImpl.class);

    private final JspCompilationContext ctxt;

    private final PageInfo pi;

    private final ErrorDispatcher err;

    private final ParserController parserController;

    private final void print(String name, String value, PrintWriter w) {
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

        for (int i = 0; i < tags.length; i++)
            out.println(tags[i].toString());

        for (int i = 0; i < tagFiles.length; i++)
            out.println(tagFiles[i].toString());

        for (int i = 0; i < functions.length; i++)
            out.println(functions[i].toString());

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
            // The URI points to the TLD itself or to a JAR file in which the
            // TLD is stored
            tldResourcePath = generateTldResourcePath(uri, ctxt);
        }

        Jar jar;
        try {
            jar = tldResourcePath.getJar();
        } catch (IOException ioe) {
            throw new JasperException(ioe);
        }

        // Add the dependencies on the TLD to the referencing page
        PageInfo pageInfo = ctxt.createCompiler().getPageInfo();
        if (pageInfo != null) {
            String path = tldResourcePath.getWebappPath();
            // Add TLD (jar==null) / JAR (jar!=null) file to dependency list
            pageInfo.addDependant(path, ctxt.getLastModified(path));
            if (jar != null) {
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
            err.jspError("jsp.error.tld.mandatory.element.missing",
                    "tlib-version", uri);
        }
        if (jspversion == null) {
            err.jspError("jsp.error.tld.mandatory.element.missing",
                    "jsp-version", uri);
        }

        this.tags = tagInfos.toArray(new TagInfo[tagInfos.size()]);
        this.tagFiles = tagFileInfos.toArray(new TagFileInfo[tagFileInfos.size()]);
        this.functions = functionInfos.toArray(new FunctionInfo[functionInfos.size()]);
    }

    @Override
    public TagLibraryInfo[] getTagLibraryInfos() {
        Collection<TagLibraryInfo> coll = pi.getTaglibs();
        return coll.toArray(new TagLibraryInfo[0]);
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
        }

        URL url = null;
        try {
            url = ctxt.getResource(uri);
        } catch (Exception ex) {
            err.jspError("jsp.error.tld.unable_to_get_jar", uri, ex
                    .toString());
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
        if (teiClassName != null && !teiClassName.equals("")) {
            try {
                Class<?> teiClass =
                    ctxt.getClassLoader().loadClass(teiClassName);
                tei = (TagExtraInfo) teiClass.newInstance();
            } catch (Exception e) {
                err.jspError(e, "jsp.error.teiclass.instantiation",
                        teiClassName);
            }
        }

        String tagName = tagXml.getName();
        String tagClassName = tagXml.getTagClass();
        String bodycontent = tagXml.getBodyContent();
        String info = tagXml.getInfo();
        String displayName = tagXml.getDisplayName();
        String smallIcon = tagXml.getSmallIcon();
        String largeIcon = tagXml.getLargeIcon();
        boolean dynamicAttributes = tagXml.hasDynamicAttributes();
        List<TagAttributeInfo> attributeInfos = tagXml.getAttributes();
        List<TagVariableInfo> variableInfos = tagXml.getVariables();

        TagInfo taginfo = new TagInfo(tagName, tagClassName, bodycontent, info,
                this, tei,
                attributeInfos.toArray(new TagAttributeInfo[attributeInfos.size()]),
                displayName, smallIcon, largeIcon,
                variableInfos.toArray(new TagVariableInfo[variableInfos.size()]),
                dynamicAttributes);
        return taginfo;
    }

    private TagFileInfo createTagFileInfo(TagFileXml tagFileXml, Jar jar)
            throws JasperException {

        String name = tagFileXml.getName();
        String path = tagFileXml.getPath();

        if (path == null) {
            // path is required
            err.jspError("jsp.error.tagfile.missingPath");
        } else if (path.startsWith("/META-INF/tags")) {
            // Tag file packaged in JAR
            // See https://issues.apache.org/bugzilla/show_bug.cgi?id=46471
            // This needs to be removed once all the broken code that depends on
            // it has been removed
            ctxt.setTagFileJarResource(path, jar);
        } else if (!path.startsWith("/WEB-INF/tags")) {
            err.jspError("jsp.error.tagfile.illegalPath", path);
        }

        TagInfo tagInfo = TagFileProcessor.parseTagFileDirectives(
                parserController, name, path, jar, this);
        return new TagFileInfo(name, path, tagInfo);
    }

    TagAttributeInfo createAttribute(TreeNode elem, String jspVersion) {
        String name = null;
        String type = null;
        String expectedType = null;
        String methodSignature = null;
        boolean required = false, rtexprvalue = false, isFragment = false, deferredValue = false, deferredMethod = false;

        Iterator<TreeNode> list = elem.findChildren();
        while (list.hasNext()) {
            TreeNode element = list.next();
            String tname = element.getName();

            if ("name".equals(tname)) {
                name = element.getBody();
            } else if ("required".equals(tname)) {
                String s = element.getBody();
                if (s != null)
                    required = JspUtil.booleanValue(s);
            } else if ("rtexprvalue".equals(tname)) {
                String s = element.getBody();
                if (s != null)
                    rtexprvalue = JspUtil.booleanValue(s);
            } else if ("type".equals(tname)) {
                type = element.getBody();
                if ("1.2".equals(jspVersion)
                        && (type.equals("Boolean") || type.equals("Byte")
                                || type.equals("Character")
                                || type.equals("Double")
                                || type.equals("Float")
                                || type.equals("Integer")
                                || type.equals("Long") || type.equals("Object")
                                || type.equals("Short") || type
                                .equals("String"))) {
                    type = "java.lang." + type;
                }
            } else if ("fragment".equals(tname)) {
                String s = element.getBody();
                if (s != null) {
                    isFragment = JspUtil.booleanValue(s);
                }
            } else if ("deferred-value".equals(tname)) {
                deferredValue = true;
                type = "javax.el.ValueExpression";
                TreeNode child = element.findChild("type");
                if (child != null) {
                    expectedType = child.getBody();
                    if (expectedType != null) {
                        expectedType = expectedType.trim();
                    }
                } else {
                    expectedType = "java.lang.Object";
                }
            } else if ("deferred-method".equals(tname)) {
                deferredMethod = true;
                type = "javax.el.MethodExpression";
                TreeNode child = element.findChild("method-signature");
                if (child != null) {
                    methodSignature = child.getBody();
                    if (methodSignature != null) {
                        methodSignature = methodSignature.trim();
                    }
                } else {
                    methodSignature = "java.lang.Object method()";
                }
            } else if ("description".equals(tname) || false) {
                // Ignored elements
            } else {
                if (log.isWarnEnabled()) {
                    log.warn(Localizer.getMessage(
                            "jsp.warning.unknown.element.in.attribute", tname));
                }
            }
        }

        if (isFragment) {
            /*
             * According to JSP.C-3 ("TLD Schema Element Structure - tag"),
             * 'type' and 'rtexprvalue' must not be specified if 'fragment' has
             * been specified (this will be enforced by validating parser).
             * Also, if 'fragment' is TRUE, 'type' is fixed at
             * javax.servlet.jsp.tagext.JspFragment, and 'rtexprvalue' is fixed
             * at true. See also JSP.8.5.2.
             */
            type = "javax.servlet.jsp.tagext.JspFragment";
            rtexprvalue = true;
        }

        if (!rtexprvalue && type == null) {
            // According to JSP spec, for static values (those determined at
            // translation time) the type is fixed at java.lang.String.
            type = "java.lang.String";
        }

        return new TagAttributeInfo(name, required, type, rtexprvalue,
                isFragment, null, deferredValue, deferredMethod, expectedType,
                methodSignature);
    }

    TagVariableInfo createVariable(TreeNode elem) {
        String nameGiven = null;
        String nameFromAttribute = null;
        String className = "java.lang.String";
        boolean declare = true;
        int scope = VariableInfo.NESTED;

        Iterator<TreeNode> list = elem.findChildren();
        while (list.hasNext()) {
            TreeNode element = list.next();
            String tname = element.getName();
            if ("name-given".equals(tname))
                nameGiven = element.getBody();
            else if ("name-from-attribute".equals(tname))
                nameFromAttribute = element.getBody();
            else if ("variable-class".equals(tname))
                className = element.getBody();
            else if ("declare".equals(tname)) {
                String s = element.getBody();
                if (s != null)
                    declare = JspUtil.booleanValue(s);
            } else if ("scope".equals(tname)) {
                String s = element.getBody();
                if (s != null) {
                    if ("NESTED".equals(s)) {
                        scope = VariableInfo.NESTED;
                    } else if ("AT_BEGIN".equals(s)) {
                        scope = VariableInfo.AT_BEGIN;
                    } else if ("AT_END".equals(s)) {
                        scope = VariableInfo.AT_END;
                    }
                }
            } else if ("description".equals(tname) || // Ignored elements
            false) {
            } else {
                if (log.isWarnEnabled()) {
                    log.warn(Localizer.getMessage(
                            "jsp.warning.unknown.element.in.variable", tname));
                }
            }
        }
        return new TagVariableInfo(nameGiven, nameFromAttribute, className,
                declare, scope);
    }

    private TagLibraryValidator createValidator(ValidatorXml validatorXml)
            throws JasperException {

        if (validatorXml == null) {
            return null;
        }

        String validatorClass = validatorXml.getValidatorClass();

        Map<String,Object> initParams = new Hashtable<>();
        initParams.putAll(validatorXml.getInitParams());

        TagLibraryValidator tlv = null;
        if (validatorClass != null && !validatorClass.equals("")) {
            try {
                Class<?> tlvClass = ctxt.getClassLoader().loadClass(validatorClass);
                tlv = (TagLibraryValidator) tlvClass.newInstance();
            } catch (Exception e) {
                err.jspError(e, "jsp.error.tlvclass.instantiation",
                        validatorClass);
            }
        }
        if (tlv != null) {
            tlv.setInitParameters(initParams);
        }
        return tlv;
    }

    String[] createInitParam(TreeNode elem) {
        String[] initParam = new String[2];

        Iterator<TreeNode> list = elem.findChildren();
        while (list.hasNext()) {
            TreeNode element = list.next();
            String tname = element.getName();
            if ("param-name".equals(tname)) {
                initParam[0] = element.getBody();
            } else if ("param-value".equals(tname)) {
                initParam[1] = element.getBody();
            } else if ("description".equals(tname)) {
                 // Do nothing
            } else {
                if (log.isWarnEnabled()) {
                    log.warn(Localizer.getMessage(
                            "jsp.warning.unknown.element.in.initParam", tname));
                }
            }
        }
        return initParam;
    }

    FunctionInfo createFunctionInfo(TreeNode elem) {

        String name = null;
        String klass = null;
        String signature = null;

        Iterator<TreeNode> list = elem.findChildren();
        while (list.hasNext()) {
            TreeNode element = list.next();
            String tname = element.getName();

            if ("name".equals(tname)) {
                name = element.getBody();
            } else if ("function-class".equals(tname)) {
                klass = element.getBody();
            } else if ("function-signature".equals(tname)) {
                signature = element.getBody();
            } else if ("display-name".equals(tname) || // Ignored elements
                    "small-icon".equals(tname) || "large-icon".equals(tname)
                    || "description".equals(tname) || "example".equals(tname)) {
            } else {
                if (log.isWarnEnabled()) {
                    log.warn(Localizer.getMessage(
                            "jsp.warning.unknown.element.in.function", tname));
                }
            }
        }

        return new FunctionInfo(name, klass, signature);
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
