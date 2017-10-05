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
package org.apache.naming.factory;

import javax.naming.NamingException;
import javax.naming.Reference;
import javax.naming.spi.ObjectFactory;

import org.apache.naming.ResourceRef;

/**
 * Object factory for Resources.
 *
 * @author Remy Maucherat
 */
public class ResourceFactory extends FactoryBase {

    @Override
    protected boolean isReferenceTypeSupported(Object obj) {
        return obj instanceof ResourceRef;
    }

    @Override
    protected ObjectFactory getDefaultFactory(Reference ref) throws NamingException {

        ObjectFactory factory = null;

        if (ref.getClassName().equals("javax.sql.DataSource")) {
            String javaxSqlDataSourceFactoryClassName =
                System.getProperty("javax.sql.DataSource.Factory",
                        Constants.DBCP_DATASOURCE_FACTORY);
            try {
                factory = (ObjectFactory) Class.forName(
                        javaxSqlDataSourceFactoryClassName).getConstructor().newInstance();
            } catch (Exception e) {
                NamingException ex = new NamingException(
                        "Could not create resource factory instance");
                ex.initCause(e);
                throw ex;
            }
        } else if (ref.getClassName().equals("javax.mail.Session")) {
            String javaxMailSessionFactoryClassName =
                System.getProperty("javax.mail.Session.Factory",
                        "org.apache.naming.factory.MailSessionFactory");
            try {
                factory = (ObjectFactory) Class.forName(
                        javaxMailSessionFactoryClassName).getConstructor().newInstance();
            } catch(Throwable t) {
                if (t instanceof NamingException) {
                    throw (NamingException) t;
                }
                if (t instanceof ThreadDeath) {
                    throw (ThreadDeath) t;
                }
                if (t instanceof VirtualMachineError) {
                    throw (VirtualMachineError) t;
                }
                NamingException ex = new NamingException(
                        "Could not create resource factory instance");
                ex.initCause(t);
                throw ex;
            }
        }

        return factory;
    }

    @Override
    protected Object getLinked(Reference ref) {
        // Not supported
        return null;
    }
}
