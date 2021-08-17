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


import org.apache.catalina.UserDatabase;


/**
 * <p>Concrete implementation of {@link org.apache.catalina.Role} for a
 * {@link UserDatabase}.</p>
 *
 * @author Craig R. McClanahan
 */
public class GenericRole<UD extends UserDatabase> extends AbstractRole {


    // ----------------------------------------------------------- Constructors


    /**
     * Package-private constructor used by the factory method in
     * {@link UserDatabase}.
     *
     * @param database The {@link UserDatabase} that owns this role
     * @param rolename Role name of this role
     * @param description Description of this role
     */
    GenericRole(UD database,
               String rolename, String description) {

        super();
        this.database = database;
        this.rolename = rolename;
        this.description = description;

    }


    // ----------------------------------------------------- Instance Variables


    /**
     * The {@link UserDatabase} that owns this role.
     */
    protected final UserDatabase database;


    // ------------------------------------------------------------- Properties


    /**
     * Return the {@link UserDatabase} within which this role is defined.
     */
    @Override
    public UserDatabase getUserDatabase() {
        return this.database;
    }


    @Override
    public void setDescription(String description) {
        database.modifiedRole(this);
        super.setDescription(description);
    }


    @Override
    public void setRolename(String rolename) {
        database.modifiedRole(this);
        super.setRolename(rolename);
    }


}
