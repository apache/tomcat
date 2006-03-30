/*
 * Copyright 1999,2004 The Apache Software Foundation.
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

import javax.management.MBeanParameterInfo;


/**
 * <p>Internal configuration information for a <code>Parameter</code>
 * descriptor.</p>
 *
 * @author Craig R. McClanahan
 * @version $Revision: 155428 $ $Date: 2005-02-26 14:12:25 +0100 (sam., 26 févr. 2005) $
 */

public class ParameterInfo extends FeatureInfo implements Serializable {
    static final long serialVersionUID = 2222796006787664020L;
    // ----------------------------------------------------------- Constructors


    /**
     * Standard zero-arguments constructor.
     */
    public ParameterInfo() {

        super();

    }


    /**
     * Special constructor for setting up parameters programatically.
     *
     * @param name Name of this parameter
     * @param type Java class of this parameter
     * @param description Description of this parameter
     */
    public ParameterInfo(String name, String type, String description) {

        super();
        setName(name);
        setType(type);
        setDescription(description);

    }


    // ----------------------------------------------------- Instance Variables


    /**
     * The <code>MBeanParameterInfo</code> object that corresponds
     * to this <code>ParameterInfo</code> instance.
     */
    transient MBeanParameterInfo info = null;
    protected String type = null;

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
     * The fully qualified Java class name of this parameter.
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
     * Create and return a <code>MBeanParameterInfo</code> object that
     * corresponds to the parameter described by this instance.
     */
    public MBeanParameterInfo createParameterInfo() {

        // Return our cached information (if any)
        if (info != null)
            return (info);

        // Create and return a new information object
        info = new MBeanParameterInfo
            (getName(), getType(), getDescription());
        return (info);

    }


    /**
     * Return a string representation of this parameter descriptor.
     */
    public String toString() {

        StringBuffer sb = new StringBuffer("ParameterInfo[");
        sb.append("name=");
        sb.append(name);
        sb.append(", description=");
        sb.append(description);
        sb.append(", type=");
        sb.append(type);
        sb.append("]");
        return (sb.toString());

    }
}
