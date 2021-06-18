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
package org.apache.catalina.realm;


import java.security.Principal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.naming.Context;
import javax.sql.DataSource;

import org.apache.catalina.LifecycleException;
import org.apache.naming.ContextBindings;

/**
*
* Implementation of <b>Realm</b> that works with any JDBC JNDI DataSource.
* See the Realm How-To for more details on how to set up the database and
* for configuration options.
*
* @author Glenn L. Nielsen
* @author Craig R. McClanahan
* @author Carson McDonald
* @author Ignacio Ortega
*/
public class DataSourceRealm extends RealmBase {

    /**
     * String object signaling an empty list of user attributes, that is, no valid
     * or existing user attributes to additionally query from the user table have
     * been specified. Used to prevent entering the DCL lock in method
     * <code>getUserAttributesStatement</code> every time in case there are no user
     * attributes.
     */
    private static final String USER_ATTRIBUTES_NONE_REQUESTED = new String();

    // ----------------------------------------------------- Instance Variables


    /**
     * The generated string for the roles PreparedStatement
     */
    private String preparedRoles = null;


    /**
     * The generated string for the credentials PreparedStatement
     */
    private String preparedCredentials = null;


    /**
     * The generated string for the user attributes PreparedStatement
     */
    private String preparedAttributesTail = null;


    /**
     * The generated string for the user attributes available PreparedStatement
     */
    private String preparedAttributesAvailable = null;


    /**
     * The name of the JNDI JDBC DataSource
     */
    protected String dataSourceName = null;


    /**
     * Context local datasource.
     */
    protected boolean localDataSource = false;


    /**
     * The column in the user role table that names a role
     */
    protected String roleNameCol = null;


    /**
     * The column in the user table that holds the user's credentials
     */
    protected String userCredCol = null;


    /**
     * The column in the user table that holds the user's name
     */
    protected String userNameCol = null;


    /**
     * The table that holds the relation between user's and roles
     */
    protected String userRoleTable = null;


    /**
     * The table that holds user data.
     */
    protected String userTable = null;


    /**
     * Last connection attempt.
     */
    private volatile boolean connectionSuccess = true;


	/**
	 * The comma separated names of user attributes to additionally query from
	 * the user table. These will be provided to the user through the created
	 * Principal's <i>attributes</i> map.
	 */
	protected String userAttributes;


    /**
     * Generated SQL statement to query additional user attributes from the user
     * table.
     */
	private volatile String userAttributesStatement;
	private final Object userAttributesStatementLock = new Object();


    // ------------------------------------------------------------- Properties


    /**
     * @return the name of the JNDI JDBC DataSource.
     */
    public String getDataSourceName() {
        return dataSourceName;
    }

    /**
     * Set the name of the JNDI JDBC DataSource.
     *
     * @param dataSourceName the name of the JNDI JDBC DataSource
     */
    public void setDataSourceName( String dataSourceName) {
      this.dataSourceName = dataSourceName;
    }

    /**
     * @return if the datasource will be looked up in the webapp JNDI Context.
     */
    public boolean getLocalDataSource() {
        return localDataSource;
    }

    /**
     * Set to true to cause the datasource to be looked up in the webapp JNDI
     * Context.
     *
     * @param localDataSource the new flag value
     */
    public void setLocalDataSource(boolean localDataSource) {
      this.localDataSource = localDataSource;
    }

    /**
     * @return the column in the user role table that names a role.
     */
    public String getRoleNameCol() {
        return roleNameCol;
    }

    /**
     * Set the column in the user role table that names a role.
     *
     * @param roleNameCol The column name
     */
    public void setRoleNameCol( String roleNameCol ) {
        this.roleNameCol = roleNameCol;
    }

    /**
     * @return the column in the user table that holds the user's credentials.
     */
    public String getUserCredCol() {
        return userCredCol;
    }

    /**
     * Set the column in the user table that holds the user's credentials.
     *
     * @param userCredCol The column name
     */
    public void setUserCredCol( String userCredCol ) {
       this.userCredCol = userCredCol;
    }

    /**
     * @return the column in the user table that holds the user's name.
     */
    public String getUserNameCol() {
        return userNameCol;
    }

