/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.tomcat.manager2;

import java.io.IOException;
import java.io.Serial;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import javax.naming.Binding;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.Reference;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.catalina.Container;
import org.apache.catalina.ContainerServlet;
import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.Group;
import org.apache.catalina.Role;
import org.apache.catalina.Server;
import org.apache.catalina.Service;
import org.apache.catalina.User;
import org.apache.catalina.UserDatabase;
import org.apache.catalina.Wrapper;
import org.apache.catalina.users.MemoryUserDatabase;
import org.apache.tomcat.util.json.JSONParser;


/**
 * The Manager2 users API. Manages the users, groups and roles of the {@link UserDatabase} JNDI resources configured for
 * this server (the default {@code server.xml} configures the file based {@code MemoryUserDatabase} that backs
 * {@code conf/tomcat-users.xml}).
 * <p>
 * User databases are discovered through the server's global JNDI naming context: every binding whose type is
 * {@code org.apache.catalina.UserDatabase} is resolved and listed (other bindings are left untouched). When several
 * databases are configured, the {@code name} JSON body field or query parameter selects the one to operate on.
 * <p>
 * Passwords are stored exactly as provided, with the same semantics as the {@code password} attribute of
 * {@code tomcat-users.xml}: the configured realm is responsible for comparing them during authentication. Passwords are
 * never returned by this API.
 * <p>
 * Like the rest of the API that is not part of the read-only status endpoints, this API requires the
 * {@code manager-gui} role.
 */
public class UsersApiServlet extends HttpServlet implements ContainerServlet {


    @Serial
    private static final long serialVersionUID = 1L;


    /**
     * The JNDI type of the user database resources.
     */
    private static final String UDB_TYPE = UserDatabase.class.getName();


    private transient Wrapper wrapper = null;

    private transient Server server = null;


    // ------------------------------------------------ ContainerServlet API


    @Override
    public Wrapper getWrapper() {
        return wrapper;
    }


    @Override
    public void setWrapper(Wrapper wrapper) {
        this.wrapper = wrapper;
        if (wrapper == null) {
            server = null;
        } else {
            server = null;
            Context context = (Context) wrapper.getParent();
            Container host = context.getParent();
            if (host != null) {
                Container engine = host.getParent();
                if (engine instanceof Engine engineContainer) {
                    Service service = engineContainer.getService();
                    if (service != null) {
                        server = service.getServer();
                    }
                }
            }
        }
    }


    // ------------------------------------------------------------ Request API


    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String path = fullPath(request);

