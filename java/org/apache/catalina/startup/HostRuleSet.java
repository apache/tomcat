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
 * <strong>RuleSet</strong> for processing the contents of a Host definition element. This <code>RuleSet</code> does NOT
 * include any rules for nested Context which should be added via instances of <code>ContextRuleSet</code>.
 *
 * @author Craig R. McClanahan
 */
public class HostRuleSet implements RuleSet {

    // ----------------------------------------------------- Instance Variables

    /**
     * The matching pattern prefix to use for recognizing our elements.
     */
    protected final String prefix;


    // ------------------------------------------------------------ Constructor

    /**
     * Construct an instance of this <code>RuleSet</code> with the default matching pattern prefix.
     */
    public HostRuleSet() {
        this("");
    }


    /**
     * Construct an instance of this <code>RuleSet</code> with the specified matching pattern prefix.
     *
     * @param prefix Prefix for matching pattern rules (including the trailing slash character)
     */
    public HostRuleSet(String prefix) {
        this.prefix = prefix;
    }


    // --------------------------------------------------------- Public Methods

    @Override
    public void addRuleInstances(Digester digester) {

        digester.addObjectCreate(prefix + "Host", "org.apache.catalina.core.StandardHost", "className");
        digester.addSetProperties(prefix + "Host");
        digester.addRule(prefix + "Host", new CopyParentClassLoaderRule());
        digester.addRule(prefix + "Host",
                new LifecycleListenerRule("org.apache.catalina.startup.HostConfig", "hostConfigClass"));
        digester.addSetNext(prefix + "Host", "addChild", "org.apache.catalina.Container");

        digester.addCallMethod(prefix + "Host/Alias", "addAlias", 0);

        // Cluster configuration start
        digester.addObjectCreate(prefix + "Host/Cluster", null, // MUST be specified in the element
                "className");
        digester.addSetProperties(prefix + "Host/Cluster");
        digester.addSetNext(prefix + "Host/Cluster", "setCluster", "org.apache.catalina.Cluster");
        // Cluster configuration end

        digester.addObjectCreate(prefix + "Host/Listener", null, // MUST be specified in the element
                "className");
        digester.addSetProperties(prefix + "Host/Listener");
        digester.addSetNext(prefix + "Host/Listener", "addLifecycleListener", "org.apache.catalina.LifecycleListener");

        digester.addRuleSet(new RealmRuleSet(prefix + "Host/"));

        digester.addObjectCreate(prefix + "Host/Valve", null, // MUST be specified in the element
                "className");
        digester.addSetProperties(prefix + "Host/Valve");
        digester.addCallMethod(prefix + "Host/Valve/init-param", "addInitParam", 2);
        digester.addCallParam(prefix + "Host/Valve/init-param/param-name", 0);
        digester.addCallParam(prefix + "Host/Valve/init-param/param-value", 1);
        digester.addSetNext(prefix + "Host/Valve", "addValve", "org.apache.catalina.Valve");
    }
}
