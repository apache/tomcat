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
package org.apache.catalina.users;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.catalina.Globals;
import org.apache.catalina.Group;
import org.apache.catalina.Role;
import org.apache.catalina.User;
import org.apache.catalina.UserDatabase;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.digester.AbstractObjectCreationFactory;
import org.apache.tomcat.util.digester.Digester;
import org.apache.tomcat.util.file.ConfigFileLoader;
import org.apache.tomcat.util.file.ConfigurationSource;
import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.util.security.Escape;
import org.xml.sax.Attributes;

/**
 * Concrete implementation of {@link UserDatabase} that loads all defined users, groups, and roles into an in-memory
 * data structure, and uses a specified XML file for its persistent storage.
 * <p>
 * This class is thread-safe.
 * <p>
 * This class does not enforce what, in an RDBMS, would be called referential integrity. Concurrent modifications may
 * result in inconsistent data such as a User retaining a reference to a Role that has been removed from the database.
 *
 * @author Craig R. McClanahan
 *
 * @since 4.1
 */
/*
 * Implementation notes:
 *
 * Any operation that acts on a single element of the database (e.g. operations that create, read, update or delete a
 * user, role or group) must first obtain the read lock. Operations that return iterators for users, roles or groups
 * also fall into this category.
 *
 * Iterators must always be created from copies of the data to prevent possible corruption of the iterator due to the
 * remove of all elements from the underlying Map that would occur during a subsequent re-loading of the database.
 *
 * Any operation that acts on multiple elements and expects the database to remain consistent during the operation (e.g.
 * saving or loading the database) must first obtain the write lock.
 */
public class MemoryUserDatabase implements UserDatabase {

    private static final Log log = LogFactory.getLog(MemoryUserDatabase.class);
    private static final StringManager sm = StringManager.getManager(MemoryUserDatabase.class);


    // ----------------------------------------------------------- Constructors

    /**
     * Create a new instance with default values.
     */
    public MemoryUserDatabase() {
        this(null);
    }


    /**
     * Create a new instance with the specified values.
     *
     * @param id Unique global identifier of this user database
     */
    public MemoryUserDatabase(String id) {
        this.id = id;
    }

    // ----------------------------------------------------- Instance Variables

    /**
     * The set of {@link Group}s defined in this database, keyed by group name.
     */
    protected final Map<String,Group> groups = new ConcurrentHashMap<>();

    /**
     * The unique global identifier of this user database.
     */
    protected final String id;

    /**
     * The relative (to <code>catalina.base</code>) or absolute pathname to the XML file in which we will save our
     * persistent information.
     */
    protected String pathname = "conf/tomcat-users.xml";

    /**
     * The relative or absolute pathname to the file in which our old information is stored while renaming is in
     * progress.
     */
    protected String pathnameOld = pathname + ".old";

    /**
     * The relative or absolute pathname of the file in which we write our new information prior to renaming.
     */
    protected String pathnameNew = pathname + ".new";

    /**
     * A flag, indicating if the user database is read only.
     */
    protected boolean readonly = true;

    /**
     * The set of {@link Role}s defined in this database, keyed by role name.
     */
    protected final Map<String,Role> roles = new ConcurrentHashMap<>();

    /**
     * The set of {@link User}s defined in this database, keyed by user name.
     */
    protected final Map<String,User> users = new ConcurrentHashMap<>();

    private final ReentrantReadWriteLock dbLock = new ReentrantReadWriteLock();
    private final Lock readLock = dbLock.readLock();
    private final Lock writeLock = dbLock.writeLock();

    private volatile long lastModified = 0;
    private boolean watchSource = true;


    // ------------------------------------------------------------- Properties

    @Override
    public Iterator<Group> getGroups() {
        readLock.lock();
        try {
            return new ArrayList<>(groups.values()).iterator();
        } finally {
            readLock.unlock();
        }
    }


    @Override
    public String getId() {
        return this.id;
    }


    /**
     * @return the relative or absolute pathname to the persistent storage file.
     */
    public String getPathname() {
        return this.pathname;
    }


    /**
     * Set the relative or absolute pathname to the persistent storage file.
     *
     * @param pathname The new pathname
     */
    public void setPathname(String pathname) {
        this.pathname = pathname;
        this.pathnameOld = pathname + ".old";
        this.pathnameNew = pathname + ".new";
    }


