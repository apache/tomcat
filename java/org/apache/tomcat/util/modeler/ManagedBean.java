/*
 * Copyright 1999-2004 The Apache Software Foundation.
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


import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.management.Descriptor;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.RuntimeOperationsException;
import javax.management.modelmbean.InvalidTargetObjectTypeException;
import javax.management.modelmbean.ModelMBean;
import javax.management.modelmbean.ModelMBeanAttributeInfo;
import javax.management.modelmbean.ModelMBeanConstructorInfo;
import javax.management.modelmbean.ModelMBeanInfo;
import javax.management.modelmbean.ModelMBeanInfoSupport;
import javax.management.modelmbean.ModelMBeanNotificationInfo;
import javax.management.modelmbean.ModelMBeanOperationInfo;


/**
 * <p>Internal configuration information for a managed bean (MBean)
 * descriptor.</p>
 *
 * @author Craig R. McClanahan
 * @version $Revision: 383268 $ $Date: 2006-03-05 03:02:01 +0100 (dim., 05 mars 2006) $
 */

public class ManagedBean implements java.io.Serializable
{
    // ----------------------------------------------------- Instance Variables


    /**
     * The <code>ModelMBeanInfo</code> object that corresponds
     * to this <code>ManagedBean</code> instance.
     */
    transient ModelMBeanInfo info = null;
    protected AttributeInfo attributes[] = new AttributeInfo[0];
    protected String className =
            "org.apache.tomcat.util.modeler.BaseModelMBean";
    protected ConstructorInfo constructors[] = new ConstructorInfo[0];
    protected String description = null;
    protected String domain = null;
    protected String group = null;
    protected String name = null;

    protected List fields = new ArrayList();
    protected NotificationInfo notifications[] = new NotificationInfo[0];
    protected OperationInfo operations[] = new OperationInfo[0];
    protected String type = null;

    /** Constructor. Will add default attributes. 
     *  
     */ 
    public ManagedBean() {
        AttributeInfo ai=new AttributeInfo();
        ai.setName("modelerType");
        ai.setDescription("Type of the modeled resource. Can be set only once");
        ai.setType("java.lang.String");
        ai.setWriteable(false);
        addAttribute(ai);
    }
    
    // ------------------------------------------------------------- Properties


    /**
     * The collection of attributes for this MBean.
     */
    public AttributeInfo[] getAttributes() {
        return (this.attributes);
    }


    /**
     * The fully qualified name of the Java class of the MBean
     * described by this descriptor.  If not specified, the standard JMX
     * class (<code>javax.management.modelmbean.RequiredModeLMBean</code>)
     * will be utilized.
     */
    public String getClassName() {
        return (this.className);
    }

    public void setClassName(String className) {
        this.className = className;
        this.info = null;
    }


    /**
     * The collection of constructors for this MBean.
     */
    public ConstructorInfo[] getConstructors() {
        return (this.constructors);
    }


    /**
     * The human-readable description of this MBean.
     */
    public String getDescription() {
        return (this.description);
    }

    public void setDescription(String description) {
        this.description = description;
        this.info = null;
    }


    /**
     * The (optional) <code>ObjectName</code> domain in which this MBean
     * should be registered in the MBeanServer.
     */
    public String getDomain() {
        return (this.domain);
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }


    /**
     * <p>Return a <code>List</code> of the {@link FieldInfo} objects for
     * the name/value pairs that should be
     * added to the Descriptor created from this metadata.</p>
     */
    public List getFields() {
        return (this.fields);
    }


    /**
     * The (optional) group to which this MBean belongs.
     */
    public String getGroup() {
        return (this.group);
    }

    public void setGroup(String group) {
        this.group = group;
    }


    /**
     * The name of this managed bean, which must be unique among all
     * MBeans managed by a particular MBeans server.
     */
    public String getName() {
        return (this.name);
    }

    public void setName(String name) {
        this.name = name;
        this.info = null;
    }


    /**
     * The collection of notifications for this MBean.
     */
    public NotificationInfo[] getNotifications() {
        return (this.notifications);
    }


    /**
     * The collection of operations for this MBean.
     */
    public OperationInfo[] getOperations() {
        return (this.operations);
    }


    /**
     * The fully qualified name of the Java class of the resource
     * implementation class described by the managed bean described
     * by this descriptor.
     */
    public String getType() {
        return (this.type);
    }

