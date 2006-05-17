package org.apache.catalina;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public interface CometProcessor {

    public void begin(HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException;
    public void end(HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException;

    public void error(HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException;
    public void read(HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException;

}
