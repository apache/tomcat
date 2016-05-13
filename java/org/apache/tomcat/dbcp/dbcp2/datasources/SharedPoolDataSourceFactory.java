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

package org.apache.tomcat.dbcp.dbcp2.datasources;

import javax.naming.RefAddr;
import javax.naming.Reference;

/**
 * A JNDI ObjectFactory which creates <code>SharedPoolDataSource</code>s
 * @since 2.0
 */
public class SharedPoolDataSourceFactory
    extends InstanceKeyDataSourceFactory
{
    private static final String SHARED_POOL_CLASSNAME =
        SharedPoolDataSource.class.getName();

    @Override
    protected boolean isCorrectClass(final String className) {
        return SHARED_POOL_CLASSNAME.equals(className);
    }

    @Override
    protected InstanceKeyDataSource getNewInstance(final Reference ref) {
        final SharedPoolDataSource spds = new SharedPoolDataSource();
        final RefAddr ra = ref.get("maxTotal");
        if (ra != null && ra.getContent() != null) {
            spds.setMaxTotal(
                Integer.parseInt(ra.getContent().toString()));
        }
        return spds;
    }
}