    /**
     * Set the column in the user table that holds the user's name.
     *
     * @param userNameCol The column name
     */
    public void setUserNameCol( String userNameCol ) {
       this.userNameCol = userNameCol;
    }

    /**
     * @return the table that holds the relation between user's and roles.
     */
    public String getUserRoleTable() {
        return userRoleTable;
    }

    /**
     * Set the table that holds the relation between user's and roles.
     *
     * @param userRoleTable The table name
     */
    public void setUserRoleTable( String userRoleTable ) {
        this.userRoleTable = userRoleTable;
    }

    /**
     * @return the table that holds user data..
     */
    public String getUserTable() {
        return userTable;
    }

    /**
     * Set the table that holds user data.
     *
     * @param userTable The table name
     */
    public void setUserTable( String userTable ) {
      this.userTable = userTable;
    }

    /**
	 * @return the comma separated names of user attributes to additionally
	 *         query from the user table
	 */
	public String getUserAttributes() {
		return userAttributes;
	}

    /**
     * Set the comma separated names of user attributes to additionally query from
     * the user table. These will be provided to the user through the created
     * Principal's <i>attributes</i> map. In this map, each field value is bound to
     * the field's name, that is, the name of the field serves as the key of the
     * mapping.
     * <p>
     * If set to the wildcard character, or, if the wildcard character is part of
     * the comma separated list, all available attributes - except the
     * <i>password</i> attribute (as specified by <code>userCredCol</code>) - are
     * queried. The wildcard character is defined by constant
     * {@link RealmBase#USER_ATTRIBUTES_WILDCARD}. It defaults to the asterisk (*)
     * character.
     *
     * @param userAttributes the comma separated names of user attributes
     */
	public void setUserAttributes(String userAttributes) {
		this.userAttributes = userAttributes;
	}


    // --------------------------------------------------------- Public Methods

    /**
     * Return the Principal associated with the specified username and
     * credentials, if there is one; otherwise return <code>null</code>.
     *
     * If there are any errors with the JDBC connection, executing
     * the query or anything we return null (don't authenticate). This
     * event is also logged, and the connection will be closed so that
     * a subsequent request will automatically re-open it.
     *
     * @param username Username of the Principal to look up
     * @param credentials Password or other credentials to use in
     *  authenticating this username
     * @return the associated principal, or <code>null</code> if there is none.
     */
    @Override
    public Principal authenticate(String username, String credentials) {

        // No user or no credentials
        // Can't possibly authenticate, don't bother the database then
        if (username == null || credentials == null) {
            return null;
        }

        Connection dbConnection = null;

        // Ensure that we have an open database connection
        dbConnection = open();
        if (dbConnection == null) {
            // If the db connection open fails, return "not authenticated"
            return null;
        }

        try
        {
            // Acquire a Principal object for this user
            return authenticate(dbConnection, username, credentials);
        }
        finally
        {
            close(dbConnection);
        }
    }


    @Override
    public boolean isAvailable() {
        return connectionSuccess;
    }

    // -------------------------------------------------------- Package Methods


    // ------------------------------------------------------ Protected Methods


    /**
     * Return the Principal associated with the specified username and
     * credentials, if there is one; otherwise return <code>null</code>.
     *
     * @param dbConnection The database connection to be used
     * @param username Username of the Principal to look up
     * @param credentials Password or other credentials to use in
     *  authenticating this username
     * @return the associated principal, or <code>null</code> if there is none.
     */
    protected Principal authenticate(Connection dbConnection,
                                     String username,
                                     String credentials) {
        // No user or no credentials
        // Can't possibly authenticate, don't bother the database then
        if (username == null || credentials == null) {
            if (containerLog.isTraceEnabled()) {
                containerLog.trace(sm.getString("dataSourceRealm.authenticateFailure",
                                                username));
            }
            return null;
        }

        // Look up the user's credentials
        String dbCredentials = getPassword(dbConnection, username);

        if(dbCredentials == null) {
            // User was not found in the database.
            // Waste a bit of time as not to reveal that the user does not exist.
            getCredentialHandler().mutate(credentials);

            if (containerLog.isTraceEnabled()) {
                containerLog.trace(sm.getString("dataSourceRealm.authenticateFailure",
                                                username));
            }
            return null;
        }

        // Validate the user's credentials
        boolean validated = getCredentialHandler().matches(credentials, dbCredentials);

        if (validated) {
            if (containerLog.isTraceEnabled()) {
                containerLog.trace(sm.getString("dataSourceRealm.authenticateSuccess",
                                                username));
            }
        } else {
            if (containerLog.isTraceEnabled()) {
                containerLog.trace(sm.getString("dataSourceRealm.authenticateFailure",
                                                username));
            }
            return null;
        }

        ArrayList<String> list = getRoles(dbConnection, username);
        Map<String, Object> attrs = getUserAttributesMap(dbConnection, username);

        // Create and return a suitable Principal for this user
        return new GenericPrincipal(username, list, null, null, null, attrs);
    }


