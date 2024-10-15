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

import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;

import org.apache.jasper.JasperException;
import org.apache.jasper.JspCompilationContext;
import org.apache.tomcat.Jar;
import org.apache.tomcat.util.security.Escape;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;

/**
 * This class has all the utility method(s). Ideally should move all the bean
 * containers here.
 *
 * @author Mandar Raje.
 * @author Rajiv Mordani.
 * @author Danno Ferrin
 * @author Pierre Delisle
 * @author Shawn Bayern
 * @author Mark Roth
 */
public class JspUtil {

    private static final String WEB_INF_TAGS = "/WEB-INF/tags/";
    private static final String META_INF_TAGS = "/META-INF/tags/";

    // Delimiters for request-time expressions (JSP and XML syntax)
    private static final String OPEN_EXPR = "<%=";
    private static final String CLOSE_EXPR = "%>";

    private static final String javaKeywords[] = { "abstract", "assert",
            "boolean", "break", "byte", "case", "catch", "char", "class",
            "const", "continue", "default", "do", "double", "else", "enum",
            "extends", "final", "finally", "float", "for", "goto", "if",
            "implements", "import", "instanceof", "int", "interface", "long",
            "native", "new", "package", "private", "protected", "public",
            "return", "short", "static", "strictfp", "super", "switch",
            "synchronized", "this", "throw", "throws", "transient", "try",
            "void", "volatile", "while" };

    static final int JSP_INPUT_STREAM_BUFFER_SIZE = 1024;

    public static final int CHUNKSIZE = 1024;

    /**
     * Takes a potential expression and converts it into XML form.
     * @param expression The expression to convert
     * @return XML view
     */
    public static String getExprInXml(String expression) {
        String returnString;
        int length = expression.length();

        if (expression.startsWith(OPEN_EXPR) &&
                expression.endsWith(CLOSE_EXPR)) {
            returnString = expression.substring(1, length - 1);
        } else {
            returnString = expression;
        }

        return Escape.xml(returnString);
    }

    /**
     * Checks to see if the given scope is valid.
     *
     * @param scope
     *            The scope to be checked
     * @param n
     *            The Node containing the 'scope' attribute whose value is to be
     *            checked
     * @param err
     *            error dispatcher
     *
     * @throws JasperException
     *             if scope is not null and different from &quot;page&quot;,
     *             &quot;request&quot;, &quot;session&quot;, and
     *             &quot;application&quot;
     */
    public static void checkScope(String scope, Node n, ErrorDispatcher err)
            throws JasperException {
        if (scope != null && !scope.equals("page") && !scope.equals("request")
                && !scope.equals("session") && !scope.equals("application")) {
            err.jspError(n, "jsp.error.invalid.scope", scope);
        }
    }

