/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.tomcat.dbcp.dbcp2;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.MessageFormat;
import java.util.HashSet;
import java.util.ResourceBundle;
import java.util.Set;

/**
 * Utility methods.
 * @since 2.0
 */
public final class Utils {

    private static final ResourceBundle messages = ResourceBundle.getBundle(
            Utils.class.getPackage().getName() + ".LocalStrings");

    public static final boolean IS_SECURITY_ENABLED =
            System.getSecurityManager() != null;

    /** Any SQL_STATE starting with this value is considered a fatal disconnect */
    public static final String DISCONNECTION_SQL_CODE_PREFIX = "08";

    /**
     * SQL codes of fatal connection errors.
     * <ul>
     *  <li>57P01 (ADMIN SHUTDOWN)</li>
     *  <li>57P02 (CRASH SHUTDOWN)</li>
     *  <li>57P03 (CANNOT CONNECT NOW)</li>
     *  <li>01002 (SQL92 disconnect error)</li>
     *  <li>JZ0C0 (Sybase disconnect error)</li>
     *  <li>JZ0C1 (Sybase disconnect error)</li>
     * </ul>
     */
    public static final Set<String> DISCONNECTION_SQL_CODES;

    static {
        DISCONNECTION_SQL_CODES = new HashSet<>();
        DISCONNECTION_SQL_CODES.add("57P01"); // ADMIN SHUTDOWN
        DISCONNECTION_SQL_CODES.add("57P02"); // CRASH SHUTDOWN
        DISCONNECTION_SQL_CODES.add("57P03"); // CANNOT CONNECT NOW
        DISCONNECTION_SQL_CODES.add("01002"); // SQL92 disconnect error
        DISCONNECTION_SQL_CODES.add("JZ0C0"); // Sybase disconnect error
        DISCONNECTION_SQL_CODES.add("JZ0C1"); // Sybase disconnect error
    }

    private Utils() {
        // not instantiable
    }

    /**
     * Closes the ResultSet (which may be null).
     *
     * @param rset a ResultSet, may be {@code null}
     */
    public static void closeQuietly(ResultSet rset) {
        if (rset != null) {
            try {
                rset.close();
            } catch (Exception e) {
                // ignored
            }
        }
    }

    /**
     * Closes the Connection (which may be null).
     *
     * @param conn a Connection, may be {@code null}
     */
    public static void closeQuietly(Connection conn) {
        if (conn != null) {
            try {
                conn.close();
            } catch (Exception e) {
                // ignored
            }
        }
    }

    /**
     * Closes the Statement (which may be null).
     *
     * @param stmt a Statement, may be {@code null}
     */
    public static void closeQuietly(Statement stmt) {
        if (stmt != null) {
            try {
                stmt.close();
            } catch (Exception e) {
                // ignored
            }
        }
    }


    /**
     * Obtain the correct i18n message for the given key.
     */
    public static String getMessage(String key) {
        return getMessage(key, (Object[]) null);
    }


    /**
     * Obtain the correct i18n message for the given key with placeholders
     * replaced by the supplied arguments.
     */
    public static String getMessage(String key, Object... args) {
        String msg =  messages.getString(key);
        if (args == null || args.length == 0) {
            return msg;
        }
        MessageFormat mf = new MessageFormat(msg);
        return mf.format(args, new StringBuffer(), null).toString();
    }
}
