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


import java.util.Hashtable;

import javax.naming.Context;
import javax.naming.Name;
import javax.naming.RefAddr;
import javax.naming.Reference;
import javax.naming.spi.ObjectFactory;
import javax.sql.DataSource;


/**
 * <p>
 * JNDI object creation factory for <code>DataSourceUserDatabase</code> instances. This makes it convenient to configure
 * a user database in the global JNDI resources associated with this Catalina instance, and then link to that resource
 * for web applications that administer the contents of the user database.
 * </p>
 * <p>
 * The <code>DataSourceUserDatabase</code> instance is configured based on the following parameter values:
 * </p>
 * <ul>
 * <li><strong>dataSourceName</strong> - JNDI name of the DataSource, which must be located in the same Context
 * environment as the UserDatabase</li>
 * </ul>
 *
 * @author Craig R. McClanahan
 */
public class DataSourceUserDatabaseFactory implements ObjectFactory {


    // --------------------------------------------------------- Public Methods


    /**
     * <p>
     * Create and return a new <code>DataSourceUserDatabase</code> instance that has been configured according to the
     * properties of the specified <code>Reference</code>. If you instance can be created, return <code>null</code>
     * instead.
     * </p>
     *
     * @param obj         The possibly null object containing location or reference information that can be used in
     *                        creating an object
     * @param name        The name of this object relative to <code>nameCtx</code>
     * @param nameCtx     The context relative to which the <code>name</code> parameter is specified, or
     *                        <code>null</code> if <code>name</code> is relative to the default initial context
     * @param environment The possibly null environment that is used in creating this object
     */
    @Override
    public Object getObjectInstance(Object obj, Name name, Context nameCtx, Hashtable<?,?> environment)
            throws Exception {

        // We only know how to deal with <code>javax.naming.Reference</code>s
        // that specify a class name of "org.apache.catalina.UserDatabase"
        if ((obj == null) || !(obj instanceof Reference)) {
            return null;
        }
        Reference ref = (Reference) obj;
        if (!"org.apache.catalina.UserDatabase".equals(ref.getClassName())) {
            return null;
        }

        DataSource dataSource = null;
        String dataSourceName = null;
        RefAddr ra = null;

        ra = ref.get("dataSourceName");
        if (ra != null) {
            dataSourceName = ra.getContent().toString();
            dataSource = (DataSource) nameCtx.lookup(dataSourceName);
        }

        // Create and configure a DataSourceUserDatabase instance based on the
        // RefAddr values associated with this Reference
        DataSourceUserDatabase database = new DataSourceUserDatabase(dataSource, name.toString());
        database.setDataSourceName(dataSourceName);

        ra = ref.get("readonly");
        if (ra != null) {
            database.setReadonly(Boolean.parseBoolean(ra.getContent().toString()));
        }

        ra = ref.get("userTable");
        if (ra != null) {
            database.setUserTable(ra.getContent().toString());
        }

        ra = ref.get("groupTable");
        if (ra != null) {
            database.setGroupTable(ra.getContent().toString());
        }

        ra = ref.get("roleTable");
        if (ra != null) {
            database.setRoleTable(ra.getContent().toString());
        }

        ra = ref.get("userRoleTable");
        if (ra != null) {
            database.setUserRoleTable(ra.getContent().toString());
        }

        ra = ref.get("userGroupTable");
        if (ra != null) {
            database.setUserGroupTable(ra.getContent().toString());
        }

        ra = ref.get("groupRoleTable");
        if (ra != null) {
            database.setGroupRoleTable(ra.getContent().toString());
        }

        ra = ref.get("roleNameCol");
        if (ra != null) {
            database.setRoleNameCol(ra.getContent().toString());
        }

        ra = ref.get("roleAndGroupDescriptionCol");
        if (ra != null) {
            database.setRoleAndGroupDescriptionCol(ra.getContent().toString());
        }

        ra = ref.get("groupNameCol");
        if (ra != null) {
            database.setGroupNameCol(ra.getContent().toString());
        }

        ra = ref.get("userCredCol");
        if (ra != null) {
            database.setUserCredCol(ra.getContent().toString());
        }

        ra = ref.get("userFullNameCol");
        if (ra != null) {
            database.setUserFullNameCol(ra.getContent().toString());
        }

        ra = ref.get("userNameCol");
        if (ra != null) {
            database.setUserNameCol(ra.getContent().toString());
        }

        // Return the configured database instance
        database.open();
        return database;

    }


}
