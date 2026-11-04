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
import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import javax.naming.RefAddr;
import javax.naming.Reference;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.Host;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.deploy.NamingResourcesImpl;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.descriptor.web.ContextEnvironment;
import org.apache.tomcat.util.descriptor.web.ContextResource;

public class TestNamingContextListener extends TomcatBaseTest {

    private static final String BUG49132_NAME = "TestName";
    private static final String BUG49132_VALUE = "Test Value";

    private static final String BUG54096_NameA = "envA";
    private static final String BUG54096_ValueA = "valueA";
    private static final String BUG54096_NameB = "envB";
    private static final String BUG54096_ValueB = "B";

    /*
     * Test JNDI is available to ServletContextListeners.
     */
    @Test
    public void testBug49132() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = getProgrammaticRootContext();

        // Enable JNDI - it is disabled by default
        tomcat.enableNaming();

        ContextEnvironment environment = new ContextEnvironment();
        environment.setType(BUG49132_VALUE.getClass().getName());
        environment.setName(BUG49132_NAME);
        environment.setValue(BUG49132_VALUE);
        ctx.getNamingResources().addEnvironment(environment);

        ctx.addApplicationListener(Bug49132Listener.class.getName());

        tomcat.start();

        Assert.assertEquals(LifecycleState.STARTED, ctx.getState());
    }

    public static final class Bug49132Listener implements ServletContextListener {

        @Override
        public void contextInitialized(ServletContextEvent sce) {
            javax.naming.Context initCtx;
            try {
                initCtx = new InitialContext();
                javax.naming.Context envCtx = (javax.naming.Context) initCtx.lookup("java:comp/env");
                String value = (String) envCtx.lookup(BUG49132_NAME);
                if (!BUG49132_VALUE.equals(value)) {
                    throw new RuntimeException();
                }
            } catch (NamingException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Test
    public void testBug54096() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = getProgrammaticRootContext();

        // Enable JNDI - it is disabled by default
        tomcat.enableNaming();

        ContextEnvironment environmentA = new ContextEnvironment();
        environmentA.setType(Bug54096EnvA.class.getName());
        environmentA.setName(BUG54096_NameA);
        environmentA.setValue(BUG54096_ValueA);
        ctx.getNamingResources().addEnvironment(environmentA);

        ContextEnvironment environmentB = new ContextEnvironment();
        environmentB.setType(Bug54096EnvB.class.getName());
        environmentB.setName(BUG54096_NameB);
        environmentB.setValue(BUG54096_ValueB);
        ctx.getNamingResources().addEnvironment(environmentB);

        ctx.addApplicationListener(Bug54096Listener.class.getName());

        tomcat.start();

        Assert.assertEquals(LifecycleState.STARTED, ctx.getState());
    }

    public static class Bug54096EnvA {

        private final String value;

        public Bug54096EnvA(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    public static class Bug54096EnvB {

        private final char value;

        public Bug54096EnvB(char value) {
            this.value = value;
        }

        public char getValue() {
            return value;
        }
    }

    public static final class Bug54096Listener implements ServletContextListener {

        @Override
        public void contextInitialized(ServletContextEvent sce) {
            javax.naming.Context initCtx;
            try {
                initCtx = new InitialContext();
                javax.naming.Context envCtx = (javax.naming.Context) initCtx.lookup("java:comp/env");

                // Validate entry A
                Bug54096EnvA valueA = (Bug54096EnvA) envCtx.lookup(BUG54096_NameA);
                if (!BUG54096_ValueA.equals(valueA.getValue())) {
                    throw new RuntimeException();
                }

                // Validate entry B
                Bug54096EnvB valueB = (Bug54096EnvB) envCtx.lookup(BUG54096_NameB);
                if (BUG54096_ValueB.charAt(0) != valueB.getValue()) {
                    throw new RuntimeException();
                }

            } catch (NamingException e) {
                throw new RuntimeException(e);
            }
        }
    }


    /*
     * Tests for the container-generated connection pool name. The naming
     * listener passes a container-unique name through the pool factory's
     * object_name attribute to a JNDI resource that the Tomcat JDBC
     * connection pool builds and that does not name itself.
     *
     * The pool classes are not on the main test suite's classpath, so the
     * injection hook is exercised directly - it takes and returns only
     * naming and descriptor types - which also keeps these tests independent
     * of whether a pool could be instantiated here at all.
     */

    private static ContextResource poolResource(String jndiName, String factory) {
        ContextResource resource = new ContextResource();
        resource.setName(jndiName);
        resource.setType("javax.sql.DataSource");
        resource.setAuth("Container");
        resource.setScope("Shareable");
        if (factory != null) {
            // The factory attribute of a Resource element is stored as a
            // parameter, the way the digester rule stores it.
            resource.setProperty("factory", factory);
        }
        return resource;
    }

    /*
     * The object_name RefAddr the listener injects for the resource when its
     * reference is built, or null when it leaves the reference alone.
     */
    private static RefAddr injectedPoolName(Object container, ContextResource resource) {
        NamingContextListener listener = new NamingContextListener();
        listener.container = container;
        Reference ref = new Reference(resource.getType());
        listener.addContainerPoolName(ref, resource);
        return ref.get(Constants.POOL_NAME_PARAMETER);
    }

    @Test
    public void testWebappPoolResourceGetsContainerUniqueName() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        Context ctx = tomcat.addContext("/app1", null);

        ContextResource resource = poolResource("jdbc/DS", Constants.TOMCAT_JDBC_POOL_FACTORY);

        RefAddr addr = injectedPoolName(ctx, resource);

        Assert.assertNotNull("a pool that does not name itself must be named by the container", addr);
        Assert.assertEquals(
                tomcat.getEngine().getName() + "/" + tomcat.getHost().getName() + "/app1/jdbc/DS",
                addr.getContent());
    }

    @Test
    public void testRootContextKeepsEmptySegment() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        Context ctx = getProgrammaticRootContext();

        ContextResource resource = poolResource("jdbc/DS", Constants.TOMCAT_JDBC_POOL_FACTORY);

        RefAddr addr = injectedPoolName(ctx, resource);

        Assert.assertNotNull(addr);
        // The segment structure never collapses: ROOT contributes an empty
        // but kept segment, so its form cannot alias another context's.
        Assert.assertEquals(
                tomcat.getEngine().getName() + "/" + tomcat.getHost().getName() + "//jdbc/DS",
                addr.getContent());
    }

    @Test
    public void testParallelDeploymentSeparatesNames() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        // In this container the version marker of a parallel deployment is
        // part of the context name, so the generated pool name of the two
        // instances of one path must differ the same way.
        Context instance1 = tomcat.addContext("/app1", null);
        Context instance2 = tomcat.addContext("/app1##v2", null);

        ContextResource resource = poolResource("jdbc/DS", Constants.TOMCAT_JDBC_POOL_FACTORY);

        String name1 = (String) injectedPoolName(instance1, resource).getContent();
        String name2 = (String) injectedPoolName(instance2, resource).getContent();

        Assert.assertNotEquals(name1, name2);
        Assert.assertEquals(
                tomcat.getEngine().getName() + "/" + tomcat.getHost().getName() + "/app1/jdbc/DS",
                name1);
        Assert.assertEquals(
                tomcat.getEngine().getName() + "/" + tomcat.getHost().getName() + "/app1##v2/jdbc/DS",
                name2);
    }

    @Test
    public void testGlobalResourceGetsDomainScopedName() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        ContextResource resource = poolResource("jdbc/DS", Constants.TOMCAT_JDBC_POOL_FACTORY);

        RefAddr addr = injectedPoolName(tomcat.getServer(), resource);

        Assert.assertNotNull(addr);
        // A global resource is named by the Server that owns it. Its JNDI
        // name is already unique within the Server's global namespace.
        Assert.assertEquals(tomcat.getEngine().getName() + "/jdbc/DS", addr.getContent());
    }

    @Test
    public void testTwoWebappsWithSameJndiNameGetDifferentNames() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        Context ctx1 = tomcat.addContext("/app1", null);
        Context ctx2 = tomcat.addContext("/app2", null);

        Host otherHost = new StandardHost();
        otherHost.setName("example");
        tomcat.getEngine().addChild(otherHost);
        StandardContext ctx3 = new StandardContext();
        ctx3.setPath("/app1");
        otherHost.addChild(ctx3);

        ContextResource resource = poolResource("jdbc/DS", Constants.TOMCAT_JDBC_POOL_FACTORY);
        String name1 = (String) injectedPoolName(ctx1, resource).getContent();
        String name2 = (String) injectedPoolName(ctx2, resource).getContent();
        String name3 = (String) injectedPoolName(ctx3, resource).getContent();

        // The case the JNDI name alone cannot serve: the same JNDI name under
        // different paths or hosts must never produce the same pool name.
        Assert.assertNotEquals(name1, name2);
        Assert.assertNotEquals(name1, name3);
        Assert.assertNotEquals(name2, name3);
        Assert.assertEquals(tomcat.getEngine().getName() + "/localhost/app1/jdbc/DS", name1);
        Assert.assertEquals(tomcat.getEngine().getName() + "/localhost/app2/jdbc/DS", name2);
        Assert.assertEquals(tomcat.getEngine().getName() + "/example/app1/jdbc/DS", name3);
    }

    @Test
    public void testConfiguredObjectNameIsNotOverridden() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        Context ctx = tomcat.addContext("/app1", null);

        ContextResource resource = poolResource("jdbc/DS", Constants.TOMCAT_JDBC_POOL_FACTORY);
        resource.setProperty(Constants.POOL_NAME_PARAMETER, "My Pool");

        Assert.assertNull("a resource that names itself must keep its own name",
                injectedPoolName(ctx, resource));
    }

    @Test
    public void testDefaultDbcpFactoryIsSkipped() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        Context ctx = tomcat.addContext("/app1", null);

        String saved = System.getProperty(Constants.DATASOURCE_FACTORY_SYSPROP);
        System.clearProperty(Constants.DATASOURCE_FACTORY_SYSPROP);
        try {
            ContextResource resource = poolResource("jdbc/DS", null);
            Assert.assertNull("the DBCP default factory must not be renamed",
                    injectedPoolName(ctx, resource));
        } finally {
            if (saved != null) {
                System.setProperty(Constants.DATASOURCE_FACTORY_SYSPROP, saved);
            }
        }
    }

    @Test
    public void testSysPropResolvedPoolFactoryIsNamed() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        Context ctx = tomcat.addContext("/app1", null);

        String saved = System.getProperty(Constants.DATASOURCE_FACTORY_SYSPROP);
        System.setProperty(Constants.DATASOURCE_FACTORY_SYSPROP, Constants.TOMCAT_JDBC_POOL_FACTORY);
        try {
            ContextResource resource = poolResource("jdbc/DS", null);
            RefAddr addr = injectedPoolName(ctx, resource);
            Assert.assertNotNull(
                    "a resource whose factory resolves to the pool factory through the system"
                            + " property must be named like an explicitly configured pool",
                    addr);
            Assert.assertEquals(
                    tomcat.getEngine().getName() + "/" + tomcat.getHost().getName() + "/app1/jdbc/DS",
                    addr.getContent());
        } finally {
            if (saved == null) {
                System.clearProperty(Constants.DATASOURCE_FACTORY_SYSPROP);
            } else {
                System.setProperty(Constants.DATASOURCE_FACTORY_SYSPROP, saved);
            }
        }
    }

    @Test
    public void testNonSingletonIsSkipped() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        Context ctx = tomcat.addContext("/app1", null);

        ContextResource resource = poolResource("jdbc/DS", Constants.TOMCAT_JDBC_POOL_FACTORY);
        resource.setSingleton(false);

        Assert.assertNull("per-lookup pools must keep their own distinct names",
                injectedPoolName(ctx, resource));
    }

    @Test
    public void testOtherFactoryIsSkipped() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        Context ctx = tomcat.addContext("/app1", null);

        ContextResource resource = poolResource("jdbc/DS", "org.apache.naming.factory.BeanFactory");

        Assert.assertNull("a resource built by another factory must not carry a pool attribute",
                injectedPoolName(ctx, resource));
    }

    @Test
    public void testNonDataSourceTypeIsSkipped() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        Context ctx = tomcat.addContext("/app1", null);

        ContextResource resource = poolResource("beans/Integer", Constants.TOMCAT_JDBC_POOL_FACTORY);
        resource.setType("java.lang.Integer");

        Assert.assertNull("the pool factory builds only the data source types",
                injectedPoolName(ctx, resource));
    }

    @Test
    public void testContextWithoutHostParentIsSkipped() throws Exception {
        StandardContext orphan = new StandardContext();
        orphan.setName("/app1");

        ContextResource resource = poolResource("jdbc/DS", Constants.TOMCAT_JDBC_POOL_FACTORY);

        Assert.assertNull("an unpredictable container position must leave the pool at its own name",
                injectedPoolName(orphan, resource));
    }

    @Test
    public void testContextWithoutEngineUsesDefaultDomain() throws Exception {
        StandardHost host = new StandardHost();
        host.setName("embedded-host");
        StandardContext ctx = new StandardContext();
        ctx.setPath("/ctx");
        host.addChild(ctx);

        ContextResource resource = poolResource("jdbc/DS", Constants.TOMCAT_JDBC_POOL_FACTORY);

        RefAddr addr = injectedPoolName(ctx, resource);

        Assert.assertNotNull(addr);
        // Same default the container's own ObjectNames use when no Engine
        // answers above the context.
        Assert.assertEquals("Catalina/embedded-host/ctx/jdbc/DS", addr.getContent());
    }

    /*
     * A LookupRef-backed resource (the lookupName attribute) must pass
     * through the injection hook untouched: the bound reference is the lookup
     * reference and the lookup resolves to the target, exactly as before pool
     * naming existed. The reference it produces is not even a ResourceRef, so
     * no factory attribute and no pool name is involved.
     */
    @Test
    public void testLookupRefResourceIsUntouched() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        Context ctx = getProgrammaticRootContext();

        tomcat.enableNaming();

        ContextEnvironment target = new ContextEnvironment();
        target.setType(String.class.getName());
        target.setName("env/target");
        target.setValue("lookup-target");
        ctx.getNamingResources().addEnvironment(target);

        ContextResource resource = poolResource("res/looked-up", Constants.TOMCAT_JDBC_POOL_FACTORY);
        resource.setType(String.class.getName());
        resource.setLookupName("java:comp/env/env/target");
        ctx.getNamingResources().addResource(resource);

        ctx.addApplicationListener(LookupRefListener.class.getName());

        tomcat.start();

        Assert.assertEquals(LifecycleState.STARTED, ctx.getState());
    }

    public static final class LookupRefListener implements ServletContextListener {

        @Override
        public void contextInitialized(ServletContextEvent sce) {
            try {
                javax.naming.Context envCtx =
                        (javax.naming.Context) new InitialContext().lookup("java:comp/env");
                if (!"lookup-target".equals(envCtx.lookup("res/looked-up"))) {
                    throw new RuntimeException("LookupRef resource did not resolve to its target");
                }
            } catch (NamingException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /*
     * A resource added to a started context - the runtime path through the
     * naming resources' property-change events - goes through the injection
     * hook just like the start-time path, and the reference is bound.
     */
    @Test
    public void testRuntimeAddedPoolResourceIsBound() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        Context ctx = getProgrammaticRootContext();

        tomcat.enableNaming();

        tomcat.start();

        Assert.assertEquals(LifecycleState.STARTED, ctx.getState());

        NamingResourcesImpl namingResources = ctx.getNamingResources();
        namingResources.addResource(poolResource("jdbc/runtime", Constants.TOMCAT_JDBC_POOL_FACTORY));

        javax.naming.Context envCtx =
                ((StandardContext) ctx).getNamingContextListener().getEnvContext();
        Assert.assertNotNull(envCtx);

        // The pool factory is not on the main test suite's classpath, so
        // resolving the reference cannot produce the data source. What the
        // test pins is the difference between "a reference is bound" and
        // "nothing is bound".
        try {
            envCtx.lookup("jdbc/runtime");
            Assert.fail("Resolving the reference must not succeed without the pool classes");
        } catch (NameNotFoundException e) {
            Assert.fail("The runtime-added resource was not bound");
        } catch (NamingException e) {
            // Expected: bound, but the reference could not be resolved.
        }
    }
}
