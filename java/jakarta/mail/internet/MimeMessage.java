/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package jakarta.mail.internet;

import jakarta.mail.Session;

/**
 * Represents a MIME message in the Jakarta Mail API.
 * Provides methods for setting message headers such as sender and subject.
 * NOTE: This is a stub API, Apache Tomcat does not provide any implementation for this API.
 */
public class MimeMessage implements MimePart {
    /**
     * Constructs a MimeMessage with the given Session.
     *
     * @param session the mail session used to create this message
     */
    public MimeMessage(Session session) {
        // Dummy implementation
    }

    /**
     * Sets the InternetAddress of the message sender.
     *
     * @param from the InternetAddress of the sender
     */
    public void setFrom(InternetAddress from) {
        // Dummy implementation
    }

    /**
     * Sets the subject of the message.
     *
     * @param subject the subject string
     */
    public void setSubject(String subject) {
        // Dummy implementation
    }
}
