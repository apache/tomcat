package org.apache.catalina.tribes.test.channel;

import junit.framework.*;
import org.apache.catalina.tribes.group.*;
import org.apache.catalina.tribes.ChannelInterceptor;
import org.apache.catalina.tribes.ChannelException;

/**
 * <p>Title: </p> 
 * 
 * <p>Description: </p> 
 * 
 * <p>Copyright: Copyright (c) 2005</p> 
 * 
 * <p>Company: </p>
 * 
 * @author not attributable
 * @version 1.0
 */
public class TestChannelOptionFlag extends TestCase {
    GroupChannel channel = null;
    protected void setUp() throws Exception {
        super.setUp();
        channel = new GroupChannel();
    }

    protected void tearDown() throws Exception {
        super.tearDown();
        if ( channel != null ) try {channel.stop(channel.DEFAULT);}catch ( Exception ignore) {}
        channel = null;
    }
    
    
    public void testOptionConflict() throws Exception {
        boolean error = false;
        channel.setOptionCheck(true);
        ChannelInterceptor i = new TestInterceptor();
        i.setOptionFlag(128);
        channel.addInterceptor(i);
        i = new TestInterceptor();
        i.setOptionFlag(128);
        channel.addInterceptor(i);
        try {
            channel.start(channel.DEFAULT);
        }catch ( ChannelException x ) {
            if ( x.getMessage().indexOf("option flag conflict") >= 0 ) error = true;
        }
        assertEquals(true,error);
    }

    public void testOptionNoConflict() throws Exception {
        boolean error = false;
        channel.setOptionCheck(true);
        ChannelInterceptor i = new TestInterceptor();
        i.setOptionFlag(128);
        channel.addInterceptor(i);
        i = new TestInterceptor();
        i.setOptionFlag(64);
        channel.addInterceptor(i);
        i = new TestInterceptor();
        i.setOptionFlag(256);
        channel.addInterceptor(i);
        try {
            channel.start(channel.DEFAULT);
        }catch ( ChannelException x ) {
            if ( x.getMessage().indexOf("option flag conflict") >= 0 ) error = true;
        }
        assertEquals(false,error);
    }
    
    public static class TestInterceptor extends ChannelInterceptorBase {
        
    }


}
