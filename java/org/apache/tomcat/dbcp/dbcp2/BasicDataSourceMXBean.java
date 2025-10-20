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
package org.apache.tomcat.dbcp.dbcp2;

/**
 * Interface to keep API compatibility. Methods listed here are not made available to
 * <a href="https://docs.oracle.com/javase/8/docs/technotes/guides/management/agent.html">JMX</a>.
 * <p>
 * As of 2.9.0, this interface extends {@link DataSourceMXBean}.
 * </p>
 *
 * @since 2.0
 */
public interface BasicDataSourceMXBean extends DataSourceMXBean {

    /**
     * See {@link BasicDataSource#getPassword()}
     *
     * @return {@link BasicDataSource#getPassword()}
     * @deprecated Exposing passwords via JMX is an Information Exposure issue.
     */
    @Deprecated
    String getPassword();
}
