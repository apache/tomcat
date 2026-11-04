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
package org.apache.catalina.ssi;


import java.io.PrintWriter;

import org.apache.tomcat.util.res.StringManager;

/**
 * Implements the Server-side #set command.
 */
public class SSISet implements SSICommand {
    private static final StringManager sm = StringManager.getManager(SSISet.class);

    /**
     * Default constructor.
     */
    public SSISet() {
    }

    /**
     * Process the SSI #set command.
     *
     * @param ssiMediator  the SSI mediator
     * @param commandName  the command name
     * @param paramNames   the parameter names
     * @param paramValues  the parameter values
     * @param writer       the print writer for output
     *
     * @return the current time (in milliseconds) if a variable was set, or
     *         zero if no variable was set
     *
     * @throws SSIStopProcessingException if processing should stop
     */
    @Override
    public long process(SSIMediator ssiMediator, String commandName, String[] paramNames, String[] paramValues,
            PrintWriter writer) throws SSIStopProcessingException {
        long lastModified = 0;
        String errorMessage = ssiMediator.getConfigErrMsg();
        String variableName = null;
        boolean variableSeen = false;
        // As for Apache, a value is assigned to the most recent var attribute so
        // multiple var/value pairs may be used in a single directive and a value
        // must follow the var attribute it applies to
        for (int i = 0; i < paramNames.length; i++) {
            String paramName = paramNames[i];
            String paramValue = paramValues[i];
            if (paramName.equalsIgnoreCase("var")) {
                variableName = paramValue;
                variableSeen = true;
            } else if (paramName.equalsIgnoreCase("value")) {
                if (variableName == null) {
                    ssiMediator.log(sm.getString("ssiSet.noVariable"));
                    writer.write(errorMessage);
                    throw new SSIStopProcessingException();
                }
                String substitutedValue = ssiMediator.substituteVariables(paramValue);
                ssiMediator.setVariableValue(variableName, substitutedValue);
                lastModified = System.currentTimeMillis();
            } else {
                ssiMediator.log(sm.getString("ssiCommand.invalidAttribute", paramName));
                writer.write(errorMessage);
                throw new SSIStopProcessingException();
            }
        }
        if (lastModified == 0) {
            // Nothing was set, so a required attribute was missing
            ssiMediator.log(sm.getString(variableSeen ? "ssiSet.noValue" : "ssiSet.noVariable"));
            writer.write(errorMessage);
            throw new SSIStopProcessingException();
        }
        return lastModified;
    }
}
