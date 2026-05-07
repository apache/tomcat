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
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.regex.Matcher;

import org.apache.catalina.util.Strftime;
import org.apache.catalina.util.URLEncoder;
import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.util.security.Escape;

/**
 * Allows the different SSICommand implementations to share data/talk to each other.
 * Acts as the central hub for SSI processing, managing variables, configuration, and file access.
 */
public class SSIMediator {
    private static final StringManager sm = StringManager.getManager(SSIMediator.class);

    /**
     * Encoding constant for no encoding.
     */
    protected static final String ENCODING_NONE = "none";
    /**
     * Encoding constant for HTML entity encoding.
     */
    protected static final String ENCODING_ENTITY = "entity";
    /**
     * Encoding constant for URL encoding.
     */
    protected static final String ENCODING_URL = "url";

    /**
     * Default error message displayed when a directive fails.
     */
    protected static final String DEFAULT_CONFIG_ERR_MSG = "[an error occurred while processing this directive]";
    /**
     * Default time format string for date/time variables.
     */
    protected static final String DEFAULT_CONFIG_TIME_FMT = "%A, %d-%b-%Y %T %Z";
    /**
     * Default size format (abbreviated).
     */
    protected static final String DEFAULT_CONFIG_SIZE_FMT = "abbrev";

    /**
     * Current error message configuration.
     */
    protected String configErrMsg = DEFAULT_CONFIG_ERR_MSG;
    /**
     * Current time format configuration.
     */
    protected String configTimeFmt = DEFAULT_CONFIG_TIME_FMT;
    /**
     * Current size format configuration.
     */
    protected String configSizeFmt = DEFAULT_CONFIG_SIZE_FMT;
    /**
     * Name of this class, used for variable name scoping.
     */
    protected final String className = getClass().getName();
    /**
     * External resolver for file and variable access.
     */
    protected final SSIExternalResolver ssiExternalResolver;
    /**
     * Last modified date of the document being processed.
     */
    protected final long lastModifiedDate;
    /**
     * Strftime formatter for date/time formatting.
     */
    protected Strftime strftime;
    /**
     * State tracker for nested conditional directives.
     */
    protected final SSIConditionalState conditionalState = new SSIConditionalState();
    /**
     * Number of regex match groups from the last match operation.
     */
    protected int lastMatchCount = 0;


    /**
     * Creates a new mediator with the given external resolver and document last modified date.
     *
     * @param ssiExternalResolver the external resolver for file/variable access
     * @param lastModifiedDate    the last modified date of the document being processed
     */
    public SSIMediator(SSIExternalResolver ssiExternalResolver, long lastModifiedDate) {
        this.ssiExternalResolver = ssiExternalResolver;
        this.lastModifiedDate = lastModifiedDate;
        setConfigTimeFmt(DEFAULT_CONFIG_TIME_FMT, true);
    }


    /**
     * Sets the error message displayed when a directive fails.
     *
     * @param configErrMsg the error message to use
     */
    public void setConfigErrMsg(String configErrMsg) {
        this.configErrMsg = configErrMsg;
    }


    /**
     * Sets the time format string for date/time variables.
     *
     * @param configTimeFmt the time format string
     */
    public void setConfigTimeFmt(String configTimeFmt) {
        setConfigTimeFmt(configTimeFmt, false);
    }


    /**
     * Sets the time format string and updates date variables accordingly.
     *
     * @param configTimeFmt    the time format string
     * @param fromConstructor true if called from the constructor
     */
    public void setConfigTimeFmt(String configTimeFmt, boolean fromConstructor) {
        this.configTimeFmt = configTimeFmt;
        this.strftime = new Strftime(configTimeFmt, Locale.US);
        /*
         * Variables like DATE_LOCAL, DATE_GMT, and LAST_MODIFIED need to be updated when the timefmt changes. This is
         * what Apache SSI does.
         */
        setDateVariables(fromConstructor);
    }


    /**
     * Sets the size format for fsize output.
     *
     * @param configSizeFmt the size format string
     */
    public void setConfigSizeFmt(String configSizeFmt) {
        this.configSizeFmt = configSizeFmt;
    }


    /**
     * Returns the current error message configuration.
     *
     * @return the error message
     */
    public String getConfigErrMsg() {
        return configErrMsg;
    }


    /**
     * Returns the current time format configuration.
     *
     * @return the time format string
     */
    public String getConfigTimeFmt() {
        return configTimeFmt;
    }


    /**
     * Returns the current size format configuration.
     *
     * @return the size format string
     */
    public String getConfigSizeFmt() {
        return configSizeFmt;
    }


    /**
     * Returns the conditional state tracker for nested if/else/endif directives.
     *
     * @return the conditional state
     */
    public SSIConditionalState getConditionalState() {
        return conditionalState;
    }


