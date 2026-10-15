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
import javax.management.Notification;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;

import org.junit.Assert;
import org.junit.Test;

public class TestBaseModelMBeanNotifications {

    private static final class TesterNotificationListener implements NotificationListener {

        private final List<String> received = new ArrayList<>();

        @Override
        public void handleNotification(Notification notification, Object handback) {
            String name = notification instanceof AttributeChangeNotification ?
                    ((AttributeChangeNotification) notification).getAttributeName() : notification.getType();
            received.add(name + ":" + handback);
        }
    }

    private static void send(BaseModelMBean mbean, String attributeName) throws Exception {
        mbean.sendAttributeChangeNotification(new Attribute(attributeName, "old"),
                new Attribute(attributeName, "new"));
    }

    @Test
    public void testCoalescedRemovalKeepsOtherNames() throws Exception {
        BaseModelMBean mbean = new BaseModelMBean();
        TesterNotificationListener listener = new TesterNotificationListener();
        // Same listener and handback: these registrations are coalesced into a
        // single entry with a filter accepting both names
        mbean.addAttributeChangeNotificationListener(listener, "A", "hb");
        mbean.addAttributeChangeNotificationListener(listener, "B", "hb");
        mbean.removeAttributeChangeNotificationListener(listener, "A");
        send(mbean, "A");
        send(mbean, "B");
        Assert.assertEquals(List.of("B:hb"), listener.received);
    }

    @Test
    public void testSingleNameRemovalDoesNotAcceptAll() throws Exception {
        BaseModelMBean mbean = new BaseModelMBean();
        TesterNotificationListener listener = new TesterNotificationListener();
        mbean.addAttributeChangeNotificationListener(listener, "A", "hb");
        mbean.removeAttributeChangeNotificationListener(listener, "A");
        // Removing the only accepted name must remove the entry entirely rather
        // than turning the (now empty) filter into an accept-all filter
        send(mbean, "A");
        send(mbean, "B");
        Assert.assertTrue(listener.received.isEmpty());
    }

    @Test
    public void testSeparateHandbacksRemovedIndependently() throws Exception {
        BaseModelMBean mbean = new BaseModelMBean();
        TesterNotificationListener listener = new TesterNotificationListener();
        mbean.addAttributeChangeNotificationListener(listener, "A", "hb1");
        mbean.addAttributeChangeNotificationListener(listener, "B", "hb2");
        mbean.removeAttributeChangeNotificationListener(listener, "A");
        send(mbean, "A");
        send(mbean, "B");
        Assert.assertEquals(List.of("B:hb2"), listener.received);
    }

    @Test
    public void testAcceptAllRemoval() throws Exception {
        BaseModelMBean mbean = new BaseModelMBean();
        TesterNotificationListener listener = new TesterNotificationListener();
        // A null attribute name registers an accept-all listener
        mbean.addAttributeChangeNotificationListener(listener, null, "hb");
        // A specific name can not be removed from an accept-all registration
        mbean.removeAttributeChangeNotificationListener(listener, "A");
        send(mbean, "A");
        Assert.assertEquals(List.of("A:hb"), listener.received);
        // The accept-all registration is removed by a null name
        mbean.removeAttributeChangeNotificationListener(listener, null);
        listener.received.clear();
        send(mbean, "A");
        Assert.assertTrue(listener.received.isEmpty());
    }

    @Test
    public void testUnknownNameRemovalIsNoOp() throws Exception {
        BaseModelMBean mbean = new BaseModelMBean();
        TesterNotificationListener listener = new TesterNotificationListener();
        mbean.addAttributeChangeNotificationListener(listener, "A", "hb");
        mbean.removeAttributeChangeNotificationListener(listener, "Z");
        send(mbean, "A");
        Assert.assertEquals(List.of("A:hb"), listener.received);
    }

    @Test
    public void testOtherListenerRemovalDoesNotAffectThisListener() throws Exception {
        BaseModelMBean mbean = new BaseModelMBean();
        TesterNotificationListener listener1 = new TesterNotificationListener();
        TesterNotificationListener listener2 = new TesterNotificationListener();
        mbean.addAttributeChangeNotificationListener(listener1, "A", "hb");
        mbean.addAttributeChangeNotificationListener(listener2, "A", "hb");
        mbean.removeAttributeChangeNotificationListener(listener1, "A");
        send(mbean, "A");
        Assert.assertTrue(listener1.received.isEmpty());
        Assert.assertEquals(List.of("A:hb"), listener2.received);
    }

    @Test
    public void testNonAttributeFilterIsNotRemovedByName() throws Exception {
        BaseModelMBean mbean = new BaseModelMBean();
        TesterNotificationListener listener = new TesterNotificationListener();
        NotificationFilter acceptAll = notification -> true;
        // Registered with a general (non attribute) filter via the
        // NotificationBroadcaster method
        mbean.addNotificationListener(listener, acceptAll, "hb");
        mbean.removeAttributeChangeNotificationListener(listener, "A");
        send(mbean, "A");
        Assert.assertEquals(List.of("A:hb"), listener.received);
    }
}
