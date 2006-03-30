/*
 * Copyright 2001-2002,2004 The Apache Software Foundation.
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


package org.apache.tomcat.util.modeler;


import java.util.Enumeration;
import java.util.Hashtable;

import javax.management.AttributeChangeNotification;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServer;
import javax.management.MBeanServerNotification;
import javax.management.Notification;
import javax.management.NotificationBroadcaster;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.naming.Context;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

// EXPERIMENTAL. It may fit better in tomcat jndi impl.


/**
 *
 * Link between JNDI and JMX. JNDI can be used for persistence ( it is
 * an API for storing hierarchical data and a perfect fit for that ), as
 * well as an alternate view of the MBean registry.
 *
 * If this component is enabled, all MBeans will be registered in JNDI, and
 * all attributes that are set via JMX can be stored in a DirContext.
 *
 * This acts as a "recorder" for creation of mbeans and attribute changes
 * done via JMX.
 *
 * XXX How can we control ( filter ) which mbeans will be registere ? Or
 * attributes ?
 * XXX How can we get the beans and attributes loaded before jndijmx ?
 *
 * The intended use:
 * - do whatever you want to start the application
 * - load JndiJmx as an mbean
 * - make changes via JMX. All changes are recorded
 * - you can use JndiJmx to save the changes in a Jndi context.
 * - you can use JndiJmx to load changes from a JndiContext and replay them.
 *
 * The main benefit is that only changed attributes are saved, and the Jndi
 * layer can preserve most of the original structure of the config file. The
 * alternative is to override the config files with config info extracted
 * from the live objects - but it's very hard to save only what was actually
 * changed and preserve structure and comments.
 *
 * @author Costin Manolache
 */
public class JndiJmx extends BaseModelMBean implements NotificationListener {


    private static Log log= LogFactory.getLog(JndiJmx.class);

    protected Context componentContext;
    protected Context descriptorContext;
    protected Context configContext;

    MBeanServer mserver;

    /**
     * Protected constructor to require use of the factory create method.
     */
    public JndiJmx() throws MBeanException {
        super(JndiJmx.class.getName());
    }


    /** If a JNDI context is set, all components
     * will be registered in the context.
     *
     * @param ctx
     */
    public void setComponentContext(Context ctx) {
        this.componentContext= ctx;
    }

    /** JNDI context for component descriptors ( metadata ).
     *
     * @param ctx
     */
    public void setDescriptorContext(Context ctx) {
        this.descriptorContext= ctx;
    }

    /** JNDI context where attributes will be stored for persistence
     *
     */
    public void setConfigContext( Context ctx ) {
        this.configContext= ctx;
    }

    // --------------------  Registration/unregistration --------------------
    // temp - will only set in the jndi contexts
    Hashtable attributes=new Hashtable();
    Hashtable instances=new Hashtable();

    public void handleNotification(Notification notification, Object handback)
    {
        // register/unregister mbeans in jndi
        if( notification instanceof MBeanServerNotification ) {
            MBeanServerNotification msnot=(MBeanServerNotification)notification;

            ObjectName oname=msnot.getMBeanName();

            if( "jmx.mbean.created".equalsIgnoreCase( notification.getType() )) {
                try {
                    Object mbean=mserver.getObjectInstance(oname);

                    if( log.isDebugEnabled() )
                        log.debug( "MBean created " + oname + " " + mbean);

                    // XXX add filter support
                    if( mbean instanceof NotificationBroadcaster ) {
                        // register for attribute changes
                        NotificationBroadcaster nb=(NotificationBroadcaster)mbean;
                        nb.addNotificationListener(this, null, null);
                        if( log.isDebugEnabled() )
                            log.debug( "Add attribute change listener");
                    }

                    instances.put( oname.toString(), mbean );
                } catch( InstanceNotFoundException ex ) {
                    log.error( "Instance not found for the created object", ex );
                }
            }
            if( "jmx.mbean.deleted".equalsIgnoreCase( notification.getType() )) {
                instances.remove(oname.toString());
            }
        }

        // set attributes in jndi
       //     if( "jmx.attribute.changed".equals( notification.getType() )) {
        if( notification instanceof AttributeChangeNotification) {

            AttributeChangeNotification anotif=(AttributeChangeNotification)notification;
            String name=anotif.getAttributeName();
            Object value=anotif.getNewValue();
            Object source=anotif.getSource();
            String mname=null;

            Hashtable mbeanAtt=(Hashtable)attributes.get( source );
            if( mbeanAtt==null ) {
                mbeanAtt=new Hashtable();
                attributes.put( source, mbeanAtt);
                if( log.isDebugEnabled())
                    log.debug("First attribute for " + source );
            }
            mbeanAtt.put( name, anotif );

            log.debug( "Attribute change notification " + name + " " + value + " " + source );

        }

    }

    public String dumpStatus() throws Exception
    {
        StringBuffer sb=new StringBuffer();
        Enumeration en=instances.keys();
        while (en.hasMoreElements()) {
            String on = (String) en.nextElement();
            Object mbean=instances.get(on);
            Hashtable mbeanAtt=(Hashtable)attributes.get(mbean);

            sb.append( "<mbean class=\"").append(on).append("\">");
            sb.append( "\n");
            Enumeration attEn=mbeanAtt.keys();
            while (attEn.hasMoreElements()) {
                String an = (String) attEn.nextElement();
                AttributeChangeNotification anotif=
                        (AttributeChangeNotification)mbeanAtt.get(an);
                sb.append("  <attribute name=\"").append(an).append("\" ");
                sb.append("value=\"").append(anotif.getNewValue()).append("\">");
                sb.append( "\n");
            }


            sb.append( "</mbean>");
            sb.append( "\n");
        }
        return sb.toString();
    }

    public void replay() throws Exception
    {


    }


    public void init() throws Exception
    {

        MBeanServer mserver=(MBeanServer)Registry.getRegistry().getMBeanServer();
        ObjectName delegate=new ObjectName("JMImplementation:type=MBeanServerDelegate");

        // XXX need to extract info about previously loaded beans

        // we'll know of all registered beans
        mserver.addNotificationListener(delegate, this, null, null );

    }

}
