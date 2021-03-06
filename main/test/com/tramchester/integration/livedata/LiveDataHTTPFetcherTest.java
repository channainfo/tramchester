package com.tramchester.integration.livedata;

import com.github.cliftonlabs.json_simple.JsonArray;
import com.github.cliftonlabs.json_simple.JsonObject;
import com.github.cliftonlabs.json_simple.Jsoner;
import com.tramchester.Dependencies;
import com.tramchester.domain.places.Station;
import com.tramchester.domain.liveUpdates.StationDepartureInfo;
import com.tramchester.integration.IntegrationTramTestConfig;
import com.tramchester.livedata.LiveDataHTTPFetcher;
import com.tramchester.mappers.LiveDataParser;
import com.tramchester.repository.TransportDataFromFiles;
import com.tramchester.testSupport.LiveDataTestCategory;
import com.tramchester.testSupport.TestEnv;
import org.junit.*;
import org.junit.experimental.categories.Category;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.*;

public class LiveDataHTTPFetcherTest {

    private static Dependencies dependencies;
    private static LiveDataHTTPFetcher fetcher;
    private static String payload;
    private static IntegrationTramTestConfig configuration;

    private TransportDataFromFiles transportData;
    private LiveDataParser parser;

    @BeforeClass
    public static void onceBeforeAnyTestsRun() throws Exception {
        dependencies = new Dependencies();
        configuration = new IntegrationTramTestConfig();
        dependencies.initialise(configuration);
        // don't want to fetch every time
        fetcher = dependencies.get(LiveDataHTTPFetcher.class);
        payload = fetcher.fetch();
    }

    @AfterClass
    public static void OnceAfterAllTestsAreFinished() {
        dependencies.close();
    }

    @Before
    public void beforeEachTestRuns() {
        transportData = dependencies.get(TransportDataFromFiles.class);
        parser = dependencies.get(LiveDataParser.class);
    }

    @Test
    public void shouldHaveTFGMKeyInConfig() {
        assertNotNull("missing tfgm live data key", configuration.getLiveDataSubscriptionKey());
    }

    @Test
    @Category(LiveDataTestCategory.class)
    public void shouldFetchSomethingFromTFGM() {
        assertNotNull(payload);
        assertFalse(payload.isEmpty());
    }

    @Test
    @Category(LiveDataTestCategory.class)
    public void shouldFetchValidDataFromTFGMAPI() {
        List<StationDepartureInfo> departureInfos = parser.parse(payload);

        assertTrue(departureInfos.size()>0);

        Optional<StationDepartureInfo> hasMsgs = departureInfos.stream().
                filter(info -> !info.getMessage().isEmpty()).findAny();

        assertTrue("display with msgs", hasMsgs.isPresent());

        StationDepartureInfo display = hasMsgs.get();

        // this assert will fail if run at certain times of day....
        // assertTrue(aDisplay.getDueTrams().size()>0);
        assertTrue(display.getLineName().length()>0);
        LocalDateTime when = display.getLastUpdate();
        assertEquals(TestEnv.LocalNow().getDayOfMonth(),when.getDayOfMonth());
    }

    @Test
    @Category(LiveDataTestCategory.class)
    public void shouldHaveCrosscheckOnLiveDateDestinations() {
        List<StationDepartureInfo> departureInfos = parser.parse(payload);

        assertTrue(departureInfos.size()>0);

        Set<Station> destinations = departureInfos.stream().map(entry -> entry.getDueTrams().stream()).
                flatMap(Function.identity()).
                map(dueTram -> dueTram.getDestination()).collect(Collectors.toSet());

        Set<String> stationNames = transportData.getStations().stream().map(station -> station.getName()).collect(Collectors.toSet());

        Set<Station> mismatch = destinations.stream().filter(destination -> !stationNames.contains(destination.getName())).
                collect(Collectors.toSet());

        assertTrue(mismatch.toString(), mismatch.isEmpty());
    }

    @Test
    @Category(LiveDataTestCategory.class)
    @Ignore("Part of spike on character set encoding issue for live api")
    public void checkCharacterEncodingOnResponse()  {
        String rawJSON = fetcher.fetch();

        //JSONParser jsonParser = new JSONParser();
        JsonObject parsed = Jsoner.deserialize(rawJSON, new JsonObject());   //(JSONObject)jsonParser.parse(rawJSON);
        assertTrue(parsed.containsKey("value"));
        JsonArray infoList = (JsonArray) parsed.get("value");

        List<String> destinations = new ArrayList<>();
        for (Object item : infoList) {
            JsonObject jsonObject = (JsonObject) item;
            for (int i = 0; i < 4; i++) {
                String place = jsonObject.get(format("Dest%d", i)).toString();
                if (!place.isEmpty()) {
                    destinations.add(place);
                }
            }
        }
        assertFalse(destinations.isEmpty());


    }
}
