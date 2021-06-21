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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.Principal;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.security.auth.login.LoginContext;

import org.apache.catalina.TomcatPrincipal;
import org.ietf.jgss.GSSCredential;

/**
 * Generic implementation of <strong>java.security.Principal</strong> that
 * is available for use by <code>Realm</code> implementations.
 *
 * @author Craig R. McClanahan
 */
public class GenericPrincipal implements TomcatPrincipal, Serializable {

    private static final long serialVersionUID = 1L;


    // ----------------------------------------------------------- Constructors

    /**
     * Construct a new Principal, associated with the specified Realm, for the
     * specified username, with no roles.
     *
     * @param name The username of the user represented by this Principal
     */
    public GenericPrincipal(String name) {
        this(name, null);
    }

    /**
     * Construct a new Principal, associated with the specified Realm, for the
     * specified username, with the specified role names (as Strings).
     *
     * @param name The username of the user represented by this Principal
     * @param roles List of roles (must be Strings) possessed by this user
     */
    public GenericPrincipal(String name, List<String> roles) {
        this(name, roles, null);
    }

    /**
     * Construct a new Principal, associated with the specified Realm, for the
     * specified username, with the specified role names (as Strings).
     *
     * @param name The username of the user represented by this Principal
     * @param password  Unused
     * @param roles List of roles (must be Strings) possessed by this user
     *
     * @deprecated This method will be removed in Tomcat 11 onwards
     */
    @Deprecated
    public GenericPrincipal(String name, String password, List<String> roles) {
        this(name, roles, null);
    }

    /**
     * Construct a new Principal, associated with the specified Realm, for the
     * specified username, with the specified role names (as Strings).
     *
     * @param name The username of the user represented by this Principal
     * @param roles List of roles (must be Strings) possessed by this user
     * @param userPrincipal - the principal to be returned from the request
     *        getUserPrincipal call if not null; if null, this will be returned
     */
    public GenericPrincipal(String name, List<String> roles,
            Principal userPrincipal) {
        this(name, roles, userPrincipal, null);
    }

    /**
     * Construct a new Principal, associated with the specified Realm, for the
     * specified username, with the specified role names (as Strings).
     *
     * @param name The username of the user represented by this Principal
     * @param password Unused
     * @param roles List of roles (must be Strings) possessed by this user
     * @param userPrincipal - the principal to be returned from the request
     *        getUserPrincipal call if not null; if null, this will be returned
     *
     * @deprecated This method will be removed in Tomcat 11 onwards
     */
    @Deprecated
    public GenericPrincipal(String name, String password, List<String> roles,
            Principal userPrincipal) {
        this(name, roles, userPrincipal, null);
    }

    /**
     * Construct a new Principal, associated with the specified Realm, for the
     * specified username, with the specified role names (as Strings).
     *
     * @param name The username of the user represented by this Principal
     * @param roles List of roles (must be Strings) possessed by this user
     * @param userPrincipal - the principal to be returned from the request
     *        getUserPrincipal call if not null; if null, this will be returned
     * @param loginContext  - If provided, this will be used to log out the user
     *        at the appropriate time
     */
    public GenericPrincipal(String name, List<String> roles,
            Principal userPrincipal, LoginContext loginContext) {
        this(name, roles, userPrincipal, loginContext, null, null);
    }

    /**
     * Construct a new Principal, associated with the specified Realm, for the
     * specified username, with the specified role names (as Strings).
     *
     * @param name The username of the user represented by this Principal
     * @param password Unused
     * @param roles List of roles (must be Strings) possessed by this user
     * @param userPrincipal - the principal to be returned from the request
     *        getUserPrincipal call if not null; if null, this will be returned
     * @param loginContext  - If provided, this will be used to log out the user
     *        at the appropriate time
     *
     * @deprecated This method will be removed in Tomcat 11 onwards
     */
    @Deprecated
    public GenericPrincipal(String name, String password, List<String> roles,
            Principal userPrincipal, LoginContext loginContext) {
        this(name, roles, userPrincipal, loginContext, null, null);
    }