    /**
     * Returns the collection of all available variable names, including built-in and external variables.
     *
     * @return the collection of variable names
     */
    public Collection<String> getVariableNames() {
        Set<String> variableNames = new HashSet<>();
        // These built-in variables are supplied by the mediator (if not over-written by the user) and always exist
        variableNames.add("DATE_GMT");
        variableNames.add("DATE_LOCAL");
        variableNames.add("LAST_MODIFIED");
        ssiExternalResolver.addVariableNames(variableNames);
        // Remove any variables that are reserved by this class
        variableNames.removeIf(this::isNameReserved);
        return variableNames;
    }


    /**
     * Returns the size of the specified file in bytes.
     *
     * @param path    the file path
     * @param virtual true for virtual path, false for physical path
     * @return the file size in bytes
     * @throws IOException if the file cannot be accessed
     */
    public long getFileSize(String path, boolean virtual) throws IOException {
        return ssiExternalResolver.getFileSize(path, virtual);
    }


    /**
     * Returns the last modified timestamp of the specified file.
     *
     * @param path    the file path
     * @param virtual true for virtual path, false for physical path
     * @return the last modified time in milliseconds
     * @throws IOException if the file cannot be accessed
     */
    public long getFileLastModified(String path, boolean virtual) throws IOException {
        return ssiExternalResolver.getFileLastModified(path, virtual);
    }


    /**
     * Returns the text content of the specified file.
     *
     * @param path    the file path
     * @param virtual true for virtual path, false for physical path
     * @return the file content as a string
     * @throws IOException if the file cannot be read
     */
    public String getFileText(String path, boolean virtual) throws IOException {
        return ssiExternalResolver.getFileText(path, virtual);
    }


    /**
     * Checks whether the given variable name is reserved by this class.
     *
     * @param name the variable name to check
     * @return true if the name is reserved
     */
    protected boolean isNameReserved(String name) {
        return name.startsWith(className + ".");
    }


    /**
     * Returns the value of the named variable with no encoding applied.
     *
     * @param variableName the name of the variable
     * @return the variable value, or null if not found
     */
    public String getVariableValue(String variableName) {
        return getVariableValue(variableName, ENCODING_NONE);
    }


    /**
     * Sets the value of the named variable. Reserved names cannot be set.
     *
     * @param variableName  the name of the variable
     * @param variableValue the value to set, or null to remove
     */
    public void setVariableValue(String variableName, String variableValue) {
        if (!isNameReserved(variableName)) {
            ssiExternalResolver.setVariableValue(variableName, variableValue);
        }
    }


    /**
     * Returns the value of the named variable with the specified encoding applied.
     *
     * @param variableName the name of the variable
     * @param encoding     the encoding to apply (none, entity, url)
     * @return the variable value, or null if not found
     */
    public String getVariableValue(String variableName, String encoding) {
        String lowerCaseVariableName = variableName.toLowerCase(Locale.ENGLISH);
        String variableValue = null;
        if (!isNameReserved(lowerCaseVariableName)) {
            // Try getting it externally first, if it fails, try getting the 'built-in' value
            variableValue = ssiExternalResolver.getVariableValue(variableName);
            if (variableValue == null) {
                variableName = variableName.toUpperCase(Locale.ENGLISH);
                variableValue = ssiExternalResolver.getVariableValue(className + "." + variableName);
            }
            if (variableValue != null) {
                variableValue = encode(variableValue, encoding);
            }
        }
        return variableValue;
    }


    /**
     * Applies variable substitution to the specified String and returns the new resolved string.
     *
     * @param val The value which should be checked
     *
     * @return the value after variable substitution
     */
    public String substituteVariables(String val) {
        // If it has no references or HTML entities then no work need to be done
        if (val.indexOf('$') < 0 && val.indexOf('&') < 0) {
            return val;
        }

        // HTML decoding
        val = val.replace("&lt;", "<");
        val = val.replace("&gt;", ">");
        val = val.replace("&quot;", "\"");
        val = val.replace("&amp;", "&");

        StringBuilder sb = new StringBuilder(val);
        int charStart = sb.indexOf("&#");
        while (charStart > -1) {
            int charEnd = sb.indexOf(";", charStart);
            if (charEnd > -1) {
                char c = (char) Integer.parseInt(sb.substring(charStart + 2, charEnd));
                sb.delete(charStart, charEnd + 1);
                sb.insert(charStart, c);
                charStart = sb.indexOf("&#");
            } else {
                break;
            }
        }

        for (int i = 0; i < sb.length();) {
            // Find the next $
            for (; i < sb.length(); i++) {
                if (sb.charAt(i) == '$') {
                    i++;
                    break;
                }
            }
            if (i == sb.length()) {
                break;
            }
            // Check to see if the $ is escaped
            if (i > 1 && sb.charAt(i - 2) == '\\') {
                sb.deleteCharAt(i - 2);
                i--;
                continue;
            }
            int nameStart = i;
            int start = i - 1;
            int end;
            int nameEnd;
            char endChar = ' ';
            // Check for {} wrapped var
            if (sb.charAt(i) == '{') {
                nameStart++;
                endChar = '}';
            }
            // Find the end of the var reference
            for (; i < sb.length(); i++) {
                if (sb.charAt(i) == endChar) {
                    break;
                }
            }
            end = i;
            nameEnd = end;
            if (endChar == '}') {
                end++;
            }
            // We should now have enough to extract the var name
            String varName = sb.substring(nameStart, nameEnd);
            String value = getVariableValue(varName);
            if (value == null) {
                value = "";
            }
            // Replace the var name with its value
            sb.replace(start, end, value);
            // Start searching for the next $ after the value that was just substituted.
            i = start + value.length();
        }
        return sb.toString();
    }


