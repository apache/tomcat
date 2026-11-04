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
package org.apache.naming.factory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.naming.Context;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import org.apache.catalina.LifecycleListener;
import org.apache.catalina.core.NamingContextListener;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.naming.ContextAccessController;
import org.apache.naming.ContextBindings;
import org.apache.naming.LookupRef;
import org.apache.tomcat.util.descriptor.web.ContextEjb;

/*
 * The type of a lookup reference is the type which the application expects, so
 * it has to be loaded with the class loader of the application. The factory
 * itself is loaded by a class loader which does not see the classes of the
 * web application, so using the class loader of the factory left the type
 * unloadable and the lookup failed for every type which was local to the web
 * application, which includes the type of a field annotated with Resource.
 */
public class TestLookupFactory extends TomcatBaseTest {

    private static final String MARKER_CLASS = "tester.Marker";

    private ClassLoader originalContextClassLoader;


    @Test
    public void testTypeOfWebappLocalClassIsLoaded() throws Exception {
        TesterWebapp webapp = startWebapp();

        Context container = ContextBindings.getClassLoader();
        container.bind("comp/env/ref", new LookupRef(MARKER_CLASS, "java:comp/env/target"));

        Object found = new javax.naming.InitialContext().lookup("java:comp/env/ref");
        Assert.assertSame(webapp.marker(), found);
    }


    @Test
    public void testEjbLookupRefIsCheckedAgainstTheBeanInterface() throws Exception {
        TesterWebapp webapp = startWebapp();

        ContextEjb ejb = new ContextEjb();
        ejb.setName("ejb");
        // The type of an EJB reference descriptor is the type of the
        // reference, which is neither a class name nor a type the looked up
        // object can have
        ejb.setType("Session");
        ejb.setLookupName("java:comp/env/target");
        ejb.setRemote(MARKER_CLASS);
        webapp.listener().addEjb(ejb);

        Object found = new javax.naming.InitialContext().lookup("java:comp/env/ejb");
        Assert.assertSame(webapp.marker(), found);
    }


    @Test
    public void testTypeMismatchIsStillReported() throws Exception {
        startWebapp();

        Context container = ContextBindings.getClassLoader();
        container.bind("comp/env/ref", new LookupRef(String.class.getName(), "java:comp/env/target"));

        try {
            Object found = new javax.naming.InitialContext().lookup("java:comp/env/ref");
            Assert.fail("The reference of the wrong type was returned: " + found);
        } catch (javax.naming.NamingException e) {
            Assert.assertTrue("The wrong failure was reported: " + e.getMessage(),
                    e.getMessage().contains("expected to be of type"));
        }
    }


    /*
     * The type of an EJB reference descriptor names the type of the reference
     * itself and is never loadable, so the remote interface has to be the type
     * which the looked up object is checked against.
     */
    @Test
    public void testEjbLookupRefIsCheckedAgainstTheRemoteInterface() throws Exception {
        TesterWebapp webapp = startWebapp();

        ContextEjb ejb = new ContextEjb();
        ejb.setName("ejb");
        ejb.setType("Session");
        ejb.setLookupName("java:comp/env/target");
        // The object bound as the target is not an instance of this interface
        ejb.setRemote(String.class.getName());
        webapp.listener().addEjb(ejb);

        try {
            Object found = new javax.naming.InitialContext().lookup("java:comp/env/ejb");
            Assert.fail("The reference of the wrong type was returned: " + found);
        } catch (javax.naming.NamingException e) {
            Assert.assertTrue("The wrong failure was reported: " + e.getMessage(),
                    e.getMessage().contains("expected to be of type"));
        }
    }


    @Test
    public void testTypeWhichCannotBeLoadedIsReported() throws Exception {
        startWebapp();

        String missingClass = MARKER_CLASS + "Missing";
        Context container = ContextBindings.getClassLoader();
        container.bind("comp/env/ref", new LookupRef(missingClass, "java:comp/env/target"));

        try {
            Object found = new javax.naming.InitialContext().lookup("java:comp/env/ref");
            Assert.fail("The reference of an unloadable type was returned: " + found);
        } catch (javax.naming.NamingException e) {
            Assert.assertTrue("The wrong failure was reported: " + e.getMessage(),
                    e.getMessage().contains("Could not load the class"));
            Assert.assertTrue("The class which cannot be loaded is not named: " + e.getMessage(),
                    e.getMessage().contains(missingClass));
            Assert.assertTrue("The reason for the failure is not reported",
                    e.getCause() instanceof ClassNotFoundException);
        }
    }


    /**
     * Starts a web application with a class which only its own class loader
     * can see and binds an instance of it as the target of the lookup
     * references. The context class loader is set to the one of the web
     * application, as it is for the threads of a web application, and restored
     * after the test.
     *
     * @return the listener of the naming resources and the instance of the class of the web application
     *
     * @throws Exception if the web application cannot be started
     */
    private TesterWebapp startWebapp() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Assume.assumeTrue("The compiler of the JDK is not available", compiler != null);

        File docBase = Files.createTempDirectory(getTemporaryDirectory().toPath(), "docBase").toFile();
        Path classes = new File(docBase, "WEB-INF/classes/").toPath();
        Files.createDirectories(classes);
        Path source = new File(docBase, "tester/Marker.java").toPath();
        Files.createDirectories(source.getParent());
        Files.write(source, ("package tester;\npublic class Marker {\n}\n".getBytes(StandardCharsets.UTF_8)));
        ByteArrayOutputStream compilerOutput = new ByteArrayOutputStream();
        @SuppressWarnings("null")
        int result = compiler.run(null, compilerOutput, compilerOutput, "-d", classes.toString(),
                source.toString());
        Assert.assertEquals("The class of the web application could not be compiled: " + compilerOutput, 0, result);

        Tomcat tomcat = getTomcatInstance();
        tomcat.enableNaming();
        org.apache.catalina.Context ctx = tomcat.addContext("", docBase.getAbsolutePath());
        tomcat.start();

        // The context of the web application is read only once it is started
        NamingContextListener listener = null;
        for (LifecycleListener lifecycleListener : ctx.findLifecycleListeners()) {
            if (lifecycleListener instanceof NamingContextListener namingContextListener) {
                listener = namingContextListener;
            }
        }
        Assert.assertNotNull(listener);
        ContextAccessController.setWritable(listener.getName(), ctx.getNamingToken());

        ClassLoader webappLoader = ctx.getLoader().getClassLoader();
        // The naming context of a web application is bound to its class loader
        originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(webappLoader);

        Object marker = webappLoader.loadClass(MARKER_CLASS).getDeclaredConstructor().newInstance();
        // The target of the references is bound in the context of the web
        // application, which is the context a lookup resolves names in
        Context container = ContextBindings.getClassLoader();
        container.bind("comp/env/target", marker);

        return new TesterWebapp(listener, marker);
    }


    /*
     * The lookup is done on the test thread with the class loader of the web
     * application as the context class loader, as it is on the threads of a
     * web application. It is restored here, which runs before the tear down of
     * the base class stops the web application, so that neither the stopping
     * nor the other tests run with the class loader of the web application.
     */
    @After
    public void restoreContextClassLoader() {
        Thread.currentThread().setContextClassLoader(originalContextClassLoader);
    }


    /**
     * The listener which binds the naming resources of the web application and
     * the instance of the class which only that web application can see.
     */
    private record TesterWebapp(NamingContextListener listener, Object marker) {
    }
}
