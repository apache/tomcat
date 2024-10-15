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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import jakarta.el.MethodExpression;
import jakarta.el.ValueExpression;
import jakarta.servlet.jsp.tagext.JspFragment;
import jakarta.servlet.jsp.tagext.TagAttributeInfo;
import jakarta.servlet.jsp.tagext.TagFileInfo;
import jakarta.servlet.jsp.tagext.TagInfo;
import jakarta.servlet.jsp.tagext.TagLibraryInfo;
import jakarta.servlet.jsp.tagext.TagVariableInfo;
import jakarta.servlet.jsp.tagext.VariableInfo;

import org.apache.jasper.JasperException;
import org.apache.jasper.JspCompilationContext;
import org.apache.jasper.runtime.JspSourceDependent;
import org.apache.jasper.servlet.JspServletWrapper;
import org.apache.tomcat.Jar;
import org.apache.tomcat.util.descriptor.tld.TldResourcePath;

/**
 * 1. Processes and extracts the directive info in a tag file. 2. Compiles and
 * loads tag files used in a JSP file.
 *
 * @author Kin-man Chung
 */

class TagFileProcessor {

    private List<Compiler> tempVector;

    /**
     * A visitor the tag file
     */
    private static class TagFileDirectiveVisitor extends Node.Visitor {

        private static final JspUtil.ValidAttribute[] tagDirectiveAttrs = {
                new JspUtil.ValidAttribute("display-name"),
                new JspUtil.ValidAttribute("body-content"),
                new JspUtil.ValidAttribute("dynamic-attributes"),
                new JspUtil.ValidAttribute("small-icon"),
                new JspUtil.ValidAttribute("large-icon"),
                new JspUtil.ValidAttribute("description"),
                new JspUtil.ValidAttribute("example"),
                new JspUtil.ValidAttribute("pageEncoding"),
                new JspUtil.ValidAttribute("language"),
                new JspUtil.ValidAttribute("import"),
                new JspUtil.ValidAttribute("deferredSyntaxAllowedAsLiteral"), // JSP 2.1
                new JspUtil.ValidAttribute("trimDirectiveWhitespaces"), // JSP 2.1
                new JspUtil.ValidAttribute("isELIgnored"),
                new JspUtil.ValidAttribute("errorOnELNotFound") };

        private static final JspUtil.ValidAttribute[] attributeDirectiveAttrs = {
                new JspUtil.ValidAttribute("name", true),
                new JspUtil.ValidAttribute("required"),
                new JspUtil.ValidAttribute("fragment"),
                new JspUtil.ValidAttribute("rtexprvalue"),
                new JspUtil.ValidAttribute("type"),
                new JspUtil.ValidAttribute("deferredValue"),            // JSP 2.1
                new JspUtil.ValidAttribute("deferredValueType"),        // JSP 2.1
                new JspUtil.ValidAttribute("deferredMethod"),           // JSP 2
                new JspUtil.ValidAttribute("deferredMethodSignature"),  // JSP 21
                new JspUtil.ValidAttribute("description") };

        private static final JspUtil.ValidAttribute[] variableDirectiveAttrs = {
                new JspUtil.ValidAttribute("name-given"),
                new JspUtil.ValidAttribute("name-from-attribute"),
                new JspUtil.ValidAttribute("alias"),
                new JspUtil.ValidAttribute("variable-class"),
                new JspUtil.ValidAttribute("scope"),
                new JspUtil.ValidAttribute("declare"),
                new JspUtil.ValidAttribute("description") };

        private ErrorDispatcher err;

        private TagLibraryInfo tagLibInfo;

        private String name = null;

        private String path = null;

        private String bodycontent = null;

        private String description = null;

        private String displayName = null;

        private String smallIcon = null;

        private String largeIcon = null;

        private String dynamicAttrsMapName;

        private String example = null;

        private List<TagAttributeInfo> attributeList;

        private List<TagVariableInfo> variableList;

        private static final String ATTR_NAME = "the name attribute of the attribute directive";

        private static final String VAR_NAME_GIVEN = "the name-given attribute of the variable directive";

        private static final String VAR_NAME_FROM = "the name-from-attribute attribute of the variable directive";

        private static final String VAR_ALIAS = "the alias attribute of the variable directive";

