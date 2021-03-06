package com.tramchester.integration.resources;

import com.tramchester.App;
import com.tramchester.domain.presentation.DTO.AreaDTO;
import com.tramchester.integration.IntegrationClient;
import com.tramchester.integration.IntegrationTestRun;
import com.tramchester.integration.IntegrationTramTestConfig;
import org.junit.ClassRule;
import org.junit.Test;

import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static junit.framework.TestCase.assertTrue;

public class AreaResourceTest {

    @ClassRule
    public static IntegrationTestRun testRule = new IntegrationTestRun(App.class, new IntegrationTramTestConfig());

    @Test
    public void shouldGetAllAreas() {
        List<AreaDTO> results = getAll();

        assertTrue(results.size()>0);
        AreaDTO area = new AreaDTO("Manchester Airport");
        assertTrue(results.contains(area));
        results.clear();
    }

    private List<AreaDTO> getAll() {
        Response result = IntegrationClient.getResponse(testRule, "areas", Optional.empty(), 200);
        return result.readEntity(new GenericType<ArrayList<AreaDTO>>(){});
    }
}
