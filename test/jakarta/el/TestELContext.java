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
package jakarta.el;

import java.util.List;

import jakarta.el.TesterEvaluationListener.Pair;

import org.junit.Assert;
import org.junit.Test;

public class TestELContext {

    /**
     * Tests that a null key results in an NPE as per EL Javadoc.
     */
    @Test(expected = NullPointerException.class)
    public void testGetContext() {
        ELContext elContext = new TesterELContext();
        elContext.getContext(null);
    }

    /**
     * Tests that a null key results in an NPE as per EL Javadoc.
     */
    @Test(expected = NullPointerException.class)
    public void testPutContext01() {
        ELContext elContext = new TesterELContext();
        elContext.putContext(null, new Object());
    }

    /**
     * Tests that a null context results in an NPE as per EL Javadoc.
     */
    @Test(expected = NullPointerException.class)
    public void testPutContext02() {
        ELContext elContext = new TesterELContext();
        elContext.putContext(Object.class, null);
    }

    /**
     * Tests that the context object will be added to the map with context
     * objects. The key is used as unique identifier of the context object in
     * the map.
     */
    @Test
    public void testPutContext03() {
        ELContext elContext = new TesterELContext();
        Assert.assertNull(elContext.getContext(String.class));
        elContext.putContext(String.class, "test");
        Assert.assertEquals("test", elContext.getContext(String.class));
        elContext.putContext(String.class, "test1");
        Assert.assertEquals("test1", elContext.getContext(String.class));
    }

    /**
     * Tests that propertyResolved will be set to true and the corresponding
     * listeners will be notified.
     */
    @Test
    public void testSetPropertyResolved() {
        ELContext elContext = new TesterELContext();

        TesterEvaluationListener listener = new TesterEvaluationListener();
        elContext.addEvaluationListener(listener);

        TesterBean bean = new TesterBean("test");

        elContext.setPropertyResolved(bean, "name");

        Assert.assertTrue(elContext.isPropertyResolved());

        List<Pair> events = listener.getResolvedProperties();
        Assert.assertEquals(1, events.size());
        Pair p = events.get(0);
        Assert.assertEquals(bean, p.getBase());
        Assert.assertEquals("name", p.getProperty());
    }

    /**
     * Tests that the corresponding listeners will be notified.
     */
    @Test
    public void testNotifyBeforeEvaluation() {
        ELContext elContext = new TesterELContext();

        TesterEvaluationListener listener = new TesterEvaluationListener();
        elContext.addEvaluationListener(listener);

        elContext.notifyBeforeEvaluation("before");

        List<String> events = listener.getBeforeEvaluationExpressions();
        Assert.assertEquals(1, events.size());
        Assert.assertEquals("before", events.get(0));
    }

    /**
     * Tests that the corresponding listeners will be notified.
     */
    @Test
    public void testNotifyAfterEvaluation() {
        ELContext elContext = new TesterELContext();

        TesterEvaluationListener listener = new TesterEvaluationListener();
        elContext.addEvaluationListener(listener);

        elContext.notifyAfterEvaluation("after");

        List<String> events = listener.getAfterEvaluationExpressions();
        Assert.assertEquals(1, events.size());
        Assert.assertEquals("after", events.get(0));
    }

    /**
     * Tests not compatible object and type.
     */
    @Test(expected = ELException.class)
    public void testConvertToType01() {
        ELContext elContext = new TesterELContext();
        elContext.convertToType("test", Integer.class);
    }

    /**
     * Tests that if there is no ELResolver the standard coercions will be
     * invoked.
     */
    @Test
    public void testConvertToType02() {
        ELContext elContext = new TesterELContext();
        boolean originalPropertyResolved = false;
        elContext.setPropertyResolved(originalPropertyResolved);

        Object result = elContext.convertToType("test", String.class);
        Assert.assertEquals("test", result);

        Assert.assertTrue(originalPropertyResolved == elContext
                .isPropertyResolved());
    }

    /**
     * Tests that if there is ELResolver it will handle the conversion. If this
     * resolver cannot return a result the standard coercions will be invoked.
     */
    @Test
    public void testConvertToType03() {
        ELContext elContext = new TesterELContext(new TesterELResolverOne());

        boolean originalPropertyResolved = false;
        elContext.setPropertyResolved(originalPropertyResolved);

        Object result = elContext.convertToType("1", String.class);
        Assert.assertEquals("ONE", result);
        Assert.assertTrue(originalPropertyResolved == elContext
                .isPropertyResolved());

        result = elContext.convertToType("test", String.class);
        Assert.assertEquals("test", result);
        Assert.assertTrue(originalPropertyResolved == elContext
                .isPropertyResolved());
    }
}
