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
package org.apache.tomcat.dbcp.pool2;

/**
 * This interface allows pooled objects to make information available about when
 * and how they were used available to the object pool. The object pool may, but
 * is not required, to use this information to make more informed decisions when
 * determining the state of a pooled object - for instance whether or not the
 * object has been abandoned.
 *
 * @since 2.0
 */
public interface TrackedUse {

    /**
     * Get the last time this object was used in ms.
     *
     * @return long time in ms
     */
    long getLastUsed();
}
