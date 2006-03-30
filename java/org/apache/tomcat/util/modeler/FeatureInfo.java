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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.management.Descriptor;


/**
 * <p>Convenience base class for <code>AttributeInfo</code>,
 * <code>ConstructorInfo</code>, and <code>OperationInfo</code> classes
 * that will be used to collect configuration information for the
 * <code>ModelMBean</code> beans exposed for management.</p>
 *
 * @author Craig R. McClanahan
 * @version $Revision: 155428 $ $Date: 2005-02-26 14:12:25 +0100 (sam., 26 févr. 2005) $
 */

public class FeatureInfo implements Serializable {
    static final long serialVersionUID = -911529176124712296L;
    protected String description = null;
    protected List fields = new ArrayList();
    protected String name = null;

    // ------------------------------------------------------------- Properties


    /**
     * The human-readable description of this feature.
     */
    public String getDescription() {
        return (this.description);
    }

    public void setDescription(String description) {
        this.description = description;
    }


    /**
     * The field information for this feature.
     */
    public List getFields() {
        return (fields);
    }


    /**
     * The name of this feature, which must be unique among features in the
     * same collection.
     */
    public String getName() {
        return (this.name);
    }

    public void setName(String name) {
        this.name = name;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * <p>Add a new field to the fields associated with the
     * Descriptor that will be created from this metadata.</p>
     *
     * @param field The field to be added
     */
    public void addField(FieldInfo field) {
        fields.add(field);
    }


    // ------------------------------------------------------ Protected Methods


    /**
     * <p>Add the name/value fields that have been stored into the
     * specified <code>Descriptor</code> instance.</p>
     *
     * @param descriptor The <code>Descriptor</code> to add fields to
     */
    protected void addFields(Descriptor descriptor) {

        Iterator items = getFields().iterator();
        while (items.hasNext()) {
            FieldInfo item = (FieldInfo) items.next();
            descriptor.setField(item.getName(), item.getValue());
        }

    }


}
