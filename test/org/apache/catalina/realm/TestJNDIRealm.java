package org.apache.catalina.realm;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.core.TesterContext;
import org.apache.naming.NameParserImpl;
import org.apache.tomcat.util.security.MD5Encoder;
import org.easymock.EasyMock;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.*;
import java.lang.reflect.Field;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;

import static org.easymock.EasyMock.*;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertThat;

public class TestJNDIRealm {

    private static final String ALGORITHM = "MD5";

    private static final String USER = "test-user";
    private static final String PASSWORD = "test-password";
    private static final String REALM = "test-realm";

    private static final String NONCE = "test-nonce";
    private static final String HA2 = "test-md5a2";
    public static final String USER_PASSWORD_ATTR = "test-pwd";

    private static MessageDigest md5Helper;

    @BeforeClass
    public static void setupClass() throws Exception {
        md5Helper = MessageDigest.getInstance(ALGORITHM);
    }

    @Test
    public void testAuthenticateWithoutUserPassword() throws Exception {
        // GIVEN
        JNDIRealm realm = buildRealm(PASSWORD);

        // WHEN
        String expectedResponse =
                MD5Encoder.encode(md5Helper.digest((ha1() + ":" + NONCE + ":" + HA2).getBytes()));
        Principal principal =
                realm.authenticate(USER, expectedResponse, NONCE, null, null, null, REALM, HA2);

        // THEN
        assertThat(principal, is(nullValue()));
    }

    @Test
    public void testAuthenticateWithUserPassword() throws Exception {
        // GIVEN
        JNDIRealm realm = buildRealm(PASSWORD);
        realm.setUserPassword(USER_PASSWORD_ATTR);

        // WHEN
        String expectedResponse =
                MD5Encoder.encode(md5Helper.digest((ha1() + ":" + NONCE + ":" + HA2).getBytes()));
        Principal principal =
                realm.authenticate(USER, expectedResponse, NONCE, null, null, null, REALM, HA2);

        // THEN
        assertThat(principal, is(instanceOf(GenericPrincipal.class)));
        assertThat( ((GenericPrincipal)principal).getPassword(), equalTo(PASSWORD));
    }

    @Test
    public void testAuthenticateWithUserPasswordAndCredentialHandler() throws Exception {
        // GIVEN
        JNDIRealm realm = buildRealm(ha1());
        realm.setCredentialHandler(buildCredentialHandler());
        realm.setUserPassword(USER_PASSWORD_ATTR);

        // WHEN
        String expectedResponse =
                MD5Encoder.encode(md5Helper.digest((ha1() + ":" + NONCE + ":" + HA2).getBytes()));
        Principal principal =
                realm.authenticate(USER, expectedResponse, NONCE, null, null, null, REALM, HA2);

        // THEN
        assertThat(principal, is(instanceOf(GenericPrincipal.class)));
        assertThat( ((GenericPrincipal)principal).getPassword(), equalTo(ha1()));
    }


    private JNDIRealm buildRealm(String password) throws javax.naming.NamingException,
            NoSuchFieldException, IllegalAccessException, LifecycleException {
        Context context = new TesterContext();
        JNDIRealm realm = new JNDIRealm();
        realm.setContainer(context);
        realm.setUserSearch("");

        Field field = JNDIRealm.class.getDeclaredField("context");
        field.setAccessible(true);
        field.set(realm, mockDirContext(mockSearchResults(password)));

        realm.start();

        return realm;
    }

    private MessageDigestCredentialHandler buildCredentialHandler()
            throws NoSuchAlgorithmException {
        MessageDigestCredentialHandler credentialHandler = new MessageDigestCredentialHandler();
        credentialHandler.setAlgorithm(ALGORITHM);
        return credentialHandler;
    }

    private NamingEnumeration<SearchResult> mockSearchResults(String password)
            throws NamingException {
        @SuppressWarnings("unchecked")
        NamingEnumeration<SearchResult> searchResults = createNiceMock(NamingEnumeration.class);
        expect(Boolean.valueOf(searchResults.hasMore()))
                .andReturn(Boolean.TRUE)
                .andReturn(Boolean.FALSE)
                .andReturn(Boolean.TRUE)
                .andReturn(Boolean.FALSE);
        expect(searchResults.next())
                .andReturn(new SearchResult("ANY RESULT", "",
                        new BasicAttributes(USER_PASSWORD_ATTR, password)))
                .times(2);
        EasyMock.replay(searchResults);
        return searchResults;
    }

    private DirContext mockDirContext(NamingEnumeration<SearchResult> namingEnumeration)
            throws NamingException {
        DirContext dirContext = createNiceMock(InitialDirContext.class);
        expect(dirContext.search(anyString(), anyString(), anyObject(SearchControls.class)))
                .andReturn(namingEnumeration)
                .times(2);
        expect(dirContext.getNameParser(""))
                .andReturn(new NameParserImpl()).times(2);
        expect(dirContext.getNameInNamespace())
                .andReturn("ANY NAME")
                .times(2);
        EasyMock.replay(dirContext);
        return dirContext;
    }

    private String ha1() {
        String a1 = USER + ":" + REALM + ":" + PASSWORD;
        return MD5Encoder.encode(md5Helper.digest(a1.getBytes()));
    }
}
