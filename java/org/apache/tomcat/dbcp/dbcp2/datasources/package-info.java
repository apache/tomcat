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

/**
 * <p>
 * This package contains two DataSources: <code>PerUserPoolDataSource</code> and
 * <code>SharedPoolDataSource</code> which provide a database connection pool.
 * Below are a couple of usage examples.  One shows deployment into a JNDI system.
 * The other is a simple example initializing the pool using standard java code.
 * </p>
 *
 * <h2>JNDI</h2>
 *
 * <p>
 * Most
 * J2EE containers will provide some way of deploying resources into JNDI.  The
 * method will vary among containers, but once the resource is available via
 * JNDI, the application can access the resource in a container independent
 * manner.  The following example shows deployment into tomcat (catalina).
 * </p>
 * <p>In server.xml, the following would be added to the &lt;Context&gt; for your
 * webapp:
 * </p>
 *
 * <code>
 *  &lt;Resource name="jdbc/bookstore" auth="Container"
 *             type="org.apache.tomcat.dbcp.dbcp2.datasources.PerUserPoolPoolDataSource"/&gt;
 *   &lt;ResourceParams name="jdbc/bookstore"&gt;
 *     &lt;parameter&gt;
 *       &lt;name&gt;factory&lt;/name&gt;
 *       &lt;value&gt;org.apache.tomcat.dbcp.dbcp2.datasources.PerUserPoolDataSourceFactory&lt;/value&gt;
 *     &lt;/parameter&gt;
 *     &lt;parameter&gt;
 *       &lt;name&gt;dataSourceName&lt;/name&gt;&lt;value&gt;java:comp/env/jdbc/bookstoreCPDS&lt;/value&gt;
 *     &lt;/parameter&gt;
 *     &lt;parameter&gt;
 *       &lt;name&gt;defaultMaxTotal&lt;/name&gt;&lt;value&gt;30&lt;/value&gt;
 *     &lt;/parameter&gt;
 *   &lt;/ResourceParams&gt;
 * </code>
 *
 * <p>
 * In web.xml.  Note that elements must be given in the order of the dtd
 * described in the servlet specification:
 * </p>
 *
 * <code>
 * &lt;resource-ref&gt;
 *   &lt;description&gt;
 *     Resource reference to a factory for java.sql.Connection
 *     instances that may be used for talking to a particular
 *     database that is configured in the server.xml file.
 *   &lt;/description&gt;
 *   &lt;res-ref-name&gt;
 *     jdbc/bookstore
 *   &lt;/res-ref-name&gt;
 *   &lt;res-type&gt;
 *     org.apache.tomcat.dbcp.dbcp2.datasources.PerUserPoolDataSource
 *   &lt;/res-type&gt;
 *   &lt;res-auth&gt;
 *     Container
 *   &lt;/res-auth&gt;
 * &lt;/resource-ref&gt;
 * </code>
 *
 * <p>
 * Apache Tomcat deploys all objects configured similarly to above within the
 * <strong>java:comp/env</strong> namespace.  So the JNDI path given for
 * the dataSourceName parameter is valid for a
 * <code>ConnectionPoolDataSource</code> that is deployed as given in the
 * <a href="../cpdsadapter/package-summary.html">cpdsadapter example</a>
 * </p>
 *
 * <p>
 * The <code>DataSource</code> is now available to the application as shown
 * below:
 * </p>
 *
 * <code>
 *
 *     Context ctx = new InitialContext();
 *     DataSource ds = (DataSource)
 *         ctx.lookup("java:comp/env/jdbc/bookstore");
 *     Connection con = null;
 *     try
 *     {
 *         con = ds.getConnection();
 *         ...
 *         use the connection
 *         ...
 *     }
 *     finally
 *     {
 *         if (con != null)
 *             con.close();
 *     }
 *
 * </code>
 *
 * <p>
 * The reference to the <code>DataSource</code> could be maintained, for
 * multiple getConnection() requests.  Or the <code>DataSource</code> can be
 * looked up in different parts of the application code.
 * <code>PerUserPoolDataSourceFactory</code> and
 * <code>SharedPoolDataSourceFactory</code> will maintain the state of the pool
 * between different lookups.  This behavior may be different in other
 * implementations.
 * </p>
 *
 * <h2>Without JNDI</h2>
 *
 * <p>
 * Connection pooling is useful in applications regardless of whether they run
 * in a J2EE environment and a <code>DataSource</code> can be used within a
 * simpler environment.  The example below shows SharedPoolDataSource using
 * DriverAdapterCPDS as the back end source, though any CPDS is applicable.
 * </p>
 *
 * <code>
 *
 * public class Pool
 * {
 *     private static DataSource ds;
 *
 *     static
 *     {
 *         DriverAdapterCPDS cpds = new DriverAdapterCPDS();
 *         cpds.setDriver("org.gjt.mm.mysql.Driver");
 *         cpds.setUrl("jdbc:mysql://localhost:3306/bookstore");
 *         cpds.setUser("foo");
 *         cpds.setPassword(null);
 *
 *         SharedPoolDataSource tds = new SharedPoolDataSource();
 *         tds.setConnectionPoolDataSource(cpds);
 *         tds.setMaxTotal(10);
 *         tds.setMaxWaitMillis(50);
 *
 *         ds = tds;
 *     }
 *
 *     public static getConnection()
 *     {
 *         return ds.getConnection();
 *     }
 * }
 *
 * </code>
 *
 * <p>
 * This class can then be used wherever a connection is needed:
 * </p>
 *
 * <code>
 *     Connection con = null;
 *     try
 *     {
 *         con = Pool.getConnection();
 *         ...
 *         use the connection
 *         ...
 *     }
 *     finally
 *     {
 *         if (con != null)
 *             con.close();
 *     }
 * </code>
 */
package org.apache.tomcat.dbcp.dbcp2.datasources;
