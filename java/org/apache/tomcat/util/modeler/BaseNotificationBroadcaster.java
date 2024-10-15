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

import javax.management.ListenerNotFoundException;
import javax.management.MBeanNotificationInfo;
import javax.management.Notification;
import javax.management.NotificationBroadcaster;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;


/**
 * Implementation of <code>NotificationBroadcaster</code> for attribute
 * change notifications.  This class is used by <code>BaseModelMBean</code> to
 * handle notifications of attribute change events to interested listeners.
 *
 * @author Craig R. McClanahan
 * @author Costin Manolache
 */

public class BaseNotificationBroadcaster implements NotificationBroadcaster {


    // ----------------------------------------------------------- Constructors


    // ----------------------------------------------------- Instance Variables


    /**
     * The set of registered <code>BaseNotificationBroadcasterEntry</code>
     * entries.
     */
    protected ArrayList<BaseNotificationBroadcasterEntry> entries =
            new ArrayList<>();


    // --------------------------------------------------------- Public Methods


    /**
     * Add a notification event listener to this MBean.
     *
     * @param listener Listener that will receive event notifications
     * @param filter Filter object used to filter event notifications
     *  actually delivered, or <code>null</code> for no filtering
     * @param handback Handback object to be sent along with event
     *  notifications
     *
     * @exception IllegalArgumentException if the listener parameter is null
     */
    @Override
    public void addNotificationListener(NotificationListener listener,
                                        NotificationFilter filter,
                                        Object handback)
        throws IllegalArgumentException {

        synchronized (entries) {

            // Optimization to coalesce attribute name filters
            if (filter instanceof BaseAttributeFilter) {
                BaseAttributeFilter newFilter = (BaseAttributeFilter) filter;
                for (BaseNotificationBroadcasterEntry item : entries) {
                    if ((item.listener == listener) &&
                        (item.filter != null) &&
                        (item.filter instanceof BaseAttributeFilter) &&
                        (item.handback == handback)) {
                        BaseAttributeFilter oldFilter =
                            (BaseAttributeFilter) item.filter;
                        String newNames[] = newFilter.getNames();
                        String oldNames[] = oldFilter.getNames();
                        if (newNames.length == 0) {
                            oldFilter.clear();
                        } else {
                            if (oldNames.length != 0) {
                                for (String newName : newNames) {
                                    oldFilter.addAttribute(newName);
                                }
                            }
                        }
                        return;
                    }
                }
            }

            // General purpose addition of a new entry
            entries.add(new BaseNotificationBroadcasterEntry
                        (listener, filter, handback));
        }

    }


    /**
     * Return an <code>MBeanNotificationInfo</code> object describing the
     * notifications sent by this MBean.
     */
    @Override
    public MBeanNotificationInfo[] getNotificationInfo() {
        return new MBeanNotificationInfo[0];
    }


    /**
     * Remove a notification event listener from this MBean.
     *
     * @param listener The listener to be removed (any and all registrations
     *  for this listener will be eliminated)
     *
     * @exception ListenerNotFoundException if this listener is not
     *  registered in the MBean
     */
    @Override
    public void removeNotificationListener(NotificationListener listener)
        throws ListenerNotFoundException {

        synchronized (entries) {
            entries.removeIf(item -> item.listener == listener);
        }

    }


    /**
     * Send the specified notification to all interested listeners.
     *
     * @param notification The notification to be sent
     */
    public void sendNotification(Notification notification) {

        synchronized (entries) {
            for (BaseNotificationBroadcasterEntry item : entries) {
                if ((item.filter != null) &&
                    (!item.filter.isNotificationEnabled(notification))) {
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

    BaseNotificationBroadcasterEntry(NotificationListener listener,
                                            NotificationFilter filter,
                                            Object handback) {
        this.listener = listener;
        this.filter = filter;
        this.handback = handback;
    }

    public NotificationFilter filter = null;

    public Object handback = null;

    public NotificationListener listener = null;

}
