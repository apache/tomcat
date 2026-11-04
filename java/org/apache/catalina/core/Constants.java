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
package org.apache.catalina.core;

import java.util.Set;

/**
 * Constants used by the core Catalina package.
 */
public class Constants {

    /**
     * Prevents instantiation.
     */
    private Constants() {
    }

    /**
     * The major version of the Servlet specification implemented by this container.
     */
    public static final int MAJOR_VERSION = 6;
    /**
     * The minor version of the Servlet specification implemented by this container.
     */
    public static final int MINOR_VERSION = 0;

    /**
     * The fully qualified class name of the JSP servlet.
     */
    public static final String JSP_SERVLET_CLASS = "org.apache.jasper.servlet.JspServlet";

    /**
     * The fully qualified class name of the JNDI resource factory of the
     * Tomcat JDBC connection pool. Referenced by name only: catalina.jar has
     * no compile-time dependency on tomcat-jdbc.jar.
     */
    public static final String TOMCAT_JDBC_POOL_FACTORY =
            "org.apache.tomcat.jdbc.pool.DataSourceFactory";

    /**
     * System property consulted to determine the default factory of a
     * {@code javax.sql.DataSource} resource declared without a
     * {@code factory} attribute.
     */
    public static final String DATASOURCE_FACTORY_SYSPROP = "javax.sql.DataSource.Factory";

    /**
     * Name of the pool attribute that carries the pool's registration name.
     * Mirrors the {@code OBJECT_NAME} constant of the pool's JNDI resource
     * factory.
     */
    public static final String POOL_NAME_PARAMETER = "object_name";

    /**
     * The resource type names the Tomcat JDBC pool's JNDI resource factory
     * builds data sources for.
     */
    public static final Set<String> POOL_DATASOURCE_TYPES = Set.of(
            "javax.sql.DataSource", "javax.sql.XADataSource",
            "org.apache.tomcat.jdbc.pool.DataSource",
            "org.apache.tomcat.jdbc.pool.XADataSource");
}
