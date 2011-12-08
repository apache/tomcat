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
package org.apache.juli.logging;

/**
 * This helper class assists with the logging associated with invalid input
 * data. A developer may want all instances of invalid input data logged to
 * assist with debugging whereas in production it is likely to be desirable not
 * to log anything for invalid data. The following settings may be used:
 * <ul>
 * <li>NOTHING: Log nothing.</li>
 * <li>DEBUG_ALL: Log all problems at DEBUG log level.</li>
 * <li>INFO_THEN_DEBUG: Log first problem at INFO log level and any further
 *     issues in the following TBD (configurable) seconds at DEBUG level</li>
 * <li>INFO_ALL: Log all problems at INFO log level.</li>
 * </ul>
 * By default, INFO_THEN_DEBUG is used with a suppression time of 24 hours.
 *
 * NOTE: This class is not completely thread-safe. When using INFO_THEN_DEBUG it
 * is possible that several INFO messages will be logged before dropping to
 * DEBUG.
 */
public class UserDataHelper {

    private final Log log;

    private Config config;

    // A value of 0 is equivalent to using INFO_ALL
    // A negative value will trigger infinite suppression
    private long suppressionTime;

    private volatile long lastInfoTime = 0;


    public UserDataHelper(Log log) {
        this.log = log;

        String configString = System.getProperty(
                "org.apache.juli.logging.UserDataHelper.CONFIG");
        if (configString == null) {
            config = Config.INFO_THEN_DEBUG;
        } else {
            try {
                config = Config.valueOf(configString);
            } catch (IllegalArgumentException iae) {
                // Ignore - use default
                config = Config.INFO_THEN_DEBUG;
            }
        }

        // Default suppression time of 1 day.
        suppressionTime = Long.getLong(
                "org.apache.juli.logging.UserDataHelper.SUPPRESSION_TIME",
                60 * 60 * 24).longValue();

        if (suppressionTime == 0) {
            config = Config.INFO_ALL;
        }
    }


    public boolean isEnabled() {
        if (Config.NONE == config) {
            return false;
        } else if (Config.DEBUG_ALL == config) {
            return log.isDebugEnabled();
        } else if (Config.INFO_THEN_DEBUG == config) {
            if (logAtInfo(false)) {
                return log.isInfoEnabled();
            } else {
                return log.isDebugEnabled();
            }
        } else if (Config.INFO_ALL == config) {
            return log.isInfoEnabled();
        }
        // Should never happen
        return false;
    }


    public void log(String message) {
        if (Config.NONE == config) {
            // NOOP;
        } else if (Config.DEBUG_ALL == config) {
            log.debug(message);
        } else if (Config.INFO_THEN_DEBUG == config) {
            if (logAtInfo(true)) {
                log.info(message);
            } else {
                log.debug(message);
            }
        } else if (Config.INFO_ALL == config) {
            log.info(message);
        }
    }


    public void log(String message, Throwable t) {
        if (Config.NONE == config) {
            // NOOP;
        } else if (Config.DEBUG_ALL == config) {
            log.debug(message, t);
        } else if (Config.INFO_THEN_DEBUG == config) {
            if (logAtInfo(true)) {
                log.info(message, t);
            } else {
                log.debug(message, t);
            }
        } else if (Config.INFO_ALL == config) {
            log.info(message, t);
        }
    }


    /*
     * Not completely thread-safe but good enough for this use case. I couldn't
     * see a simple enough way to make it completely thread-safe that was not
     * likely to compromise performance.
     */
    private boolean logAtInfo(boolean updateLastLoggedTime) {

        if (suppressionTime < 0 && lastInfoTime > 0) {
            return false;
        }

        long now = System.currentTimeMillis();

        if (lastInfoTime + suppressionTime > now) {
            return false;
        }

        if (updateLastLoggedTime) {
            lastInfoTime = now;
        }
        return true;
    }


    private static enum Config {
        NONE,
        DEBUG_ALL,
        INFO_THEN_DEBUG,
        INFO_ALL
    }
}
