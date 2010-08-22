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


import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.digester.Digester;
import org.apache.tomcat.util.digester.RuleSetBase;
public class ClusterRuleSetFactory {
    
    public static final Log log = LogFactory.getLog(ClusterRuleSetFactory.class);
    
    public static RuleSetBase getClusterRuleSet(String prefix) {
        
        //OLD CLUSTER 1
        //first try the same classloader as this class server/lib
        try {
            return loadRuleSet(prefix,"org.apache.catalina.cluster.ClusterRuleSet",ClusterRuleSetFactory.class.getClassLoader());
        } catch ( Exception x ) {
            //display warning
            if ( log.isDebugEnabled() ) log.debug("Unable to load ClusterRuleSet (org.apache.catalina.cluster.ClusterRuleSet), falling back on context classloader");
        }
        //try to load it from the context class loader
        try {
            return loadRuleSet(prefix,"org.apache.catalina.cluster.ClusterRuleSet",Thread.currentThread().getContextClassLoader());
        } catch ( Exception x ) {
            //display warning
            if ( log.isDebugEnabled() ) log.debug("Unable to load ClusterRuleSet (org.apache.catalina.cluster.ClusterRuleSet), will try to load the HA cluster");
        }
        
        //NEW CLUSTER 2
        //first try the same classloader as this class server/lib
        try {
            return loadRuleSet(prefix,"org.apache.catalina.ha.ClusterRuleSet",ClusterRuleSetFactory.class.getClassLoader());
        } catch ( Exception x ) {
            //display warning
            if ( log.isDebugEnabled() ) log.debug("Unable to load HA ClusterRuleSet (org.apache.catalina.ha.ClusterRuleSet), falling back on context classloader");
        }
        //try to load it from the context class loader
        try {
            return loadRuleSet(prefix,"org.apache.catalina.ha.ClusterRuleSet",Thread.currentThread().getContextClassLoader());
        } catch ( Exception x ) {
            //display warning
            if ( log.isDebugEnabled() ) log.debug("Unable to load HA ClusterRuleSet (org.apache.catalina.ha.ClusterRuleSet), falling back on DefaultClusterRuleSet");
        }

        log.info("Unable to find a cluster rule set in the classpath. Will load the default rule set.");
        return new DefaultClusterRuleSet(prefix);
    }
    
    
    protected static RuleSetBase loadRuleSet(String prefix, String className, ClassLoader cl) 
        throws ClassNotFoundException, InstantiationException, 
               NoSuchMethodException,IllegalAccessException,
               InvocationTargetException {
        Class<?> clazz = Class.forName(className,true,cl);
        Constructor<?> cons = clazz.getConstructor(new Class[] {String.class});
        return (RuleSetBase)cons.newInstance(prefix);
    }
    
    /**
     * <p><strong>RuleSet</strong> for processing the contents of a
     * Cluster definition element.  </p>
     *
     * @author Filip Hanik
     * @author Peter Rossbach
     * @version $Id$
     */

    public static class DefaultClusterRuleSet extends RuleSetBase {


        // ----------------------------------------------------- Instance Variables


        /**
         * The matching pattern prefix to use for recognizing our elements.
         */
        protected String prefix = null;


        // ------------------------------------------------------------ Constructor


        /**
         * Construct an instance of this <code>RuleSet</code> with the default
         * matching pattern prefix.
         */
        public DefaultClusterRuleSet() {

            this("");

        }


        /**
         * Construct an instance of this <code>RuleSet</code> with the specified
         * matching pattern prefix.
         *
         * @param prefix Prefix for matching pattern rules (including the
         *  trailing slash character)
         */
        public DefaultClusterRuleSet(String prefix) {
            super();
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
            //Cluster configuration start
            digester.addObjectCreate(prefix + "Membership",
                                     null, // MUST be specified in the element
                                     "className");
            digester.addSetProperties(prefix + "Membership");
            digester.addSetNext(prefix + "Membership",
                                "setMembershipService",
                                "org.apache.catalina.cluster.MembershipService");

            digester.addObjectCreate(prefix + "Sender",
                                     null, // MUST be specified in the element
                                     "className");
            digester.addSetProperties(prefix + "Sender");
            digester.addSetNext(prefix + "Sender",
                                "setClusterSender",
                                "org.apache.catalina.cluster.ClusterSender");

            digester.addObjectCreate(prefix + "Receiver",
                                     null, // MUST be specified in the element
                                     "className");
            digester.addSetProperties(prefix + "Receiver");
            digester.addSetNext(prefix + "Receiver",
                                "setClusterReceiver",
                                "org.apache.catalina.cluster.ClusterReceiver");

            digester.addObjectCreate(prefix + "Valve",
                                     null, // MUST be specified in the element
                                     "className");
            digester.addSetProperties(prefix + "Valve");
            digester.addSetNext(prefix + "Valve",
                                "addValve",
                                "org.apache.catalina.Valve");

            digester.addObjectCreate(prefix + "Deployer",
                                     null, // MUST be specified in the element
                                     "className");
            digester.addSetProperties(prefix + "Deployer");
            digester.addSetNext(prefix + "Deployer",
                                "setClusterDeployer",
                                "org.apache.catalina.cluster.ClusterDeployer");

            digester.addObjectCreate(prefix + "Listener",
                    null, // MUST be specified in the element
                    "className");
            digester.addSetProperties(prefix + "Listener");
            digester.addSetNext(prefix + "Listener",
                                "addLifecycleListener",
                                "org.apache.catalina.LifecycleListener");

            digester.addObjectCreate(prefix + "ClusterListener",
                    null, // MUST be specified in the element
                    "className");
            digester.addSetProperties(prefix + "ClusterListener");
            digester.addSetNext(prefix + "ClusterListener",
                                "addClusterListener",
                                "org.apache.catalina.cluster.MessageListener");
            //Cluster configuration end
        }


    }
}