    /**
     * Construct a new Principal, associated with the specified Realm, for the
     * specified username, with the specified role names (as Strings).
     *
     * @param name The username of the user represented by this Principal
     * @param roles List of roles (must be Strings) possessed by this user
     * @param userPrincipal - the principal to be returned from the request
     *        getUserPrincipal call if not null; if null, this will be returned
     * @param loginContext  - If provided, this will be used to log out the user
     *        at the appropriate time
     * @param gssCredential - If provided, the user's delegated credentials
     * @param attributes - If provided, additional attributes associated with
     *        this Principal
     */
    public GenericPrincipal(String name, List<String> roles,
            Principal userPrincipal, LoginContext loginContext,
            GSSCredential gssCredential, Map<String, Object> attributes) {
        super();
        this.name = name;
        this.userPrincipal = userPrincipal;
        if (roles == null) {
            this.roles = new String[0];
        } else {
            this.roles = roles.toArray(new String[0]);
            if (this.roles.length > 1) {
                Arrays.sort(this.roles);
            }
        }
        this.loginContext = loginContext;
        this.gssCredential = gssCredential;
        this.attributes = attributes;
    }


    /**
     * Construct a new Principal, associated with the specified Realm, for the
     * specified username, with the specified role names (as Strings).
     *
     * @param name The username of the user represented by this Principal
     * @param password Unused
     * @param roles List of roles (must be Strings) possessed by this user
     * @param userPrincipal - the principal to be returned from the request
     *        getUserPrincipal call if not null; if null, this will be returned
     * @param loginContext  - If provided, this will be used to log out the user
     *        at the appropriate time
     * @param gssCredential - If provided, the user's delegated credentials
     *
     * @deprecated This method will be removed in Tomcat 11 onwards
     */
    @Deprecated
    public GenericPrincipal(String name, String password, List<String> roles,
            Principal userPrincipal, LoginContext loginContext,
            GSSCredential gssCredential) {
        this(name, roles, userPrincipal, loginContext, gssCredential, null);
    }


    // -------------------------------------------------------------- Properties

    /**
     * The username of the user represented by this Principal.
     */
    protected final String name;

    @Override
    public String getName() {
        return this.name;
    }

    /**
     * The set of roles associated with this user.
     */
    protected final String[] roles;

    public String[] getRoles() {
        return this.roles.clone();
    }


    /**
     * The authenticated Principal to be exposed to applications.
     */
    protected final Principal userPrincipal;

    @Override
    public Principal getUserPrincipal() {
        if (userPrincipal != null) {
            return userPrincipal;
        } else {
            return this;
        }
    }


    /**
     * The JAAS LoginContext, if any, used to authenticate this Principal.
     * Kept so we can call logout().
     */
    protected final transient LoginContext loginContext;


    /**
     * The user's delegated credentials.
     */
    protected transient GSSCredential gssCredential = null;

    @Override
    public GSSCredential getGssCredential() {
        return this.gssCredential;
    }
    protected void setGssCredential(GSSCredential gssCredential) {
        this.gssCredential = gssCredential;
    }
    
    /**
     * The additional attributes associated with this Principal.
     */
    protected final Map<String, Object> attributes;


    // ---------------------------------------------------------- Public Methods

    /**
     * Does the user represented by this Principal possess the specified role?
     *
     * @param role Role to be tested
     *
     * @return <code>true</code> if this Principal has been assigned the given
     *         role, otherwise <code>false</code>
     */
    public boolean hasRole(String role) {
        if ("*".equals(role)) { // Special 2.4 role meaning everyone
            return true;
        }
        if (role == null) {
            return false;
        }
        return Arrays.binarySearch(roles, role) >= 0;
    }


