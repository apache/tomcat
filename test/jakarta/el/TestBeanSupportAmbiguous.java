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

import jakarta.el.BeanELResolver.BeanProperties;
import jakarta.el.BeanELResolver.BeanProperty;
import jakarta.el.TestBeanSupport.TypeA;

import org.junit.Assert;
import org.junit.Test;

public class TestBeanSupportAmbiguous {

    /*
     * Different JREs appear to call different methods on AmbiguousBean. This test ensures that the behaviour is
     * consistent across BeanSupport implementations within any single JRE,
     */
    @Test
    public void testAmbiguousBean() {
        // Disable caching so we can switch implementations within a JVM instance.
        System.setProperty("jakarta.el.BeanSupport.doNotCacheInstance", "true");

        // First test the stand-alone type
        System.setProperty("jakarta.el.BeanSupport.useStandalone", Boolean.TRUE.toString());
        Class<?> standaloneType = doTest();

        // Then test the full JavaBeans implementation
        System.setProperty("jakarta.el.BeanSupport.useStandalone", Boolean.FALSE.toString());
        Class<?> javaBeansType = doTest();

        Assert.assertEquals(standaloneType, javaBeansType);
    }


    private Class<?> doTest() {
        BeanProperties beanProperties = BeanSupport.getInstance().getBeanProperties(AmbiguousBean.class);
        BeanProperty beanProperty = beanProperties.properties.get("value");

        // Property type
        Assert.assertNotNull(beanProperty);
        Class<?> propertyType = beanProperty.getPropertyType();
        Assert.assertNotNull(propertyType);

        // There is no getter
        Assert.assertNull(beanProperty.getReadMethod());

        // Check setter
        Assert.assertEquals(void.class, beanProperty.getWriteMethod().getReturnType());
        Assert.assertEquals(1, beanProperty.getWriteMethod().getParameterCount());
        Assert.assertEquals(propertyType, beanProperty.getWriteMethod().getParameterTypes()[0]);

        return propertyType;
    }


    public static class AmbiguousBean {
        public void setValue(@SuppressWarnings("unused") TypeA value) {
        }

        public void setValue(@SuppressWarnings("unused") String value) {
        }
    }
}