    /**
     * @return the readonly status of the user database
     */
    public boolean getReadonly() {
        return this.readonly;
    }


    /**
     * Setting the readonly status of the user database
     *
     * @param readonly the new status
     */
    public void setReadonly(boolean readonly) {
        this.readonly = readonly;
    }


    public boolean getWatchSource() {
        return watchSource;
    }


    public void setWatchSource(boolean watchSource) {
        this.watchSource = watchSource;
    }


    @Override
    public Iterator<Role> getRoles() {
        readLock.lock();
        try {
            return new ArrayList<>(roles.values()).iterator();
        } finally {
            readLock.unlock();
        }
    }


    @Override
    public Iterator<User> getUsers() {
        readLock.lock();
        try {
            return new ArrayList<>(users.values()).iterator();
        } finally {
            readLock.unlock();
        }
    }


    // --------------------------------------------------------- Public Methods

    @Override
    public void close() throws Exception {

        writeLock.lock();
        try {
            save();
            users.clear();
            groups.clear();
            roles.clear();
        } finally {
            writeLock.unlock();
        }
    }


    @Override
    public Group createGroup(String groupname, String description) {
        if (groupname == null || groupname.length() == 0) {
            String msg = sm.getString("memoryUserDatabase.nullGroup");
            log.warn(msg);
            throw new IllegalArgumentException(msg);
        }

        Group group = new GenericGroup<>(this, groupname, description, null);
        readLock.lock();
        try {
            groups.put(group.getGroupname(), group);
        } finally {
            readLock.unlock();
        }
        return group;
    }


    @Override
    public Role createRole(String rolename, String description) {
        if (rolename == null || rolename.length() == 0) {
            String msg = sm.getString("memoryUserDatabase.nullRole");
            log.warn(msg);
            throw new IllegalArgumentException(msg);
        }

        Role role = new GenericRole<>(this, rolename, description);
        readLock.lock();
        try {
            roles.put(role.getRolename(), role);
        } finally {
            readLock.unlock();
        }
        return role;
    }


    @Override
    public User createUser(String username, String password, String fullName) {

        if (username == null || username.length() == 0) {
            String msg = sm.getString("memoryUserDatabase.nullUser");
            log.warn(msg);
            throw new IllegalArgumentException(msg);
        }

        User user = new GenericUser<>(this, username, password, fullName, null, null);
        readLock.lock();
        try {
            users.put(user.getUsername(), user);
        } finally {
            readLock.unlock();
        }
        return user;
    }


    @Override
    public Group findGroup(String groupname) {
        readLock.lock();
        try {
            return groups.get(groupname);
        } finally {
            readLock.unlock();
        }
    }


    @Override
    public Role findRole(String rolename) {
        readLock.lock();
        try {
            return roles.get(rolename);
        } finally {
            readLock.unlock();
        }
    }


    @Override
    public User findUser(String username) {
        readLock.lock();
        try {
            return users.get(username);
        } finally {
            readLock.unlock();
        }
    }


    @Override
    public void open() throws Exception {
        writeLock.lock();
        try {
            // Erase any previous groups and users
            users.clear();
            groups.clear();
            roles.clear();

            String pathName = getPathname();
            try (ConfigurationSource.Resource resource = ConfigFileLoader.getSource().getResource(pathName)) {
                lastModified = resource.getLastModified();

                // Construct a digester to read the XML input file
                Digester digester = new Digester();
                try {
                    digester.setFeature("http://apache.org/xml/features/allow-java-encodings", true);
                } catch (Exception e) {
                    log.warn(sm.getString("memoryUserDatabase.xmlFeatureEncoding"), e);
                }
                digester.addFactoryCreate("tomcat-users/group", new MemoryGroupCreationFactory(this), true);
                digester.addFactoryCreate("tomcat-users/role", new MemoryRoleCreationFactory(this), true);
                digester.addFactoryCreate("tomcat-users/user", new MemoryUserCreationFactory(this), true);

                // Parse the XML input to load this database
                digester.parse(resource.getInputStream());
            } catch (IOException ioe) {
                log.error(sm.getString("memoryUserDatabase.fileNotFound", pathName));
            } catch (Exception e) {
                // Fail safe on error
                users.clear();
                groups.clear();
                roles.clear();
                throw e;
            }
        } finally {
            writeLock.unlock();
        }
    }