        private static final String TAG_DYNAMIC = "the dynamic-attributes attribute of the tag directive";

        private Map<String,NameEntry> nameTable = new HashMap<>();

        private Map<String,NameEntry> nameFromTable = new HashMap<>();

        TagFileDirectiveVisitor(Compiler compiler,
                TagLibraryInfo tagLibInfo, String name, String path) {
            err = compiler.getErrorDispatcher();
            this.tagLibInfo = tagLibInfo;
            this.name = name;
            this.path = path;
            attributeList = new ArrayList<>();
            variableList = new ArrayList<>();
        }

        @Override
        public void visit(Node.TagDirective n) throws JasperException {

            JspUtil.checkAttributes("Tag directive", n, tagDirectiveAttrs, err);

            bodycontent = checkConflict(n, bodycontent, "body-content");
            if (bodycontent != null
                    && !bodycontent
                            .equalsIgnoreCase(TagInfo.BODY_CONTENT_EMPTY)
                    && !bodycontent
                            .equalsIgnoreCase(TagInfo.BODY_CONTENT_TAG_DEPENDENT)
                    && !bodycontent
                            .equalsIgnoreCase(TagInfo.BODY_CONTENT_SCRIPTLESS)) {
                err.jspError(n, "jsp.error.tagdirective.badbodycontent",
                        bodycontent);
            }
            dynamicAttrsMapName = checkConflict(n, dynamicAttrsMapName,
                    "dynamic-attributes");
            if (dynamicAttrsMapName != null) {
                checkUniqueName(dynamicAttrsMapName, TAG_DYNAMIC, n);
            }
            smallIcon = checkConflict(n, smallIcon, "small-icon");
            largeIcon = checkConflict(n, largeIcon, "large-icon");
            description = checkConflict(n, description, "description");
            displayName = checkConflict(n, displayName, "display-name");
            example = checkConflict(n, example, "example");
        }

        private String checkConflict(Node n, String oldAttrValue, String attr)
                throws JasperException {

            String result = oldAttrValue;
            String attrValue = n.getAttributeValue(attr);
            if (attrValue != null) {
                if (oldAttrValue != null && !oldAttrValue.equals(attrValue)) {
                    err.jspError(n, "jsp.error.tag.conflict.attr", attr,
                            oldAttrValue, attrValue);
                }
                result = attrValue;
            }
            return result;
        }

