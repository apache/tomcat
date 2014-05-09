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

  /** One of the access flags for fields, methods, or classes.
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


  /** Attributes and their corresponding names.
   */
  public static final byte ATTR_UNKNOWN                                 = -1;
  public static final byte ATTR_SOURCE_FILE                             = 0;
  public static final byte ATTR_CONSTANT_VALUE                          = 1;
  public static final byte ATTR_CODE                                    = 2;
  public static final byte ATTR_EXCEPTIONS                              = 3;
  public static final byte ATTR_LINE_NUMBER_TABLE                       = 4;
  public static final byte ATTR_LOCAL_VARIABLE_TABLE                    = 5;
  public static final byte ATTR_INNER_CLASSES                           = 6;
  public static final byte ATTR_SYNTHETIC                               = 7;
  public static final byte ATTR_DEPRECATED                              = 8;
  public static final byte ATTR_PMG                                     = 9;
  public static final byte ATTR_SIGNATURE                               = 10;
  public static final byte ATTR_STACK_MAP                               = 11;
  public static final byte ATTR_RUNTIME_VISIBLE_ANNOTATIONS             = 12;
  public static final byte ATTR_RUNTIME_INVISIBLE_ANNOTATIONS           = 13;
  public static final byte ATTR_RUNTIME_VISIBLE_PARAMETER_ANNOTATIONS   = 14;
  public static final byte ATTR_RUNTIME_INVISIBLE_PARAMETER_ANNOTATIONS = 15;
  public static final byte ATTR_ANNOTATION_DEFAULT                      = 16;
  public static final byte ATTR_LOCAL_VARIABLE_TYPE_TABLE               = 17;
  public static final byte ATTR_ENCLOSING_METHOD                        = 18;
  public static final byte ATTR_STACK_MAP_TABLE                         = 19;
  public static final byte ATTR_BOOTSTRAP_METHODS                       = 20;
  public static final byte ATTR_METHOD_PARAMETERS                       = 21;

  public static final short KNOWN_ATTRIBUTES = 22;

  // TOFO: FIXXXXX
  public static final String[] ATTRIBUTE_NAMES = {
    "SourceFile", "ConstantValue", "Code", "Exceptions",
    "LineNumberTable", "LocalVariableTable",
    "InnerClasses", "Synthetic", "Deprecated",
    "PMGClass", "Signature", "StackMap",
    "RuntimeVisibleAnnotations", "RuntimeInvisibleAnnotations",
    "RuntimeVisibleParameterAnnotations", "RuntimeInvisibleParameterAnnotations",
    "AnnotationDefault", "LocalVariableTypeTable", "EnclosingMethod", "StackMapTable",
    "BootstrapMethods", "MethodParameters"
  };

  /** Constants used in the StackMap attribute.
   */
  public static final byte ITEM_Bogus      = 0;
  public static final byte ITEM_Object     = 7;
  public static final byte ITEM_NewObject  = 8;

  /** Constants used to identify StackMapEntry types.
   *
   * For those types which can specify a range, the
   * constant names the lowest value.
   */
  public static final int SAME_FRAME = 0;
  public static final int SAME_LOCALS_1_STACK_ITEM_FRAME = 64;
  public static final int SAME_LOCALS_1_STACK_ITEM_FRAME_EXTENDED = 247;
  public static final int CHOP_FRAME = 248;
  public static final int SAME_FRAME_EXTENDED = 251;
  public static final int APPEND_FRAME = 252;
  public static final int FULL_FRAME = 255;

  /** Constants that define the maximum value of
   * those constants which store ranges. */

  public static final int SAME_FRAME_MAX = 63;
  public static final int SAME_LOCALS_1_STACK_ITEM_FRAME_MAX = 127;
  public static final int CHOP_FRAME_MAX = 250;
  public static final int APPEND_FRAME_MAX = 254;
}
