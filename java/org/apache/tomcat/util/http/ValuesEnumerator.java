package org.apache.tomcat.util.http;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Enumeration;

import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.res.StringManager;


/** Enumerate the values for a (possibly ) multiple
    value element.
*/
class ValuesEnumerator implements Enumeration<String> {
    private int pos;
    private final int size;
    private MessageBytes next;
    private final MimeHeaders headers;
    private final String name;

    ValuesEnumerator(MimeHeaders headers, String name) {
        this.name=name;
        this.headers=headers;
        pos=0;
        size = headers.size();
        findNext();
    }

    private void findNext() {
        next=null;
        for(; pos< size; pos++ ) {
            MessageBytes n1=headers.getName( pos );
            if( n1.equalsIgnoreCase( name )) {
                next=headers.getValue( pos );
                break;
            }
        }
        pos++;
    }

    @Override
    public boolean hasMoreElements() {
        return next!=null;
    }

    @Override
    public String nextElement() {
        MessageBytes current=next;
        findNext();
        return current.toString();
    }
}