    /**
     * Checks if all mandatory attributes are present and if all attributes
     * present have valid names. Checks attributes specified as XML-style
     * attributes as well as attributes specified using the jsp:attribute
     * standard action.
     * @param typeOfTag The tag type
     * @param n The corresponding node
     * @param validAttributes The array with the valid attributes
     * @param err Dispatcher for errors
     * @throws JasperException An error occurred
     */
    public static void checkAttributes(String typeOfTag, Node n,
            ValidAttribute[] validAttributes, ErrorDispatcher err)
            throws JasperException {
        Attributes attrs = n.getAttributes();
        Mark start = n.getStart();
        boolean valid = true;

        // AttributesImpl.removeAttribute is broken, so we do this...
        int tempLength = (attrs == null) ? 0 : attrs.getLength();
        ArrayList<String> temp = new ArrayList<>(tempLength);
        for (int i = 0; i < tempLength; i++) {
            @SuppressWarnings("null")  // If attrs==null, tempLength == 0
            String qName = attrs.getQName(i);
            if ((!qName.equals("xmlns")) && (!qName.startsWith("xmlns:"))) {
                temp.add(qName);
            }
        }

        // Add names of attributes specified using jsp:attribute
        Node.Nodes tagBody = n.getBody();
        if (tagBody != null) {
            int numSubElements = tagBody.size();
            for (int i = 0; i < numSubElements; i++) {
                Node node = tagBody.getNode(i);
                if (node instanceof Node.NamedAttribute) {
                    String attrName = node.getAttributeValue("name");
                    temp.add(attrName);
                    // Check if this value appear in the attribute of the node
                    if (n.getAttributeValue(attrName) != null) {
                        err.jspError(n,
                                "jsp.error.duplicate.name.jspattribute",
                                attrName);
                    }
                } else {
                    // Nothing can come before jsp:attribute, and only
                    // jsp:body can come after it.
                    break;
                }
            }
        }

        /*
         * First check to see if all the mandatory attributes are present. If so
         * only then proceed to see if the other attributes are valid for the
         * particular tag.
         */
        String missingAttribute = null;

        for (ValidAttribute validAttribute : validAttributes) {
            int attrPos;
            if (validAttribute.mandatory) {
                attrPos = temp.indexOf(validAttribute.name);
                if (attrPos != -1) {
                    temp.remove(attrPos);
                    valid = true;
                } else {
                    valid = false;
                    missingAttribute = validAttribute.name;
                    break;
                }
            }
        }

        // If mandatory attribute is missing then the exception is thrown
        if (!valid) {
            err.jspError(start, "jsp.error.mandatory.attribute", typeOfTag,
                    missingAttribute);
        }

        // Check to see if there are any more attributes for the specified tag.
        int attrLeftLength = temp.size();
        if (attrLeftLength == 0) {
            return;
        }

        // Now check to see if the rest of the attributes are valid too.
        for(String attribute : temp) {
            valid = false;
            for (ValidAttribute validAttribute : validAttributes) {
                if (attribute.equals(validAttribute.name)) {
                    valid = true;
                    break;
                }
            }
            if (!valid) {
                err.jspError(start, "jsp.error.invalid.attribute", typeOfTag,
                        attribute);
            }
        }
        // XXX *could* move EL-syntax validation here... (sb)
    }

    public static class ValidAttribute {

        private final String name;
        private final boolean mandatory;

        public ValidAttribute(String name, boolean mandatory) {
            this.name = name;
            this.mandatory = mandatory;
        }

        public ValidAttribute(String name) {
            this(name, false);
        }
    }

    /**
     * Convert a String value to 'boolean'. Besides the standard conversions
     * done by Boolean.parseBoolean(s), the value "yes" (ignore case)
     * is also converted to 'true'. If 's' is null, then 'false' is returned.
     *
     * @param s
     *            the string to be converted
     * @return the boolean value associated with the string s
     */
    public static boolean booleanValue(String s) {
        boolean b = false;
        if (s != null) {
            if (s.equalsIgnoreCase("yes")) {
                b = true;
            } else {
                b = Boolean.parseBoolean(s);
            }
        }
        return b;
    }

    /**
     * Returns the <code>Class</code> object associated with the class or
     * interface with the given string name.
     *
     * <p>
     * The <code>Class</code> object is determined by passing the given string
     * name to the <code>Class.forName()</code> method, unless the given string
     * name represents a primitive type, in which case it is converted to a
     * <code>Class</code> object by appending ".class" to it (e.g.,
     * "int.class").
     * @param type The class name, array or primitive type
     * @param loader The class loader
     * @return the loaded class
     * @throws ClassNotFoundException Loading class failed
     */
    public static Class<?> toClass(String type, ClassLoader loader)
            throws ClassNotFoundException {

        Class<?> c = null;
        int i0 = type.indexOf('[');
        int dims = 0;
        if (i0 > 0) {
            // This is an array. Count the dimensions
            for (int i = 0; i < type.length(); i++) {
                if (type.charAt(i) == '[') {
                    dims++;
                }
            }
            type = type.substring(0, i0);
        }

        if ("boolean".equals(type)) {
            c = boolean.class;
        } else if ("char".equals(type)) {
            c = char.class;
        } else if ("byte".equals(type)) {
            c = byte.class;
        } else if ("short".equals(type)) {
            c = short.class;
        } else if ("int".equals(type)) {
            c = int.class;
        } else if ("long".equals(type)) {
            c = long.class;
        } else if ("float".equals(type)) {
            c = float.class;
        } else if ("double".equals(type)) {
            c = double.class;
        } else if ("void".equals(type)) {
            c = void.class;
        } else {
            c = loader.loadClass(type);
        }

        if (dims == 0) {
            return c;
        }

        if (dims == 1) {
            return java.lang.reflect.Array.newInstance(c, 1).getClass();
        }

        // Array of more than i dimension
        return java.lang.reflect.Array.newInstance(c, new int[dims]).getClass();
    }

