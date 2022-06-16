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


import java.util.HashMap;
import java.util.Set;

import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.digester.ObjectCreateRule;
import org.apache.tomcat.util.res.StringManager;
import org.xml.sax.Attributes;


/**
 * Rule implementation that creates a server listener.
 */
public class ListenerCreateRule extends ObjectCreateRule {

    private static final Log log = LogFactory.getLog(ListenerCreateRule.class);
    protected static final StringManager sm = StringManager.getManager(ListenerCreateRule.class);

    public ListenerCreateRule(String className, String attributeName) {
        super(className, attributeName);
    }

    @Override
    public void begin(String namespace, String name, Attributes attributes)
            throws Exception {
        if ("true".equals(attributes.getValue("optional"))) {
            try {
                super.begin(namespace, name, attributes);
            } catch (Exception e) {
                String className = getRealClassName(attributes);
                if (log.isDebugEnabled()) {
                    log.info(sm.getString("listener.createFailed", className), e);
                } else {
                    log.info(sm.getString("listener.createFailed", className));
                }
                Object instance = new OptionalListener(className);
                digester.push(instance);
                StringBuilder code = digester.getGeneratedCode();
                if (code != null) {
                    code.append(OptionalListener.class.getName().replace('$', '.')).append(' ');
                    code.append(digester.toVariableName(instance)).append(" = new ");
                    code.append(OptionalListener.class.getName().replace('$', '.')).append("(\"").append(className).append("\");");
                    code.append(System.lineSeparator());
                }
            }
        } else {
            super.begin(namespace, name, attributes);
        }
    }

    public static class OptionalListener implements LifecycleListener {
        protected final String className;
        protected final HashMap<String, String> properties = new HashMap<>();
        public OptionalListener(String className) {
            this.className = className;
        }
        /**
         * @return the className
         */
        public String getClassName() {
            return className;
        }
        @Override
        public void lifecycleEvent(LifecycleEvent event) {
            // Do nothing
        }
        /**
         * Return a set of the property keys.
         * @return the set
         */
        public Set<String> getProperties() {
            return properties.keySet();
        }
        /**
         * Return a property from the protocol handler.
         *
         * @param name the property name
         * @return the property value
         */
        public Object getProperty(String name) {
            return properties.get(name);
        }
        /**
         * Set the given property.
         *
         * @param name the property name
         * @param value the property value
         * @return <code>true</code>
         */
        public boolean setProperty(String name, String value) {
            properties.put(name, value);
            return true;
        }
    }
}
