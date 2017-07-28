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
 * <p><strong>RuleSet</strong> for processing the contents of a
 * Context definition element.</p>
 *
 * @author Craig R. McClanahan
 */
public class ContextRuleSet implements RuleSet {

    // ----------------------------------------------------- Instance Variables

    /**
     * The matching pattern prefix to use for recognizing our elements.
     */
    protected final String prefix;


    /**
     * Should the context be created.
     */
    protected final boolean create;


    // ------------------------------------------------------------ Constructor

    /**
     * Construct an instance of this <code>RuleSet</code> with the default
     * matching pattern prefix.
     */
    public ContextRuleSet() {
        this("");
    }


    /**
     * Construct an instance of this <code>RuleSet</code> with the specified
     * matching pattern prefix.
     *
     * @param prefix Prefix for matching pattern rules (including the
     *  trailing slash character)
     */
    public ContextRuleSet(String prefix) {
        this(prefix, true);
    }


    /**
     * Construct an instance of this <code>RuleSet</code> with the specified
     * matching pattern prefix.
     *
     * @param prefix Prefix for matching pattern rules (including the
     *  trailing slash character)
     * @param create <code>true</code> if the main context instance should be
     *  created
     */
    public ContextRuleSet(String prefix, boolean create) {
        this.prefix = prefix;
        this.create = create;
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

        if (create) {
            digester.addObjectCreate(prefix + "Context",
                    "org.apache.catalina.core.StandardContext", "className");
            digester.addSetProperties(prefix + "Context");
        } else {
            digester.addRule(prefix + "Context", new SetContextPropertiesRule());
        }

        if (create) {
            digester.addRule(prefix + "Context",
                             new LifecycleListenerRule
                                 ("org.apache.catalina.startup.ContextConfig",
                                  "configClass"));
            digester.addSetNext(prefix + "Context",
                                "addChild",
                                "org.apache.catalina.Container");
        }

        digester.addObjectCreate(prefix + "Context/Listener",
                                 null, // MUST be specified in the element
                                 "className");
        digester.addSetProperties(prefix + "Context/Listener");
        digester.addSetNext(prefix + "Context/Listener",
                            "addLifecycleListener",
                            "org.apache.catalina.LifecycleListener");

        digester.addObjectCreate(prefix + "Context/Loader",
                            "org.apache.catalina.loader.WebappLoader",
                            "className");
        digester.addSetProperties(prefix + "Context/Loader");
        digester.addSetNext(prefix + "Context/Loader",
                            "setLoader",
                            "org.apache.catalina.Loader");

        digester.addObjectCreate(prefix + "Context/Manager",
                                 "org.apache.catalina.session.StandardManager",
                                 "className");
        digester.addSetProperties(prefix + "Context/Manager");
        digester.addSetNext(prefix + "Context/Manager",
                            "setManager",
                            "org.apache.catalina.Manager");

        digester.addObjectCreate(prefix + "Context/Manager/Store",
                                 null, // MUST be specified in the element
                                 "className");
        digester.addSetProperties(prefix + "Context/Manager/Store");
        digester.addSetNext(prefix + "Context/Manager/Store",
                            "setStore",
                            "org.apache.catalina.Store");

        digester.addObjectCreate(prefix + "Context/Manager/SessionIdGenerator",
                                 "org.apache.catalina.util.StandardSessionIdGenerator",
                                 "className");
        digester.addSetProperties(prefix + "Context/Manager/SessionIdGenerator");
        digester.addSetNext(prefix + "Context/Manager/SessionIdGenerator",
                            "setSessionIdGenerator",
                            "org.apache.catalina.SessionIdGenerator");

        digester.addObjectCreate(prefix + "Context/Parameter",
                                 "org.apache.tomcat.util.descriptor.web.ApplicationParameter");
        digester.addSetProperties(prefix + "Context/Parameter");
        digester.addSetNext(prefix + "Context/Parameter",
                            "addApplicationParameter",
                            "org.apache.tomcat.util.descriptor.web.ApplicationParameter");

        digester.addRuleSet(new RealmRuleSet(prefix + "Context/"));

        digester.addObjectCreate(prefix + "Context/Resources",
                                 "org.apache.catalina.webresources.StandardRoot",
                                 "className");
        digester.addSetProperties(prefix + "Context/Resources");
        digester.addSetNext(prefix + "Context/Resources",
                            "setResources",
                            "org.apache.catalina.WebResourceRoot");

        digester.addObjectCreate(prefix + "Context/Resources/PreResources",
                                 null, // MUST be specified in the element
                                 "className");
        digester.addSetProperties(prefix + "Context/Resources/PreResources");
        digester.addSetNext(prefix + "Context/Resources/PreResources",
                            "addPreResources",
                            "org.apache.catalina.WebResourceSet");

        digester.addObjectCreate(prefix + "Context/Resources/JarResources",
                                 null, // MUST be specified in the element
                                 "className");
        digester.addSetProperties(prefix + "Context/Resources/JarResources");
        digester.addSetNext(prefix + "Context/Resources/JarResources",
                            "addJarResources",
                            "org.apache.catalina.WebResourceSet");

        digester.addObjectCreate(prefix + "Context/Resources/PostResources",
                                 null, // MUST be specified in the element
                                 "className");
        digester.addSetProperties(prefix + "Context/Resources/PostResources");
        digester.addSetNext(prefix + "Context/Resources/PostResources",
                            "addPostResources",
                            "org.apache.catalina.WebResourceSet");


        digester.addObjectCreate(prefix + "Context/ResourceLink",
                "org.apache.tomcat.util.descriptor.web.ContextResourceLink");
        digester.addSetProperties(prefix + "Context/ResourceLink");
        digester.addRule(prefix + "Context/ResourceLink",
                new SetNextNamingRule("addResourceLink",
                        "org.apache.tomcat.util.descriptor.web.ContextResourceLink"));

        digester.addObjectCreate(prefix + "Context/Valve",
                                 null, // MUST be specified in the element
                                 "className");
        digester.addSetProperties(prefix + "Context/Valve");
        digester.addSetNext(prefix + "Context/Valve",
                            "addValve",
                            "org.apache.catalina.Valve");

        digester.addCallMethod(prefix + "Context/WatchedResource",
                               "addWatchedResource", 0);

        digester.addCallMethod(prefix + "Context/WrapperLifecycle",
                               "addWrapperLifecycle", 0);

        digester.addCallMethod(prefix + "Context/WrapperListener",
                               "addWrapperListener", 0);

        digester.addObjectCreate(prefix + "Context/JarScanner",
                                 "org.apache.tomcat.util.scan.StandardJarScanner",
                                 "className");
        digester.addSetProperties(prefix + "Context/JarScanner");
        digester.addSetNext(prefix + "Context/JarScanner",
                            "setJarScanner",
                            "org.apache.tomcat.JarScanner");

        digester.addObjectCreate(prefix + "Context/JarScanner/JarScanFilter",
                                 "org.apache.tomcat.util.scan.StandardJarScanFilter",
                                 "className");
        digester.addSetProperties(prefix + "Context/JarScanner/JarScanFilter");
        digester.addSetNext(prefix + "Context/JarScanner/JarScanFilter",
                            "setJarScanFilter",
                            "org.apache.tomcat.JarScanFilter");

        digester.addObjectCreate(prefix + "Context/CookieProcessor",
                                 "org.apache.tomcat.util.http.Rfc6265CookieProcessor",
                                 "className");
        digester.addSetProperties(prefix + "Context/CookieProcessor");
        digester.addSetNext(prefix + "Context/CookieProcessor",
                            "setCookieProcessor",
                            "org.apache.tomcat.util.http.CookieProcessor");
    }
}
