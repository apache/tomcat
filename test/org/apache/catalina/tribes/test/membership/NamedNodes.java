package org.apache.catalina.tribes.test.membership;

import org.apache.catalina.tribes.group.GroupChannel;
import org.apache.catalina.tribes.membership.MemberImpl;
import org.apache.catalina.tribes.MembershipListener;
import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.Channel;

public class NamedNodes implements MembershipListener{
    
    public void namedNodes() throws Exception {
        GroupChannel[] channels = new GroupChannel[3];
        for (int i=0; i<channels.length; i++) {
            channels[i] = new GroupChannel();
            //start the receiver, so that we can identify the local member
            channels[i].start(Channel.SND_RX_SEQ);
            MemberImpl member = (MemberImpl)channels[i].getLocalMember(false);
            String name = "Member Nr-"+(i+1);
            member.setPayload(name.getBytes("ASCII"));
            channels[i].addMembershipListener(this);
            channels[i].start(Channel.SND_TX_SEQ|Channel.MBR_RX_SEQ|Channel.MBR_TX_SEQ);
         }
         Thread.sleep(5000);
         for (int i=0; i<channels.length; i++) {
             channels[i].stop(Channel.DEFAULT);
             Thread.sleep(1000);
         }
    }
    
    public static void main(String[] args) throws Exception {
        NamedNodes nm = new NamedNodes();
        nm.namedNodes();
    }
    
    public void memberAdded(Member member) {
        try {
            String name = new String(member.getPayload(), "ASCII");
            System.out.println("Node with name:"+name+" just joined");
        }catch ( Exception x ) {
            x.printStackTrace();
        }
    }

    public void memberDisappeared(Member member) {
        try {
            String name = new String(member.getPayload(), "ASCII");
            System.out.println("Node with name:"+name+" just left");
        }catch ( Exception x ) {
            x.printStackTrace();
        }
        
    }

}