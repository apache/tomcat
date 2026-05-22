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
package org.apache.tomcat.util.descriptor;

import java.util.ArrayList;
import java.util.List;

import org.apache.juli.logging.Log;
import org.apache.tomcat.util.res.StringManager;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * SAX error handler that collects warnings and errors for later processing.
 */
public class XmlErrorHandler implements ErrorHandler {

    /**
     * Default constructor.
     */
    public XmlErrorHandler() {
    }

    private static final StringManager sm = StringManager.getManager(Constants.PACKAGE_NAME);

    private final List<SAXParseException> errors = new ArrayList<>();

    private final List<SAXParseException> warnings = new ArrayList<>();

    @Override
    public void error(SAXParseException exception) throws SAXException {
        // Collect non-fatal errors
        errors.add(exception);
    }

    @Override
    public void fatalError(SAXParseException exception) throws SAXException {
        // Re-throw fatal errors
        throw exception;
    }

    @Override
    public void warning(SAXParseException exception) throws SAXException {
        // Collect warnings
        warnings.add(exception);
    }

    /**
     * Returns the list of collected parsing errors.
     *
     * @return the list of errors
     */
    public List<SAXParseException> getErrors() {
        // Internal use only - don't worry about immutability
        return errors;
    }

    /**
     * Returns the list of collected parsing warnings.
     *
     * @return the list of warnings
     */
    public List<SAXParseException> getWarnings() {
        // Internal use only - don't worry about immutability
        return warnings;
    }

    /**
     * Logs all collected warnings and errors to the specified log.
     *
     * @param log    the log to use
     * @param source the source of the XML being parsed
     */
    public void logFindings(Log log, String source) {
        for (SAXParseException e : getWarnings()) {
            log.warn(sm.getString("xmlErrorHandler.warning", e.getMessage(), source));
        }
        for (SAXParseException e : getErrors()) {
            log.warn(sm.getString("xmlErrorHandler.error", e.getMessage(), source));
        }
    }
}
