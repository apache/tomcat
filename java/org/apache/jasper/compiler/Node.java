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

import java.util.ArrayList;
import java.util.List;

import javax.el.ELContext;
import javax.el.ELException;
import javax.el.ExpressionFactory;
import javax.servlet.jsp.tagext.BodyTag;
import javax.servlet.jsp.tagext.DynamicAttributes;
import javax.servlet.jsp.tagext.IterationTag;
import javax.servlet.jsp.tagext.JspIdConsumer;
import javax.servlet.jsp.tagext.SimpleTag;
import javax.servlet.jsp.tagext.TagAttributeInfo;
import javax.servlet.jsp.tagext.TagData;
import javax.servlet.jsp.tagext.TagFileInfo;
import javax.servlet.jsp.tagext.TagInfo;
import javax.servlet.jsp.tagext.TagVariableInfo;
import javax.servlet.jsp.tagext.TryCatchFinally;
import javax.servlet.jsp.tagext.VariableInfo;

import org.apache.jasper.Constants;
import org.apache.jasper.JasperException;
import org.apache.jasper.compiler.tagplugin.TagPluginContext;
import org.xml.sax.Attributes;

/**
 * An internal data representation of a JSP page or a JSP document (XML). Also included here is a visitor class for
 * traversing nodes.
 */
public abstract class Node implements TagConstants {

    private static final VariableInfo[] ZERO_VARIABLE_INFO = {};

    /**
     * The attributes for this node.
     */
    protected Attributes attrs;

    /**
     * The xmlns attributes that represent tag libraries (only in XML syntax).
     */
    protected Attributes taglibAttrs;

    /**
     * The xmlns attributes that do not represent tag libraries (only in XML syntax).
     */
    protected Attributes nonTaglibXmlnsAttrs;

    /**
     * The body of this node.
     */
    protected Nodes body;

    /**
     * The text content associated with this node.
     */
    protected String text;

    /**
     * The location in the source file where this node begins.
     */
    protected Mark startMark;

    /**
     * The starting line number in the generated Java code.
     */
    protected int beginJavaLine;

    /**
     * The ending line number in the generated Java code.
     */
    protected int endJavaLine;

    /**
     * The parent node in the parse tree.
     */
    protected Node parent;

    /**
     * Cached named attribute nodes for performance.
     */
    protected Nodes namedAttributeNodes;

    /**
     * The qualified name of this node.
     */
    protected String qName;

    /**
     * The local name of this node.
     */
    protected String localName;

    /**
     * The name of the inner class to which the codes for this node and its body are generated. For instance, for
     * &lt;jsp:body> in foo.jsp, this is "foo_jspHelper". This is primarily used for communicating such info from
     * Generator to Smap generator.
     */
    protected String innerClassName;


    /**
     * Zero-arg Constructor.
     */
    Node() {
    }

    /**
     * Constructor.
     *
     * @param start  The location of the jsp page
     * @param parent The enclosing node
     */
    Node(Mark start, Node parent) {
        this.startMark = start;
        addToParent(parent);
    }

    /**
     * Constructor for Nodes parsed from standard syntax.
     *
     * @param qName     The action's qualified name
     * @param localName The action's local name
     * @param attrs     The attributes for this node
     * @param start     The location of the jsp page
     * @param parent    The enclosing node
     */
    Node(String qName, String localName, Attributes attrs, Mark start, Node parent) {
        this.qName = qName;
        this.localName = localName;
        this.attrs = attrs;
        this.startMark = start;
        addToParent(parent);
    }

    /**
     * Constructor for Nodes parsed from XML syntax.
     *
     * @param qName               The action's qualified name
     * @param localName           The action's local name
     * @param attrs               The action's attributes whose name does not start with xmlns
     * @param nonTaglibXmlnsAttrs The action's xmlns attributes that do not represent tag libraries
     * @param taglibAttrs         The action's xmlns attributes that represent tag libraries
     * @param start               The location of the jsp page
     * @param parent              The enclosing node
     */
    Node(String qName, String localName, Attributes attrs, Attributes nonTaglibXmlnsAttrs, Attributes taglibAttrs,
            Mark start, Node parent) {
        this.qName = qName;
        this.localName = localName;
        this.attrs = attrs;
        this.nonTaglibXmlnsAttrs = nonTaglibXmlnsAttrs;
        this.taglibAttrs = taglibAttrs;
        this.startMark = start;
        addToParent(parent);
    }

    /*
     * Constructor.
     *
     * @param qName The action's qualified name @param localName The action's local name @param text The text associated
     * with this node @param start The location of the jsp page @param parent The enclosing node
     */
    Node(String qName, String localName, String text, Mark start, Node parent) {
        this.qName = qName;
        this.localName = localName;
        this.text = text;
        this.startMark = start;
        addToParent(parent);
    }

    /**
     * Returns the qualified name of this node.
     *
     * @return The qualified name
     */
    public String getQName() {
        return this.qName;
    }

    /**
     * Returns the local name of this node.
     *
     * @return The local name
     */
    public String getLocalName() {
        return this.localName;
    }

    /**
     * Gets this Node's attributes.
     * <p>
     * In the case of a Node parsed from standard syntax, this method returns all the Node's attributes.
     * <p>
     * In the case of a Node parsed from XML syntax, this method returns only those attributes whose name does not start
     * with xmlns.
     *
     * @return The attributes
     */
    public Attributes getAttributes() {
        return this.attrs;
    }

    /**
     * Gets this Node's xmlns attributes that represent tag libraries (only meaningful for Nodes parsed from XML syntax).
     *
     * @return The taglib attributes
     */
    public Attributes getTaglibAttributes() {
        return this.taglibAttrs;
    }

    /**
     * Gets this Node's xmlns attributes that do not represent tag libraries (only meaningful for Nodes parsed from XML
     * syntax).
     *
     * @return The non-taglib xmlns attributes
     */
    public Attributes getNonTaglibXmlnsAttributes() {
        return this.nonTaglibXmlnsAttrs;
    }

    /**
     * Sets the attributes for this node.
     *
     * @param attrs The attributes to set
     */
    public void setAttributes(Attributes attrs) {
        this.attrs = attrs;
    }

    /**
     * Returns the value of the attribute with the given name.
     *
     * @param name The name of the attribute
     * @return The attribute value, or {@code null} if not found
     */
    public String getAttributeValue(String name) {
        return (attrs == null) ? null : attrs.getValue(name);
    }

    /**
     * Get the attribute that is non request time expression, either from the attribute of the node, or from a
     * jsp:attribute
     *
     * @param name The name of the attribute
     *
     * @return The attribute value
     */
    public String getTextAttribute(String name) {

        String attr = getAttributeValue(name);
        if (attr != null) {
            return attr;
        }

        NamedAttribute namedAttribute = getNamedAttributeNode(name);
        if (namedAttribute == null) {
            return null;
        }

        return namedAttribute.getText();
    }

    /**
     * Searches all sub-nodes of this node for jsp:attribute standard actions with the given name.
     * <p>
     * This should always be called and only be called for nodes that accept dynamic runtime attribute expressions.
     *
     * @param name The name of the attribute
     *
     * @return the NamedAttribute node of the matching named attribute, nor null if no such node is found.
     */
    public NamedAttribute getNamedAttributeNode(String name) {
        NamedAttribute result = null;

        // Look for the attribute in NamedAttribute children
        Nodes nodes = getNamedAttributeNodes();
        int numChildNodes = nodes.size();
        for (int i = 0; i < numChildNodes; i++) {
            NamedAttribute na = (NamedAttribute) nodes.getNode(i);
            boolean found;
            int index = name.indexOf(':');
            if (index != -1) {
                // qualified name
                found = na.getName().equals(name);
            } else {
                found = na.getLocalName().equals(name);
            }
            if (found) {
                result = na;
                break;
            }
        }

        return result;
    }

    /**
     * Searches all subnodes of this node for jsp:attribute standard actions, and returns that set of nodes as a
     * Node.Nodes object.
     *
     * @return Possibly empty Node.Nodes object containing any jsp:attribute subnodes of this Node
     */
    public Node.Nodes getNamedAttributeNodes() {

        if (namedAttributeNodes != null) {
            return namedAttributeNodes;
        }

        Node.Nodes result = new Node.Nodes();

        // Look for the attribute in NamedAttribute children
        Nodes nodes = getBody();
        if (nodes != null) {
            int numChildNodes = nodes.size();
            for (int i = 0; i < numChildNodes; i++) {
                Node n = nodes.getNode(i);
                if (n instanceof NamedAttribute) {
                    result.add(n);
                } else if (!(n instanceof Comment)) {
                    // Nothing can come before jsp:attribute, and only
                    // jsp:body can come after it.
                    break;
                }
            }
        }

        namedAttributeNodes = result;
        return result;
    }

    /**
     * Returns the body of this node.
     *
     * @return The body nodes
     */
    public Nodes getBody() {
        return body;
    }

    /**
     * Sets the body of this node.
     *
     * @param body The body nodes
     */
    public void setBody(Nodes body) {
        this.body = body;
    }

    /**
     * Returns the text content of this node.
     *
     * @return The text content
     */
    public String getText() {
        return text;
    }

    /**
     * Returns the start mark (location) of this node.
     *
     * @return The start mark
     */
    public Mark getStart() {
        return startMark;
    }

    /**
     * Returns the parent node.
     *
     * @return The parent node
     */
    public Node getParent() {
        return parent;
    }

    /**
     * Returns the starting line number in the generated Java code.
     *
     * @return The starting Java line number
     */
    public int getBeginJavaLine() {
        return beginJavaLine;
    }

    /**
     * Sets the starting line number in the generated Java code.
     *
     * @param begin The starting Java line number
     */
    public void setBeginJavaLine(int begin) {
        beginJavaLine = begin;
    }

    /**
     * Returns the ending line number in the generated Java code.
     *
     * @return The ending Java line number
     */
    public int getEndJavaLine() {
        return endJavaLine;
    }

    /**
     * Sets the ending line number in the generated Java code.
     *
     * @param end The ending Java line number
     */
    public void setEndJavaLine(int end) {
        endJavaLine = end;
    }