    public void setType(String type) {
        this.type = type;
        this.info = null;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * Add a new attribute to the set of attributes for this MBean.
     *
     * @param attribute The new attribute descriptor
     */
    public void addAttribute(AttributeInfo attribute) {

        synchronized (attributes) {
            AttributeInfo results[] =
                new AttributeInfo[attributes.length + 1];
            System.arraycopy(attributes, 0, results, 0, attributes.length);
            results[attributes.length] = attribute;
            attributes = results;
            this.info = null;
        }

    }


    /**
     * Add a new constructor to the set of constructors for this MBean.
     *
     * @param constructor The new constructor descriptor
     */
    public void addConstructor(ConstructorInfo constructor) {

        synchronized (constructors) {
            ConstructorInfo results[] =
                new ConstructorInfo[constructors.length + 1];
            System.arraycopy(constructors, 0, results, 0, constructors.length);
            results[constructors.length] = constructor;
            constructors = results;
            this.info = null;
        }

    }


    /**
     * <p>Add a new field to the fields associated with the
     * Descriptor that will be created from this metadata.</p>
     *
     * @param field The field to be added
     */
    public void addField(FieldInfo field) {
        fields.add(field);
    }


    /**
     * Add a new notification to the set of notifications for this MBean.
     *
     * @param notification The new notification descriptor
     */
    public void addNotification(NotificationInfo notification) {

        synchronized (notifications) {
            NotificationInfo results[] =
                new NotificationInfo[notifications.length + 1];
            System.arraycopy(notifications, 0, results, 0,
                             notifications.length);
            results[notifications.length] = notification;
            notifications = results;
            this.info = null;
        }

    }


    /**
     * Add a new operation to the set of operations for this MBean.
     *
     * @param operation The new operation descriptor
     */
    public void addOperation(OperationInfo operation) {
        synchronized (operations) {
            OperationInfo results[] =
                new OperationInfo[operations.length + 1];
            System.arraycopy(operations, 0, results, 0, operations.length);
            results[operations.length] = operation;
            operations = results;
            this.info = null;
        }

    }


    /**
     * Create and return a <code>ModelMBean</code> that has been
     * preconfigured with the <code>ModelMBeanInfo</code> information
     * for this managed bean, but is not associated with any particular
     * managed resource.  The returned <code>ModelMBean</code> will
     * <strong>NOT</strong> have been registered with our
     * <code>MBeanServer</code>.
     *
     * @exception InstanceNotFoundException if the managed resource
     *  object cannot be found
     * @exception InvalidTargetObjectTypeException if our MBean cannot
     *  handle object references (should never happen)
     * @exception MBeanException if a problem occurs instantiating the
     *  <code>ModelMBean</code> instance
     * @exception RuntimeOperationsException if a JMX runtime error occurs
     */
    public ModelMBean createMBean()
        throws InstanceNotFoundException,
        InvalidTargetObjectTypeException,
        MBeanException, RuntimeOperationsException {

        return (createMBean(null));

    }


    /**
     * Create and return a <code>ModelMBean</code> that has been
     * preconfigured with the <code>ModelMBeanInfo</code> information
     * for this managed bean, and is associated with the specified
     * managed object instance.  The returned <code>ModelMBean</code>
     * will <strong>NOT</strong> have been registered with our
     * <code>MBeanServer</code>.
     *
     * @param instance Instanced of the managed object, or <code>null</code>
     *  for no associated instance
     *
     * @exception InstanceNotFoundException if the managed resource
     *  object cannot be found
     * @exception InvalidTargetObjectTypeException if our MBean cannot
     *  handle object references (should never happen)
     * @exception MBeanException if a problem occurs instantiating the
     *  <code>ModelMBean</code> instance
     * @exception RuntimeOperationsException if a JMX runtime error occurs
     */
    public ModelMBean createMBean(Object instance)
        throws InstanceNotFoundException,
        InvalidTargetObjectTypeException,
        MBeanException, RuntimeOperationsException {

        // Load the ModelMBean implementation class
        Class clazz = null;
        Exception ex = null;
        try {
            clazz = Class.forName(getClassName());
        } catch (Exception e) {
        }
      
        if( clazz==null ) {  
            try {
                ClassLoader cl= Thread.currentThread().getContextClassLoader();
                if ( cl != null)
                    clazz= cl.loadClass(getClassName());
            } catch (Exception e) {
                ex=e;
            }
        }

        if( clazz==null) { 
            throw new MBeanException
                (ex, "Cannot load ModelMBean class " + getClassName());
        }

        // Create a new ModelMBean instance
        ModelMBean mbean = null;
        try {
            mbean = (ModelMBean) clazz.newInstance();
            mbean.setModelMBeanInfo(createMBeanInfo());
        } catch (MBeanException e) {
            throw e;
        } catch (RuntimeOperationsException e) {
            throw e;
        } catch (Exception e) {
            throw new MBeanException
                (e, "Cannot instantiate ModelMBean of class " +
                 getClassName());
        }

        // Set the managed resource (if any)
        try {
            if (instance != null)
                mbean.setManagedResource(instance, "ObjectReference");
        } catch (InstanceNotFoundException e) {
            throw e;
        } catch (InvalidTargetObjectTypeException e) {
            throw e;
        }
        return (mbean);

    }


    /**
     * Create and return a <code>ModelMBeanInfo</code> object that
     * describes this entire managed bean.
     */
    public ModelMBeanInfo createMBeanInfo() {

        // Return our cached information (if any)
        if (info != null)
            return (info);

        // Create subordinate information descriptors as required
        AttributeInfo attrs[] = getAttributes();
        ModelMBeanAttributeInfo attributes[] =
            new ModelMBeanAttributeInfo[attrs.length];
        for (int i = 0; i < attrs.length; i++)
            attributes[i] = attrs[i].createAttributeInfo();
        
        ConstructorInfo consts[] = getConstructors();
        ModelMBeanConstructorInfo constructors[] =
            new ModelMBeanConstructorInfo[consts.length];
        for (int i = 0; i < consts.length; i++)
            constructors[i] = consts[i].createConstructorInfo();
        NotificationInfo notifs[] = getNotifications();
        ModelMBeanNotificationInfo notifications[] =
            new ModelMBeanNotificationInfo[notifs.length];
        for (int i = 0; i < notifs.length; i++)
            notifications[i] = notifs[i].createNotificationInfo();
        OperationInfo opers[] = getOperations();
        ModelMBeanOperationInfo operations[] =
            new ModelMBeanOperationInfo[opers.length];
        for (int i = 0; i < opers.length; i++)
            operations[i] = opers[i].createOperationInfo();

        /*
        // Add operations for attribute getters and setters as needed
        ArrayList list = new ArrayList();
        for (int i = 0; i < operations.length; i++)
            list.add(operations[i]);
        for (int i = 0; i < attributes.length; i++) {
            Descriptor descriptor = attributes[i].getDescriptor();
            String getMethod = (String) descriptor.getFieldValue("getMethod");
            if (getMethod != null) {
                OperationInfo oper =
                    new OperationInfo(getMethod, true,
                                      attributes[i].getType());
                list.add(oper.createOperationInfo());
            }
            String setMethod = (String) descriptor.getFieldValue("setMethod");
            if (setMethod != null) {
                OperationInfo oper =
                    new OperationInfo(setMethod, false,
                                      attributes[i].getType());
                list.add(oper.createOperationInfo());
            }
        }
        if (list.size() > operations.length)
            operations =
                (ModelMBeanOperationInfo[]) list.toArray(operations);
        */
        
        // Construct and return a new ModelMBeanInfo object
        info = new ModelMBeanInfoSupport
            (getClassName(), getDescription(),
             attributes, constructors, operations, notifications);
        try {
            Descriptor descriptor = info.getMBeanDescriptor();
            Iterator fields = getFields().iterator();
            while (fields.hasNext()) {
                FieldInfo field = (FieldInfo) fields.next();
                descriptor.setField(field.getName(), field.getValue());
            }
            info.setMBeanDescriptor(descriptor);
        } catch (MBeanException e) {
            ;
        }

        return (info);

    }


    /**
     * Return a string representation of this managed bean.
     */
    public String toString() {

        StringBuffer sb = new StringBuffer("ManagedBean[");
        sb.append("name=");
        sb.append(name);
        sb.append(", className=");
        sb.append(className);
        sb.append(", description=");
        sb.append(description);
        if (group != null) {
            sb.append(", group=");
            sb.append(group);
        }
        sb.append(", type=");
        sb.append(type);
        sb.append("]");
        return (sb.toString());

    }


}
