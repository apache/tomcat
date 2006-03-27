/*
 *  Copyright 1999-2004 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.coyote.http11;

import javax.management.MBeanRegistration;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.commons.modeler.Registry;
import org.apache.coyote.RequestInfo;
import org.apache.tomcat.util.threads.ThreadPool;
import org.apache.tomcat.util.threads.ThreadWithAttributes;


/**
 * Abstract the protocol implementation, including threading, etc.
 * Processor is single threaded and specific to stream-based protocols,
 * will not fit Jk protocols like JNI.
 *
 * @author Remy Maucherat
 * @author Costin Manolache
 */
public class Http11Protocol extends Http11BaseProtocol implements MBeanRegistration
{
    public Http11Protocol() {
    }
    
    protected Http11ConnectionHandler createConnectionHandler() {
        return new JmxHttp11ConnectionHandler( this ) ;
    }

    ObjectName tpOname;
    ObjectName rgOname;

    public void start() throws Exception {
        if( this.domain != null ) {
            try {
                // XXX We should be able to configure it separately
                // XXX It should be possible to use a single TP
                tpOname=new ObjectName
                    (domain + ":" + "type=ThreadPool,name=" + getName());
                if ("ms".equals(getStrategy())) {
                    Registry.getRegistry(null, null)
                        .registerComponent(ep, tpOname, null );
                } else {
                    Registry.getRegistry(null, null)
                        .registerComponent(tp, tpOname, null );
                }
                tp.setName(getName());
                tp.setDaemon(false);
                tp.addThreadPoolListener(new MXPoolListener(this, tp));
            } catch (Exception e) {
                log.error("Can't register threadpool" );
            }
            rgOname=new ObjectName
                (domain + ":type=GlobalRequestProcessor,name=" + getName());
            Registry.getRegistry(null, null).registerComponent
                ( cHandler.global, rgOname, null );
        }

        super.start();
    }

    public void destroy() throws Exception {
        super.destroy();
        if( tpOname!=null )
            Registry.getRegistry(null, null).unregisterComponent(tpOname);
        if( rgOname != null )
            Registry.getRegistry(null, null).unregisterComponent(rgOname);
    }

    // --------------------  Connection handler --------------------

    static class MXPoolListener implements ThreadPool.ThreadPoolListener {
        MXPoolListener( Http11Protocol proto, ThreadPool control ) {

        }

        public void threadStart(ThreadPool tp, Thread t) {
        }

        public void threadEnd(ThreadPool tp, Thread t) {
            // Register our associated processor
            // TP uses only TWA
            ThreadWithAttributes ta=(ThreadWithAttributes)t;
            Object tpData[]=ta.getThreadData(tp);
            if( tpData==null ) return;
            // Weird artifact - it should be cleaned up, but that may break something
            // and it won't gain us too much
            if( tpData[1] instanceof Object[] ) {
                tpData=(Object [])tpData[1];
            }
            ObjectName oname=(ObjectName)tpData[Http11BaseProtocol.THREAD_DATA_OBJECT_NAME];
            if( oname==null ) return;
            Registry.getRegistry(null, null).unregisterComponent(oname);
            Http11Processor processor =
                (Http11Processor) tpData[Http11Protocol.THREAD_DATA_PROCESSOR];
            RequestInfo rp=processor.getRequest().getRequestProcessor();
            rp.setGlobalProcessor(null);
        }
    }

    static class JmxHttp11ConnectionHandler extends Http11ConnectionHandler  {
        Http11Protocol proto;
        static int count=0;

        JmxHttp11ConnectionHandler( Http11Protocol proto ) {
            super(proto);
            this.proto = proto ;
        }

        public void setAttribute( String name, Object value ) {
        }

        public void setServer( Object o ) {
        }

        public Object[] init() {

            Object thData[]=super.init();

            // was set up by supper
            Http11Processor  processor = (Http11Processor)
                    thData[ Http11BaseProtocol.THREAD_DATA_PROCESSOR];

            if( proto.getDomain() != null ) {
                try {
                    RequestInfo rp=processor.getRequest().getRequestProcessor();
                    rp.setGlobalProcessor(global);
                    ObjectName rpName=new ObjectName
                        (proto.getDomain() + ":type=RequestProcessor,worker="
                         + proto.getName() +",name=HttpRequest" + count++ );
                    Registry.getRegistry(null, null).registerComponent( rp, rpName, null);
                    thData[Http11BaseProtocol.THREAD_DATA_OBJECT_NAME]=rpName;
                } catch( Exception ex ) {
                    log.warn("Error registering request");
                }
            }

            return  thData;
        }
    }

    // -------------------- Various implementation classes --------------------


    protected String domain;
    protected ObjectName oname;
    protected MBeanServer mserver;

    public ObjectName getObjectName() {
        return oname;
    }

    public String getDomain() {
        return domain;
    }

    public ObjectName preRegister(MBeanServer server,
                                  ObjectName name) throws Exception {
        oname=name;
        mserver=server;
        domain=name.getDomain();
        return name;
    }

    public void postRegister(Boolean registrationDone) {
    }

    public void preDeregister() throws Exception {
    }

    public void postDeregister() {
    }

}
