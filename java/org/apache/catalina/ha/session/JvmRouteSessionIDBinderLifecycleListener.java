/*
 * Copyright 1999,2004-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.catalina.ha.session;

import javax.management.DynamicMBean;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.ha.CatalinaCluster;
import org.apache.catalina.ha.ClusterListener;
import org.apache.catalina.util.StringManager;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.modeler.ManagedBean;
import org.apache.tomcat.util.modeler.Registry;

/**
 * Register new JvmRouteSessionIDBinderListener to receive Session ID changes.
 * 
 * add following at your server.xml Host section
 * 
 * <pre>
 *        &lt;Host &gt;... 
 *          &lt;Listener className=&quot;org.apache.catalina.ha.session.JvmRouteSessionIDBinderLifecycleListener&quot; /&gt;
 *          &lt;Cluster ...&gt;
 *        &lt;/Host&gt;
 * </pre>
 * FIXME add Engine support
 * @deprecated
 * @author Peter Rossbach
 */
public class JvmRouteSessionIDBinderLifecycleListener implements
        LifecycleListener {
    private static Log log = LogFactory
            .getLog(JvmRouteSessionIDBinderLifecycleListener.class);

    /**
     * The descriptive information string for this implementation.
     */
    private static final String info = "org.apache.catalina.ha.session.JvmRouteSessionIDBinderLifecycleListener/1.0";

    /**
     * The string resources for this package.
     */
    protected static final StringManager sm = StringManager
            .getManager(Constants.Package);

    private boolean enabled = true;

    private MBeanServer mserver = null;

    private Registry registry = null;

    private ClusterListener sessionMoverListener;

    /*
     * start and stop cluster
     * 
     * @see org.apache.catalina.LifecycleListener#lifecycleEvent(org.apache.catalina.LifecycleEvent)
     */
    public void lifecycleEvent(LifecycleEvent event) {

        if (enabled && event.getSource() instanceof StandardHost) {

            if (Lifecycle.AFTER_START_EVENT.equals(event.getType())) {
                if (log.isDebugEnabled())
                    log.debug(sm.getString("jvmRoute.listener.started"));
                startSessionIDListener((StandardHost) event.getSource());
            } else if (Lifecycle.BEFORE_STOP_EVENT.equals(event.getType())) {
                if (log.isDebugEnabled())
                    log.debug(sm.getString("jvmRoute.listener.stopped"));
                stopSessionIDListener((StandardHost) event.getSource());
            }
        }
    }

    /**
     * stop sessionID binder at cluster
     * 
     * @param host
     *            clustered host
     */
    protected void stopSessionIDListener(StandardHost host) {
        if (sessionMoverListener != null) {
            CatalinaCluster cluster = (CatalinaCluster) host.getCluster();
            cluster.removeClusterListener(sessionMoverListener);
            if (mserver != null) {
                try {
                    ObjectName objectName = getObjectName(host);
                    mserver.unregisterMBean(objectName);
                } catch (Exception e) {
                    log.error(e);
                }
            }
        }
    }

    /**
     * @param host
     * @return The object name
     * @throws MalformedObjectNameException
     */
    protected ObjectName getObjectName(StandardHost host) throws MalformedObjectNameException {
        ObjectName objectName = new ObjectName(
                host.getDomain()
                        + ":type=Listener,name=JvmRouteSessionIDBinderListener,host=" + host.getName());
        return objectName;
    }

    /**
     * start sessionID mover at cluster
     * 
     * @param host
     *            clustered host
     */
    protected void startSessionIDListener(StandardHost host) {
        try {
            ObjectName objectName = null;
            getMBeanServer();
            objectName = getObjectName(host);
            if (mserver.isRegistered(objectName)) {
                if (log.isInfoEnabled())
                    log.info(sm.getString("jvmRoute.run.already"));
                return;
            }
            sessionMoverListener = new JvmRouteSessionIDBinderListener();
            mserver.registerMBean(getManagedBean(sessionMoverListener),
                    objectName);
            CatalinaCluster cluster = (CatalinaCluster) host.getCluster();
            sessionMoverListener.setCluster(cluster);
            ((JvmRouteSessionIDBinderListener) sessionMoverListener).start();
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
        }
    }

    protected MBeanServer getMBeanServer() throws Exception {
        if (mserver == null) {
            if (MBeanServerFactory.findMBeanServer(null).size() > 0) {
                mserver = (MBeanServer) MBeanServerFactory
                        .findMBeanServer(null).get(0);
            } else {
                mserver = MBeanServerFactory.createMBeanServer();
            }
            registry = Registry.getRegistry(null, null);
            registry.loadMetadata(this.getClass().getResourceAsStream(
                    "mbeans-descriptors.xml"));
        }
        return (mserver);
    }

    /**
     * Returns the ModelMBean
     * 
     * @param object
     *            The Object to get the ModelMBean for
     * @return The ModelMBean
     * @throws Exception
     *             If an error occurs this constructors throws this exception
     */
    protected DynamicMBean getManagedBean(Object object) throws Exception {
        DynamicMBean mbean = null;
        if (registry != null) {
            ManagedBean managedBean = registry.findManagedBean(object
                    .getClass().getName());
            mbean = managedBean.createMBean(object);
        }
        return mbean;
    }

    /**
     * @return Returns the enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * @param enabled
     *            The enabled to set.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Return descriptive information about this Listener implementation and the
     * corresponding version number, in the format
     * <code>&lt;description&gt;/&lt;version&gt;</code>.
     */
    public String getInfo() {

        return (info);

    }
}