        @Override
        public void visit(Node.AttributeDirective n) throws JasperException {

            JspUtil.checkAttributes("Attribute directive", n,
                    attributeDirectiveAttrs, err);

            // JSP 2.1 Table JSP.8-3
            // handle deferredValue and deferredValueType
            boolean deferredValue = false;
            boolean deferredValueSpecified = false;
            String deferredValueString = n.getAttributeValue("deferredValue");
            if (deferredValueString != null) {
                deferredValueSpecified = true;
                deferredValue = JspUtil.booleanValue(deferredValueString);
            }
            String deferredValueType = n.getAttributeValue("deferredValueType");
            if (deferredValueType != null) {
                if (deferredValueSpecified && !deferredValue) {
                    err.jspError(n, "jsp.error.deferredvaluetypewithoutdeferredvalue");
                } else {
                    deferredValue = true;
                }
            } else if (deferredValue) {
                deferredValueType = "java.lang.Object";
            } else {
                deferredValueType = "java.lang.String";
            }

            // JSP 2.1 Table JSP.8-3
            // handle deferredMethod and deferredMethodSignature
            boolean deferredMethod = false;
            boolean deferredMethodSpecified = false;
            String deferredMethodString = n.getAttributeValue("deferredMethod");
            if (deferredMethodString != null) {
                deferredMethodSpecified = true;
                deferredMethod = JspUtil.booleanValue(deferredMethodString);
            }
            String deferredMethodSignature = n
                    .getAttributeValue("deferredMethodSignature");
            if (deferredMethodSignature != null) {
                if (deferredMethodSpecified && !deferredMethod) {
                    err.jspError(n, "jsp.error.deferredmethodsignaturewithoutdeferredmethod");
                } else {
                    deferredMethod = true;
                }
            } else if (deferredMethod) {
                deferredMethodSignature = "void methodname()";
            }

            if (deferredMethod && deferredValue) {
                err.jspError(n, "jsp.error.deferredmethodandvalue");
            }

            String attrName = n.getAttributeValue("name");
            boolean required = JspUtil.booleanValue(n
                    .getAttributeValue("required"));
            boolean rtexprvalue = true;
            String rtexprvalueString = n.getAttributeValue("rtexprvalue");
            if (rtexprvalueString != null) {
                rtexprvalue = JspUtil.booleanValue(rtexprvalueString);
            }
            boolean fragment = JspUtil.booleanValue(n
                    .getAttributeValue("fragment"));
            String type = n.getAttributeValue("type");
            if (fragment) {
                // type is fixed to "JspFragment" and a translation error
                // must occur if specified.
                if (type != null) {
                    err.jspError(n, "jsp.error.fragmentwithtype", JspFragment.class.getName());
                }
                // rtexprvalue is fixed to "true" and a translation error
                // must occur if specified.
                rtexprvalue = true;
                if (rtexprvalueString != null) {
                    err.jspError(n, "jsp.error.frgmentwithrtexprvalue");
                }
            } else {
                if (type == null) {
                    type = "java.lang.String";
                }

                if (deferredValue) {
                    type = ValueExpression.class.getName();
                } else if (deferredMethod) {
                    type = MethodExpression.class.getName();
                }
            }

            if (("2.0".equals(tagLibInfo.getRequiredVersion()) || ("1.2".equals(tagLibInfo.getRequiredVersion())))
                    && (deferredMethodSpecified || deferredMethod
                            || deferredValueSpecified || deferredValue)) {
                err.jspError("jsp.error.invalid.version", path);
            }

            TagAttributeInfo tagAttributeInfo = new TagAttributeInfo(attrName,
                    required, type, rtexprvalue, fragment, null, deferredValue,
                    deferredMethod, deferredValueType, deferredMethodSignature);
            attributeList.add(tagAttributeInfo);
            checkUniqueName(attrName, ATTR_NAME, n, tagAttributeInfo);
        }

        @Override
        public void visit(Node.VariableDirective n) throws JasperException {

            JspUtil.checkAttributes("Variable directive", n,
                    variableDirectiveAttrs, err);

            String nameGiven = n.getAttributeValue("name-given");
            String nameFromAttribute = n
                    .getAttributeValue("name-from-attribute");
            if (nameGiven == null && nameFromAttribute == null) {
                err.jspError("jsp.error.variable.either.name");
            }

            if (nameGiven != null && nameFromAttribute != null) {
                err.jspError("jsp.error.variable.both.name");
            }

            String alias = n.getAttributeValue("alias");
            if (nameFromAttribute != null && alias == null
                    || nameFromAttribute == null && alias != null) {
                err.jspError("jsp.error.variable.alias");
            }

            String className = n.getAttributeValue("variable-class");
            if (className == null) {
                className = "java.lang.String";
            }

            String declareStr = n.getAttributeValue("declare");
            boolean declare = true;
            if (declareStr != null) {
                declare = JspUtil.booleanValue(declareStr);
            }

            int scope = VariableInfo.NESTED;
            String scopeStr = n.getAttributeValue("scope");
            if (scopeStr != null) {
                if ("NESTED".equals(scopeStr)) {
                    // Already the default
                } else if ("AT_BEGIN".equals(scopeStr)) {
                    scope = VariableInfo.AT_BEGIN;
                } else if ("AT_END".equals(scopeStr)) {
                    scope = VariableInfo.AT_END;
                }
            }

            if (nameFromAttribute != null) {
                /*
                 * An alias has been specified. We use 'nameGiven' to hold the
                 * value of the alias, and 'nameFromAttribute' to hold the name
                 * of the attribute whose value (at invocation-time) denotes the
                 * name of the variable that is being aliased
                 */
                nameGiven = alias;
                checkUniqueName(nameFromAttribute, VAR_NAME_FROM, n);
                checkUniqueName(alias, VAR_ALIAS, n);
            } else {
                // name-given specified
                checkUniqueName(nameGiven, VAR_NAME_GIVEN, n);
            }

            variableList.add(new TagVariableInfo(nameGiven, nameFromAttribute, className, declare, scope));
        }

