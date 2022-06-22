package com.sri.speech.olive.api.utils;

import com.sri.speech.olive.api.utils.parser.ClassRegionParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

/**
 * Tests for the parsing files and class ids (speakers) from a file
 */
public class ClassParser_Test {


    private static final Logger logger = LoggerFactory.getLogger(ClassParser_Test.class);

    // default path:  /$SCENIC/scenic/ui/scenic-api/
    public final String  ONE_COLUMN_INPUT = "src/test/resources/one_column.lst";
    public final String  TWO_COLUMN_INPUT = "src/test/resources/enroll_column.lst";
    public final String  THREE_COLUMN_INPUT = "src/test/resources/three_column.lst";
    public final String  BAD_THREE_COLUMN_INPUT = "src/test/resources/three_column_bad_files.lst";

    public String SMOKE_FILENAME = "${TEST_DATA_ROOT}/testSuite/general/sad_smoke.wav";
    public String ENGLISH_FILENAME = "${TEST_DATA_ROOT}/testSuite/general/English.wav";
    public String FSH_FILENAME = "${TEST_DATA_ROOT}/testSuite/general/fsh_kws.wav";


    @BeforeTest
    public void init(){

        Map<String, String> envMap = System.getenv();
        if(!envMap.containsKey("TEST_DATA_ROOT")){
            throw new SkipException("Skipping test 'TEST_DATA_ROOT not set");
        }
        SMOKE_FILENAME = CommonUtils.expandEnvVars(SMOKE_FILENAME, envMap);
        ENGLISH_FILENAME = CommonUtils.expandEnvVars(ENGLISH_FILENAME, envMap);
        FSH_FILENAME = CommonUtils.expandEnvVars(FSH_FILENAME, envMap);
    }



    @Test
    public void testOneColumn() throws Exception {

        // Test invalid data

        // default path:  /Users/E24652/dev/scenic/ui/scenic-api/

        ClassRegionParser rp = new ClassRegionParser();
        Assert.assertFalse(rp.isValid());
        Assert.assertEquals(rp.getFilenames().size(), 0);
        Assert.assertEquals(rp.getEnrollments().size(), 0);

        // Parse a single colunm file
        Assert.assertFalse(rp.parse(ONE_COLUMN_INPUT));

        // Now get file names
        Assert.assertEquals(rp.getFilenames().size(), 0);


        Assert.assertFalse(rp.isValid());

    }

    @Test
    public void testTwoColumn() throws Exception {
        // Test valid infput file

        // default path:  /Users/E24652/dev/scenic/ui/scenic-api/

        ClassRegionParser rp = new ClassRegionParser();
        Assert.assertFalse(rp.isValid());
        Assert.assertEquals(rp.getFilenames().size(), 0);
        Assert.assertEquals(rp.getEnrollments().size(), 0);

        // Parse the file
        boolean parsed = rp.parse(TWO_COLUMN_INPUT);
        Assert.assertTrue(parsed);

        // Now get file names
        Assert.assertEquals(rp.getFilenames().size(), 3);
        List<Pair<String, String>> enrollments = rp.getEnrollments();
        Assert.assertEquals(enrollments.size(), 3);

        Assert.assertTrue(enrollments.contains(new Pair<>(SMOKE_FILENAME, "sam")));
        Assert.assertFalse(enrollments.contains(new Pair<>(SMOKE_FILENAME, "bran")));
        Assert.assertTrue(enrollments.contains(new Pair<>(ENGLISH_FILENAME, "hodor")));
        Assert.assertTrue(enrollments.contains(new Pair<>(FSH_FILENAME, "sansa")));

        Assert.assertTrue(rp.isValid());



    }


}
