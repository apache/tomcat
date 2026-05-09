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
package jakarta.el;

/**
 * Listener interface for EL context lifecycle events. Implementations are notified when an
 * {@link ELContext} is created or released, allowing them to perform initialization or
 * cleanup operations on the context.
 *
 * @since EL 2.1
 */
public interface ELContextListener extends java.util.EventListener {

    /**
     * Notification that an EL context has been created. Implementations can use this callback
     * to register resources, resolvers, or other objects with the newly created context.
     *
     * @param event the event containing the ELContext that was created
     */
    void contextCreated(ELContextEvent event);

}
