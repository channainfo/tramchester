package com.tramchester.unit.dataimport.datacleanse;

import com.tramchester.config.DownloadConfig;
import com.tramchester.dataimport.FetchDataFromUrl;
import com.tramchester.dataimport.URLDownloader;
import com.tramchester.dataimport.Unzipper;
import org.assertj.core.util.Files;
import org.easymock.EasyMock;
import org.easymock.EasyMockSupport;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;

public class FetchDataFromUrlTest extends EasyMockSupport {

    private Path path = Paths.get(Files.temporaryFolderPath());
    private URLDownloader downloader;
    private FetchDataFromUrl fetchDataFromUrl;
    private Path zipFilename;
    private Unzipper unzipper;

    @Before
    public void beforeEachTestRuns() {
        downloader = createMock(URLDownloader.class);
        zipFilename = path.resolve(FetchDataFromUrl.ZIP_FILENAME);
        unzipper = createMock(Unzipper.class);

        DownloadConfig downloadConfig = new DownloadConfig() {
            @Override
            public String getTramDataUrl() {
                return null;
            }

            @Override
            public Path getDataPath() {
                return path;
            }

            @Override
            public Path getUnzipPath() {
                return Paths.get("gtdf-out");
            }
        };

        fetchDataFromUrl = new FetchDataFromUrl(downloader, downloadConfig);
        removeTmpFile();
    }

    @After
    public void afterEachTestRuns() {
        removeTmpFile();
    }

    private void removeTmpFile() {
        if (zipFilename.toFile().exists()) {
            zipFilename.toFile().delete();
        }
    }

    @Test
    public void shouldFetchIfModTimeIsNewer() throws IOException {
        Files.newFile(zipFilename.toAbsolutePath().toString());
        LocalDateTime time = LocalDateTime.now();
        EasyMock.expect(downloader.getModTime()).andReturn(time.plusMinutes(30));
        downloader.downloadTo(zipFilename);
        EasyMock.expectLastCall();
        EasyMock.expect(unzipper.unpack(zipFilename, path)).andReturn(true);

        replayAll();
        fetchDataFromUrl.fetchData(unzipper);
        verifyAll();
    }

    @Test
    public void shouldFetchIfLocalFileNotPresent() throws IOException {
        downloader.downloadTo(zipFilename);
        EasyMock.expectLastCall();
        EasyMock.expect(unzipper.unpack(zipFilename, path)).andReturn(true);

        replayAll();
        fetchDataFromUrl.fetchData(unzipper);
        verifyAll();
    }

    @Test
    public void shouldNotFetchIfModTimeIsNotNewer() throws IOException {
        Files.newFile(zipFilename.toAbsolutePath().toString());
        LocalDateTime time = LocalDateTime.now();
        EasyMock.expect(downloader.getModTime()).andReturn(time.minusDays(1));
        EasyMock.expect(unzipper.unpack(zipFilename, path)).andReturn(true);

        replayAll();
        fetchDataFromUrl.fetchData(unzipper);
        verifyAll();
    }


}
