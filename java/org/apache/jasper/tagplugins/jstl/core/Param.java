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

import org.apache.jasper.compiler.tagplugin.TagPlugin;
import org.apache.jasper.compiler.tagplugin.TagPluginContext;

/**
 * Tag plugin for the JSTL {@code <c:param>} tag. This plugin generates code to append URL-encoded
 * name=value pairs to a URL string.
 */
public class Param implements TagPlugin {

    /**
     * Construct a new {@code Param} instance.
     */
    public Param() {
        // NO-OP
    }

    @Override
    public void doTag(TagPluginContext ctxt) {

        // don't support the body content

        // define names of all the temp variables
        String nameName = ctxt.getTemporaryVariableName();
        String valueName = ctxt.getTemporaryVariableName();
        String encName = ctxt.getTemporaryVariableName();
        String index = ctxt.getTemporaryVariableName();

        // if the param tag has no parents, throw an exception
        TagPluginContext parent = ctxt.getParentContext();
        if (parent == null) {
            ctxt.generateJavaSource(
                    " throw new JspTagException" + "(\"&lt;param&gt; outside &lt;import&gt; or &lt;urlEncode&gt;\");");
            return;
        }

        // Append the parameter to the working URL of the enclosing URL tag.
        // The Import, Url and Redirect plugins share the name of the local
        // variable holding that URL through the plugin context, so that
        // nested URL tags cannot interfere with each other through a shared
        // page scope attribute. As in the standard handler, which at runtime
        // adds the parameter to the closest URL tag ancestor, the search
        // walks up the plugin context chain until a context provides the
        // working URL, stopping at parents that no plugin handled.
        TagPluginContext urlContext = parent;
        while (urlContext != null &&
                urlContext.getPluginAttribute(Import.URL_VARIABLE_ATTRIBUTE) == null) {
            urlContext = urlContext.getParentContext();
        }
        if (urlContext == null) {
            // No plugin handled an enclosing URL tag, so there is no working
            // URL variable to append to. Let the standard tag handler process
            // the parameter. A standard URL tag then takes it as usual, and
            // the handler reports the error for a misplaced tag.
            ctxt.dontUseTagPlugin();
            return;
        }
        String urlTargetName = (String) urlContext.getPluginAttribute(Import.URL_VARIABLE_ATTRIBUTE);

        // get the value of "name"
        ctxt.generateJavaSource("String " + nameName + " = ");
        ctxt.generateAttribute("name");
        ctxt.generateJavaSource(";");

        // if the "name" is null then do nothing.
        // else add such string "name=value" to the url.
        // and the url should be encoded
        ctxt.generateJavaSource("if(" + nameName + " != null && !" + nameName + ".equals(\"\")){");
        ctxt.generateJavaSource("    String " + valueName + " = ");
        ctxt.generateAttribute("value");
        ctxt.generateJavaSource(";");
        ctxt.generateJavaSource("    if(" + valueName + " == null) " + valueName + " = \"\";");
        ctxt.generateJavaSource(
                "    String " + encName + " = _jspx_page_context.getResponse().getCharacterEncoding();");
        ctxt.generateJavaSource(
                "    " + nameName + " = java.net.URLEncoder.encode(" + nameName + ", " + encName + ");");
        ctxt.generateJavaSource(
                "    " + valueName + " = java.net.URLEncoder.encode(" + valueName + ", " + encName + ");");
        ctxt.generateJavaSource("    int " + index + ";");
        ctxt.generateJavaSource("    " + index + " = " + urlTargetName + ".indexOf(\'?\');");
        // if the current param is the first one, add a "?" ahead of it
        // else add a "&" ahead of it
        ctxt.generateJavaSource("    if(" + index + " == -1){");
        ctxt.generateJavaSource("        " + urlTargetName + " = " + urlTargetName + " + \"?\" + " + nameName +
                " + \"=\" + " + valueName + ";");
        ctxt.generateJavaSource("    }else{");
        ctxt.generateJavaSource("        " + urlTargetName + " = " + urlTargetName + " + \"&\" + " + nameName +
                " + \"=\" + " + valueName + ";");
        ctxt.generateJavaSource("    }");
        ctxt.generateJavaSource("}");
    }
}
