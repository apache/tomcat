package org.apache.catalina.startup;

import org.apache.catalina.connector.Connector;
import org.apache.catalina.core.StandardServer;
import org.apache.tomcat.util.digester.Rule;
import org.xml.sax.Attributes;

public class AddPortOffsetRule extends Rule {
  // Set portOffset on all the connectors based on portOffset in the Standard Server
  @Override
  public void begin(String namespace, String name, Attributes attributes) throws Exception {

    Connector conn = (Connector) digester.peek();
    StandardServer server = (StandardServer) digester.peek(2);

    int portOffset = server.getPortOffset();
    conn.setPortOffset(portOffset);
  }
}