    @Override
    public void removeGroup(Group group) {
        readLock.lock();
        try {
            Iterator<User> users = getUsers();
            while (users.hasNext()) {
                User user = users.next();
                user.removeGroup(group);
            }
            groups.remove(group.getGroupname());
        } finally {
            readLock.unlock();
        }
    }


    @Override
    public void removeRole(Role role) {
        readLock.lock();
        try {
            Iterator<Group> groups = getGroups();
            while (groups.hasNext()) {
                Group group = groups.next();
                group.removeRole(role);
            }
            Iterator<User> users = getUsers();
            while (users.hasNext()) {
                User user = users.next();
                user.removeRole(role);
            }
            roles.remove(role.getRolename());
        } finally {
            readLock.unlock();
        }
    }


    @Override
    public void removeUser(User user) {
        readLock.lock();
        try {
            users.remove(user.getUsername());
        } finally {
            readLock.unlock();
        }
    }


    /**
     * Check for permissions to save this user database to persistent storage location.
     *
     * @return <code>true</code> if the database is writable
     */
    public boolean isWritable() {

        File file = new File(pathname);
        if (!file.isAbsolute()) {
            file = new File(System.getProperty(Globals.CATALINA_BASE_PROP), pathname);
        }
        File dir = file.getParentFile();
        return dir.exists() && dir.isDirectory() && dir.canWrite();
    }


