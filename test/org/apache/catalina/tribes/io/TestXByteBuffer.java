/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.catalina.tribes.io;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsInstanceOf.instanceOf;

import org.junit.Assert;
import org.junit.Test;

public class TestXByteBuffer {

    @Test
    public void testEmptyArray() throws Exception {
        Object obj = XByteBuffer.deserialize(new byte[0]);
        Assert.assertNull(obj);
    }

    @Test
    public void testSerializationString() throws Exception {
        String test = "This is as test.";
        byte[] msg = XByteBuffer.serialize(test);
        Object obj = XByteBuffer.deserialize(msg);
        assertThat(obj, instanceOf(String.class));
        Assert.assertEquals(test, obj);
    }

    private static byte[] packageOf(int... message) {
        XByteBuffer msg = new XByteBuffer(new byte[0], false);
        for (int b : message) {
            msg.append(new byte[] { (byte) b }, 0, 1);
        }
        return XByteBuffer.createDataPackage(new ChannelData(new byte[] { 1, 2 }, msg, System.currentTimeMillis()));
    }

    private static XByteBuffer validPackageBuffer() {
        return new XByteBuffer(packageOf(3), false);
    }

    private static byte[] packageStub(int size) {
        // A header, a size field and three filler bytes, the minimum that
        // countPackages() inspects before the footer check
        byte[] stub = new byte[14];
        System.arraycopy(packageOf(2), 0, stub, 0, 7);
        XByteBuffer.toBytes(size, stub, 7);
        stub[11] = 'x';
        stub[12] = 'y';
        stub[13] = 'z';
        return stub;
    }

    @Test
    public void testCountPackagesValid() throws Exception {
        XByteBuffer buf = validPackageBuffer();
        Assert.assertEquals(1, buf.countPackages());
        Assert.assertEquals(1, buf.countPackages(true));
        byte[] pkg = packageOf(2, 3);
        buf.append(pkg, 0, pkg.length);
        Assert.assertEquals(2, buf.countPackages());
        Assert.assertEquals(1, buf.countPackages(true));
    }

    // A size field of -18 makes the footer check land on the footer of the
    // preceding package. This used to reset the loop start to its previous
    // value, so countPackages() never terminated.
    @Test(timeout = 30000)
    public void testCountPackagesNegativeSizeAfterValidPackage() throws Exception {
        XByteBuffer buf = validPackageBuffer();
        byte[] stub = packageStub(-18);
        buf.append(stub, 0, stub.length);
        Assert.assertEquals(1, buf.countPackages());
        Assert.assertEquals(1, buf.countPackages(true));
    }

    @Test
    public void testCountPackagesNegativeSize() throws Exception {
        XByteBuffer buf = new XByteBuffer(4096, false);
        byte[] stub = packageStub(-18);
        buf.append(stub, 0, stub.length);
        Assert.assertEquals(0, buf.countPackages());
    }

    @Test
    public void testCountPackagesHugeSize() throws Exception {
        XByteBuffer buf = new XByteBuffer(4096, false);
        byte[] stub = packageStub(Integer.MAX_VALUE);
        buf.append(stub, 0, stub.length);
        Assert.assertEquals(0, buf.countPackages());
    }
}
