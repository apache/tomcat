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
package org.apache.jasper.compiler;

/**
 * Defines the interface for the String interpreter. This allows users to
 * provide custom String interpreter implementations that can optimise
 * String processing for an application by performing code generation for
 * a sub-set of Strings.
 */
public interface StringInterpreter {

    /**
     * Generates the source code that represents the conversion of the string
     * value to the appropriate type.
     *
     * @param c
     *              The target class to which to coerce the given string
     * @param s
     *              The string value
     * @param attrName
     *              The name of the attribute whose value is being supplied
     * @param propEditorClass
     *              The property editor for the given attribute
     * @param isNamedAttribute
     *              true if the given attribute is a named attribute (that
     *              is, specified using the jsp:attribute standard action),
     *              and false otherwise
     *
     * @return the string representing the code that will be inserted into the
     *         source code for the Servlet generated for the JSP.
     */
    String convertString(Class<?> c, String s, String attrName,
            Class<?> propEditorClass, boolean isNamedAttribute);
}