    /**
     * Formats a date using the configured strftime pattern and the given time zone.
     *
     * @param date     the date to format
     * @param timeZone the time zone to use, or null for the default
     * @return the formatted date string
     */
    protected String formatDate(Date date, TimeZone timeZone) {
        String retVal;
        if (timeZone != null) {
            // we temporarily change strftime. Since SSIMediator is inherently single-threaded, this isn't a problem
            TimeZone oldTimeZone = strftime.getTimeZone();
            strftime.setTimeZone(timeZone);
            retVal = strftime.format(date);
            strftime.setTimeZone(oldTimeZone);
        } else {
            retVal = strftime.format(date);
        }
        return retVal;
    }


    /**
     * Encodes the given value using the specified encoding type.
     *
     * @param value    the value to encode
     * @param encoding the encoding type (none, entity, url)
     * @return the encoded value
     */
    protected String encode(String value, String encoding) {
        String retVal;
        if (encoding.equalsIgnoreCase(ENCODING_URL)) {
            retVal = URLEncoder.DEFAULT.encode(value, StandardCharsets.UTF_8);
        } else if (encoding.equalsIgnoreCase(ENCODING_NONE)) {
            retVal = value;
        } else if (encoding.equalsIgnoreCase(ENCODING_ENTITY)) {
            retVal = Escape.htmlElementContent(value);
        } else {
            // This shouldn't be possible
            throw new IllegalArgumentException(sm.getString("ssiMediator.unknownEncoding", encoding));
        }
        return retVal;
    }


    /**
     * Logs a message without an associated throwable.
     *
     * @param message the log message
     */
    public void log(String message) {
        ssiExternalResolver.log(message, null);
    }


    /**
     * Logs a message with an associated throwable.
     *
     * @param message     the log message
     * @param throwable   the associated throwable
     */
    public void log(String message, Throwable throwable) {
        ssiExternalResolver.log(message, throwable);
    }


    /**
     * Updates the built-in date variables (DATE_GMT, DATE_LOCAL, LAST_MODIFIED).
     *
     * @param fromConstructor true if called from the constructor
     */
    protected void setDateVariables(boolean fromConstructor) {
        boolean alreadySet = ssiExternalResolver.getVariableValue(className + ".alreadyset") != null;
        // skip this if we are being called from the constructor, and this has already been set
        if (!(fromConstructor && alreadySet)) {
            ssiExternalResolver.setVariableValue(className + ".alreadyset", "true");
            Date date = new Date();
            TimeZone timeZone = TimeZone.getTimeZone("GMT");
            String retVal = formatDate(date, timeZone);
            /*
             * If we are setting on of the date variables, we want to remove them from the user defined list of
             * variables, because this is what Apache does.
             */
            setVariableValue("DATE_GMT", null);
            ssiExternalResolver.setVariableValue(className + ".DATE_GMT", retVal);
            retVal = formatDate(date, null);
            setVariableValue("DATE_LOCAL", null);
            ssiExternalResolver.setVariableValue(className + ".DATE_LOCAL", retVal);
            retVal = formatDate(new Date(lastModifiedDate), null);
            setVariableValue("LAST_MODIFIED", null);
            ssiExternalResolver.setVariableValue(className + ".LAST_MODIFIED", retVal);
        }
    }


    /**
     * Clears all regex match group variables.
     */
    protected void clearMatchGroups() {
        for (int i = 1; i <= lastMatchCount; i++) {
            setVariableValue(Integer.toString(i), "");
        }
        lastMatchCount = 0;
    }


    /**
     * Populates match group variables from the given regex matcher.
     *
     * @param matcher the regex matcher containing the groups
     */
    protected void populateMatchGroups(Matcher matcher) {
        lastMatchCount = matcher.groupCount();
        // $0 is not used
        if (lastMatchCount == 0) {
            return;
        }
        for (int i = 1; i <= lastMatchCount; i++) {
            setVariableValue(Integer.toString(i), matcher.group(i));
        }
    }
}