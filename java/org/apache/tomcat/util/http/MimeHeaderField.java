package org.apache.tomcat.util.http;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Enumeration;

import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.res.StringManager;

class MimeHeaderField {

    private final MessageBytes nameB = MessageBytes.newInstance();
    private final MessageBytes valueB = MessageBytes.newInstance();

    /**
     * Creates a new, uninitialized header field.
     */
    public MimeHeaderField() {
        // NO-OP
    }

    public void recycle() {
        nameB.recycle();
        valueB.recycle();
    }

    public MessageBytes getName() {
        return nameB;
    }

    public MessageBytes getValue() {
        return valueB;
    }

    @Override
    public String toString() {
        return nameB + ": " + valueB;
    }
}
