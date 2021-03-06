package com.tramchester.unit.dataimport.parsers;


import com.tramchester.dataimport.parsers.FeedInfoDataMapper;
import com.tramchester.domain.FeedInfo;
import com.tramchester.domain.time.ProvidesLocalNow;
import com.tramchester.domain.time.ProvidesNow;
import org.junit.Test;

import java.io.IOException;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

public class FeedInfoDataParserTest {
    String feedInfo = "feed_publisher_name,feed_publisher_url,feed_timezone,feed_lang,20160530,20160615,feed_version";

    @Test
    public void shouldParserFeedInfo() throws IOException {
        ProvidesNow providesNow = new ProvidesLocalNow();
        FeedInfoDataMapper feedInfoDataParser = new FeedInfoDataMapper(providesNow);

        FeedInfo info = feedInfoDataParser.parseEntry(ParserBuilder.getRecordFor(feedInfo));
        assertThat(info.getPublisherName()).isEqualTo("feed_publisher_name");
        assertThat(info.getPublisherUrl()).isEqualTo("feed_publisher_url");
        assertThat(info.getTimezone()).isEqualTo("feed_timezone");
        assertThat(info.getLang()).isEqualTo("feed_lang");
        assertThat(info.getVersion()).isEqualTo("feed_version");
        assertThat(info.validFrom()).isEqualTo(LocalDate.of(2016,5,30));
        assertThat(info.validUntil()).isEqualTo(LocalDate.of(2016,6,15));
    }
}
