package org.apache.catalina.tribes.test;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.util.Iterator;
import java.nio.channels.Selector;
import org.apache.catalina.tribes.transport.nio.NioSender;
import org.apache.catalina.tribes.membership.MemberImpl;
import org.apache.catalina.tribes.io.ChannelData;
import org.apache.catalina.tribes.io.XByteBuffer;
import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.Channel;

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
public class TestNioSender {
    private Selector selector = null;
    private int counter = 0;
    MemberImpl mbr;
    private static int testOptions = Channel.SEND_OPTIONS_DEFAULT;
    public TestNioSender()  {
        
    }
    
    public synchronized int inc() {
        return ++counter;
    }
    
    public synchronized ChannelData getMessage(Member mbr) {
        String msg = new String("Thread-"+Thread.currentThread().getName()+" Message:"+inc());
        ChannelData data = new ChannelData(true);
        data.setMessage(new XByteBuffer(msg.getBytes(),false));
        data.setAddress(mbr);
        
        return data;
    }

    public void init() throws Exception {
        selector = Selector.open();
        mbr = new MemberImpl("localhost",4444,0);
        NioSender sender = new NioSender();
        sender.setDestination(mbr);
        sender.setDirectBuffer(true);
        sender.setSelector(selector);
        sender.setMessage(XByteBuffer.createDataPackage(getMessage(mbr)));
        sender.connect();
    }

    public void run() {
        while (true) {

            int selectedKeys = 0;
            try {
                selectedKeys = selector.select(100);
                //               if ( selectedKeys == 0 ) {
                //                   System.out.println("No registered interests. Sleeping for a second.");
                //                   Thread.sleep(100);
            } catch (Exception e) {
                e.printStackTrace();
                continue;
            }

            if (selectedKeys == 0) {
                continue;
            }

            Iterator it = selector.selectedKeys().iterator();
            while (it.hasNext()) {
                SelectionKey sk = (SelectionKey) it.next();
                it.remove();
                try {
                    int readyOps = sk.readyOps();
                    sk.interestOps(sk.interestOps() & ~readyOps);
                    NioSender sender = (NioSender) sk.attachment();
                    if ( sender.process(sk, (testOptions&Channel.SEND_OPTIONS_USE_ACK)==Channel.SEND_OPTIONS_USE_ACK) ) {
                        System.out.println("Message completed for handler:"+sender);
                        Thread.currentThread().sleep(2000);
                        sender.reset();
                        sender.setMessage(XByteBuffer.createDataPackage(getMessage(mbr)));
                    }
                    

                } catch (Throwable t) {
                    t.printStackTrace();
                    return;
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        TestNioSender sender = new TestNioSender();
        sender.init();
        sender.run();
    }
}
