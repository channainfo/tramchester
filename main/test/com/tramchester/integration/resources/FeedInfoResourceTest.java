package com.tramchester.integration.resources;


import com.tramchester.App;
import com.tramchester.domain.presentation.FeedInfoDTO;
import com.tramchester.integration.IntegrationClient;
import com.tramchester.integration.IntegrationTestRun;
import com.tramchester.integration.IntegrationTramTestConfig;
import org.junit.ClassRule;
import org.junit.Test;

import javax.ws.rs.core.Response;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class FeedInfoResourceTest {
    public static LocalDate validFrom = LocalDate.of(2020, 5, 22); // year, month, day
    public static LocalDate validUntil = LocalDate.of(2020, 7, 22);

    @ClassRule
    public static IntegrationTestRun testRule = new IntegrationTestRun(App.class, new IntegrationTramTestConfig());

    @Test
    public void shouldGetFeedinfoCorrectly() {
        String endPoint = "feedinfo";

        Response responce = IntegrationClient.getResponse(testRule, endPoint, Optional.empty(), 200);

        FeedInfoDTO result = responce.readEntity(FeedInfoDTO.class);

        assertEquals("Transport for Greater Manchester", result.getPublisherName());
        assertEquals("http://www.tfgm.com", result.getPublisherUrl());
        assertEquals("Europe/London", result.getTimezone());
        assertEquals("en", result.getLang());
        assertEquals(validFrom.format(DateTimeFormatter.ofPattern("YYYYMMdd")), result.getVersion());
        assertEquals(validFrom, result.validFrom());
        assertEquals(validUntil, result.validUntil());
        assertFalse(result.getBus());
    }

}
