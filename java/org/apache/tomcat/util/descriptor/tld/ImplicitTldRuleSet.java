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
package org.apache.tomcat.util.descriptor.tld;

import org.apache.tomcat.util.digester.Digester;
import org.apache.tomcat.util.digester.Rule;
import org.apache.tomcat.util.digester.RuleSet;
import org.apache.tomcat.util.res.StringManager;
import org.xml.sax.Attributes;

/**
 * RulesSet for digesting implicit.tld files.
 *
 * Only version information used and short names are allowed.
 */
public class ImplicitTldRuleSet implements RuleSet {

    private static final StringManager sm = StringManager.getManager(ImplicitTldRuleSet.class);

    private static final String PREFIX = "taglib";
    private static final String VALIDATOR_PREFIX = PREFIX + "/validator";
    private static final String TAG_PREFIX = PREFIX + "/tag";
    private static final String TAGFILE_PREFIX = PREFIX + "/tag-file";
    private static final String FUNCTION_PREFIX = PREFIX + "/function";


    @Override
    public void addRuleInstances(Digester digester) {

        digester.addCallMethod(PREFIX + "/tlibversion", "setTlibVersion", 0);
        digester.addCallMethod(PREFIX + "/tlib-version", "setTlibVersion", 0);
        digester.addCallMethod(PREFIX + "/jspversion", "setJspVersion", 0);
        digester.addCallMethod(PREFIX + "/jsp-version", "setJspVersion", 0);
        digester.addRule(PREFIX, new Rule() {
            // for TLD 2.0 and later, jsp-version is set by version attribute
            @Override
            public void begin(String namespace, String name, Attributes attributes) {
                TaglibXml taglibXml = (TaglibXml) digester.peek();
                taglibXml.setJspVersion(attributes.getValue("version"));

                StringBuilder code = digester.getGeneratedCode();
                if (code != null) {
                    code.append(digester.toVariableName(taglibXml)).append(".setJspVersion(\"");
                    code.append(attributes.getValue("version")).append("\");");
                    code.append(System.lineSeparator());
                }
            }
        });
        digester.addCallMethod(PREFIX + "/shortname", "setShortName", 0);
        digester.addCallMethod(PREFIX + "/short-name", "setShortName", 0);

        // Elements not permitted
        digester.addRule(PREFIX + "/uri", new ElementNotAllowedRule());
        digester.addRule(PREFIX + "/info", new ElementNotAllowedRule());
        digester.addRule(PREFIX + "/description", new ElementNotAllowedRule());
        digester.addRule(PREFIX + "/listener/listener-class", new ElementNotAllowedRule());

        digester.addRule(VALIDATOR_PREFIX, new ElementNotAllowedRule());
        digester.addRule(TAG_PREFIX, new ElementNotAllowedRule());
        digester.addRule(TAGFILE_PREFIX, new ElementNotAllowedRule());
        digester.addRule(FUNCTION_PREFIX, new ElementNotAllowedRule());
    }


    private static class ElementNotAllowedRule extends Rule {
        @Override
        public void begin(String namespace, String name, Attributes attributes) throws Exception {
            throw new IllegalArgumentException(
                    ImplicitTldRuleSet.sm.getString("implicitTldRule.elementNotAllowed", name));
        }
    }
}
