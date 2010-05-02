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

package org.apache.catalina;

import javax.management.MBeanRegistration;
import javax.management.ObjectName;

/**
 * This interface extends the {@link MBeanRegistration} interface and adds
 * methods for obtaining the domain and object name used to register this
 * component. This interface is intended to be implemented by components that
 * already implement {@link Lifecycle} to indicate that they require
 * registration during {@link Lifecycle#init()} and de-registration during
 * {@link Lifecycle#destroy()}. 
 */
public interface LifecycleMBeanRegistration extends MBeanRegistration {

    /**
     * Obtain the {@link ObjectName} under which this component will be / has
     * been registered.
     */
    public ObjectName getObjectName();


    /**
     * Obtain the domain under which this component will be / has been
     * registered.
     */
    public String getDomain();

}
