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

import org.apache.catalina.core.StandardContext;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.ha.CatalinaCluster;
import org.apache.catalina.tribes.tipis.ReplicatedMap;
import org.apache.catalina.tribes.Channel;
import org.apache.catalina.Loader;
import org.apache.catalina.core.ApplicationContext;
import org.apache.catalina.Globals;
import javax.servlet.ServletContext;
import java.util.AbstractMap;
import org.apache.catalina.tribes.tipis.AbstractReplicatedMap;
import java.util.ArrayList;
import java.util.Iterator;
import javax.servlet.ServletContextAttributeListener;
import javax.servlet.ServletContextAttributeEvent;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import java.util.Enumeration;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.catalina.util.Enumerator;

/**
 * @author Filip Hanik
 * @version 1.0
 */
public class ReplicatedContext extends StandardContext implements LifecycleListener {
    private int mapSendOptions = Channel.SEND_OPTIONS_DEFAULT;
    public static org.apache.juli.logging.Log log = org.apache.juli.logging.LogFactory.getLog( ReplicatedContext.class );
    protected boolean startComplete = false;
    protected static long DEFAULT_REPL_TIMEOUT = 15000;//15 seconds
    
    public void lifecycleEvent(LifecycleEvent event) {
        if ( event.getType() == AFTER_START_EVENT ) 
            startComplete = true;
    }

    public synchronized void start() throws LifecycleException {
        if ( this.started ) return;
        super.addLifecycleListener(this);            
        try {
            CatalinaCluster catclust = (CatalinaCluster)this.getCluster();
            if (this.context == null) this.context = new ReplApplContext(this.getBasePath(), this);
            if ( catclust != null ) {
                ReplicatedMap map = new ReplicatedMap(this,catclust.getChannel(),DEFAULT_REPL_TIMEOUT,
                                                      getName(),getClassLoaders());
                map.setChannelSendOptions(mapSendOptions);
                ((ReplApplContext)this.context).setAttributeMap(map);
                if (getAltDDName() != null) context.setAttribute(Globals.ALT_DD_ATTR, getAltDDName());
            }
            super.start();
        }  catch ( Exception x ) {
            log.error("Unable to start ReplicatedContext",x);
            throw new LifecycleException("Failed to start ReplicatedContext",x);
        }
    }
    
    public synchronized void stop() throws LifecycleException
    {
        ReplicatedMap map = (ReplicatedMap)((ReplApplContext)this.context).getAttributeMap();
        if ( map!=null ) {
            map.breakdown();
        }
        if ( !this.started ) return;
        try {
            super.lifecycle.removeLifecycleListener(this);
        } catch ( Exception x ){
            log.error("Unable to stop ReplicatedContext",x);
            throw new LifecycleException("Failed to stop ReplicatedContext",x);
        } finally {
            this.startComplete = false;
            super.stop();
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
        if (loader != null) classLoader = loader.getClassLoader();
        if ( classLoader == null ) classLoader = Thread.currentThread().getContextClassLoader();
        if ( classLoader == Thread.currentThread().getContextClassLoader() ) {
            return new ClassLoader[] {classLoader};
        } else {
            return new ClassLoader[] {classLoader,Thread.currentThread().getContextClassLoader()};
        }
    }
    
    public ServletContext getServletContext() {
        if (context == null) {
            context = new ReplApplContext(getBasePath(), this);
            if (getAltDDName() != null)
                context.setAttribute(Globals.ALT_DD_ATTR,getAltDDName());
        }

        return ((ReplApplContext)context).getFacade();

    }

    
    protected static class ReplApplContext extends ApplicationContext {
        protected ConcurrentHashMap tomcatAttributes = new ConcurrentHashMap();
        
        public ReplApplContext(String basePath, ReplicatedContext context) {
            super(basePath,context);
        }
        
        protected ReplicatedContext getParent() {
            return (ReplicatedContext)getContext();
        }
        
        protected ServletContext getFacade() {
             return super.getFacade();
        }
        
        public AbstractMap getAttributeMap() {
            return (AbstractMap)this.attributes;
        }
        public void setAttributeMap(AbstractMap map) {
            this.attributes = map;
        }
        
        public void removeAttribute(String name) {
            tomcatAttributes.remove(name);
            //do nothing
            super.removeAttribute(name);
        }
        
        public void setAttribute(String name, Object value) {
            if ( (!getParent().startComplete) || "org.apache.jasper.runtime.JspApplicationContextImpl".equals(name) ){
                tomcatAttributes.put(name,value);
            } else
                super.setAttribute(name,value);
        }
        
        public Object getAttribute(String name) {
            if (tomcatAttributes.containsKey(name) )
                return tomcatAttributes.get(name);
            else 
                return super.getAttribute(name);
        }
        
        public Enumeration getAttributeNames() {
            return new MultiEnumeration(new Enumeration[] {super.getAttributeNames(),new Enumerator(tomcatAttributes.keySet(), true)});
        }
        
    }

    protected static class MultiEnumeration implements Enumeration {
        Enumeration[] e=null;
        public MultiEnumeration(Enumeration[] lists) {
            e = lists;
        }
        public boolean hasMoreElements() {
            for ( int i=0; i<e.length; i++ ) {
                if ( e[i].hasMoreElements() ) return true;
            }
            return false;
        }
        public Object nextElement() {
            for ( int i=0; i<e.length; i++ ) {
                if ( e[i].hasMoreElements() ) return e[i].nextElement();
            }
            return null;

        }
    }

}