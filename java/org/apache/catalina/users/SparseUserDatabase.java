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
 */
package org.apache.catalina.users;

import org.apache.catalina.UserDatabase;

/**
 * Abstract base class for a sparse user database implementation.
 * A sparse database creates MBeans for users, groups, and roles on demand
 * rather than registering all of them at startup.
 */
public abstract class SparseUserDatabase implements UserDatabase {

    /**
     * Default constructor required for subclasses.
     */
    public SparseUserDatabase() {
    }

    /**
     * Indicates that this is a sparse user database.
     *
     * @return always {@code true}
     */
    @Override
    public boolean isSparse() {
        return true;
    }


}
