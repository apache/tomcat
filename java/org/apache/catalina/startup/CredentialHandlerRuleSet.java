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
import org.apache.tomcat.util.digester.RuleSetBase;

/**
 * <p><strong>RuleSet</strong> for processing the contents of a
 * CredentialHandler definition element.  This <code>RuleSet</code> supports
 * CredentialHandler such as the <code>NestedCredentialHandler</code> that used
 * nested CredentialHandlers.</p>
 */
public class CredentialHandlerRuleSet extends RuleSetBase {


    private static final int MAX_NESTED_LEVELS = Integer.getInteger(
            "org.apache.catalina.startup.CredentialHandlerRuleSet.MAX_NESTED_LEVELS",
            3).intValue();

    // ----------------------------------------------------- Instance Variables


    /**
     * The matching pattern prefix to use for recognizing our elements.
     */
    protected final String prefix;


    // ------------------------------------------------------------ Constructor


    /**
     * Construct an instance of this <code>RuleSet</code> with the default
     * matching pattern prefix.
     */
    public CredentialHandlerRuleSet() {
        this("");
    }


    /**
     * Construct an instance of this <code>RuleSet</code> with the specified
     * matching pattern prefix.
     *
     * @param prefix Prefix for matching pattern rules (including the
     *  trailing slash character)
     */
    public CredentialHandlerRuleSet(String prefix) {
        this.namespaceURI = null;
        this.prefix = prefix;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * <p>Add the set of Rule instances defined in this RuleSet to the
     * specified <code>Digester</code> instance, associating them with
     * our namespace URI (if any).  This method should only be called
     * by a Digester instance.</p>
     *
     * @param digester Digester instance to which the new Rule instances
     *  should be added.
     */
    @Override
    public void addRuleInstances(Digester digester) {

        String pattern = prefix;

        for (int i = 0; i < MAX_NESTED_LEVELS; i++) {

            if (i > 0) {
                pattern += "/";
            }
            pattern += "CredentialHandler";

            digester.addObjectCreate(pattern,
                                     null, // MUST be specified in the element,
                                     "className");
            digester.addSetProperties(pattern);
            if (i == 0) {
                digester.addSetNext(pattern,
                                    "setCredentialHandler",
                                    "org.apache.catalina.CredentialHandler");
            } else {
                digester.addSetNext(pattern,
                                    "addCredentialHandler",
                                    "org.apache.catalina.CredentialHandler");
            }
        }
    }
}