    /**
     * Returns the root node of the parse tree.
     *
     * @return The root node
     */
    public Node.Root getRoot() {
        Node n = this;
        while (!(n instanceof Node.Root)) {
            n = n.getParent();
        }
        return (Node.Root) n;
    }

    /**
     * Returns the name of the inner class for this node.
     *
     * @return The inner class name
     */
    public String getInnerClassName() {
        return innerClassName;
    }

    /**
     * Sets the name of the inner class for this node.
     *
     * @param icn The inner class name
     */
    public void setInnerClassName(String icn) {
        innerClassName = icn;
    }

    /**
     * Selects and invokes a method in the visitor class based on the node type. This is abstract and should be
     * overridden by the extending classes.
     *
     * @param v The visitor class
     */
    abstract void accept(Visitor v) throws JasperException;

    // *********************************************************************
    // Private utility methods

    /*
     * Adds this Node to the body of the given parent.
     */
    private void addToParent(Node parent) {
        if (parent != null) {
            this.parent = parent;
            Nodes parentBody = parent.getBody();
            if (parentBody == null) {
                parentBody = new Nodes();
                parent.setBody(parentBody);
            }
            parentBody.add(this);
        }
    }


    /**
     * Represents the root of a Jsp page or Jsp document
     */
    public static class Root extends Node {

        private final Root parentRoot;

        private final boolean isXmlSyntax;

        // Source encoding of the page containing this Root
        private String pageEnc;

        // Page encoding specified in JSP config element
        private String jspConfigPageEnc;

        /*
         * Flag indicating if the default page encoding is being used (only applicable with standard syntax).
         *
         * True if the page does not provide a page directive with a 'contentType' attribute (or the 'contentType'
         * attribute doesn't have a CHARSET value), the page does not provide a page directive with a 'pageEncoding'
         * attribute, and there is no JSP configuration element page-encoding whose URL pattern matches the page.
         */
        private boolean isDefaultPageEncoding;

        /*
         * Indicates whether an encoding has been explicitly specified in the page's XML prolog (only used for pages in
         * XML syntax). This information is used to decide whether a translation error must be reported for encoding
         * conflicts.
         */
        private boolean isEncodingSpecifiedInProlog;

        /*
         * Indicates whether an encoding has been explicitly specified in the page's dom.
         */
        private boolean isBomPresent;

        /*
         * Sequence number for temporary variables.
         */
        private int tempSequenceNumber = 0;

        /*
         * Constructor.
         */
        Root(Mark start, Node parent, boolean isXmlSyntax) {
            super(start, parent);
            this.isXmlSyntax = isXmlSyntax;
            this.qName = JSP_ROOT_ACTION;
            this.localName = ROOT_ACTION;

            // Figure out and set the parent root
            Node r = parent;
            while ((r != null) && !(r instanceof Node.Root)) {
                r = r.getParent();
            }
            parentRoot = (Node.Root) r;
        }

        @Override
        public void accept(Visitor v) throws JasperException {
            v.visit(this);
        }

        /**
         * Returns whether this JSP page uses XML syntax.
         *
         * @return true if XML syntax is used
         */
        public boolean isXmlSyntax() {
            return isXmlSyntax;
        }

        /**
         * Sets the encoding specified in the JSP config element whose URL pattern matches the page containing this
         * Root.
         *
         * @param enc The page encoding from the JSP config
         */
        public void setJspConfigPageEncoding(String enc) {
            jspConfigPageEnc = enc;
        }

        /**
         * Gets the encoding specified in the JSP config element whose URL pattern matches the page containing this
         * Root.
         *
         * @return The page encoding from the JSP config
         */
        public String getJspConfigPageEncoding() {
            return jspConfigPageEnc;
        }

        /**
         * Sets the encoding of the page.
         *
         * @param enc The page encoding
         */
        public void setPageEncoding(String enc) {
            pageEnc = enc;
        }

        /**
         * Returns the encoding of the page.
         *
         * @return The page encoding
         */
        public String getPageEncoding() {
            return pageEnc;
        }

        /**
         * Sets whether the page encoding is the default encoding.
         *
         * @param isDefault true if the page encoding is the default
         */
        public void setIsDefaultPageEncoding(boolean isDefault) {
            isDefaultPageEncoding = isDefault;
        }

        /**
         * Returns whether the page encoding is the default encoding.
         *
         * @return true if the page encoding is the default
         */
        public boolean isDefaultPageEncoding() {
            return isDefaultPageEncoding;
        }

        /**
         * Sets whether the encoding is specified in the prolog.
         *
         * @param isSpecified true if the encoding is specified in the prolog
         */
        public void setIsEncodingSpecifiedInProlog(boolean isSpecified) {
            isEncodingSpecifiedInProlog = isSpecified;
        }

        /**
         * Returns whether the encoding is specified in the prolog.
         *
         * @return true if the encoding is specified in the prolog
         */
        public boolean isEncodingSpecifiedInProlog() {
            return isEncodingSpecifiedInProlog;
        }

        /**
         * Sets whether a BOM (Byte Order Mark) is present.
         *
         * @param isBom true if a BOM is present
         */
        public void setIsBomPresent(boolean isBom) {
            isBomPresent = isBom;
        }

        /**
         * Returns whether a BOM (Byte Order Mark) is present.
         *
         * @return true if a BOM is present
         */
        public boolean isBomPresent() {
            return isBomPresent;
        }

        /**
         * Generates a new temporary variable name.
         *
         * @return The name to use for the temporary variable
         */
        public String nextTemporaryVariableName() {
            if (parentRoot == null) {
                return Constants.TEMP_VARIABLE_NAME_PREFIX + (tempSequenceNumber++);
            } else {
                return parentRoot.nextTemporaryVariableName();
            }

        }
    }

    /**
     * Represents the root of a Jsp document (XML syntax)
     */
    public static class JspRoot extends Node {

        JspRoot(String qName, Attributes attrs, Attributes nonTaglibXmlnsAttrs, Attributes taglibAttrs, Mark start,
                Node parent) {
            super(qName, ROOT_ACTION, attrs, nonTaglibXmlnsAttrs, taglibAttrs, start, parent);
        }

        @Override
        public void accept(Visitor v) throws JasperException {
            v.visit(this);
        }
    }

    /**
     * Represents a page directive
     */
    public static class PageDirective extends Node {

        private final List<String> imports;

        PageDirective(Attributes attrs, Mark start, Node parent) {
            this(JSP_PAGE_DIRECTIVE_ACTION, attrs, null, null, start, parent);
        }

        PageDirective(String qName, Attributes attrs, Attributes nonTaglibXmlnsAttrs, Attributes taglibAttrs,
                Mark start, Node parent) {
            super(qName, PAGE_DIRECTIVE_ACTION, attrs, nonTaglibXmlnsAttrs, taglibAttrs, start, parent);
            imports = new ArrayList<>();
        }

        @Override
        public void accept(Visitor v) throws JasperException {
            v.visit(this);
        }

        /**
         * Parses the comma-separated list of class or package names in the given attribute value and adds each
         * component to this PageDirective's vector of imported classes and packages.
         *
         * @param value A comma-separated string of imports.
         */
        public void addImport(String value) {
            int start = 0;
            int index;
            while ((index = value.indexOf(',', start)) != -1) {
                imports.add(validateImport(value.substring(start, index)));
                start = index + 1;
            }
            if (start == 0) {
                // No comma found
                imports.add(validateImport(value));
            } else {
                imports.add(validateImport(value.substring(start)));
            }
        }

        /**
         * Returns the list of imported classes and packages.
         *
         * @return The list of imports
         */
        public List<String> getImports() {
            return imports;
        }

        /**
         * Just need enough validation to make sure nothing strange is going on. The compiler will validate this
         * thoroughly when it tries to compile the resulting .java file.
         */
        private String validateImport(String importEntry) {
            // This should either be a fully-qualified class name or a package
            // name with a wildcard
            if (importEntry.indexOf(';') > -1) {
                throw new IllegalArgumentException(Localizer.getMessage("jsp.error.page.invalid.import"));
            }
            return importEntry.trim();
        }
    }

    /**
     * Represents an include directive
     */
    public static class IncludeDirective extends Node {

        IncludeDirective(Attributes attrs, Mark start, Node parent) {
            this(JSP_INCLUDE_DIRECTIVE_ACTION, attrs, null, null, start, parent);
        }

        IncludeDirective(String qName, Attributes attrs, Attributes nonTaglibXmlnsAttrs, Attributes taglibAttrs,
                Mark start, Node parent) {
            super(qName, INCLUDE_DIRECTIVE_ACTION, attrs, nonTaglibXmlnsAttrs, taglibAttrs, start, parent);
        }

        @Override
        public void accept(Visitor v) throws JasperException {
            v.visit(this);
        }
    }

    /**
     * Represents a custom taglib directive
     */
    public static class TaglibDirective extends Node {

        TaglibDirective(Attributes attrs, Mark start, Node parent) {
            super(JSP_TAGLIB_DIRECTIVE_ACTION, TAGLIB_DIRECTIVE_ACTION, attrs, start, parent);
        }

        @Override
        public void accept(Visitor v) throws JasperException {
            v.visit(this);
        }
    }

    /**
     * Represents a tag directive
     */
    public static class TagDirective extends Node {
        private final List<String> imports;

        TagDirective(Attributes attrs, Mark start, Node parent) {
            this(JSP_TAG_DIRECTIVE_ACTION, attrs, null, null, start, parent);
        }

        TagDirective(String qName, Attributes attrs, Attributes nonTaglibXmlnsAttrs, Attributes taglibAttrs, Mark start,
                Node parent) {
            super(qName, TAG_DIRECTIVE_ACTION, attrs, nonTaglibXmlnsAttrs, taglibAttrs, start, parent);
            imports = new ArrayList<>();
        }

        @Override
        public void accept(Visitor v) throws JasperException {
            v.visit(this);
        }

        /**
         * Parses the comma-separated list of class or package names in the given attribute value and adds each
         * component to this PageDirective's vector of imported classes and packages.
         *
         * @param value A comma-separated string of imports.
         */
        public void addImport(String value) {
            int start = 0;
            int index;
            while ((index = value.indexOf(',', start)) != -1) {
                imports.add(value.substring(start, index).trim());
                start = index + 1;
            }
            if (start == 0) {
                // No comma found
                imports.add(value.trim());
            } else {
                imports.add(value.substring(start).trim());
            }
        }

