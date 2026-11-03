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
        private final List<AttributeChangeNotification> changes = new ArrayList<>();
        private final List<Long> sequences = new ArrayList<>();

        @Override
        public void handleNotification(Notification notification, Object handback) {
            sequences.add(Long.valueOf(notification.getSequenceNumber()));
            String name;
            if (notification instanceof AttributeChangeNotification) {
                AttributeChangeNotification acn = (AttributeChangeNotification) notification;
                changes.add(acn);
                name = acn.getAttributeName();
            } else {
                name = notification.getType();
            }
            received.add(name + ":" + handback);
        }
    }

    public static class TesterResource {

        private String value;

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
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
    public void testNullNameRemovalRemovesAllAttributeRegistrations() throws Exception {
        BaseModelMBean mbean = new BaseModelMBean();
        TesterNotificationListener listener = new TesterNotificationListener();
        TesterNotificationListener otherListener = new TesterNotificationListener();
        // Cover coalesced names, a separate handback and an accept-all registration.
        mbean.addAttributeChangeNotificationListener(listener, "A", "hb1");
        mbean.addAttributeChangeNotificationListener(listener, "B", "hb1");
        mbean.addAttributeChangeNotificationListener(listener, "C", "hb2");
        mbean.addAttributeChangeNotificationListener(listener, null, "hb3");
        mbean.addAttributeChangeNotificationListener(otherListener, "A", "other");

        mbean.removeAttributeChangeNotificationListener(listener, null);
        send(mbean, "A");
        send(mbean, "B");
        send(mbean, "C");
        send(mbean, "D");

        Assert.assertTrue(listener.received.isEmpty());
        Assert.assertEquals(List.of("A:other"), otherListener.received);
    }

    @Test
    public void testNullNameRemovalKeepsGeneralNotifications() throws Exception {
        BaseModelMBean mbean = new BaseModelMBean();
        TesterNotificationListener listener = new TesterNotificationListener();
        mbean.addNotificationListener(listener, notification -> true, "hb");

        mbean.removeAttributeChangeNotificationListener(listener, null);
        send(mbean, "A");
        Assert.assertTrue(listener.received.isEmpty());

        mbean.sendNotification("message");
        Assert.assertEquals(List.of("jmx.modelmbean.generic:hb"), listener.received);
    }

    @Test
    public void testSharedFilterRemovalKeepsOtherListener() throws Exception {
        BaseModelMBean mbean = new BaseModelMBean();
        TesterNotificationListener listener1 = new TesterNotificationListener();
        TesterNotificationListener listener2 = new TesterNotificationListener();
        BaseAttributeFilter filter = new BaseAttributeFilter("A");
        filter.addAttribute("B");
        // The same filter may be supplied for independent registrations.
        mbean.addNotificationListener(listener1, filter, "hb1");
        mbean.addNotificationListener(listener2, filter, "hb2");

        mbean.removeAttributeChangeNotificationListener(listener1, "A");
        send(mbean, "A");
        send(mbean, "B");

        Assert.assertEquals(List.of("B:hb1"), listener1.received);
        Assert.assertEquals(List.of("A:hb2", "B:hb2"), listener2.received);
    }

    /*
     * A filter may be shared between registrations (BaseModelMBean.addNotificationListener() uses the same filter for
     * the general and the attribute registration). Coalescing registrations must not change the shared filter.
     */
    @Test
    public void testCoalescingNamesDoesNotChangeSharedFilter() throws Exception {
        BaseModelMBean mbean = new BaseModelMBean();
        TesterNotificationListener listener1 = new TesterNotificationListener();
        TesterNotificationListener listener2 = new TesterNotificationListener();
        BaseAttributeFilter filter = new BaseAttributeFilter("A");
        mbean.addNotificationListener(listener1, filter, "hb1");
        mbean.addNotificationListener(listener2, filter, "hb2");

        // Same listener and handback so the registrations are coalesced
        mbean.addAttributeChangeNotificationListener(listener1, "B", "hb1");
        send(mbean, "A");
        send(mbean, "B");

        Assert.assertEquals(List.of("A:hb1", "B:hb1"), listener1.received);
        Assert.assertEquals(List.of("A:hb2"), listener2.received);
        Assert.assertArrayEquals(new String[] { "A" }, filter.getNames());
    }

    @Test
    public void testCoalescingAcceptAllDoesNotChangeSharedFilter() throws Exception {
        BaseModelMBean mbean = new BaseModelMBean();
        TesterNotificationListener listener1 = new TesterNotificationListener();
        TesterNotificationListener listener2 = new TesterNotificationListener();
        BaseAttributeFilter filter = new BaseAttributeFilter("A");
        mbean.addNotificationListener(listener1, filter, "hb1");
        mbean.addNotificationListener(listener2, filter, "hb2");

        mbean.addAttributeChangeNotificationListener(listener1, null, "hb1");
        send(mbean, "Z");

        Assert.assertEquals(List.of("Z:hb1"), listener1.received);
        Assert.assertTrue(listener2.received.isEmpty());
        Assert.assertArrayEquals(new String[] { "A" }, filter.getNames());
    }

    @Test
    public void testCoalescingKeepsAcceptAll() throws Exception {
        BaseModelMBean mbean = new BaseModelMBean();
        TesterNotificationListener listener = new TesterNotificationListener();
        mbean.addAttributeChangeNotificationListener(listener, null, "hb");
        // The accept-all registration is not narrowed by a later named registration
        mbean.addAttributeChangeNotificationListener(listener, "A", "hb");
        send(mbean, "Z");

        Assert.assertEquals(List.of("Z:hb"), listener.received);
    }

    /*
     * A sub-class of the filter may change how notifications are filtered. Its registration is never coalesced with
     * another registration as that would replace it with a plain filter.
     */
    @Test
    public void testSubclassFilterIsNotCoalesced() throws Exception {
        BaseModelMBean mbean = new BaseModelMBean();
        TesterNotificationListener listener = new TesterNotificationListener();
        BaseAttributeFilter rejectAll = new BaseAttributeFilter("A") {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isNotificationEnabled(Notification notification) {
                return false;
            }
        };
        mbean.addNotificationListener(listener, rejectAll, "hb");
        mbean.addAttributeChangeNotificationListener(listener, "B", "hb");
        send(mbean, "A");
        send(mbean, "B");

        Assert.assertEquals(List.of("B:hb"), listener.received);
    }

    @Test
    public void testSharedSubclassFilterRemovalKeepsOtherListener() throws Exception {
        BaseModelMBean mbean = new BaseModelMBean();
        TesterNotificationListener listener1 = new TesterNotificationListener();
        TesterNotificationListener listener2 = new TesterNotificationListener();
        BaseAttributeFilter filter = new BaseAttributeFilter("A") {
            private static final long serialVersionUID = 1L;
        };
        filter.addAttribute("B");
        mbean.addNotificationListener(listener1, filter, "hb1");
        mbean.addNotificationListener(listener2, filter, "hb2");

        mbean.removeAttributeChangeNotificationListener(listener1, "A");
        send(mbean, "A");
        send(mbean, "B");
        Assert.assertEquals(List.of("B:hb1"), listener1.received);
        Assert.assertEquals(List.of("A:hb2", "B:hb2"), listener2.received);

        // Removing the remaining name must not turn the registration into an accept-all filter.
        listener1.received.clear();
        listener2.received.clear();
        mbean.removeAttributeChangeNotificationListener(listener1, "B");
        send(mbean, "A");
        send(mbean, "B");
        send(mbean, "C");
        Assert.assertTrue(listener1.received.isEmpty());
        Assert.assertEquals(List.of("A:hb2", "B:hb2"), listener2.received);
    }

    @Test
    public void testSubclassFilterRemovalPreservesCustomFiltering() throws Exception {
        BaseModelMBean mbean = new BaseModelMBean();
        TesterNotificationListener listener = new TesterNotificationListener();
        BaseAttributeFilter filter = new BaseAttributeFilter("A") {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isNotificationEnabled(Notification notification) {
                return super.isNotificationEnabled(notification) &&
                        !"C".equals(((AttributeChangeNotification) notification).getAttributeName());
            }
        };
        filter.addAttribute("B");
        filter.addAttribute("C");
        mbean.addNotificationListener(listener, filter, "hb");

        mbean.removeAttributeChangeNotificationListener(listener, "A");
        send(mbean, "A");
        send(mbean, "B");
        send(mbean, "C");
        Assert.assertEquals(List.of("B:hb"), listener.received);

        // Changes to the original subclass filter still apply to the registration.
        filter.addAttribute("D");
        send(mbean, "D");
        Assert.assertEquals(List.of("B:hb", "D:hb"), listener.received);
    }

    @Test
    public void testSubclassFilterRemovalKeepsOtherAcceptedNames() throws Exception {
        BaseModelMBean mbean = new BaseModelMBean();
        TesterNotificationListener listener = new TesterNotificationListener();
        BaseAttributeFilter filter = new BaseAttributeFilter("A") {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isNotificationEnabled(Notification notification) {
                return notification instanceof AttributeChangeNotification &&
                        ("A".equals(((AttributeChangeNotification) notification).getAttributeName()) ||
                                "B".equals(((AttributeChangeNotification) notification).getAttributeName()));
            }
        };
        mbean.addNotificationListener(listener, filter, "hb");

        // A subclass may accept more names than getNames() reports.
        mbean.removeAttributeChangeNotificationListener(listener, "A");
        send(mbean, "A");
        send(mbean, "B");
        Assert.assertEquals(List.of("B:hb"), listener.received);
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
    public void testSetAttributeNotificationContainsOldValue() throws Exception {
        ManagedBean managedBean = new ManagedBean();
        managedBean.setName("Value");
        managedBean.setType("Value");
        AttributeInfo attributeInfo = new AttributeInfo();
        attributeInfo.setName("value");
        attributeInfo.setDescription("The value");
        attributeInfo.setType("java.lang.String");
        managedBean.addAttribute(attributeInfo);

        TesterResource resource = new TesterResource();
        resource.setValue("initial");
        BaseModelMBean mbean = (BaseModelMBean) managedBean.createMBean(resource);

        TesterNotificationListener listener = new TesterNotificationListener();
        mbean.addAttributeChangeNotificationListener(listener, "value", "hb");
        mbean.setAttribute(new Attribute("value", "updated"));

        Assert.assertEquals(1, listener.changes.size());
        AttributeChangeNotification change = listener.changes.get(0);
        Assert.assertEquals("initial", change.getOldValue());
        Assert.assertEquals("updated", change.getNewValue());
    }

    @Test
    public void testSequenceNumbersIncrease() throws Exception {
        BaseModelMBean mbean = new BaseModelMBean();
        TesterNotificationListener listener = new TesterNotificationListener();
        mbean.addAttributeChangeNotificationListener(listener, "A", "hb");
        send(mbean, "A");
        send(mbean, "A");
        Assert.assertEquals(2, listener.changes.size());
        Assert.assertEquals(1L, listener.changes.get(0).getSequenceNumber());
        Assert.assertEquals(2L, listener.changes.get(1).getSequenceNumber());

        // General notifications share the same sequence counter
        mbean.addNotificationListener(listener, null, null);
        mbean.sendNotification("message");
        Assert.assertEquals(3, listener.sequences.size());
        Assert.assertEquals(3L, listener.sequences.get(2).longValue());
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