    /**
     * Produces a String representing a call to the EL interpreter.
     *
     * @param isTagFile <code>true</code> if the file is a tag file
     *  rather than a JSP
     * @param expression
     *            a String containing zero or more "${}" expressions
     * @param expectedType
     *            the expected type of the interpreted result
     * @param fnmapvar
     *            Variable pointing to a function map.
     * @return a String representing a call to the EL interpreter.
     */
    public static String interpreterCall(boolean isTagFile, String expression,
            Class<?> expectedType, String fnmapvar) {
        /*
         * Determine which context object to use.
         */
        String jspCtxt = null;
        if (isTagFile) {
            jspCtxt = "this.getJspContext()";
        } else {
            jspCtxt = "_jspx_page_context";
        }

        /*
         * Determine whether to use the expected type's textual name or, if it's
         * a primitive, the name of its correspondent boxed type.
         */
        String returnType = expectedType.getCanonicalName();
        String targetType = returnType;
        String primitiveConverterMethod = null;
        if (expectedType.isPrimitive()) {
            if (expectedType.equals(Boolean.TYPE)) {
                returnType = Boolean.class.getName();
                primitiveConverterMethod = "booleanValue";
            } else if (expectedType.equals(Byte.TYPE)) {
                returnType = Byte.class.getName();
                primitiveConverterMethod = "byteValue";
            } else if (expectedType.equals(Character.TYPE)) {
                returnType = Character.class.getName();
                primitiveConverterMethod = "charValue";
            } else if (expectedType.equals(Short.TYPE)) {
                returnType = Short.class.getName();
                primitiveConverterMethod = "shortValue";
            } else if (expectedType.equals(Integer.TYPE)) {
                returnType = Integer.class.getName();
                primitiveConverterMethod = "intValue";
            } else if (expectedType.equals(Long.TYPE)) {
                returnType = Long.class.getName();
                primitiveConverterMethod = "longValue";
            } else if (expectedType.equals(Float.TYPE)) {
                returnType = Float.class.getName();
                primitiveConverterMethod = "floatValue";
            } else if (expectedType.equals(Double.TYPE)) {
                returnType = Double.class.getName();
                primitiveConverterMethod = "doubleValue";
            }
        }

        /*
         * Build up the base call to the interpreter.
         */
        targetType = toJavaSourceType(targetType);
        StringBuilder call = new StringBuilder(
                "("
                        + returnType
                        + ") "
                        + "org.apache.jasper.runtime.PageContextImpl.proprietaryEvaluate"
                        + "(" + Generator.quote(expression) + ", " + targetType
                        + ".class, " + "(jakarta.servlet.jsp.PageContext)" + jspCtxt + ", "
                        + fnmapvar + ")");

        /*
         * Add the primitive converter method if we need to.
         */
        if (primitiveConverterMethod != null) {
            call.insert(0, "(");
            call.append(")." + primitiveConverterMethod + "()");
        }

        return call.toString();
    }

    public static String coerceToPrimitiveBoolean(String s,
            boolean isNamedAttribute) {
        if (isNamedAttribute) {
            return "org.apache.jasper.runtime.JspRuntimeLibrary.coerceToBoolean("
                    + s + ")";
        } else {
            if (s == null || s.length() == 0) {
                return "false";
            } else {
                return Boolean.valueOf(s).toString();
            }
        }
    }

