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
import java.util.List;

/**
 * An SQLException based on a list of Throwable causes.
 * <p>
 * The first exception in the list is used as this exception's cause and is accessible with the usual
 * {@link #getCause()} while the complete list is accessible with {@link #getCauseList()}.
 * </p>
 *
 * @since 2.7.0
 */
public class SQLExceptionList extends SQLException {

    private static final long serialVersionUID = 1L;
    private final List<? extends Throwable> causeList;

    /**
     * Creates a new exception caused by a list of exceptions.
     *
     * @param causeList a list of cause exceptions.
     */
    public SQLExceptionList(final List<? extends Throwable> causeList) {
        super(String.format("%,d exceptions: %s", Integer.valueOf(causeList == null ? 0 : causeList.size()), causeList),
                causeList == null ? null : causeList.get(0));
        this.causeList = causeList;
    }

    /**
     * Gets the cause list.
     *
     * @return The list of causes.
     */
    public List<? extends Throwable> getCauseList() {
        return causeList;
    }

}
