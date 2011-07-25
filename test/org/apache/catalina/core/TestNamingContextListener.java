/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.catalina.core;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import org.apache.catalina.LifecycleState;
import org.apache.catalina.deploy.ContextEnvironment;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;

public class TestNamingContextListener extends TomcatBaseTest {

    private static final String JNDI_NAME = "TestName";
    private static final String JNDI_VALUE= "Test Value";

    /** 
     * Test JNDI is available to ServletContextListeners.
     */
    @Test
    public void testBug49132() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        
        // Must have a real docBase - just use temp
        org.apache.catalina.Context ctx = 
            tomcat.addContext("", System.getProperty("java.io.tmpdir"));
        
        // Enable JNDI - it is disabled by default
        tomcat.enableNaming();

        ContextEnvironment environment = new ContextEnvironment();
        environment.setType(JNDI_VALUE.getClass().getName());
        environment.setName(JNDI_NAME);
        environment.setValue(JNDI_VALUE);
        ctx.getNamingResources().addEnvironment(environment);
        
        ctx.addApplicationListener(Bug49132Listener.class.getName());
        
        tomcat.start();

        assertEquals(LifecycleState.STARTED, ctx.getState());
    }

    public static final class Bug49132Listener implements ServletContextListener {

        @Override
        public void contextDestroyed(ServletContextEvent sce) {
            // NOOP
        }

        @Override
        public void contextInitialized(ServletContextEvent sce) {
            javax.naming.Context initCtx;
            try {
                initCtx = new InitialContext();
                javax.naming.Context envCtx =
                    (javax.naming.Context) initCtx.lookup("java:comp/env");
                String value = (String) envCtx.lookup(JNDI_NAME);
                if (!JNDI_VALUE.equals(value)) {
                    throw new RuntimeException();
                }
            } catch (NamingException e) {
                throw new RuntimeException(e);
            }
        }
    }

}
