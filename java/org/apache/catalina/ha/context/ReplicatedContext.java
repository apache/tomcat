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
import java.util.HashMap;
import org.apache.catalina.tribes.tipis.LazyReplicatedMap;
import java.util.AbstractMap;

/**
 * @author Filip Hanik
 * @version 1.0
 */
public class ReplicatedContext extends StandardContext {
    private int mapSendOptions = Channel.SEND_OPTIONS_DEFAULT;
    public static org.apache.juli.logging.Log log = org.apache.juli.logging.LogFactory.getLog( ReplicatedContext.class );

    protected static long DEFAULT_REPL_TIMEOUT = 15000;//15 seconds
    


    public synchronized void start() throws LifecycleException {
        if ( this.started ) return;
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
        } catch ( Exception x ){
            log.error("Unable to stop ReplicatedContext",x);
            throw new LifecycleException("Failed to stop ReplicatedContext",x);
        } finally {
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
        return ((ReplApplContext)context).getFacade();

    }

    
    protected static class ReplApplContext extends ApplicationContext {
        public ReplApplContext(String basePath, StandardContext context) {
            super(basePath,context);
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

    }


}