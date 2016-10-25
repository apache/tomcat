/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
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
package org.apache.coyote;

import java.io.IOException;

/**
 * This exception is thrown to signal to the Tomcat internals that an error has
 * occurred that requires the connection to be closed. For multiplexed protocols
 * such as HTTP/2, this means the channel must be closed but the connection can
 * continue. For non-multiplexed protocols, the connection must be closed. It
 * corresponds to {@link ErrorState#CLOSE_NOW}.
 */
public class CloseNowException extends IOException {

    private static final long serialVersionUID = 1L;


    public CloseNowException() {
        super();
    }


    public CloseNowException(String message, Throwable cause) {
        super(message, cause);
    }


    public CloseNowException(String message) {
        super(message);
    }


    public CloseNowException(Throwable cause) {
        super(cause);
    }
}
