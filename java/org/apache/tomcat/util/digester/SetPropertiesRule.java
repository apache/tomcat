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


package org.apache.tomcat.util.digester;


import java.util.HashMap;

import org.apache.tomcat.util.IntrospectionUtils;
import org.xml.sax.Attributes;


/**
 * <p>Rule implementation that sets properties on the object at the top of the
 * stack, based on attributes with corresponding names.</p>
 */

public class SetPropertiesRule extends Rule {

    public interface Listener {
        void endSetPropertiesRule();
    }

    protected final HashMap<String,String> excludes;

    public SetPropertiesRule() {
        excludes = null;
    }

    public SetPropertiesRule(String[] exclude) {
        excludes = new HashMap<>();
        for (String s : exclude) {
            if (s != null) {
                this.excludes.put(s, s);
            }
        }
    }

    /**
     * Process the beginning of this element.
     *
     * @param namespace the namespace URI of the matching element, or an
     *   empty string if the parser is not namespace aware or the element has
     *   no namespace
     * @param theName the local name if the parser is namespace aware, or just
     *   the element name otherwise
     * @param attributes The attribute list for this element
     */
    @Override
    public void begin(String namespace, String theName, Attributes attributes)
            throws Exception {

        // Populate the corresponding properties of the top object
        Object top = digester.peek();
        if (digester.log.isDebugEnabled()) {
            if (top != null) {
                digester.log.debug("[SetPropertiesRule]{" + digester.match +
                                   "} Set " + top.getClass().getName() +
                                   " properties");
            } else {
                digester.log.debug("[SetPropertiesRule]{" + digester.match +
                                   "} Set NULL properties");
            }
        }
        StringBuilder code = digester.getGeneratedCode();
        String variableName = null;
        if (code != null) {
            variableName = digester.toVariableName(top);
        }

        for (int i = 0; i < attributes.getLength(); i++) {
            String name = attributes.getLocalName(i);
            if (name.isEmpty()) {
                name = attributes.getQName(i);
            }
            String value = attributes.getValue(i);

            if (digester.log.isDebugEnabled()) {
                digester.log.debug("[SetPropertiesRule]{" + digester.match +
                        "} Setting property '" + name + "' to '" +
                        value + "'");
            }
            if (!digester.isFakeAttribute(top, name) && (excludes == null || !excludes.containsKey(name))) {
                StringBuilder actualMethod = null;
                if (code != null) {
                    actualMethod = new StringBuilder();
                }
                if (!IntrospectionUtils.setProperty(top, name, value, true, actualMethod)) {
                    if (digester.getRulesValidation() && !"optional".equals(name)) {
                        digester.log.warn(sm.getString("rule.noProperty", digester.match, name, value));
                    }
                } else {
                    if (code != null) {
                        code.append(variableName).append(".").append(actualMethod).append(';');
                        code.append(System.lineSeparator());
                    }
                }
            }
        }

        if (top instanceof Listener) {
            ((Listener) top).endSetPropertiesRule();
            if (code != null) {
                code.append("((org.apache.tomcat.util.digester.SetPropertiesRule.Listener) ");
                code.append(variableName).append(").endSetPropertiesRule();");
                code.append(System.lineSeparator());
            }
        }

    }


    /**
     * Render a printable version of this Rule.
     */
    @Override
    public String toString() {
        return "SetPropertiesRule[]";
    }
}
