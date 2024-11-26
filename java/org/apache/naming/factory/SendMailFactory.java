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

import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimePartDataSource;

import org.apache.tomcat.util.ExceptionUtils;

/**
 * Factory class that creates a JNDI named javamail MimePartDataSource
 * object which can be used for sending email using SMTP.
 * <p>
 * Can be configured in the Context scope
 * of your server.xml configuration file.
 * <p>
 * Example:
 * <pre>
 * &lt;Resource name="mail/send"
 *           auth="CONTAINER"
 *           type="jakarta.mail.internet.MimePartDataSource"
 *           factory="org.apache.naming.factory.SendMailFactory"
 *           mail.smtp.host="your.smtp.host"
 *           mail.smtp.user="someuser"
 *           mail.from="someuser@some.host"
 *           mail.smtp.sendpartial="true"
 *           mail.smtp.dsn.notify="FAILURE"
 *           mail.smtp.dsn.ret="FULL"
 *           /&gt;
 * </pre>
 *
 * @author Glenn Nielsen Rich Catlett
 */

public class SendMailFactory implements ObjectFactory
{
    // The class name for the javamail MimeMessageDataSource
    protected static final String DataSourceClassName =
        "jakarta.mail.internet.MimePartDataSource";

    @Override
    public Object getObjectInstance(Object refObj, Name name, Context ctx,
            Hashtable<?,?> env) throws Exception {
        final Reference ref = (Reference)refObj;

        if (ref.getClassName().equals(DataSourceClassName)) {
            // set up the smtp session that will send the message
            Properties props = new Properties();
            // enumeration of all refaddr
            Enumeration<RefAddr> list = ref.getAll();
            // current refaddr to be set
            RefAddr refaddr;
            // set transport to smtp
            props.put("mail.transport.protocol", "smtp");

            while (list.hasMoreElements()) {
                refaddr = list.nextElement();

                // set property
                props.put(refaddr.getType(), refaddr.getContent());
            }
            MimeMessage message = new MimeMessage(
                Session.getInstance(props));
            try {
                RefAddr fromAddr = ref.get("mail.from");
                String from = null;
                if (fromAddr != null) {
                    from = (String) fromAddr.getContent();
                }
                if (from != null) {
                    message.setFrom(new InternetAddress(from));
                }
                message.setSubject("");
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                // Otherwise ignore
            }
            MimePartDataSource mds = new MimePartDataSource(message);
            return mds;
        } else { // We can't create an instance of the DataSource
            return null;
        }
    }
}
