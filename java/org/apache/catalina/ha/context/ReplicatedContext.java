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
package org.apache.catalina.ha.context;

import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.ServletContext;

import org.apache.catalina.Globals;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Loader;
import org.apache.catalina.core.ApplicationContext;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.ha.CatalinaCluster;
import org.apache.catalina.tribes.Channel;
import org.apache.catalina.tribes.tipis.AbstractReplicatedMap.MapOwner;
import org.apache.catalina.tribes.tipis.ReplicatedMap;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

public class ReplicatedContext extends StandardContext implements MapOwner {
    private int mapSendOptions = Channel.SEND_OPTIONS_DEFAULT;
    private static final Log log = LogFactory.getLog(ReplicatedContext.class);
    protected static final long DEFAULT_REPL_TIMEOUT = 15000;// 15 seconds
    private static final StringManager sm = StringManager.getManager(ReplicatedContext.class);

    /**
     * Start this component and implement the requirements of
     * {@link org.apache.catalina.util.LifecycleBase#startInternal()}.
     *
     * @exception LifecycleException if this component detects a fatal error that prevents this component from being
     *                                   used
     */
    @Override
    protected void startInternal() throws LifecycleException {
        super.startInternal();
        try {
            CatalinaCluster catclust = (CatalinaCluster) this.getCluster();
            if (catclust != null) {
                ReplicatedMap<String,Object> map = new ReplicatedMap<>(this, catclust.getChannel(),
                        DEFAULT_REPL_TIMEOUT, getName(), getClassLoaders());
                map.setChannelSendOptions(mapSendOptions);
                ((ReplApplContext) this.context).setAttributeMap(map);
            }
        } catch (Exception x) {
            log.error(sm.getString("replicatedContext.startUnable", getName()), x);
            throw new LifecycleException(sm.getString("replicatedContext.startFailed", getName()), x);
        }
    }

    /**
     * Stop this component and implement the requirements of
     * {@link org.apache.catalina.util.LifecycleBase#stopInternal()}.
     *
     * @exception LifecycleException if this component detects a fatal error that prevents this component from being
     *                                   used
     */
    @Override
    protected void stopInternal() throws LifecycleException {

        Map<String,Object> map = ((ReplApplContext) this.context).getAttributeMap();

        super.stopInternal();

        if (map instanceof ReplicatedMap) {
            ((ReplicatedMap<?,?>) map).breakdown();
        }

    }


    public void setMapSendOptions(int mapSendOptions) {
        this.mapSendOptions = mapSendOptions;
    }

    public int getMapSendOptions() {
        return mapSendOptions;
    }

    public ClassLoader[] getClassLoaders() {
        Loader loader = null;
        ClassLoader classLoader = null;
        loader = this.getLoader();
        if (loader != null) {
            classLoader = loader.getClassLoader();
        }
        Thread currentThread = Thread.currentThread();
        if (classLoader == null) {
            classLoader = currentThread.getContextClassLoader();
        }
        if (classLoader == currentThread.getContextClassLoader()) {
            return new ClassLoader[] { classLoader };
        } else {
            return new ClassLoader[] { classLoader, currentThread.getContextClassLoader() };
        }
    }

    @Override
    public ServletContext getServletContext() {
        if (context == null) {
            context = new ReplApplContext(this);
            if (getAltDDName() != null) {
                context.setAttribute(Globals.ALT_DD_ATTR, getAltDDName());
            }
        }

        return ((ReplApplContext) context).getFacade();

    }


    protected static class ReplApplContext extends ApplicationContext {
        protected final Map<String,Object> tomcatAttributes = new ConcurrentHashMap<>();

        public ReplApplContext(ReplicatedContext context) {
            super(context);
        }

        protected ReplicatedContext getParent() {
            return (ReplicatedContext) getContext();
        }

        @Override
        protected ServletContext getFacade() {
            return super.getFacade();
        }

        public Map<String,Object> getAttributeMap() {
            return this.attributes;
        }

        public void setAttributeMap(Map<String,Object> map) {
            this.attributes = map;
        }

        @Override
        public void removeAttribute(String name) {
            tomcatAttributes.remove(name);
            // do nothing
            super.removeAttribute(name);
        }

        @Override
        public void setAttribute(String name, Object value) {
            if (name == null) {
                throw new IllegalArgumentException(sm.getString("applicationContext.setAttribute.namenull"));
            }
            if (value == null) {
                removeAttribute(name);
                return;
            }
            if ((!getParent().getState().isAvailable()) ||
                    "org.apache.jasper.runtime.JspApplicationContextImpl".equals(name)) {
                tomcatAttributes.put(name, value);
            } else {
                super.setAttribute(name, value);
            }
        }

        @Override
        public Object getAttribute(String name) {
            Object obj = tomcatAttributes.get(name);
            if (obj == null) {
                return super.getAttribute(name);
            } else {
                return obj;
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public Enumeration<String> getAttributeNames() {
            Set<String> names = new HashSet<>(attributes.keySet());

            return new MultiEnumeration<>(
                    new Enumeration[] { super.getAttributeNames(), Collections.enumeration(names) });
        }
    }

    protected static class MultiEnumeration<T> implements Enumeration<T> {
        private final Enumeration<T>[] enumerations;

        public MultiEnumeration(Enumeration<T>[] enumerations) {
            this.enumerations = enumerations;
        }

        @Override
        public boolean hasMoreElements() {
            for (Enumeration<T> enumeration : enumerations) {
                if (enumeration.hasMoreElements()) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public T nextElement() {
            for (Enumeration<T> enumeration : enumerations) {
                if (enumeration.hasMoreElements()) {
                    return enumeration.nextElement();
                }
            }
            return null;

        }
    }

    @Override
    public void objectMadePrimary(Object key, Object value) {
        // noop
    }
}