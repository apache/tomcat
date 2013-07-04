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
package javax.el;

import org.junit.Assert;
import org.junit.Test;

public class TestBeanNameELResolver {

    private static final String BEAN01_NAME = "bean01";
    private static final TesterBean BEAN01 = new TesterBean(BEAN01_NAME);
    private static final String BEAN02_NAME = "bean02";
    private static final TesterBean BEAN02 = new TesterBean(BEAN02_NAME);

    /**
     * Creates the resolver that is used for the test. All the tests use a
     * resolver with the same configuration.
     */
    private BeanNameELResolver createBeanNameELResolver() {

        BeanNameResolver beanNameResolver = new TesterBeanNameResolver();
        beanNameResolver.setBeanValue(BEAN01_NAME, BEAN01);
        beanNameResolver.setBeanValue(BEAN02_NAME, BEAN02);

        BeanNameELResolver beanNameELResolver =
                new BeanNameELResolver(beanNameResolver);
        return beanNameELResolver;
    }


    /**
     * Tests that a null context results in an NPE as per EL Javadoc.
     */
    @Test(expected=NullPointerException.class)
    public void testGetValue01() {
        BeanNameELResolver resolver = createBeanNameELResolver();
        resolver.getValue(null, new Object(), new Object());
    }


    /**
     * Tests that a valid bean is resolved.
     */
    @Test
    public void testGetValue02() {

        BeanNameELResolver resolver = createBeanNameELResolver();
        ELContext context =
                new StandardELContext(ELManager.getExpressionFactory());

        Object result = resolver.getValue(context, null, BEAN01_NAME);

        Assert.assertEquals(BEAN01, result);
    }

}
