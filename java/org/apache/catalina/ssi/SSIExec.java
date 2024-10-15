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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.StringTokenizer;

import org.apache.catalina.util.IOTools;
import org.apache.tomcat.util.res.StringManager;

/**
 * Implements the Server-side #exec command
 *
 * @author Bip Thelin
 * @author Amy Roh
 * @author Paul Speed
 * @author Dan Sandberg
 * @author David Becker
 */
public class SSIExec implements SSICommand {
    private static final StringManager sm = StringManager.getManager(SSIExec.class);
    protected final SSIInclude ssiInclude = new SSIInclude();
    protected static final int BUFFER_SIZE = 1024;


    @Override
    public long process(SSIMediator ssiMediator, String commandName, String[] paramNames, String[] paramValues,
            PrintWriter writer) {
        long lastModified = 0;
        String configErrMsg = ssiMediator.getConfigErrMsg();
        String paramName = paramNames[0];
        String paramValue = paramValues[0];
        String substitutedValue = ssiMediator.substituteVariables(paramValue);
        if (paramName.equalsIgnoreCase("cgi")) {
            lastModified = ssiInclude.process(ssiMediator, "include", new String[] { "virtual" },
                    new String[] { substitutedValue }, writer);
        } else if (paramName.equalsIgnoreCase("cmd")) {
            boolean foundProgram = false;
            try {
                Runtime rt = Runtime.getRuntime();
                StringTokenizer st = new StringTokenizer(substitutedValue);
                String[] cmdArray = new String[st.countTokens()];
                for (int i = 0; i < cmdArray.length; i++) {
                    cmdArray[i] = st.nextToken();
                }
                Process proc = rt.exec(cmdArray);
                foundProgram = true;
                char[] buf = new char[BUFFER_SIZE];
                try (BufferedReader stdOutReader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
                        BufferedReader stdErrReader =
                                new BufferedReader(new InputStreamReader(proc.getErrorStream()));) {
                    IOTools.flow(stdErrReader, writer, buf);
                    IOTools.flow(stdOutReader, writer, buf);
                }
                proc.waitFor();
                lastModified = System.currentTimeMillis();
            } catch (InterruptedException e) {
                ssiMediator.log(sm.getString("ssiExec.executeFailed", substitutedValue), e);
                writer.write(configErrMsg);
            } catch (IOException e) {
                if (!foundProgram) {
                    // Apache doesn't output an error message if it can't find
                    // a program
                }
                ssiMediator.log(sm.getString("ssiExec.executeFailed", substitutedValue), e);
            }
        }
        return lastModified;
    }
}