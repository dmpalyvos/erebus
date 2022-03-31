package io.palyvos.provenance.util;

import java.io.File;
import java.io.IOException;
import org.apache.commons.io.FileUtils;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

public class ExperimentSettingsTest {

  public static final String TEMP_DIRECTORY = "src/test/resources/temp";

  @BeforeTest
  public void createDirectory() throws IOException {
    new File(TEMP_DIRECTORY).mkdirs();
  }

  @AfterTest
  public void deleteDirectory() throws IOException {
    FileUtils.deleteDirectory(new File(TEMP_DIRECTORY));
  }

  @Test
  public void testHostnameStatisticsFile() throws IOException {
    ExperimentSettings.hostnameStatisticsFile("a", "1", TEMP_DIRECTORY, "test", "csv");
  }

  @Test(expectedExceptions = IllegalStateException.class)
  public void testHostnameStatisticsFileExists() throws IOException {
    ExperimentSettings.hostnameStatisticsFile("a", "1", TEMP_DIRECTORY, "test", "csv");
    ExperimentSettings.hostnameStatisticsFile("a", "1", TEMP_DIRECTORY, "test", "csv");
  }

  @Test
  public void testUniqueStatisticsFile() {
    for (int i = 0; i < 100; i++) {
      ExperimentSettings.uniqueStatisticsFile("a", TEMP_DIRECTORY, "test", "csv");
    }
  }
}