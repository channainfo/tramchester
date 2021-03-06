package com.tramchester.integration.resources;

import com.tramchester.App;
import com.tramchester.config.AppConfiguration;
import com.tramchester.domain.places.Location;
import com.tramchester.domain.places.MyLocationFactory;
import com.tramchester.domain.presentation.DTO.JourneyDTO;
import com.tramchester.domain.presentation.DTO.JourneyPlanRepresentation;
import com.tramchester.domain.presentation.DTO.StageDTO;
import com.tramchester.domain.presentation.LatLong;
import com.tramchester.domain.time.TramTime;
import com.tramchester.integration.IntegrationTestRun;
import com.tramchester.integration.IntegrationTramTestConfig;
import com.tramchester.testSupport.Stations;
import com.tramchester.testSupport.TestEnv;
import org.junit.*;

import javax.ws.rs.core.Response;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.SortedSet;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class JourneyPlannerLocationResourceTest {

    private static final String TIME_PATTERN = "HH:mm:00";
    private static AppConfiguration config = new IntegrationTramTestConfig();

    @ClassRule
    public static IntegrationTestRun testRule = new IntegrationTestRun(App.class, config);

    private LocalDate when;

    @Before
    public void beforeEachTestRuns() {
        when = TestEnv.nextTuesday(0);
    }

    @Test
    public void shouldFindStationsNearPiccGardensToExchangeSquare() {
        validateJourneyFromLocation(TestEnv.nearPiccGardens, Stations.ExchangeSquare.getId(), LocalTime.of(9,0), false);
    }

    @Test
    public void planRouteAllowingForWalkingTime() {
        SortedSet<JourneyDTO> journeys = validateJourneyFromLocation(TestEnv.nearAltrincham, Stations.Deansgate.getId(),  LocalTime.of(20,9), false);
        assertTrue(journeys.size()>0);
        JourneyDTO firstJourney = journeys.first();

        List<StageDTO> stages = firstJourney.getStages();
        assertEquals(2, stages.size());
        StageDTO walkingStage = stages.get(0);
        TramTime departureTime = walkingStage.getFirstDepartureTime();

        // two walks result in same arrival time
        //List<TramTime> possibleTimes = Arrays.asList(TramTime.of(20, 19), TramTime.of(20, 12));

        // new lockdown timetable
        List<TramTime> possibleTimes = Arrays.asList(TramTime.of(20, 19), TramTime.of(20, 12));

        assertTrue("Expected time "+departureTime.toString(), possibleTimes.contains(departureTime));

        // assertEquals(firstJourney.toString(), TramTime.of(20,48), firstJourney.getExpectedArrivalTime());
        // new lockdown timetable
        assertEquals(firstJourney.toString(), TramTime.of(20,48), firstJourney.getExpectedArrivalTime());
    }

    @Test
    public void reproCacheStalenessIssueWithNearAltyToDeansgate() {
        for (int i = 0; i <1000; i++) {
            LocalTime localTime = LocalTime.of(10, 15);
            SortedSet<JourneyDTO> journeys = validateJourneyFromLocation(TestEnv.nearAltrincham, Stations.Deansgate.getId(),
                    localTime, false);
            assertTrue(journeys.size()>0);

            TramTime planTime = TramTime.of(localTime);
            for (JourneyDTO result : journeys) {
                TramTime departTime = result.getFirstDepartureTime();
                assertTrue(result.toString(), departTime.isAfter(planTime));

                TramTime arriveTime = result.getExpectedArrivalTime();
                assertTrue(result.toString(), arriveTime.isAfter(departTime));
            }
        }
    }

    @Test
    public void planRouteAllowingForWalkingTimeArriveBy() {
        LocalTime queryTime = LocalTime.of(20, 9);
        SortedSet<JourneyDTO> journeys = validateJourneyFromLocation(TestEnv.nearAltrincham, Stations.Deansgate.getId(), queryTime, true);
        assertTrue(journeys.size()>0);
        JourneyDTO firstJourney = journeys.first();

        assertTrue(firstJourney.getFirstDepartureTime().isBefore(TramTime.of(queryTime)));

        List<StageDTO> stages = firstJourney.getStages();
        assertEquals(2, stages.size());
    }

    @Test
    public void shouldPlanRouteEndingInAWalk() {
        SortedSet<JourneyDTO> journeys = validateJourneyToLocation(Stations.Deansgate.getId(), TestEnv.nearAltrincham,
                LocalTime.of(20,9), false);
        JourneyDTO firstJourney = journeys.first();
        List<StageDTO> stages = firstJourney.getStages();
        assertEquals(2, stages.size());
        StageDTO walkingStage = stages.get(1);

        assertEquals(Stations.NavigationRoad.getId(), walkingStage.getFirstStation().getId());
        assertEquals(TestEnv.nearAltrincham, walkingStage.getLastStation().getLatLong());
        assertEquals(14, walkingStage.getDuration());
    }

    @Test
    public void shouldPlanRouteEndingInAWalkArriveBy() {
        LocalTime queryTime = LocalTime.of(20, 9);
        SortedSet<JourneyDTO> results = validateJourneyToLocation(Stations.Deansgate.getId(), TestEnv.nearAltrincham,
                queryTime, true);

        List<JourneyDTO> journeys = results.stream().filter(journeyDTO -> journeyDTO.getStages().size() == 2).collect(Collectors.toList());
        assertFalse(journeys.isEmpty());

        JourneyDTO firstJourney = journeys.get(0);
        List<StageDTO> stages = firstJourney.getStages();
        StageDTO walkingStage = stages.get(1);
        assertTrue(firstJourney.getFirstDepartureTime().isBefore(TramTime.of(queryTime)));

        assertEquals(Stations.NavigationRoad.getId(), walkingStage.getFirstStation().getId());
        assertEquals(TestEnv.nearAltrincham, walkingStage.getLastStation().getLatLong());
        assertEquals(14, walkingStage.getDuration());
    }

    @Test
    public void shouldGiveWalkingRouteFromMyLocationToNearbyStop() {
        SortedSet<JourneyDTO> journeys = validateJourneyFromLocation(TestEnv.nearAltrincham, Stations.Altrincham.getId(),
                LocalTime.of(22, 9), false);
        assertTrue(journeys.size()>0);
        JourneyDTO first = journeys.first();

        List<StageDTO> stages = first.getStages();
        assertEquals(1, stages.size());
        StageDTO walkingStage = stages.get(0);
        assertEquals(TramTime.of(22,9), walkingStage.getFirstDepartureTime());
    }

    @Test
    public void shouldGiveWalkingRouteFromStationToMyLocation() {
        SortedSet<JourneyDTO> journeys = validateJourneyToLocation(Stations.Altrincham.getId(), TestEnv.nearAltrincham,
                LocalTime.of(22, 9), false);
        assertTrue(journeys.size()>0);
        JourneyDTO first = journeys.first();

        List<StageDTO> stages = first.getStages();
        assertEquals(1, stages.size());
        StageDTO walkingStage = stages.get(0);
        assertEquals(TramTime.of(22,9), walkingStage.getFirstDepartureTime());
    }

    @Test
    public void shouldFindStationsNearPiccGardensWalkingOnly() {
        SortedSet<JourneyDTO> journeys = validateJourneyFromLocation(TestEnv.nearPiccGardens, Stations.PiccadillyGardens.getId(),
                LocalTime.of(9,0), false);
        checkAltyToPiccGardens(journeys);
    }

    @Test
    public void shouldFindStationsNearPiccGardensWalkingOnlyArriveBy() {
        LocalTime queryTime = LocalTime.of(9, 0);
        SortedSet<JourneyDTO> journeys = validateJourneyFromLocation(TestEnv.nearPiccGardens, Stations.PiccadillyGardens.getId(),
                queryTime, true);
        journeys.forEach(journeyDTO -> assertTrue(journeyDTO.getFirstDepartureTime().isBefore(TramTime.of(queryTime))));
    }

    private void checkAltyToPiccGardens(SortedSet<JourneyDTO> journeys) {
        assertTrue(journeys.size()>0);
        JourneyDTO first = journeys.first();
        List<StageDTO> stages = first.getStages();
        assertEquals(journeys.toString(), TramTime.of(9,0), first.getFirstDepartureTime());
        assertEquals(journeys.toString(), TramTime.of(9,3), first.getExpectedArrivalTime());
        assertEquals(Stations.PiccadillyGardens.getId(), first.getEnd().getId());

        assertEquals(1, stages.size());
        StageDTO stage = stages.get(0);
        assertEquals(TramTime.of(9,0), stage.getFirstDepartureTime());
        assertEquals(TramTime.of(9,3), stage.getExpectedArrivalTime());
    }

    @Test
    public void reproduceIssueNearAltyToAshton()  {
        SortedSet<JourneyDTO> journeys = validateJourneyFromLocation(TestEnv.nearAltrincham, Stations.Ashton.getId(),
                LocalTime.of(19,47), false);

        journeys.forEach(journey -> {
            assertEquals(Stations.Ashton.getId(), journey.getEnd().getId());
            assertEquals(3, journey.getStages().size());
        });
    }

    @Ignore("Temporary: trams finish at 2300")
    @Test
    public void shouldFindRouteNearEndOfServiceTimes() {
        Location destination = Stations.Deansgate;

        LocalTime queryTime = LocalTime.of(23,00);
        int walkingTime = 13;
        JourneyPlanRepresentation directFromStationNoWalking = getPlanFor(Stations.NavigationRoad, destination,
                queryTime.plusMinutes(walkingTime));
        assertTrue(directFromStationNoWalking.getJourneys().size()>0);
        // now check walking
        validateJourneyFromLocation(TestEnv.nearAltrincham, destination.getId(), queryTime, false);
        validateJourneyFromLocation(TestEnv.nearAltrincham, destination.getId(), queryTime, true);
    }

    private JourneyPlanRepresentation getPlanFor(Location start, Location end, LocalTime time) {
        String date = when.format(TestEnv.dateFormatDashes);
        String timeString = time.format(DateTimeFormatter.ofPattern(TIME_PATTERN));
        Response response = JourneyPlannerResourceTest.getResponseForJourney(testRule, start.getId(), end.getId(), timeString, date, null, false, 3);
        Assert.assertEquals(200, response.getStatus());
        return response.readEntity(JourneyPlanRepresentation.class);
    }

    private SortedSet<JourneyDTO> validateJourneyFromLocation(LatLong location, String destination, LocalTime queryTime, boolean arriveBy) {

        String date = when.format(TestEnv.dateFormatDashes);
        String time = queryTime.format(DateTimeFormatter.ofPattern(TIME_PATTERN));

        Response response = JourneyPlannerResourceTest.getResponseForJourney(testRule,
                MyLocationFactory.MY_LOCATION_PLACEHOLDER_ID, destination, time, date, location, arriveBy, 3);
        Assert.assertEquals(200, response.getStatus());

        return validateJourneyPresent(response);
    }

    private SortedSet<JourneyDTO> validateJourneyToLocation(String startId, LatLong location, LocalTime queryTime, boolean arriveBy) {
        String date = when.format(TestEnv.dateFormatDashes);
        String time = queryTime.format(DateTimeFormatter.ofPattern(TIME_PATTERN));

        Response response = JourneyPlannerResourceTest.getResponseForJourney(testRule, startId,
                MyLocationFactory.MY_LOCATION_PLACEHOLDER_ID, time, date, location, arriveBy, 3);
        Assert.assertEquals(200, response.getStatus());

        return validateJourneyPresent(response);

    }

    private SortedSet<JourneyDTO> validateJourneyPresent(Response response) {
        JourneyPlanRepresentation plan = response.readEntity(JourneyPlanRepresentation.class);
        SortedSet<JourneyDTO> journeys = plan.getJourneys();
        assertTrue(journeys.size()>=1);
        List<StageDTO> stages = journeys.first().getStages();
        assertTrue(stages.size()>0);
        stages.forEach(stage -> assertTrue(stage.toString(),stage.getDuration()>0));
        return journeys;
    }

}