        /**
         * Returns the list of imported classes and packages for this tag directive.
         *
         * @return The list of imports
         */
        public List<String> getImports() {
            return imports;
        }
    }

    /**
     * Represents an attribute directive
     */
    public static class AttributeDirective extends Node {

        AttributeDirective(Attributes attrs, Mark start, Node parent) {
            this(JSP_ATTRIBUTE_DIRECTIVE_ACTION, attrs, null, null, start, parent);
        }

        AttributeDirective(String qName, Attributes attrs, Attributes nonTaglibXmlnsAttrs, Attributes taglibAttrs,
                Mark start, Node parent) {
            super(qName, ATTRIBUTE_DIRECTIVE_ACTION, attrs, nonTaglibXmlnsAttrs, taglibAttrs, start, parent);
        }

        @Override
        public void accept(Visitor v) throws JasperException {
            v.visit(this);
        }
    }

    /**
     * Represents a variable directive
     */
    public static class VariableDirective extends Node {

        VariableDirective(Attributes attrs, Mark start, Node parent) {
            this(JSP_VARIABLE_DIRECTIVE_ACTION, attrs, null, null, start, parent);
        }

        VariableDirective(String qName, Attributes attrs, Attributes nonTaglibXmlnsAttrs, Attributes taglibAttrs,
                Mark start, Node parent) {
            super(qName, VARIABLE_DIRECTIVE_ACTION, attrs, nonTaglibXmlnsAttrs, taglibAttrs, start, parent);
        }

        @Override
        public void accept(Visitor v) throws JasperException {
            v.visit(this);
        }
    }

    /**
     * Represents a &lt;jsp:invoke> tag file action
     */
    public static class InvokeAction extends Node {

        InvokeAction(Attributes attrs, Mark start, Node parent) {
            this(JSP_INVOKE_ACTION, attrs, null, null, start, parent);
        }

        InvokeAction(String qName, Attributes attrs, Attributes nonTaglibXmlnsAttrs, Attributes taglibAttrs, Mark start,
                Node parent) {
            super(qName, INVOKE_ACTION, attrs, nonTaglibXmlnsAttrs, taglibAttrs, start, parent);
        }

        @Override
        public void accept(Visitor v) throws JasperException {
            v.visit(this);
        }
    }

    /**
     * Represents a &lt;jsp:doBody> tag file action
     */
    public static class DoBodyAction extends Node {

        DoBodyAction(Attributes attrs, Mark start, Node parent) {
            this(JSP_DOBODY_ACTION, attrs, null, null, start, parent);
        }

        DoBodyAction(String qName, Attributes attrs, Attributes nonTaglibXmlnsAttrs, Attributes taglibAttrs, Mark start,
                Node parent) {
            super(qName, DOBODY_ACTION, attrs, nonTaglibXmlnsAttrs, taglibAttrs, start, parent);
        }

        @Override
        public void accept(Visitor v) throws JasperException {
            v.visit(this);
        }
    }

    /**
     * Represents a Jsp comment Comments are kept for completeness.
     */
    public static class Comment extends Node {

        Comment(String text, Mark start, Node parent) {
            super(null, null, text, start, parent);
        }

        @Override
        public void accept(Visitor v) throws JasperException {
            v.visit(this);
        }
    }

    /**
     * Represents an expression, declaration, or scriptlet
     */
    public abstract static class ScriptingElement extends Node {

        ScriptingElement(String qName, String localName, String text, Mark start, Node parent) {
            super(qName, localName, text, start, parent);
        }

        ScriptingElement(String qName, String localName, Attributes nonTaglibXmlnsAttrs, Attributes taglibAttrs,
                Mark start, Node parent) {
            super(qName, localName, null, nonTaglibXmlnsAttrs, taglibAttrs, start, parent);
        }

        /**
         * When this node was created from a JSP page in JSP syntax, its text was stored as a String in the "text"
         * field, whereas when this node was created from a JSP document, its text was stored as one or more
         * TemplateText nodes in its body. This method handles either case.
         *
         * @return The text string
         */
        @Override
        public String getText() {
            String ret = text;
            if (ret == null) {
                if (body != null) {
                    StringBuilder buf = new StringBuilder();
                    for (int i = 0; i < body.size(); i++) {
                        buf.append(body.getNode(i).getText());
                    }
                    ret = buf.toString();
                } else {
                    // Nulls cause NPEs further down the line
                    ret = "";
                }
            }
            return ret;
        }

        /**
         * For the same reason as above, the source line information in the contained TemplateText node should be used.
         */
        @Override
        public Mark getStart() {
            if (text == null && body != null && body.size() > 0) {
                return body.getNode(0).getStart();
            } else {
                return super.getStart();
            }
        }
    }

    /**
     * Represents a declaration
     */
    public static class Declaration extends ScriptingElement {

        Declaration(String text, Mark start, Node parent) {
            super(JSP_DECLARATION_ACTION, DECLARATION_ACTION, text, start, parent);
        }

        Declaration(String qName, Attributes nonTaglibXmlnsAttrs, Attributes taglibAttrs, Mark start, Node parent) {
            super(qName, DECLARATION_ACTION, nonTaglibXmlnsAttrs, taglibAttrs, start, parent);
        }

        @Override
        public void accept(Visitor v) throws JasperException {
            v.visit(this);
        }
    }

    /**
     * Represents an expression. Expressions in attributes are embedded in the attribute string and not here.
     */
    public static class Expression extends ScriptingElement {

        Expression(String text, Mark start, Node parent) {
            super(JSP_EXPRESSION_ACTION, EXPRESSION_ACTION, text, start, parent);
        }

        Expression(String qName, Attributes nonTaglibXmlnsAttrs, Attributes taglibAttrs, Mark start, Node parent) {
            super(qName, EXPRESSION_ACTION, nonTaglibXmlnsAttrs, taglibAttrs, start, parent);
        }

        @Override
        public void accept(Visitor v) throws JasperException {
            v.visit(this);
        }
    }

    /**
     * Represents a scriptlet
     */
    public static class Scriptlet extends ScriptingElement {

        Scriptlet(String text, Mark start, Node parent) {
            super(JSP_SCRIPTLET_ACTION, SCRIPTLET_ACTION, text, start, parent);
        }

        Scriptlet(String qName, Attributes nonTaglibXmlnsAttrs, Attributes taglibAttrs, Mark start, Node parent) {
            super(qName, SCRIPTLET_ACTION, nonTaglibXmlnsAttrs, taglibAttrs, start, parent);
        }

        @Override
        public void accept(Visitor v) throws JasperException {
            v.visit(this);
        }
    }

    /**
     * Represents an EL expression. Expressions in attributes are embedded in the attribute string and not here.
     */
    public static class ELExpression extends Node {

        private ELNode.Nodes el;

        private final char type;

        ELExpression(char type, String text, Mark start, Node parent) {
            super(null, null, text, start, parent);
            this.type = type;
        }

        @Override
        public void accept(Visitor v) throws JasperException {
            v.visit(this);
        }

        /**
         * Sets the EL expression nodes.
         *
         * @param el The EL nodes
         */
        public void setEL(ELNode.Nodes el) {
            this.el = el;
        }

        /**
         * Returns the EL expression nodes.
         *
         * @return The EL nodes
         */
        public ELNode.Nodes getEL() {
            return el;
        }

        /**
         * Returns the type character of this EL expression.
         *
         * @return The type character
         */
        public char getType() {
            return this.type;
        }
    }

    /**
     * Represents a param action
     */
    public static class ParamAction extends Node {

        private JspAttribute value;

        ParamAction(Attributes attrs, Mark start, Node parent) {
            this(JSP_PARAM_ACTION, attrs, null, null, start, parent);
        }

        ParamAction(String qName, Attributes attrs, Attributes nonTaglibXmlnsAttrs, Attributes taglibAttrs, Mark start,
                Node parent) {
            super(qName, PARAM_ACTION, attrs, nonTaglibXmlnsAttrs, taglibAttrs, start, parent);
        }

        @Override
        public void accept(Visitor v) throws JasperException {
            v.visit(this);
        }

        /**
         * Sets the value attribute for this param action.
         *
         * @param value The value to set
         */
        public void setValue(JspAttribute value) {
            this.value = value;
        }

        /**
         * Returns the value attribute of this param action.
         *
         * @return The value attribute
         */
        public JspAttribute getValue() {
            return value;
        }
    }

    /**
     * Represents a params action
     */
    public static class ParamsAction extends Node {

        ParamsAction(Mark start, Node parent) {
            this(JSP_PARAMS_ACTION, null, null, start, parent);
        }

        ParamsAction(String qName, Attributes nonTaglibXmlnsAttrs, Attributes taglibAttrs, Mark start, Node parent) {
            super(qName, PARAMS_ACTION, null, nonTaglibXmlnsAttrs, taglibAttrs, start, parent);
        }

        @Override
        public void accept(Visitor v) throws JasperException {
            v.visit(this);
        }
    }

    /**
     * Represents a fallback action
     */
    public static class FallBackAction extends Node {

        FallBackAction(Mark start, Node parent) {
            this(JSP_FALLBACK_ACTION, null, null, start, parent);
        }

        FallBackAction(String qName, Attributes nonTaglibXmlnsAttrs, Attributes taglibAttrs, Mark start, Node parent) {
            super(qName, FALLBACK_ACTION, null, nonTaglibXmlnsAttrs, taglibAttrs, start, parent);
        }

        @Override
        public void accept(Visitor v) throws JasperException {
            v.visit(this);
        }
    }

    /**
     * Represents an include action
     */
    public static class IncludeAction extends Node {

        private JspAttribute page;

        IncludeAction(Attributes attrs, Mark start, Node parent) {
            this(JSP_INCLUDE_ACTION, attrs, null, null, start, parent);
        }

        IncludeAction(String qName, Attributes attrs, Attributes nonTaglibXmlnsAttrs, Attributes taglibAttrs,
                Mark start, Node parent) {
            super(qName, INCLUDE_ACTION, attrs, nonTaglibXmlnsAttrs, taglibAttrs, start, parent);
        }

        @Override
        public void accept(Visitor v) throws JasperException {
            v.visit(this);
        }

