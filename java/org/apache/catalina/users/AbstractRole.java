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
package org.apache.catalina.users;


import org.apache.catalina.Role;


/**
 * <p>
 * Convenience base class for {@link Role} implementations.
 * </p>
 *
 * @author Craig R. McClanahan
 *
 * @since 4.1
 */
public abstract class AbstractRole implements Role {


    // ----------------------------------------------------- Instance Variables


    /**
     * The description of this Role.
     */
    protected String description = null;


    /**
     * The role name of this Role.
     */
    protected String rolename = null;


    // ------------------------------------------------------------- Properties


    @Override
    public String getDescription() {
        return this.description;
    }


    @Override
    public void setDescription(String description) {
        this.description = description;
    }


    @Override
    public String getRolename() {
        return this.rolename;
    }


    @Override
    public void setRolename(String rolename) {
        this.rolename = rolename;
    }


    // ------------------------------------------------------ Principal Methods


    /**
     * Make the principal name the same as the role name.
     */
    @Override
    public String getName() {
        return getRolename();
    }


}
