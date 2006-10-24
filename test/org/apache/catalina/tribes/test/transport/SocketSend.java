package org.apache.catalina.tribes.test.transport;

import java.io.OutputStream;
import java.net.Socket;
import java.text.DecimalFormat;
import org.apache.catalina.tribes.membership.MemberImpl;
import org.apache.catalina.tribes.io.XByteBuffer;
import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.io.ChannelData;
import org.apache.catalina.tribes.Channel;
import java.math.BigDecimal;

public class SocketSend {

    public static void main(String[] args) throws Exception {
        
        
        Member mbr = new MemberImpl("localhost", 9999, 0);
        ChannelData data = new ChannelData();
        data.setOptions(Channel.SEND_OPTIONS_BYTE_MESSAGE);
        data.setAddress(mbr);
        byte[] buf = new byte[8192 * 4];
        data.setMessage(new XByteBuffer(buf,false));
        buf = XByteBuffer.createDataPackage(data);
        int len = buf.length;
        System.out.println("Message size:"+len+" bytes");
        BigDecimal total = new BigDecimal((double)0);
        BigDecimal bytes = new BigDecimal((double)len);
        Socket socket = new Socket("localhost",9999);
        System.out.println("Writing to 9999");
        OutputStream out = socket.getOutputStream();
        long start = 0;
        double mb = 0;
        boolean first = true;
        int count = 0;
        DecimalFormat df = new DecimalFormat("##.00");
        while ( count<100000 ) {
            if ( first ) { first = false; start = System.currentTimeMillis();}
            out.write(buf,0,buf.length);
            mb += ( (double) buf.length) / 1024 / 1024;
            total = total.add(bytes);
            if ( ((++count) % 10000) == 0 ) {
                long time = System.currentTimeMillis();
                double seconds = ((double)(time-start))/1000;
                System.out.println("Throughput "+df.format(mb/seconds)+" MB/seconds messages "+count+", total "+mb+" MB, total "+total+" bytes.");
            }
        }
        out.flush(); 
        System.out.println("Complete, sleeping 5 seconds");
        Thread.sleep(5000);

    }
}
