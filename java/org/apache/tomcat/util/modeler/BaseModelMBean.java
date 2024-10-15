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
package org.apache.tomcat.util.modeler;


import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import javax.management.Attribute;
import javax.management.AttributeChangeNotification;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.DynamicMBean;
import javax.management.InstanceNotFoundException;
import javax.management.InvalidAttributeValueException;
import javax.management.ListenerNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanRegistration;
import javax.management.MBeanServer;
import javax.management.Notification;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.RuntimeErrorException;
import javax.management.RuntimeOperationsException;
import javax.management.modelmbean.InvalidTargetObjectTypeException;
import javax.management.modelmbean.ModelMBeanNotificationBroadcaster;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

/*
 * Changes from commons.modeler:
 *
 *  - use DynamicMBean
 *  - remove methods not used in tomcat and redundant/not very generic
 *  - must be created from the ManagedBean - I don't think there were any direct
 *    uses, but now it is required.
 *  - some of the gratuitous flexibility removed - instead this is more predictive and
 *    strict with the use cases.
 *  - all Method and metadata is stored in ManagedBean. BaseModelBMean and ManagedBean act
 *    like Object and Class.
 *  - setModelMBean is no longer called on resources ( not used in tomcat )
 *  - no caching of Methods for now - operations and setters are not called repeatedly in most
 *  management use cases. Getters shouldn't be called very frequently either - and even if they
 *  are, the overhead of getting the method should be small compared with other JMX costs ( RMI, etc ).
 *  We can add getter cache if needed.
 *  - removed unused constructor, fields
 *
 *  TODO:
 *   - clean up catalina.mbeans, stop using weird inheritance
 */

/**
 * <p>Basic implementation of the <code>DynamicMBean</code> interface, which
 * supports the minimal requirements of the interface contract.</p>
 *
 * <p>This can be used directly to wrap an existing java bean, or inside
 * an mlet or anywhere an MBean would be used.
 *
 * Limitations:
 * <ul>
 * <li>Only managed resources of type <code>objectReference</code> are
 *     supported.</li>
 * <li>Caching of attribute values and operation results is not supported.
 *     All calls to <code>invoke()</code> are immediately executed.</li>
 * <li>Persistence of MBean attributes and operations is not supported.</li>
 * <li>All classes referenced as attribute types, operation parameters, or
 *     operation return values must be one of the following:
 *     <ul>
 *     <li>One of the Java primitive types (boolean, byte, char, double,
 *         float, integer, long, short).  Corresponding value will be wrapped
 *         in the appropriate wrapper class automatically.</li>
 *     <li>Operations that return no value should declare a return type of
 *         <code>void</code>.</li>
 *     </ul>
 * <li>Attribute caching is not supported</li>
 * </ul>
 *
 * @author Craig R. McClanahan
 * @author Costin Manolache
 */
