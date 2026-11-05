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
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;

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

    // Not instantiable: this class only holds the static registration helpers
    private JmxUtil() {
    }

    private static final Log log = LogFactory.getLog(JmxUtil.class);

    /** How many refused unregistrations are kept for a later retry. */
    private static final int MAX_pendingUnregistrations = 64;

    /**
     * Names whose unregistration the MBean server refused. Every later unregistration retries them, so a transient
     * refusal does not leave the MBean published - and its component pinned - for the life of the JVM. The queue is
     * bounded: a permanent refusal cannot grow it without limit.
     */
    private static final ConcurrentLinkedQueue<ObjectName> pendingUnregistrations =
            new ConcurrentLinkedQueue<>();

    /**
     * Register an object as an MBean.
     *
     * @param base    the base object name, or {@code null} to register nothing -
     *             the skip a component whose own registration is absent relies
     *             on, so it is deliberate and unlogged
     * @param keyprop the key property
     * @param obj     the object to register
     *
     * @return the registered object name, or {@code null} on failure - or on the
     *             absent-base skip. A registration the MBean server refused - because
     *             the name is already in use by another MBean, for instance - is such
     *             a failure, so a non-{@code null} name always means this call is the
     *             one that published the object under that name
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
            // The log is the only record this failure ever gets, and the JDK
            // message of a malformed composed name does not quote that name:
            // name the pieces, so a refused registration is attributable when
            // several pools share the platform server.
            log.error(MessageFactory.get("jmxUtil.jmxRegistrationFailed", base, keyprop,
                    obj == null ? null : obj.getClass().getName()), e);
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
     *             to unregister it, which means the MBean is normally still
     *             published under that name and, since callers drop their
     *             handle regardless, held by nothing for the rest of the JVM's
     *             life (an MBean whose {@code postDeregister()} throws reports
     *             the same failure although the server already took it down;
     *             the pool's own MBeans never do)
     */
    public static boolean unregisterJmx(ObjectName oname) {
        retryPendingUnregistrations();
        if (oname == null) {
            return true;
        }
        try {
            ManagementFactory.getPlatformMBeanServer().unregisterMBean(oname);
            return true;
        } catch (InstanceNotFoundException e) {
            // The MBean is already down - the end state this call was asked to
            // bring about, not a failure, and no caller can act on it
            log.debug(MessageFactory.get("jmxUtil.jmxAlreadyUnregistered") + " " + oname);
            return true;
        } catch (Exception e) {
            // Normally the MBean kept its name and that name is now owned by
            // nobody, so remember it for a later retry and say so, with the
            // name, so an operator can take it down if it stays refused.
            rememberPendingUnregistration(oname);
            log.error(MessageFactory.get("jmxUtil.jmxUnregistrationRefused", oname), e);
            return false;
        }
    }

    /**
     * Retries the unregistrations the server refused earlier. A name that has since become unregistrable is removed
     * from the pending set; a name that is still refused stays there silently, the first refusal already having been
     * reported.
     */
    private static void retryPendingUnregistrations() {
        if (pendingUnregistrations.isEmpty()) {
            return;
        }
        for (Iterator<ObjectName> it = pendingUnregistrations.iterator(); it.hasNext();) {
            ObjectName pending = it.next();
            try {
                ManagementFactory.getPlatformMBeanServer().unregisterMBean(pending);
                pendingUnregistrations.remove(pending);
            } catch (InstanceNotFoundException e) {
                // The MBean went down through another path after all
                pendingUnregistrations.remove(pending);
            } catch (Exception e) {
                // Still refused; the first refusal was already reported and a
                // retry must not flood the log
                if (log.isDebugEnabled()) {
                    log.debug("Jmx unregistration of [" + pending + "] still refused: " + e);
                }
            }
        }
    }

    /**
     * Remembers a refused name for a later retry, up to the bounded capacity.
     *
     * @param oname the name whose unregistration was refused
     */
    private static void rememberPendingUnregistration(ObjectName oname) {
        if (pendingUnregistrations.contains(oname)) {
            return;
        }
        if (pendingUnregistrations.size() >= MAX_pendingUnregistrations) {
            log.warn(MessageFactory.get("jmxUtil.jmxUnregistrationRetryLimit", oname));
            return;
        }
        pendingUnregistrations.add(oname);
    }

    /*
     * Concatenates base and keyprop into one object name, so the key
     * property must carry its own leading comma and a value the ObjectName
     * grammar accepts unquoted (or already quoted): a missing comma mis-parses
     * into the base's last key or throws, and the concatenation validates
     * neither - safe today because every caller passes an internal constant
     * as the key property and a previously registered (so valid, non-pattern)
     * name as the base. A null base is the documented skip: it returns null.
     */
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