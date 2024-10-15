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


import java.io.IOException;
import java.io.PrintWriter;
import java.text.DecimalFormat;

import org.apache.tomcat.util.res.StringManager;

/**
 * Implements the Server-side #fsize command
 *
 * @author Bip Thelin
 * @author Paul Speed
 * @author Dan Sandberg
 * @author David Becker
 */
public final class SSIFsize implements SSICommand {
    private static final StringManager sm = StringManager.getManager(SSIFsize.class);
    static final int ONE_KIBIBYTE = 1024;
    static final int ONE_MEBIBYTE = 1024 * 1024;


    @Override
    public long process(SSIMediator ssiMediator, String commandName, String[] paramNames, String[] paramValues,
            PrintWriter writer) {
        long lastModified = 0;
        String configErrMsg = ssiMediator.getConfigErrMsg();
        for (int i = 0; i < paramNames.length; i++) {
            String paramName = paramNames[i];
            String paramValue = paramValues[i];
            String substitutedValue = ssiMediator.substituteVariables(paramValue);
            try {
                if (paramName.equalsIgnoreCase("file") || paramName.equalsIgnoreCase("virtual")) {
                    boolean virtual = paramName.equalsIgnoreCase("virtual");
                    lastModified = ssiMediator.getFileLastModified(substitutedValue, virtual);
                    long size = ssiMediator.getFileSize(substitutedValue, virtual);
                    String configSizeFmt = ssiMediator.getConfigSizeFmt();
                    writer.write(formatSize(size, configSizeFmt));
                } else {
                    ssiMediator.log(sm.getString("ssiCommand.invalidAttribute", paramName));
                    writer.write(configErrMsg);
                }
            } catch (IOException e) {
                ssiMediator.log(sm.getString("ssiFsize.noSize", substitutedValue), e);
                writer.write(configErrMsg);
            }
        }
        return lastModified;
    }


    public String repeat(char aChar, int numChars) {
        if (numChars < 0) {
            throw new IllegalArgumentException(sm.getString("ssiFsize.invalidNumChars"));
        }
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < numChars; i++) {
            buf.append(aChar);
        }
        return buf.toString();
    }


    public String padLeft(String str, int maxChars) {
        String result = str;
        int charsToAdd = maxChars - str.length();
        if (charsToAdd > 0) {
            result = repeat(' ', charsToAdd) + str;
        }
        return result;
    }


    // We try to mimic httpd here, as we do everywhere.
    // All the 'magic' numbers are from the util_script.c httpd source file.
    // Should use KiB and MiB in output but use k and M for consistency with httpd.
    protected String formatSize(long size, String format) {
        String retString = "";
        if (format.equalsIgnoreCase("bytes")) {
            DecimalFormat decimalFormat = new DecimalFormat("#,##0");
            retString = decimalFormat.format(size);
        } else {
            if (size < 0) {
                retString = "-";
            } else if (size == 0) {
                retString = "0k";
            } else if (size < ONE_KIBIBYTE) {
                retString = "1k";
            } else if (size < ONE_MEBIBYTE) {
                retString = Long.toString((size + 512) / ONE_KIBIBYTE);
                retString += "k";
            } else if (size < 99 * ONE_MEBIBYTE) {
                DecimalFormat decimalFormat = new DecimalFormat("0.0M");
                retString = decimalFormat.format(size / (double) ONE_MEBIBYTE);
            } else {
                retString = Long.toString((size + (529 * ONE_KIBIBYTE)) / ONE_MEBIBYTE);
                retString += "M";
            }
            retString = padLeft(retString, 5);
        }
        return retString;
    }
}