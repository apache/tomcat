package org.apache.catalina.loader;

import java.io.IOException;

import javax.servlet.jsp.JspException;
import javax.servlet.jsp.tagext.TagSupport;

public class EchoTag extends TagSupport {
    private static final long serialVersionUID = 1L;

    private String echo = null;

    public void setEcho(String echo) {
        this.echo = echo;
    }

    public String getEcho() {
        return echo;
    }

    @Override
    public int doStartTag() throws JspException {
        try {
            pageContext.getOut().print("<p>" + echo + "</p>");
        } catch (IOException e) {
            throw new JspException(e);
        }
        return super.doStartTag();
    }
}