    /**
     * Return a String representation of this object, which exposes only
     * information that should be public.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("GenericPrincipal[");
        boolean first = true;
        sb.append(this.name);
        sb.append('(');
        for (String role : roles) {
            if (first) {
                first = false;
            } else {
                sb.append(',');
            }
            sb.append(role);
        }
        sb.append(")]");
        return sb.toString();
    }


    @Override
    public void logout() throws Exception {
        if (loginContext != null) {
            loginContext.logout();
        }
        if (gssCredential != null) {
            gssCredential.dispose();
        }
    }


    @Override
    public Object getAttribute(String name) {
        if (attributes == null || name == null) {
            return null;
        }
        Object value = attributes.get(name);
        if (value == null) {
            return null;
        }
        Object copy = copyObject(value);
        return copy != null ? copy : value.toString();
    }


    @Override
    public Enumeration<String> getAttributeNames() {
        if (attributes == null) {
            return Collections.emptyEnumeration();
        }
        return Collections.enumeration(attributes.keySet());
    }


    /**
     * Creates and returns a deep copy of the specified object. Deep-copying
     * works only for objects of a couple of <i>hard coded</i> types or, if the
     * object implements <code>java.io.Serializable</code>. In all other cases,
     * this method returns <code>null</code>.
     * 
     * @param obj the object to copy
     * 
     * @return a deep copied clone of the specified object, or <code>null</code>
     *         if deep-copying was not possible
     */
    private Object copyObject(Object obj) {

        // first, try some commonly used object types
        if (obj instanceof String) {
            return new String((String) obj);

        } else if (obj instanceof Integer) {
            return Integer.valueOf((int) obj);

        } else if (obj instanceof Long) {
            return Long.valueOf((long) obj);

        } else if (obj instanceof Boolean) {
            return Boolean.valueOf((boolean) obj);

        } else if (obj instanceof Double) {
            return Double.valueOf((double) obj);

        } else if (obj instanceof Float) {
            return Float.valueOf((float) obj);

        } else if (obj instanceof Character) {
            return Character.valueOf((char) obj);

        } else if (obj instanceof Byte) {
            return Byte.valueOf((byte) obj); 

        } else if (obj instanceof Short) {
            return Short.valueOf((short) obj);

        } else if (obj instanceof BigDecimal) {
            return new BigDecimal(((BigDecimal) obj).toString());

        } else if (obj instanceof BigInteger) {
            return new BigInteger(((BigInteger) obj).toString());

        }

        // Date and JDBC date and time types
        else if (obj instanceof java.sql.Date) {
            return ((java.sql.Date) obj).clone();

        } else if (obj instanceof java.sql.Time) {
            return ((java.sql.Time) obj).clone();

        } else if (obj instanceof java.sql.Timestamp) {
            return ((java.sql.Timestamp) obj).clone();

        } else if (obj instanceof Date) {
            return ((Date) obj).clone();

        }

        // these types may come up as well
        else if (obj instanceof URI) {
            try {
                return new URI(((URI) obj).toString());
            } catch (URISyntaxException e) {
                // not expected
            }
        } else if (obj instanceof URL) {
            try {
                return new URI(((URL) obj).toString());
            } catch (URISyntaxException e) {
                // not expected
            }
        } else if (obj instanceof UUID) {
            return new UUID(((UUID) obj).getMostSignificantBits(),
                    ((UUID) obj).getLeastSignificantBits());

        }

        // return a deep copy created by serialization/deserialization (if the
        // specified object implements java.io.Serializable), null otherwise
        return copySerializableObject(obj);
    }


    /**
     * Creates and returns a deep copy of the specified object. This method
     * tries to deep-copy the object by serializing and deserializing it to and
     * from a memory buffer, respectively.
     * <p>
     * This method returns <code>null</code>, if
     * <ul>
     * <li>the specified object does not implement
     * <code>java.io.Serializable</code></li>
     * <li>an error occurred while the object was serialized to memory</li>
     * <li>an error occurred while the object was deserialized from memory</li>
     * </ul>
     * 
     * @param obj the object to copy
     * 
     * @return a deep copied clone of the specified object or <code>null</code>,
     *         if the specified object does not implement
     *         <code>java.io.Serializable</code> or an error occurred while the
     *         object was serialized/deserialized
     */
    private Object copySerializableObject(Object obj) {
        if (!(obj instanceof Serializable)) {
            return null;
        }
        try {
            ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
            ObjectOutputStream out = new ObjectOutputStream(outBuf);
            out.writeObject(obj);
            ByteArrayInputStream inBuf = new ByteArrayInputStream(outBuf.toByteArray());
            ObjectInputStream in = new ObjectInputStream(inBuf);
            return in.readObject();
        } catch (Exception e) {
            // no-op
        }
        return null;
    }

    // ----------------------------------------------------------- Serialization

    private Object writeReplace() {
        return new SerializablePrincipal(name, roles, userPrincipal, attributes);
    }

    private static class SerializablePrincipal implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String name;
        private final String[] roles;
        private final Principal principal;
        private final Map<String, Object> attributes;

        public SerializablePrincipal(String name, String[] roles,
                Principal principal, Map<String, Object> attributes) {
            this.name = name;
            this.roles = roles;
            if (principal instanceof Serializable) {
                this.principal = principal;
            } else {
                this.principal = null;
            }
            this.attributes = attributes;
        }

        private Object readResolve() {
            return new GenericPrincipal(name, Arrays.asList(roles), principal, null, null,
                    attributes);
        }
    }
}
