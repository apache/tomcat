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
package org.apache.tomcat.util.modeler;

import javax.management.ObjectName;

import org.junit.Assert;
import org.junit.Test;

public class TestRegisterComponent {

    public static class TesterBean {

        private String value = "test";

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    /*
     * Registry that simulates the documented case where findManagedBean()
     * finds no metadata for the requested type and returns null.
     */
    private static class NoMetadataRegistry extends Registry {

        @Override
        public ManagedBean findManagedBean(Object bean, Class<?> beanClass, String type) throws Exception {
            return null;
        }
    }

    @Test
    public void testRegisterComponentWithoutMetadataThrowsIllegalState() throws Exception {
        Registry registry = new NoMetadataRegistry();
        ObjectName oname = new ObjectName("tester:type=NoMetadataTester");

        Assert.assertThrows(IllegalStateException.class,
                () -> registry.registerComponent(new TesterBean(), oname, null));
    }

    @Test
    public void testRegisterComponentWithIntrospectedMetadata() throws Exception {
        Registry registry = new Registry();
        ObjectName oname = new ObjectName("tester:type=RegisterComponentTester");

        try {
            registry.registerComponent(new TesterBean(), oname, null);
            Assert.assertTrue(registry.getMBeanServer().isRegistered(oname));
        } finally {
            if (registry.getMBeanServer().isRegistered(oname)) {
                registry.getMBeanServer().unregisterMBean(oname);
            }
        }
    }
}
