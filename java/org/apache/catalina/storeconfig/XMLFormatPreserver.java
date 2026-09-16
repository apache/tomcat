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
package org.apache.catalina.storeconfig;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.util.security.Escape;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.DTDHandler;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.LexicalHandler;

/**
 * Best-effort preservation of the original layout (comments, blank lines and attribute order) of <code>server.xml</code>
 * and context configuration files when StoreConfig rewrites them.
 * <p>
 * The freshly generated XML is always the source of truth for the content. Both the previous version and the freshly
 * generated version are parsed, the elements of both documents are matched and the freshly generated document is
 * re-serialized using the layout of the previous version.
 * <p>
 * The preservation is best-effort. Whenever the previous version is missing, cannot be parsed, or the result cannot
 * be verified, the freshly generated XML is returned unchanged.
 */
public final class XMLFormatPreserver {

    private static final Log log = LogFactory.getLog(XMLFormatPreserver.class);

    /**
     * The string manager for this package.
     */
    private static final StringManager sm = StringManager.getManager(Constants.Package);

    /**
     * The attribute names that strongly identify an element when matching elements between the previous and the
     * freshly generated document.
     */
    private static final String[] KEY_ATTRIBUTES = { "className", "name", "port", "path", "docBase" };

    private XMLFormatPreserver() {
        // Utility class, do not instantiate
    }

    /**
     * Preserves the layout of the previous version of a configuration file for the freshly generated XML.
     *
     * @param originalFile The previous version of the configuration file, may be {@code null}
     * @param newXml       The freshly generated XML
     * @param encoding     The character encoding of the configuration file
     *
     * @return The freshly generated XML, re-laid-out using the previous version when possible, otherwise the freshly
     *         generated XML unchanged
     */
    public static String preserve(File originalFile, String newXml, String encoding) {
        if (newXml == null || originalFile == null || !originalFile.isFile()) {
            return newXml;
        }
        try {
            String originalXml = new String(Files.readAllBytes(originalFile.toPath()), Charset.forName(encoding));
            if (originalXml.startsWith("\uFEFF")) {
                originalXml = originalXml.substring(1);
            }
            return preserve(originalXml, newXml, encoding);
        } catch (Exception e) {
            log.debug(sm.getString("xmlFormatPreserver.unreadable", originalFile), e);
            return newXml;
        }
    }

    /**
     * Preserves the layout of the previous version of an XML document for the freshly generated XML.
     *
     * @param originalXml The previous version of the XML document, may be {@code null}
     * @param newXml      The freshly generated XML
     * @param encoding    The character encoding used in the XML declaration of the result
     *
     * @return The freshly generated XML, re-laid-out using the previous version when possible, otherwise the freshly
     *         generated XML unchanged
     */
    public static String preserve(String originalXml, String newXml, String encoding) {
        if (newXml == null) {
            return null;
        }
        if (originalXml == null || originalXml.isEmpty()) {
            return newXml;
        }
        try {
            return preserveInternal(originalXml, newXml, (encoding == null || encoding.isEmpty()) ? "UTF-8" : encoding);
        } catch (Exception e) {
            log.debug(sm.getString("xmlFormatPreserver.failed"), e);
            return newXml;
        }
    }

    /**
     * Parse both documents, match the elements and re-serialize the freshly generated document using the layout of
     * the previous version.
     *
     * @param originalXml The previous version of the XML document
     * @param newXml      The freshly generated XML
     * @param encoding    The character encoding used in the XML declaration of the result
     *
     * @return The re-laid-out XML document
     *
     * @throws Exception If one of the documents cannot be parsed or the result cannot be verified
     */
    private static String preserveInternal(String originalXml, String newXml, String encoding) throws Exception {
        Model original = parse(originalXml);
        Model fresh = parse(newXml);
        if (original.root == null || fresh.root == null || !original.root.name.equals(fresh.root.name)) {
            // Not enough overlap to preserve the layout
            return newXml;
        }
        String lineSeparator = originalXml.contains("\r\n") ? "\r\n" : "\n";
        matchChildren(original.root, fresh.root);
        StringBuilder result = new StringBuilder(newXml.length() + 512);
        result.append("<?xml version=\"1.0\" encoding=\"").append(encoding).append("\"?>").append(lineSeparator);
        if (original.doctype != null) {
            result.append(original.doctype).append(lineSeparator);
        }
        emitTokens(result, original.root.preamble, 0, lineSeparator);
        emitElement(result, fresh.root, 0, lineSeparator);
        emitTokens(result, original.trailing, 0, lineSeparator);
        String formatted = result.toString();
        // Verify that the re-laid-out document still carries exactly the same content as the freshly generated one
        Model check = parse(formatted);
        if (check.root == null || !contentEquals(fresh.root, check.root)) {
            log.debug(sm.getString("xmlFormatPreserver.verifyFailed"));
            return newXml;
        }
        return formatted;
    }