        try {
            if ("/api/users".equals(path)) {
                list(response, request.getParameter("name"));
            } else {
                Api.notFound(response);
            }
        } catch (Exception e) {
            log(Strings.sm().getString("manager2.error.users"), e);
            throw new ServletException(e);
        }
    }


    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String path = fullPath(request);
        String[] tail = tail(path);

        try {
            if ("/api/users".equals(path)) {
                createUser(request, response);
            } else if (path.startsWith("/api/users/") && tail.length == 2) {
                if ("password".equals(tail[1])) {
                    setPassword(request, response, tail[0]);
                } else if ("roles".equals(tail[1])) {
                    setUserRoles(request, response, tail[0]);
                } else {
                    Api.notFound(response);
                }
            } else if ("/api/groups".equals(path)) {
                createGroup(request, response);
            } else if (path.startsWith("/api/groups/") && tail.length == 2) {
                if ("members".equals(tail[1])) {
                    setGroupMembers(request, response, tail[0]);
                } else if ("roles".equals(tail[1])) {
                    setGroupRoles(request, response, tail[0]);
                } else {
                    Api.notFound(response);
                }
            } else if ("/api/roles".equals(path)) {
                createRole(request, response);
            } else {
                Api.notFound(response);
            }
        } catch (Exception e) {
            log(Strings.sm().getString("manager2.error.users"), e);
            throw new ServletException(e);
        }
    }


    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String path = fullPath(request);
        String[] tail = tail(path);

        try {
            if (path.startsWith("/api/users/") && tail.length == 1) {
                removeUser(request, response, tail[0]);
            } else if (path.startsWith("/api/groups/") && tail.length == 1) {
                removeGroup(request, response, tail[0]);
            } else if (path.startsWith("/api/roles/") && tail.length == 1) {
                removeRole(request, response, tail[0]);
            } else {
                Api.notFound(response);
            }
        } catch (Exception e) {
            log(Strings.sm().getString("manager2.error.users"), e);
            throw new ServletException(e);
        }
    }


    // ---------------------------------------------------------------- List


    private void list(HttpServletResponse response, String selected) throws IOException {

        List<Map.Entry<String, UserDatabase>> found = discover();
        if (found.isEmpty()) {
            Api.error(response, HttpServletResponse.SC_NOT_FOUND, "USER_DATABASE_MISSING",
                    Strings.sm().getString("manager2.userDatabaseMissing"));
            return;
        }

        Map.Entry<String, UserDatabase> dbEntry = select(selected, found, response);
        if (dbEntry == null) {
            return;
        }
        UserDatabase db = dbEntry.getValue();

        List<Map<String, Object>> databases = new ArrayList<>();
        for (Map.Entry<String, UserDatabase> entry : found) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", entry.getKey());
            item.put("id", entry.getValue().getId());
            item.put("type", entry.getValue().getClass().getSimpleName());
            item.put("readonly", readonly(entry.getValue()));
            item.put("writable", writable(entry.getValue()));
            databases.add(item);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("databases", databases);
        payload.put("name", dbEntry.getKey());
        payload.put("readonly", readonly(db));
        payload.put("writable", writable(db));
        payload.put("users", listUsers(db));
        payload.put("groups", listGroups(db));
        payload.put("roles", listRoles(db));
        Api.json(response, payload);
    }


    private List<Map<String, Object>> listUsers(UserDatabase db) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (User user : collect(db.getUsers())) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("username", user.getUsername());
            item.put("fullName", user.getFullName());
            item.put("hasPassword", user.getPassword() != null);
            List<String> roles = collectNames(user.getRoles(), Role::getRolename);
            List<String> groups = collectNames(user.getGroups(), Group::getGroupname);
            item.put("roles", roles);
            item.put("groups", groups);
            Set<String> effective = new LinkedHashSet<>(roles);
            for (String groupname : groups) {
                Group group = db.findGroup(groupname);
                if (group != null) {
                    effective.addAll(collectNames(group.getRoles(), Role::getRolename));
                }
            }
            item.put("effectiveRoles", new ArrayList<>(effective));
            result.add(item);
        }
        result.sort((a, b) -> String.valueOf(a.get("username")).compareTo(String.valueOf(b.get("username"))));
        return result;
    }


    private List<Map<String, Object>> listGroups(UserDatabase db) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Group group : collect(db.getGroups())) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("groupname", group.getGroupname());
            item.put("description", group.getDescription());
            item.put("roles", collectNames(group.getRoles(), Role::getRolename));
            // Drop members that no longer exist in the database.
            List<String> members = new ArrayList<>();
            for (User user : collect(group.getUsers())) {
                if (db.findUser(user.getUsername()) != null) {
                    members.add(user.getUsername());
                }
            }
            members.sort(String::compareTo);
            item.put("members", members);
            result.add(item);
        }
        result.sort((a, b) -> String.valueOf(a.get("groupname")).compareTo(String.valueOf(b.get("groupname"))));
        return result;
    }


    private List<Map<String, Object>> listRoles(UserDatabase db) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Role role : collect(db.getRoles())) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("rolename", role.getRolename());
            item.put("description", role.getDescription());
            result.add(item);
        }
        result.sort((a, b) -> String.valueOf(a.get("rolename")).compareTo(String.valueOf(b.get("rolename"))));
        return result;
    }


    // ------------------------------------------------------------- Mutations


    private void createUser(HttpServletRequest request, HttpServletResponse response) throws IOException {

        Map<String, Object> body = readJson(request);
        String username = asString(body.get("username"));
        if (!validName(username)) {
            Api.error(response, HttpServletResponse.SC_BAD_REQUEST, "INVALID_NAME",
                    Strings.sm().getString("manager2.usernameMissing", username));
            return;
        }
        if (!body.containsKey("password") || !(body.get("password") instanceof String password)) {
            Api.error(response, HttpServletResponse.SC_BAD_REQUEST, "MISSING_FIELD",
                    Strings.sm().getString("manager2.passwordMissing"));
            return;
        }
        List<String> roles = asStringList(body.get("roles"));

        UserDatabase db = selected(request, body, response);
        if (db == null) {
            return;
        }
        if (!writableForMutation(db, response)) {
            return;
        }

        User user = db.createUser(username, password, asString(body.get("fullName")));
        if (user == null) {
            Api.error(response, HttpServletResponse.SC_CONFLICT, "USER_EXISTS",
                    Strings.sm().getString("manager2.userExists", username));
            return;
        }
        for (String role : roles) {
            user.addRole(findOrCreateRole(db, role));
        }
        save(db, response);
    }


    private void removeUser(HttpServletRequest request, HttpServletResponse response, String username)
            throws IOException {

        UserDatabase db = selected(request, null, response);
        if (db == null) {
            return;
        }
        User user = db.findUser(username);
        if (user == null) {
            Api.error(response, HttpServletResponse.SC_NOT_FOUND, "USER_NOT_FOUND",
                    Strings.sm().getString("manager2.userNotFound", username));
            return;
        }
        if (request.getUserPrincipal() != null && username.equals(request.getUserPrincipal().getName())) {
            Api.error(response, HttpServletResponse.SC_BAD_REQUEST, "SELF_REMOVAL",
                    Strings.sm().getString("manager2.selfRemoval"));
            return;
        }
        if (!writableForMutation(db, response)) {
            return;
        }
        db.removeUser(user);
        save(db, response);
    }


    private void setPassword(HttpServletRequest request, HttpServletResponse response, String username)
            throws IOException {

        Map<String, Object> body = readJson(request);
        if (!body.containsKey("password") || !(body.get("password") instanceof String password)) {
            Api.error(response, HttpServletResponse.SC_BAD_REQUEST, "MISSING_FIELD",
                    Strings.sm().getString("manager2.passwordMissing"));
            return;
        }

        UserDatabase db = selected(request, body, response);
        if (db == null) {
            return;
        }
        User user = db.findUser(username);
        if (user == null) {
            Api.error(response, HttpServletResponse.SC_NOT_FOUND, "USER_NOT_FOUND",
                    Strings.sm().getString("manager2.userNotFound", username));
            return;
        }
        if (!writableForMutation(db, response)) {
            return;
        }
        user.setPassword(password);
        save(db, response);
    }


    private void setUserRoles(HttpServletRequest request, HttpServletResponse response, String username)
            throws IOException {

        Map<String, Object> body = readJson(request);
        List<String> roles = asStringList(body.get("roles"));

        UserDatabase db = selected(request, body, response);
        if (db == null) {
            return;
        }
        User user = db.findUser(username);
        if (user == null) {
            Api.error(response, HttpServletResponse.SC_NOT_FOUND, "USER_NOT_FOUND",
                    Strings.sm().getString("manager2.userNotFound", username));
            return;
        }
        if (!writableForMutation(db, response)) {
            return;
        }
        user.removeRoles();
        for (String role : roles) {
            user.addRole(findOrCreateRole(db, role));
        }
        save(db, response);
    }


    private void createGroup(HttpServletRequest request, HttpServletResponse response) throws IOException {

        Map<String, Object> body = readJson(request);
        String groupname = asString(body.get("groupname"));
        if (!validName(groupname)) {
            Api.error(response, HttpServletResponse.SC_BAD_REQUEST, "INVALID_NAME",
                    Strings.sm().getString("manager2.groupnameMissing", groupname));
            return;
        }
        List<String> roles = asStringList(body.get("roles"));

        UserDatabase db = selected(request, body, response);
        if (db == null) {
            return;
        }
        if (!writableForMutation(db, response)) {
            return;
        }

        Group group = db.createGroup(groupname, asString(body.get("description")));
        if (group == null) {
            Api.error(response, HttpServletResponse.SC_CONFLICT, "GROUP_EXISTS",
                    Strings.sm().getString("manager2.groupExists", groupname));
            return;
        }
        for (String role : roles) {
            group.addRole(findOrCreateRole(db, role));
        }
        save(db, response);
    }


    private void removeGroup(HttpServletRequest request, HttpServletResponse response, String groupname)
            throws IOException {

        UserDatabase db = selected(request, null, response);
        if (db == null) {
            return;
        }
        Group group = db.findGroup(groupname);
        if (group == null) {
            Api.error(response, HttpServletResponse.SC_NOT_FOUND, "GROUP_NOT_FOUND",
                    Strings.sm().getString("manager2.groupNotFound", groupname));
            return;
        }
        if (!writableForMutation(db, response)) {
            return;
        }
        db.removeGroup(group);
        save(db, response);
    }


    private void setGroupMembers(HttpServletRequest request, HttpServletResponse response, String groupname)
            throws IOException {

        Map<String, Object> body = readJson(request);
        List<String> members = asStringList(body.get("members"));

        UserDatabase db = selected(request, body, response);
        if (db == null) {
            return;
        }
        Group group = db.findGroup(groupname);
        if (group == null) {
            Api.error(response, HttpServletResponse.SC_NOT_FOUND, "GROUP_NOT_FOUND",
                    Strings.sm().getString("manager2.groupNotFound", groupname));
            return;
        }

        List<String> missing = new ArrayList<>();
        for (String member : members) {
            if (db.findUser(member) == null) {
                missing.add(member);
            }
        }
        if (!missing.isEmpty()) {
            Api.error(response, HttpServletResponse.SC_BAD_REQUEST, "UNKNOWN_GROUP_MEMBER",
                    Strings.sm().getString("manager2.unknownGroupMember", String.join(", ", missing)));
            return;
        }
        if (!writableForMutation(db, response)) {
            return;
        }

        Set<String> wanted = new LinkedHashSet<>(members);
        for (User user : collect(group.getUsers())) {
            if (!wanted.contains(user.getUsername())) {
                user.removeGroup(group);
            }
        }
        for (String member : members) {
            db.findUser(member).addGroup(group);
        }
        save(db, response);
    }


    private void setGroupRoles(HttpServletRequest request, HttpServletResponse response, String groupname)
            throws IOException {

        Map<String, Object> body = readJson(request);
        List<String> roles = asStringList(body.get("roles"));

        UserDatabase db = selected(request, body, response);
        if (db == null) {
            return;
        }
        Group group = db.findGroup(groupname);
        if (group == null) {
            Api.error(response, HttpServletResponse.SC_NOT_FOUND, "GROUP_NOT_FOUND",
                    Strings.sm().getString("manager2.groupNotFound", groupname));
            return;
        }
        if (!writableForMutation(db, response)) {
            return;
        }
        group.removeRoles();
        for (String role : roles) {
            group.addRole(findOrCreateRole(db, role));
        }
        save(db, response);
    }


    private void createRole(HttpServletRequest request, HttpServletResponse response) throws IOException {

        Map<String, Object> body = readJson(request);
        String rolename = asString(body.get("rolename"));
        if (!validName(rolename)) {
            Api.error(response, HttpServletResponse.SC_BAD_REQUEST, "INVALID_NAME",
                    Strings.sm().getString("manager2.rolenameMissing", rolename));
            return;
        }

        UserDatabase db = selected(request, body, response);
        if (db == null) {
            return;
        }
        if (!writableForMutation(db, response)) {
            return;
        }

        Role role = db.createRole(rolename, asString(body.get("description")));
        if (role == null) {
            Api.error(response, HttpServletResponse.SC_CONFLICT, "ROLE_EXISTS",
                    Strings.sm().getString("manager2.roleExists", rolename));
            return;
        }
        save(db, response);
    }


    private void removeRole(HttpServletRequest request, HttpServletResponse response, String rolename)
            throws IOException {

        UserDatabase db = selected(request, null, response);
        if (db == null) {
            return;
        }
        Role role = db.findRole(rolename);
        if (role == null) {
            Api.error(response, HttpServletResponse.SC_NOT_FOUND, "ROLE_NOT_FOUND",
                    Strings.sm().getString("manager2.roleNotFound", rolename));
            return;
        }
        // Removing a role detaches it from every user and group that holds it,
        // so a manager must not be able to remove a role they hold themselves:
        // that would drop their own access on the next sign in.
        String principalName = request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : null;
        if (principalName != null && holdsRole(db, principalName, rolename)) {
            Api.error(response, HttpServletResponse.SC_BAD_REQUEST, "SELF_ROLE_REMOVAL",
                    Strings.sm().getString("manager2.selfRoleRemoval", rolename));
            return;
        }
        if (!writableForMutation(db, response)) {
            return;
        }
        db.removeRole(role);
        save(db, response);
    }


    // -------------------------------------------------------------- Discovery


    /**
     * Discover the user databases configured as JNDI resources of the global naming context of this server. Bindings
     * whose type is {@code org.apache.catalina.UserDatabase} are resolved (other bindings are left untouched).
     */
    private List<Map.Entry<String, UserDatabase>> discover() {

        List<Map.Entry<String, UserDatabase>> found = new ArrayList<>();
        if (server == null) {
            return found;
        }
        javax.naming.Context jndi = server.getGlobalNamingContext();
        if (jndi == null) {
            return found;
        }
        try {
            NamingEnumeration<Binding> bindings = jndi.listBindings("");
            while (bindings.hasMore()) {
                String name = bindings.next().getName();
                if (name.isEmpty()) {
                    continue;
                }
                try {
                    Object bound = jndi.lookupLink(name);
                    if (bound instanceof UserDatabase udb) {
                        found.add(new AbstractMap.SimpleEntry<>(name, udb));
                        continue;
                    }
                    if (bound instanceof Reference ref && UDB_TYPE.equals(ref.getClassName())) {
                        Object resolved = jndi.lookup(name);
                        if (resolved instanceof UserDatabase udb) {
                            found.add(new AbstractMap.SimpleEntry<>(name, udb));
                        }
                    }
                } catch (NamingException e) {
                    log(Strings.sm().getString("manager2.userDatabaseLookup", name), e);
                }
            }
        } catch (NamingException e) {
            log(Strings.sm().getString("manager2.error.users"), e);
        }
        return found;
    }


    /**
     * Select the user database to operate on. When no name is given, the single configured database is used (or the one
     * named {@code UserDatabase}, or the first one, when several are configured). Writes an error response and returns
     * {@code null} on failure.
     */
    private Map.Entry<String, UserDatabase> select(String name, List<Map.Entry<String, UserDatabase>> found,
            HttpServletResponse response) throws IOException {

        if (name == null || name.isEmpty()) {
            if (found.size() == 1) {
                return found.get(0);
            }
            for (Map.Entry<String, UserDatabase> entry : found) {
                if ("UserDatabase".equals(entry.getKey())) {
                    return entry;
                }
            }
            return found.get(0);
        }
        for (Map.Entry<String, UserDatabase> entry : found) {
            if (entry.getKey().equals(name)) {
                return entry;
            }
        }
        Api.error(response, HttpServletResponse.SC_BAD_REQUEST, "USER_DATABASE_NOT_FOUND",
                Strings.sm().getString("manager2.userDatabaseNotFound", name));
        return null;
    }


    /**
     * The user database selected by the {@code name} JSON body field (POST requests) or query parameter (DELETE
     * requests). Writes an error response and returns {@code null} on failure.
     */
    private UserDatabase selected(HttpServletRequest request, Map<String, Object> body, HttpServletResponse response)
            throws IOException {

        String name = null;
        if (body != null && body.get("name") instanceof String s && !s.isEmpty()) {
            name = s;
        } else {
            String param = request.getParameter("name");
            name = (param == null || param.isEmpty()) ? null : param;
        }
        List<Map.Entry<String, UserDatabase>> found = discover();
        if (found.isEmpty()) {
            Api.error(response, HttpServletResponse.SC_NOT_FOUND, "USER_DATABASE_MISSING",
                    Strings.sm().getString("manager2.userDatabaseMissing"));
            return null;
        }
        Map.Entry<String, UserDatabase> selected = select(name, found, response);
        return selected == null ? null : selected.getValue();
    }


    /**
     * Check that the database accepts mutations: it must not be read-only and (for the file based database) its storage
     * location must be writable. Writes an error response and returns {@code false} on failure.
     */
    private boolean writableForMutation(UserDatabase db, HttpServletResponse response) throws IOException {

        if (readonly(db)) {
            Api.error(response, HttpServletResponse.SC_BAD_REQUEST, "USER_DATABASE_READONLY",
                    Strings.sm().getString("manager2.userDatabaseReadonly", db.getId()));
            return false;
        }
        if (!writable(db)) {
            Api.error(response, HttpServletResponse.SC_BAD_REQUEST, "USER_DATABASE_NOT_WRITABLE",
                    Strings.sm().getString("manager2.userDatabaseNotWritable", db.getId()));
            return false;
        }
        return true;
    }


    private void save(UserDatabase db, HttpServletResponse response) throws IOException {
        try {
            db.save();
        } catch (Exception e) {
            log(Strings.sm().getString("manager2.error.users"), e);
            Api.error(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "USER_DATABASE_SAVE_FAILED",
                    Strings.sm().getString("manager2.userDatabaseSaveFailed", String.valueOf(e.getMessage())));
            return;
        }
        Api.ok(response, Strings.sm().getString("manager2.usersSaved"));
    }


    // ---------------------------------------------------------------- Helpers


    /**
     * Whether the database is configured read-only (only the file based database exposes this; other implementations
     * assume writable).
     */
    private static boolean readonly(UserDatabase db) {
        return db instanceof MemoryUserDatabase memory && memory.getReadonly();
    }


    /**
     * Whether the database can be persisted (only the file based database exposes this; other implementations assume
     * writable).
     */
    private static boolean writable(UserDatabase db) {
        return !(db instanceof MemoryUserDatabase memory) || memory.isWritable();
    }


    private static Role findOrCreateRole(UserDatabase db, String rolename) {
        Role role = db.findRole(rolename);
        return role != null ? role : db.createRole(rolename, null);
    }


    /**
     * Whether the named user holds the given role, directly or through one of their groups.
     */
    private static boolean holdsRole(UserDatabase db, String username, String rolename) {
        User user = db.findUser(username);
        if (user == null) {
            return false;
        }
        for (Role role : collect(user.getRoles())) {
            if (rolename.equals(role.getRolename())) {
                return true;
            }
        }
        for (Group group : collect(user.getGroups())) {
            for (Role role : collect(group.getRoles())) {
                if (rolename.equals(role.getRolename())) {
                    return true;
                }
            }
        }
        return false;
    }


    /**
     * {@code true} for a name that is safe in the XML storage format (the names are persisted in comma separated lists)
     * and in a URL path segment (the user and group names are also addressed that way): a non empty string of letters,
     * digits and the characters {@code . _ - @ +}.
     */
    private static boolean validName(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            boolean valid = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '.' ||
                    c == '_' || c == '-' || c == '@' || c == '+';
            if (!valid) {
                return false;
            }
        }
        return true;
    }


    private static <T> List<T> collect(Iterator<T> iterator) {
        List<T> result = new ArrayList<>();
        while (iterator.hasNext()) {
            result.add(iterator.next());
        }
        return result;
    }


    private static <T> List<String> collectNames(Iterator<T> iterator, Function<T, String> nameOf) {
        List<String> result = new ArrayList<>();
        for (T item : collect(iterator)) {
            result.add(nameOf.apply(item));
        }
        Collections.sort(result);
        return result;
    }


    /**
     * The servlet path plus the path info, e.g. {@code /api/users/manager1/roles}.
     */
    private static String fullPath(HttpServletRequest request) {
        String path = request.getServletPath();
        String info = request.getPathInfo();
        if (info != null && !info.isEmpty()) {
            path = path + info;
        }
        return path;
    }


    /**
     * The path segments after the leading {@code /api/users} or {@code /api/groups} segments, e.g.
     * {@code [manager1, roles]}.
     */
    private static String[] tail(String path) {
        String[] segments = path.split("/");
        String[] result = new String[Math.max(0, segments.length - 3)];
        System.arraycopy(segments, 3, result, 0, result.length);
        return result;
    }


    private static Map<String, Object> readJson(HttpServletRequest request) throws IOException {
        String body = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (body.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return new JSONParser(body).parseObject();
        } catch (Exception e) {
            throw new IllegalArgumentException(Strings.sm().getString("manager2.invalidJson", e.getMessage()), e);
        }
    }


    private static String asString(Object value) {
        return value instanceof String s && !s.isEmpty() ? s : null;
    }


    /**
     * A JSON array of strings (empty when absent).
     */
    private static List<String> asStringList(Object value) {
        if (value == null) {
            return new ArrayList<>();
        }
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException(Strings.sm().getString("manager2.invalidJson",
                    Strings.sm().getString("manager2.expectedStringArray")));
        }
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof String s) || !validName(s)) {
                throw new IllegalArgumentException(Strings.sm().getString("manager2.invalidJson",
                        Strings.sm().getString("manager2.expectedStringArray")));
            }
            result.add(s);
        }
        return result;
    }


    @Override
    public String getServletInfo() {
        return "Manager2 Users API";
    }
}
