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

import java.util.Set;

/**
 * Interface describing a collection of Valves that should be executed in sequence when the <code>invoke()</code> method
 * is invoked. It is required that a Valve somewhere in the pipeline (usually the last one) must process the request and
 * create the corresponding response, rather than trying to pass the request on.
 * <p>
 * There is generally a single Pipeline instance associated with each Container. The container's normal request
 * processing functionality is generally encapsulated in a container-specific Valve, which should always be executed at
 * the end of a pipeline. To facilitate this, the <code>setBasic()</code> method is provided to set the Valve instance
 * that will always be executed last. Other Valves will be executed in the order that they were added, before the basic
 * Valve is executed.
 */
public interface Pipeline extends Contained {

    /**
     * Returns the basic Valve for this Pipeline. The basic Valve is always executed last in the pipeline
     * and is responsible for processing the request and creating the response.
     *
     * @return the basic Valve for this Pipeline, or {@code null} if none has been set
     */
    Valve getBasic();


    /**
     * <p>
     * Set the Valve instance that has been distinguished as the basic Valve for this Pipeline (if any). Prior to
     * setting the basic Valve, the Valve's <code>setContainer()</code> will be called, if it implements
     * <code>Contained</code>, with the owning Container as an argument. The method may throw an
     * <code>IllegalArgumentException</code> if this Valve chooses not to be associated with this Container, or
     * <code>IllegalStateException</code> if it is already associated with a different Container.
     * </p>
     *
     * @param valve Valve to be distinguished as the basic Valve
     */
    void setBasic(Valve valve);


    /**
     * <p>
     * Add a new Valve to the end of the pipeline associated with this Container. Prior to adding the Valve, the Valve's
     * <code>setContainer()</code> method will be called, if it implements <code>Contained</code>, with the owning
     * Container as an argument. The method may throw an <code>IllegalArgumentException</code> if this Valve chooses not
     * to be associated with this Container, or <code>IllegalStateException</code> if it is already associated with a
     * different Container.
     * </p>
     * <p>
     * Implementation note: Implementations are expected to trigger the {@link Container#ADD_VALVE_EVENT} for the
     * associated container if this call is successful.
     * </p>
     *
     * @param valve Valve to be added
     *
     * @exception IllegalArgumentException if this Container refused to accept the specified Valve
     * @exception IllegalArgumentException if the specified Valve refuses to be associated with this Container
     * @exception IllegalStateException    if the specified Valve is already associated with a different Container
     */
    void addValve(Valve valve);


    /**
     * Returns all Valves in the pipeline, including the basic Valve. Valves are returned in the order
     * they will be executed, with the basic Valve last.
     *
     * @return the array of Valves in the pipeline, or an empty array if no Valves are configured
     */
    Valve[] getValves();


    /**
     * Remove the specified Valve from the pipeline associated with this Container, if it is found; otherwise, do
     * nothing. If the Valve is found and removed, the Valve's <code>setContainer(null)</code> method will be called if
     * it implements <code>Contained</code>.
     * <p>
     * Implementation note: Implementations are expected to trigger the {@link Container#REMOVE_VALVE_EVENT} for the
     * associated container if this call is successful.
     * </p>
     *
     * @param valve Valve to be removed
     */
    void removeValve(Valve valve);


    /**
     * Returns the first Valve in the pipeline. This is the Valve that will be executed first
     * when a request enters the pipeline.
     *
     * @return the first Valve in the pipeline, or {@code null} if the pipeline is empty
     */
    Valve getFirst();


    /**
     * Returns true if all the valves in this pipeline support async, false otherwise
     *
     * @return true if all the valves in this pipeline support async, false otherwise
     */
    boolean isAsyncSupported();


    /**
     * Identifies the Valves, if any, in this Pipeline that do not support async.
     *
     * @param result The Set to which the fully qualified class names of each Valve in this Pipeline that does not
     *                   support async will be added
     */
    void findNonAsyncValves(Set<String> result);
}
