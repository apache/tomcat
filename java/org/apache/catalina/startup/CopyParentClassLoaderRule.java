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
package org.apache.catalina.startup;


import java.lang.reflect.Method;

import org.apache.catalina.Container;
import org.apache.tomcat.util.digester.Rule;
import org.xml.sax.Attributes;


/**
 * Rule that copies the <code>parentClassLoader</code> property from the next-to-top item on the stack (which must be a
 * <code>Container</code>) to the top item on the stack (which must also be a <code>Container</code>).
 *
 * @author Craig R. McClanahan
 */
public class CopyParentClassLoaderRule extends Rule {


    // ----------------------------------------------------------- Constructors


    /**
     * Construct a new instance of this Rule.
     */
    public CopyParentClassLoaderRule() {
    }


    // --------------------------------------------------------- Public Methods


    @Override
    public void begin(String namespace, String name, Attributes attributes) throws Exception {

        if (digester.getLogger().isTraceEnabled()) {
            digester.getLogger().trace("Copying parent class loader");
        }
        Container child = (Container) digester.peek(0);
        Object parent = digester.peek(1);
        Method method = parent.getClass().getMethod("getParentClassLoader", new Class[0]);
        ClassLoader classLoader = (ClassLoader) method.invoke(parent, new Object[0]);
        child.setParentClassLoader(classLoader);

        StringBuilder code = digester.getGeneratedCode();
        if (code != null) {
            code.append(digester.toVariableName(child)).append(".setParentClassLoader(");
            code.append(digester.toVariableName(parent)).append(".getParentClassLoader());");
            code.append(System.lineSeparator());
        }
    }


}
