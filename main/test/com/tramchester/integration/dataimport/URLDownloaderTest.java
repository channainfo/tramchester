package com.tramchester.integration.dataimport;

import com.tramchester.dataimport.URLDownloader;
import com.tramchester.testSupport.TestEnv;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;

import static junit.framework.TestCase.assertTrue;

public class URLDownloaderTest {

    private Path temporaryFile;

    @Before
    public void beforeEachTestRuns() {
        temporaryFile = Paths.get(FileUtils.getTempDirectoryPath(), "downloadAFile");
        tidyFile();
    }

    @After
    public void afterEachTestRuns() {
        tidyFile();
    }

    private void tidyFile() {
        if (temporaryFile.toFile().exists()) {
            temporaryFile.toFile().delete();
        }
    }

    @Test
    public void shouldDownloadSomething() throws IOException {
        String url = "https://github.com/fluidicon.png";

        URLDownloader urlDownloader = new URLDownloader();

        LocalDateTime modTime = urlDownloader.getModTime(url);
        assertTrue(modTime.isBefore(TestEnv.LocalNow()));
        assertTrue(modTime.isAfter(LocalDateTime.of(2000,1,1,12,59,22)));

        urlDownloader.downloadTo(temporaryFile, url);

        assertTrue(temporaryFile.toFile().exists());
        assertTrue(temporaryFile.toFile().length()>0);

    }
}
