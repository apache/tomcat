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
package org.apache.naming.factory;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Properties;

import javax.naming.Context;
import javax.naming.Name;
import javax.naming.RefAddr;
import javax.naming.Reference;
import javax.naming.spi.ObjectFactory;

import jakarta.mail.Authenticator;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;

/**
 * <p>Factory class that creates a JNDI named JavaMail Session factory,
 * which can be used for managing inbound and outbound electronic mail
 * messages via JavaMail APIs.  All messaging environment properties
 * described in the JavaMail Specification may be passed to the Session
 * factory; however the following properties are the most commonly used:</p>
 * <ul>
 * <li><strong>mail.smtp.host</strong> - Hostname for outbound transport
 *     connections.  Defaults to <code>localhost</code> if not specified.</li>
 * </ul>
 *
 * <p>This factory can be configured in a
 * <code>&lt;Context&gt;</code> element in your <code>conf/server.xml</code>
 * configuration file.  An example of factory configuration is:</p>
 * <pre>
 * &lt;Resource name="mail/smtp"
 *           auth="CONTAINER"
 *           type="jakarta.mail.Session"
 *           mail.smtp.host="mail.mycompany.com"
 *           /&gt;
 * </pre>
 *
 * @author Craig R. McClanahan
 */
public class MailSessionFactory implements ObjectFactory {


    /**
     * The Java type for which this factory knows how to create objects.
     */
    protected static final String factoryType = "jakarta.mail.Session";


    @Override
    public Object getObjectInstance(Object refObj, Name name, Context context,
            Hashtable<?,?> env) throws Exception {

        // Return null if we cannot create an object of the requested type
        final Reference ref = (Reference) refObj;
        if (!ref.getClassName().equals(factoryType)) {
            return null;
        }

        // Create the JavaMail properties we will use
        Properties props = new Properties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.host", "localhost");

        String password = null;

        Enumeration<RefAddr> attrs = ref.getAll();
        while (attrs.hasMoreElements()) {
            RefAddr attr = attrs.nextElement();
            if ("factory".equals(attr.getType())) {
                continue;
            }

            if ("password".equals(attr.getType())) {
                password = (String) attr.getContent();
                continue;
            }

            props.put(attr.getType(), attr.getContent());
        }

        Authenticator auth = null;
        if (password != null) {
            String user = props.getProperty("mail.smtp.user");
            if(user == null) {
                user = props.getProperty("mail.user");
            }

            if(user != null) {
                final PasswordAuthentication pa = new PasswordAuthentication(user, password);
                auth = new Authenticator() {
                        @Override
                        protected PasswordAuthentication getPasswordAuthentication() {
                            return pa;
                        }
                    };
            }
        }

        // Create and return the new Session object
        Session session = Session.getInstance(props, auth);
        return session;
    }
}
