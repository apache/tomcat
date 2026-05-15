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
package org.apache.catalina.tribes.group.interceptors;

/**
 * MBean interface for managing the fragmentation interceptor.
 */
public interface FragmentationInterceptorMBean {

    // Attributes

    /**
     * Returns the maximum size of a fragment in bytes.
     *
     * @return The maximum fragment size
     */
    int getMaxSize();

    /**
     * Returns the expiration time for fragments in milliseconds.
     *
     * @return The fragment expiration time
     */
    long getExpire();

    /**
     * Sets the maximum size of a fragment in bytes.
     *
     * @param maxSize The maximum fragment size
     */
    void setMaxSize(int maxSize);

    /**
     * Sets the expiration time for fragments in milliseconds.
     *
     * @param expire The fragment expiration time
     */
    void setExpire(long expire);
}