    /**
     * Close the specified database connection.
     *
     * @param dbConnection The connection to be closed
     */
    protected void close(Connection dbConnection) {

        // Do nothing if the database connection is already closed
        if (dbConnection == null) {
            return;
        }

        // Commit if not auto committed
        try {
            if (!dbConnection.getAutoCommit()) {
                dbConnection.commit();
            }
        } catch (SQLException e) {
            containerLog.error(sm.getString("dataSourceRealm.commit"), e);
        }

        // Close this database connection, and log any errors
        try {
            dbConnection.close();
        } catch (SQLException e) {
            containerLog.error(sm.getString("dataSourceRealm.close"), e); // Just log it here
        }

    }

    /**
     * Open the specified database connection.
     *
     * @return Connection to the database
     */
    protected Connection open() {

        try {
            Context context = null;
            if (localDataSource) {
                context = ContextBindings.getClassLoader();
                context = (Context) context.lookup("comp/env");
            } else {
                context = getServer().getGlobalNamingContext();
            }
            DataSource dataSource = (DataSource)context.lookup(dataSourceName);
            Connection connection = dataSource.getConnection();
            connectionSuccess = true;
            return connection;
        } catch (Exception e) {
            connectionSuccess = false;
            // Log the problem for posterity
            containerLog.error(sm.getString("dataSourceRealm.exception"), e);
        }
        return null;
    }

    /**
     * @return the password associated with the given principal's user name.
     */
    @Override
    protected String getPassword(String username) {

        Connection dbConnection = null;

        // Ensure that we have an open database connection
        dbConnection = open();
        if (dbConnection == null) {
            return null;
        }

        try {
            return getPassword(dbConnection, username);
        } finally {
            close(dbConnection);
        }
    }


