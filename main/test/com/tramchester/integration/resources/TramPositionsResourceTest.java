package com.tramchester.integration.resources;

import com.tramchester.App;
import com.tramchester.domain.presentation.DTO.TramsPositionsDTO;
import com.tramchester.domain.presentation.TramPositionDTO;
import com.tramchester.integration.IntegrationClient;
import com.tramchester.integration.IntegrationTestRun;
import com.tramchester.integration.IntegrationTramTestConfig;
import org.junit.ClassRule;
import org.junit.Test;

import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertFalse;

public class TramPositionsResourceTest {
    @ClassRule
    public static IntegrationTestRun testRule = new IntegrationTestRun(App.class, new IntegrationTramTestConfig());

    @Test
    public void shouldGetSomePositions() {
        String endPoint = "positions";
        Response responce = IntegrationClient.getResponse(testRule, endPoint, Optional.empty(), 200);

        TramsPositionsDTO result = responce.readEntity(TramsPositionsDTO.class);

        List<TramPositionDTO> positions = result.getPositionsList();
        assertFalse(positions.isEmpty());

        TramPositionDTO first = positions.get(0);
        assertFalse(first.getTrams().isEmpty());

    }
}