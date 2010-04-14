/* *******************************************************************
 * Copyright (c) 2004 IBM Corporation
 * 
 * All rights reserved. 
 * This program and the accompanying materials are made available 
 * under the terms of the Common Public License v1.0 
 * which accompanies this distribution and is available at 
 * http://www.eclipse.org/legal/cpl-v10.html 
 *  
 * Contributors: 
 *    Andy Clement     initial implementation 
 *    Heavily based on LocalVariableTable
 * ******************************************************************/

/*
 * Under the terms of the CPL v1.0, the ASF has elected to distribute this
 * file under the Eclipse Public License (EPL) 1.0 which has been designated
 * as the follow-on version of the CPL by the Agreement Steward.
 */

package org.apache.tomcat.util.bcel.classfile;


import  org.apache.tomcat.util.bcel.Constants;
import  java.io.*;

// The new table is used when generic types are about...

//LocalVariableTable_attribute {
//	   u2 attribute_name_index;
//	   u4 attribute_length;
//	   u2 local_variable_table_length;
//	   {  u2 start_pc;
//	      u2 length;
//	      u2 name_index;
//	      u2 descriptor_index;
//	      u2 index;
//	   } local_variable_table[local_variable_table_length];
//	 }

//LocalVariableTypeTable_attribute {
//    u2 attribute_name_index;
//    u4 attribute_length;
//    u2 local_variable_type_table_length;
//    { 
//      u2 start_pc;
//      u2 length;
//      u2 name_index;
//      u2 signature_index;
//      u2 index;
//    } local_variable_type_table[local_variable_type_table_length];
//  }
// J5TODO: Needs some testing !
public class LocalVariableTypeTable extends Attribute {
  private int             local_variable_type_table_length; // Table of local
  private LocalVariable[] local_variable_type_table;        // variables

  

  public LocalVariableTypeTable(int name_index, int length,
			    LocalVariable[] local_variable_table,
			    ConstantPool    constant_pool)
  {
    super(Constants.ATTR_LOCAL_VARIABLE_TYPE_TABLE, name_index, length, constant_pool);
    setLocalVariableTable(local_variable_table);
  }    

  LocalVariableTypeTable(int nameIdx, int len, DataInputStream dis,ConstantPool cpool) throws IOException {
    this(nameIdx, len, (LocalVariable[])null, cpool);

    local_variable_type_table_length = (dis.readUnsignedShort());
    local_variable_type_table = new LocalVariable[local_variable_type_table_length];

    for(int i=0; i < local_variable_type_table_length; i++)
      local_variable_type_table[i] = new LocalVariable(dis, cpool);
  }

  public final void dump(DataOutputStream file) throws IOException
  {
    super.dump(file);
    file.writeShort(local_variable_type_table_length);
    for(int i=0; i < local_variable_type_table_length; i++)
      local_variable_type_table[i].dump(file);
  }

      

  

  public final void setLocalVariableTable(LocalVariable[] local_variable_table)
  {
    this.local_variable_type_table = local_variable_table;
    local_variable_type_table_length = (local_variable_table == null)? 0 :
      local_variable_table.length;
  }

  /**
   * @return String representation.
   */ 
  public final String toString() {
    StringBuffer buf = new StringBuffer();

    for(int i=0; i < local_variable_type_table_length; i++) {
      buf.append(local_variable_type_table[i].toString());

      if(i < local_variable_type_table_length - 1) buf.append('\n');
    }

    return buf.toString();
  }

  /**
   * @return deep copy of this attribute
   */
  public Attribute copy(ConstantPool constant_pool) {
    LocalVariableTypeTable c = (LocalVariableTypeTable)clone();

    c.local_variable_type_table = new LocalVariable[local_variable_type_table_length];
    for(int i=0; i < local_variable_type_table_length; i++)
      c.local_variable_type_table[i] = local_variable_type_table[i].copy();

    c.constant_pool = constant_pool;
    return c;
  }

  
}