        public TagInfo getTagInfo(String packageName) throws JasperException {

            if (name == null) {
                // XXX Get it from tag file name
            }

            if (bodycontent == null) {
                bodycontent = TagInfo.BODY_CONTENT_SCRIPTLESS;
            }

            String tagClassName = JspUtil.getTagHandlerClassName(
                    path, packageName, tagLibInfo.getReliableURN(), err);

            TagVariableInfo[] tagVariableInfos = variableList.toArray(new TagVariableInfo[0]);
            TagAttributeInfo[] tagAttributeInfo = attributeList.toArray(new TagAttributeInfo[0]);

            return new JasperTagInfo(name, tagClassName, bodycontent,
                    description, tagLibInfo, null, tagAttributeInfo,
                    displayName, smallIcon, largeIcon, tagVariableInfos,
                    dynamicAttrsMapName);
        }

        static class NameEntry {
            private String type;

            private Node node;

            private TagAttributeInfo attr;

            NameEntry(String type, Node node, TagAttributeInfo attr) {
                this.type = type;
                this.node = node;
                this.attr = attr;
            }

            String getType() {
                return type;
            }

            Node getNode() {
                return node;
            }

            TagAttributeInfo getTagAttributeInfo() {
                return attr;
            }
        }

        /**
         * Reports a translation error if names specified in attributes of
         * directives are not unique in this translation unit.
         *
         * The value of the following attributes must be unique. 1. 'name'
         * attribute of an attribute directive 2. 'name-given' attribute of a
         * variable directive 3. 'alias' attribute of variable directive 4.
         * 'dynamic-attributes' of a tag directive except that
         * 'dynamic-attributes' can (and must) have the same value when it
         * appears in multiple tag directives.
         *
         * Also, 'name-from' attribute of a variable directive cannot have the
         * same value as that from another variable directive.
         */
        private void checkUniqueName(String name, String type, Node n)
                throws JasperException {
            checkUniqueName(name, type, n, null);
        }

        private void checkUniqueName(String name, String type, Node n,
                TagAttributeInfo attr) throws JasperException {

            Map<String, NameEntry> table = (VAR_NAME_FROM.equals(type)) ? nameFromTable : nameTable;
            NameEntry nameEntry = table.get(name);
            if (nameEntry != null) {
                if (!TAG_DYNAMIC.equals(type) ||
                        !TAG_DYNAMIC.equals(nameEntry.getType())) {
                    int line = nameEntry.getNode().getStart().getLineNumber();
                    err.jspError(n, "jsp.error.tagfile.nameNotUnique", type,
                            nameEntry.getType(), Integer.toString(line));
                }
            } else {
                table.put(name, new NameEntry(type, n, attr));
            }
        }

        /**
         * Perform miscellaneous checks after the nodes are visited.
         */
        void postCheck() throws JasperException {
            // Check that var.name-from-attributes has valid values.
            for (Entry<String, NameEntry> entry : nameFromTable.entrySet()) {
                String key = entry.getKey();
                NameEntry nameEntry = nameTable.get(key);
                NameEntry nameFromEntry = entry.getValue();
                Node nameFromNode = nameFromEntry.getNode();
                if (nameEntry == null) {
                    err.jspError(nameFromNode,
                            "jsp.error.tagfile.nameFrom.noAttribute", key);
                } else {
                    Node node = nameEntry.getNode();
                    TagAttributeInfo tagAttr = nameEntry.getTagAttributeInfo();
                    if (!"java.lang.String".equals(tagAttr.getTypeName())
                            || !tagAttr.isRequired()
                            || tagAttr.canBeRequestTime()) {
                        err.jspError(nameFromNode,
                                "jsp.error.tagfile.nameFrom.badAttribute",
                                key, Integer.toString(node.getStart()
                                        .getLineNumber()));
                    }
                }
            }
        }
    }

