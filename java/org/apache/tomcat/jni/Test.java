
package org.apache.tomcat.jni;
import java.io.*;
import java.util.*;


public class Test {

    public Test() {
        try {
            Library.initialize(null);
            System.out.println("Test T " + Status.APR_STATUS_IS_ENOTIMPL(Status.APR_ENOTIMPL));
            System.out.println("Test F " + Status.APR_STATUS_IS_ENOTIMPL(Status.APR_EINIT));
            long pool = Pool.create(0);
            try {
                long sa = Address.info("127.0.0.1",Socket.APR_UNSPEC,
                                        80, 0, pool);
                Sockaddr addr = new Sockaddr();
                Address.fill(addr, sa);
        long [] fp = new long[1];
        System.out.println("Fork " + Proc.fork(fp, pool));
                System.out.println("hostname " + addr.hostname);
                System.out.println("servname " + addr.servname);
                System.out.println("family   " + addr.family);
                System.out.println("nameinfo " + Address.getnameinfo(sa, 0));
                if (addr.next > 0) {
                    Address.fill(addr, addr.next);
                    System.out.println("N hostname " + addr.hostname);
                    System.out.println("N servname " + addr.servname);
                    System.out.println("N family   " + addr.family);
                }

                Pool.destroy(pool);
            }
            catch(Error ex) {
                System.out.println("Returned " + ex.getError());
                ex.printStackTrace();
            }

            Library.terminate(true);
            System.out.println("Finished.");
        }
        catch(Exception e) {
            e.printStackTrace();
        }
    }
    public static void main(String[] args)
    {
        new Test();
    }
}

