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
 * <strong>RuleSet</strong> for processing the JNDI Enterprise Naming Context resource declaration elements.
 *
 * @author Craig R. McClanahan
 * @author Remy Maucherat
 */
public class NamingRuleSet implements RuleSet {

    // ----------------------------------------------------- Instance Variables

    /**
     * The matching pattern prefix to use for recognizing our elements.
     */
    protected final String prefix;


    // ------------------------------------------------------------ Constructor

    /**
     * Construct an instance of this <code>RuleSet</code> with the default matching pattern prefix.
     */
    public NamingRuleSet() {
        this("");
    }


    /**
     * Construct an instance of this <code>RuleSet</code> with the specified matching pattern prefix.
     *
     * @param prefix Prefix for matching pattern rules (including the trailing slash character)
     */
    public NamingRuleSet(String prefix) {
        this.prefix = prefix;
    }


    // --------------------------------------------------------- Public Methods

    @Override
    public void addRuleInstances(Digester digester) {

        digester.addObjectCreate(prefix + "Ejb", "org.apache.tomcat.util.descriptor.web.ContextEjb");
        digester.addSetProperties(prefix + "Ejb");
        digester.addRule(prefix + "Ejb",
                new SetNextNamingRule("addEjb", "org.apache.tomcat.util.descriptor.web.ContextEjb"));

        digester.addObjectCreate(prefix + "Environment", "org.apache.tomcat.util.descriptor.web.ContextEnvironment");
        digester.addSetProperties(prefix + "Environment");
        digester.addRule(prefix + "Environment",
                new SetNextNamingRule("addEnvironment", "org.apache.tomcat.util.descriptor.web.ContextEnvironment"));

        digester.addObjectCreate(prefix + "LocalEjb", "org.apache.tomcat.util.descriptor.web.ContextLocalEjb");
        digester.addSetProperties(prefix + "LocalEjb");
        digester.addRule(prefix + "LocalEjb",
                new SetNextNamingRule("addLocalEjb", "org.apache.tomcat.util.descriptor.web.ContextLocalEjb"));

        digester.addObjectCreate(prefix + "Resource", "org.apache.tomcat.util.descriptor.web.ContextResource");
        digester.addSetProperties(prefix + "Resource");
        digester.addRule(prefix + "Resource",
                new SetNextNamingRule("addResource", "org.apache.tomcat.util.descriptor.web.ContextResource"));

        digester.addObjectCreate(prefix + "ResourceEnvRef",
                "org.apache.tomcat.util.descriptor.web.ContextResourceEnvRef");
        digester.addSetProperties(prefix + "ResourceEnvRef");
        digester.addRule(prefix + "ResourceEnvRef", new SetNextNamingRule("addResourceEnvRef",
                "org.apache.tomcat.util.descriptor.web.ContextResourceEnvRef"));

        digester.addObjectCreate(prefix + "ServiceRef", "org.apache.tomcat.util.descriptor.web.ContextService");
        digester.addSetProperties(prefix + "ServiceRef");
        digester.addRule(prefix + "ServiceRef",
                new SetNextNamingRule("addService", "org.apache.tomcat.util.descriptor.web.ContextService"));

        digester.addObjectCreate(prefix + "Transaction", "org.apache.tomcat.util.descriptor.web.ContextTransaction");
        digester.addSetProperties(prefix + "Transaction");
        digester.addRule(prefix + "Transaction",
                new SetNextNamingRule("setTransaction", "org.apache.tomcat.util.descriptor.web.ContextTransaction"));
    }
}
