/*
 *  Copyright 1999-2004 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.tomcat.jni;

public class Sockaddr {

   /** The pool to use... */
    public long pool;
    /** The hostname */
    public String hostname;
    /** Either a string of the port number or the service name for the port */
    public String servname;
    /** The numeric port */
    public int port;
    /** The family */
    public int family;
    /** How big is the sockaddr we're using? */
    public int salen;
    /** How big is the ip address structure we're using? */
    public int ipaddr_len;
    /** How big should the address buffer be?  16 for v4 or 46 for v6
     *  used in inet_ntop... */
    public int addr_str_len;
    /** This points to the IP address structure within the appropriate
     *  sockaddr structure.  */
    public long ipaddr_ptr;
    /** If multiple addresses were found by apr_sockaddr_info_get(), this
     *  points to a representation of the next address. */
    public long next;

}
