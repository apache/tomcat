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
package org.apache.catalina.startup;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.naming.StringManager;

/**
 * Concrete implementation of the <code>UserDatabase</code> interface that processes the <code>/etc/passwd</code> file
 * on a Unix system.
 *
 * @author Craig R. McClanahan
 */
public final class PasswdUserDatabase implements UserDatabase {

    private static final Log log = LogFactory.getLog(PasswdUserDatabase.class);
    private static final StringManager sm = StringManager.getManager(PasswdUserDatabase.class);

    /**
     * The pathname of the Unix password file.
     */
    private static final String PASSWORD_FILE = "/etc/passwd";


    /**
     * The set of home directories for all defined users, keyed by user name.
     */
    private final Map<String,String> homes = new HashMap<>();


    /**
     * The UserConfig listener with which we are associated.
     */
    private UserConfig userConfig = null;


    @Override
    public UserConfig getUserConfig() {
        return userConfig;
    }


    @Override
    public void setUserConfig(UserConfig userConfig) {
        this.userConfig = userConfig;
        init();
    }


    @Override
    public String getHome(String user) {
        return homes.get(user);
    }


    @Override
    public Enumeration<String> getUsers() {
        return Collections.enumeration(homes.keySet());
    }


    /**
     * Initialize our set of users and home directories.
     */
    private void init() {
        try (BufferedReader reader = new BufferedReader(new FileReader(PASSWORD_FILE))) {
            String line = reader.readLine();
            while (line != null) {
                String tokens[] = line.split(":");
                // Need non-zero 1st and 6th tokens
                if (tokens.length > 5 && tokens[0].length() > 0 && tokens[5].length() > 0) {
                    // Add this user and corresponding directory
                    homes.put(tokens[0], tokens[5]);
                }
                line = reader.readLine();
            }
        } catch (Exception e) {
            log.warn(sm.getString("passwdUserDatabase.readFail"), e);
        }
    }
}
