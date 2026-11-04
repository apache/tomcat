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

import javax.naming.NamingException;
import javax.naming.StringRefAddr;

import org.junit.Assert;
import org.junit.Test;

import org.apache.naming.ResourceRef;

public class TestBeanFactory {

    private static final String IP_ADDRESS = "127.0.0.1";

    @Test
    public void testForceStringAlternativeWithout() throws Exception {
        doTestForceStringAlternatove(false);
    }


    @Test
    public void testForceStringAlternativeWith() throws Exception {
        doTestForceStringAlternatove(true);
    }


    private void doTestForceStringAlternatove(boolean useForceString) throws Exception {

        // Create the resource definition
        ResourceRef resourceRef = new ResourceRef(TesterBean.class.getName(), null, null, null, false);
        StringRefAddr server = new StringRefAddr("server", IP_ADDRESS);
        resourceRef.add(server);
        if (useForceString) {
            StringRefAddr force = new StringRefAddr("forceString", "server");
            resourceRef.add(force);
        }

        // Create the factory
        BeanFactory factory = new BeanFactory();

        // Use the factory to create the resource from the definition
        Object obj = factory.getObjectInstance(resourceRef, null, null, null);

        // Check the correct type was created
        Assert.assertNotNull(obj);
        Assert.assertEquals(obj.getClass(), TesterBean.class);
        // Check the server field was set
        TesterBean result = (TesterBean) obj;
        Assert.assertNotNull(result.getServer());
        Assert.assertEquals(IP_ADDRESS, result.getServer().getHostAddress());
    }


    /*
     * The description of a resource is a property of the resource definition
     * and not a property of the bean, so the factory has to ignore it.
     */
    @Test
    public void testDescriptionIsNotPassedToTheBean() throws Exception {

        // Create the resource definition with a description
        ResourceRef resourceRef = new ResourceRef(TesterBean.class.getName(), "The tester bean", null, null, false);
        resourceRef.add(new StringRefAddr("server", IP_ADDRESS));

        // Create the factory
        BeanFactory factory = new BeanFactory();

        // Use the factory to create the resource from the definition
        Object obj = factory.getObjectInstance(resourceRef, null, null, null);

        // Check the correct type was created
        Assert.assertNotNull(obj);
        Assert.assertEquals(obj.getClass(), TesterBean.class);
        // Check the description was ignored and only the server field was set
        TesterBean result = (TesterBean) obj;
        Assert.assertNotNull(result.getServer());
        Assert.assertEquals(IP_ADDRESS, result.getServer().getHostAddress());
    }


    /*
     * A value which does not match the type of the property is a configuration
     * error which has to be reported as a NamingException with the property as
     * context, not as the unchecked exception of the conversion.
     */
    @Test
    public void testValueNotMatchingThePropertyTypeIsReported() throws Exception {

        ResourceRef resourceRef = new ResourceRef(TesterBean.class.getName(), null, null, null, false);
        resourceRef.add(new StringRefAddr("limit", "not-a-number"));

        BeanFactory factory = new BeanFactory();

        try {
            Object obj = factory.getObjectInstance(resourceRef, null, null, null);
            Assert.fail("The invalid value was accepted: " + obj);
        } catch (NamingException e) {
            Assert.assertTrue("The property is not named: " + e.getMessage(), e.getMessage().contains("limit"));
            Assert.assertTrue("The value is not named: " + e.getMessage(), e.getMessage().contains("not-a-number"));
            Assert.assertTrue("The reason for the failure is not reported",
                    e.getCause() instanceof NumberFormatException);
        }
    }


    /*
     * The value of a character property is read from the first character of
     * the configured value, so an empty value has to be reported in the same
     * way as a value which cannot be converted.
     */
    @Test
    public void testEmptyValueForCharacterPropertyIsReported() throws Exception {

        ResourceRef resourceRef = new ResourceRef(TesterBean.class.getName(), null, null, null, false);
        resourceRef.add(new StringRefAddr("letter", ""));

        BeanFactory factory = new BeanFactory();

        try {
            Object obj = factory.getObjectInstance(resourceRef, null, null, null);
            Assert.fail("The invalid value was accepted: " + obj);
        } catch (NamingException e) {
            Assert.assertTrue("The property is not named: " + e.getMessage(), e.getMessage().contains("letter"));
            Assert.assertTrue("The type is not named: " + e.getMessage(), e.getMessage().contains("char"));
            Assert.assertTrue("The reason for the failure is not reported",
                    e.getCause() instanceof StringIndexOutOfBoundsException);
        }
    }
}