    public static String coerceToBoolean(String s, boolean isNamedAttribute) {
        if (isNamedAttribute) {
            return "(java.lang.Boolean) org.apache.jasper.runtime.JspRuntimeLibrary.coerce("
                    + s + ", java.lang.Boolean.class)";
        } else {
            if (s == null || s.length() == 0) {
                return "java.lang.Boolean.FALSE";
            } else {
                // Detect format error at translation time
                return "java.lang.Boolean.valueOf(" + Generator.quote(s) + ")";
            }
        }
    }

    public static String coerceToPrimitiveByte(String s,
            boolean isNamedAttribute) {
        if (isNamedAttribute) {
            return "org.apache.jasper.runtime.JspRuntimeLibrary.coerceToByte("
                    + s + ")";
        } else {
            if (s == null || s.length() == 0) {
                return "(byte) 0";
            } else {
                return "((byte)" + Byte.valueOf(s).toString() + ")";
            }
        }
    }

    public static String coerceToByte(String s, boolean isNamedAttribute) {
        if (isNamedAttribute) {
            return "(java.lang.Byte) org.apache.jasper.runtime.JspRuntimeLibrary.coerce("
                    + s + ", java.lang.Byte.class)";
        } else {
            if (s == null || s.length() == 0) {
                return "java.lang.Byte.valueOf((byte) 0)";
            } else {
                // Detect format error at translation time
                return "java.lang.Byte.valueOf(" + Generator.quote(s) + ")";
            }
        }
    }

    public static String coerceToChar(String s, boolean isNamedAttribute) {
        if (isNamedAttribute) {
            return "org.apache.jasper.runtime.JspRuntimeLibrary.coerceToChar("
                    + s + ")";
        } else {
            if (s == null || s.length() == 0) {
                return "(char) 0";
            } else {
                char ch = s.charAt(0);
                // this trick avoids escaping issues
                return "((char) " + (int) ch + ")";
            }
        }
    }

    public static String coerceToCharacter(String s, boolean isNamedAttribute) {
        if (isNamedAttribute) {
            return "(java.lang.Character) org.apache.jasper.runtime.JspRuntimeLibrary.coerce("
                    + s + ", java.lang.Character.class)";
        } else {
            if (s == null || s.length() == 0) {
                return "java.lang.Character.valueOf((char) 0)";
            } else {
                char ch = s.charAt(0);
                // this trick avoids escaping issues
                return "java.lang.Character.valueOf((char) " + (int) ch + ")";
            }
        }
    }

    public static String coerceToPrimitiveDouble(String s,
            boolean isNamedAttribute) {
        if (isNamedAttribute) {
            return "org.apache.jasper.runtime.JspRuntimeLibrary.coerceToDouble("
                    + s + ")";
        } else {
            if (s == null || s.length() == 0) {
                return "(double) 0";
            } else {
                return Double.valueOf(s).toString();
            }
        }
    }

    public static String coerceToDouble(String s, boolean isNamedAttribute) {
        if (isNamedAttribute) {
            return "(java.lang.Double) org.apache.jasper.runtime.JspRuntimeLibrary.coerce("
                    + s + ", Double.class)";
        } else {
            if (s == null || s.length() == 0) {
                return "java.lang.Double.valueOf(0)";
            } else {
                // Detect format error at translation time
                return "java.lang.Double.valueOf(" + Generator.quote(s) + ")";
            }
        }
    }

    public static String coerceToPrimitiveFloat(String s,
            boolean isNamedAttribute) {
        if (isNamedAttribute) {
            return "org.apache.jasper.runtime.JspRuntimeLibrary.coerceToFloat("
                    + s + ")";
        } else {
            if (s == null || s.length() == 0) {
                return "(float) 0";
            } else {
                return Float.valueOf(s).toString() + "f";
            }
        }
    }

    public static String coerceToFloat(String s, boolean isNamedAttribute) {
        if (isNamedAttribute) {
            return "(java.lang.Float) org.apache.jasper.runtime.JspRuntimeLibrary.coerce("
                    + s + ", java.lang.Float.class)";
        } else {
            if (s == null || s.length() == 0) {
                return "java.lang.Float.valueOf(0)";
            } else {
                // Detect format error at translation time
                return "java.lang.Float.valueOf(" + Generator.quote(s) + ")";
            }
        }
    }

