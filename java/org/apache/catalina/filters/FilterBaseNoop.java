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
package org.apache.catalina.filters;

import org.apache.juli.logging.Log;

import javax.servlet.GenericFilter;

public abstract class FilterBaseNoop extends GenericFilter {

    /**
     * This method returns the parameter's value if it exists, or defaultValue if not.
     *
     * @param name - The parameter's name
     * @param defaultValue - The default value to return if the parameter does not exist
     * @return The parameter's value or the default value if the parameter does not exist
     */
    protected String getInitParameter(String name, String defaultValue){

        String value = getInitParameter(name);
        if (value != null)
            return value;

        return defaultValue;
    }

    /**
     * Sub-classes can return a Log that will be used to write log entries
     * @return The default implementation returns null which means that no log entries will be written
     */
    protected Log getLogger(){
        return null;
    }

    /**
     * Logs the message at warn level if a logger exists
     * @param message The warning message to be logged
     */
    protected void warn(Object message){
        Log log = getLogger();
        if (log != null)
            log.warn(message);
    }

}