        /**
         * Sets the page attribute for this include action.
         *
         * @param page The page attribute
         */
        public void setPage(JspAttribute page) {
            this.page = page;
        }

        /**
         * Returns the page attribute for this include action.
         *
         * @return The page attribute
         */
        public JspAttribute getPage() {
            return page;
        }
    }

    /**
     * Represents a forward action
     */
    public static class ForwardAction extends Node {

        private JspAttribute page;

        ForwardAction(Attributes attrs, Mark start, Node parent) {
            this(JSP_FORWARD_ACTION, attrs, null, null, start, parent);
        }

        ForwardAction(String qName, Attributes attrs, Attributes nonTaglibXmlnsAttrs, Attributes taglibAttrs,
                Mark start, Node parent) {
            super(qName, FORWARD_ACTION, attrs, nonTaglibXmlnsAttrs, taglibAttrs, start, parent);
        }

        @Override
        public void accept(Visitor v) throws JasperException {
            v.visit(this);
        }

        /**
         * Sets the page attribute for this forward action.
         *
         * @param page The page attribute
         */
        public void setPage(JspAttribute page) {
            this.page = page;
        }

        /**
         * Returns the page attribute for this forward action.
         *
         * @return The page attribute
         */
        public JspAttribute getPage() {
            return page;
        }
    }

    /**
     * Represents a getProperty action
     */
    public static class GetProperty extends Node {

        GetProperty(Attributes attrs, Mark start, Node parent) {
            this(JSP_GET_PROPERTY_ACTION, attrs, null, null, start, parent);
        }

        GetProperty(String qName, Attributes attrs, Attributes nonTaglibXmlnsAttrs, Attributes taglibAttrs, Mark start,
                Node parent) {
            super(qName, GET_PROPERTY_ACTION, attrs, nonTaglibXmlnsAttrs, taglibAttrs, start, parent);
        }

        @Override
        public void accept(Visitor v) throws JasperException {
            v.visit(this);
        }
    }

    /**
     * Represents a setProperty action
     */
    public static class SetProperty extends Node {

        private JspAttribute value;

        SetProperty(Attributes attrs, Mark start, Node parent) {
            this(JSP_SET_PROPERTY_ACTION, attrs, null, null, start, parent);
        }

        SetProperty(String qName, Attributes attrs, Attributes nonTaglibXmlnsAttrs, Attributes taglibAttrs, Mark start,
                Node parent) {
            super(qName, SET_PROPERTY_ACTION, attrs, nonTaglibXmlnsAttrs, taglibAttrs, start, parent);
        }

        @Override
        public void accept(Visitor v) throws JasperException {
            v.visit(this);
        }

        /**
         * Sets the value attribute for this setProperty action.
         *
         * @param value The value to set
         */
        public void setValue(JspAttribute value) {
            this.value = value;
        }

        /**
         * Returns the value attribute of this setProperty action.
         *
         * @return The value attribute
         */
        public JspAttribute getValue() {
            return value;
        }
    }

    /**
     * Represents a useBean action
     */
    public static class UseBean extends Node {

        private JspAttribute beanName;

        UseBean(Attributes attrs, Mark start, Node parent) {
            this(JSP_USE_BEAN_ACTION, attrs, null, null, start, parent);
        }

        UseBean(String qName, Attributes attrs, Attributes nonTaglibXmlnsAttrs, Attributes taglibAttrs, Mark start,
                Node parent) {
            super(qName, USE_BEAN_ACTION, attrs, nonTaglibXmlnsAttrs, taglibAttrs, start, parent);
        }

        @Override
        public void accept(Visitor v) throws JasperException {
            v.visit(this);
        }

        /**
         * Sets the bean name attribute for this useBean action.
         *
         * @param beanName The bean name to set
         */
        public void setBeanName(JspAttribute beanName) {
            this.beanName = beanName;
        }

        /**
         * Returns the bean name attribute of this useBean action.
         *
         * @return The bean name attribute
         */
        public JspAttribute getBeanName() {
            return beanName;
        }
    }

    /**
     * Represents a plugin action
     */
    public static class PlugIn extends Node {

        private JspAttribute width;

        private JspAttribute height;

        PlugIn(Attributes attrs, Mark start, Node parent) {
            this(JSP_PLUGIN_ACTION, attrs, null, null, start, parent);
        }

        PlugIn(String qName, Attributes attrs, Attributes nonTaglibXmlnsAttrs, Attributes taglibAttrs, Mark start,
                Node parent) {
            super(qName, PLUGIN_ACTION, attrs, nonTaglibXmlnsAttrs, taglibAttrs, start, parent);
        }

        @Override
        public void accept(Visitor v) throws JasperException {
            v.visit(this);
        }

        public void setHeight(JspAttribute height) {
            this.height = height;
        }

        public void setWidth(JspAttribute width) {
            this.width = width;
        }

        public JspAttribute getHeight() {
            return height;
        }

        public JspAttribute getWidth() {
            return width;
        }
    }

    /**
     * Represents an uninterpreted tag, from a Jsp document
     */
    public static class UninterpretedTag extends Node {

        private JspAttribute[] jspAttrs;

        UninterpretedTag(String qName, String localName, Attributes attrs, Attributes nonTaglibXmlnsAttrs,
                Attributes taglibAttrs, Mark start, Node parent) {
            super(qName, localName, attrs, nonTaglibXmlnsAttrs, taglibAttrs, start, parent);
        }

        @Override
        public void accept(Visitor v) throws JasperException {
            v.visit(this);
        }

        /**
         * Sets the JSP attributes for this uninterpreted tag.
         *
         * @param jspAttrs The JSP attributes to set
         */
        public void setJspAttributes(JspAttribute[] jspAttrs) {
            this.jspAttrs = jspAttrs;
        }

        /**
         * Returns the JSP attributes of this uninterpreted tag.
         *
         * @return The JSP attributes
         */
        public JspAttribute[] getJspAttributes() {
            return jspAttrs;
        }
    }

    /**
     * Represents a &lt;jsp:element>.
     */
    public static class JspElement extends Node {

        private JspAttribute[] jspAttrs;

        private JspAttribute nameAttr;

        JspElement(Attributes attrs, Mark start, Node parent) {
            this(JSP_ELEMENT_ACTION, attrs, null, null, start, parent);
        }

        JspElement(String qName, Attributes attrs, Attributes nonTaglibXmlnsAttrs, Attributes taglibAttrs, Mark start,
                Node parent) {
            super(qName, ELEMENT_ACTION, attrs, nonTaglibXmlnsAttrs, taglibAttrs, start, parent);
        }

        @Override
        public void accept(Visitor v) throws JasperException {
            v.visit(this);
        }

        /**
         * Sets the JSP attributes for this element.
         *
         * @param jspAttrs The JSP attributes to set
         */
        public void setJspAttributes(JspAttribute[] jspAttrs) {
            this.jspAttrs = jspAttrs;
        }

        /**
         * Returns the JSP attributes of this element.
         *
         * @return The JSP attributes
         */
        public JspAttribute[] getJspAttributes() {
            return jspAttrs;
        }

        /**
         * Sets the XML-style 'name' attribute.
         *
         * @param nameAttr The name attribute to set
         */
        public void setNameAttribute(JspAttribute nameAttr) {
            this.nameAttr = nameAttr;
        }

        /**
         * Gets the XML-style 'name' attribute.
         *
         * @return The name attribute
         */
        public JspAttribute getNameAttribute() {
            return this.nameAttr;
        }
    }

    /**
     * Represents a &lt;jsp:output>.
     */
    public static class JspOutput extends Node {

        JspOutput(String qName, Attributes attrs, Attributes nonTaglibXmlnsAttrs, Attributes taglibAttrs, Mark start,
                Node parent) {
            super(qName, OUTPUT_ACTION, attrs, nonTaglibXmlnsAttrs, taglibAttrs, start, parent);
        }

        @Override
        public void accept(Visitor v) throws JasperException {
            v.visit(this);
        }
    }

    /**
     * Collected information about child elements. Used by nodes like CustomTag, JspBody, and NamedAttribute. The
     * information is set in the Collector.
     */
    public static class ChildInfo {

        /**
         * True if the tag and its body contain no scripting elements.
         */
        private boolean scriptless;

        /**
         * True if the tag body contains a useBean action.
         */
        private boolean hasUseBean;

        /**
         * True if the tag body contains an include action.
         */
        private boolean hasIncludeAction;

        /**
         * True if the tag body contains a param action.
         */
        private boolean hasParamAction;

        /**
         * True if the tag body contains a setProperty action.
         */
        private boolean hasSetProperty;

        /**
         * True if the tag body contains scripting variables.
         */
        private boolean hasScriptingVars;

        /**
         * Constructs a new ChildInfo instance.
         */
        public ChildInfo() {
        }

        /**
         * Sets whether the tag and its body contain no scripting elements.
         *
         * @param s True if scriptless
         */
        public void setScriptless(boolean s) {
            scriptless = s;
        }

        /**
         * Returns whether the tag and its body contain no scripting elements.
         *
         * @return True if scriptless
         */
        public boolean isScriptless() {
            return scriptless;
        }

        /**
         * Sets whether the tag body contains a useBean action.
         *
         * @param u True if it has useBean
         */
        public void setHasUseBean(boolean u) {
            hasUseBean = u;
        }

        /**
         * Returns whether the tag body contains a useBean action.
         *
         * @return True if it has useBean
         */
        public boolean hasUseBean() {
            return hasUseBean;
        }

        /**
         * Sets whether the tag body contains an include action.
         *
         * @param i True if it has include action
         */
        public void setHasIncludeAction(boolean i) {
            hasIncludeAction = i;
        }

        /**
         * Returns whether the tag body contains an include action.
         *
         * @return True if it has include action
         */
        public boolean hasIncludeAction() {
            return hasIncludeAction;
        }

        /**
         * Sets whether the tag body contains a param action.
         *
         * @param i True if it has param action
         */
        public void setHasParamAction(boolean i) {
            hasParamAction = i;
        }

        /**
         * Returns whether the tag body contains a param action.
         *
         * @return True if it has param action
         */
        public boolean hasParamAction() {
            return hasParamAction;
        }

        /**
         * Sets whether the tag body contains a setProperty action.
         *
         * @param s True if it has setProperty
         */
        public void setHasSetProperty(boolean s) {
            hasSetProperty = s;
        }

