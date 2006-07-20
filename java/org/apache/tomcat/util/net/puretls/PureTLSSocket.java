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

import java.io.IOException;

/*
 * PureTLSSocket.java
 *
 * Wraps COM.claymoresystems.ptls.SSLSocket
 *
 * This class translates PureTLS's interfaces into those
 * expected by Tomcat
 *
 * @author Eric Rescorla
 *
 */

public class PureTLSSocket extends COM.claymoresystems.ptls.SSLSocket
{
    // The only constructor we need here is the no-arg
    // constructor since this class is only used with
    // implAccept
    public PureTLSSocket() throws IOException {
	super();
    }
}
 
