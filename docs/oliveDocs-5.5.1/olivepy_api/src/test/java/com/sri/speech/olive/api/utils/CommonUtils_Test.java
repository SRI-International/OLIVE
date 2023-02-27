package com.sri.speech.olive.api.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class CommonUtils_Test {

    private static final Logger logger = LoggerFactory.getLogger(ClassParser_Test.class);

    @Test
    public void testResolvePath() throws Exception {

        // Test resolving home paths (havea ~)
        String m2Path = "~/.m2";
        String m2LongPath = " ~/.m2/repository/com/sri";
        // this path should exist if working directory is the api directory
        String fullPath = "src/test/resources/one_column.lst";

        Assert.assertTrue(Files.exists(Paths.get("/Users/e24652/.m2/repository/com/sri")));
        Path resolvedPath = CommonUtils.resolvePath(m2Path);
        Assert.assertTrue(Files.exists(resolvedPath) );

        resolvedPath = CommonUtils.resolvePath(m2LongPath);
        Assert.assertTrue(Files.exists(resolvedPath) );

        resolvedPath = CommonUtils.resolvePath(fullPath);
        Assert.assertTrue(Files.exists(resolvedPath) );

    }
}


