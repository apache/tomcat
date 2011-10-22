/*
 * Copyright 2004 Costin Manolache
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

package org.apache.tomcat.lite.service;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.tomcat.lite.http.HttpRequest;
import org.apache.tomcat.lite.http.HttpResponse;
import org.apache.tomcat.lite.http.HttpChannel.HttpService;

/**
 * Log configuration
 *
 */
public class LogConfig implements HttpService {

    /**
     * Framework can set this attribute with comma separated
     * list of loggers to set to debug level.
     * This is used at startup.
     */
    public void setDebug(String debug) {
        for (String log : debug.split(",")) {
            Logger logger = Logger.getLogger(log);
            logger.setLevel(Level.INFO);
        }
    }

    /**
     *
     */
    public void setWarn(String nodebug) {
        for (String log : nodebug.split(",")) {
            Logger logger = Logger.getLogger(log);
            logger.setLevel(Level.WARNING);
        }
    }

    @Override
    public void service(HttpRequest httpReq, HttpResponse httpRes)
            throws IOException {
        String debug = httpReq.getParameter("debug");
        setDebug(debug);
        String warn = httpReq.getParameter("warn");
        setWarn(warn);
    }
}
