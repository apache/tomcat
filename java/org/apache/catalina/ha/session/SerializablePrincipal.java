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


package org.apache.catalina.ha.session;


import java.util.Arrays;
import java.util.List;
import org.apache.catalina.Realm;


/**
 * Generic implementation of <strong>java.security.Principal</strong> that
 * is available for use by <code>Realm</code> implementations.
 * The GenericPrincipal does NOT implement serializable and I didn't want to change that implementation
 * hence I implemented this one instead.
 * @author Filip Hanik
 * @version $Revision$ $Date$
 */
import org.apache.catalina.realm.GenericPrincipal;
import java.io.ObjectInput;
import java.io.ObjectOutput;
public class SerializablePrincipal  implements java.io.Serializable {


    // ----------------------------------------------------------- Constructors

    public SerializablePrincipal()
    {
        super();
    }
    /**
     * Construct a new Principal, associated with the specified Realm, for the
     * specified username and password.
     *
     * @param realm The Realm that owns this Principal
     * @param name The username of the user represented by this Principal
     * @param password Credentials used to authenticate this user
     */
    public SerializablePrincipal(Realm realm, String name, String password) {

        this(realm, name, password, null);

    }


    /**
     * Construct a new Principal, associated with the specified Realm, for the
     * specified username and password, with the specified role names
     * (as Strings).
     *
     * @param realm The Realm that owns this principal
     * @param name The username of the user represented by this Principal
     * @param password Credentials used to authenticate this user
     * @param roles List of roles (must be Strings) possessed by this user
     */
    public SerializablePrincipal(Realm realm, String name, String password,
                            List roles) {

        super();
        this.realm = realm;
        this.name = name;
        this.password = password;
        if (roles != null) {
            this.roles = new String[roles.size()];
            this.roles = (String[]) roles.toArray(this.roles);
            if (this.roles.length > 0)
                Arrays.sort(this.roles);
        }

    }


    // ------------------------------------------------------------- Properties


    /**
     * The username of the user represented by this Principal.
     */
    protected String name = null;

    public String getName() {
        return (this.name);
    }


    /**
     * The authentication credentials for the user represented by
     * this Principal.
     */
    protected String password = null;

    public String getPassword() {
        return (this.password);
    }


    /**
     * The Realm with which this Principal is associated.
     */
    protected transient Realm realm = null;

    public Realm getRealm() {
        return (this.realm);
    }

    public void setRealm(Realm realm) {
        this.realm = realm;
    }




    /**
     * The set of roles associated with this user.
     */
    protected String roles[] = new String[0];

    public String[] getRoles() {
        return (this.roles);
    }


    // --------------------------------------------------------- Public Methods




    /**
     * Return a String representation of this object, which exposes only
     * information that should be public.
     */
    public String toString() {

        StringBuffer sb = new StringBuffer("SerializablePrincipal[");
        sb.append(this.name);
        sb.append("]");
        return (sb.toString());

    }

    public static SerializablePrincipal createPrincipal(GenericPrincipal principal)
    {
        if ( principal==null) return null;
        return new SerializablePrincipal(principal.getRealm(),
                                         principal.getName(),
                                         principal.getPassword(),
                                         principal.getRoles()!=null?Arrays.asList(principal.getRoles()):null);
    }

    public GenericPrincipal getPrincipal( Realm realm )
    {
        return new GenericPrincipal(realm,name,password,getRoles()!=null?Arrays.asList(getRoles()):null);
    }
    
    public static GenericPrincipal readPrincipal(ObjectInput in, Realm realm) throws java.io.IOException{
        String name = in.readUTF();
        boolean hasPwd = in.readBoolean();
        String pwd = null;
        if ( hasPwd ) pwd = in.readUTF();
        int size = in.readInt();
        String[] roles = new String[size];
        for ( int i=0; i<size; i++ ) roles[i] = in.readUTF();
        return new GenericPrincipal(realm,name,pwd,Arrays.asList(roles));
    }
    
    public static void writePrincipal(GenericPrincipal p, ObjectOutput out) throws java.io.IOException {
        out.writeUTF(p.getName());
        out.writeBoolean(p.getPassword()!=null);
        if ( p.getPassword()!= null ) out.writeUTF(p.getPassword());
        String[] roles = p.getRoles();
        if ( roles == null ) roles = new String[0];
        out.writeInt(roles.length);
        for ( int i=0; i<roles.length; i++ ) out.writeUTF(roles[i]);
    }


}