        /**
         * Returns whether the tag body contains a setProperty action.
         *
         * @return True if it has setProperty
         */
        public boolean hasSetProperty() {
            return hasSetProperty;
        }

        /**
         * Sets whether the tag body contains scripting variables.
         *
         * @param s True if it has scripting variables
         */
        public void setHasScriptingVars(boolean s) {
            hasScriptingVars = s;
        }

        /**
         * Returns whether the tag body contains scripting variables.
         *
         * @return True if it has scripting variables
         */
        public boolean hasScriptingVars() {
            return hasScriptingVars;
        }
    }


    /**
     * Base class for nodes that collect child element information.
     */
    public abstract static class ChildInfoBase extends Node {

        private final ChildInfo childInfo = new ChildInfo();

        /**
         * Constructs a ChildInfoBase node.
         *
         * @param qName The qualified name
         * @param localName The local name
         * @param attrs The attributes
         * @param nonTaglibXmlnsAttrs The non-taglib xmlns attributes
         * @param taglibAttrs The taglib xmlns attributes
         * @param start The start mark
         * @param parent The parent node
         */
        ChildInfoBase(String qName, String localName, Attributes attrs, Attributes nonTaglibXmlnsAttrs,
                Attributes taglibAttrs, Mark start, Node parent) {
            super(qName, localName, attrs, nonTaglibXmlnsAttrs, taglibAttrs, start, parent);
        }

        /**
         * Returns the collected child element information.
         *
         * @return The child info
         */
        public ChildInfo getChildInfo() {
            return childInfo;
        }
    }


    /**
     * Represents a custom tag
     */
    public static class CustomTag extends ChildInfoBase {

        private final String uri;

        private final String prefix;

        private JspAttribute[] jspAttrs;

        private TagData tagData;

        private String tagHandlerPoolName;

        private final TagInfo tagInfo;

        private final TagFileInfo tagFileInfo;

        private Class<?> tagHandlerClass;

        private VariableInfo[] varInfos;

        private final int customNestingLevel;

        private final boolean implementsIterationTag;

        private final boolean implementsBodyTag;

        private final boolean implementsTryCatchFinally;

        private final boolean implementsJspIdConsumer;

        private final boolean implementsSimpleTag;

        private final boolean implementsDynamicAttributes;

        private List<Object> atBeginScriptingVars;

        private List<Object> atEndScriptingVars;

        private List<Object> nestedScriptingVars;

        private Node.CustomTag customTagParent;

        private Integer numCount;

        private boolean useTagPlugin;

        private TagPluginContext tagPluginContext;

        /**
         * The following two fields are used for holding the Java scriptlets that the tag plugins may generate.
         * Meaningful only if useTagPlugin is true; Could move them into TagPluginContextImpl, but we'll need to cast
         * tagPluginContext to TagPluginContextImpl all the time...
         */
        private Nodes atSTag;

        private Nodes atETag;

        /*
         * Constructor for custom action implemented by tag handler.
         */
        CustomTag(String qName, String prefix, String localName, String uri, Attributes attrs, Mark start, Node parent,
                TagInfo tagInfo, Class<?> tagHandlerClass) {
            this(qName, prefix, localName, uri, attrs, null, null, start, parent, tagInfo, tagHandlerClass);
        }

        /*
         * Constructor for custom action implemented by tag handler.
         */
        CustomTag(String qName, String prefix, String localName, String uri, Attributes attrs,
                Attributes nonTaglibXmlnsAttrs, Attributes taglibAttrs, Mark start, Node parent, TagInfo tagInfo,
                Class<?> tagHandlerClass) {
            super(qName, localName, attrs, nonTaglibXmlnsAttrs, taglibAttrs, start, parent);

            this.uri = uri;
            this.prefix = prefix;
            this.tagInfo = tagInfo;
            this.tagFileInfo = null;
            this.tagHandlerClass = tagHandlerClass;
            this.customNestingLevel = makeCustomNestingLevel();

            this.implementsIterationTag = IterationTag.class.isAssignableFrom(tagHandlerClass);
            this.implementsBodyTag = BodyTag.class.isAssignableFrom(tagHandlerClass);
            this.implementsTryCatchFinally = TryCatchFinally.class.isAssignableFrom(tagHandlerClass);
            this.implementsSimpleTag = SimpleTag.class.isAssignableFrom(tagHandlerClass);
            this.implementsDynamicAttributes = DynamicAttributes.class.isAssignableFrom(tagHandlerClass);
            this.implementsJspIdConsumer = JspIdConsumer.class.isAssignableFrom(tagHandlerClass);
        }

        /*
         * Constructor for custom action implemented by tag file.
         */
        CustomTag(String qName, String prefix, String localName, String uri, Attributes attrs, Mark start, Node parent,
                TagFileInfo tagFileInfo) {
            this(qName, prefix, localName, uri, attrs, null, null, start, parent, tagFileInfo);
        }

        /*
         * Constructor for custom action implemented by tag file.
         */
        CustomTag(String qName, String prefix, String localName, String uri, Attributes attrs,
                Attributes nonTaglibXmlnsAttrs, Attributes taglibAttrs, Mark start, Node parent,
                TagFileInfo tagFileInfo) {

            super(qName, localName, attrs, nonTaglibXmlnsAttrs, taglibAttrs, start, parent);

            this.uri = uri;
            this.prefix = prefix;
            this.tagFileInfo = tagFileInfo;
            this.tagInfo = tagFileInfo.getTagInfo();
            this.customNestingLevel = makeCustomNestingLevel();

            this.implementsIterationTag = false;
            this.implementsBodyTag = false;
            this.implementsTryCatchFinally = false;
            this.implementsSimpleTag = true;
            this.implementsJspIdConsumer = false;
            this.implementsDynamicAttributes = tagInfo.hasDynamicAttributes();
        }

        @Override
        public void accept(Visitor v) throws JasperException {
            v.visit(this);
        }

        /**
         * Returns the URI namespace that this custom action belongs to.
         *
         * @return The URI namespace
         */
        public String getURI() {
            return this.uri;
        }

        /**
         * Returns the tag prefix.
         *
         * @return The tag prefix
         */
        public String getPrefix() {
            return prefix;
        }

        /**
         * Sets the JSP attributes for this custom tag.
         *
         * @param jspAttrs The JSP attributes
         */
        public void setJspAttributes(JspAttribute[] jspAttrs) {
            this.jspAttrs = jspAttrs;
        }

        /**
         * Returns the JSP attributes for this custom tag.
         *
         * @return The JSP attributes
         */
        public JspAttribute[] getJspAttributes() {
            return jspAttrs;
        }

        /**
         * Sets the tag data for this custom tag.
         *
         * @param tagData The tag data
         */
        public void setTagData(TagData tagData) {
            this.tagData = tagData;
            this.varInfos = tagInfo.getVariableInfo(tagData);
            if (this.varInfos == null) {
                this.varInfos = ZERO_VARIABLE_INFO;
            }
        }

        /**
         * Returns the tag data for this custom tag.
         *
         * @return The tag data
         */
        public TagData getTagData() {
            return tagData;
        }

        /**
         * Sets the tag handler pool name.
         *
         * @param s The tag handler pool name
         */
        public void setTagHandlerPoolName(String s) {
            tagHandlerPoolName = s;
        }

        /**
         * Returns the tag handler pool name.
         *
         * @return The tag handler pool name
         */
        public String getTagHandlerPoolName() {
            return tagHandlerPoolName;
        }

        /**
         * Returns the tag information for this custom tag.
         *
         * @return The tag info
         */
        public TagInfo getTagInfo() {
            return tagInfo;
        }

        /**
         * Returns the tag file information for this custom tag.
         *
         * @return The tag file info, or {@code null} if not a tag file
         */
        public TagFileInfo getTagFileInfo() {
            return tagFileInfo;
        }

        /**
         * Returns whether this custom action is supported by a tag file.
         *
         * @return {@code true} if this custom action is supported by a tag file, {@code false} otherwise
         */
        public boolean isTagFile() {
            return tagFileInfo != null;
        }

        /**
         * Returns the tag handler class.
         *
         * @return The tag handler class
         */
        public Class<?> getTagHandlerClass() {
            return tagHandlerClass;
        }

        /**
         * Sets the tag handler class.
         *
         * @param hc The tag handler class
         */
        public void setTagHandlerClass(Class<?> hc) {
            tagHandlerClass = hc;
        }

        /**
         * Returns whether the tag handler implements IterationTag.
         *
         * @return {@code true} if the tag handler implements IterationTag
         */
        public boolean implementsIterationTag() {
            return implementsIterationTag;
        }

        /**
         * Returns whether the tag handler implements BodyTag.
         *
         * @return {@code true} if the tag handler implements BodyTag
         */
        public boolean implementsBodyTag() {
            return implementsBodyTag;
        }

        /**
         * Returns whether the tag handler implements TryCatchFinally.
         *
         * @return {@code true} if the tag handler implements TryCatchFinally
         */
        public boolean implementsTryCatchFinally() {
            return implementsTryCatchFinally;
        }

        /**
         * Returns whether the tag handler implements JspIdConsumer.
         *
         * @return {@code true} if the tag handler implements JspIdConsumer
         */
        public boolean implementsJspIdConsumer() {
            return implementsJspIdConsumer;
        }

        /**
         * Returns whether the tag handler implements SimpleTag.
         *
         * @return {@code true} if the tag handler implements SimpleTag
         */
        public boolean implementsSimpleTag() {
            return implementsSimpleTag;
        }

        /**
         * Returns whether the tag handler implements DynamicAttributes.
         *
         * @return {@code true} if the tag handler implements DynamicAttributes
         */
        public boolean implementsDynamicAttributes() {
            return implementsDynamicAttributes;
        }

        /**
         * Returns the tag variable information from the tag info.
         *
         * @return The tag variable info array
         */
        public TagVariableInfo[] getTagVariableInfos() {
            return tagInfo.getTagVariableInfos();
        }

        /**
         * Returns the variable information computed from the tag data.
         *
         * @return The variable info array
         */
        public VariableInfo[] getVariableInfos() {
            return varInfos;
        }