    /**
     * Parse an XML document into a model that also captures the layout information (comments, blank lines, attribute
     * order).
     *
     * @param xml The XML document to parse
     *
     * @return The parsed model
     *
     * @throws SAXException                 If the document is not well-formed
     * @throws IOException                  If the document cannot be read
     * @throws ParserConfigurationException If the parser cannot be configured
     */
    private static Model parse(String xml) throws SAXException, IOException, ParserConfigurationException {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setValidating(false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        disableFeature(factory, "http://xml.org/sax/features/external-general-entities");
        disableFeature(factory, "http://xml.org/sax/features/external-parameter-entities");
        Model model = new Model();
        ModelBuilder builder = new ModelBuilder(model);
        XMLReader reader = factory.newSAXParser().getXMLReader();
        reader.setContentHandler(builder);
        reader.setDTDHandler(builder);
        reader.setErrorHandler(builder);
        reader.setProperty("http://xml.org/sax/properties/lexical-handler", builder);
        reader.parse(new InputSource(new StringReader(xml)));
        return model;
    }

    /**
     * Disable a parser feature, ignoring parsers that do not support it.
     *
     * @param factory  The parser factory
     * @param feature  The feature to disable
     */
    private static void disableFeature(SAXParserFactory factory, String feature) {
        try {
            factory.setFeature(feature, false);
        } catch (ParserConfigurationException ignore) {
            // The parser does not support this feature. Continue with the remaining protections.
        } catch (SAXException ignore) {
            // The parser does not support this feature. Continue with the remaining protections.
        }
    }

    /**
     * Match the element children of the previous version with the element children of the freshly generated version.
     * The match of each freshly generated element is stored in {@link XmlElement#match}.
     *
     * @param original The element of the previous version
     * @param fresh    The matching element of the freshly generated version
     */
    private static void matchChildren(XmlElement original, XmlElement fresh) {
        Map<String, List<XmlElement>> candidatesByName = new HashMap<>();
        for (XmlElement child : original.children) {
            candidatesByName.computeIfAbsent(child.name, (name) -> new ArrayList<>()).add(child);
        }
        for (XmlElement freshChild : fresh.children) {
            List<XmlElement> candidates = candidatesByName.get(freshChild.name);
            if (candidates == null) {
                continue;
            }
            candidates.removeIf((candidate) -> candidate.matched);
            if (candidates.isEmpty()) {
                continue;
            }
            XmlElement best = null;
            int bestScore = 0;
            for (XmlElement candidate : candidates) {
                int score = matchScore(freshChild, candidate);
                if (score > bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }
            if (best == null && candidates.size() == 1) {
                // A unique candidate with the same tag name is assumed to be the match
                best = candidates.get(0);
            }
            if (best != null) {
                best.matched = true;
                freshChild.match = best;
                matchChildren(best, freshChild);
            }
        }
    }

    /**
     * Score how well a candidate element of the previous version matches a freshly generated element.
     *
     * @param fresh    The freshly generated element
     * @param candidate The candidate element of the previous version
     *
     * @return The match score, 0 if nothing is shared
     */
    private static int matchScore(XmlElement fresh, XmlElement candidate) {
        // Identical attribute sets (the order may differ) are the strongest match
        if (attributesEqual(fresh, candidate)) {
            return 1000;
        }
        int score = 0;
        for (int i = 0; i < fresh.attributeNames.size(); i++) {
            String name = fresh.attributeNames.get(i);
            String value = fresh.attributeValues.get(i);
            int index = candidate.attributeIndex(name);
            if (index >= 0 && candidate.attributeValues.get(index).equals(value)) {
                score += isKeyAttribute(name) ? 100 : 10;
            }
        }
        String freshText = fresh.getText();
        String candidateText = candidate.getText();
        if (freshText != null && !freshText.trim().isEmpty()
                && Objects.equals(freshText.trim(), candidateText == null ? null : candidateText.trim())) {
            score += 50;
        }
        return score;
    }

    /**
     * Check whether two elements have the same attribute set (the order may differ).
     *
     * @param a The first element
     * @param b The second element
     *
     * @return {@code true} if both elements have the same attribute set
     */
    private static boolean attributesEqual(XmlElement a, XmlElement b) {
        if (a.attributeNames.size() != b.attributeNames.size()) {
            return false;
        }
        for (int i = 0; i < a.attributeNames.size(); i++) {
            int index = b.attributeIndex(a.attributeNames.get(i));
            if (index < 0 || !a.attributeValues.get(i).equals(b.attributeValues.get(index))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check whether an attribute name strongly identifies an element.
     *
     * @param name The attribute name
     *
     * @return {@code true} if the attribute name is a key attribute
     */
    private static boolean isKeyAttribute(String name) {
        for (String key : KEY_ATTRIBUTES) {
            if (key.equals(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Serialize an element of the freshly generated document, decorated with the layout of its match in the previous
     * version.
     *
     * @param result        The output buffer
     * @param fresh         The element of the freshly generated document
     * @param indent        The indentation in spaces
     * @param lineSeparator The line separator to use
     */
    private static void emitElement(StringBuilder result, XmlElement fresh, int indent, String lineSeparator) {
        XmlElement original = fresh.match;
        String indentString = spaces(indent);
        if (original != null) {
            emitTokens(result, original.preamble, indent, lineSeparator);
        }
        boolean hasChildren = !fresh.children.isEmpty();
        String text = fresh.getText();
        boolean hasText = text != null && !text.trim().isEmpty();
        // Reproduce the attribute line wrapping of StoreAppender: the position starts at the indentation level and
        // the attributes are wrapped on a new line once the position exceeds 60 characters
        int position = indent;
        int wrapIndent = hasChildren ? indent + 4 : indent + 2;
        List<String> orderedNames = new ArrayList<>(fresh.attributeNames);
        if (original != null) {
            // Use the attribute order of the previous version. Attributes that are only present in the freshly
            // generated document keep their position at the end
            List<String> reordered = new ArrayList<>(fresh.attributeNames.size());
            for (String name : original.attributeNames) {
                if (fresh.attributeIndex(name) >= 0) {
                    reordered.add(name);
                }
            }
            for (String name : orderedNames) {
                if (!reordered.contains(name)) {
                    reordered.add(name);
                }
            }
            orderedNames = reordered;
        }
        result.append(indentString).append('<').append(fresh.name);
        for (String name : orderedNames) {
            String value = Escape.xml(fresh.attributeValues.get(fresh.attributeIndex(name)));
            position += name.length() + value.length();
            if (position > 60) {
                result.append(lineSeparator).append(spaces(wrapIndent));
                position = wrapIndent;
            } else {
                result.append(' ');
            }
            result.append(name).append("=\"").append(value).append("\"");
        }
        if (!hasChildren && !hasText) {
            result.append("/>").append(lineSeparator);
            return;
        }
        result.append('>');
        if (hasText && !hasChildren) {
            result.append(Escape.xml(text)).append("</").append(fresh.name).append('>').append(lineSeparator);
            return;
        }
        result.append(lineSeparator);
        for (XmlElement child : fresh.children) {
            emitElement(result, child, indent + 2, lineSeparator);
        }
        if (original != null) {
            emitTokens(result, original.beforeClose, indent, lineSeparator);
        }
        result.append(indentString).append("</").append(fresh.name).append('>').append(lineSeparator);
    }

    /**
     * Emit the layout tokens (comments and blank lines) of the previous version.
     *
     * @param result        The output buffer
     * @param tokens        The layout tokens, may be {@code null}
     * @param indent        The indentation in spaces
     * @param lineSeparator The line separator to use
     */
    private static void emitTokens(StringBuilder result, List<Token> tokens, int indent, String lineSeparator) {
        if (tokens == null || tokens.isEmpty()) {
            return;
        }
        String indentString = spaces(indent);
        for (Token token : tokens) {
            if (token.comment == null) {
                for (int i = 0; i < token.blankLines; i++) {
                    result.append(lineSeparator);
                }
            } else {
                // Keep the indentation the comment had in the previous version if it is deeper than the current
                // indentation level
                String commentIndent = token.indentHint.length() > indentString.length() ? token.indentHint
                        : indentString;
                emitComment(result, token.comment, commentIndent, lineSeparator);
            }
        }
    }

    /**
     * Emit a comment of the previous version. The first line is aligned to the previous indentation level, the
     * remaining lines keep their original layout.
     *
     * @param result        The output buffer
     * @param comment       The comment text, without the delimiters
     * @param indentString  The indentation for the first line
     * @param lineSeparator The line separator to use
     */
    private static void emitComment(StringBuilder result, String comment, String indentString, String lineSeparator) {
        String normalized = comment.replace("\r\n", "\n");
        String[] lines = normalized.split("\n", -1);
        result.append(indentString).append("<!--").append(lines[0]);
        if (lines.length == 1) {
            result.append("-->").append(lineSeparator);
            return;
        }
        result.append(lineSeparator);
        for (int i = 1; i < lines.length; i++) {
            if (i < lines.length - 1) {
                result.append(lines[i]).append(lineSeparator);
            } else if (lines[i].isEmpty()) {
                result.append("-->").append(lineSeparator);
            } else {
                result.append(lines[i]).append("-->").append(lineSeparator);
            }
        }
    }

    /**
     * Compare the content (element names, attributes and text) of two elements, ignoring layout information.
     *
     * @param a The first element, may be {@code null}
     * @param b The second element, may be {@code null}
     *
     * @return {@code true} if both elements carry the same content
     */
    private static boolean contentEquals(XmlElement a, XmlElement b) {
        if (a == null || b == null) {
            return a == b;
        }
        if (!a.name.equals(b.name)) {
            return false;
        }
        if (a.attributeNames.size() != b.attributeNames.size()) {
            return false;
        }
        for (int i = 0; i < a.attributeNames.size(); i++) {
            int index = b.attributeIndex(a.attributeNames.get(i));
            if (index < 0 || !a.attributeValues.get(i).equals(b.attributeValues.get(index))) {
                return false;
            }
        }
        // Only the significant text is compared: whitespace between child elements is layout, not content
        String aText = a.getText();
        String bText = b.getText();
        String aTrimmed = (aText == null || aText.trim().isEmpty()) ? null : aText.trim();
        String bTrimmed = (bText == null || bText.trim().isEmpty()) ? null : bText.trim();
        if (!Objects.equals(aTrimmed, bTrimmed)) {
            return false;
        }
        if (a.children.size() != b.children.size()) {
            return false;
        }
        for (int i = 0; i < a.children.size(); i++) {
            if (!contentEquals(a.children.get(i), b.children.get(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Create a string of spaces.
     *
     * @param count The number of spaces
     *
     * @return The string of spaces
     */
    private static String spaces(int count) {
        StringBuilder result = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            result.append(' ');
        }
        return result.toString();
    }

    /**
     * The parsed representation of an XML document.
     */
    private static final class Model {
        private XmlElement root;
        private List<Token> trailing = new ArrayList<>();
        private String doctype;
    }

    /**
     * A parsed XML element, including the layout information of the document it was parsed from.
     */
    private static final class XmlElement {
        private final String name;
        private final List<String> attributeNames = new ArrayList<>();
        private final List<String> attributeValues = new ArrayList<>();
        private final List<XmlElement> children = new ArrayList<>();
        private StringBuilder text;
        // Layout of the document this element was parsed from
        private List<Token> preamble = new ArrayList<>();
        private List<Token> beforeClose = new ArrayList<>();
        private List<Token> pending = new ArrayList<>();
        // Matching with the other document
        private XmlElement match;
        private boolean matched;

        private XmlElement(String name) {
            this.name = name;
        }

        private void appendText(String text) {
            if (this.text == null) {
                this.text = new StringBuilder();
            }
            this.text.append(text);
        }

        private String getText() {
            return text == null ? null : text.toString();
        }

        private int attributeIndex(String name) {
            for (int i = 0; i < attributeNames.size(); i++) {
                if (attributeNames.get(i).equals(name)) {
                    return i;
                }
            }
            return -1;
        }
    }

    /**
     * A layout token: a comment or a run of blank lines.
     */
    private static final class Token {
        private final String comment;
        private final int blankLines;
        // The indentation the comment had in the document it was parsed from
        private final String indentHint;

        private Token(String comment, int blankLines, String indentHint) {
            this.comment = comment;
            this.blankLines = blankLines;
            this.indentHint = indentHint;
        }

        static Token comment(String comment, String indentHint) {
            return new Token(comment, 0, indentHint);
        }

        static Token blankLines(int count) {
            return new Token(null, count, "");
        }
    }

    /**
     * SAX handler that builds a {@link Model} from an XML document.
     */
    private static final class ModelBuilder implements ContentHandler, DTDHandler, ErrorHandler, LexicalHandler {

        private final Model model;
        private final Deque<XmlElement> stack = new ArrayDeque<>();
        private final StringBuilder gap = new StringBuilder();

        ModelBuilder(Model model) {
            this.model = model;
            // The document node is the base of the stack. Its children are the document elements.
            stack.push(new XmlElement(null));
        }

        /**
         * If the whitespace accumulated since the last layout token contains at least two line breaks, add a blank
         * lines token to the current pending tokens.
         */
        private void flushGap() {
            XmlElement top = stack.peek();
            int newlines = 0;
            for (int i = 0; i < gap.length(); i++) {
                if (gap.charAt(i) == '\n') {
                    newlines++;
                }
            }
            gap.setLength(0);
            if (newlines >= 2) {
                top.pending.add(Token.blankLines(newlines - 1));
            }
        }

        /**
         * The whitespace after the last line break of the current gap, i.e. the indentation a following comment had
         * in the document.
         *
         * @return The indentation hint, an empty string if there is none
         */
        private String gapIndentHint() {
            int lastNewline = gap.lastIndexOf("\n");
            return lastNewline < 0 ? "" : gap.substring(lastNewline + 1);
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes)
                throws SAXException {
            flushGap();
            XmlElement parent = stack.peek();
            XmlElement element = new XmlElement(qName);
            for (int i = 0; i < attributes.getLength(); i++) {
                // The SAX attribute order is the document order
                element.attributeNames.add(attributes.getQName(i));
                element.attributeValues.add(attributes.getValue(i));
            }
            // The layout tokens of the parent, accumulated since its last child, belong to this element
            element.preamble = parent.pending;
            parent.pending = new ArrayList<>();
            parent.children.add(element);
            stack.push(element);
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            XmlElement element = stack.pop();
            flushGap();
            element.beforeClose = element.pending;
        }

        @Override
        public void endDocument() throws SAXException {
            flushGap();
            XmlElement document = stack.peek();
            model.root = document.children.isEmpty() ? null : document.children.get(0);
            model.trailing = document.pending;
        }

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            String text = new String(ch, start, length);
            if (text.isEmpty()) {
                return;
            }
            XmlElement top = stack.peek();
            if (top.name != null) {
                top.appendText(text);
            }
            boolean whitespace = true;
            for (int i = 0; i < text.length(); i++) {
                if (!Character.isWhitespace(text.charAt(i))) {
                    whitespace = false;
                    break;
                }
            }
            if (whitespace) {
                gap.append(text);
            } else {
                gap.setLength(0);
            }
        }

        @Override
        public void comment(char[] ch, int start, int length) throws SAXException {
            String indentHint = gapIndentHint();
            flushGap();
            stack.peek().pending.add(Token.comment(new String(ch, start, length), indentHint));
        }

        @Override
        public void startDTD(String name, String publicId, String systemId) throws SAXException {
            StringBuilder doctype = new StringBuilder("<!DOCTYPE ").append(name);
            if (publicId != null) {
                doctype.append(" PUBLIC \"").append(publicId).append("\" \"")
                        .append(systemId == null ? "" : systemId).append("\"");
            } else if (systemId != null) {
                doctype.append(" SYSTEM \"").append(systemId).append("\"");
            }
            doctype.append('>');
            model.doctype = doctype.toString();
        }

        @Override
        public void error(SAXParseException exception) throws SAXException {
            throw exception;
        }

        @Override
        public void fatalError(SAXParseException exception) throws SAXException {
            throw exception;
        }

        @Override
        public void warning(SAXParseException exception) throws SAXException {
            // Ignore
        }

        @Override
        public void setDocumentLocator(Locator locator) {
            // Not used
        }

        @Override
        public void startDocument() throws SAXException {
            // Not used
        }

        @Override
        public void startPrefixMapping(String prefix, String uri) throws SAXException {
            // Not used
        }

        @Override
        public void endPrefixMapping(String prefix) throws SAXException {
            // Not used
        }

        @Override
        public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
            characters(ch, start, length);
        }

        @Override
        public void processingInstruction(String target, String data) throws SAXException {
            // Not used
        }

        @Override
        public void skippedEntity(String name) throws SAXException {
            // Not used
        }

        @Override
        public void endDTD() throws SAXException {
            // Not used
        }

        @Override
        public void startEntity(String name) throws SAXException {
            // Not used
        }

        @Override
        public void endEntity(String name) throws SAXException {
            // Not used
        }

        @Override
        public void startCDATA() throws SAXException {
            // Not used
        }

        @Override
        public void endCDATA() throws SAXException {
            // Not used
        }

        @Override
        public void notationDecl(String name, String publicId, String systemId) throws SAXException {
            // Not used
        }

        @Override
        public void unparsedEntityDecl(String name, String publicId, String systemId, String notationName)
                throws SAXException {
            // Not used
        }
    }
}
