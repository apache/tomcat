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

package org.apache.tomcat.dbcp.dbcp2;

import java.lang.management.ManagementFactory;
import java.util.Objects;

import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * Internal wrapper class that allows JMX to be a noop if absent or disabled.
 *
 * @since 2.2.1
 */
class ObjectNameWrapper {

    private static final Log log = LogFactory.getLog(ObjectNameWrapper.class);

    private static MBeanServer MBEAN_SERVER = getPlatformMBeanServer();

    private static MBeanServer getPlatformMBeanServer() {
        try {
            return ManagementFactory.getPlatformMBeanServer();
        } catch (LinkageError | Exception e) {
            // ignore - JMX not available
            log.debug("Failed to get platform MBeanServer", e);
            return null;
        }
    }

    public static ObjectName unwrap(final ObjectNameWrapper wrapper) {
        return wrapper == null ? null : wrapper.unwrap();
    }

    public static ObjectNameWrapper wrap(final ObjectName objectName) {
        return new ObjectNameWrapper(objectName);
    }

    public static ObjectNameWrapper wrap(final String name) throws MalformedObjectNameException {
        return wrap(new ObjectName(name));
    }

    private final ObjectName objectName;

    public ObjectNameWrapper(final ObjectName objectName) {
        this.objectName = objectName;
    }

    public void registerMBean(final Object object) {
        if (MBEAN_SERVER == null || objectName == null) {
            return;
        }
        try {
            MBEAN_SERVER.registerMBean(object, objectName);
        } catch (LinkageError | Exception e) {
            log.warn("Failed to complete JMX registration for " + objectName, e);
        }
    }

    /**
     * @since 2.7.0
     */
    @Override
    public String toString() {
        return Objects.toString(objectName);
    }

    public void unregisterMBean() {
        if (MBEAN_SERVER == null || objectName == null) {
            return;
        }
        if (MBEAN_SERVER.isRegistered(objectName)) {
            try {
                MBEAN_SERVER.unregisterMBean(objectName);
            } catch (LinkageError | Exception e) {
                log.warn("Failed to complete JMX unregistration for " + objectName, e);
            }
        }
    }

    public ObjectName unwrap() {
        return objectName;
    }

}
