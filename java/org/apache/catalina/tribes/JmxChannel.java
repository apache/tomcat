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
package org.apache.catalina.tribes;

import javax.management.MBeanRegistration;


public interface JmxChannel extends MBeanRegistration {

    /**
     * If set to true, this channel is registered with jmx.
     * @return true if this channel will be registered with jmx.
     */
    public boolean isJmxEnabled();

    /**
     * If set to true, this channel is registered with jmx.
     * @param jmxEnabled set to true if this channel should be registered with jmx.
     */
    public void setJmxEnabled(boolean jmxEnabled);

    /**
     * Return the jmx domain which this channel is registered.
     * @return jmxDomain
     */
    public String getJmxDomain();

    /**
     * Set the jmx domain which this channel should be registered.
     * @param jmxDomain The jmx domain which this channel should be registered.
     */
    public void setJmxDomain(String jmxDomain);

    /**
     * Return the jmx prefix which will be used with channel ObjectName.
     * @return jmxPrefix
     */
    public String getJmxPrefix();

    /**
     * Set the jmx prefix which will be used with channel ObjectName.
     * @param jmxPrefix The jmx prefix which will be used with channel ObjectName.
     */
    public void setJmxPrefix(String jmxPrefix);

}