    public static String coerceToInt(String s, boolean isNamedAttribute) {
        if (isNamedAttribute) {
            return "org.apache.jasper.runtime.JspRuntimeLibrary.coerceToInt("
                    + s + ")";
        } else {
            if (s == null || s.length() == 0) {
                return "0";
            } else {
                return Integer.valueOf(s).toString();
            }
        }
    }

    public static String coerceToInteger(String s, boolean isNamedAttribute) {
        if (isNamedAttribute) {
            return "(java.lang.Integer) org.apache.jasper.runtime.JspRuntimeLibrary.coerce("
                    + s + ", java.lang.Integer.class)";
        } else {
            if (s == null || s.length() == 0) {
                return "java.lang.Integer.valueOf(0)";
            } else {
                // Detect format error at translation time
                return "java.lang.Integer.valueOf(" + Generator.quote(s) + ")";
            }
        }
    }

    public static String coerceToPrimitiveShort(String s,
            boolean isNamedAttribute) {
        if (isNamedAttribute) {
            return "org.apache.jasper.runtime.JspRuntimeLibrary.coerceToShort("
                    + s + ")";
        } else {
            if (s == null || s.length() == 0) {
                return "(short) 0";
            } else {
                return "((short) " + Short.valueOf(s).toString() + ")";
            }
        }
    }

    public static String coerceToShort(String s, boolean isNamedAttribute) {
        if (isNamedAttribute) {
            return "(java.lang.Short) org.apache.jasper.runtime.JspRuntimeLibrary.coerce("
                    + s + ", java.lang.Short.class)";
        } else {
            if (s == null || s.length() == 0) {
                return "java.lang.Short.valueOf((short) 0)";
            } else {
                // Detect format error at translation time
                return "java.lang.Short.valueOf(" + Generator.quote(s) + ")";
            }
        }
    }

    public static String coerceToPrimitiveLong(String s,
            boolean isNamedAttribute) {
        if (isNamedAttribute) {
            return "org.apache.jasper.runtime.JspRuntimeLibrary.coerceToLong("
                    + s + ")";
        } else {
            if (s == null || s.length() == 0) {
                return "(long) 0";
            } else {
                return Long.valueOf(s).toString() + "l";
            }
        }
    }

    public static String coerceToLong(String s, boolean isNamedAttribute) {
        if (isNamedAttribute) {
            return "(java.lang.Long) org.apache.jasper.runtime.JspRuntimeLibrary.coerce("
                    + s + ", java.lang.Long.class)";
        } else {
            if (s == null || s.length() == 0) {
                return "java.lang.Long.valueOf(0)";
            } else {
                // Detect format error at translation time
                return "java.lang.Long.valueOf(" + Generator.quote(s) + ")";
            }
        }
    }

    public static BufferedInputStream getInputStream(String fname, Jar jar,
            JspCompilationContext ctxt) throws IOException {

        InputStream in = null;

        if (jar != null) {
            String jarEntryName = fname.substring(1);
            in = jar.getInputStream(jarEntryName);
        } else {
            in = ctxt.getResourceAsStream(fname);
        }

        if (in == null) {
            throw new FileNotFoundException(Localizer.getMessage(
                    "jsp.error.file.not.found", fname));
        }

        return new BufferedInputStream(in, JSP_INPUT_STREAM_BUFFER_SIZE);
    }

    public static InputSource getInputSource(String fname, Jar jar, JspCompilationContext ctxt)
        throws IOException {
        InputSource source;
        if (jar != null) {
            String jarEntryName = fname.substring(1);
            source = new InputSource(jar.getInputStream(jarEntryName));
            source.setSystemId(jar.getURL(jarEntryName));
        } else {
            source = new InputSource(ctxt.getResourceAsStream(fname));
            source.setSystemId(ctxt.getResource(fname).toExternalForm());
        }
        return source;
    }

