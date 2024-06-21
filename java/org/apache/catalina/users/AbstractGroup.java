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


import org.apache.catalina.Group;


/**
 * <p>
 * Convenience base class for {@link Group} implementations.
 * </p>
 *
 * @author Craig R. McClanahan
 *
 * @since 4.1
 */
public abstract class AbstractGroup implements Group {


    // ----------------------------------------------------- Instance Variables


    /**
     * The description of this group.
     */
    protected String description = null;


    /**
     * The group name of this group.
     */
    protected String groupname = null;


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
    public String getGroupname() {
        return this.groupname;
    }


    @Override
    public void setGroupname(String groupname) {
        this.groupname = groupname;
    }


    // ------------------------------------------------------ Principal Methods


    /**
     * Make the principal name the same as the group name.
     */
    @Override
    public String getName() {
        return getGroupname();
    }


}
