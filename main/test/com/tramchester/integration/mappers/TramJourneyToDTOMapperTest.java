package com.tramchester.integration.mappers;


import com.tramchester.Dependencies;
import com.tramchester.domain.places.Location;
import com.tramchester.graph.GraphDatabase;
import com.tramchester.domain.*;
import com.tramchester.domain.input.Trip;
import com.tramchester.domain.presentation.DTO.JourneyDTO;
import com.tramchester.domain.presentation.DTO.StageDTO;
import com.tramchester.domain.presentation.TransportStage;
import com.tramchester.domain.time.TramServiceDate;
import com.tramchester.domain.time.TramTime;
import com.tramchester.integration.IntegrationTramTestConfig;
import com.tramchester.testSupport.Stations;
import com.tramchester.mappers.TramJourneyToDTOMapper;
import com.tramchester.repository.TransportDataFromFiles;
import com.tramchester.testSupport.TestEnv;
import org.junit.*;
import org.neo4j.graphdb.Transaction;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TramJourneyToDTOMapperTest {
    private static GraphDatabase database;
    private static TransportDataFromFiles transportData;
    private final LocalDate when = TestEnv.nextTuesday(0);
    private LocalTime sevenAM;
    private LocalTime eightAM;

    private static Dependencies dependencies;
    private TramJourneyToDTOMapper mapper;
    private List<TransportStage> stages;
    private Transaction tx;
    private TramServiceDate tramServiceDate;

    @BeforeClass
    public static void onceBeforeAnyTestsRun() throws IOException {
        dependencies = new Dependencies();
        IntegrationTramTestConfig testConfig = new IntegrationTramTestConfig();
        dependencies.initialise(testConfig);
        transportData = dependencies.get(TransportDataFromFiles.class);
        database = dependencies.get(GraphDatabase.class);
    }

    @AfterClass
    public static void OnceAfterAllTestsAreFinished() {
        dependencies.close();
    }

    @Before
    public void beforeEachTestRuns() {
        tx = database.beginTx(180, TimeUnit.SECONDS);

        mapper = dependencies.get(TramJourneyToDTOMapper.class);
        stages = new LinkedList<>();
        sevenAM = LocalTime.of(7, 0);
        eightAM = LocalTime.of(8, 0);
        tramServiceDate = new TramServiceDate(when);
    }

    @After
    public void onceAfterEachTestRuns() {
        tx.close();
    }

    @Test
    public void shouldEnsureTripsAreOrderByEarliestFirst() {
        TramTime time = TramTime.of(15,30);

        TransportStage vicToRoch = getRawVehicleStage(Stations.Victoria, Stations.Rochdale, TestEnv.getTestRoute(),
                time, 42, 16);
        stages.add(vicToRoch);

        JourneyDTO result = mapper.createJourneyDTO(new Journey(stages, time), tramServiceDate);

        StageDTO stage = result.getStages().get(0);
        // for this service trips later in the list actually depart earlier, so this would fail
        assertTrue(stage.getFirstDepartureTime().asLocalTime().isBefore(LocalTime.of(16,0)));
    }

    @Test
    public void shouldEnsureTripsAreOrderByEarliestFirstSpanningMidnightService() {
        TramTime pm1044  = TramTime.of(22,44);

        VehicleStage rawStage = getRawVehicleStage(Stations.ManAirport, Stations.Cornbrook, TestEnv.getTestRoute(),
                pm1044, 42, 14);

        stages.add(rawStage);
        JourneyDTO journey = mapper.createJourneyDTO(new Journey(stages, pm1044), tramServiceDate);

        assertFalse(journey.getStages().isEmpty());
        StageDTO stage = journey.getStages().get(0);
        // for this service trips later in the list actually depart earlier, so this would fail
        assertTrue(stage.getFirstDepartureTime().asLocalTime().isBefore(LocalTime.of(22,55)));
    }

    @Test
    public void shouldMapSimpleJourney() {

        VehicleStage altToCorn = getRawVehicleStage(Stations.Altrincham, Stations.Cornbrook, TestEnv.getTestRoute(),
                TramTime.of(7,0), 42, 8);

        stages.add(altToCorn);
        JourneyDTO journeyDTO = mapper.createJourneyDTO(new Journey(stages, TramTime.of(7,0)), tramServiceDate);

        assertEquals(1, journeyDTO.getStages().size());
        StageDTO stage = journeyDTO.getStages().get(0);
        assertEquals(Stations.Altrincham.getId(),stage.getFirstStation().getId());
        assertEquals(Stations.Cornbrook.getId(),stage.getLastStation().getId());
        assertTrue(stage.getDuration()>0);
        assertTrue(stage.getFirstDepartureTime().asLocalTime().isAfter(sevenAM));
        assertTrue(stage.getFirstDepartureTime().asLocalTime().isBefore(eightAM));
        assertTrue(stage.getExpectedArrivalTime().asLocalTime().isAfter(sevenAM));
        assertTrue(stage.getExpectedArrivalTime().asLocalTime().isBefore(eightAM));
        assertEquals(8, stage.getPassedStops());
        assertEquals(TramTime.of(7,0), journeyDTO.getQueryTime());
    }

    @Test
    public void shouldMapTwoStageJourney() {
        TramTime am10 = TramTime.of(10,0);
        Location begin = Stations.Altrincham;
        Location middle = Stations.Cornbrook;
        Location end = Stations.ManAirport;

        VehicleStage rawStageA = getRawVehicleStage(begin, middle, createRoute("route text"), am10, 42, 8);
        VehicleStage rawStageB = getRawVehicleStage(middle, end, createRoute("route2 text"), am10.plusMinutes(42), 20, 8);
        stages.add(rawStageA);
        stages.add(rawStageB);

        JourneyDTO journey = mapper.createJourneyDTO(new Journey(stages, am10), tramServiceDate);

        assertEquals(2, journey.getStages().size());

        StageDTO stage1 = journey.getStages().get(0);
        assertEquals(begin.getId(),stage1.getFirstStation().getId());
        assertEquals(middle.getId(),stage1.getLastStation().getId());

        StageDTO stage2 = journey.getStages().get(1);
        assertEquals(middle.getId(),stage2.getFirstStation().getId());
        assertEquals(end.getId(),stage2.getLastStation().getId());
    }

    private Route createRoute(String name) {
        return new Route("routeId", "shortName", name, TestEnv.MetAgency(), TransportMode.Tram);
    }

    @Test
    public void shouldMapWalkingStageJourneyFromMyLocation() {
        TramTime pm10 = TramTime.of(22,0);

        WalkingStage walkingStage = new WalkingStage(Stations.Deansgate, Stations.MarketStreet, 10, pm10, false);
        stages.add(walkingStage);

        JourneyDTO journey = mapper.createJourneyDTO(new Journey(stages, pm10), tramServiceDate);
        assertEquals(1, journey.getStages().size());

        StageDTO stage = journey.getStages().get(0);
        assertEquals(Stations.Deansgate.getId(),stage.getFirstStation().getId());
        assertEquals(Stations.MarketStreet.getId(),stage.getLastStation().getId());
        assertEquals(Stations.MarketStreet.getId(),stage.getActionStation().getId());
    }

    @Test
    public void shouldMapWalkingStageJourneyToMyLocation() {
        TramTime pm10 = TramTime.of(22,0);

        WalkingStage walkingStage = new WalkingStage(Stations.Deansgate, Stations.MarketStreet, 10, pm10, true);
        stages.add(walkingStage);

        JourneyDTO journey = mapper.createJourneyDTO(new Journey(stages, pm10), tramServiceDate);
        assertEquals(1, journey.getStages().size());

        StageDTO stage = journey.getStages().get(0);
        assertEquals(Stations.Deansgate.getId(),stage.getFirstStation().getId());
        assertEquals(Stations.MarketStreet.getId(),stage.getLastStation().getId());
        assertEquals(Stations.Deansgate.getId(),stage.getActionStation().getId());
    }

    @Test
    public void shouldMapThreeStageJourneyWithWalk() {
        TramTime am10 = TramTime.of(10,0);
        Location begin = Stations.Altrincham;
        Location middleA = Stations.Deansgate;
        Location middleB = Stations.MarketStreet;
        Location end = Stations.Bury;

        VehicleStage rawStageA = getRawVehicleStage(begin, middleA, createRoute("route text"), am10, 42, 8);
        int walkCost = 10;
        WalkingStage walkingStage = new WalkingStage(middleA, middleB, walkCost, am10, false);
        VehicleStage finalStage = getRawVehicleStage(middleB, end, createRoute("route3 text"), am10, 42, 9);

        stages.add(rawStageA);
        stages.add(walkingStage);
        stages.add(finalStage);

        JourneyDTO journey = mapper.createJourneyDTO(new Journey(stages, am10), tramServiceDate);

        assertEquals(3, journey.getStages().size());

//        StageDTO stage1 = journey.getStages().get(0);

        StageDTO stage2 = journey.getStages().get(1);
        assertEquals(middleB.getId(),stage2.getActionStation().getId());
        assertEquals(middleB.getId(),stage2.getLastStation().getId());
        assertEquals(walkCost, stage2.getDuration());

        StageDTO stage3 = journey.getStages().get(2);
        assertEquals(middleB.getId(),stage3.getFirstStation().getId());
        assertEquals(end.getId(),stage3.getLastStation().getId());

        TramTime arrivalTime = stage3.getExpectedArrivalTime();
        assertTrue(arrivalTime.asLocalTime().isAfter(LocalTime.of(10,10)));

    }

    @Test
    public void shouldMapEndOfDayJourneyCorrectly() {
        TramTime startTime = TramTime.of(22,50);
        Location start = Stations.Altrincham;
        Location middle = Stations.TraffordBar;
        Location finish = Stations.ManAirport;

        VehicleStage rawStageA = getRawVehicleStage(start, middle, createRoute("route text"), startTime,
                18, 8);
        VehicleStage rawStageB = getRawVehicleStage(middle, finish, createRoute("route2 text"), startTime.plusMinutes(18),
                42, 9);

        stages.add(rawStageA);
        stages.add(rawStageB);

        JourneyDTO result = mapper.createJourneyDTO(new Journey(stages, startTime), tramServiceDate);

        assertEquals(2, result.getStages().size());
        // +1, leave tram time
        assertEquals(startTime.plusMinutes(18+42+1), result.getExpectedArrivalTime());
    }

    private VehicleStage getRawVehicleStage(Location start, Location finish, Route route, TramTime startTime,
                                            int cost, int passedStops) {

        Trip validTrip = transportData.getTripsFor(start.getId()).iterator().next();

        VehicleStage vehicleStage = new VehicleStage(start, route, TransportMode.Tram, "cssClass", validTrip,
                startTime.plusMinutes(1), finish, passedStops);

        vehicleStage.setCost(cost);
        vehicleStage.setPlatform(new Platform(start.getId() + "1", "platform name"));

        return vehicleStage;

    }

}
