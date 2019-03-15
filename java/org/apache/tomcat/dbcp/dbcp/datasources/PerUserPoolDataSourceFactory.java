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

package org.apache.tomcat.dbcp.dbcp.datasources;

import java.io.IOException;
import java.util.Map;

import javax.naming.RefAddr;
import javax.naming.Reference;

/**
 * A JNDI ObjectFactory which creates <code>SharedPoolDataSource</code>s
 * 
 * @version $Revision: 479137 $ $Date: 2006-11-25 10:51:48 -0500 (Sat, 25 Nov 2006) $
 */
public class PerUserPoolDataSourceFactory
    extends InstanceKeyObjectFactory
{
    private static final String PER_USER_POOL_CLASSNAME =
        PerUserPoolDataSource.class.getName();

    protected boolean isCorrectClass(String className) {
        return PER_USER_POOL_CLASSNAME.equals(className);
    }

    protected InstanceKeyDataSource getNewInstance(Reference ref) 
        throws IOException, ClassNotFoundException {
        PerUserPoolDataSource pupds =  new PerUserPoolDataSource();
        RefAddr ra = ref.get("defaultMaxActive");
        if (ra != null && ra.getContent() != null) {
            pupds.setDefaultMaxActive(
                Integer.parseInt(ra.getContent().toString()));
        }

        ra = ref.get("defaultMaxIdle");
        if (ra != null && ra.getContent() != null) {
            pupds.setDefaultMaxIdle(
                Integer.parseInt(ra.getContent().toString()));
        }

        ra = ref.get("defaultMaxWait");
        if (ra != null && ra.getContent() != null) {
            pupds.setDefaultMaxWait(
                Integer.parseInt(ra.getContent().toString()));
        }

        ra = ref.get("perUserDefaultAutoCommit");
        if (ra != null  && ra.getContent() != null) {
            byte[] serialized = (byte[]) ra.getContent();
            pupds.perUserDefaultAutoCommit = (Map) deserialize(serialized);
        }

        ra = ref.get("perUserDefaultTransactionIsolation");
        if (ra != null  && ra.getContent() != null) {
            byte[] serialized = (byte[]) ra.getContent();
            pupds.perUserDefaultTransactionIsolation = 
                (Map) deserialize(serialized);
        }

        ra = ref.get("perUserMaxActive");
        if (ra != null  && ra.getContent() != null) {
            byte[] serialized = (byte[]) ra.getContent();
            pupds.perUserMaxActive = (Map) deserialize(serialized);
        }
        
        ra = ref.get("perUserMaxIdle");
        if (ra != null  && ra.getContent() != null) {
            byte[] serialized = (byte[]) ra.getContent();
            pupds.perUserMaxIdle = (Map) deserialize(serialized);
        }
        
        ra = ref.get("perUserMaxWait");
        if (ra != null  && ra.getContent() != null) {
            byte[] serialized = (byte[]) ra.getContent();
            pupds.perUserMaxWait = (Map) deserialize(serialized);
        }
                
        ra = ref.get("perUserDefaultReadOnly");
        if (ra != null  && ra.getContent() != null) {
            byte[] serialized = (byte[]) ra.getContent();
            pupds.perUserDefaultReadOnly = (Map) deserialize(serialized);
        }
        return pupds;
    }            
}