    @Override
    public void save() throws Exception {

        if (getReadonly()) {
            log.error(sm.getString("memoryUserDatabase.readOnly"));
            return;
        }

        if (!isWritable()) {
            log.warn(sm.getString("memoryUserDatabase.notPersistable"));
            return;
        }

        // Write out contents to a temporary file
        File fileNew = new File(pathnameNew);
        if (!fileNew.isAbsolute()) {
            fileNew = new File(System.getProperty(Globals.CATALINA_BASE_PROP), pathnameNew);
        }

        writeLock.lock();
        try {
            try (FileOutputStream fos = new FileOutputStream(fileNew);
                    OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
                    PrintWriter writer = new PrintWriter(osw)) {

                // Print the file prolog
                writer.println("<?xml version='1.0' encoding='utf-8'?>");
                writer.println("<tomcat-users xmlns=\"http://tomcat.apache.org/xml\"");
                writer.print("              ");
                writer.println("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"");
                writer.print("              ");
                writer.println("xsi:schemaLocation=\"http://tomcat.apache.org/xml tomcat-users.xsd\"");
                writer.println("              version=\"1.0\">");

                // Print entries for each defined role, group, and user
                Iterator<?> values = null;
                values = getRoles();
                while (values.hasNext()) {
                    Role role = (Role) values.next();
                    writer.print("  <role rolename=\"");
                    writer.print(Escape.xml(role.getRolename()));
                    writer.print("\"");
                    if (null != role.getDescription()) {
                        writer.print(" description=\"");
                        writer.print(Escape.xml(role.getDescription()));
                        writer.print("\"");
                    }
                    writer.println("/>");
                }
                values = getGroups();
                while (values.hasNext()) {
                    Group group = (Group) values.next();
                    writer.print("  <group groupname=\"");
                    writer.print(Escape.xml(group.getName()));
                    writer.print("\"");
                    if (null != group.getDescription()) {
                        writer.print(" description=\"");
                        writer.print(Escape.xml(group.getDescription()));
                        writer.print("\"");
                    }
                    writer.print(" roles=\"");
                    for (Iterator<Role> roles = group.getRoles(); roles.hasNext();) {
                        Role role = roles.next();
                        writer.print(Escape.xml(role.getRolename()));
                        if (roles.hasNext()) {
                            writer.print(',');
                        }
                    }
                    writer.println("\"/>");
                }

                values = getUsers();
                while (values.hasNext()) {
                    User user = (User) values.next();
                    writer.print("  <user username=\"");
                    writer.print(Escape.xml(user.getUsername()));
                    writer.print("\" password=\"");
                    writer.print(Escape.xml(user.getPassword()));
                    writer.print("\"");
                    if (null != user.getFullName()) {
                        writer.print(" fullName=\"");
                        writer.print(Escape.xml(user.getFullName()));
                        writer.print("\"");
                    }
                    writer.print(" groups=\"");
                    for (Iterator<Group> groups = user.getGroups(); groups.hasNext();) {
                        Group group = groups.next();
                        writer.print(Escape.xml(group.getGroupname()));
                        if (groups.hasNext()) {
                            writer.print(',');
                        }
                    }
                    writer.print("\" roles=\"");
                    for (Iterator<Role> roles = user.getRoles(); roles.hasNext();) {
                        Role role = roles.next();
                        writer.print(Escape.xml(role.getRolename()));
                        if (roles.hasNext()) {
                            writer.print(',');
                        }
                    }
                    writer.print("\"/>");
                }

                // Print the file epilog
                writer.println("</tomcat-users>");

                // Check for errors that occurred while printing
                if (writer.checkError()) {
                    throw new IOException(sm.getString("memoryUserDatabase.writeException", fileNew.getAbsolutePath()));
                }
            } catch (IOException e) {
                if (fileNew.exists() && !fileNew.delete()) {
                    log.warn(sm.getString("memoryUserDatabase.fileDelete", fileNew));
                }
                throw e;
            }
            this.lastModified = fileNew.lastModified();
        } finally {
            writeLock.unlock();
        }

        // Perform the required renames to permanently save this file
        File fileOld = new File(pathnameOld);
        if (!fileOld.isAbsolute()) {
            fileOld = new File(System.getProperty(Globals.CATALINA_BASE_PROP), pathnameOld);
        }
        if (fileOld.exists() && !fileOld.delete()) {
            throw new IOException(sm.getString("memoryUserDatabase.fileDelete", fileOld));
        }
        File fileOrig = new File(pathname);
        if (!fileOrig.isAbsolute()) {
            fileOrig = new File(System.getProperty(Globals.CATALINA_BASE_PROP), pathname);
        }
        if (fileOrig.exists()) {
            if (!fileOrig.renameTo(fileOld)) {
                throw new IOException(sm.getString("memoryUserDatabase.renameOld", fileOld.getAbsolutePath()));
            }
        }
        if (!fileNew.renameTo(fileOrig)) {
            if (fileOld.exists()) {
                if (!fileOld.renameTo(fileOrig)) {
                    log.warn(sm.getString("memoryUserDatabase.restoreOrig", fileOld));
                }
            }
            throw new IOException(sm.getString("memoryUserDatabase.renameNew", fileOrig.getAbsolutePath()));
        }
        if (fileOld.exists() && !fileOld.delete()) {
            throw new IOException(sm.getString("memoryUserDatabase.fileDelete", fileOld));
        }
    }


    @Override
    public void backgroundProcess() {
        if (!watchSource) {
            return;
        }

        URI uri = ConfigFileLoader.getSource().getURI(getPathname());
        URLConnection uConn = null;
        try {
            URL url = uri.toURL();
            uConn = url.openConnection();

            if (this.lastModified != uConn.getLastModified()) {
                writeLock.lock();
                try {
                    long detectedLastModified = uConn.getLastModified();
                    // Last modified as a resolution of 1s. Ensure that a write
                    // to the file is not in progress by ensuring that the last
                    // modified time is at least 2 seconds ago.
                    if (this.lastModified != detectedLastModified &&
                            detectedLastModified + 2000 < System.currentTimeMillis()) {
                        log.info(sm.getString("memoryUserDatabase.reload", id, uri));
                        open();
                    }
                } finally {
                    writeLock.unlock();
                }
            }
        } catch (Exception ioe) {
            log.error(sm.getString("memoryUserDatabase.reloadError", id, uri), ioe);
        } finally {
            if (uConn != null) {
                try {
                    // Can't close a uConn directly. Have to do it like this.
                    uConn.getInputStream().close();
                } catch (FileNotFoundException fnfe) {
                    // The file doesn't exist.
                    // This has been logged above. No need to log again.
                    // Set the last modified time to avoid repeated log messages
                    this.lastModified = 0;
                } catch (IOException ioe) {
                    log.warn(sm.getString("memoryUserDatabase.fileClose", pathname), ioe);
                }
            }
        }
    }


    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("MemoryUserDatabase[id=");
        sb.append(this.id);
        sb.append(",pathname=");
        sb.append(pathname);
        sb.append(",groupCount=");
        sb.append(this.groups.size());
        sb.append(",roleCount=");
        sb.append(this.roles.size());
        sb.append(",userCount=");
        sb.append(this.users.size());
        sb.append(']');
        return sb.toString();
    }
}


