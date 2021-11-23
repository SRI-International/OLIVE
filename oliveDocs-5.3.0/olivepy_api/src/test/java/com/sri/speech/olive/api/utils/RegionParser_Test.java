package com.sri.speech.olive.api.utils;

import com.sri.speech.olive.api.utils.parser.RegionParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

/**
 * Tests for the RegionParser
 */
public class RegionParser_Test {


    private static final Logger logger = LoggerFactory.getLogger(RegionParser_Test.class);

    // default path:  /$SCENIC/scenic/ui/scenic-api/
    public String AUDIO_TEST_FILE = "${TEST_DATA_ROOT}/testSuite/general/fsh_kws.wav";
    public final String  ONE_COLUMN_INPUT = "src/test/resources/one_column.lst";
    public final String  TWO_COLUMN_INPUT = "src/test/resources/two_column.lst";
    public final String  THREE_COLUMN_INPUT = "src/test/resources/three_column.lst";
    public final String  BAD_THREE_COLUMN_INPUT = "src/test/resources/three_column_bad_files.lst";

    public String  TEST_FILE_SMOKE_NAME = "${TEST_DATA_ROOT}/testSuite/general/sad_smoke.wav";
    public String  TEST_FILE_ENG_NAME = "${TEST_DATA_ROOT}/testSuite/general/English.wav";

    @BeforeTest
    public void init(){

        Map<String, String> envMap = System.getenv();
        if(!envMap.containsKey("TEST_DATA_ROOT")){
            throw new SkipException("Skipping test 'TEST_DATA_ROOT not set");
        }
        AUDIO_TEST_FILE = CommonUtils.expandEnvVars(AUDIO_TEST_FILE, envMap);
        TEST_FILE_SMOKE_NAME = CommonUtils.expandEnvVars(TEST_FILE_SMOKE_NAME, envMap);
        TEST_FILE_ENG_NAME = CommonUtils.expandEnvVars(TEST_FILE_ENG_NAME, envMap);
    }


    @Test
    public void testOneColumn() throws Exception {


        // default path:  /Users/E24652/dev/scenic/ui/scenic-api/

        RegionParser rp = new RegionParser();
        Assert.assertFalse(rp.isValid());
        Assert.assertEquals(rp.getFilenames().size(), 0);
        Assert.assertEquals(rp.getRegions(null).size(), 0);

        // Parse a single colunm file
        Assert.assertTrue(rp.parse(ONE_COLUMN_INPUT));

        // Now get file names
        Assert.assertEquals(rp.getFilenames().size(), 3);
        for(String filename : rp.getFilenames()){
            Assert.assertEquals(rp.getRegions(filename).size(), 0);
        }

        Assert.assertTrue(rp.isValid());
        Assert.assertFalse(rp.isRegionsOnly());

    }

    @Test
    public void testTwoColumn() throws Exception {
        // Regions only - the audio filename is supplied some other way

        // default path:  /Users/E24652/dev/scenic/ui/scenic-api/

        RegionParser rp = new RegionParser();
        Assert.assertFalse(rp.isValid());
        Assert.assertEquals(rp.getFilenames().size(), 0);
        Assert.assertEquals(rp.getRegions(null).size(), 0);

        // Parse a single colunm file
        Assert.assertTrue(rp.parse(TWO_COLUMN_INPUT));

        // Now get file names
        Assert.assertEquals(rp.getFilenames().size(), 0);
        List<RegionWord> words = rp.getRegions(null);
        Assert.assertEquals(words.size(), 3);


        Assert.assertTrue(rp.isValid());
        Assert.assertTrue(rp.isRegionsOnly());

    }

    @Test
    public void testThreeColumn() throws Exception {
        testThreeColumnImpl();
    }


    @Test
    public void testBadThreeColumn() throws Exception {
        testThreeColumnImpl();
    }


    private void testThreeColumnImpl() throws Exception {
        // default path:  /Users/E24652/dev/scenic/ui/scenic-api/

        RegionParser rp = new RegionParser();
        Assert.assertFalse(rp.isValid());
        Assert.assertEquals(rp.getFilenames().size(), 0);
        Assert.assertEquals(rp.getRegions(null).size(), 0);

        // Parse a single colunm file
        Assert.assertTrue(rp.parse(THREE_COLUMN_INPUT));

        // Now get file names
        Assert.assertEquals(rp.getFilenames().size(), 3);

        List<RegionWord> words = rp.getRegions(TEST_FILE_SMOKE_NAME);
        Assert.assertEquals(words.size(), 2);
        RegionWord rw = words.get(0);
        Assert.assertEquals(rw.start, 500);
        Assert.assertEquals(rw.end, 1400);

        rw = words.get(1);
        Assert.assertEquals(rw.start, 2000);
        Assert.assertEquals(rw.end, 3000);

        words = rp.getRegions(TEST_FILE_ENG_NAME);
        Assert.assertEquals(words.size(), 1);
        rw = words.get(0);
        Assert.assertEquals(rw.start, 4100);
        Assert.assertEquals(rw.end, 6000);


        words = rp.getRegions(AUDIO_TEST_FILE);
        Assert.assertEquals(words.size(), 1);
        rw = words.get(0);
        Assert.assertEquals(rw.start, 0);
        Assert.assertEquals(rw.end, 7000);


        words = rp.getRegions(null);
        Assert.assertEquals(words.size(), 0);


        Assert.assertTrue(rp.isValid());
        Assert.assertFalse(rp.isRegionsOnly());
    }
}