        /**
         * Sets the parent custom tag.
         *
         * @param n The parent custom tag
         */
        public void setCustomTagParent(Node.CustomTag n) {
            this.customTagParent = n;
        }

        /**
         * Returns the parent custom tag.
         *
         * @return The parent custom tag
         */
        public Node.CustomTag getCustomTagParent() {
            return this.customTagParent;
        }

        /**
         * Sets the number count for this custom tag.
         *
         * @param count The number count
         */
        public void setNumCount(Integer count) {
            this.numCount = count;
        }

        /**
         * Returns the number count for this custom tag.
         *
         * @return The number count
         */
        public Integer getNumCount() {
            return this.numCount;
        }

        /**
         * Sets the scripting variables for the given scope.
         *
         * @param vec The list of scripting variables
         * @param scope The variable scope (AT_BEGIN, AT_END, or NESTED)
         * @throws IllegalArgumentException if the scope is invalid
         */
        public void setScriptingVars(List<Object> vec, int scope) {
            switch (scope) {
                case VariableInfo.AT_BEGIN:
                    this.atBeginScriptingVars = vec;
                    break;
                case VariableInfo.AT_END:
                    this.atEndScriptingVars = vec;
                    break;
                case VariableInfo.NESTED:
                    this.nestedScriptingVars = vec;
                    break;
                default:
                    throw new IllegalArgumentException(
                            Localizer.getMessage("jsp.error.page.invalid.varscope", Integer.valueOf(scope)));
            }
        }

        /**
         * Gets the scripting variables for the given scope that need to be declared.
         *
         * @param scope The variable scope (AT_BEGIN, AT_END, or NESTED)
         * @return The list of scripting variables for the given scope
         * @throws IllegalArgumentException if the scope is invalid
         */
        public List<Object> getScriptingVars(int scope) {
            List<Object> vec = null;

            switch (scope) {
                case VariableInfo.AT_BEGIN:
                    vec = this.atBeginScriptingVars;
                    break;
                case VariableInfo.AT_END:
                    vec = this.atEndScriptingVars;
                    break;
                case VariableInfo.NESTED:
                    vec = this.nestedScriptingVars;
                    break;
                default:
                    throw new IllegalArgumentException(
                            Localizer.getMessage("jsp.error.page.invalid.varscope", Integer.valueOf(scope)));
            }

            return vec;
        }

        /**
         * Gets this custom tag's custom nesting level, which is given as the number of times this custom tag is nested
         * inside itself.
         *
         * @return The custom nesting level
         */
        public int getCustomNestingLevel() {
            return customNestingLevel;
        }

        /**
         * Checks to see if the attribute of the given name is of type JspFragment.
         *
         * @param name The attribute to check
         *
         * @return {@code true} if it is a JspFragment
         */
        public boolean checkIfAttributeIsJspFragment(String name) {
            boolean result = false;

            TagAttributeInfo[] attributes = tagInfo.getAttributes();
            for (TagAttributeInfo attribute : attributes) {
                if (attribute.getName().equals(name) && attribute.isFragment()) {
                    result = true;
                    break;
                }
            }

            return result;
        }

        /**
         * Sets whether to use a tag plugin for this custom tag.
         *
         * @param use {@code true} to use a tag plugin
         */
        public void setUseTagPlugin(boolean use) {
            useTagPlugin = use;
        }

        /**
         * Returns whether a tag plugin is used for this custom tag.
         *
         * @return {@code true} if a tag plugin is used
         */
        public boolean useTagPlugin() {
            return useTagPlugin;
        }

        /**
         * Sets the tag plugin context for this custom tag.
         *
         * @param tagPluginContext The tag plugin context
         */
        public void setTagPluginContext(TagPluginContext tagPluginContext) {
            this.tagPluginContext = tagPluginContext;
        }

        /**
         * Returns the tag plugin context for this custom tag.
         *
         * @return The tag plugin context
         */
        public TagPluginContext getTagPluginContext() {
            return tagPluginContext;
        }

        /**
         * Sets the nodes generated at the start tag by a tag plugin.
         *
         * @param sTag The start tag nodes
         */
        public void setAtSTag(Nodes sTag) {
            atSTag = sTag;
        }

        /**
         * Returns the nodes generated at the start tag by a tag plugin.
         *
         * @return The start tag nodes
         */
        public Nodes getAtSTag() {
            return atSTag;
        }

        /**
         * Sets the nodes generated at the end tag by a tag plugin.
         *
         * @param eTag The end tag nodes
         */
        public void setAtETag(Nodes eTag) {
            atETag = eTag;
        }

        /**
         * Returns the nodes generated at the end tag by a tag plugin.
         *
         * @return The end tag nodes
         */
        public Nodes getAtETag() {
            return atETag;
        }

        /*
         * Computes this custom tag's custom nesting level, which corresponds to the number of times this custom tag is
         * nested inside itself.
         *
         * Example:
         *
         * <g:h> <a:b> -- nesting level 0 <c:d> <e:f> <a:b> -- nesting level 1 <a:b> -- nesting level 2 </a:b> </a:b>
         * <a:b> -- nesting level 1 </a:b> </e:f> </c:d> </a:b> </g:h>
         *
         * @return Custom tag's nesting level
         */
        private int makeCustomNestingLevel() {
            int n = 0;
            Node p = parent;
            while (p != null) {
                if ((p instanceof Node.CustomTag) && qName.equals(p.qName)) {
                    n++;
                }
                p = p.parent;
            }
            return n;
        }

        /**
         * A custom action is considered to have an empty body if any of the following hold true:
         * <ul>
         * <li>getBody() returns null</li>
         * <li>all immediate children are jsp:attribute actions</li>
         * <li>the action's jsp:body is empty</li>
         * </ul>
         *
         * @return {@code true} if this custom action has an empty body, and {@code false} otherwise.
         */
        public boolean hasEmptyBody() {
            boolean hasEmptyBody = true;
            Nodes nodes = getBody();
            if (nodes != null) {
                int numChildNodes = nodes.size();
                for (int i = 0; i < numChildNodes; i++) {
                    Node n = nodes.getNode(i);
                    if (!(n instanceof NamedAttribute)) {
                        if (n instanceof JspBody) {
                            hasEmptyBody = (n.getBody() == null);
                        } else {
                            hasEmptyBody = false;
                        }
                        break;
                    }
                }
            }

            return hasEmptyBody;
        }
    }

    /**
     * Used as a placeholder for the evaluation code of a custom action attribute (used by the tag plugin machinery
     * only).
     */
    public static class AttributeGenerator extends Node {
        private final String name; // name of the attribute

        private final CustomTag tag; // The tag this attribute belongs to

        AttributeGenerator(Mark start, String name, CustomTag tag) {
            super(start, null);
            this.name = name;
            this.tag = tag;
        }

        @Override
        public void accept(Visitor v) throws JasperException {
            v.visit(this);
        }

        /**
         * Returns the name of the attribute.
         *
         * @return The attribute name
         */
        public String getName() {
            return name;
        }

        /**
         * Returns the custom tag this attribute belongs to.
         *
         * @return The custom tag
         */
        public CustomTag getTag() {
            return tag;
        }
    }

    /**
     * Represents the body of a &lt;jsp:text&gt; element
     */
    public static class JspText extends Node {

        JspText(String qName, Attributes nonTaglibXmlnsAttrs, Attributes taglibAttrs, Mark start, Node parent) {
            super(qName, TEXT_ACTION, null, nonTaglibXmlnsAttrs, taglibAttrs, start, parent);
        }

        @Override
        public void accept(Visitor v) throws JasperException {
            v.visit(this);
        }
    }

    /**
     * Represents a Named Attribute (&lt;jsp:attribute&gt;)
     */
    public static class NamedAttribute extends ChildInfoBase {

        // A unique temporary variable name suitable for code generation
        private String temporaryVariableName;

        // True if this node is to be trimmed, or false otherwise
        private boolean trim = true;

        // True if this attribute should be omitted from the output if
        // used with a <jsp:element>, otherwise false
        private JspAttribute omit;

        private final String name;

        private String localName;

        private String prefix;

        NamedAttribute(Attributes attrs, Mark start, Node parent) {
            this(JSP_ATTRIBUTE_ACTION, attrs, null, null, start, parent);
        }

        NamedAttribute(String qName, Attributes attrs, Attributes nonTaglibXmlnsAttrs, Attributes taglibAttrs,
                Mark start, Node parent) {

            super(qName, ATTRIBUTE_ACTION, attrs, nonTaglibXmlnsAttrs, taglibAttrs, start, parent);
            if ("false".equals(this.getAttributeValue("trim"))) {
                // (if null or true, leave default of true)
                trim = false;
            }
            name = this.getAttributeValue("name");
            if (name != null) {
                // Mandatory attribute "name" will be checked in Validator
                localName = name;
                int index = name.indexOf(':');
                if (index != -1) {
                    prefix = name.substring(0, index);
                    localName = name.substring(index + 1);
                }
            }
        }

        @Override
        public void accept(Visitor v) throws JasperException {
            v.visit(this);
        }

        /**
         * Returns the name of this named attribute.
         *
         * @return The attribute name
         */
        public String getName() {
            return this.name;
        }

        @Override
        public String getLocalName() {
            return this.localName;
        }

        /**
         * Returns the prefix of this named attribute.
         *
         * @return The attribute prefix
         */
        public String getPrefix() {
            return this.prefix;
        }

        /**
         * Returns whether this named attribute should be trimmed.
         *
         * @return true if trimming is enabled
         */
        public boolean isTrim() {
            return trim;
        }

        /**
         * Sets the omit attribute for this named attribute.
         *
         * @param omit The omit attribute to set
         */
        public void setOmit(JspAttribute omit) {
            this.omit = omit;
        }

        /**
         * Returns the omit attribute of this named attribute.
         *
         * @return The omit attribute
         */
        public JspAttribute getOmit() {
            return omit;
        }

        /**
         * Returns a unique temporary variable name to store the result in.
         *
         * @return A unique temporary variable name
         */
        public String getTemporaryVariableName() {
            if (temporaryVariableName == null) {
                temporaryVariableName = getRoot().nextTemporaryVariableName();
            }
            return temporaryVariableName;
        }

