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
 * This package contains one public class which is a
 * <code>ConnectionPoolDataSource</code> (CPDS) implementation that can be used to
 * adapt older <code>Driver</code> based jdbc implementations. Below is an
 * example of setting up the CPDS to be available via JNDI in the
 * catalina servlet container.
 * </p>
 * <p>In server.xml, the following would be added to the &lt;Context&gt; for your
 * webapp:
 * </p>
 *
 * <pre>
 *  &lt;Resource name="jdbc/bookstoreCPDS" auth="Container"
 *             type="org.apache.tomcat.dbcp.dbcp2.cpdsadapter.DriverAdapterCPDS"/&gt;
 *   &lt;ResourceParams name="jdbc/bookstoreCPDS"&gt;
 *     &lt;parameter&gt;
 *       &lt;name&gt;factory&lt;/name&gt;
 *       &lt;value&gt;org.apache.tomcat.dbcp.dbcp2.cpdsadapter.DriverAdapterCPDS&lt;/value&gt;
 *     &lt;/parameter&gt;
 *         &lt;parameter&gt;&lt;name&gt;user&lt;/name&gt;&lt;value&gt;root&lt;/value&gt;&lt;/parameter&gt;
 *         &lt;parameter&gt;&lt;name&gt;password&lt;/name&gt;&lt;value&gt;&lt;/value&gt;&lt;/parameter&gt;
 *         &lt;parameter&gt;
 *             &lt;name&gt;driver&lt;/name&gt;
 *             &lt;value&gt;org.gjt.mm.mysql.Driver&lt;/value&gt;&lt;/parameter&gt;
 *         &lt;parameter&gt;
 *              &lt;name&gt;url&lt;/name&gt;
 *              &lt;value&gt;jdbc:mysql://localhost:3306/bookstore&lt;/value&gt;
 *         &lt;/parameter&gt;
 *   &lt;/ResourceParams&gt;
 * </pre>
 *
 * <p>
 * In web.xml.  Note that elements must be given in the order of the dtd
 * described in the servlet specification:
 * </p>
 *
 * <pre>
 * &lt;resource-ref&gt;
 *   &lt;description&gt;
 *     Resource reference to a factory for java.sql.Connection
 *     instances that may be used for talking to a particular
 *     database that is configured in the server.xml file.
 *   &lt;/description&gt;
 *   &lt;res-ref-name&gt;
 *     jdbc/bookstoreCPDS
 *   &lt;/res-ref-name&gt;
 *   &lt;res-type&gt;
 *     org.apache.tomcat.dbcp.dbcp2.cpdsadapter.DriverAdapterCPDS
 *   &lt;/res-type&gt;
 *   &lt;res-auth&gt;
 *     Container
 *   &lt;/res-auth&gt;
 * &lt;/resource-ref&gt;
 * </pre>
 *
 * <p>
 * Catalina deploys all objects configured similarly to above within the
 * <strong>java:comp/env</strong> namespace.
 * </p>
 */
package org.apache.tomcat.dbcp.dbcp2.cpdsadapter;
