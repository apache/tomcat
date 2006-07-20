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

package org.apache.tomcat.util.net.puretls;

import java.net.Socket;

import org.apache.tomcat.util.net.SSLImplementation;
import org.apache.tomcat.util.net.SSLSupport;
import org.apache.tomcat.util.net.ServerSocketFactory;

import COM.claymoresystems.ptls.SSLSocket;

/* PureTLSImplementation:

   Concrete implementation class for PureTLS

   @author EKR
*/

public class PureTLSImplementation extends SSLImplementation
{
    public PureTLSImplementation() throws ClassNotFoundException {
	// Check to see if PureTLS is floating around somewhere
	Class.forName("COM.claymoresystems.ptls.SSLContext");
    }

    public String getImplementationName(){
      return "PureTLS";
    }
      
    public ServerSocketFactory getServerSocketFactory()
    {
	return new PureTLSSocketFactory();
    } 

    public SSLSupport getSSLSupport(Socket s)
    {
	return new PureTLSSupport((SSLSocket)s);
    }



}
