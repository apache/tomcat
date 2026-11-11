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
package org.apache.jasper.compiler.tagplugin;


/**
 * This interface allows the plugin author to make inquiries about the properties of the current tag, and to use Jasper
 * resources to generate direct Java codes in place of tag handler invocations.
 */

public interface TagPluginContext {
    /**
     * Checks whether the body of the current tag is scriptless.
     *
     * @return true if the body of the tag is scriptless
     */
    boolean isScriptless();

    /**
     * Checks whether the current tag has a body. A tag whose only children are <code>jsp:attribute</code> actions, or
     * whose <code>jsp:body</code> is empty, has no body. A body that contains only whitespace is still a body.
     *
     * @return true if the tag has a body, false if the body is empty. The default implementation returns true, which
     *             is always safe for a plugin that uses the query to skip work that is only needed for a body.
     */
    default boolean hasBody() {
        return true;
    }

    /**
     * Checks whether the specified attribute is present in the current tag.
     *
     * @param attribute Name of the attribute
     * @return true if the attribute is specified in the tag
     */
    boolean isAttributeSpecified(String attribute);

    /**
     * Generates a unique temporary variable name for use in generated code.
     *
     * @return A unique temporary variable name that the plugin can use
     */
    String getTemporaryVariableName();

    /**
     * Generate an import statement
     *
     * @param s Name of the import class, '*' allowed.
     */
    void generateImport(String s);

    /**
     * Generate a declaration in the of the generated class. This can be used to declare an inner class, a method, or a
     * class variable.
     *
     * @param id   A unique ID identifying the declaration. It is not part of the declaration, and is used to ensure
     *                 that the declaration will only appear once. If this method is invoked with the same id more than
     *                 once in the translation unit, only the first declaration will be taken.
     * @param text The text of the declaration.
     */
    void generateDeclaration(String id, String text);

    /**
     * Generate Java source code scriptlet
     *
     * @param s the scriptlet (raw Java source)
     */
    void generateJavaSource(String s);

    /**
     * Checks whether the specified attribute has a translation-time constant value.
     *
     * @param attribute The attribute name
     * @return true if the attribute is specified and its value is a translation-time constant
     */
    boolean isConstantAttribute(String attribute);

    /**
     * Returns the value of a constant attribute.
     *
     * @param attribute The attribute name
     * @return A string that is the value of a constant attribute. Undefined if the attribute is not a
     *             (translation-time) constant. null if the attribute is not specified
     */
    String getConstantAttribute(String attribute);

    /**
     * Generate codes to evaluate value of an attribute in the custom tag The codes is a Java expression. NOTE:
     * Currently cannot handle attributes that are fragments.
     *
     * @param attribute The specified attribute
     */
    void generateAttribute(String attribute);

    /**
     * Generate codes for the body of the custom tag
     */
    void generateBody();

    /**
     * Abandon optimization for this tag handler, and instruct Jasper to generate the tag handler calls, as usual.
     * Should be invoked if errors are detected, or when the tag body is deemed too complicated for optimization.
     */
    void dontUseTagPlugin();

    /**
     * Get the PluginContext for the parent of this custom tag. NOTE: The operations available for PluginContext so
     * obtained is limited to getPluginAttribute and setPluginAttribute, and queries (e.g. isScriptless()). There should
     * be no calls to generate*().
     *
     * @return The pluginContext for the parent node. null if the parent is not a custom tag, if the parent custom tag
     *             has no plugin context, or if the tag associated with this context has abandoned the plugin, in which
     *             case the body is not generated inline and the scopes of the generated code are disconnected.
     */
    TagPluginContext getParentContext();

    /**
     * Get the PluginContext of the closest enclosing custom tag that has one, optionally looking beyond tags whose
     * body is generated by the standard tag handler mechanism. The same restrictions as for
     * {@link #getParentContext()} apply to the context obtained.
     * <p>
     * Tags that abandoned their plugin are not skipped. Their body is generated in a separate method of the
     * generated servlet, so local variables of outer tags are out of scope inside it and the result must not be used
     * to share such variables. It is suitable for finding a tag that must be told to abandon its plugin.
     *
     * @param crossTagHandlers {@code true} to continue past tags that abandoned their plugin
     *
     * @return the pluginContext of the closest enclosing tag with a plugin, or {@code null} if there is none
     */
    default TagPluginContext getParentContext(boolean crossTagHandlers) {
        return getParentContext();
    }

    /**
     * Associate the attribute with a value in the current tagplugin context. The plugin attributes can be used for
     * communication among tags that must work together as a group. See &lt;c:when&gt; for an example.
     *
     * @param attr  The attribute name
     * @param value The attribute value
     */
    void setPluginAttribute(String attr, Object value);

    /**
     * Get the value of an attribute in the current tagplugin context.
     *
     * @param attr The attribute name
     *
     * @return the attribute value
     */
    Object getPluginAttribute(String attr);

    /**
     * Is the tag being used inside a tag file?
     *
     * @return <code>true</code> if inside a tag file
     */
    boolean isTagFile();

    /**
     * Generate the code to terminate the current page.
     */
    default void generatePageTermination() {
        throw new UnsupportedOperationException();
    }
}

