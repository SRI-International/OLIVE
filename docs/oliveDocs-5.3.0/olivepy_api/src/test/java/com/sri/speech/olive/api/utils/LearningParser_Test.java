package com.sri.speech.olive.api.utils;

import com.sri.speech.olive.api.utils.parser.LearningParser;
import com.sri.speech.olive.api.utils.parser.LearningParser.LearningDataType;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

/**
 * Tests for the RegionParser
 */
public class LearningParser_Test {


    private static final Logger logger = LoggerFactory.getLogger(LearningParser_Test.class);

    // default path:  /$SCENIC/scenic/ui/scenic-api/
//    public final String AUDIO_TEST_FILE = "../../data/fsh_kws.wav";
    public final String  ONE_COLUMN_INPUT = "src/test/resources/one_column.lst";
    public final String  TWO_COLUMN_INPUT = "src/test/resources/training_two_column.lst";
    public final String FOUR_COLUMN_INPUT = "src/test/resources/training_four_column.lst";
    public final String  BAD_THREE_COLUMN_INPUT = "src/test/resources/three_column_bad_files.lst";
    public String  TEST_FILE_NAME = "${TEST_DATA_ROOT}/testSuite/general/sad_smoke.wav";
    public String  TEST_FILE_ENG_NAME = "${TEST_DATA_ROOT}/testSuite/general/English.wav";

    @BeforeTest
    public void init(){

        Map<String, String> envMap = System.getenv();
        if(!envMap.containsKey("TEST_DATA_ROOT")){
            throw new SkipException("Skipping test 'TEST_DATA_ROOT not set");
        }
        TEST_FILE_NAME = CommonUtils.expandEnvVars(TEST_FILE_NAME, envMap);
        TEST_FILE_ENG_NAME = CommonUtils.expandEnvVars(TEST_FILE_ENG_NAME, envMap);
    }

    @Test
    public void testOneColumn() throws Exception {

        // Test a file with only filenames (no class or regions), this is unsupervised input

        // default path:  /Users/E24652/dev/scenic/ui/scenic-api/

        LearningParser rp = new LearningParser();
        Assert.assertFalse(rp.isValid());
        Assert.assertFalse(rp.isUnsupervised());
        Assert.assertFalse(rp.hasClasses());
        Assert.assertFalse(rp.hasRegions());
        Assert.assertNull(rp.getDataType());
        Assert.assertEquals(rp.getFilenames().size(), 0);
        //Assert.assertEquals(rp.getAnnotations().size(), 0);

        // Parse a single colunm file
        Assert.assertTrue(rp.parse(ONE_COLUMN_INPUT));

        // Now get file names
        Assert.assertEquals(rp.getFilenames().size(), 3);
        for(String filename : rp.getFilenames()){
            Assert.assertEquals(rp.getAnnotations(filename).size(), 0);
        }

        Assert.assertTrue(rp.isValid());
        Assert.assertTrue(rp.isUnsupervised());
        Assert.assertFalse(rp.hasClasses());
        Assert.assertFalse(rp.hasRegions());

    }

    @Test
    public void testTwoColumn() throws Exception {

        // Test supervised input with class only annotations

        LearningParser lp = new LearningParser();
        Assert.assertFalse(lp.isValid());
        Assert.assertFalse(lp.isUnsupervised());
        Assert.assertFalse(lp.hasClasses());
        Assert.assertFalse(lp.hasRegions());
        Assert.assertNull(lp.getDataType());
        Assert.assertEquals(lp.getFilenames().size(), 0);
        //Assert.assertEquals(rp.getAnnotations().size(), 0);

        // Parse a single colunm file
        Assert.assertTrue(lp.parse(TWO_COLUMN_INPUT));

        // Now get file names
        Assert.assertEquals(lp.getFilenames().size(), 3);
        Map<String, List<RegionWord>> annots = lp.getAnnotations(TEST_FILE_NAME);
        Assert.assertEquals(annots.size(), 2);
        // There should be no regions
        for(String classID : annots.keySet()){
            Assert.assertEquals(annots.get(classID).size(), 0);
        }

        Assert.assertTrue(lp.isValid());
        Assert.assertFalse(lp.isUnsupervised());
        Assert.assertTrue(lp.hasClasses());
        Assert.assertFalse(lp.hasRegions());
        Assert.assertEquals(lp.getDataType(), LearningDataType.SUPERVISED);


    }

    @Test
    private void testFourColumn() throws Exception {
        // default path:  /Users/E24652/dev/scenic/ui/scenic-api/

        LearningParser lp = new LearningParser();
        Assert.assertFalse(lp.isValid());
        Assert.assertFalse(lp.isUnsupervised());
        Assert.assertFalse(lp.hasClasses());
        Assert.assertFalse(lp.hasRegions());
        Assert.assertNull(lp.getDataType());
        Assert.assertEquals(lp.getFilenames().size(), 0);

        // Parse a single colunm file
        Assert.assertTrue(lp.parse(FOUR_COLUMN_INPUT));

        // Now get file names
        Assert.assertEquals(lp.getFilenames().size(), 3);

        Map<String, List<RegionWord>> annots = lp.getAnnotations(TEST_FILE_NAME);
        Assert.assertEquals(annots.size(), 1);
        // There should be two regions
        Assert.assertEquals(annots.get("sad").size(), 2);

        annots = lp.getAnnotations(TEST_FILE_ENG_NAME);
        Assert.assertEquals(annots.size(), 1);
        // There should be two regions
        Assert.assertEquals(annots.get("eng").size(), 1);
        Assert.assertNull(annots.get("sad"));

        Assert.assertTrue(lp.isValid());
        Assert.assertFalse(lp.isUnsupervised());
        Assert.assertTrue(lp.hasClasses());
        Assert.assertTrue(lp.hasRegions());
        Assert.assertEquals(lp.getDataType(), LearningDataType.SUPERVISED_WITH_REGIONS);

    }
}
