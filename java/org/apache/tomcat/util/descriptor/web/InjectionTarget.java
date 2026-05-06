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
package org.apache.tomcat.util.descriptor.web;

import java.io.Serializable;

/**
 * Represents a target for dependency injection. Defines the class and field/method to inject into.
 */
public class InjectionTarget implements Serializable {

    private static final long serialVersionUID = 1L;

    private String targetClass;
    private String targetName;


    /**
     * Creates a new injection target with no initial values.
     */
    public InjectionTarget() {
        // NOOP
    }

    /**
     * Creates a new injection target with the specified class and name.
     *
     * @param targetClass the target class name
     * @param targetName the target field or method name
     */
    public InjectionTarget(String targetClass, String targetName) {
        this.targetClass = targetClass;
        this.targetName = targetName;
    }

    /**
     * Returns the target class name.
     *
     * @return the target class name
     */
    public String getTargetClass() {
        return targetClass;
    }

    /**
     * Sets the target class name.
     *
     * @param targetClass the target class name
     */
    public void setTargetClass(String targetClass) {
        this.targetClass = targetClass;
    }

    /**
     * Returns the target field or method name.
     *
     * @return the target name
     */
    public String getTargetName() {
        return targetName;
    }

    /**
     * Sets the target field or method name.
     *
     * @param targetName the target name
     */
    public void setTargetName(String targetName) {
        this.targetName = targetName;
    }

}
