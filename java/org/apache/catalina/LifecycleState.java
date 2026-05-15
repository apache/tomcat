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
package org.apache.catalina;

/**
 * The list of valid states for components that implement {@link Lifecycle}. See {@link Lifecycle} for the state
 * transition diagram.
 */
public enum LifecycleState {
    /**
     * New component, not yet initialized.
     */
    NEW(false, null),
    /**
     * Component is being initialized.
     */
    INITIALIZING(false, Lifecycle.BEFORE_INIT_EVENT),
    /**
     * Component has been initialized.
     */
    INITIALIZED(false, Lifecycle.AFTER_INIT_EVENT),
    /**
     * Component is preparing to start.
     */
    STARTING_PREP(false, Lifecycle.BEFORE_START_EVENT),
    /**
     * Component is starting.
     */
    STARTING(true, Lifecycle.START_EVENT),
    /**
     * Component has started.
     */
    STARTED(true, Lifecycle.AFTER_START_EVENT),
    /**
     * Component is preparing to stop.
     */
    STOPPING_PREP(true, Lifecycle.BEFORE_STOP_EVENT),
    /**
     * Component is stopping.
     */
    STOPPING(false, Lifecycle.STOP_EVENT),
    /**
     * Component has stopped.
     */
    STOPPED(false, Lifecycle.AFTER_STOP_EVENT),
    /**
     * Component is being destroyed.
     */
    DESTROYING(false, Lifecycle.BEFORE_DESTROY_EVENT),
    /**
     * Component has been destroyed.
     */
    DESTROYED(false, Lifecycle.AFTER_DESTROY_EVENT),
    /**
     * Component has failed.
     */
    FAILED(false, null);

    private final boolean available;
    private final String lifecycleEvent;

    LifecycleState(boolean available, String lifecycleEvent) {
        this.available = available;
        this.lifecycleEvent = lifecycleEvent;
    }

    /**
     * May the public methods other than property getters/setters and lifecycle methods be called for a component in
     * this state? It returns <code>true</code> for any component in any of the following states:
     * <ul>
     * <li>{@link #STARTING}</li>
     * <li>{@link #STARTED}</li>
     * <li>{@link #STOPPING_PREP}</li>
     * </ul>
     *
     * @return <code>true</code> if the component is available for use, otherwise <code>false</code>
     */
    public boolean isAvailable() {
        return available;
    }

    /**
     * Returns the lifecycle event associated with this state.
     *
     * @return the lifecycle event, or {@code null}
     */
    public String getLifecycleEvent() {
        return lifecycleEvent;
    }
}
