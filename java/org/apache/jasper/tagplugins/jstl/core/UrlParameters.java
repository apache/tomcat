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

import java.util.ArrayList;
import java.util.List;

import org.apache.jasper.compiler.tagplugin.TagPluginContext;

/**
 * Translation time protocol shared by the URL tag plugins and the {@link Param} plugin.
 * <p>
 * A URL tag plugin shares the name of the generated local variable that holds its working URL, and the name of a
 * second local variable that records where the parameters end, with the {@link Param} plugins in its body. The
 * parameters are inserted ahead of any query string that is already part of the URL, as the standard tag handler does.
 * <p>
 * A {@link Param} plugin can only append to those variables when it is generated in the same method as the URL tag. If
 * it cannot, the URL tag and all the parameters of the tag that were already generated inline are returned to the
 * standard tag handlers, which exchange parameters at runtime.
 */
final class UrlParameters {

    /**
     * Plugin attribute of a URL tag context: the name of the local variable that holds the working URL.
     */
    static final String URL_VARIABLE_ATTRIBUTE = "urlVariable";

    private static final String PARAM_END_ATTRIBUTE = "urlParamEnd";
    private static final String PARAMS_ATTRIBUTE = "urlParams";
    private static final String ABANDONED_ATTRIBUTE = "urlAbandoned";

    private UrlParameters() {
    }

    /**
     * Generate the body of a URL tag whose working URL is held in a local variable.
     *
     * @param ctxt    the context of the URL tag
     * @param urlName the name of the local variable that holds the working URL
     */
    static void generateBody(TagPluginContext ctxt, String urlName) {
        if (ctxt.hasBody()) {
            String paramEnd = ctxt.getTemporaryVariableName();
            // Index in the URL at which the next parameter is inserted; -1 until the first one is added.
            ctxt.generateJavaSource("int " + paramEnd + " = -1;");
            ctxt.setPluginAttribute(URL_VARIABLE_ATTRIBUTE, urlName);
            ctxt.setPluginAttribute(PARAM_END_ATTRIBUTE, paramEnd);
            ctxt.setPluginAttribute(PARAMS_ATTRIBUTE, new ArrayList<TagPluginContext>());
        }
        ctxt.generateBody();
    }

    /**
     * Find the context of the URL tag that a parameter generated inline can append to.
     *
     * @param param the context of the parameter
     *
     * @return the context of the URL tag, or {@code null} if the parameter cannot be generated inline
     */
    static TagPluginContext findUrlContext(TagPluginContext param) {
        TagPluginContext context = param.getParentContext();
        while (context != null) {
            if (context.getPluginAttribute(ABANDONED_ATTRIBUTE) != null) {
                return null;
            }
            if (context.getPluginAttribute(URL_VARIABLE_ATTRIBUTE) != null) {
                return context;
            }
            context = context.getParentContext();
        }
        return null;
    }

    static String getUrlVariable(TagPluginContext url) {
        return (String) url.getPluginAttribute(URL_VARIABLE_ATTRIBUTE);
    }

    static String getParamEndVariable(TagPluginContext url) {
        return (String) url.getPluginAttribute(PARAM_END_ATTRIBUTE);
    }

    @SuppressWarnings("unchecked")
    static void register(TagPluginContext url, TagPluginContext param) {
        ((List<TagPluginContext>) url.getPluginAttribute(PARAMS_ATTRIBUTE)).add(param);
    }

    /**
     * Return a parameter to the standard tag handler, together with the closest enclosing URL tag and the parameters
     * of that tag that were already generated inline. The standard handler of a parameter adds it to the closest
     * ancestor handler at runtime, which requires the URL tag to be a standard handler as well.
     *
     * @param param the context of the parameter
     */
    @SuppressWarnings("unchecked")
    static void fallBack(TagPluginContext param) {
        param.dontUseTagPlugin();
        TagPluginContext url = param.getParentContext(true);
        while (url != null && url.getPluginAttribute(URL_VARIABLE_ATTRIBUTE) == null) {
            url = url.getParentContext(true);
        }
        if (url == null || url.getPluginAttribute(ABANDONED_ATTRIBUTE) != null) {
            return;
        }
        url.setPluginAttribute(ABANDONED_ATTRIBUTE, Boolean.TRUE);
        url.dontUseTagPlugin();
        for (TagPluginContext inline : (List<TagPluginContext>) url.getPluginAttribute(PARAMS_ATTRIBUTE)) {
            inline.dontUseTagPlugin();
        }
    }
}
