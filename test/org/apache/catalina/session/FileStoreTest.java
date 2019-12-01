package org.apache.catalina.session;

import org.apache.catalina.Manager;
import org.apache.tomcat.unittest.TesterContext;
import org.apache.tomcat.unittest.TesterServletContext;
import org.apache.tomcat.util.http.fileupload.FileUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 * Test utility methods of FileStore class
 *
 * @author Govinda Sakhare
 */
public class FileStoreTest {

    private static final String SESS_TEMPPATH = "SESS_TEMP";
    private static final File dir = new File (SESS_TEMPPATH);
    private static FileStore fileStore;
    private static File file1 = new File (SESS_TEMPPATH + "/tmp1.session");
    private static File file2 = new File (SESS_TEMPPATH + "/tmp2.session");
    private static Manager manager = new StandardManager ();

    @BeforeClass
    public static void setup() throws IOException {
        TesterContext testerContext = new TesterContext ();
        testerContext.setServletContext (new TesterServletContext ());
        manager.setContext (testerContext);
        fileStore = new FileStore ();
        fileStore.setManager (manager);
    }

    @AfterClass
    public static void cleanup() throws IOException {
        FileUtils.cleanDirectory (dir);
        FileUtils.deleteDirectory (dir);
    }

    @Before
    public void beforeEachTest() throws IOException {
        fileStore.setDirectory (SESS_TEMPPATH);
        dir.mkdir ();
        file1.createNewFile ();
        file2.createNewFile ();
    }

    @Test
    public void getSize() throws Exception {
        assertEquals (2, fileStore.getSize ());
    }

    @Test
    public void clear() throws Exception {
        fileStore.clear ();
        assertEquals (0, fileStore.getSize ());
    }

    @Test
    public void keys() throws Exception {
        assertArrayEquals (new String[]{"tmp1", "tmp2"}, fileStore.keys ());
        fileStore.clear ();
        assertArrayEquals (new String[]{}, fileStore.keys ());
    }

    @Test
    public void removeTest() throws Exception {
        fileStore.remove ("tmp1");
        assertEquals (1, fileStore.getSize ());
    }
}