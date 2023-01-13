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

import java.io.File;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * Concrete implementation of the <code>UserDatabase</code> interface
 * considers all directories in a directory whose pathname is specified
 * to our constructor to be "home" directories for those users.
 *
 * @author Craig R. McClanahan
 */
public final class HomesUserDatabase implements UserDatabase {

    /**
     * The set of home directories for all defined users, keyed by username.
     */
    private final Map<String,String> homes = new HashMap<>();

    /**
     * The UserConfig listener with which we are associated.
     */
    private UserConfig userConfig = null;


    /**
     * Return the UserConfig listener with which we are associated.
     */
    @Override
    public UserConfig getUserConfig() {
        return this.userConfig;
    }


    /**
     * Set the UserConfig listener with which we are associated.
     *
     * @param userConfig The new UserConfig listener
     */
    @Override
    public void setUserConfig(UserConfig userConfig) {
        this.userConfig = userConfig;
        init();
    }


    /**
     * Return an absolute pathname to the home directory for the specified user.
     *
     * @param user User for which a home directory should be retrieved
     */
    @Override
    public String getHome(String user) {
        return homes.get(user);
    }


    /**
     * Return an enumeration of the user names defined on this server.
     */
    @Override
    public Enumeration<String> getUsers() {
        return Collections.enumeration(homes.keySet());
    }


    /**
     * Initialize our set of users and home directories.
     */
    private void init() {

        String homeBase = userConfig.getHomeBase();
        File homeBaseDir = new File(homeBase);
        if (!homeBaseDir.exists() || !homeBaseDir.isDirectory()) {
            return;
        }
        String homeBaseFiles[] = homeBaseDir.list();
        if (homeBaseFiles == null) {
            return;
        }

        for (String homeBaseFile : homeBaseFiles) {
            File homeDir = new File(homeBaseDir, homeBaseFile);
            if (!homeDir.isDirectory() || !homeDir.canRead()) {
                continue;
            }
            homes.put(homeBaseFile, homeDir.toString());
        }
    }
}
