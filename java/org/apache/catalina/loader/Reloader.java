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


/**
 * Internal interface that <code>ClassLoader</code> implementations may
 * optionally implement to support the auto-reload functionality of
 * the classloader associated with the context.
 *
 * @author Craig R. McClanahan
 * @version $Revision$ $Date$
 */

public interface Reloader {


    /**
     * Add a new repository to the set of places this ClassLoader can look for
     * classes to be loaded.
     *
     * @param repository Name of a source of classes to be loaded, such as a
     *  directory pathname, a JAR file pathname, or a ZIP file pathname
     *
     * @exception IllegalArgumentException if the specified repository is
     *  invalid or does not exist
     */
    public void addRepository(String repository);


    /**
     * Return a String array of the current repositories for this class
     * loader.  If there are no repositories, a zero-length array is
     * returned.
     */
    public String[] findRepositories();


    /**
     * Have one or more classes or resources been modified so that a reload
     * is appropriate?
     */
    public boolean modified();


}
