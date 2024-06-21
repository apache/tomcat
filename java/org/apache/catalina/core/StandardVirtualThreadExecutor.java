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

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.catalina.Executor;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.util.LifecycleMBeanBase;
import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.util.threads.VirtualThreadExecutor;

/**
 * An executor that uses a new virtual thread for each task.
 */
public class StandardVirtualThreadExecutor extends LifecycleMBeanBase implements Executor {

    private static final StringManager sm = StringManager.getManager(StandardVirtualThreadExecutor.class);

    private String name;
    private java.util.concurrent.ExecutorService executor;
    private String namePrefix = "tomcat-virt-";

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
        if (executor == null) {
            throw new IllegalStateException(sm.getString("standardVirtualThreadExecutor.notStarted"));
        } else {
            executor.execute(command);
        }
    }


    @Override
    protected void startInternal() throws LifecycleException {
        executor = new VirtualThreadExecutor(getNamePrefix());
        setState(LifecycleState.STARTING);
    }

    @Override
    protected void stopInternal() throws LifecycleException {
        executor = null;
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

    @Override
    public void shutdown() {
        // Controlled by Lifecycle instead
    }


    @Override
    public List<Runnable> shutdownNow() {
        // Controlled by Lifecycle instead
        return Collections.emptyList();
    }


    @Override
    public boolean isShutdown() {
        if (executor != null) {
            return executor.isShutdown();
        } else {
            throw new IllegalStateException(sm.getString("standardThreadExecutor.notStarted"));
        }
    }


    @Override
    public boolean isTerminated() {
        if (executor != null) {
            return executor.isTerminated();
        } else {
            throw new IllegalStateException(sm.getString("standardThreadExecutor.notStarted"));
        }
    }


    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return false;
    }


    @Override
    public <T> Future<T> submit(Callable<T> task) {
        if (executor != null) {
            return executor.submit(task);
        } else {
            throw new IllegalStateException(sm.getString("standardThreadExecutor.notStarted"));
        }
    }


    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        if (executor != null) {
            return executor.submit(task, result);
        } else {
            throw new IllegalStateException(sm.getString("standardThreadExecutor.notStarted"));
        }
    }


    @Override
    public Future<?> submit(Runnable task) {
        if (executor != null) {
            return executor.submit(task);
        } else {
            throw new IllegalStateException(sm.getString("standardThreadExecutor.notStarted"));
        }
    }


    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        if (executor != null) {
            return executor.invokeAll(tasks);
        } else {
            throw new IllegalStateException(sm.getString("standardThreadExecutor.notStarted"));
        }
    }


    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException {
        if (executor != null) {
            return executor.invokeAll(tasks, timeout, unit);
        } else {
            throw new IllegalStateException(sm.getString("standardThreadExecutor.notStarted"));
        }
    }


    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        if (executor != null) {
            return executor.invokeAny(tasks);
        } else {
            throw new IllegalStateException(sm.getString("standardThreadExecutor.notStarted"));
        }
    }


    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        if (executor != null) {
            return executor.invokeAny(tasks, timeout, unit);
        } else {
            throw new IllegalStateException(sm.getString("standardThreadExecutor.notStarted"));
        }
    }
}