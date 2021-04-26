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
package org.apache.tomcat.websocket.pojo;

import jakarta.websocket.DeploymentException;

import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.websocket.Util;

/**
 * Stores the parameter type and name for a parameter that needs to be passed to
 * an onXxx method of {@link jakarta.websocket.Endpoint}. The name is only present
 * for parameters annotated with
 * {@link jakarta.websocket.server.PathParam}. For the
 * {@link jakarta.websocket.Session} and {@link java.lang.Throwable} parameters,
 * {@link #getName()} will always return <code>null</code>.
 */
public class PojoPathParam {

    private static final StringManager sm = StringManager.getManager(PojoPathParam.class);

    private final Class<?> type;
    private final String name;


    public PojoPathParam(Class<?> type, String name)  throws DeploymentException {
        if (name != null) {
            // Annotated as @PathParam so validate type
            validateType(type);
        }
        this.type = type;
        this.name = name;
    }


    public Class<?> getType() {
        return type;
    }


    public String getName() {
        return name;
    }


    private static void validateType(Class<?> type) throws DeploymentException {
        if (String.class == type) {
            return;
        }
        if (Util.isPrimitive(type)) {
            return;
        }
        throw new DeploymentException(sm.getString("pojoPathParam.wrongType", type.getName()));
    }
}
