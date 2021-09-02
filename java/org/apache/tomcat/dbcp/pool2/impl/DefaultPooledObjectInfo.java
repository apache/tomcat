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
package org.apache.tomcat.dbcp.pool2.impl;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;

import org.apache.tomcat.dbcp.pool2.PooledObject;

/**
 * Implementation of object that is used to provide information on pooled
 * objects via JMX.
 *
 * @since 2.0
 */
public class DefaultPooledObjectInfo implements DefaultPooledObjectInfoMBean {

    private static final String PATTERN = "yyyy-MM-dd HH:mm:ss Z";

    private final PooledObject<?> pooledObject;

    /**
     * Constructs a new instance for the given pooled object.
     *
     * @param pooledObject The pooled object that this instance will represent
     */
    public DefaultPooledObjectInfo(final PooledObject<?> pooledObject) {
        this.pooledObject = pooledObject;
    }

    @Override
    public long getBorrowedCount() {
        return pooledObject.getBorrowedCount();
    }

    @Override
    public long getCreateTime() {
        return pooledObject.getCreateInstant().toEpochMilli();
    }

    @Override
    public String getCreateTimeFormatted() {
        return getTimeMillisFormatted(getCreateTime());
    }

    @Override
    public long getLastBorrowTime() {
        return pooledObject.getLastBorrowInstant().toEpochMilli();
    }


    @Override
    public String getLastBorrowTimeFormatted() {
        return getTimeMillisFormatted(getLastBorrowTime());
    }

    @Override
    public String getLastBorrowTrace() {
        final StringWriter sw = new StringWriter();
        pooledObject.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    @Override
    public long getLastReturnTime() {
        return pooledObject.getLastReturnInstant().toEpochMilli();
    }

    @Override
    public String getLastReturnTimeFormatted() {
        return getTimeMillisFormatted(getLastReturnTime());
    }

    @Override
    public String getPooledObjectToString() {
        return pooledObject.getObject().toString();
    }

    @Override
    public String getPooledObjectType() {
        return pooledObject.getObject().getClass().getName();
    }

    private String getTimeMillisFormatted(final long millis) {
        return new SimpleDateFormat(PATTERN).format(Long.valueOf(millis));
    }

    /**
     * @since 2.4.3
     */
    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("DefaultPooledObjectInfo [pooledObject=");
        builder.append(pooledObject);
        builder.append("]");
        return builder.toString();
    }
}
