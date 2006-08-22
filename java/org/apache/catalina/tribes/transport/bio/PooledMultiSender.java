package org.apache.catalina.tribes.transport.bio;

import org.apache.catalina.tribes.transport.DataSender;
import org.apache.catalina.tribes.transport.PooledSender;
import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.ChannelException;
import org.apache.catalina.tribes.transport.MultiPointSender;
import org.apache.catalina.tribes.ChannelMessage;

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
public class PooledMultiSender extends PooledSender {
    

    public PooledMultiSender() {
    }
    
    public void sendMessage(Member[] destination, ChannelMessage msg) throws ChannelException {
        MultiPointSender sender = null;
        try {
            sender = (MultiPointSender)getSender();
            if (sender == null) {
                ChannelException cx = new ChannelException("Unable to retrieve a data sender, time out error.");
                for (int i = 0; i < destination.length; i++) cx.addFaultyMember(destination[i], new NullPointerException("Unable to retrieve a sender from the sender pool"));
                throw cx;
            } else {
                sender.sendMessage(destination, msg);
            }
            sender.keepalive();
        }finally {
            if ( sender != null ) returnSender(sender);
        }
    }

    /**
     * getNewDataSender
     *
     * @return DataSender
     * @todo Implement this org.apache.catalina.tribes.transport.PooledSender
     *   method
     */
    public DataSender getNewDataSender() {
        MultipointBioSender sender = new MultipointBioSender();
        sender.transferProperties(this,sender);
        return sender;
    }


    public void memberAdded(Member member) {

    }

    public void memberDisappeared(Member member) {
        //disconnect senders
    } 

}