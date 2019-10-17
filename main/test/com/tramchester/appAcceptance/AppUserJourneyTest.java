package com.tramchester.appAcceptance;

import com.tramchester.App;
import com.tramchester.TestConfig;
import com.tramchester.acceptance.infra.*;
import com.tramchester.acceptance.pages.App.AppPage;
import com.tramchester.acceptance.pages.App.Stage;
import com.tramchester.acceptance.pages.App.SummaryResult;
import com.tramchester.integration.Stations;
import org.junit.*;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.openqa.selenium.Cookie;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class AppUserJourneyTest {
    private static final String configPath = "config/localAcceptance.yml";
    private static DriverFactory driverFactory;

    @ClassRule
    public static AcceptanceTestRun testRule = new AcceptanceTestRun(App.class, configPath);

    private final String bury = Stations.Bury.getName();
    private final String altrincham = Stations.Altrincham.getName();
    private final String deansgate = Stations.Deansgate.getName();

    @Rule
    public TestName testName = new TestName();

    private LocalDate nextTuesday;
    private String url;
    private ProvidesDriver providesDriver;

    public static List<String> getBrowserList() {
        if (System.getenv("CIRCLECI") == null) {
            return Arrays.asList("chrome", "firefox");
        }
        // Headless Chrome on CI BOX is ignoring locale which breaks many acceptance tests
        // https://bugs.chromium.org/p/chromium/issues/detail?id=755338
        return Arrays.asList("firefox");
    }

    @Parameterized.Parameters
    public static Iterable<? extends Object> data() {
        return getBrowserList();
    }

    @Parameterized.Parameter
    public String browserName;

    @BeforeClass
    public static void beforeAnyTestsRun() {
        driverFactory = new DriverFactory();
    }

    @Before
    public void beforeEachTestRuns() {
        url = testRule.getUrl()+"/app/index.html";

        providesDriver = driverFactory.get(false, browserName);
        providesDriver.init();

        // TODO offset for when tfgm data is expiring
        nextTuesday = TestConfig.nextTuesday(0);
    }

    @After
    public void afterEachTestRuns() {
        providesDriver.commonAfter(testName);
    }

    @AfterClass
    public static void afterAllTestsRun() {
        driverFactory.close();
        driverFactory.quit();
    }

    @Test
    public void shouldRedirectDirectToJourneyPageAfterFirstVisit() throws UnsupportedEncodingException {
        AppPage welcomePage = providesDriver.getAppPage();
        welcomePage.load(url);
        assertTrue(welcomePage.hasConsentButton());

        assertNull(providesDriver.getCookieNamed("tramchesterVisited"));
        welcomePage.consent();

        // cookie should now be set
        Cookie cookie = providesDriver.getCookieNamed("tramchesterVisited");
        String cookieContents = URLDecoder.decode(cookie.getValue(), "utf8");
        assertEquals("{\"visited\":true}", cookieContents);

        // check redirect
        AppPage shouldBeRedirected = providesDriver.getAppPage();
        shouldBeRedirected.load(url);
        shouldBeRedirected.waitForToStops();
    }

    @Test
    public void shouldHaveInitialValuesAndSetInputsSetCorrectly() {
        AppPage appPage = prepare();

        LocalTime now = LocalTime.now();
        String timeOnPageRaw = appPage.getTime();
        LocalTime timeOnPage = LocalTime.parse(timeOnPageRaw);

        int diff = Math.abs(now.toSecondOfDay() - timeOnPage.toSecondOfDay());
        assertTrue(diff<=60); // allow for page render and webdriver overheads

        assertEquals(LocalDate.now(), appPage.getDate());

        desiredJourney(appPage, altrincham, bury, nextTuesday, LocalTime.parse("10:15"));
        assertJourney(appPage, altrincham, bury, "10:15", nextTuesday);
        desiredJourney(appPage, altrincham, bury, nextTuesday.plusMonths(1), LocalTime.parse("03:15"));
        assertJourney(appPage, altrincham, bury, "03:15", nextTuesday.plusMonths(1));
        desiredJourney(appPage, altrincham, bury, nextTuesday.plusYears(1), LocalTime.parse("20:15"));
        assertJourney(appPage, altrincham, bury, "20:15", nextTuesday.plusYears(1));
    }

    @Test
    public void shouldTravelAltyToBuryAndSetRecents() {
        AppPage appPage = prepare();
        desiredJourney(appPage, altrincham, bury, nextTuesday, LocalTime.parse("10:15"));
        appPage.planAJourney();
        assertTrue(appPage.resultsClickable());

        // check recents are set
        List<String> fromRecent = appPage.getRecentFromStops();
        assertTrue(fromRecent.contains(altrincham));
        assertTrue(fromRecent.contains(bury));
        // check "non recents" don't contain these two stations now
        List<String> remainingStops = appPage.getAllStopsFromStops();
        assertFalse(remainingStops.contains(altrincham));
        assertFalse(remainingStops.contains(bury));
        // still displaying all stations
        assertEquals(93, remainingStops.size()+fromRecent.size());

        // inputs still set
        assertJourney(appPage, altrincham, bury, "10:15", nextTuesday);
    }

    @Test
    public void shouldCheckAltrinchamToDeansgate() {
        AppPage appPage = prepare();
        LocalTime planTime = LocalTime.parse("10:15");
        desiredJourney(appPage, altrincham, deansgate, nextTuesday, planTime);
        appPage.planAJourney();

        assertTrue(appPage.resultsClickable());

        List<SummaryResult> results = appPage.getResults();
        assertEquals(6, results.size());

        for (SummaryResult result : results) {
            assertTrue(result.getDepartTime().isAfter(planTime));
            assertTrue(result.getArriveTime().isAfter(result.getDepartTime()));
            assertEquals("Direct", result.getChanges());
            assertEquals("Tram with No Changes - 23 minutes", result.getSummary());
        }

        SummaryResult firstResult = results.get(0);
        firstResult.moveTo(providesDriver);
        appPage.waitForClickable(firstResult.getElement());
        firstResult.click(providesDriver);
        List<Stage> stages = firstResult.getStages();
    }

    @Test
    public void shouldHideStationInToListWhenSelectedInFromList() {
        AppPage appPage = prepare();
        desiredJourney(appPage, altrincham, bury, nextTuesday, LocalTime.parse("10:15"));
        List<String> toStops = appPage.getToStops();
        assertFalse(toStops.contains(altrincham));
    }

    @Test
    public void shouldShowNoRoutesMessage() {
        AppPage appPage = prepare();
        desiredJourney(appPage, altrincham, bury, nextTuesday, LocalTime.parse("03:15"));
        appPage.planAJourney();

        assertTrue(appPage.noResults());
    }

    private AppPage prepare() {
        AppPage appPage = providesDriver.getAppPage();
        appPage.load(url);
        appPage.consent();
        appPage.waitForToStops();
        return appPage;
    }

    private void desiredJourney(AppPage appPage, String start, String dest, LocalDate date, LocalTime time) {
        appPage.setStart(start);
        appPage.setDest(dest);
        appPage.setDate(date);
        appPage.setTime(time);
    }

    private void assertJourney(AppPage appPage, String start, String dest, String time, LocalDate date) {
        assertEquals(start, appPage.getFromStop());
        assertEquals(dest, appPage.getToStop());
        assertEquals(time, appPage.getTime());
        assertEquals(date, appPage.getDate());
    }

}

