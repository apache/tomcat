/* Licensed to the Apache Software Foundation (ASF) under one or more
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
package org.apache.tomcat.jdbc.pool.interceptor;

import javax.management.openmbean.CompositeData;
import javax.management.openmbean.OpenDataException;

/**
 * JMX MBean interface for exposing slow query statistics via JMX.
 * <p>
 * The resource published for a pool name is {@link SlowQueryReportJmx.Registration}, exposed as a <em>Standard</em>
 * MBean, so this interface is the whole management surface: a public method of the resource that is not declared here
 * stays invisible to a JMX client - only the notification methods contributed by
 * {@link javax.management.NotificationEmitter} come on top. Everything an operator needs, and everything the
 * interceptor's {@code mbeans-descriptors.xml} advertises, therefore has to be declared here.
 * </p>
 * <p>
 * Each pool publishes its own MBean, under
 * {@code tomcat.jdbc:type=org.apache.tomcat.jdbc.pool.interceptor.SlowQueryReportJmx,name=<pool>} unless the
 * {@code objectName} interceptor property overrides it, and only when the interceptor is configured with
 * {@code notifyPool=false}. With the default {@code notifyPool=true} no MBean is registered and the statistics are only
 * observable through the connection pool's own MBean. A second live pool trying to publish under a name that is already
 * occupied is refused by the MBean server; sharing one name between live pools is not supported.
 * </p>
 * <p>
 * {@code notifyPool} itself is deliberately not part of this interface. It is a per-interceptor-instance configuration
 * property - the pool builds one interceptor chain per pooled connection and configures every instance with it - and
 * every instance decides the routing of its own notifications from its own copy when it reports a query. Writing it
 * through JMX would change only the single instance that happens to own the registration and leave the pool's other
 * connections routing as before, so the attribute would promise a pool-wide effect it could not deliver. It stays a
 * configuration entry point, applied to every instance of the pool by
 * {@link SlowQueryReportJmx#setProperties(java.util.Map)}.
 * </p>
 */
public interface SlowQueryReportJmxMBean {
    /**
     * Returns the registration name of the connection pool this MBean reports for, as the {@code PoolName} attribute.
     * The registration, the notifications and the statistics belong to that one pool; a second live pool configured
     * with the same name does not share this MBean, and its registration is refused while this one holds the name.
     * <p>
     * The value is recorded when the pool starts. It is not cleared when the pool's chain is released, so it can remain
     * non-null after the MBean has been given up; only an instance that was never bound to a pool answers
     * {@code null}.
     *
     * @return the pool's recorded registration name, or {@code null} before this instance is bound to a pool
     */
    String getPoolName();

    /**
     * Returns the names of all the connection pools this JVM currently holds recorded query statistics for, as the
     * {@code PoolNames} attribute. That is a JVM-wide view, not this pool alone: a pool using the plain
     * {@code SlowQueryReport} interceptor, which never registers an MBean of its own, appears here as well, and pools
     * configured with the same name appear once.
     *
     * @return the names of the pools that currently hold a statistics map; an empty array when no pool has registered
     *             statistics
     */
    String[] getPoolNames();

    /**
     * Removes all query statistics recorded for the pool this MBean reports for, so an operator can start from a clean
     * slate instead of restarting the pool. Only that pool's store is cleared; another live pool's statistics are
     * untouched, even if it shares the name (a configuration the pool does not support for JMX publication). Only the
     * recorded content is dropped: the pool stays monitored and keeps accumulating statistics afterwards, and a query
     * recorded again starts from empty counters.
     */
    void resetStats();

    /**
     * Returns slow query statistics as JMX CompositeData, as the {@code SlowQueriesCD} attribute. The element schema is
     * {@link SlowQueryReport.QueryStats}: every duration ({@code maxInvocationTime}, {@code minInvocationTime},
     * {@code totalInvocationTime}, {@code prepareTime}) is in milliseconds and every date field
     * ({@code maxInvocationDate}, {@code minInvocationDate}, {@code lastInvocation}) is epoch milliseconds.
     *
     * @return array of composite data representing slow query statistics
     *
     * @throws OpenDataException if the composite data cannot be created; a JMX client gets it wrapped in an
     *                               {@link javax.management.MBeanException}
     */
    CompositeData[] getSlowQueriesCD() throws OpenDataException;
}
