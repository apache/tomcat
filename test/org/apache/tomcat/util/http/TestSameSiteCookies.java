package org.apache.tomcat.util.http;

import org.junit.Test;

import org.junit.Assert;

public class TestSameSiteCookies {

    @Test
    public void testNone() {
        SameSiteCookies attribute = SameSiteCookies.NONE;

        Assert.assertEquals("None", attribute.toString());
        Assert.assertEquals(SameSiteCookies.NONE, attribute);

        Assert.assertNotEquals(SameSiteCookies.LAX, attribute);
        Assert.assertNotEquals(SameSiteCookies.STRICT, attribute);
    }

    @Test
    public void testLax() {
        SameSiteCookies attribute = SameSiteCookies.LAX;

        Assert.assertEquals("Lax", attribute.toString());
        Assert.assertEquals(SameSiteCookies.LAX, attribute);

        Assert.assertNotEquals(SameSiteCookies.NONE, attribute);
        Assert.assertNotEquals(SameSiteCookies.STRICT, attribute);
    }

    @Test
    public void testStrict() {
        SameSiteCookies attribute = SameSiteCookies.STRICT;

        Assert.assertEquals("Strict", attribute.toString());
        Assert.assertEquals(SameSiteCookies.STRICT, attribute);

        Assert.assertNotEquals(SameSiteCookies.NONE, attribute);
        Assert.assertNotEquals(SameSiteCookies.LAX, attribute);
    }

    @Test
    public void testToValidAttribute() {
        Assert.assertEquals(SameSiteCookies.toAttribute("none"), SameSiteCookies.NONE);
        Assert.assertEquals(SameSiteCookies.toAttribute("None"), SameSiteCookies.NONE);
        Assert.assertEquals(SameSiteCookies.toAttribute("NONE"), SameSiteCookies.NONE);

        Assert.assertEquals(SameSiteCookies.toAttribute("lax"), SameSiteCookies.LAX);
        Assert.assertEquals(SameSiteCookies.toAttribute("Lax"), SameSiteCookies.LAX);
        Assert.assertEquals(SameSiteCookies.toAttribute("LAX"), SameSiteCookies.LAX);

        Assert.assertEquals(SameSiteCookies.toAttribute("strict"), SameSiteCookies.STRICT);
        Assert.assertEquals(SameSiteCookies.toAttribute("Strict"), SameSiteCookies.STRICT);
        Assert.assertEquals(SameSiteCookies.toAttribute("STRICT"), SameSiteCookies.STRICT);
    }

    @Test(expected = IllegalStateException.class)
    public void testToInvalidAttribute01() {
        SameSiteCookies.toAttribute("");
    }

    @Test(expected = IllegalStateException.class)
    public void testToInvalidAttribute02() {
        SameSiteCookies.toAttribute(" ");
    }

    @Test(expected = IllegalStateException.class)
    public void testToInvalidAttribute03() {
        SameSiteCookies.toAttribute("Strict1");
    }

    @Test(expected = IllegalStateException.class)
    public void testToInvalidAttribute04() {
        SameSiteCookies.toAttribute("foo");
    }

    @Test(expected = IllegalStateException.class)
    public void testToInvalidAttribute05() {
        SameSiteCookies.toAttribute("Lax ");
    }
}