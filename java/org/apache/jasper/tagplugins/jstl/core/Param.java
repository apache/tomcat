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

        // Append the parameter to the working URL of the enclosing URL tag.
        // The URL plugins share the names of the local variables holding
        // that URL with the Param plugins in their bodies, so that nested URL
        // tags cannot interfere with each other. As in the standard handler,
        // which at runtime adds the parameter to the closest URL tag
        // ancestor, the search walks up the plugin context chain until a
        // context provides the working URL. It stops at tags that no plugin
        // handled, since their bodies are generated in separate methods where
        // the local variables are out of scope.
        TagPluginContext urlContext = UrlParameters.findUrlContext(ctxt);
        if (urlContext == null) {
            // The parameter cannot be appended inline. Let the standard tag
            // handler process it, and return the enclosing URL tag to its
            // standard handler as well, since only that can receive it. If
            // there is no URL tag, the standard handler reports the misplaced
            // tag.
            UrlParameters.fallBack(ctxt);
            return;
        }
        UrlParameters.register(urlContext, ctxt);
        String urlTargetName = UrlParameters.getUrlVariable(urlContext);
        String paramEnd = UrlParameters.getParamEndVariable(urlContext);

        // define names of all the temp variables
        String nameName = ctxt.getTemporaryVariableName();
        String valueName = ctxt.getTemporaryVariableName();
        String encName = ctxt.getTemporaryVariableName();
        String index = ctxt.getTemporaryVariableName();
        String paramName = ctxt.getTemporaryVariableName();

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
        ctxt.generateJavaSource("    String " + paramName + " = java.net.URLEncoder.encode(" + nameName + ", " +
                encName + ") + \"=\" + java.net.URLEncoder.encode(" + valueName + ", " + encName + ");");
        // The parameters precede any query string already in the URL. The
        // first one is inserted after the "?", or appended with a new "?" if
        // there is no query string. Later ones follow the previous parameter.
        ctxt.generateJavaSource("    if(" + paramEnd + " < 0){");
        ctxt.generateJavaSource("        int " + index + " = " + urlTargetName + ".indexOf('?');");
        ctxt.generateJavaSource("        if(" + index + " == -1){");
        ctxt.generateJavaSource("            " + urlTargetName + " = " + urlTargetName + " + \"?\" + " + paramName +
                ";");
        ctxt.generateJavaSource("            " + paramEnd + " = " + urlTargetName + ".length();");
        ctxt.generateJavaSource("        }else{");
        ctxt.generateJavaSource("            " + urlTargetName + " = " + urlTargetName + ".substring(0, " + index +
                " + 1) + " + paramName + " + \"&\" + " + urlTargetName + ".substring(" + index + " + 1);");
        ctxt.generateJavaSource("            " + paramEnd + " = " + index + " + 1 + " + paramName + ".length();");
        ctxt.generateJavaSource("        }");
        ctxt.generateJavaSource("    }else{");
        ctxt.generateJavaSource("        " + urlTargetName + " = " + urlTargetName + ".substring(0, " + paramEnd +
                ") + \"&\" + " + paramName + " + " + urlTargetName + ".substring(" + paramEnd + ");");
        ctxt.generateJavaSource("        " + paramEnd + " += 1 + " + paramName + ".length();");
        ctxt.generateJavaSource("    }");
        ctxt.generateJavaSource("}");
    }
}