    /**
     * Parses the tag file, and collects information on the directives included
     * in it. The method is used to obtain the info on the tag file, when the
     * handler that it represents is referenced. The tag file is not compiled
     * here.
     *
     * @param pc
     *            the current ParserController used in this compilation
     * @param name
     *            the tag name as specified in the TLD
     * @param path
     *            the path for the tagfile
     * @param jar
     *            the Jar resource containing the tag file
     * @param tagLibInfo
     *            the TagLibraryInfo object associated with this TagInfo
     * @return a TagInfo object assembled from the directives in the tag file.
     *
     * @throws JasperException If an error occurs during parsing
     */
    @SuppressWarnings("null") // page can't be null
    public static TagInfo parseTagFileDirectives(ParserController pc,
            String name, String path, Jar jar, TagLibraryInfo tagLibInfo)
            throws JasperException {


        ErrorDispatcher err = pc.getCompiler().getErrorDispatcher();

        Node.Nodes page = null;
        try {
            page = pc.parseTagFileDirectives(path, jar);
        } catch (IOException e) {
            err.jspError("jsp.error.file.not.found", path);
        }

        TagFileDirectiveVisitor tagFileVisitor = new TagFileDirectiveVisitor(pc
                .getCompiler(), tagLibInfo, name, path);
        page.visit(tagFileVisitor);
        tagFileVisitor.postCheck();

        return tagFileVisitor.getTagInfo(pc.getJspCompilationContext().getOptions().getGeneratedTagFilePackageName());
    }

    /**
     * Compiles and loads a tagfile.
     */
    private Class<?> loadTagFile(Compiler compiler, String tagFilePath,
            TagInfo tagInfo, PageInfo parentPageInfo) throws JasperException {

        Jar tagJar = null;
        Jar tagJarOriginal = null;
        try {
            if (tagFilePath.startsWith("/META-INF/")) {
                try {
                    tagJar = compiler.getCompilationContext().getTldResourcePath(
                                tagInfo.getTagLibrary().getURI()).openJar();
                } catch (IOException ioe) {
                    throw new JasperException(ioe);
                }
            }
            String wrapperUri;
            if (tagJar == null) {
                wrapperUri = tagFilePath;
            } else {
                wrapperUri = tagJar.getURL(tagFilePath);
            }

            JspCompilationContext ctxt = compiler.getCompilationContext();
            JspRuntimeContext rctxt = ctxt.getRuntimeContext();

            synchronized (rctxt) {
                JspServletWrapper wrapper = null;
                try {
                    wrapper = rctxt.getWrapper(wrapperUri);
                    if (wrapper == null) {
                        wrapper = new JspServletWrapper(ctxt.getServletContext(), ctxt
                                .getOptions(), tagFilePath, tagInfo, ctxt
                                .getRuntimeContext(), tagJar);
                        // Use same classloader and classpath for compiling tag files
                        wrapper.getJspEngineContext().setClassLoader(
                                ctxt.getClassLoader());
                        wrapper.getJspEngineContext().setClassPath(ctxt.getClassPath());
                        rctxt.addWrapper(wrapperUri, wrapper);
                    } else {
                        // Make sure that JspCompilationContext gets the latest TagInfo
                        // for the tag file. TagInfo instance was created the last
                        // time the tag file was scanned for directives, and the tag
                        // file may have been modified since then.
                        wrapper.getJspEngineContext().setTagInfo(tagInfo);
                        // This compilation needs to use the current tagJar.
                        // Compilation may be nested in which case the old tagJar
                        // will need to be restored
                        tagJarOriginal = wrapper.getJspEngineContext().getTagFileJar();
                        wrapper.getJspEngineContext().setTagFileJar(tagJar);
                    }

                    Class<?> tagClazz;
                    int tripCount = wrapper.incTripCount();
                    try {
                        if (tripCount > 0) {
                            // When tripCount is greater than zero, a circular
                            // dependency exists. The circularly dependent tag
                            // file is compiled in prototype mode, to avoid infinite
                            // recursion.

                            JspServletWrapper tempWrapper = new JspServletWrapper(ctxt
                                    .getServletContext(), ctxt.getOptions(),
                                    tagFilePath, tagInfo, ctxt.getRuntimeContext(),
                                    tagJar);
                            // Use same classloader and classpath for compiling tag files
                            tempWrapper.getJspEngineContext().setClassLoader(
                                    ctxt.getClassLoader());
                            tempWrapper.getJspEngineContext().setClassPath(ctxt.getClassPath());
                            tagClazz = tempWrapper.loadTagFilePrototype();
                            tempVector.add(tempWrapper.getJspEngineContext()
                                    .getCompiler());
                        } else {
                            tagClazz = wrapper.loadTagFile();
                        }
                    } finally {
                        wrapper.decTripCount();
                    }

                    // Add the dependents for this tag file to its parent's
                    // Dependent list. The only reliable dependency information
                    // can only be obtained from the tag instance.
                    try {
                        Object tagIns = tagClazz.getConstructor().newInstance();
                        if (tagIns instanceof JspSourceDependent) {
                            for (Entry<String, Long> entry : ((JspSourceDependent)
                                    tagIns).getDependants().entrySet()) {
                                parentPageInfo.addDependant(entry.getKey(),
                                        entry.getValue());
                            }
                        }
                    } catch (RuntimeException | ReflectiveOperationException e) {
                        // ignore errors
                    }

                    return tagClazz;
                } finally {
                    if (wrapper != null && tagJarOriginal != null) {
                        wrapper.getJspEngineContext().setTagFileJar(tagJarOriginal);
                    }
                }
            }
        } finally {
            if (tagJar != null) {
                tagJar.close();
            }
        }
    }

