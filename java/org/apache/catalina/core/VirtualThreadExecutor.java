/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.catalina.core;

import java.util.concurrent.TimeUnit;

import org.apache.catalina.Executor;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.util.LifecycleMBeanBase;
import org.apache.tomcat.util.compat.JreCompat;
import org.apache.tomcat.util.res.StringManager;

/**
 * An executor that uses a new virtual thread for each task.
 */
public class VirtualThreadExecutor extends LifecycleMBeanBase implements Executor {

    private static final StringManager sm = StringManager.getManager(VirtualThreadExecutor.class);

    private final JreCompat jreCompat = JreCompat.getInstance();

    private String name;
    private Object threadBuilder;
    private String namePrefix;

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    public String getNamePrefix() {
        return namePrefix;
    }

    public void setNamePrefix(String namePrefix) {
        this.namePrefix = namePrefix;
    }

    @Override
    public void execute(Runnable command) {
        JreCompat.getInstance().threadBuilderStart(threadBuilder, command);
    }


    @Override
    public void execute(Runnable command, long timeout, TimeUnit unit) {
        JreCompat.getInstance().threadBuilderStart(threadBuilder, command);
    }


    @Override
    protected void initInternal() throws LifecycleException {
        super.initInternal();
        if (!JreCompat.isJre21Available()) {
            throw new LifecycleException(sm.getString("virtualThreadExecutor.noVirtualThreads"));
        }
    }

    @Override
    protected void startInternal() throws LifecycleException {
        threadBuilder = jreCompat.createVirtualThreadBuilder(getNamePrefix());
        setState(LifecycleState.STARTING);
    }

    @Override
    protected void stopInternal() throws LifecycleException {
        threadBuilder = null;
        setState(LifecycleState.STOPPING);
    }

    @Override
    protected String getDomainInternal() {
        // No way to navigate to Engine. Needs to have domain set.
        return null;
    }

    @Override
    protected String getObjectNameKeyProperties() {
        return "type=Executor,name=" + getName();
    }
}