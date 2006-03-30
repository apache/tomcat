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


import java.io.Serializable;
import java.lang.reflect.Method;

import javax.management.Descriptor;
import javax.management.modelmbean.ModelMBeanAttributeInfo;


/**
 * <p>Internal configuration information for an <code>Attribute</code>
 * descriptor.</p>
 *
 * @author Craig R. McClanahan
 * @version $Revision: 155428 $ $Date: 2005-02-26 14:12:25 +0100 (sam., 26 févr. 2005) $
 */

public class AttributeInfo extends FeatureInfo implements Serializable {
    static final long serialVersionUID = -2511626862303972143L;

    // ----------------------------------------------------- Instance Variables


    /**
     * The <code>ModelMBeanAttributeInfo</code> object that corresponds
     * to this <code>AttributeInfo</code> instance.
     */
    protected transient ModelMBeanAttributeInfo info = null;
    protected String displayName = null;
    protected String getMethod = null;
    protected String setMethod = null;

    protected transient Method getMethodObj = null;
    protected transient Method setMethodObj = null;

    protected boolean readable = true;
    protected boolean writeable = true;

    protected boolean is = false;
    protected String type = null;

    protected String persist;
    protected String defaultStringValue;
    // ------------------------------------------------------------- Properties


    /**
     * Override the <code>description</code> property setter.
     *
     * @param description The new description
     */
    public void setDescription(String description) {
        super.setDescription(description);
        this.info = null;
    }

    /**
     * Override the <code>name</code> property setter.
     *
     * @param name The new name
     */
    public void setName(String name) {
        super.setName(name);
        this.info = null;
    }

    /**
     * The display name of this attribute.
     */
    public String getDisplayName() {
        return (this.displayName);
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    /**
     * The name of the property getter method, if non-standard.
     */
    public String getGetMethod() {
        return (this.getMethod);
    }

    public void setGetMethod(String getMethod) {
        this.getMethod = getMethod;
        this.info = null;
    }

    public Method getGetMethodObj() {
        return getMethodObj;
    }

    public void setGetMethodObj(Method getMethodObj) {
        this.getMethodObj = getMethodObj;
    }

    public Method getSetMethodObj() {
        return setMethodObj;
    }

    public void setSetMethodObj(Method setMethodObj) {
        this.setMethodObj = setMethodObj;
    }

    /**
     * Is this a boolean attribute with an "is" getter?
     */
    public boolean isIs() {
        return (this.is);
    }

    public void setIs(boolean is) {
        this.is = is;
        this.info = null;
    }


    /**
     * Is this attribute readable by management applications?
     */
    public boolean isReadable() {
        return (this.readable);
    }

    public void setReadable(boolean readable) {
        this.readable = readable;
        this.info = null;
    }


    /**
     * The name of the property setter method, if non-standard.
     */
    public String getSetMethod() {
        return (this.setMethod);
    }

    public void setSetMethod(String setMethod) {
        this.setMethod = setMethod;
        this.info = null;
    }


    /**
     * The fully qualified Java class name of this attribute.
     */
    public String getType() {
        return (this.type);
    }

    public void setType(String type) {
        this.type = type;
        this.info = null;
    }


    /**
     * Is this attribute writeable by management applications?
     */
    public boolean isWriteable() {
        return (this.writeable);
    }

    public void setWriteable(boolean writeable) {
        this.writeable = writeable;
        this.info = null;
    }

    /** Persistence policy.
     * All persistent attributes should have this attribute set.
     * Valid values:
     *   ???
     */
    public String getPersist() {
        return persist;
    }

    public void setPersist(String persist) {
        this.persist = persist;
    }

    /** Default value. If set, it can provide info to the user and
     * it can be used by persistence mechanism to generate a more compact
     * representation ( a value may not be saved if it's default )
     */
    public String getDefault() {
        return defaultStringValue;
    }

    public void setDefault(String defaultStringValue) {
        this.defaultStringValue = defaultStringValue;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * Create and return a <code>ModelMBeanAttributeInfo</code> object that
     * corresponds to the attribute described by this instance.
     */
    public ModelMBeanAttributeInfo createAttributeInfo() {
        // Return our cached information (if any)
        if (info != null)
            return (info);
        if((getMethodObj != null) || (setMethodObj != null) ) {
            try {
                info=new ModelMBeanAttributeInfo(getName(), getDescription(),
                                        getMethodObj,  setMethodObj);
                return info;
            } catch( Exception ex) {
                ex.printStackTrace();
            }
        }

        // Create and return a new information object
        info = new ModelMBeanAttributeInfo
            (getName(), getType(), getDescription(),
             isReadable(), isWriteable(), false);
        Descriptor descriptor = info.getDescriptor();
        if (getDisplayName() != null)
            descriptor.setField("displayName", getDisplayName());
        if (isReadable()) {
            if (getGetMethod() != null)
                descriptor.setField("getMethod", getGetMethod());
            else
                descriptor.setField("getMethod",
                                    getMethodName(getName(), true, isIs()));
        }
        if (isWriteable()) {
            if (getSetMethod() != null)
                descriptor.setField("setMethod", getSetMethod());
            else
                descriptor.setField("setMethod",
                                    getMethodName(getName(), false, false));
        }
        addFields(descriptor);
        info.setDescriptor(descriptor);
        return (info);

    }


    /**
     * Return a string representation of this attribute descriptor.
     */
    public String toString() {

        StringBuffer sb = new StringBuffer("AttributeInfo[");
        sb.append("name=");
        sb.append(name);
        sb.append(", description=");
        sb.append(description);
        if (!readable) {
            sb.append(", readable=");
            sb.append(readable);
        }
        sb.append(", type=");
        sb.append(type);
        if (!writeable) {
            sb.append(", writeable=");
            sb.append(writeable);
        }
        sb.append("]");
        return (sb.toString());

    }


    // -------------------------------------------------------- Private Methods


    /**
     * Create and return the name of a default property getter or setter
     * method, according to the specified values.
     *
     * @param name Name of the property itself
     * @param getter Do we want a get method (versus a set method)?
     * @param is If returning a getter, do we want the "is" form?
     */
    private String getMethodName(String name, boolean getter, boolean is) {

        StringBuffer sb = new StringBuffer();
        if (getter) {
            if (is)
                sb.append("is");
            else
                sb.append("get");
        } else
            sb.append("set");
        sb.append(Character.toUpperCase(name.charAt(0)));
        sb.append(name.substring(1));
        return (sb.toString());

    }


}