    /**
     * Gets the fully-qualified class name of the tag handler corresponding to
     * the given tag file path.
     *
     * @param path Tag file path
     * @param packageName The package name
     * @param urn The tag identifier
     * @param err Error dispatcher
     *
     * @return Fully-qualified class name of the tag handler corresponding to
     *         the given tag file path
     * @throws JasperException Failed to generate a class name for the tag
     */
    public static String getTagHandlerClassName(String path, String packageName, String urn,
            ErrorDispatcher err) throws JasperException {


        String className = null;
        int begin = 0;
        int index;

        index = path.lastIndexOf(".tag");
        if (index == -1) {
            err.jspError("jsp.error.tagfile.badSuffix", path);
        }

        // It's tempting to remove the ".tag" suffix here, but we can't.
        // If we remove it, the fully-qualified class name of this tag
        // could conflict with the package name of other tags.
        // For instance, the tag file
        // /WEB-INF/tags/foo.tag
        // would have fully-qualified class name
        // org.apache.jsp.tag.web.foo
        // which would conflict with the package name of the tag file
        // /WEB-INF/tags/foo/bar.tag

        index = path.indexOf(WEB_INF_TAGS);
        if (index != -1) {
            className = packageName + ".web.";
            begin = index + WEB_INF_TAGS.length();
        } else {
            index = path.indexOf(META_INF_TAGS);
            if (index != -1) {
                className = getClassNameBase(packageName, urn);
                begin = index + META_INF_TAGS.length();
            } else {
                err.jspError("jsp.error.tagfile.illegalPath", path);
            }
        }

        className += makeJavaPackage(path.substring(begin));

        return className;
    }

    private static String getClassNameBase(String packageName, String urn) {
        StringBuilder base =
                new StringBuilder(packageName + ".meta.");
        if (urn != null) {
            base.append(makeJavaPackage(urn));
            base.append('.');
        }
        return base.toString();
    }

    /**
     * Converts the given path to a Java package or fully-qualified class name
     *
     * @param path
     *            Path to convert
     *
     * @return Java package corresponding to the given path
     */
    public static final String makeJavaPackage(String path) {
        String classNameComponents[] = path.split("/");
        StringBuilder legalClassNames = new StringBuilder();
        for (String classNameComponent : classNameComponents) {
            if (classNameComponent.length() > 0) {
                if (legalClassNames.length() > 0) {
                    legalClassNames.append('.');
                }
                legalClassNames.append(makeJavaIdentifier(classNameComponent));
            }
        }
        return legalClassNames.toString();
    }

    /**
     * Converts the given identifier to a legal Java identifier
     *
     * @param identifier
     *            Identifier to convert
     *
     * @return Legal Java identifier corresponding to the given identifier
     */
    public static final String makeJavaIdentifier(String identifier) {
        return makeJavaIdentifier(identifier, true);
    }

    /**
     * Converts the given identifier to a legal Java identifier
     * to be used for JSP Tag file attribute names.
     *
     * @param identifier
     *            Identifier to convert
     *
     * @return Legal Java identifier corresponding to the given identifier
     */
    public static final String makeJavaIdentifierForAttribute(String identifier) {
        return makeJavaIdentifier(identifier, false);
    }

    /**
     * Converts the given identifier to a legal Java identifier.
     *
     * @param identifier
     *            Identifier to convert
     *
     * @return Legal Java identifier corresponding to the given identifier
     */
    private static String makeJavaIdentifier(String identifier,
            boolean periodToUnderscore) {
        StringBuilder modifiedIdentifier = new StringBuilder(identifier.length());
        if (!Character.isJavaIdentifierStart(identifier.charAt(0))) {
            modifiedIdentifier.append('_');
        }
        for (int i = 0; i < identifier.length(); i++) {
            char ch = identifier.charAt(i);
            if (Character.isJavaIdentifierPart(ch) &&
                    (ch != '_' || !periodToUnderscore)) {
                modifiedIdentifier.append(ch);
            } else if (ch == '.' && periodToUnderscore) {
                modifiedIdentifier.append('_');
            } else {
                modifiedIdentifier.append(mangleChar(ch));
            }
        }
        if (isJavaKeyword(modifiedIdentifier.toString())) {
            modifiedIdentifier.append('_');
        }
        return modifiedIdentifier.toString();
    }

