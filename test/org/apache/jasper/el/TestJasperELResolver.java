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
import jakarta.el.ELException;
import jakarta.el.ELResolver;
import jakarta.servlet.jsp.el.ImplicitObjectELResolver;

import org.junit.Assert;
import org.junit.Test;

import org.apache.el.stream.StreamELResolverImpl;
import org.apache.jasper.runtime.JspRuntimeLibrary;

public class TestJasperELResolver {

    private static final int STANDARD_RESOLVERS_COUNT = 12;

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

    private static Object getField(String name, Object target)
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
        Assert.assertFalse(resolver.isReadOnly(context, new TestBean(), "bla"));

        // A setter whose parameter type is a supertype of the value type must
        // be used. The inverted assignability check in getWriteMethod()
        // previously rejected such setters, silently dropping the write.
        TestBean bean = new TestBean();
        resolver.setValue(context, bean, "bla", "a string value");
        Assert.assertEquals("a string value", bean.getLastBla());

        // A null value has no class to check the setter against. It must reach
        // a setter whose parameter can hold null. Before the fix, deriving the
        // value class from the null value caused a NullPointerException here.
        context.setPropertyResolved(false);
        resolver.setValue(context, bean, "bla", null);
        Assert.assertNull("A null value should be written to a reference-typed setter", bean.getLastBla());
        Assert.assertTrue("A null value should resolve the property", context.isPropertyResolved());

        // A value whose type the setter parameter cannot hold must be left
        // unresolved so that another resolver in the chain can handle it
        context.setPropertyResolved(false);
        resolver.setValue(context, bean, "foo", Integer.valueOf(7));
        Assert.assertFalse("A mismatched value must leave the property unresolved",
                context.isPropertyResolved());

        // An exception thrown by a getter must be reported rather than
        // swallowed. Before the fix it produced a silently resolved null.
        context.setPropertyResolved(false);
        try {
            resolver.getValue(context, bean, "failing");
            Assert.fail("A getter exception must be reported");
        } catch (ELException e) {
            Assert.assertTrue("The reported cause must be the thrown exception",
                    e.getCause() instanceof IllegalStateException);
            Assert.assertEquals("getter failed", e.getCause().getMessage());
            Assert.assertTrue("A found property should be marked resolved", context.isPropertyResolved());
        }

        // An exception thrown by a setter must be reported rather than
        // swallowed. Before the fix the failed write went unnoticed.
        context.setPropertyResolved(false);
        try {
            resolver.setValue(context, bean, "failing", "some value");
            Assert.fail("A setter exception must be reported");
        } catch (ELException e) {
            Assert.assertTrue("The reported cause must be the thrown exception",
                    e.getCause() instanceof IllegalStateException);
            Assert.assertEquals("setter failed", e.getCause().getMessage());
            Assert.assertTrue("A found property should be marked resolved", context.isPropertyResolved());
        }
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
        private Object bla;
        public String getBla() {
            return "bla";
        }
        public void setBla(Object bla) {
            this.bla = bla;
        }
        public Object getLastBla() {
            return bla;
        }
        public String getFailing() {
            throw new IllegalStateException("getter failed");
        }
        public void setFailing(@SuppressWarnings("unused") String value) {
            throw new IllegalStateException("setter failed");
        }
    }
}