    /**
     * Return the password associated with the given principal's user name.
     *
     * @param dbConnection The database connection to be used
     * @param username Username for which password should be retrieved
     *
     * @return the password for the specified user
     */
    protected String getPassword(Connection dbConnection, String username) {

        String dbCredentials = null;

        try (PreparedStatement stmt = dbConnection.prepareStatement(preparedCredentials)) {
            stmt.setString(1, username);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    dbCredentials = rs.getString(1);
                }

                return (dbCredentials != null) ? dbCredentials.trim() : null;
            }
        } catch (SQLException e) {
            containerLog.error(sm.getString("dataSourceRealm.getPassword.exception", username), e);
        }

        return null;
    }


    /**
     * Return the Principal associated with the given user name.
     * @param username the user name
     * @return the principal object
     */
    @Override
    protected Principal getPrincipal(String username) {
        Connection dbConnection = open();
        if (dbConnection == null) {
            return new GenericPrincipal(username, null);
        }
        try {
            return new GenericPrincipal(username, getRoles(dbConnection, username), null, null,
                    null, getUserAttributesMap(dbConnection, username));
        } finally {
            close(dbConnection);
        }

    }

    /**
     * Return the roles associated with the given user name.
     * @param username User name for which roles should be retrieved
     * @return an array list of the role names
     */
    protected ArrayList<String> getRoles(String username) {

        Connection dbConnection = null;

        // Ensure that we have an open database connection
        dbConnection = open();
        if (dbConnection == null) {
            return null;
        }

        try {
            return getRoles(dbConnection, username);
        } finally {
            close(dbConnection);
        }
    }


    /**
     * Return the roles associated with the given user name.
     *
     * @param dbConnection The database connection to be used
     * @param username User name for which roles should be retrieved
     *
     * @return an array list of the role names
     */
    protected ArrayList<String> getRoles(Connection dbConnection, String username) {

        if (allRolesMode != AllRolesMode.STRICT_MODE && !isRoleStoreDefined()) {
            // Using an authentication only configuration and no role store has
            // been defined so don't spend cycles looking
            return null;
        }

        // Using a Set removes duplicate roles
        Set<String> roles = null;

        try (PreparedStatement stmt = dbConnection.prepareStatement(preparedRoles)) {
            stmt.setString(1, username);

            try (ResultSet rs = stmt.executeQuery()) {
                roles = new HashSet<>();

                while (rs.next()) {
                    String role = rs.getString(1);
                    if (role != null) {
                        roles.add(role.trim());
                    }
                }
                return new ArrayList<String>(roles);
            }
        } catch(SQLException e) {
            containerLog.error(sm.getString("dataSourceRealm.getRoles.exception", username), e);
        }

        return null;
    }


    private boolean isRoleStoreDefined() {
        return userRoleTable != null || roleNameCol != null;
    }


	protected Map<String, Object> getUserAttributesMap(Connection dbConnection,
			String username) {

	    String preparedAttributes = getUserAttributesStatement(dbConnection);
        if (preparedAttributes == null || preparedAttributes == USER_ATTRIBUTES_NONE_REQUESTED) {
            // The above reference comparison is intentional. USER_ATTRIBUTES_NONE_REQUESTED
            // is a tag object (empty String) to distinguish between null (not yet
            // initialized) and empty (no attributes requested).
	        // TODO Could as well be changed to `preparedAttributes.lenghth() = 0` 

            // Return null if no user attributes are requested (or if the statement was not
            // yet built successfully) 
			return null;
		}

	    try (PreparedStatement stmt =
				dbConnection.prepareStatement(preparedAttributes)) {
			stmt.setString(1, username);

			try (ResultSet rs = stmt.executeQuery()) {

			    if (rs.next()) {
			        Map<String, Object> attrs = new LinkedHashMap<>();
				    ResultSetMetaData md = rs.getMetaData();
				    int ncols = md.getColumnCount();
				    for (int columnIndex = 1; columnIndex <= ncols; columnIndex++) {
				        String columnName = md.getColumnName(columnIndex);
				        // Ignore case, database may have case-insensitive field names
				        if (columnName.equalsIgnoreCase(userCredCol)) {
                            // Always skip userCredCol (must be there if all columns
                            // have been requested)  
				            continue;
				        }
				        attrs.put(columnName, rs.getObject(columnIndex));
					}
				    return attrs.size() > 0 ? attrs : null;
				}
			}
		} catch (SQLException e) {
			containerLog.error(sm.getString(
					"dataSourceRealm.getUserAttributes.exception", username), e);
		}

		return null;
	}


    /**
     * Return the SQL statement for querying additional user attributes. The
     * statement is lazily initialized (<i>lazily initialized singleton</i> with
     * <i>double-checked locking, DCL</i>) since building it may require an extra
     * database query under some conditions.
     * 
     * @param dbConnection connection for accessing the database
     */
    private String getUserAttributesStatement(Connection dbConnection) {
        // DCL so userAttributesStatement MUST be volatile
        if (userAttributesStatement == null) {
            synchronized (userAttributesStatementLock) {
                if (userAttributesStatement == null) {
                    List<String> requestedAttributes = parseUserAttributes(userAttributes);
                    if (requestedAttributes == null) {
                        return USER_ATTRIBUTES_NONE_REQUESTED;
                    }
                    if (requestedAttributes.size() > 0
                            && requestedAttributes.get(0).equals(USER_ATTRIBUTES_WILDCARD)) {
                        userAttributesStatement = "SELECT *" + preparedAttributesTail;
                        return userAttributesStatement;
                    }
                    List<String> availableUserAttributes = getAvailableUserAttributes(dbConnection);
                    if (availableUserAttributes == null) {
                        // Failed getting all available user attributes (this has already been
                        // logged) so, just return, leaving userAttributesStatement null (aka
                        // uninitialized, will try again next time).
                        return null;
                    }
                    requestedAttributes = validateUserAttributes(requestedAttributes,
                            getDeniedUserAttributes(), availableUserAttributes);
                    StringBuilder sb = new StringBuilder("SELECT ");
                    boolean first = true;
                    for (String attr : requestedAttributes) {
                        if (first) {
                            first = false;
                        } else {
                            sb.append(", ");
                        }
                        sb.append(attr);
                    }
                    userAttributesStatement = sb.append(preparedAttributesTail).toString();
                }
            }
        }
        return userAttributesStatement;
    }


    /**
     * Return a list of all available user attributes. The list contains all field
     * names of the user table, except for fields for which access is denied (e. g.
     * field <code>userCredCol</code>).
     * 
     * @param dbConnection connection for accessing the database
     */
	private List<String> getAvailableUserAttributes(Connection dbConnection) {

        try (PreparedStatement stmt =
                dbConnection.prepareStatement(preparedAttributesAvailable)) {

            try (ResultSet rs = stmt.executeQuery()) {

                // Query is not expected to return any rows (...WHERE FALSE) so, must not call
                // next(). ResultSetMetadata is available before calling next() anyway.  
                List<String> result = new ArrayList<>();
                ResultSetMetaData md = rs.getMetaData();
                int ncols = md.getColumnCount();
                for (int columnIndex = 1; columnIndex <= ncols; columnIndex++) {
                    String columnName = md.getColumnName(columnIndex);
                    // Ignore case, database may have case-insensitive field names
                    if (columnName.equalsIgnoreCase(userCredCol)) {
                        // always skip userCredCol
                        continue;
                    }
                    result.add(columnName);
                }
                return result;
            }
        } catch (SQLException e) {
            containerLog.error(sm.getString(
                    "dataSourceRealm.getAvailableUserAttributes.exception"), e);
        }

        return null;
    }


    /**
     * Return a list of user attribute names for which access is denied or
     * <code>null</code>, if no restricted columns are known. That list consists of
     * the name of the <i>user credential</i> column specified in configuration
     * attribute <code>userCredCol</code>.
     */
    private List<String> getDeniedUserAttributes() {
        String attr = getUserCredCol();
        if (attr != null && !attr.trim().isEmpty()) {
            return Collections.singletonList(attr.trim());
        }
        return null;
    }


    // ------------------------------------------------------ Lifecycle Methods

    /**
     * Prepare for the beginning of active use of the public methods of this
     * component and implement the requirements of
     * {@link org.apache.catalina.util.LifecycleBase#startInternal()}.
     *
     * @exception LifecycleException if this component detects a fatal error
     *  that prevents this component from being used
     */
    @Override
    protected void startInternal() throws LifecycleException {

        // Create the roles PreparedStatement string
        StringBuilder temp = new StringBuilder("SELECT ");
        temp.append(roleNameCol);
        temp.append(" FROM ");
        temp.append(userRoleTable);
        temp.append(" WHERE ");
        temp.append(userNameCol);
        temp.append(" = ?");
        preparedRoles = temp.toString();

        // Create the credentials PreparedStatement string
        temp = new StringBuilder("SELECT ");
        temp.append(userCredCol);
        temp.append(" FROM ");
        temp.append(userTable);
        temp.append(" WHERE ");
        temp.append(userNameCol);
        temp.append(" = ?");
        preparedCredentials = temp.toString();

        // Create the user attributes PreparedStatement string (only its tail w/o SELECT
        // clause)
        temp = new StringBuilder(" FROM ");
        temp.append(userTable);
        temp.append(" WHERE ");
        temp.append(userNameCol);
        temp.append(" = ?");
        preparedAttributesTail = temp.toString();

        // Create the available user attributes PreparedStatement string
        temp = new StringBuilder("SELECT * FROM ");
        temp.append(userTable);
        temp.append(" WHERE FALSE");
        preparedAttributesAvailable = temp.toString();

        super.startInternal();
    }
}