    /**
     * Mangle the specified character to create a legal Java class name.
     * @param ch The character
     * @return the replacement character as a string
     */
    public static final String mangleChar(char ch) {
        char[] result = new char[5];
        result[0] = '_';
        result[1] = Character.forDigit((ch >> 12) & 0xf, 16);
        result[2] = Character.forDigit((ch >> 8) & 0xf, 16);
        result[3] = Character.forDigit((ch >> 4) & 0xf, 16);
        result[4] = Character.forDigit(ch & 0xf, 16);
        return new String(result);
    }

    /**
     * Test whether the argument is a Java keyword.
     * @param key The name
     * @return <code>true</code> if the name is a java identifier
     */
    public static boolean isJavaKeyword(String key) {
        int i = 0;
        int j = javaKeywords.length;
        while (i < j) {
            int k = (i + j) >>> 1;
            int result = javaKeywords[k].compareTo(key);
            if (result == 0) {
                return true;
            }
            if (result < 0) {
                i = k + 1;
            } else {
                j = k;
            }
        }
        return false;
    }

    static InputStreamReader getReader(String fname, String encoding,
            Jar jar, JspCompilationContext ctxt, ErrorDispatcher err)
            throws JasperException, IOException {

        return getReader(fname, encoding, jar, ctxt, err, 0);
    }

    static InputStreamReader getReader(String fname, String encoding,
            Jar jar, JspCompilationContext ctxt, ErrorDispatcher err, int skip)
            throws JasperException, IOException {

        InputStreamReader reader = null;
        InputStream in = getInputStream(fname, jar, ctxt);
        try {
            for (int i = 0; i < skip; i++) {
                in.read();
            }
        } catch (IOException ioe) {
            try {
                in.close();
            } catch (IOException e) {
                // Ignore
            }
            throw ioe;
        }
        try {
            reader = new InputStreamReader(in, encoding);
        } catch (UnsupportedEncodingException ex) {
            err.jspError("jsp.error.unsupported.encoding", encoding);
        }

        return reader;
    }

    /**
     * Handles taking input from TLDs 'java.lang.Object' -&gt;
     * 'java.lang.Object.class' 'int' -&gt; 'int.class' 'void' -&gt; 'Void.TYPE'
     * 'int[]' -&gt; 'int[].class'
     *
     * @param type The type from the TLD
     * @return the Java type
     */
    public static String toJavaSourceTypeFromTld(String type) {
        if (type == null || "void".equals(type)) {
            return "java.lang.Void.TYPE";
        }
        return type + ".class";
    }

    /**
     * Class.getName() return arrays in the form "[[[&lt;et&gt;", where et, the
     * element type can be one of ZBCDFIJS or L&lt;classname&gt;;. It is
     * converted into forms that can be understood by javac.
     * @param type the type to convert
     * @return the equivalent type in Java sources
     */
    public static String toJavaSourceType(String type) {

        if (type.charAt(0) != '[') {
            return type;
        }

        int dims = 1;
        String t = null;
        for (int i = 1; i < type.length(); i++) {
            if (type.charAt(i) == '[') {
                dims++;
            } else {
                switch (type.charAt(i)) {
                case 'Z': t = "boolean"; break;
                case 'B': t = "byte"; break;
                case 'C': t = "char"; break;
                case 'D': t = "double"; break;
                case 'F': t = "float"; break;
                case 'I': t = "int"; break;
                case 'J': t = "long"; break;
                case 'S': t = "short"; break;
                case 'L': t = type.substring(i+1, type.indexOf(';')); break;
                }
                break;
            }
        }

        if (t == null) {
            // Should never happen
            throw new IllegalArgumentException(Localizer.getMessage("jsp.error.unable.getType", type));
        }

        StringBuilder resultType = new StringBuilder(t);
        for (; dims > 0; dims--) {
            resultType.append("[]");
        }
        return resultType.toString();
    }
}