    /*
     * Visitor which scans the page and looks for tag handlers that are tag
     * files, compiling (if necessary) and loading them.
     */
    private class TagFileLoaderVisitor extends Node.Visitor {

        private Compiler compiler;

        private PageInfo pageInfo;

        TagFileLoaderVisitor(Compiler compiler) {

            this.compiler = compiler;
            this.pageInfo = compiler.getPageInfo();
        }

        @Override
        public void visit(Node.CustomTag n) throws JasperException {
            TagFileInfo tagFileInfo = n.getTagFileInfo();
            if (tagFileInfo != null) {
                String tagFilePath = tagFileInfo.getPath();
                if (tagFilePath.startsWith("/META-INF/")) {
                    // For tags in JARs, add the TLD and the tag as a dependency
                    TldResourcePath tldResourcePath =
                        compiler.getCompilationContext().getTldResourcePath(
                            tagFileInfo.getTagInfo().getTagLibrary().getURI());

                    try (Jar jar = tldResourcePath.openJar()) {

                        if (jar != null) {
                            // Add TLD
                            pageInfo.addDependant(jar.getURL(tldResourcePath.getEntryName()),
                                                  Long.valueOf(jar.getLastModified(tldResourcePath.getEntryName())));
                            // Add Tag
                            pageInfo.addDependant(jar.getURL(tagFilePath.substring(1)),
                                                  Long.valueOf(jar.getLastModified(tagFilePath.substring(1))));
                        } else {
                            pageInfo.addDependant(tagFilePath,
                                                  compiler.getCompilationContext().getLastModified(tagFilePath));
                        }
                    } catch (IOException ioe) {
                        throw new JasperException(ioe);
                    }
                } else {
                    pageInfo.addDependant(tagFilePath,
                            compiler.getCompilationContext().getLastModified(tagFilePath));
                }
                Class<?> c = loadTagFile(compiler, tagFilePath, n.getTagInfo(),
                        pageInfo);
                n.setTagHandlerClass(c);
            }
            visitBody(n);
        }
    }

    /**
     * Implements a phase of the translation that compiles (if necessary) the
     * tag files used in a JSP files. The directives in the tag files are
     * assumed to have been processed and encapsulated as TagFileInfo in the
     * CustomTag nodes.
     *
     * @param compiler Compiler to use to compile tag files
     * @param page     The page from to scan for tag files to compile
     *
     * @throws JasperException If an error occurs during the scan or compilation
     */
    public void loadTagFiles(Compiler compiler, Node.Nodes page)
            throws JasperException {

        tempVector = new ArrayList<>();
        page.visit(new TagFileLoaderVisitor(compiler));
    }

    /**
     * Removed the java and class files for the tag prototype generated from the
     * current compilation.
     *
     * @param classFileName
     *            If non-null, remove only the class file with with this name.
     */
    public void removeProtoTypeFiles(String classFileName) {
        for (Compiler c : tempVector) {
            if (classFileName == null) {
                c.removeGeneratedClassFiles();
            } else if (classFileName.equals(c.getCompilationContext().getClassFileName())) {
                c.removeGeneratedClassFiles();
                tempVector.remove(c);
                return;
            }
        }
    }
}
