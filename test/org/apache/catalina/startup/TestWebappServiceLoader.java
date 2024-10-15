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

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

import jakarta.servlet.ServletContainerInitializer;
import jakarta.servlet.ServletContext;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsInstanceOf.instanceOf;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.webresources.StandardRoot;
import org.apache.tomcat.unittest.TesterContext;
import org.easymock.EasyMock;
import org.easymock.IMocksControl;

public class TestWebappServiceLoader {
    private static final String CONFIG_FILE =
            "META-INF/services/jakarta.servlet.ServletContainerInitializer";
    private IMocksControl control;
    private ClassLoader cl;
    private ClassLoader parent;
    private Context context;
    private ServletContext servletContext;
    private WebappServiceLoader<ServletContainerInitializer> loader;

    @Before
    public void init() {
        control = EasyMock.createStrictControl();
        parent = control.createMock(ClassLoader.class);
        cl = EasyMock.createMockBuilder(ClassLoader.class)
                .withConstructor(parent)
                .addMockedMethod("loadClass", String.class)
                .createMock(control);
        servletContext = control.createMock(ServletContext.class);
        EasyMock.expect(servletContext.getClassLoader()).andStubReturn(cl);
        context = new ExtendedTesterContext(servletContext, parent);
    }

    @Test
    public void testNoInitializersFound() throws IOException {
        loader = new WebappServiceLoader<>(context);
        EasyMock.expect(cl.getResources(CONFIG_FILE))
                .andReturn(Collections.<URL>emptyEnumeration());
        EasyMock.expect(servletContext.getAttribute(ServletContext.ORDERED_LIBS))
                .andReturn(null);
        EasyMock.expect(cl.getResources(CONFIG_FILE))
                .andReturn(Collections.<URL>emptyEnumeration());
        control.replay();
        Assert.assertTrue(loader.load(ServletContainerInitializer.class).isEmpty());
        control.verify();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testInitializerFromClasspath() throws IOException {
        URL url = URI.create("file://test").toURL();
        loader = EasyMock.createMockBuilder(WebappServiceLoader.class)
                .addMockedMethod("parseConfigFile", LinkedHashSet.class, URL.class)
                .withConstructor(context).createMock(control);
        EasyMock.expect(cl.getResources(CONFIG_FILE))
                .andReturn(Collections.enumeration(Collections.singleton(url)));
        loader.parseConfigFile(EasyMock.isA(LinkedHashSet.class), EasyMock.same(url));
        EasyMock.expect(servletContext.getAttribute(ServletContext.ORDERED_LIBS))
                .andReturn(null);
        EasyMock.expect(cl.getResources(CONFIG_FILE))
                .andReturn(Collections.enumeration(Collections.singleton(url)));
        control.replay();
        Assert.assertTrue(loader.load(ServletContainerInitializer.class).isEmpty());
        control.verify();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testWithOrdering() throws IOException {
        URL url1 = URI.create("file://jar1.jar").toURL();
        URL sci1 = URI.create("jar:file://jar1.jar!/" + CONFIG_FILE).toURL();
        URL url2 = URI.create("file://dir/").toURL();
        URL sci2 = URI.create("file://dir/" + CONFIG_FILE).toURL();
        loader = EasyMock.createMockBuilder(WebappServiceLoader.class)
                .addMockedMethod("parseConfigFile", LinkedHashSet.class, URL.class)
                .withConstructor(context).createMock(control);
        List<String> jars = Arrays.asList("jar1.jar", "dir/");
        EasyMock.expect(parent.getResources(CONFIG_FILE))
                .andReturn(Collections.<URL>emptyEnumeration());
        EasyMock.expect(servletContext.getAttribute(ServletContext.ORDERED_LIBS))
                .andReturn(jars);
        EasyMock.expect(servletContext.getResource("/WEB-INF/classes/" + CONFIG_FILE))
                .andReturn(null);
        EasyMock.expect(servletContext.getResource("/WEB-INF/lib/jar1.jar"))
                .andReturn(url1);
        loader.parseConfigFile(EasyMock.isA(LinkedHashSet.class), EasyMock.eq(sci1));
        EasyMock.expect(servletContext.getResource("/WEB-INF/lib/dir/"))
                .andReturn(url2);
        loader.parseConfigFile(EasyMock.isA(LinkedHashSet.class), EasyMock.eq(sci2));

        control.replay();
        Assert.assertTrue(loader.load(ServletContainerInitializer.class).isEmpty());
        control.verify();
    }

    @Test
    public void testParseConfigFile() throws IOException {
        LinkedHashSet<String> found = new LinkedHashSet<>();
        loader = new WebappServiceLoader<>(context);
        loader.parseConfigFile(found, getClass().getResource("service-config.txt"));
        Assert.assertEquals(Collections.singleton("provider1"), found);
    }

    @Test
    public void testLoadServices() throws Exception {
        Class<?> sci = TesterServletContainerInitializer1.class;
        loader = new WebappServiceLoader<>(context);
        cl.loadClass(sci.getName());
        EasyMock.expectLastCall()
                .andReturn(sci);
        LinkedHashSet<String> names = new LinkedHashSet<>();
        names.add(sci.getName());
        control.replay();
        Collection<ServletContainerInitializer> initializers =
                loader.loadServices(ServletContainerInitializer.class, names);
        Assert.assertEquals(1, initializers.size());
        Assert.assertTrue(sci.isInstance(initializers.iterator().next()));
        control.verify();
    }

    @Test
    public void testServiceIsNotExpectedType() throws Exception {
        Class<?> sci = Object.class;
        loader = new WebappServiceLoader<>(context);
        cl.loadClass(sci.getName());
        EasyMock.expectLastCall()
                .andReturn(sci);
        LinkedHashSet<String> names = new LinkedHashSet<>();
        names.add(sci.getName());
        control.replay();
        try {
            loader.loadServices(ServletContainerInitializer.class, names);
        } catch (IOException e) {
            assertThat(e.getCause(), instanceOf(ClassCastException.class));
        } finally {
            control.verify();
        }
    }

    @Test
    public void testServiceCannotBeConstructed() throws Exception {
        Class<?> sci = Integer.class;
        loader = new WebappServiceLoader<>(context);
        cl.loadClass(sci.getName());
        EasyMock.expectLastCall()
                .andReturn(sci);
        LinkedHashSet<String> names = new LinkedHashSet<>();
        names.add(sci.getName());
        control.replay();
        try {
            loader.loadServices(ServletContainerInitializer.class, names);
        } catch (IOException e) {
            assertThat(e.getCause(), instanceOf(ReflectiveOperationException.class));
        } finally {
            control.verify();
        }
    }

    private static class ExtendedTesterContext extends TesterContext {
        private final ServletContext servletContext;
        private final ClassLoader parent;
        private final WebResourceRoot resources;

        ExtendedTesterContext(ServletContext servletContext, ClassLoader parent) {
            this.servletContext = servletContext;
            this.parent = parent;
            // Empty resources - any non-null returns will be mocked on the
            // ServletContext
            this.resources = new StandardRoot(this);
            try {
                this.resources.start();
            } catch (LifecycleException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public ServletContext getServletContext() {
            return servletContext;
        }

        @Override
        public String getContainerSciFilter() {
            return "";
        }

        @Override
        public ClassLoader getParentClassLoader() {
            return parent;
        }

        @Override
        public WebResourceRoot getResources() {
            return resources;
        }
    }
}
