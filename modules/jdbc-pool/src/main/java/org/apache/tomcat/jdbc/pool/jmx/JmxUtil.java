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
package org.apache.tomcat.jdbc.pool.jmx;


import java.lang.management.ManagementFactory;

import javax.management.InstanceNotFoundException;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.jdbc.pool.util.MessageFactory;

/**
 * Utility class for JMX registration of JDBC pool components.
 */
public class JmxUtil {

    /**
     * Constructs a new JmxUtil.
     */
    private JmxUtil() {
    }

    private static final Log log = LogFactory.getLog(JmxUtil.class);

    /**
     * Register an object as an MBean.
     *
     * @param base    the base object name
     * @param keyprop the key property
     * @param obj     the object to register
     *
     * @return the registered object name, or {@code null} on failure. A registration the MBean server refused - because
     *             the name is already in use by another MBean, for instance - is such a failure, so a non-{@code null}
     *             name always means this call is the one that published the object under that name
     */
    public static ObjectName registerJmx(ObjectName base, String keyprop, Object obj) {
        ObjectName oname = null;
        try {
            ObjectName name = getObjectName(base, keyprop);
            if (name != null) {
                ManagementFactory.getPlatformMBeanServer().registerMBean(obj, name);
                // Only a publish the MBean server accepted is a registration:
                // handing back the name of a refused one would have the caller
                // treat it as its own, and its later unregistration would then
                // take the name away from the MBean that really owns it
                oname = name;
            }
        } catch (Exception e) {
            log.error(MessageFactory.get("jmxUtil.jmxRegistrationFailed"), e);
        }
        return oname;
    }

    /**
     * Unregister an MBean.
     *
     * @param oname the object name to unregister, or {@code null} to release
     *            nothing
     *
     * @return {@code true} if the name is no longer registered - either this
     *             call took it down, or it was already gone, or there was
     *             nothing to release; {@code false} if the MBean server refused
     *             to unregister it, which means the MBean is still published
     *             under that name and, since callers drop their handle
     *             regardless, held by nothing for the rest of the JVM's life
     */
    public static boolean unregisterJmx(ObjectName oname) {
        if (oname == null) {
            return true;
        }
        try {
            ManagementFactory.getPlatformMBeanServer().unregisterMBean(oname);
            return true;
        } catch (InstanceNotFoundException e) {
            // The MBean is already down - the end state this call was asked to
            // bring about, not a failure, and no caller can act on it
            log.debug(MessageFactory.get("jmxUtil.jmxUnregistrationFailed") + " " + oname);
            return true;
        } catch (Exception e) {
            // The MBean is still registered, and this name is now owned by
            // nobody: say so, with the name, so an operator can take it down
            log.error(MessageFactory.get("jmxUtil.jmxUnregistrationRefused", oname), e);
            return false;
        }
    }

    private static ObjectName getObjectName(ObjectName base, String keyprop) throws MalformedObjectNameException {
        if (base == null) {
            return null;
        }
        StringBuilder OnameStr = new StringBuilder(base.toString());
        if (keyprop != null) {
            OnameStr.append(keyprop);
        }
        ObjectName oname = new ObjectName(OnameStr.toString());
        return oname;
    }
}