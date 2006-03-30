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


/**
 * <p>Simple JavaBean representing the contents of a <code>&lt;field&gt;</code>
 * element in an MBeans descriptor file.
 */

public class FieldInfo implements Serializable {
    static final long serialVersionUID = -8226401620640873691L;

    /**
     * <p>The field name for this field of a descriptor.</p>
     */
    protected String name = null;

    public String getName() {
        return (this.name);
    }

    public void setName(String name) {
        this.name = name;
    }


    /**
     * <p>The field value for this field of a descriptor.</p>
     */
    protected Object value = null;

    public Object getValue() {
        return (this.value);
    }

    public void setValue(Object value) {
        this.value = value;
    }


}
