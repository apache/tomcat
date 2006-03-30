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

import javax.management.Descriptor;
import javax.management.MBeanParameterInfo;
import javax.management.modelmbean.ModelMBeanConstructorInfo;


/**
 * <p>Internal configuration information for a <code>Constructor</code>
 * descriptor.</p>
 *
 * @author Craig R. McClanahan
 * @version $Revision: 155428 $ $Date: 2005-02-26 14:12:25 +0100 (sam., 26 févr. 2005) $
 */

public class ConstructorInfo extends FeatureInfo implements Serializable {
    static final long serialVersionUID = -5735336213417238238L;

    // ----------------------------------------------------- Instance Variables


    /**
     * The <code>ModelMBeanConstructorInfo</code> object that corresponds
     * to this <code>ConstructorInfo</code> instance.
     */
    transient ModelMBeanConstructorInfo info = null;
    protected String displayName = null;
    protected ParameterInfo parameters[] = new ParameterInfo[0];


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
     * The set of parameters for this constructor.
     */
    public ParameterInfo[] getSignature() {
        return (this.parameters);
    }


    // --------------------------------------------------------- Public Methods


    /**
     * Add a new parameter to the set of parameters for this constructor.
     *
     * @param parameter The new parameter descriptor
     */
    public void addParameter(ParameterInfo parameter) {

        synchronized (parameters) {
            ParameterInfo results[] = new ParameterInfo[parameters.length + 1];
            System.arraycopy(parameters, 0, results, 0, parameters.length);
            results[parameters.length] = parameter;
            parameters = results;
            this.info = null;
        }

    }


    /**
     * Create and return a <code>ModelMBeanConstructorInfo</code> object that
     * corresponds to the attribute described by this instance.
     */
    public ModelMBeanConstructorInfo createConstructorInfo() {

        // Return our cached information (if any)
        if (info != null)
            return (info);

        // Create and return a new information object
        ParameterInfo params[] = getSignature();
        MBeanParameterInfo parameters[] =
            new MBeanParameterInfo[params.length];
        for (int i = 0; i < params.length; i++)
            parameters[i] = params[i].createParameterInfo();
        info = new ModelMBeanConstructorInfo
            (getName(), getDescription(), parameters);
        Descriptor descriptor = info.getDescriptor();
        descriptor.removeField("class");
        if (getDisplayName() != null)
            descriptor.setField("displayName", getDisplayName());
        addFields(descriptor);
        info.setDescriptor(descriptor);
        return (info);

    }


    /**
     * Return a string representation of this constructor descriptor.
     */
    public String toString() {

        StringBuffer sb = new StringBuffer("ConstructorInfo[");
        sb.append("name=");
        sb.append(name);
        sb.append(", description=");
        sb.append(description);
        sb.append(", parameters=");
        sb.append(parameters.length);
        sb.append("]");
        return (sb.toString());

    }


}
