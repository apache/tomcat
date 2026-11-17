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
package org.apache.jasper.tagplugins.jstl.core;

import org.apache.jasper.compiler.tagplugin.TagPluginContext;

/**
 * Generates the validation of the <code>begin</code>, <code>end</code> and <code>step</code> loop control attributes
 * shared by the &lt;c:forEach&gt; and &lt;c:forTokens&gt; tag plugins. The checks and messages match the setters of the
 * reference implementation, <code>LoopTagSupport</code>.
 */
final class LoopAttributeChecks {

    private LoopAttributeChecks() {
        // Utility class. Hide default constructor
    }

    /**
     * Generates a check that throws a <code>JspTagException</code> if the named <code>begin</code> variable is
     * negative.
     *
     * @param ctxt The tag plugin context
     * @param variable The name of the generated variable holding the value of the attribute
     */
    static void generateBeginCheck(TagPluginContext ctxt, String variable) {
        generateCheck(ctxt, variable, "<", "'begin' < 0");
    }

    /**
     * Generates a check that throws a <code>JspTagException</code> if the named <code>end</code> variable is negative.
     *
     * @param ctxt The tag plugin context
     * @param variable The name of the generated variable holding the value of the attribute
     */
    static void generateEndCheck(TagPluginContext ctxt, String variable) {
        generateCheck(ctxt, variable, "<", "'end' < 0");
    }

    /**
     * Generates a check that throws a <code>JspTagException</code> if the named <code>step</code> variable is not
     * positive.
     *
     * @param ctxt The tag plugin context
     * @param variable The name of the generated variable holding the value of the attribute
     */
    static void generateStepCheck(TagPluginContext ctxt, String variable) {
        generateCheck(ctxt, variable, "<=", "'step' <= 0");
    }

    private static void generateCheck(TagPluginContext ctxt, String variable, String operator, String message) {
        ctxt.generateJavaSource("if (" + variable + " " + operator + " 0) {" +
                "throw new jakarta.servlet.jsp.JspTagException(\"" + message + "\");}");
    }
}