public class BaseModelMBean implements DynamicMBean, MBeanRegistration,
        ModelMBeanNotificationBroadcaster {

    private static final Log log = LogFactory.getLog(BaseModelMBean.class);
    private static final StringManager sm = StringManager.getManager(BaseModelMBean.class);

    // ----------------------------------------------------- Instance Variables

    protected ObjectName oname=null;

    /**
     * Notification broadcaster for attribute changes.
     */
    protected BaseNotificationBroadcaster attributeBroadcaster = null;

    /**
     * Notification broadcaster for general notifications.
     */
    protected BaseNotificationBroadcaster generalBroadcaster = null;

    /**
     * Metadata for the mbean instance.
     */
    protected ManagedBean managedBean = null;

    /**
     * The managed resource this MBean is associated with (if any).
     */
    protected Object resource = null;

    // --------------------------------------------------- DynamicMBean Methods
    // TODO: move to ManagedBean
    static final Object[] NO_ARGS_PARAM = new Object[0];

    protected String resourceType = null;

    // key: operation val: invoke method
    //private Hashtable invokeAttMap=new Hashtable();

    @Override
    public Object getAttribute(String name)
        throws AttributeNotFoundException, MBeanException,
            ReflectionException {
        // Validate the input parameters
        if (name == null) {
            throw new RuntimeOperationsException
                (new IllegalArgumentException(sm.getString("baseModelMBean.nullAttributeName")),
                        sm.getString("baseModelMBean.nullAttributeName"));
        }

        if( (resource instanceof DynamicMBean) &&
             ! ( resource instanceof BaseModelMBean )) {
            return ((DynamicMBean)resource).getAttribute(name);
        }

        Method m=managedBean.getGetter(name, this, resource);
        Object result = null;
        try {
            Class<?> declaring = m.getDeclaringClass();
            // workaround for catalina weird mbeans - the declaring class is BaseModelMBean.
            // but this is the catalina class.
            if( declaring.isAssignableFrom(this.getClass()) ) {
                result = m.invoke(this, NO_ARGS_PARAM );
            } else {
                result = m.invoke(resource, NO_ARGS_PARAM );
            }
        } catch (InvocationTargetException e) {
            Throwable t = e.getTargetException();
            if (t == null) {
                t = e;
            }
            if (t instanceof RuntimeException) {
                throw new RuntimeOperationsException
                    ((RuntimeException) t, sm.getString("baseModelMBean.invokeError", name));
            } else if (t instanceof Error) {
                throw new RuntimeErrorException
                    ((Error) t, sm.getString("baseModelMBean.invokeError", name));
            } else {
                throw new MBeanException
                    (e, sm.getString("baseModelMBean.invokeError", name));
            }
        } catch (Exception e) {
            throw new MBeanException
                (e, sm.getString("baseModelMBean.invokeError", name));
        }

        // Return the results of this method invocation
        return result;
    }


    @Override
    public AttributeList getAttributes(String names[]) {

        // Validate the input parameters
        if (names == null) {
            throw new RuntimeOperationsException
                (new IllegalArgumentException(sm.getString("baseModelMBean.nullAttributeNameList")),
                        sm.getString("baseModelMBean.nullAttributeNameList"));
        }

        // Prepare our response, eating all exceptions
        AttributeList response = new AttributeList();
        for (String name : names) {
            try {
                response.add(new Attribute(name, getAttribute(name)));
            } catch (Exception e) {
                // Not having a particular attribute in the response
                // is the indication of a getter problem
            }
        }
        return response;

    }

    public void setManagedBean(ManagedBean managedBean) {
        this.managedBean = managedBean;
    }

    /**
     * Return the <code>MBeanInfo</code> object for this MBean.
     */
    @Override
    public MBeanInfo getMBeanInfo() {
        return managedBean.getMBeanInfo();
    }


    /**
     * {@inheritDoc}
     * <p><strong>IMPLEMENTATION NOTE</strong> - This implementation will
     * attempt to invoke this method on the MBean itself, or (if not
     * available) on the managed resource object associated with this
     * MBean.</p>
     */
    @Override
    public Object invoke(String name, Object params[], String signature[])
        throws MBeanException, ReflectionException
    {
        if( (resource instanceof DynamicMBean) &&
             ! ( resource instanceof BaseModelMBean )) {
            return ((DynamicMBean)resource).invoke(name, params, signature);
        }

        // Validate the input parameters
        if (name == null) {
            throw new RuntimeOperationsException
                (new IllegalArgumentException(sm.getString("baseModelMBean.nullMethodName")),
                        sm.getString("baseModelMBean.nullMethodName"));
        }

        if( log.isTraceEnabled()) {
            log.trace("Invoke " + name);
        }

        Method method= managedBean.getInvoke(name, params, signature, this, resource);

        // Invoke the selected method on the appropriate object
        Object result = null;
        try {
            if( method.getDeclaringClass().isAssignableFrom( this.getClass()) ) {
                result = method.invoke(this, params );
            } else {
                result = method.invoke(resource, params);
            }
        } catch (InvocationTargetException e) {
            Throwable t = e.getTargetException();
            log.error(sm.getString("baseModelMBean.invokeError", name), t );
            if (t == null) {
                t = e;
            }
            if (t instanceof RuntimeException) {
                throw new RuntimeOperationsException
                    ((RuntimeException) t, sm.getString("baseModelMBean.invokeError", name));
            } else if (t instanceof Error) {
                throw new RuntimeErrorException
                    ((Error) t, sm.getString("baseModelMBean.invokeError", name));
            } else {
                throw new MBeanException
                    ((Exception)t, sm.getString("baseModelMBean.invokeError", name));
            }
        } catch (Exception e) {
            log.error(sm.getString("baseModelMBean.invokeError", name), e );
            throw new MBeanException
                (e, sm.getString("baseModelMBean.invokeError", name));
        }

        // Return the results of this method invocation
        return result;

    }

    static Class<?> getAttributeClass(String signature)
        throws ReflectionException
    {
        if (signature.equals(Boolean.TYPE.getName())) {
            return Boolean.TYPE;
        } else if (signature.equals(Byte.TYPE.getName())) {
            return Byte.TYPE;
        } else if (signature.equals(Character.TYPE.getName())) {
            return Character.TYPE;
        } else if (signature.equals(Double.TYPE.getName())) {
            return Double.TYPE;
        } else if (signature.equals(Float.TYPE.getName())) {
            return Float.TYPE;
        } else if (signature.equals(Integer.TYPE.getName())) {
            return Integer.TYPE;
        } else if (signature.equals(Long.TYPE.getName())) {
            return Long.TYPE;
        } else if (signature.equals(Short.TYPE.getName())) {
            return Short.TYPE;
        } else {
            try {
                ClassLoader cl=Thread.currentThread().getContextClassLoader();
                if( cl!=null ) {
                    return cl.loadClass(signature);
                }
            } catch( ClassNotFoundException e ) {
            }
            try {
                return Class.forName(signature);
            } catch (ClassNotFoundException e) {
                throw new ReflectionException(e, sm.getString("baseModelMBean.cnfeForSignature", signature));
            }
        }
    }

    @Override
    public void setAttribute(Attribute attribute)
        throws AttributeNotFoundException, MBeanException,
        ReflectionException
    {
        if( log.isTraceEnabled() ) {
            log.trace("Setting attribute " + this + " " + attribute );
        }

        if( (resource instanceof DynamicMBean) &&
             ! ( resource instanceof BaseModelMBean )) {
            try {
                ((DynamicMBean)resource).setAttribute(attribute);
            } catch (InvalidAttributeValueException e) {
                throw new MBeanException(e);
            }
            return;
        }

        // Validate the input parameters
        if (attribute == null) {
            throw new RuntimeOperationsException
                (new IllegalArgumentException(sm.getString("baseModelMBean.nullAttribute")),
                        sm.getString("baseModelMBean.nullAttribute"));
        }

        String name = attribute.getName();
        Object value = attribute.getValue();

        if (name == null) {
            throw new RuntimeOperationsException
                (new IllegalArgumentException(sm.getString("baseModelMBean.nullAttributeName")),
                        sm.getString("baseModelMBean.nullAttributeName"));
        }

        Object oldValue=null;
        //if( getAttMap.get(name) != null )
        //    oldValue=getAttribute( name );

        Method m=managedBean.getSetter(name,this,resource);

        try {
            if( m.getDeclaringClass().isAssignableFrom( this.getClass()) ) {
                m.invoke(this, new Object[] { value });
            } else {
                m.invoke(resource, new Object[] { value });
            }
        } catch (InvocationTargetException e) {
            Throwable t = e.getTargetException();
            if (t == null) {
                t = e;
            }
            if (t instanceof RuntimeException) {
                throw new RuntimeOperationsException
                    ((RuntimeException) t, sm.getString("baseModelMBean.invokeError", name));
            } else if (t instanceof Error) {
                throw new RuntimeErrorException
                    ((Error) t, sm.getString("baseModelMBean.invokeError", name));
            } else {
                throw new MBeanException
                    (e, sm.getString("baseModelMBean.invokeError", name));
            }
        } catch (Exception e) {
            log.error(sm.getString("baseModelMBean.invokeError", name) , e );
            throw new MBeanException
                (e, sm.getString("baseModelMBean.invokeError", name));
        }
        try {
            sendAttributeChangeNotification(new Attribute( name, oldValue),
                    attribute);
        } catch(Exception ex) {
            log.error(sm.getString("baseModelMBean.notificationError", name), ex);
        }
        //attributes.put( name, value );
//        if( source != null ) {
//            // this mbean is associated with a source - maybe we want to persist
//            source.updateField(oname, name, value);
//        }
    }

    @Override
    public String toString() {
        if( resource==null ) {
            return "BaseModelMbean[" + resourceType + "]";
        }
        return resource.toString();
    }

    @Override
    public AttributeList setAttributes(AttributeList attributes) {
        AttributeList response = new AttributeList();

        // Validate the input parameters
        if (attributes == null) {
            return response;
        }

        // Prepare and return our response, eating all exceptions
        String names[] = new String[attributes.size()];
        int n = 0;
        for (Object attribute : attributes) {
            Attribute item = (Attribute) attribute;
            names[n++] = item.getName();
            try {
                setAttribute(item);
            } catch (Exception e) {
                // Ignore all exceptions
            }
        }

        return getAttributes(names);

    }


    // ----------------------------------------------------- ModelMBean Methods


    /**
     * Get the instance handle of the object against which we execute
     * all methods in this ModelMBean management interface.
     *
     * @return the backend managed object
     * @exception InstanceNotFoundException if the managed resource object
     *  cannot be found
     * @exception InvalidTargetObjectTypeException if the managed resource
     *  object is of the wrong type
     * @exception MBeanException if the initializer of the object throws
     *  an exception
     * @exception RuntimeOperationsException if the managed resource or the
     *  resource type is <code>null</code> or invalid
     */
    public Object getManagedResource()
        throws InstanceNotFoundException, InvalidTargetObjectTypeException,
        MBeanException, RuntimeOperationsException {

        if (resource == null) {
            throw new RuntimeOperationsException
                (new IllegalArgumentException(sm.getString("baseModelMBean.nullResource")),
                        sm.getString("baseModelMBean.nullResource"));
        }

        return resource;

    }


    /**
     * Set the instance handle of the object against which we will execute
     * all methods in this ModelMBean management interface.
     *
     * The caller can provide the mbean instance or the object name to
     * the resource, if needed.
     *
     * @param resource The resource object to be managed
     * @param type The type of reference for the managed resource
     *  ("ObjectReference", "Handle", "IOR", "EJBHandle", or
     *  "RMIReference")
     *
     * @exception InstanceNotFoundException if the managed resource object
     *  cannot be found
     * @exception MBeanException if the initializer of the object throws
     *  an exception
     * @exception RuntimeOperationsException if the managed resource or the
     *  resource type is <code>null</code> or invalid
     */
    public void setManagedResource(Object resource, String type)
        throws InstanceNotFoundException,
        MBeanException, RuntimeOperationsException
    {
        if (resource == null) {
            throw new RuntimeOperationsException
                (new IllegalArgumentException(sm.getString("baseModelMBean.nullResource")),
                        sm.getString("baseModelMBean.nullResource"));
        }

//        if (!"objectreference".equalsIgnoreCase(type))
//            throw new InvalidTargetObjectTypeException(type);

        this.resource = resource;
        this.resourceType = resource.getClass().getName();

//        // Make the resource aware of the model mbean.
//        try {
//            Method m=resource.getClass().getMethod("setModelMBean",
//                    new Class[] {ModelMBean.class});
//            if( m!= null ) {
//                m.invoke(resource, new Object[] {this});
//            }
//        } catch( NoSuchMethodException t ) {
//            // ignore
//        } catch( Throwable t ) {
//            log.error( "Can't set model mbean ", t );
//        }
    }


    // ------------------------------ ModelMBeanNotificationBroadcaster Methods


    @Override
    public void addAttributeChangeNotificationListener
        (NotificationListener listener, String name, Object handback)
        throws IllegalArgumentException {

        if (listener == null) {
            throw new IllegalArgumentException(sm.getString("baseModelMBean.nullListener"));
        }
        if (attributeBroadcaster == null) {
            attributeBroadcaster = new BaseNotificationBroadcaster();
        }

        if( log.isTraceEnabled() ) {
            log.trace("addAttributeNotificationListener " + listener);
        }

        BaseAttributeFilter filter = new BaseAttributeFilter(name);
        attributeBroadcaster.addNotificationListener
            (listener, filter, handback);

    }


    @Override
    public void removeAttributeChangeNotificationListener
        (NotificationListener listener, String name)
        throws ListenerNotFoundException {

        if (listener == null) {
            throw new IllegalArgumentException(sm.getString("baseModelMBean.nullListener"));
        }

        // FIXME - currently this removes *all* notifications for this listener
        if (attributeBroadcaster != null) {
            attributeBroadcaster.removeNotificationListener(listener);
        }

    }


    @Override
    public void sendAttributeChangeNotification
        (AttributeChangeNotification notification)
        throws MBeanException, RuntimeOperationsException {

        if (notification == null) {
            throw new RuntimeOperationsException
                (new IllegalArgumentException(sm.getString("baseModelMBean.nullNotification")),
                        sm.getString("baseModelMBean.nullNotification"));
        }
        if (attributeBroadcaster == null)
         {
            return; // This means there are no registered listeners
        }
        if( log.isTraceEnabled() ) {
            log.trace( "AttributeChangeNotification " + notification );
        }
        attributeBroadcaster.sendNotification(notification);

    }


    @Override
    public void sendAttributeChangeNotification
        (Attribute oldValue, Attribute newValue)
        throws MBeanException, RuntimeOperationsException {

        // Calculate the class name for the change notification
        String type = null;
        if (newValue.getValue() != null) {
            type = newValue.getValue().getClass().getName();
        } else if (oldValue.getValue() != null) {
            type = oldValue.getValue().getClass().getName();
        }
        else {
            return;  // Old and new are both null == no change
        }

        AttributeChangeNotification notification =
            new AttributeChangeNotification
            (this, 1, System.currentTimeMillis(),
             "Attribute value has changed",
             oldValue.getName(), type,
             oldValue.getValue(), newValue.getValue());
        sendAttributeChangeNotification(notification);

    }


    @Override
    public void sendNotification(Notification notification)
        throws MBeanException, RuntimeOperationsException {

        if (notification == null) {
            throw new RuntimeOperationsException
                (new IllegalArgumentException(sm.getString("baseModelMBean.nullNotification")),
                        sm.getString("baseModelMBean.nullNotification"));
        }
        if (generalBroadcaster == null)
         {
            return; // This means there are no registered listeners
        }
        generalBroadcaster.sendNotification(notification);

    }


    @Override
    public void sendNotification(String message)
        throws MBeanException, RuntimeOperationsException {

        if (message == null) {
            throw new RuntimeOperationsException
                (new IllegalArgumentException(sm.getString("baseModelMBean.nullMessage")),
                        sm.getString("baseModelMBean.nullMessage"));
        }
        Notification notification = new Notification
            ("jmx.modelmbean.generic", this, 1, message);
        sendNotification(notification);

    }


    // ---------------------------------------- NotificationBroadcaster Methods


    @Override
    public void addNotificationListener(NotificationListener listener,
                                        NotificationFilter filter,
                                        Object handback)
        throws IllegalArgumentException {

        if (listener == null) {
            throw new IllegalArgumentException(sm.getString("baseModelMBean.nullListener"));
        }

        if( log.isTraceEnabled() ) {
            log.trace("addNotificationListener " + listener);
        }

        if (generalBroadcaster == null) {
            generalBroadcaster = new BaseNotificationBroadcaster();
        }
        generalBroadcaster.addNotificationListener
            (listener, filter, handback);

        // We'll send the attribute change notifications to all listeners ( who care )
        // The normal filtering can be used.
        // The problem is that there is no other way to add attribute change listeners
        // to a model mbean ( AFAIK ). I suppose the spec should be fixed.
        if (attributeBroadcaster == null) {
            attributeBroadcaster = new BaseNotificationBroadcaster();
        }

        if( log.isTraceEnabled() ) {
            log.trace("addAttributeNotificationListener " + listener);
        }

        attributeBroadcaster.addNotificationListener
                (listener, filter, handback);
    }


    @Override
    public MBeanNotificationInfo[] getNotificationInfo() {

        // Acquire the set of application notifications
        MBeanNotificationInfo current[] = getMBeanInfo().getNotifications();
        MBeanNotificationInfo response[] =
            new MBeanNotificationInfo[current.length + 2];
 //       Descriptor descriptor = null;

        // Fill in entry for general notifications
//        descriptor = new DescriptorSupport
//            (new String[] { "name=GENERIC",
//                            "descriptorType=notification",
//                            "log=T",
//                            "severity=5",
//                            "displayName=jmx.modelmbean.generic" });
        response[0] = new MBeanNotificationInfo
            (new String[] { "jmx.modelmbean.generic" },
             "GENERIC",
             "Text message notification from the managed resource");
             //descriptor);

        // Fill in entry for attribute change notifications
//        descriptor = new DescriptorSupport
//            (new String[] { "name=ATTRIBUTE_CHANGE",
//                            "descriptorType=notification",
//                            "log=T",
//                            "severity=5",
//                            "displayName=jmx.attribute.change" });
        response[1] = new MBeanNotificationInfo
            (new String[] { "jmx.attribute.change" },
             "ATTRIBUTE_CHANGE",
             "Observed MBean attribute value has changed");
             //descriptor);

        // Copy remaining notifications as reported by the application
        System.arraycopy(current, 0, response, 2, current.length);
        return response;

    }


    @Override
    public void removeNotificationListener(NotificationListener listener)
        throws ListenerNotFoundException {

        if (listener == null) {
            throw new IllegalArgumentException(sm.getString("baseModelMBean.nullListener"));
        }

        if (generalBroadcaster != null) {
            generalBroadcaster.removeNotificationListener(listener);
        }

        if (attributeBroadcaster != null) {
            attributeBroadcaster.removeNotificationListener(listener);
        }
     }


    public String getModelerType() {
        return resourceType;
    }

    /**
     * @return the fully qualified Java class name of the managed object for this MBean
     */
    public String getClassName() {
        return getModelerType();
    }

    public ObjectName getJmxName() {
        return oname;
    }

    public String getObjectName() {
        if (oname != null) {
            return oname.toString();
        } else {
            return null;
        }
    }


    // -------------------- Registration  --------------------
    // XXX We can add some method patterns here- like setName() and
    // setDomain() for code that doesn't implement the Registration

    @Override
    public ObjectName preRegister(MBeanServer server,
                                  ObjectName name)
            throws Exception
    {
        if( log.isTraceEnabled()) {
            log.trace("preRegister " + resource + " " + name );
        }
        oname=name;
        if( resource instanceof MBeanRegistration ) {
            oname = ((MBeanRegistration)resource).preRegister(server, name );
        }
        return oname;
    }

    @Override
    public void postRegister(Boolean registrationDone) {
        if( resource instanceof MBeanRegistration ) {
            ((MBeanRegistration)resource).postRegister(registrationDone);
        }
    }

    @Override
    public void preDeregister() throws Exception {
        if( resource instanceof MBeanRegistration ) {
            ((MBeanRegistration)resource).preDeregister();
        }
    }

    @Override
    public void postDeregister() {
        if( resource instanceof MBeanRegistration ) {
            ((MBeanRegistration)resource).postDeregister();
        }
    }
}
