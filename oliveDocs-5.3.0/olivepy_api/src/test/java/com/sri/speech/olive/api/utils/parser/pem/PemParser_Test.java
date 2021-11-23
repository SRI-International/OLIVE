package com.sri.speech.olive.api.utils.parser.pem;

import com.sri.speech.olive.api.utils.CommonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 *
 */
public class PemParser_Test {

    private static final Logger logger = LoggerFactory.getLogger(PemParser_Test.class);

    public  String AUDIO_TEST_FILE = "${TEST_DATA_ROOT}/testSuite/general/fsh_kws.wav";
    public final String  BASIC_PEM_FILE = "src/test/resources/basic.pem";
    public final String  COMPLICATED_PEM_FILE = "src/test/resources/overlap.pem";
    public final String  BAD_PEM_FILE = "src/test/resources/negative.pem";

    //String scenic_path      = System.getenv("SCENIC");

    public String  TEST_FILE_SMOKE_NAME = "${TEST_DATA_ROOT}/testSuite/general/sad_smoke.wav";
    public String  TEST_FILE_ENG_NAME = "${TEST_DATA_ROOT}/testSuite/general/English.wav";
    public String  TEST_FILE_FSH_NAME = "${TEST_DATA_ROOT}/testSuite/general/fsh_kws.wav";

    @BeforeTest
    public void init(){

        Map<String, String> envMap = System.getenv();
        if(!envMap.containsKey("TEST_DATA_ROOT")){
            throw new SkipException("Skipping test 'TEST_DATA_ROOT not set");
        }
        AUDIO_TEST_FILE = CommonUtils.expandEnvVars(AUDIO_TEST_FILE, envMap);
        TEST_FILE_SMOKE_NAME = CommonUtils.expandEnvVars(TEST_FILE_SMOKE_NAME, envMap);
        TEST_FILE_ENG_NAME = CommonUtils.expandEnvVars(TEST_FILE_ENG_NAME, envMap);
        TEST_FILE_FSH_NAME = CommonUtils.expandEnvVars(TEST_FILE_FSH_NAME, envMap);
    }


    @Test
    public void testSimplePem() throws Exception {

        PemParser pemParser = new PemParser();

        Assert.assertEquals(pemParser.getRegions().size(), 0);
        Assert.assertFalse(pemParser.isValid());

        Assert.assertTrue(pemParser.parse(BASIC_PEM_FILE));

        Assert.assertEquals(pemParser.getRegions().size(), 4);
        Assert.assertTrue(pemParser.isValid());

        List<PemRecord> expectedRecords = new ArrayList<>();
        expectedRecords.add(new PemRecord(TEST_FILE_SMOKE_NAME, "1", "speech", 0.5f, 1.4f));

        expectedRecords.add(new PemRecord(TEST_FILE_SMOKE_NAME, "1", "speech", 2.0f, 3.0f));
        expectedRecords.add(new PemRecord(TEST_FILE_ENG_NAME, "1", "speech", 4.1f, 6.0f));
        expectedRecords.add(new PemRecord(TEST_FILE_FSH_NAME, "1", "speech", 6.1f, 7.0f));

//        expectedRecords.add(new PemRecord("", "1", "", 0, 1));

        Collection<PemRecord> records = pemParser.getRegions();
        for(PemRecord rec : expectedRecords){
            Assert.assertTrue(records.contains(rec));
        }


    }

    @Test
    public void testNotSimplePem() throws Exception {

        PemParser pemParser = new PemParser();

        Assert.assertEquals(pemParser.getRegions().size(), 0);
        Assert.assertFalse(pemParser.isValid());

        Assert.assertTrue(pemParser.parse(COMPLICATED_PEM_FILE));

        Assert.assertEquals(pemParser.getRegions().size(), 5);
        Assert.assertTrue(pemParser.isValid());

        List<PemRecord> expectedRecords = new ArrayList<>();
        expectedRecords.add(new PemRecord(TEST_FILE_SMOKE_NAME, "1", "speech", 0.5f, 1.4f));

        //expectedRecords.add(new PemRecord("../data/sad_smoke.wav", "1", "speech", 1.9, 3.0));
        expectedRecords.add(new PemRecord(TEST_FILE_SMOKE_NAME, "1", "non-speech", 2.0f, 3.0f));
        expectedRecords.add(new PemRecord(TEST_FILE_ENG_NAME, "1", "speech", 4.1f, 6.0f));
        expectedRecords.add(new PemRecord(TEST_FILE_SMOKE_NAME, "1", "speech", 1.9f, 3.3f));

//        expectedRecords.add(new PemRecord("../data/fsh_kws.wav", "1", "speech", 6.1, 7.3));



//        expectedRecords.add(new PemRecord("", "1", "", 0, 1));

        Collection<PemRecord> records = pemParser.getRegions();
        for(PemRecord rec : expectedRecords){
            Assert.assertTrue(records.contains(rec));
        }



    }

    @Test
    public void testBadPem() throws Exception {

        PemParser pemParser = new PemParser();

        Assert.assertEquals(pemParser.getRegions().size(), 0);
        Assert.assertFalse(pemParser.isValid());

        Assert.assertFalse(pemParser.parse(BAD_PEM_FILE));

        // All regions should fail
        Assert.assertEquals(pemParser.getRegions().size(), 0);
        Assert.assertFalse(pemParser.isValid());


        Collection<PemRecord> records = pemParser.getRegions();
        Assert.assertTrue(records.isEmpty());

    }




}



