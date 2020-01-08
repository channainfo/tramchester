package com.tramchester.integration.graph;

import com.tramchester.Dependencies;
import com.tramchester.TestConfig;
import com.tramchester.config.TramchesterConfig;
import com.tramchester.domain.*;
import com.tramchester.graph.RouteCalculator;
import com.tramchester.integration.IntegrationTramTestConfig;
import com.tramchester.integration.Stations;
import com.tramchester.repository.TransportData;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.*;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.NotInTransactionException;
import org.neo4j.graphdb.Transaction;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static org.junit.Assert.*;

public class RouteCalculatorTestAllJourneys {

    // TODO this needs to be > longest running test which is far from ideal
    public static final int TXN_TIMEOUT_SECS = 2 * 60;
    private static Dependencies dependencies;
    private static TramchesterConfig testConfig;
    private static GraphDatabaseService database;

    private RouteCalculator calculator;
    private LocalDate nextTuesday = TestConfig.nextTuesday(0);
    private Transaction tx;
    private Map<Long, Transaction> threadToTxnMap;

    @BeforeClass
    public static void onceBeforeAnyTestsRun() throws Exception {
        dependencies = new Dependencies();
        testConfig = new IntegrationTramTestConfig();
        dependencies.initialise(testConfig);
        database = dependencies.get(GraphDatabaseService.class);
    }

    @AfterClass
    public static void OnceAfterAllTestsAreFinished() {
        dependencies.close();
    }

    @Before
    public void beforeEachTestRuns() {
        tx = database.beginTx(TXN_TIMEOUT_SECS, TimeUnit.SECONDS);
        calculator = dependencies.get(RouteCalculator.class);
        threadToTxnMap = new HashMap<>();
    }

    @After
    public void afterEachTestRuns() {
        tx.close();
        // can't close transactions on other threads as neo4j uses thread local to cache the transaction
//        threadToTxnMap.values().forEach(Transaction::close);
        threadToTxnMap.clear();
    }

    @Test
    public void shouldFindRouteEachStationToEveryOtherStream() {

        TransportData data = dependencies.get(TransportData.class);

        Set<Station> allStations = data.getStations();

        // pairs of stations to check
        Set<Pair<String, String>> combinations = allStations.stream().map(start -> allStations.stream().
                map(dest -> Pair.of(start, dest))).
                flatMap(Function.identity()).
                filter(pair -> !matches(pair, Stations.Interchanges)).
                filter(pair -> !matches(pair, Stations.EndOfTheLine)).
                map(pair -> Pair.of(pair.getLeft().getId(), pair.getRight().getId())).
                collect(Collectors.toSet());

        List<TramTime> queryTimes = Collections.singletonList(TramTime.of(6, 5));
        Map<Pair<String, String>, Optional<RawJourney>> results = validateAllHaveAtLeastOneJourney(nextTuesday, combinations, queryTimes);

        // now find longest journey
        Optional<Integer> maxNumberStops = results.values().stream().
                filter(Optional::isPresent).
                map(Optional::get).
                map(journey -> journey.getStages().stream().
                        map(RawStage::getPassedStops).
                        reduce(Integer::sum)).
                filter(Optional::isPresent).
                map(Optional::get).
                max(Integer::compare);

        assertTrue(maxNumberStops.isPresent());
        assertEquals(39, maxNumberStops.get().intValue());
    }

    private boolean matches(Pair<Station, Station> locationPair, List<Location> locations) {
        return locations.contains(locationPair.getLeft()) && locations.contains(locationPair.getRight());
    }

    private Map<Pair<String, String>, Optional<RawJourney>> validateAllHaveAtLeastOneJourney(
            LocalDate queryDate, Set<Pair<String, String>> combinations, List<TramTime> queryTimes) {

        Map<Pair<String, String>, Optional<RawJourney>> results = new HashMap<>();

        // check each pair, collect results into (station,station)->result
        results =
                combinations.parallelStream().
                        map(this::checkForTx).
                        map(journey -> Pair.of(journey,
                                calculator.calculateRoute(journey.getLeft(), journey.getRight(), queryTimes,
                                        new TramServiceDate(queryDate), RouteCalculator.MAX_NUM_GRAPH_PATHS).limit(1).
                                        findAny())).
                        collect(Collectors.toMap(Pair::getLeft, Pair::getRight));

        // check all results present, collect failures into a list
        List<Pair<String, String>> failed = results.entrySet().stream().
                filter(journey -> !journey.getValue().isPresent()).
                map(Map.Entry::getKey).
                map(pair -> Pair.of(pair.getLeft(), pair.getRight())).
                collect(Collectors.toList());
        assertEquals(format("Failed some of %s (finished %s) combinations", results.size(), combinations.size()) + failed.toString(),
                0L, failed.size());
        assertEquals("Not enough results", combinations.size(), results.size());
        return results;
    }

    private Pair<String, String>  checkForTx(Pair<String, String> journey) {
        long id = Thread.currentThread().getId();
        if (threadToTxnMap.containsKey(id)) {
            return journey;
        }

        try {
            database.getNodeById(1);
        }
        catch (NotInTransactionException noTxnForThisThread) {
            Transaction txn = database.beginTx(TXN_TIMEOUT_SECS, TimeUnit.SECONDS);
            threadToTxnMap.put(id, txn);
        }
        catch(Exception uncaught) {
            throw uncaught;
        }
        return journey;
    }


}
