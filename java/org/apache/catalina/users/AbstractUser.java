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


import org.apache.catalina.User;


/**
 * <p>
 * Convenience base class for {@link User} implementations.
 * </p>
 *
 * @author Craig R. McClanahan
 *
 * @since 4.1
 */
public abstract class AbstractUser implements User {


    // ----------------------------------------------------- Instance Variables


    /**
     * The full name of this user.
     */
    protected String fullName = null;


    /**
     * The logon password of this user.
     */
    protected String password = null;


    /**
     * The logon username of this user.
     */
    protected String username = null;


    // ------------------------------------------------------------- Properties


    @Override
    public String getFullName() {
        return this.fullName;
    }


    @Override
    public void setFullName(String fullName) {
        this.fullName = fullName;
    }


    @Override
    public String getPassword() {
        return this.password;
    }


    @Override
    public void setPassword(String password) {
        this.password = password;
    }


    @Override
    public String getUsername() {
        return this.username;
    }


    @Override
    public void setUsername(String username) {
        this.username = username;
    }


    // ------------------------------------------------------ Principal Methods


    /**
     * Make the principal name the same as the group name.
     */
    @Override
    public String getName() {
        return getUsername();
    }


}
