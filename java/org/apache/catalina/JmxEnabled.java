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
 * This interface is implemented by components that will be registered with an
 * MBean server when they are created and unregistered when they are destroyed.
 * It is primarily intended to be implemented by components that implement
 * {@link Lifecycle} but is not exclusively for them.
 */
public interface JmxEnabled extends MBeanRegistration {

    /**
     * Obtain the domain under which this component will be / has been
     * registered.
     */
    String getDomain();


    /**
     * Specify the domain under which this component should be registered. Used
     * with components that cannot (easily) navigate the component hierarchy to
     * determine the correct domain to use.
     */
    void setDomain(String domain);


    /**
     * Obtain the name under which this component has been registered with JMX.
     */
    ObjectName getObjectName();
}
