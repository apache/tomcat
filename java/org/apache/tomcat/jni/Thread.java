/*
 *  Copyright 2000-2005 The Apache Software Foundation
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

/** Thread
 *
 * @author Mladen Turk
 * @version $Revision: 300969 $, $Date: 2005-07-12 16:56:11 +0200 (uto, 12 srp 2005) $
 */

public class Thread {
    
    /**
     * Get the current thread ID handle.
     */
    public static native long current();    

}