        /*
         * Get the attribute value from this named attribute (<jsp:attribute>). Since this method is only for attributes
         * that are not rtexpr, we can assume the body of the jsp:attribute is a template text.
         */
        @Override
        public String getText() {

            class AttributeVisitor extends Visitor {
                private String attrValue = null;

                @Override
                public void visit(TemplateText txt) {
                    attrValue = txt.getText();
                }

                public String getAttrValue() {
                    return attrValue;
                }
            }

            // According to JSP 2.0, if the body of the <jsp:attribute>
            // action is empty, it is equivalent of specifying "" as the value
            // of the attribute.
            String text = "";
            if (getBody() != null) {
                AttributeVisitor attributeVisitor = new AttributeVisitor();
                try {
                    getBody().visit(attributeVisitor);
                } catch (JasperException e) {
                    // Ignore
                }
                text = attributeVisitor.getAttrValue();
            }

            return text;
        }
    }

    /**
     * Represents a JspBody node (&lt;jsp:body&gt;)
     */
    public static class JspBody extends ChildInfoBase {

        JspBody(Mark start, Node parent) {
            this(JSP_BODY_ACTION, null, null, start, parent);
        }

        JspBody(String qName, Attributes nonTaglibXmlnsAttrs, Attributes taglibAttrs, Mark start, Node parent) {
            super(qName, BODY_ACTION, null, nonTaglibXmlnsAttrs, taglibAttrs, start, parent);
        }

        @Override
        public void accept(Visitor v) throws JasperException {
            v.visit(this);
        }
    }

    /**
     * Represents a template text string
     */
    public static class TemplateText extends Node {

        private ArrayList<Integer> extraSmap = null;

        TemplateText(String text, Mark start, Node parent) {
            super(null, null, text, start, parent);
        }

        @Override
        public void accept(Visitor v) throws JasperException {
            v.visit(this);
        }

        /**
         * Trim all whitespace from the left of the template text
         */
        public void ltrim() {
            int index = 0;
            while ((index < text.length()) && (text.charAt(index) <= ' ')) {
                index++;
            }
            text = text.substring(index);
        }

        /**
         * Sets the text content of this template text node.
         *
         * @param text The text content to set
         */
        public void setText(String text) {
            this.text = text;
        }

        /**
         * Trim all whitespace from the right of the template text
         */
        public void rtrim() {
            int index = text.length();
            while ((index > 0) && (text.charAt(index - 1) <= ' ')) {
                index--;
            }
            text = text.substring(0, index);
        }

        /**
         * Checks if this template text contains whitespace only.
         *
         * @return true if this template text contains whitespace only
         */
        public boolean isAllSpace() {
            boolean isAllSpace = true;
            for (int i = 0; i < text.length(); i++) {
                if (!Character.isWhitespace(text.charAt(i))) {
                    isAllSpace = false;
                    break;
                }
            }
            return isAllSpace;
        }

        /**
         * Add a source to Java line mapping
         *
         * @param srcLine The position of the source line, relative to the line at the start of this node. The
         *                    corresponding java line is assumed to be consecutive, i.e. one more than the last.
         */
        public void addSmap(int srcLine) {
            if (extraSmap == null) {
                extraSmap = new ArrayList<>();
            }
            extraSmap.add(Integer.valueOf(srcLine));
        }

        /**
         * Returns the extra source-to-Java line mappings for this template text.
         *
         * @return The extra SMAP entries, or null if none
         */
        public ArrayList<Integer> getExtraSmap() {
            return extraSmap;
        }
    }

    /**
     * Represents attributes that can be request time expressions. Can either be a plain attribute, an attribute that
     * represents a request time expression value, or a named attribute (specified using the jsp:attribute standard
     * action).
     */

    public static class JspAttribute {

        private final String qName;

        private final String uri;

        private final String localName;

        private final String value;

        private final boolean expression;

        private final boolean dynamic;

        private final ELNode.Nodes el;

        private final TagAttributeInfo tai;

        // If true, this JspAttribute represents a <jsp:attribute>
        private final boolean namedAttribute;

        // The node in the parse tree for the NamedAttribute
        private final NamedAttribute namedAttributeNode;

        JspAttribute(TagAttributeInfo tai, String qName, String uri, String localName, String value, boolean expr,
                ELNode.Nodes el, boolean dyn) {
            this.qName = qName;
            this.uri = uri;
            this.localName = localName;
            this.value = value;
            this.namedAttributeNode = null;
            this.expression = expr;
            this.el = el;
            this.dynamic = dyn;
            this.namedAttribute = false;
            this.tai = tai;
        }

        /**
         * Allow node to validate itself.
         *
         * @param ef  The expression factory to use to evaluate any EL
         * @param ctx The context to use to evaluate any EL
         *
         * @throws ELException If validation fails
         */
        public void validateEL(ExpressionFactory ef, ELContext ctx) throws ELException {
            if (this.el != null) {
                // determine exact type
                ef.createValueExpression(ctx, this.value, String.class);
            }
        }

        /**
         * Use this constructor if the JspAttribute represents a named attribute. In this case, we have to store the
         * nodes of the body of the attribute.
         */
        JspAttribute(NamedAttribute na, TagAttributeInfo tai, boolean dyn) {
            this.qName = na.getName();
            this.localName = na.getLocalName();
            this.value = null;
            this.namedAttributeNode = na;
            this.expression = false;
            this.el = null;
            this.dynamic = dyn;
            this.namedAttribute = true;
            this.tai = tai;
            this.uri = null;
        }

        /**
         * Returns the name of the attribute.
         *
         * @return The name of the attribute
         */
        public String getName() {
            return qName;
        }

        /**
         * Returns the local name of the attribute.
         *
         * @return The local name of the attribute
         */
        public String getLocalName() {
            return localName;
        }

        /**
         * Returns the namespace of the attribute.
         *
         * @return The namespace of the attribute, or {@code null} if in the default namespace
         */
        public String getURI() {
            return uri;
        }

        /**
         * Returns the TagAttributeInfo for this attribute.
         *
         * @return The TagAttributeInfo, or {@code null} if not available
         */
        public TagAttributeInfo getTagAttributeInfo() {
            return this.tai;
        }

        /**
         * Returns whether this is a deferred value expression input.
         *
         * @return {@code true} if there's TagAttributeInfo meaning we need to assign a ValueExpression
         */
        public boolean isDeferredInput() {
            return this.tai != null && this.tai.isDeferredValue();
        }

        /**
         * Returns whether this is a deferred method expression input.
         *
         * @return {@code true} if there's TagAttributeInfo meaning we need to assign a MethodExpression
         */
        public boolean isDeferredMethodInput() {
            return this.tai != null && this.tai.isDeferredMethod();
        }

        /**
         * Returns the expected type name for this attribute.
         *
         * @return The expected type name, or "java.lang.Object" as default
         */
        public String getExpectedTypeName() {
            if (this.tai != null) {
                if (this.isDeferredInput()) {
                    return this.tai.getExpectedTypeName();
                } else if (this.isDeferredMethodInput()) {
                    String m = this.tai.getMethodSignature();
                    if (m != null) {
                        int rti = m.trim().indexOf(' ');
                        if (rti > 0) {
                            return m.substring(0, rti).trim();
                        }
                    }
                }
            }
            return "java.lang.Object";
        }

        /**
         * Returns the parameter type names for a deferred method expression.
         *
         * @return Array of parameter type names, or empty array if not applicable
         */
        public String[] getParameterTypeNames() {
            if (this.tai != null) {
                if (this.isDeferredMethodInput()) {
                    String m = this.tai.getMethodSignature();
                    if (m != null) {
                        m = m.trim();
                        m = m.substring(m.indexOf('(') + 1);
                        m = m.substring(0, m.length() - 1);
                        if (!m.trim().isEmpty()) {
                            String[] p = m.split(",");
                            for (int i = 0; i < p.length; i++) {
                                p[i] = p[i].trim();
                            }
                            return p;
                        }
                    }
                }
            }
            return new String[0];
        }

        /**
         * Returns the value for the attribute. Only makes sense if namedAttribute is false.
         *
         * @return The value for the attribute, or the expression string (stripped of "&lt;%=", "%>", "%=", or "%" but
         *             containing "${" and "}" for EL expressions)
         */
        public String getValue() {
            return value;
        }

        /**
         * Returns the named attribute node. Only makes sense if namedAttribute is true.
         *
         * @return The nodes that evaluate to the body of this attribute
         */
        public NamedAttribute getNamedAttributeNode() {
            return namedAttributeNode;
        }

        /**
         * Returns whether the value represents a traditional rtexprvalue.
         *
         * @return {@code true} if the value represents a traditional rtexprvalue
         */
        public boolean isExpression() {
            return expression;
        }

        /**
         * Returns whether the value represents a NamedAttribute value.
         *
         * @return {@code true} if the value represents a NamedAttribute value
         */
        public boolean isNamedAttribute() {
            return namedAttribute;
        }

        /**
         * Returns whether the value represents an expression that should be fed to the expression interpreter.
         *
         * @return {@code true} if the value should be fed to the expression interpreter; {@code false} for
         *             string literals or rtexprvalues that should not be interpreted or reevaluated
         */
        public boolean isELInterpreterInput() {
            return el != null || this.isDeferredInput() || this.isDeferredMethodInput();
        }

        /**
         * Returns whether the value is a string literal known at translation time.
         *
         * @return {@code true} if the value is a string literal known at translation time
         */
        public boolean isLiteral() {
            return !expression && (el == null) && !namedAttribute;
        }

        /**
         * Returns whether the attribute is a "dynamic" attribute of a custom tag that implements
         * DynamicAttributes interface.
         *
         * @return {@code true} if the attribute is a "dynamic" attribute of a custom tag that implements
         *             DynamicAttributes interface. That is, a random extra attribute that is not declared by the tag
         */
        public boolean isDynamic() {
            return dynamic;
        }

        /**
         * Returns the EL expression nodes for this attribute.
         *
         * @return The EL nodes, or {@code null} if not an EL expression
         */
        public ELNode.Nodes getEL() {
            return el;
        }
    }

    /**
     * An ordered list of Node, used to represent the body of an element, or a jsp page of jsp document.
     */
    public static class Nodes {

        private final List<Node> list;

        private Node.Root root; // null if this is not a page

        private boolean generatedInBuffer;

