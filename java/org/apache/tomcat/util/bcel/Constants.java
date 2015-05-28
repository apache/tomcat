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
package org.apache.tomcat.util.bcel;

/**
 * Constants for the project, mostly defined in the JVM specification.
 *
 * @author  <A HREF="mailto:m.dahm@gmx.de">M. Dahm</A>
 */
public interface Constants {

  /**
   * One of the access flags for fields, methods, or classes.
   * @see "<a href='http://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.5'>Flag definitions for Fields in the Java Virtual Machine Specification (Java SE 8 Edition).</a>"
   * @see "<a href='http://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.6'>Flag definitions for Methods in the Java Virtual Machine Specification (Java SE 8 Edition).</a>"
   * @see "<a href='http://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.7.6-300-D.1-D.1'>Flag definitions for Classes in the Java Virtual Machine Specification (Java SE 8 Edition).</a>"
   */
  public static final short ACC_FINAL        = 0x0010;

  /** One of the access flags for fields, methods, or classes.
   */
  public static final short ACC_INTERFACE    = 0x0200;

  /** One of the access flags for fields, methods, or classes.
   */
  public static final short ACC_ABSTRACT     = 0x0400;

  /** One of the access flags for fields, methods, or classes.
   */
  public static final short ACC_ANNOTATION   = 0x2000;

  /** Marks a constant pool entry as type UTF-8.  */
  public static final byte CONSTANT_Utf8               = 1;

  /** Marks a constant pool entry as type Integer.  */
  public static final byte CONSTANT_Integer            = 3;

  /** Marks a constant pool entry as type Float.  */
  public static final byte CONSTANT_Float              = 4;

  /** Marks a constant pool entry as type Long.  */
  public static final byte CONSTANT_Long               = 5;

  /** Marks a constant pool entry as type Double.  */
  public static final byte CONSTANT_Double             = 6;

  /** Marks a constant pool entry as a Class.  */
  public static final byte CONSTANT_Class              = 7;

  /** Marks a constant pool entry as a Field Reference.  */
  public static final byte CONSTANT_Fieldref           = 9;

  /** Marks a constant pool entry as type String.  */
  public static final byte CONSTANT_String             = 8;

  /** Marks a constant pool entry as a Method Reference.  */
  public static final byte CONSTANT_Methodref          = 10;

  /** Marks a constant pool entry as an Interface Method Reference.  */
  public static final byte CONSTANT_InterfaceMethodref = 11;

  /** Marks a constant pool entry as a name and type.  */
  public static final byte CONSTANT_NameAndType        = 12;

  /** Marks a constant pool entry as a Method Handle.  */
  public static final byte CONSTANT_MethodHandle       = 15;

  /** Marks a constant pool entry as a Method Type.    */
  public static final byte CONSTANT_MethodType         = 16;

  /** Marks a constant pool entry as an Invoke Dynamic */
  public static final byte CONSTANT_InvokeDynamic      = 18;

  /** The names of the types of entries in a constant pool. */
  public static final String[] CONSTANT_NAMES = {
    "", "CONSTANT_Utf8", "", "CONSTANT_Integer",
    "CONSTANT_Float", "CONSTANT_Long", "CONSTANT_Double",
    "CONSTANT_Class", "CONSTANT_String", "CONSTANT_Fieldref",
    "CONSTANT_Methodref", "CONSTANT_InterfaceMethodref",
    "CONSTANT_NameAndType", "", "", "CONSTANT_MethodHandle",
    "CONSTANT_MethodType", "", "CONSTANT_InvokeDynamic" };
}
