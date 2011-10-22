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

package org.apache.cometd.bayeux.samples;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletContextAttributeListener;
import javax.servlet.ServletContextAttributeEvent;
import org.apache.cometd.bayeux.Bayeux;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.cometd.bayeux.Client;
import org.apache.cometd.bayeux.Listener;
import org.apache.cometd.bayeux.Message;
import org.apache.cometd.bayeux.Channel;

public class EchoChatClient implements ServletContextListener, ServletContextAttributeListener, Listener {

    static AtomicInteger counter = new AtomicInteger(0);
    protected int id;
    protected Bayeux b;
    protected Client c;
    protected boolean alive = true;
    protected TimestampThread tt = new TimestampThread();

    public EchoChatClient() {
        id = counter.incrementAndGet();
        System.out.println("new listener created with id:"+id);
    }

    public void contextDestroyed(ServletContextEvent servletContextEvent) {
        alive = false;
        tt.interrupt();
    }

    public void contextInitialized(ServletContextEvent servletContextEvent) {
    }

    public void attributeAdded(ServletContextAttributeEvent scae) {
        if (scae.getName().equals(Bayeux.DOJOX_COMETD_BAYEUX)) {
            System.out.println("Starting echo chat client!");
            b = (Bayeux)scae.getValue();
            c = b.newClient("echochat-",this);
            Channel ch = b.getChannel("/chat/demo",true);
            ch.subscribe(c);
            tt.start();
        }
    }

    public void attributeRemoved(ServletContextAttributeEvent servletContextAttributeEvent) {
    }

    public void attributeReplaced(ServletContextAttributeEvent servletContextAttributeEvent) {
    }

    public void removed(boolean timeout) {
        System.out.println("Client removed.");
    }

    public void deliver(Message[] msgs) {
        for (int i=0; msgs!=null && i<msgs.length; i++) {
            Message msg = msgs[i];
            System.out.println("[echochatclient ]received message:" + msg);
            Message m = b.newMessage(c);
            m.putAll(msg);
            //echo the same message
            m.put("user", "echochatserver");
            if (m.containsKey("msg")) {
                //simple chat demo
                String chat = (String) m.get("msg");
                m.put("msg", "echochatserver|I received your message-" + chat.substring(chat.indexOf("|") + 1));
            }
            System.out.println("[echochatclient ]sending message:" + m);
            msg.getChannel().publish(m);
        }
    }

    public class TimestampThread extends Thread {
        public TimestampThread() {
            setDaemon(true);
        }

        public void run() {
            while (alive) {
                try {
                    sleep(5000);
                    Channel ch = b.getChannel("/chat/demo",false);
                    if (ch.getSubscribers().size()<=1) {
                        continue;
                    }
                    Message m = b.newMessage(c);
                    m.put("user","echochatserver");
                    m.put("chat","Time is:"+new java.sql.Date(System.currentTimeMillis()).toLocaleString());
                    m.put("join",false);
                    ch.publish(m);
                }catch (InterruptedException ignore) {
                    Thread.currentThread().interrupted();
                }catch (Exception x) {
                    x.printStackTrace();
                }
            }
        }
    }
}
