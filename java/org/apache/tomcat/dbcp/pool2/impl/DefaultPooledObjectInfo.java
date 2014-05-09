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

    private final PooledObject<?> pooledObject;

    /**
     * Create a new instance for the given pooled object.
     *
     * @param pooledObject The pooled object that this instance will represent
     */
    public DefaultPooledObjectInfo(PooledObject<?> pooledObject) {
        this.pooledObject = pooledObject;
    }

    @Override
    public long getCreateTime() {
        return pooledObject.getCreateTime();
    }

    @Override
    public String getCreateTimeFormatted() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z");
        return sdf.format(Long.valueOf(pooledObject.getCreateTime()));
    }

    @Override
    public long getLastBorrowTime() {
        return pooledObject.getLastBorrowTime();
    }

    @Override
    public String getLastBorrowTimeFormatted() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z");
        return sdf.format(Long.valueOf(pooledObject.getLastBorrowTime()));
    }

    @Override
    public String getLastBorrowTrace() {
        StringWriter sw = new StringWriter();
        pooledObject.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    @Override
    public long getLastReturnTime() {
        return pooledObject.getLastReturnTime();
    }

    @Override
    public String getLastReturnTimeFormatted() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z");
        return sdf.format(Long.valueOf(pooledObject.getLastReturnTime()));
    }

    @Override
    public String getPooledObjectType() {
        return pooledObject.getObject().getClass().getName();
    }

    @Override
    public String getPooledObjectToString() {
        return pooledObject.getObject().toString();
    }

    @Override
    public long getBorrowedCount() {
        // TODO Simplify this once getBorrowedCount has been added to PooledObject
        if (pooledObject instanceof DefaultPooledObject) {
            return ((DefaultPooledObject<?>) pooledObject).getBorrowedCount();
        } else {
            return -1;
        }
    }
}
