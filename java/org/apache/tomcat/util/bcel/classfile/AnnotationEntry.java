/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package org.apache.tomcat.util.bcel.classfile;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.apache.tomcat.util.bcel.Constants;

/**
 * represents one annotation in the annotation table
 * 
 * @version $Id: AnnotationEntry
 * @author  <A HREF="mailto:dbrosius@mebigfatguy.com">D. Brosius</A>
 * @since 5.3
 */
public class AnnotationEntry implements Constants, Serializable {

    private int type_index;
    private int num_element_value_pairs;
    private List element_value_pairs;
    private ConstantPool constant_pool;
    private boolean isRuntimeVisible;


    /**
     * Construct object from file stream.
     * @param file Input stream
     */
    public AnnotationEntry(int type_index, ConstantPool constant_pool, boolean isRuntimeVisible) {
        this.type_index = type_index;
        
        this.constant_pool = constant_pool;
        this.isRuntimeVisible = isRuntimeVisible;
    }
    
    public static AnnotationEntry read(DataInputStream file, ConstantPool constant_pool, boolean isRuntimeVisible) throws IOException 
    {
    	AnnotationEntry annotationEntry = new AnnotationEntry(file.readUnsignedShort(), constant_pool, isRuntimeVisible);
    	annotationEntry.num_element_value_pairs = (file.readUnsignedShort());
    	annotationEntry.element_value_pairs = new ArrayList();
        for (int i = 0; i < annotationEntry.num_element_value_pairs; i++) {
        	annotationEntry.element_value_pairs.add(new ElementValuePair(file.readUnsignedShort(), ElementValue.readElementValue(file, constant_pool), constant_pool));
        }
        return annotationEntry;
    }


    


    /**
     * @return the annotation type name
     */
    public String getAnnotationType() {
        ConstantUtf8 c;
        c = (ConstantUtf8) constant_pool.getConstant(type_index, CONSTANT_Utf8);
        return c.getBytes();
    }
    
    


    


    /**
     * @return the element value pairs in this annotation entry
     */
    public ElementValuePair[] getElementValuePairs() {
    	// TOFO return List
        return (ElementValuePair[]) element_value_pairs.toArray(new ElementValuePair[element_value_pairs.size()]);
    }


	public void dump(DataOutputStream dos) throws IOException
	{
		dos.writeShort(type_index);	// u2 index of type name in cpool
		dos.writeShort(element_value_pairs.size()); // u2 element_value pair count
		for (int i = 0 ; i<element_value_pairs.size();i++) {
			ElementValuePair envp = (ElementValuePair) element_value_pairs.get(i);
			envp.dump(dos);
		}
	}


	

	

	
}