/**
 * Digester object creation factory for group instances.
 */
class MemoryGroupCreationFactory extends AbstractObjectCreationFactory {

    MemoryGroupCreationFactory(MemoryUserDatabase database) {
        this.database = database;
    }


    @Override
    public Object createObject(Attributes attributes) {
        String groupname = attributes.getValue("groupname");
        if (groupname == null) {
            groupname = attributes.getValue("name");
        }
        String description = attributes.getValue("description");
        String roles = attributes.getValue("roles");
        Group group = database.findGroup(groupname);
        if (group == null) {
            group = database.createGroup(groupname, description);
        } else {
            if (group.getDescription() == null) {
                group.setDescription(description);
            }
        }
        if (roles != null) {
            while (roles.length() > 0) {
                String rolename = null;
                int comma = roles.indexOf(',');
                if (comma >= 0) {
                    rolename = roles.substring(0, comma).trim();
                    roles = roles.substring(comma + 1);
                } else {
                    rolename = roles.trim();
                    roles = "";
                }
                if (rolename.length() > 0) {
                    Role role = database.findRole(rolename);
                    if (role == null) {
                        role = database.createRole(rolename, null);
                    }
                    group.addRole(role);
                }
            }
        }
        return group;
    }

    private final MemoryUserDatabase database;
}


/**
 * Digester object creation factory for role instances.
 */
class MemoryRoleCreationFactory extends AbstractObjectCreationFactory {

    MemoryRoleCreationFactory(MemoryUserDatabase database) {
        this.database = database;
    }


    @Override
    public Object createObject(Attributes attributes) {
        String rolename = attributes.getValue("rolename");
        if (rolename == null) {
            rolename = attributes.getValue("name");
        }
        String description = attributes.getValue("description");
        Role existingRole = database.findRole(rolename);
        if (existingRole == null) {
            return database.createRole(rolename, description);
        }
        if (existingRole.getDescription() == null) {
            existingRole.setDescription(description);
        }
        return existingRole;
    }

    private final MemoryUserDatabase database;
}


/**
 * Digester object creation factory for user instances.
 */
class MemoryUserCreationFactory extends AbstractObjectCreationFactory {

    MemoryUserCreationFactory(MemoryUserDatabase database) {
        this.database = database;
    }


    @Override
    public Object createObject(Attributes attributes) {
        String username = attributes.getValue("username");
        if (username == null) {
            username = attributes.getValue("name");
        }
        String password = attributes.getValue("password");
        String fullName = attributes.getValue("fullName");
        if (fullName == null) {
            fullName = attributes.getValue("fullname");
        }
        String groups = attributes.getValue("groups");
        String roles = attributes.getValue("roles");
        User user = database.createUser(username, password, fullName);
        if (groups != null) {
            while (groups.length() > 0) {
                String groupname = null;
                int comma = groups.indexOf(',');
                if (comma >= 0) {
                    groupname = groups.substring(0, comma).trim();
                    groups = groups.substring(comma + 1);
                } else {
                    groupname = groups.trim();
                    groups = "";
                }
                if (groupname.length() > 0) {
                    Group group = database.findGroup(groupname);
                    if (group == null) {
                        group = database.createGroup(groupname, null);
                    }
                    user.addGroup(group);
                }
            }
        }
        if (roles != null) {
            while (roles.length() > 0) {
                String rolename = null;
                int comma = roles.indexOf(',');
                if (comma >= 0) {
                    rolename = roles.substring(0, comma).trim();
                    roles = roles.substring(comma + 1);
                } else {
                    rolename = roles.trim();
                    roles = "";
                }
                if (rolename.length() > 0) {
                    Role role = database.findRole(rolename);
                    if (role == null) {
                        role = database.createRole(rolename, null);
                    }
                    user.addRole(role);
                }
            }
        }
        return user;
    }

    private final MemoryUserDatabase database;
}
