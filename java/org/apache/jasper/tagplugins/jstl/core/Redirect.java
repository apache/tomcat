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
 * Tag plugin for the JSTL &lt;c:redirect&gt; tag. Generates code to perform an HTTP redirect
 * to the specified URL with optional query parameters.
 */
public class Redirect implements TagPlugin {

    /**
     * Default constructor.
     */
    public Redirect() {
    }

    @Override
    public void doTag(TagPluginContext ctxt) {

        // flag for the existence of the "context"
        boolean hasContext = ctxt.isAttributeSpecified("context");

        // names of the temp variables
        String urlName = ctxt.getTemporaryVariableName();
        String contextName = ctxt.getTemporaryVariableName();
        String baseUrlName = ctxt.getTemporaryVariableName();
        String responseName = ctxt.getTemporaryVariableName();

        // get context
        ctxt.generateJavaSource("String " + contextName + " = null;");
        if (hasContext) {
            ctxt.generateJavaSource(contextName + " = ");
            ctxt.generateAttribute("context");
            ctxt.generateJavaSource(";");
        }

        // get the url
        ctxt.generateJavaSource("String " + urlName + " = ");
        ctxt.generateAttribute("url");
        ctxt.generateJavaSource(";");

        // get the raw url according to "url" and "context"
        ctxt.generateJavaSource("String " + baseUrlName + " = " + "org.apache.jasper.tagplugins.jstl.Util.resolveUrl(" +
                urlName + ", " + contextName + ", _jspx_page_context);");

        // Share the working URL with the <c:param> tags in the body by
        // handing them the name of the local variable that holds it.
        // Appending to the variable directly is re-entrant: a nested URL tag
        // in the body of this tag must not be able to overwrite or remove it,
        // which a page scope attribute with a fixed name would allow.
        ctxt.setPluginAttribute(Import.URL_VARIABLE_ATTRIBUTE, baseUrlName);

        // add params
        ctxt.generateBody();

        // get the response object
        ctxt.generateJavaSource("HttpServletResponse " + responseName + " = " +
                "((HttpServletResponse) _jspx_page_context.getResponse());");

        // if the url is relative, encode it
        ctxt.generateJavaSource("if(!org.apache.jasper.tagplugins.jstl.Util.isAbsoluteUrl(" + baseUrlName + ")){");
        ctxt.generateJavaSource("    " + baseUrlName + " = " + responseName + ".encodeRedirectURL(" + baseUrlName + ");");
        ctxt.generateJavaSource("}");

        // do redirect
        ctxt.generateJavaSource("try{");
        ctxt.generateJavaSource("    " + responseName + ".sendRedirect(" + baseUrlName + ");");
        // Avoid unreachable code error in compiler
        ctxt.generateJavaSource("    if (true) {");
        ctxt.generatePageTermination();
        ctxt.generateJavaSource("    }");
        ctxt.generateJavaSource("}catch(java.io.IOException ex){");
        ctxt.generateJavaSource("    throw new JspTagException(ex.toString(), ex);");
        ctxt.generateJavaSource("}");
    }
}
