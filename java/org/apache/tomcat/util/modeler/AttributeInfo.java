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

import java.io.Serial;

import javax.management.MBeanAttributeInfo;

/**
 * <p>Internal configuration information for an <code>Attribute</code>
 * descriptor.</p>
 *
 * @author Craig R. McClanahan
 */
public class AttributeInfo extends FeatureInfo {
    @Serial
    private static final long serialVersionUID = -2511626862303972143L;

    // ----------------------------------------------------- Instance Variables
    protected String displayName = null;

    // Information about the method to use
    protected String getMethod = null;
    protected String setMethod = null;
    protected boolean readable = true;
    protected boolean writeable = true;
    protected boolean is = false;

    // ------------------------------------------------------------- Properties

    /**
     * @return the display name of this attribute.
     */
    public String getDisplayName() {
        return this.displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    /**
     * @return the name of the property getter method, if non-standard.
     */
    public String getGetMethod() {
        if(getMethod == null) {
            getMethod = getMethodName(getName(), true, isIs());
        }
        return this.getMethod;
    }

    public void setGetMethod(String getMethod) {
        this.getMethod = getMethod;
    }

    /**
     * Is this a boolean attribute with an "is" getter?
     * @return <code>true</code> if this is a boolean attribute
     *  with an "is" getter
     */
    public boolean isIs() {
        return this.is;
    }

    public void setIs(boolean is) {
        this.is = is;
    }


    /**
     * Is this attribute readable by management applications?
     * @return <code>true</code> if readable
     */
    public boolean isReadable() {
        return this.readable;
    }

    public void setReadable(boolean readable) {
        this.readable = readable;
    }


    /**
     * @return the name of the property setter method, if non-standard.
     */
    public String getSetMethod() {
        if( setMethod == null ) {
            setMethod = getMethodName(getName(), false, false);
        }
        return this.setMethod;
    }

    public void setSetMethod(String setMethod) {
        this.setMethod = setMethod;
    }

    /**
     * Is this attribute writable by management applications?
     * @return <code>true</code> if writable
     */
    public boolean isWriteable() {
        return this.writeable;
    }

    public void setWriteable(boolean writeable) {
        this.writeable = writeable;
    }

    // --------------------------------------------------------- Public Methods


    /**
     * Create and return a <code>ModelMBeanAttributeInfo</code> object that
     * corresponds to the attribute described by this instance.
     * @return the attribute info
     */
    MBeanAttributeInfo createAttributeInfo() {
        // Return our cached information (if any)
        if (info == null) {
            info = new MBeanAttributeInfo(getName(), getType(), getDescription(),
                            isReadable(), isWriteable(), false);
        }
        return (MBeanAttributeInfo)info;
    }

    // -------------------------------------------------------- Private Methods


    /**
     * Create and return the name of a default property getter or setter
     * method, according to the specified values.
     *
     * @param name Name of the property itself
     * @param getter Do we want a get method (versus a set method)?
     * @param is If returning a getter, do we want the "is" form?
     * @return the method name
     */
    private String getMethodName(String name, boolean getter, boolean is) {
        StringBuilder sb = new StringBuilder();
        if (getter) {
            if (is) {
                sb.append("is");
            } else {
                sb.append("get");
            }
        } else {
            sb.append("set");
        }
        sb.append(Character.toUpperCase(name.charAt(0)));
        sb.append(name.substring(1));
        return sb.toString();
    }


}
