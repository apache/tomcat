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
package jakarta.servlet.descriptor;

import java.util.Collection;

/**
 * Represents the JSP property groups in the deployment descriptors.
 *
 * @since Servlet 3.0
 */
public interface JspPropertyGroupDescriptor {

    /**
     * Obtain the patterns to which this group applies.
     *
     * @return the patterns to which this group applies
     */
    public Collection<String> getUrlPatterns();

    /**
     * Is Expression Language ignored for this group?
     *
     * @return {@code true} if EL is ignored, otherwise {@code false}
     */
    public String getElIgnored();

    /**
     * Will the use of an unknown identifier in EL within a JSP page trigger an
     * error for this group?
     *
     * @return {@code true} if an error will be triggered, otherwise {@code false}
     *
     * @since Servlet 6.0
     */
    public String getErrorOnELNotFound();

    /**
     * Obtain the page encoding for this group.
     *
     * @return the page encoding for this group
     */
    public String getPageEncoding();

    /**
     * Is scripting disabled for this group?
     *
     * @return {@code true} if scripting is disabled, otherwise {@code false}
     */
    public String getScriptingInvalid();

    /**
     * Should the JSPs in this group be treated as JSP documents?
     *
     * @return {@code true} if the JSPs should be treated as JSP documents,
     *         otherwise {@code false}
     */
    public String getIsXml();

    /**
     * Obtain the preludes to include for this group.
     *
     * @return the preludes to include for this group
     */
    public Collection<String> getIncludePreludes();

    /**
     * Obtain the codas to include for this group.
     *
     * @return the codas to include for this group.
     */
    public Collection<String> getIncludeCodas();

    /**
     * Is the deferred El syntax <code>#{...}</code> allowed to be used as a
     * literal in this group?
     *
     * @return {@code true} if the deferred EL syntax is allowed to be used as
     *         a literal, otherwise {@code false}
     */
    public String getDeferredSyntaxAllowedAsLiteral();

    /**
     * Should the JSPs in this group have template text that only contains
     * whitespace removed?
     *
     * @return {@code true} if the whitespace be removed, otherwise
     *         {@code false}
     */
    public String getTrimDirectiveWhitespaces();

    /**
     * Obtain the default content type this group of JSP pages.#
     *
     * @return the default content type this group of JSP pages
     */
    public String getDefaultContentType();

    /**
     * Obtain the per-page buffer configuration for this group of JSP pages.
     *
     * @return the per-page buffer configuration for this group of JSP pages
     */
    public String getBuffer();

    /**
     * Should an error be raised at translation time for a page in this group if
     * the page contains a reference (e.g. a tag) to a undeclared namespace.
     *
     * @return {@code true} if an error should be raised, otherwise
     *         {@code false}
     */
    public String getErrorOnUndeclaredNamespace();
}
