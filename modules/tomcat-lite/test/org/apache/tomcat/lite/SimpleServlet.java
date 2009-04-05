/*
 */
package org.apache.tomcat.lite;

import java.io.IOException;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class SimpleServlet extends HttpServlet {
  public void doGet(HttpServletRequest req, HttpServletResponse res) 
  throws IOException {
    res.setHeader("Foo", "Bar");
    res.getWriter().write("Hello world");
  }
  public void doPost(HttpServletRequest req, HttpServletResponse res) 
      throws IOException {
    res.setHeader("Foo", "Post");
    res.getWriter().write("Hello post world");
  }
}