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
package org.apache.catalina.mbeans;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.RuntimeOperationsException;
import javax.management.modelmbean.InvalidTargetObjectTypeException;

import org.apache.tomcat.util.modeler.BaseModelMBean;

public abstract class BaseCatalinaMBean<T> extends BaseModelMBean {

    protected T doGetManagedResource() throws MBeanException {
        try {
            @SuppressWarnings("unchecked")
            T resource = (T) getManagedResource();
            return resource;
        } catch (InstanceNotFoundException | RuntimeOperationsException | InvalidTargetObjectTypeException e) {
            throw new MBeanException(e);
        }
    }


    protected static Object newInstance(String type) throws MBeanException {
        try {
            return Class.forName(type).getConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new MBeanException(e);
        }
    }
}
