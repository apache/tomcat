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

import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanOperationInfo;

import org.junit.Assert;
import org.junit.Test;

public class TestManagedBean {

    @Test
    public void testAddAttributeInvalidatesCachedMBeanInfo() {
        ManagedBean managedBean = new ManagedBean();
        managedBean.setName("Tester");
        managedBean.setType("Tester");

        AttributeInfo first = new AttributeInfo();
        first.setName("first");
        first.setDescription("First attribute");
        first.setType("java.lang.String");
        managedBean.addAttribute(first);

        MBeanInfo info = managedBean.getMBeanInfo();
        int baseCount = info.getAttributes().length;

        AttributeInfo second = new AttributeInfo();
        second.setName("second");
        second.setDescription("Second attribute");
        second.setType("java.lang.String");
        managedBean.addAttribute(second);

        MBeanInfo updated = managedBean.getMBeanInfo();
        Assert.assertNotSame(info, updated);
        Assert.assertEquals(baseCount + 1, updated.getAttributes().length);
        boolean found = false;
        for (MBeanAttributeInfo attributeInfo : updated.getAttributes()) {
            found |= "second".equals(attributeInfo.getName());
        }
        Assert.assertTrue(found);
    }

    @Test
    public void testAddOperationInvalidatesCachedMBeanInfo() {
        ManagedBean managedBean = new ManagedBean();
        managedBean.setName("Tester");
        managedBean.setType("Tester");

        OperationInfo first = new OperationInfo();
        first.setName("first");
        first.setDescription("First operation");
        first.setReturnType("void");
        managedBean.addOperation(first);

        MBeanInfo info = managedBean.getMBeanInfo();
        Assert.assertEquals(1, info.getOperations().length);

        OperationInfo second = new OperationInfo();
        second.setName("second");
        second.setDescription("Second operation");
        second.setReturnType("void");
        managedBean.addOperation(second);

        MBeanInfo updated = managedBean.getMBeanInfo();
        Assert.assertNotSame(info, updated);
        Assert.assertEquals(2, updated.getOperations().length);
        boolean found = false;
        for (MBeanOperationInfo operationInfo : updated.getOperations()) {
            found |= "second".equals(operationInfo.getName());
        }
        Assert.assertTrue(found);
    }
}
