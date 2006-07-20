/*
 * Copyright 1999,2004 The Apache Software Foundation.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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
import java.util.Hashtable;
import java.util.Enumeration;


/**
 * Concrete implementation of the <strong>UserDatabase</code> interface
 * considers all directories in a directory whose pathname is specified
 * to our constructor to be "home" directories for those users.
 *
 * @author Craig R. McClanahan
 * @version $Revision: 302726 $ $Date: 2004-02-27 15:59:07 +0100 (ven., 27 févr. 2004) $
 */

public final class HomesUserDatabase
    implements UserDatabase {


    // --------------------------------------------------------- Constructors


    /**
     * Initialize a new instance of this user database component.
     */
    public HomesUserDatabase() {

        super();

    }


    // --------------------------------------------------- Instance Variables


    /**
     * The set of home directories for all defined users, keyed by username.
     */
    private Hashtable homes = new Hashtable();


    /**
     * The UserConfig listener with which we are associated.
     */
    private UserConfig userConfig = null;


    // ----------------------------------------------------------- Properties


    /**
     * Return the UserConfig listener with which we are associated.
     */
    public UserConfig getUserConfig() {

        return (this.userConfig);

    }


    /**
     * Set the UserConfig listener with which we are associated.
     *
     * @param userConfig The new UserConfig listener
     */
    public void setUserConfig(UserConfig userConfig) {

        this.userConfig = userConfig;
        init();

    }


    // ------------------------------------------------------- Public Methods


    /**
     * Return an absolute pathname to the home directory for the specified user.
     *
     * @param user User for which a home directory should be retrieved
     */
    public String getHome(String user) {

        return ((String) homes.get(user));

    }


    /**
     * Return an enumeration of the usernames defined on this server.
     */
    public Enumeration getUsers() {

        return (homes.keys());

    }


    // ------------------------------------------------------ Private Methods


    /**
     * Initialize our set of users and home directories.
     */
    private void init() {

        String homeBase = userConfig.getHomeBase();
        File homeBaseDir = new File(homeBase);
        if (!homeBaseDir.exists() || !homeBaseDir.isDirectory())
            return;
        String homeBaseFiles[] = homeBaseDir.list();

        for (int i = 0; i < homeBaseFiles.length; i++) {
            File homeDir = new File(homeBaseDir, homeBaseFiles[i]);
            if (!homeDir.isDirectory() || !homeDir.canRead())
                continue;
            homes.put(homeBaseFiles[i], homeDir.toString());
        }


    }


}
