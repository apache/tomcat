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
package org.apache.catalina.ha.backend;

/* for MBean to read ready and busy */

import java.util.Set;

import javax.management.MBeanServer;
import javax.management.ObjectInstance;
import javax.management.ObjectName;

import org.apache.tomcat.util.modeler.Registry;
import org.apache.tomcat.util.res.StringManager;

/*
 * Listener to provider informations to mod_heartbeat.c
 * *msg_format = "v=%u&ready=%u&busy=%u"; (message to send).
 * send the multicast message using the format...
 * what about the bind(IP. port) only IP makes sense (for the moment).
 * BTW:v  = version :-)
 */
public class CollectedInfo {

    private static final StringManager sm = StringManager.getManager(CollectedInfo.class);

    /* Collect info via JMX */
    protected MBeanServer mBeanServer = null;
    protected ObjectName objName = null;

    int ready;
    int busy;

    int port = 0;
    String host = null;

    public CollectedInfo(String host, int port) throws Exception {
        init(host, port);
    }

    public void init(String host, int port) throws Exception {
        int iport = 0;
        String shost = null;
        mBeanServer = Registry.getRegistry(null, null).getMBeanServer();
        String onStr = "*:type=ThreadPool,*";
        ObjectName objectName = new ObjectName(onStr);
        Set<ObjectInstance> set = mBeanServer.queryMBeans(objectName, null);
        for (ObjectInstance oi : set) {
            objName = oi.getObjectName();
            String subtype = objName.getKeyProperty("subType");
            if (subtype != null && subtype.equals("SocketProperties")) {
                objName = null;
                continue;
            }
            String name = objName.getKeyProperty("name");
            name = name.replace("\"", "");

            // Example names:
            // ajp-nio-8009
            // ajp-nio-127.0.0.1-8009
            // ajp-nio-0:0:0:0:0:0:0:1-8009
            // ajp-nio-10.36.116.209-8009
            String[] elenames = name.split("-");
            String sport = elenames[elenames.length - 1];
            iport = Integer.parseInt(sport);
            if (elenames.length == 4) {
                shost = elenames[2];
            }

            if (port == 0 && host == null) {
                break; /* Done: take the first one */
            }
            if (iport == port) {
                if (host == null) {
                    break; /* Done: return the first with the right port */
                } else if (shost != null && shost.compareTo(host) == 0) {
                    break; /* Done port and host are the expected ones */
                }
            }
            objName = null;
            shost = null;
        }
        if (objName == null) {
            throw new Exception(sm.getString("collectedInfo.noConnector", host, Integer.valueOf(port)));
        }
        this.port = iport;
        this.host = shost;

    }

    public void refresh() throws Exception {
        if (mBeanServer == null || objName == null) {
            throw new Exception(sm.getString("collectedInfo.notInitialized"));
        }
        Integer imax = (Integer) mBeanServer.getAttribute(objName, "maxThreads");

        // the currentThreadCount could be 0 before the threads are created...
        // Integer iready = (Integer) mBeanServer.getAttribute(objName, "currentThreadCount");

        Integer ibusy = (Integer) mBeanServer.getAttribute(objName, "currentThreadsBusy");

        busy = ibusy.intValue();
        ready = imax.intValue() - ibusy.intValue();
    }
}
