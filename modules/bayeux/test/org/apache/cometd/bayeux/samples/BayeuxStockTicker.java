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

import java.text.DecimalFormat;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.cometd.bayeux.Client;
import org.apache.cometd.bayeux.Listener;
import org.apache.cometd.bayeux.Message;
import org.apache.cometd.bayeux.Channel;

public class BayeuxStockTicker implements ServletContextListener,
        ServletContextAttributeListener, Listener {

    static AtomicInteger counter = new AtomicInteger(0);
    protected int id;
    protected Bayeux b;
    protected Client c;
    protected boolean alive = true;
    protected boolean initialized = false;
    protected TickerThread tt = new TickerThread();

    public BayeuxStockTicker() {
        id = counter.incrementAndGet();
        System.out.println("new listener created with id:" + id);
    }

    public void contextDestroyed(ServletContextEvent servletContextEvent) {
        alive = false;
        tt.run = false;
        tt.interrupt();
    }

    public void contextInitialized(ServletContextEvent servletContextEvent) {
    }

    public void attributeAdded(ServletContextAttributeEvent scae) {
        if (scae.getName().equals(Bayeux.DOJOX_COMETD_BAYEUX)) {
            if (initialized) return;
            initialized = true;
            System.out.println("Starting stock ticker server client!");
            b = (Bayeux) scae.getValue();
            c = b.newClient("stock-ticker-", this);
            tt.start();
        }
    }

    public void attributeRemoved(ServletContextAttributeEvent scae) {
        if (scae.getName().equals(Bayeux.DOJOX_COMETD_BAYEUX)) {
            initialized = false;
            b = (Bayeux) scae.getValue();
            List<Channel> chs = b.getChannels();
            for (Channel ch : chs) {
                ch.unsubscribe(c);
            }
        }
    }

    public void attributeReplaced(
            ServletContextAttributeEvent servletContextAttributeEvent) {
    }

    public void removed(boolean timeout) {
        System.out.println("Client removed.");
    }

    public void deliver(Message[] msgs) {
        for (int i = 0; msgs != null && i < msgs.length; i++) {
            Message msg = msgs[i];
            System.out.println("[stock ticker server client ]received message:" + msg);
        }
    }

    public class TickerThread extends Thread {
        public boolean run = true;

        public TickerThread() {
            setName("Ticker Thread");
        }

        public void run() {
            try {

                Stock[] stocks = new Stock[] {
                        new Stock("GOOG", 435.43),
                        new Stock("YHOO", 27.88),
                        new Stock("ASF", 1015.55), };
                for (Stock s : stocks) {
                    Channel ch = b.getChannel("/stock/"+s.getSymbol(), true);
                    ch.subscribe(c);

                }
                Random r = new Random(System.currentTimeMillis());
                while (run) {
                    for (int j = 0; j < 1; j++) {
                        int i = r.nextInt() % 3;
                        if (i < 0)
                            i = i * (-1);
                        Stock stock = stocks[i];
                        double change = r.nextDouble();
                        boolean plus = r.nextBoolean();
                        if (plus) {
                            stock.setValue(stock.getValue() + change);
                        } else {
                            stock.setValue(stock.getValue() - change);
                        }
                        Channel ch = b.getChannel("/stock/"+stock.getSymbol(), true);
                        Message m = b.newMessage(c);
                        m.put("stock", stock.toString());
                        m.put("symbol", stock.getSymbol());
                        m.put("price", stock.getValueAsString());
                        m.put("change", stock.getLastChangeAsString());
                        ch.publish(m);
                        System.out.println("Bayeux Stock: "+stock.getSymbol()+" Price: "+stock.getValueAsString()+" Change: "+stock.getLastChangeAsString());
                    }
                    Thread.sleep(850);
                }
            } catch (InterruptedException ix) {

            } catch (Exception x) {
                x.printStackTrace();
            }
        }
    }

    public static class Stock {
        protected static DecimalFormat df = new DecimalFormat("0.00");
        protected String symbol = "";
        protected double value = 0.0d;
        protected double lastchange = 0.0d;
        protected int cnt = 0;

        public Stock(String symbol, double initvalue) {
            this.symbol = symbol;
            this.value = initvalue;
        }

        public void setCnt(int c) {
            this.cnt = c;
        }

        public int getCnt() {
            return cnt;
        }

        public String getSymbol() {
            return symbol;
        }

        public double getValue() {
            return value;
        }

        public void setValue(double value) {
            double old = this.value;
            this.value = value;
            this.lastchange = value - old;
        }

        public String getValueAsString() {
            return df.format(value);
        }

        public double getLastChange() {
            return this.lastchange;
        }

        public void setLastChange(double lastchange) {
            this.lastchange = lastchange;
        }

        public String getLastChangeAsString() {
            return df.format(lastchange);
        }

        public int hashCode() {
            return symbol.hashCode();
        }

        public boolean equals(Object other) {
            if (other instanceof Stock) {
                return this.symbol.equals(((Stock) other).symbol);
            } else {
                return false;
            }
        }

        public String toString(){
            StringBuilder buf = new StringBuilder("STOCK#");
            buf.append(getSymbol());
            buf.append("#");
            buf.append(getValueAsString());
            buf.append("#");
            buf.append(getLastChangeAsString());
            buf.append("#");
            buf.append(String.valueOf(getCnt()));
            return buf.toString();

        }

        public Object clone() {
            Stock s = new Stock(this.getSymbol(), this.getValue());
            s.setLastChange(this.getLastChange());
            s.setCnt(this.cnt);
            return s;
        }
    }

}