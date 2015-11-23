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
package org.apache.tomcat;

public interface ContextBind {

    /**
     * Change the current thread context class loader to the web application
     * class loader. If no web application class loader is defined, or if the
     * current thread is already using the web application class loader then no
     * change will be made. If the class loader is changed and a
     * {@link org.apache.catalina.ThreadBindingListener} is configured then
     * {@link org.apache.catalina.ThreadBindingListener#bind()} will be called
     * after the change has been made.
     *
     * @param usePrivilegedAction
     *          Should a {@link java.security.PrivilegedAction} be used when
     *          obtaining the current thread context class loader and setting
     *          the new one?
     * @param originalClassLoader
     *          The current class loader if known to save this method having to
     *          look it up
     *
     * @return If the class loader has been changed by the method it will return
     *         the thread context class loader in use when the method was
     *         called. If no change was made then this method returns null.
     */
    ClassLoader bind(boolean usePrivilegedAction, ClassLoader originalClassLoader);

    /**
     * Restore the current thread context class loader to the original class
     * loader in used before {@link #bind(boolean, ClassLoader)} was called. If
     * no original class loader is passed to this method then no change will be
     * made. If the class loader is changed and a
     * {@link org.apache.catalina.ThreadBindingListener} is configured then
     * {@link org.apache.catalina.ThreadBindingListener#unbind()} will be called
     * before the change is made.
     *
     * @param usePrivilegedAction
     *          Should a {@link java.security.PrivilegedAction} be used when
     *          setting the current thread context class loader?
     * @param originalClassLoader
     *          The class loader to restore as the thread context class loader
     */
    void unbind(boolean usePrivilegedAction, ClassLoader originalClassLoader);
}
