package com.tramchester.testSupport;

import com.tramchester.config.AppConfiguration;
import com.tramchester.domain.Agency;
import com.tramchester.domain.Route;
import com.tramchester.domain.TransportMode;
import com.tramchester.domain.presentation.LatLong;
import com.tramchester.domain.time.TramServiceDate;
import io.federecio.dropwizard.swagger.SwaggerBundleConfiguration;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static java.util.Arrays.asList;


public abstract class TestConfig extends AppConfiguration {

    public static AppConfiguration GET() {
        return new TestConfig() {
            @Override
            public Path getDataFolder() {
                return null;
            }
        };
    }

    public static LocalDateTime LocalNow() {
        return LocalDateTime.now(TestConfig.TimeZone);
    }

    @Override
    public boolean getChangeAtInterchangeOnly() { return true; }

    //////
    private List<String> closedStations = asList("St Peters Square");

    @Override
    public boolean getRebuildGraph() {
        return false;
    }

    @Override
    public List<String> getClosedStations() {
        return closedStations;
    }

    @Override
    public String getInstanceDataUrl() {
        return "http://localhost:8080";
    }

    @Override
    public String getTramDataUrl() {
        return "http://odata.tfgm.com/opendata/downloads/TfGMgtfs.zip";
    }

    @Override
    public Path getUnzipPath() {
        return Paths.get("gtdf-out");
    }

    @Override
    public Path getDataPath() {
        return getDataFolder();
    }

    @Override
    public Double getNearestStopRangeKM() {
        return 2D;
    }

    @Override
    public int getNumOfNearestStops() {
        return 6;
    }

    @Override
    public int getNumOfNearestStopsForWalking() {
        return 3;
    }

    @Override
    public double getWalkingMPH() {
        return 3;
    }

    public abstract Path getDataFolder();

    @Override
    public boolean getRedirectHTTP() { return false; }

    @Override
    public String getSecureHost() {
        return "tramchester.com";
    }

    @Override
    public int getMaxWait() {
        return 25;
    }

    // see RouteCalculatorTest.shouldFindEndOfLinesToEndOfLines
    @Override
    public int getMaxJourneyDuration() { return 112; }

    @Override
    public int getNumberQueries() { return 3; }

    @Override
    public int getQueryInterval() { return 12; }

    @Override
    public int getRecentStopsToShow() {
        return 5;
    }

    @Override
    public int getMaxNumResults() {
        return 5;
    }

    @Override
    public SwaggerBundleConfiguration getSwaggerBundleConfiguration() {
        SwaggerBundleConfiguration bundleConfiguration = new SwaggerBundleConfiguration();
        bundleConfiguration.setResourcePackage("com.tramchester.resources");
        return bundleConfiguration;
    }

    @Override
    public int getDataExpiryThreadhold() { return 3; }

    @Override
    public String getLiveDataUrl() {
        return "https://api.tfgm.com/odata/Metrolinks";
    }

    @Override
    public String getLiveDataSubscriptionKey() {
        return System.getenv("TFGMAPIKEY");
    }

    @Override
    public String getLiveDataS3Bucket() { return "tramchestertestlivedatabucket"; }

    @Override
    public long getLiveDataRefreshPeriodSeconds() { return 20L; }

    @Override
    public boolean getRemoveRouteNameSuffix() {
        // issue with truncation of source data, until this is fixed this needs to remain true
        return true;
    }

    @Override
    public boolean getBus() {
        return false;
    }

    public static DateTimeFormatter dateFormatDashes = DateTimeFormatter.ofPattern("YYYY-MM-dd");
    public static DateTimeFormatter dateFormatSimple = DateTimeFormatter.ofPattern("ddMMYYYY");
    private static Agency MET = new Agency("MET");

    public static LocalDate nextTuesday(int offsetDays) {
        DayOfWeek dayOfWeek = DayOfWeek.TUESDAY;
        LocalDate date = LocalNow().toLocalDate().plusDays(offsetDays);
        return getNextDate(dayOfWeek, date);
    }

    private static LocalDate getNextDate(DayOfWeek dayOfWeek, LocalDate date) {
        while (date.getDayOfWeek()!= dayOfWeek) {
            date = date.plusDays(1);
        }
        return avoidChristmasDate(date);
    }

    public static LocalDate avoidChristmasDate(LocalDate date) {
        while (new TramServiceDate(date).isChristmasPeriod()) {
            date = date.plusWeeks(1);
        }
        return date;
    }

    public static LocalDate nextSaturday() {
        return getNextDate(DayOfWeek.SATURDAY, LocalNow().toLocalDate());
    }
    public static LocalDate nextSunday() {
        return getNextDate(DayOfWeek.SUNDAY, LocalNow().toLocalDate());
    }
    public static final LatLong nearAltrincham = new LatLong(53.387483D, -2.351463D);
    public static final LatLong nearPiccGardens = new LatLong(53.4805248D, -2.2394929D);
    public static final LatLong nearShudehill = new LatLong(53.485846, -2.239472);
    public static boolean isCircleci() {
        return System.getenv("CIRCLECI") != null;
    }
    public static Path LiveDataExampleFile = Paths.get("data","test","liveDataSample.json");
    public static DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:00");

    public static Route getTestRoute() {
        return getTestRoute("RouteId");
    }

    public static Route getTestRoute(String routeId) {
        return new Route(routeId, "routeCode", "routeName", TestConfig.MetAgency(), TransportMode.Tram);
    }

    public static Agency MetAgency() {
        return MET;
    }

}
