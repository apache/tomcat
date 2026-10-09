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

import java.util.ArrayList;
import java.util.List;

import javax.management.Attribute;
import javax.management.AttributeChangeNotification;
import javax.management.ListenerNotFoundException;
import javax.management.NotificationListener;

import org.junit.Assert;
import org.junit.Test;

public class TestBaseModelMBean {

    @Test
    public void testRemoveAttributeChangeNotificationListener() throws Exception {
        BaseModelMBean mbean = new BaseModelMBean();
        List<String> attributes = new ArrayList<>();
        NotificationListener listener = (notification, handback) -> attributes.add(
                ((AttributeChangeNotification) notification).getAttributeName());

        mbean.addAttributeChangeNotificationListener(listener, "first", null);
        mbean.addAttributeChangeNotificationListener(listener, "second", null);
        mbean.removeAttributeChangeNotificationListener(listener, "first");

        sendAttributeChangeNotification(mbean, "first");
        sendAttributeChangeNotification(mbean, "second");

        Assert.assertEquals(List.of("second"), attributes);

        attributes.clear();
        mbean.removeAttributeChangeNotificationListener(listener, "second");
        sendAttributeChangeNotification(mbean, "second");
        Assert.assertTrue(attributes.isEmpty());
    }


    @Test
    public void testRemoveAttributeFromListenerForAllAttributes() throws Exception {
        BaseModelMBean mbean = new BaseModelMBean();
        List<String> attributes = new ArrayList<>();
        NotificationListener listener = (notification, handback) -> attributes.add(
                ((AttributeChangeNotification) notification).getAttributeName());

        mbean.addAttributeChangeNotificationListener(listener, null, null);
        mbean.removeAttributeChangeNotificationListener(listener, "first");

        sendAttributeChangeNotification(mbean, "first");
        sendAttributeChangeNotification(mbean, "second");

        Assert.assertEquals(List.of("second"), attributes);

        attributes.clear();
        mbean.addAttributeChangeNotificationListener(listener, "first", null);
        sendAttributeChangeNotification(mbean, "first");
        Assert.assertEquals(List.of("first"), attributes);
    }


    @Test
    public void testRemoveAttributeChangeRegistrationPreservesGeneralRegistration() throws Exception {
        BaseModelMBean mbean = new BaseModelMBean();
        List<String> attributes = new ArrayList<>();
        NotificationListener listener = (notification, handback) -> attributes.add(
                ((AttributeChangeNotification) notification).getAttributeName());

        mbean.addNotificationListener(listener, null, null);
        mbean.addAttributeChangeNotificationListener(listener, "first", null);
        mbean.removeAttributeChangeNotificationListener(listener, "first");
        sendAttributeChangeNotification(mbean, "first");

        Assert.assertEquals(List.of("first"), attributes);
    }


    @Test
    public void testRemoveUnknownAttributeChangeNotificationListener() throws Exception {
        BaseModelMBean mbean = new BaseModelMBean();
        NotificationListener listener = (notification, handback) -> {
            // NO-OP
        };

        Assert.assertThrows(ListenerNotFoundException.class,
                () -> mbean.removeAttributeChangeNotificationListener(listener, "first"));
    }


    private static void sendAttributeChangeNotification(BaseModelMBean mbean, String name) throws Exception {
        mbean.sendAttributeChangeNotification(new Attribute(name, "old"), new Attribute(name, "new"));
    }
}
