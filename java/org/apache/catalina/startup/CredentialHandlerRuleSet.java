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
package org.apache.catalina.startup;

import org.apache.tomcat.util.digester.Digester;
import org.apache.tomcat.util.digester.RuleSet;

/**
 * <strong>RuleSet</strong> for processing the contents of a CredentialHandler definition element. This
 * <code>RuleSet</code> supports CredentialHandler such as the <code>NestedCredentialHandler</code> that used nested
 * CredentialHandlers.
 */
public class CredentialHandlerRuleSet implements RuleSet {


    private static final int MAX_NESTED_LEVELS =
            Integer.getInteger("org.apache.catalina.startup.CredentialHandlerRuleSet.MAX_NESTED_LEVELS", 3).intValue();

    // ----------------------------------------------------- Instance Variables


    /**
     * The matching pattern prefix to use for recognizing our elements.
     */
    protected final String prefix;


    // ------------------------------------------------------------ Constructor


    /**
     * Construct an instance of this <code>RuleSet</code> with the default matching pattern prefix.
     */
    public CredentialHandlerRuleSet() {
        this("");
    }


    /**
     * Construct an instance of this <code>RuleSet</code> with the specified matching pattern prefix.
     *
     * @param prefix Prefix for matching pattern rules (including the trailing slash character)
     */
    public CredentialHandlerRuleSet(String prefix) {
        this.prefix = prefix;
    }


    // --------------------------------------------------------- Public Methods


    @Override
    public void addRuleInstances(Digester digester) {
        StringBuilder pattern = new StringBuilder(prefix);
        for (int i = 0; i < MAX_NESTED_LEVELS; i++) {
            if (i > 0) {
                pattern.append('/');
            }
            pattern.append("CredentialHandler");
            addRuleInstances(digester, pattern.toString(), i == 0 ? "setCredentialHandler" : "addCredentialHandler");
        }
    }

    private void addRuleInstances(Digester digester, String pattern, String methodName) {
        digester.addObjectCreate(pattern, null /* MUST be specified in the element */, "className");
        digester.addSetProperties(pattern);
        digester.addSetNext(pattern, methodName, "org.apache.catalina.CredentialHandler");
    }
}
