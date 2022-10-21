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
package org.apache.jasper.el;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.el.ELContext;
import jakarta.el.ELResolver;
import jakarta.servlet.jsp.el.ImplicitObjectELResolver;

import org.junit.Assert;
import org.junit.Test;

import org.apache.el.stream.StreamELResolverImpl;
import org.apache.jasper.runtime.JspRuntimeLibrary;

public class TestJasperELResolver {

    private static final int STANDARD_RESOLVERS_COUNT = 11;

    @Test
    public void testConstructorNone() throws Exception {
        doTestConstructor(0);
    }

    @Test
    public void testConstructorOne() throws Exception {
        doTestConstructor(1);
    }

    @Test
    public void testConstructorFive() throws Exception {
        doTestConstructor(5);
    }

    private void doTestConstructor(int count) throws Exception {

        List<ELResolver> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(new ImplicitObjectELResolver());
        }

        int adjustedForGraalCount = JspRuntimeLibrary.GRAAL ? count + 1 : count;

        JasperELResolver resolver =
                new JasperELResolver(list, new StreamELResolverImpl());

        Assert.assertEquals(Integer.valueOf(count),
                getField("appResolversSize", resolver));
        Assert.assertEquals(STANDARD_RESOLVERS_COUNT + adjustedForGraalCount,
                ((ELResolver[])getField("resolvers", resolver)).length);
        Assert.assertEquals(Integer.valueOf(STANDARD_RESOLVERS_COUNT + adjustedForGraalCount),
                Integer.valueOf(((AtomicInteger) getField("resolversSize", resolver)).get()));
    }

    private static final Object getField(String name, Object target)
            throws NoSuchFieldException, SecurityException,
            IllegalArgumentException, IllegalAccessException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    @Test
    public void testGraalResolver() throws Exception {
        ELResolver resolver = new JasperELResolver.GraalBeanELResolver();
        ELContext context = new ELContextImpl(resolver);
        Assert.assertEquals("foo", resolver.getValue(context, new TestBean(), "foo"));
        Assert.assertEquals(Boolean.TRUE, resolver.getValue(context, new TestBean(), "foo2"));
        Assert.assertEquals("bla", resolver.getValue(context, new TestBean(), "bla"));
        Assert.assertNull(resolver.getValue(context, new TestBean(), "foobar"));
        Assert.assertNull(resolver.getValue(context, new TestBean(), "bar"));
        Assert.assertFalse(resolver.isReadOnly(context, new TestBean(), "foo"));
        Assert.assertTrue(resolver.isReadOnly(context, new TestBean(), "bla"));
    }

    public static class TestBean {
        public String getFoo() {
            return "foo";
        }
        public void setFoo(@SuppressWarnings("unused") String foo) {
        }
        public boolean isFoo2() {
            return true;
        }
        public void setFoo2(@SuppressWarnings("unused") boolean foo) {
        }
        public String getBar(@SuppressWarnings("unused") boolean i) {
            return "bar";
        }
        public String isFoobar() {
            return "foobar";
        }
        public String getBla() {
            return "bla";
        }
        public void setBla(@SuppressWarnings("unused") Object bla) {
        }
    }
}
