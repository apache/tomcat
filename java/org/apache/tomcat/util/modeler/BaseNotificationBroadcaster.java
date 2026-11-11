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
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import javax.management.AttributeChangeNotification;
import javax.management.ListenerNotFoundException;
import javax.management.MBeanNotificationInfo;
import javax.management.Notification;
import javax.management.NotificationBroadcaster;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;


/**
 * Implementation of <code>NotificationBroadcaster</code> for attribute change notifications. This class is used by
 * <code>BaseModelMBean</code> to handle notifications of attribute change events to interested listeners.
 */

public class BaseNotificationBroadcaster implements NotificationBroadcaster {

    /**
     * Default constructor.
     */
    public BaseNotificationBroadcaster() {
    }

    // ----------------------------------------------------------- Constructors

    // ----------------------------------------------------- Instance Variables


    /**
     * The set of registered <code>BaseNotificationBroadcasterEntry</code> entries.
     */
    final ArrayList<BaseNotificationBroadcasterEntry> entries = new ArrayList<>();


    // --------------------------------------------------------- Public Methods


    /**
     * Add a notification event listener to this MBean.
     *
     * @param listener Listener that will receive event notifications
     * @param filter   Filter object used to filter event notifications actually delivered, or <code>null</code> for no
     *                     filtering
     * @param handback object to be sent along with event notifications
     *
     * @exception IllegalArgumentException if the listener parameter is null
     */
    @Override
    public void addNotificationListener(NotificationListener listener, NotificationFilter filter, Object handback)
            throws IllegalArgumentException {

        synchronized (entries) {

            /*
             * Optimization to coalesce attribute name filters. The filters are never modified. A filter may be shared
             * with other registrations or broadcasters so the entry is given a new filter instead. Only plain
             * BaseAttributeFilter instances are coalesced. A sub-class may change how notifications are filtered, which
             * a plain filter would not preserve.
             */
            if (isPlainAttributeFilter(filter)) {
                BaseAttributeFilter newFilter = (BaseAttributeFilter) filter;
                for (BaseNotificationBroadcasterEntry item : entries) {
                    if ((item.listener == listener) && isPlainAttributeFilter(item.filter) &&
                            (item.handback == handback)) {
                        String[] newNames = newFilter.getNames();
                        String[] oldNames = ((BaseAttributeFilter) item.filter).getNames();
                        if (newNames.length == 0) {
                            if (oldNames.length != 0) {
                                // Now accepts all attribute names
                                item.filter = new BaseAttributeFilter(null);
                            }
                        } else if (oldNames.length != 0) {
                            BaseAttributeFilter combined = new BaseAttributeFilter(null);
                            for (String oldName : oldNames) {
                                combined.addAttribute(oldName);
                            }
                            for (String newName : newNames) {
                                combined.addAttribute(newName);
                            }
                            item.filter = combined;
                        }
                        return;
                    }
                }
            }

            // General purpose addition of a new entry
            entries.add(new BaseNotificationBroadcasterEntry(listener, filter, handback));
        }

    }


    /**
     * Return an <code>MBeanNotificationInfo</code> object describing the notifications sent by this MBean.
     */
    @Override
    public MBeanNotificationInfo[] getNotificationInfo() {
        return new MBeanNotificationInfo[0];
    }


    /**
     * Remove a notification event listener from this MBean.
     *
     * @param listener The listener to be removed (any and all registrations for this listener will be eliminated)
     *
     * @exception ListenerNotFoundException if this listener is not registered in the MBean
     */
    @Override
    public void removeNotificationListener(NotificationListener listener) throws ListenerNotFoundException {

        synchronized (entries) {
            entries.removeIf(item -> item.listener == listener);
        }

    }


    /**
     * Remove an attribute change notification event listener for a specific attribute name from this MBean, or all
     * attribute change registrations for the listener if the name is <code>null</code>. For a specific name, other
     * registrations of the same listener are left untouched. If a coalesced filter ends up accepting no explicit
     * names, the corresponding entry is removed entirely rather than being turned into an accept-all filter. A
     * registration that accepts all attribute names (a filter with no explicit names, created when the listener was
     * added with a <code>null</code> attribute name) can only be removed by passing a <code>null</code> name here.
     *
     * @param listener The listener to be removed
     * @param name     The attribute name for which the registration should be removed, or <code>null</code> to remove
     *                     all attribute change registrations for the listener
     */
    public void removeAttributeNotificationListener(NotificationListener listener, String name) {

        synchronized (entries) {
            Iterator<BaseNotificationBroadcasterEntry> iterator = entries.iterator();
            while (iterator.hasNext()) {
                BaseNotificationBroadcasterEntry item = iterator.next();
                if (item.listener != listener) {
                    continue;
                }
                if (name == null) {
                    iterator.remove();
                } else if (item.filter instanceof BaseAttributeFilter filter) {
                    String[] names = filter.getNames();
                    if (!isPlainAttributeFilter(filter)) {
                        if (names.length > 0) {
                            // Keep the subclass's filtering behavior and its shared state intact. The subclass may
                            // accept names beyond those reported by getNames(), so exclude only the requested name
                            // from this entry rather than replacing the filter or removing the entry.
                            item.excludeAttribute(name);
                        }
                    } else if (names.length == 1 && name.equals(names[0])) {
                        iterator.remove();
                    } else if (names.length > 1) {
                        // The filter may be shared with other registrations or broadcasters. Replace this entry's
                        // filter rather than changing the shared filter.
                        BaseAttributeFilter replacement = new BaseAttributeFilter(null);
                        boolean removed = false;
                        for (String attributeName : names) {
                            if (name.equals(attributeName)) {
                                removed = true;
                            } else {
                                replacement.addAttribute(attributeName);
                            }
                        }
                        if (removed) {
                            item.filter = replacement;
                        }
                    }
                }
            }
        }

    }


    /**
     * Is the filter a plain <code>BaseAttributeFilter</code> rather than an instance of a sub-class, which may change
     * how notifications are filtered?
     */
    private static boolean isPlainAttributeFilter(NotificationFilter filter) {
        return filter != null && filter.getClass() == BaseAttributeFilter.class;
    }


    /**
     * Send the specified notification to all interested listeners.
     *
     * @param notification The notification to be sent
     */
    public void sendNotification(Notification notification) {

        synchronized (entries) {
            for (BaseNotificationBroadcasterEntry item : entries) {
                if (!item.isNotificationEnabled(notification)) {
                    continue;
                }
                item.listener.handleNotification(notification, item.handback);
            }
        }

    }

}


/**
 * Utility class representing a particular registered listener entry.
 */

class BaseNotificationBroadcasterEntry {

    BaseNotificationBroadcasterEntry(NotificationListener listener, NotificationFilter filter, Object handback) {
        this.listener = listener;
        this.filter = filter;
        this.handback = handback;
    }

    public NotificationFilter filter;

    public Object handback;

    public NotificationListener listener;

    // Accessed only while holding the broadcaster's entries lock.
    private Set<String> excludedAttributes;

    void excludeAttribute(String name) {
        if (excludedAttributes == null) {
            excludedAttributes = new HashSet<>();
        }
        excludedAttributes.add(name);
    }

    boolean isNotificationEnabled(Notification notification) {
        if (excludedAttributes != null && notification instanceof AttributeChangeNotification acn &&
                excludedAttributes.contains(acn.getAttributeName())) {
            return false;
        }
        return filter == null || filter.isNotificationEnabled(notification);
    }

}
