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


import java.security.Principal;


/**
 * <p>
 * Abstract representation of a security role, suitable for use in environments like JAAS that want to deal with
 * <code>Principals</code>.
 * </p>
 *
 * @author Craig R. McClanahan
 *
 * @since 4.1
 */
public interface Role extends Principal {


    // ------------------------------------------------------------- Properties


    /**
     * @return the description of this role.
     */
    String getDescription();


    /**
     * Set the description of this role.
     *
     * @param description The new description
     */
    void setDescription(String description);


    /**
     * @return the role name of this role, which must be unique within the scope of a {@link UserDatabase}.
     */
    String getRolename();


    /**
     * Set the role name of this role, which must be unique within the scope of a {@link UserDatabase}.
     *
     * @param rolename The new role name
     */
    void setRolename(String rolename);


    /**
     * @return the {@link UserDatabase} within which this Role is defined.
     */
    UserDatabase getUserDatabase();


}
