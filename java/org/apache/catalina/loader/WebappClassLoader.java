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
package org.apache.catalina.loader;

import org.apache.catalina.LifecycleException;

public class WebappClassLoader extends WebappClassLoaderBase {

    public WebappClassLoader() {
        super();
    }


    public WebappClassLoader(ClassLoader parent) {
        super(parent);
    }


    @Override
    public WebappClassLoader copyWithoutTransformers() {

        WebappClassLoader result = new WebappClassLoader(getParent());

        super.copyStateWithoutTransformers(result);

        try {
            result.start();
        } catch (LifecycleException e) {
            throw new IllegalStateException(e);
        }

        return result;
    }


    /**
     * This class loader is not parallel capable so lock on the class loader rather than a per-class lock.
     */
    @Override
    protected Object getClassLoadingLock(String className) {
        return this;
    }
}