        Nodes() {
            list = new ArrayList<>();
        }

        Nodes(Node.Root root) {
            this.root = root;
            list = new ArrayList<>();
            list.add(root);
        }

        /**
         * Appends a node to the list
         *
         * @param n The node to add
         */
        public void add(Node n) {
            list.add(n);
            root = null;
        }

        /**
         * Removes the given node from the list.
         *
         * @param n The node to be removed
         */
        public void remove(Node n) {
            list.remove(n);
        }

        /**
         * Visit the nodes in the list with the supplied visitor
         *
         * @param v The visitor used
         *
         * @throws JasperException if an error occurs while visiting a node
         */
        public void visit(Visitor v) throws JasperException {
            for (Node n : list) {
                n.accept(v);
            }
        }

        /**
         * Returns the number of nodes in this collection.
         *
         * @return The number of nodes
         */
        public int size() {
            return list.size();
        }

        /**
         * Returns the node at the given index.
         *
         * @param index The index of the node to retrieve
         * @return The node at the given index, or null if out of bounds
         */
        public Node getNode(int index) {
            Node n = null;
            try {
                n = list.get(index);
            } catch (ArrayIndexOutOfBoundsException e) {
                // Ignore
            }
            return n;
        }

        /**
         * Returns the root node of this collection.
         *
         * @return The root node, or null if not set
         */
        public Node.Root getRoot() {
            return root;
        }

        /**
         * Returns whether the generated content is buffered.
         *
         * @return true if content is generated in a buffer
         */
        public boolean isGeneratedInBuffer() {
            return generatedInBuffer;
        }

        /**
         * Sets whether the generated content is buffered.
         *
         * @param g true if content is generated in a buffer
         */
        public void setGeneratedInBuffer(boolean g) {
            generatedInBuffer = g;
        }
    }

    /**
     * A visitor class for visiting the node. This class also provides the default action (i.e. nop) for each of the
     * child class of the Node. An actual visitor should extend this class and supply the visit method for the nodes
     * that it cares.
     */
    public static class Visitor {

        /**
         * Constructs a new Visitor instance.
         */
        public Visitor() {
        }

        /**
         * This method provides a place to put actions that are common to all nodes. Override this in the child visitor
         * class if needed.
         *
         * @param n The node to visit
         * @throws JasperException if an error occurs while visiting the node
         */
        @SuppressWarnings("unused")
        protected void doVisit(Node n) throws JasperException {
            // NOOP by default
        }

        /**
         * Visit the body of a node, using the current visitor
         *
         * @param n The node to visit
         * @throws JasperException if an error occurs while visiting the body
         */
        protected void visitBody(Node n) throws JasperException {
            if (n.getBody() != null) {
                n.getBody().visit(this);
            }
        }

        /**
         * Visits a root node.
         *
         * @param n The root node to visit
         * @throws JasperException if an error occurs while visiting the node
         */
        public void visit(Root n) throws JasperException {
            doVisit(n);
            visitBody(n);
        }

        /**
         * Visits a JSP root node.
         *
         * @param n The JSP root node to visit
         * @throws JasperException if an error occurs while visiting the node
         */
        public void visit(JspRoot n) throws JasperException {
            doVisit(n);
            visitBody(n);
        }

        /**
         * Visits a page directive node.
         *
         * @param n The page directive node to visit
         * @throws JasperException if an error occurs while visiting the node
         */
        public void visit(PageDirective n) throws JasperException {
            doVisit(n);
        }

        /**
         * Visits a tag directive node.
         *
         * @param n The tag directive node to visit
         * @throws JasperException if an error occurs while visiting the node
         */
        public void visit(TagDirective n) throws JasperException {
            doVisit(n);
        }

        /**
         * Visits an include directive node.
         *
         * @param n The include directive node to visit
         * @throws JasperException if an error occurs while visiting the node
         */
        public void visit(IncludeDirective n) throws JasperException {
            doVisit(n);
            visitBody(n);
        }

        /**
         * Visits a taglib directive node.
         *
         * @param n The taglib directive node to visit
         * @throws JasperException if an error occurs while visiting the node
         */
        public void visit(TaglibDirective n) throws JasperException {
            doVisit(n);
        }

        /**
         * Visits an attribute directive node.
         *
         * @param n The attribute directive node to visit
         * @throws JasperException if an error occurs while visiting the node
         */
        public void visit(AttributeDirective n) throws JasperException {
            doVisit(n);
        }

        /**
         * Visits a variable directive node.
         *
         * @param n The variable directive node to visit
         * @throws JasperException if an error occurs while visiting the node
         */
        public void visit(VariableDirective n) throws JasperException {
            doVisit(n);
        }

        /**
         * Visits a comment node.
         *
         * @param n The comment node to visit
         * @throws JasperException if an error occurs while visiting the node
         */
        public void visit(Comment n) throws JasperException {
            doVisit(n);
        }

        /**
         * Visits a declaration node.
         *
         * @param n The declaration node to visit
         * @throws JasperException if an error occurs while visiting the node
         */
        public void visit(Declaration n) throws JasperException {
            doVisit(n);
        }

        /**
         * Visits an expression node.
         *
         * @param n The expression node to visit
         * @throws JasperException if an error occurs while visiting the node
         */
        public void visit(Expression n) throws JasperException {
            doVisit(n);
        }

        /**
         * Visits a scriptlet node.
         *
         * @param n The scriptlet node to visit
         * @throws JasperException if an error occurs while visiting the node
         */
        public void visit(Scriptlet n) throws JasperException {
            doVisit(n);
        }

        /**
         * Visits an EL expression node.
         *
         * @param n The EL expression node to visit
         * @throws JasperException if an error occurs while visiting the node
         */
        public void visit(ELExpression n) throws JasperException {
            doVisit(n);
        }

        /**
         * Visits an include action node.
         *
         * @param n The include action node to visit
         * @throws JasperException if an error occurs while visiting the node
         */
        public void visit(IncludeAction n) throws JasperException {
            doVisit(n);
            visitBody(n);
        }

        /**
         * Visits a forward action node.
         *
         * @param n The forward action node to visit
         * @throws JasperException if an error occurs while visiting the node
         */
        public void visit(ForwardAction n) throws JasperException {
            doVisit(n);
            visitBody(n);
        }

        /**
         * Visits a get property node.
         *
         * @param n The get property node to visit
         * @throws JasperException if an error occurs while visiting the node
         */
        public void visit(GetProperty n) throws JasperException {
            doVisit(n);
            visitBody(n);
        }

        /**
         * Visits a set property node.
         *
         * @param n The set property node to visit
         * @throws JasperException if an error occurs while visiting the node
         */
        public void visit(SetProperty n) throws JasperException {
            doVisit(n);
            visitBody(n);
        }

        /**
         * Visits a param action node.
         *
         * @param n The param action node to visit
         * @throws JasperException if an error occurs while visiting the node
         */
        public void visit(ParamAction n) throws JasperException {
            doVisit(n);
            visitBody(n);
        }

        public void visit(ParamsAction n) throws JasperException {
            doVisit(n);
            visitBody(n);
        }

        public void visit(FallBackAction n) throws JasperException {
            doVisit(n);
            visitBody(n);
        }

        /**
         * Visits a use bean node.
         *
         * @param n The use bean node to visit
         * @throws JasperException if an error occurs while visiting the node
         */
        public void visit(UseBean n) throws JasperException {
            doVisit(n);
            visitBody(n);
        }

        public void visit(PlugIn n) throws JasperException {
            doVisit(n);
            visitBody(n);
        }

        /**
         * Visits a custom tag node.
         *
         * @param n The custom tag node to visit
         * @throws JasperException if an error occurs while visiting the node
         */
        public void visit(CustomTag n) throws JasperException {
            doVisit(n);
            visitBody(n);
        }

        /**
         * Visits an uninterpreted tag node.
         *
         * @param n The uninterpreted tag node to visit
         * @throws JasperException if an error occurs while visiting the node
         */
        public void visit(UninterpretedTag n) throws JasperException {
            doVisit(n);
            visitBody(n);
        }

        /**
         * Visits a JSP element node.
         *
         * @param n The JSP element node to visit
         * @throws JasperException if an error occurs while visiting the node
         */
        public void visit(JspElement n) throws JasperException {
            doVisit(n);
            visitBody(n);
        }

        /**
         * Visits a JSP text node.
         *
         * @param n The JSP text node to visit
         * @throws JasperException if an error occurs while visiting the node
         */
        public void visit(JspText n) throws JasperException {
            doVisit(n);
            visitBody(n);
        }

        /**
         * Visits a named attribute node.
         *
         * @param n The named attribute node to visit
         * @throws JasperException if an error occurs while visiting the node
         */
        public void visit(NamedAttribute n) throws JasperException {
            doVisit(n);
            visitBody(n);
        }

        /**
         * Visits a JSP body node.
         *
         * @param n The JSP body node to visit
         * @throws JasperException if an error occurs while visiting the node
         */
        public void visit(JspBody n) throws JasperException {
            doVisit(n);
            visitBody(n);
        }

        /**
         * Visits an invoke action node.
         *
         * @param n The invoke action node to visit
         * @throws JasperException if an error occurs while visiting the node
         */
        public void visit(InvokeAction n) throws JasperException {
            doVisit(n);
            visitBody(n);
        }

        /**
         * Visits a do body action node.
         *
         * @param n The do body action node to visit
         * @throws JasperException if an error occurs while visiting the node
         */
        public void visit(DoBodyAction n) throws JasperException {
            doVisit(n);
            visitBody(n);
        }

        /**
         * Visits a template text node.
         *
         * @param n The template text node to visit
         * @throws JasperException if an error occurs while visiting the node
         */
        public void visit(TemplateText n) throws JasperException {
            doVisit(n);
        }

        /**
         * Visits a JSP output node.
         *
         * @param n The JSP output node to visit
         * @throws JasperException if an error occurs while visiting the node
         */
        public void visit(JspOutput n) throws JasperException {
            doVisit(n);
        }

        /**
         * Visits an attribute generator node.
         *
         * @param n The attribute generator node to visit
         * @throws JasperException if an error occurs while visiting the node
         */
        public void visit(AttributeGenerator n) throws JasperException {
            doVisit(n);
        }
    }
}
