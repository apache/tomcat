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

import java.sql.SQLException;

/**
 * Exception thrown when a connection's maximum lifetime has been exceeded.
 *
 * @since 2.1
 */
final class LifetimeExceededException extends SQLException {

    private static final long serialVersionUID = -3783783104516492659L;

    /**
     * Constructs a new instance.
     */
    LifetimeExceededException() {
    }

    /**
     * Constructs a new instance with the given message.
     *
     * @param reason a description of the exception
     */
    LifetimeExceededException(final String reason) {
        super(reason);
    }
}
