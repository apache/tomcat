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

import java.util.concurrent.CopyOnWriteArrayList;

import javax.management.MBeanNotificationInfo;

/**
 * <p>Internal configuration information for a <code>Notification</code>
 * descriptor.</p>
 *
 * @author Craig R. McClanahan
 */
public class NotificationInfo extends FeatureInfo {

    private static final long serialVersionUID = -6319885418912650856L;

    // ----------------------------------------------------- Instance Variables

    protected CopyOnWriteArrayList<String> notifTypes = new CopyOnWriteArrayList<>();

    // ------------------------------------------------------------- Properties


    /**
     * @return the set of notification types for this MBean.
     */
    public String[] getNotifTypes() {
        return this.notifTypes.toArray(new String[0]);
    }


    // --------------------------------------------------------- Public Methods


    /**
     * Add a new notification type to the set managed by an MBean.
     *
     * @param notifType The new notification type
     */
    public void addNotifType(String notifType) {

        this.notifTypes.add(notifType);
        afterNodeChanged();
    }


    @Override
    protected MBeanNotificationInfo buildMBeanInfo() {
        return new MBeanNotificationInfo
                (getNotifTypes(), getName(), getDescription());
    }


    /**
     * Return a string representation of this notification descriptor.
     */
    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder("NotificationInfo[");
        sb.append("name=");
        sb.append(name);
        sb.append(", description=");
        sb.append(description);
        sb.append(", notifTypes=");
        sb.append(notifTypes.size());
        sb.append(']');
        return sb.toString();
    }
}
