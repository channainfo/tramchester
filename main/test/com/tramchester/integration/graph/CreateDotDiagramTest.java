package com.tramchester.integration.graph;


import com.tramchester.Dependencies;
import com.tramchester.DiagramCreator;
import com.tramchester.domain.places.Location;
import com.tramchester.graph.GraphDatabase;
import com.tramchester.integration.IntegrationTramTestConfig;
import com.tramchester.testSupport.Stations;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static java.lang.String.format;

public class CreateDotDiagramTest {
    private static Dependencies dependencies;
    private GraphDatabase database;

    @BeforeClass
    public static void onceBeforeAnyTestsRun() throws Exception {
        dependencies = new Dependencies();
        IntegrationTramTestConfig configuration = new IntegrationTramTestConfig();
        dependencies.initialise(configuration);
    }

    @Before
    public void beforeEachOfTheTestsRun() {
        database = dependencies.get(GraphDatabase.class);
    }

    @AfterClass
    public static void OnceAfterAllTestsAreFinished() {
        dependencies.close();
    }

    @Test
    public void shouldProduceADotDiagramOfTheTramNetwork() throws IOException {
        int depthLimit = 2;

        create(Stations.Deansgate, depthLimit);
        create(Stations.StPetersSquare, depthLimit);
        create(Stations.Cornbrook, depthLimit);
        create(Stations.ExchangeSquare, depthLimit);
        create(Stations.MarketStreet, depthLimit);
        create(Stations.Victoria, depthLimit);
        create(Arrays.asList(Stations.ExchangeSquare,Stations.Deansgate,Stations.Cornbrook,Stations.ExchangeSquare), 4);
    }

    private void create(List<Location> startPoints, int depthLimit) throws IOException {
        String filename = startPoints.get(0).getName();
        DiagramCreator creator = new DiagramCreator(database, depthLimit);
        List<String> ids = startPoints.stream().map(point -> point.getId()).collect(Collectors.toList());
        creator.create(format("around_%s_trams.dot", filename), ids);
    }

    private void create(Location startPoint, int depthLimit) throws IOException {
        DiagramCreator creator = new DiagramCreator(database, depthLimit);
        creator.create(format("%s_trams.dot", startPoint.getName()), startPoint.getId());
    }

}
