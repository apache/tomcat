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

package org.apache.catalina.mbeans;


import javax.management.MBeanException;
import javax.management.MBeanServer;
import javax.management.RuntimeOperationsException;

import org.apache.catalina.Valve;
import org.apache.catalina.core.StandardHost;
import org.apache.tomcat.util.modeler.BaseModelMBean;
import org.apache.tomcat.util.modeler.ManagedBean;
import org.apache.tomcat.util.modeler.Registry;


/**
 * <p>A <strong>ModelMBean</strong> implementation for the
 * <code>org.apache.catalina.core.StandardHost</code> component.</p>
 *
 * @author Amy Roh
 * @version $Revision$ $Date$
 */

public class StandardHostMBean extends BaseModelMBean {

    /**
     * The <code>MBeanServer</code> for this application.
     */
    private static MBeanServer mserver = MBeanUtils.createServer();

    // ----------------------------------------------------------- Constructors


    /**
     * Construct a <code>ModelMBean</code> with default
     * <code>ModelMBeanInfo</code> information.
     *
     * @exception MBeanException if the initializer of an object
     *  throws an exception
     * @exception RuntimeOperationsException if an IllegalArgumentException
     *  occurs
     */
    public StandardHostMBean()
        throws MBeanException, RuntimeOperationsException {

        super();

    }


    // ------------------------------------------------------------- Attributes



    // ------------------------------------------------------------- Operations


   /**
     * Add an alias name that should be mapped to this Host
     *
     * @param alias The alias to be added
     *
     * @exception Exception if an MBean cannot be created or registered
     */
    public void addAlias(String alias)
        throws Exception {

        StandardHost host = (StandardHost) this.resource;
        host.addAlias(alias);

    }


   /**
     * Return the set of alias names for this Host
     *
     * @exception Exception if an MBean cannot be created or registered
     */
    public String [] findAliases()
        throws Exception {

        StandardHost host = (StandardHost) this.resource;
        return host.findAliases();

    }


   /**
     * Return the MBean Names of the Valves assoicated with this Host
     *
     * @exception Exception if an MBean cannot be created or registered
     */
    public String [] getValves()
        throws Exception {

        Registry registry = MBeanUtils.createRegistry();
        StandardHost host = (StandardHost) this.resource;
        String mname = MBeanUtils.createManagedName(host);
        ManagedBean managed = registry.findManagedBean(mname);
        String domain = null;
        if (managed != null) {
            domain = managed.getDomain();
        }
        if (domain == null)
            domain = mserver.getDefaultDomain();
        Valve [] valves = host.getValves();
        String [] mbeanNames = new String[valves.length];
        for (int i = 0; i < valves.length; i++) {
            mbeanNames[i] =
                MBeanUtils.createObjectName(domain, valves[i]).toString();
        }

        return mbeanNames;

    }


   /**
     * Return the specified alias name from the aliases for this Host
     *
     * @param alias Alias name to be removed
     *
     * @exception Exception if an MBean cannot be created or registered
     */
    public void removeAlias(String alias)
        throws Exception {

        StandardHost host = (StandardHost) this.resource;
        host.removeAlias(alias);

    }


}
