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
package jakarta.annotation.sql;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines a data source for dependency injection via the {@link jakarta.annotation.Resource}
 * annotation. This annotation allows developers to declaratively specify the properties
 * of a JDBC data source, including connection details, pool sizing, and transactional
 * behavior. The container creates and manages the data source based on the specified
 * configuration, making it available for injection into application components.
 *
 * @since Common Annotations 1.1
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(DataSourceDefinitions.class)
public @interface DataSourceDefinition {

    /**
     * Specifies the fully qualified class name of the JDBC driver or the Jakarta
     * Connector connection factory class that implements this data source.
     *
     * @return the fully qualified class name of the data source implementation
     */
    String className();

    /**
     * Specifies the JNDI name under which the data source will be registered.
     * This is the lookup name used for dependency injection or explicit JNDI
     * lookup of the data source.
     *
     * @return the JNDI name for the data source
     */
    String name();

    /**
     * Provides a human-readable description of the data source for documentation
     * and administrative purposes.
     *
     * @return the description of the data source
     */
    String description() default "";

    /**
     * Specifies the JDBC connection URL for the database. This URL is used by
     * the driver to establish connections to the database server.
     *
     * @return the JDBC connection URL
     */
    String url() default "";

    /**
     * Specifies the database user name used to establish connections.
     *
     * @return the database user name
     */
    String user() default "";

    /**
     * Specifies the password for the database user account used to establish connections.
     *
     * @return the database password
     */
    String password() default "";

    /**
     * Specifies the name of the database to connect to. This is used in conjunction
     * with the server name and port number to locate the target database.
     *
     * @return the database name
     */
    String databaseName() default "";

    /**
     * Specifies the network port number on which the database server is listening.
     * A value of -1 indicates the default port for the database type.
     *
     * @return the database server port number, or -1 for the default
     */
    int portNumber() default -1;

    /**
     * Specifies the host name or IP address of the database server.
     *
     * @return the database server host name, defaulting to "localhost"
     */
    String serverName() default "localhost";

    /**
     * Specifies the transaction isolation level for connections from this data source,
     * using the constants defined in {@link java.sql.Connection}. A value of -1
     * indicates the database default isolation level should be used.
     *
     * @return the transaction isolation level, or -1 for the database default
     */
    int isolationLevel() default -1;

    /**
     * Indicates whether connections from this data source support container-managed
     * transactions. When true, the data source participates in Jakarta Transactions.
     *
     * @return true if the data source participates in container-managed transactions
     */
    boolean transactional() default true;

    /**
     * Specifies the number of connections to create when the connection pool is
     * initialized. A value of -1 indicates the container default should be used.
     *
     * @return the initial number of connections in the pool, or -1 for default
     */
    int initialPoolSize() default -1;

    /**
     * Specifies the maximum number of connections that the pool may create.
     * A value of -1 indicates the container default should be used.
     *
     * @return the maximum pool size, or -1 for the container default
     */
    int maxPoolSize() default -1;

    /**
     * Specifies the minimum number of connections that the pool must maintain.
     * A value of -1 indicates the container default should be used.
     *
     * @return the minimum pool size, or -1 for the container default
     */
    int minPoolSize() default -1;

    /**
     * Specifies the maximum idle time in seconds before a connection is closed
     * and removed from the pool. A value of -1 indicates no idle timeout.
     *
     * @return the maximum idle time in seconds, or -1 for no timeout
     */
    int maxIdleTime() default -1;

    /**
     * Specifies the maximum number of prepared statements that may be cached
     * per connection in the pool. A value of -1 indicates the container default.
     *
     * @return the maximum cached prepared statements per connection, or -1 for default
     */
    int maxStatements() default -1;

    /**
     * Specifies additional connection properties in the form "name=value" strings.
     * These properties are passed to the JDBC driver during connection establishment.
     *
     * @return the array of additional connection properties
     */
    String[] properties() default {};

    /**
     * Specifies the maximum time in seconds that the driver will wait when
     * attempting to connect to the database. A value of 0 means no timeout.
     *
     * @return the login timeout in seconds, or 0 for no timeout
     */
    int loginTimeout() default 0;
}
