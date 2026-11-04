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
package org.apache.catalina.mbeans;

import javax.management.Attribute;
import javax.management.RuntimeOperationsException;

import org.apache.juli.logging.Log;
import org.apache.tomcat.util.res.StringManager;

/**
 * Abstract base class for Naming Resource MBeans.
 *
 * @param <T> the type of the managed resource
 */
public abstract class BaseNamingResourceMBean<T> extends BaseCatalinaMBean<T> {

    private static final StringManager sm = StringManager.getManager(BaseNamingResourceMBean.class);


    /**
     * Validate the provided attribute. Checks include:
     * <ul>
     * <li>attribute is not null</li>
     * <li>attribute name is not null</li>
     * <li>attribute name is not "name"</li>
     * </ul>
     *
     * @param attribute The attribute to validate
     *
     * @return {@code true} if the attribute is valid, {@code false} if the caller should stop further processing.
     */
    protected boolean validateAttribute(Attribute attribute) {
        // Validate the input parameters
        if (attribute == null) {
            throw new RuntimeOperationsException(new IllegalArgumentException(sm.getString("mBean.nullAttribute")),
                    sm.getString("mBean.nullAttribute"));
        }

        String name = attribute.getName();
        if (name == null) {
            throw new RuntimeOperationsException(new IllegalArgumentException(sm.getString("mBean.nullName")),
                    sm.getString("mBean.nullName"));
        }

        if ("name".equals(name)) {
            // Updating the name actually needs removing and adding back the
            // component under the new name. Ignore the change, as the other
            // naming resource MBeans do.
            getLog().info(sm.getString("mBean.nameChange"));
            return false;
        }

        return true;
    }


    /**
     * Enables sub-classes to provide the correct logger for any log messages.
     *
     * @return The logger to use
     */
    protected abstract Log getLog();
}
