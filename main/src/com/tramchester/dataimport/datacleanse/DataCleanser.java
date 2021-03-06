package com.tramchester.dataimport.datacleanse;

import com.tramchester.config.TramchesterConfig;
import com.tramchester.dataimport.ErrorCount;
import com.tramchester.dataimport.TransportDataReaderFactory;
import com.tramchester.dataimport.data.*;
import com.tramchester.dataimport.parsers.*;
import com.tramchester.domain.FeedInfo;
import com.tramchester.domain.time.ProvidesNow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;


public class DataCleanser {
    private static final Logger logger = LoggerFactory.getLogger(DataCleanser.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("YYYMMdd");

    private final TransportDataReaderFactory dataReaderFactory;
    private final TransportDataWriterFactory transportDataWriterFactory;
    private final TramchesterConfig config;
    private final ProvidesNow providesNow;

    public DataCleanser(TransportDataReaderFactory readerFactory, TransportDataWriterFactory writerFactort,
                        ProvidesNow providesNow, TramchesterConfig config) {
        this.dataReaderFactory = readerFactory;
        this.transportDataWriterFactory = writerFactort;
        this.providesNow = providesNow;
        this.config = config;
    }

    public ErrorCount run() throws IOException {
        ErrorCount count = new ErrorCount();

        Set<String> agencies = config.getAgencies();

        Set<String> routeCodes = cleanseRoutes(new RouteDataMapper(agencies));

        ServicesAndTrips servicesAndTrips = cleanseTrips(new TripDataMapper(routeCodes));

        Set<String> stopIds = cleanseStoptimes(new StopTimeDataMapper(servicesAndTrips.getTripIds()));

        cleanseStops(new StopDataMapper(stopIds));

        cleanseCalendar(new CalendarDataMapper(servicesAndTrips.getServiceIds()));

        cleanFeedInfo(new FeedInfoDataMapper(providesNow));

        if (!count.noErrors()) {
            logger.warn("Unable to cleanse all data" + count);
        }
        return count;
    }

    public void cleanseCalendar(CalendarDataMapper calendarDataMapper) throws IOException {
        logger.info("**** Start cleansing calendar.");

        Stream<CalendarData> calendar = dataReaderFactory.getForCleanser().getCalendar(calendarDataMapper);

        TransportDataWriter writer = transportDataWriterFactory.getWriter("calendar");
        calendar.forEach(calendarData -> writer.writeLine(String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s",
                        calendarData.getServiceId(),
                        runsOnDay(calendarData.isMonday()),
                        runsOnDay(calendarData.isTuesday()),
                        runsOnDay(calendarData.isWednesday()),
                        runsOnDay(calendarData.isThursday()),
                        runsOnDay(calendarData.isFriday()),
                        runsOnDay(calendarData.isSaturday()),
                        runsOnDay(calendarData.isSunday()),
                        calendarData.getStartDate().format(DATE_FORMAT),
                        calendarData.getEndDate().format(DATE_FORMAT))));

        writer.close();
        calendar.close();
        logger.info("**** End cleansing calendar.\n\n");
    }

    public Set<String> cleanseStoptimes(StopTimeDataMapper stopTimeDataMapper) throws IOException {
        logger.info("**** Start cleansing stop times.");
        Set<String> stopIds = new HashSet<>();

        Stream<StopTimeData> stopTimes = dataReaderFactory.getForCleanser().getStopTimes(stopTimeDataMapper);
        TransportDataWriter writer = transportDataWriterFactory.getWriter("stop_times");

        stopTimes.forEach(stopTime -> {
            writer.writeLine(stopTime.asOutputLine());
            stopIds.add(stopTime.getStopId());
        });
        writer.close();
        stopTimes.close();
        logger.info("**** End cleansing stop times.\n");
        return stopIds;
    }

    public ServicesAndTrips cleanseTrips(TripDataMapper tripDataMapper) throws IOException {
        logger.info("**** Start cleansing trips.");
        Set<String> uniqueSvcIds = new HashSet<>();
        Set<String> tripIds = new HashSet<>();
        Stream<TripData> trips = dataReaderFactory.getForCleanser().getTrips(tripDataMapper);

        TransportDataWriter writer = transportDataWriterFactory.getWriter("trips");

        trips.forEach(trip -> {
            writer.writeLine(String.format("%s,%s,%s,%s",
                    trip.getRouteId(),
                    trip.getServiceId(),
                    trip.getTripId(),
                    trip.getTripHeadsign()));
            tripIds.add(trip.getTripId());
            uniqueSvcIds.add(trip.getServiceId());
        });
        writer.close();
        trips.close();
        logger.info("**** End cleansing trips.\n\n");
        return new ServicesAndTrips(uniqueSvcIds, tripIds);
    }

    public void cleanseStops(StopDataMapper stopDataMapper) throws IOException {
        logger.info("**** Start cleansing stops.");
        Stream<StopData> stops = dataReaderFactory.getForCleanser().getStops(stopDataMapper);

        TransportDataWriter writer = transportDataWriterFactory.getWriter("stops");

        stops.forEach(stop -> {
            String tramPrefix = stop.isTram() ? StopDataMapper.tramStation : "";
            if (unexpectedIdFormat(stop)) {
                logger.warn("Assumption all stations start with digit broken by " + stop.getId());
            }
            writer.writeLine(String.format("%s,%s,\"%s,%s%s\",%s,%s",
                    stop.getId(),
                    stop.getCode(),
                    stop.getArea(),
                    workAroundName(stop.getName()), tramPrefix,
                    stop.getLatitude(),
                    stop.getLongitude()));
        });

        writer.close();
        stops.close();
        logger.info("**** End cleansing stops.\n\n");
    }

    private boolean unexpectedIdFormat(StopData stop) {
        return !Character.isDigit(stop.getId().charAt(0));
    }

    private String workAroundName(String name) {
        if ("St Peters Square".equals(name)) {
            return "St Peter's Square";
        }
        return name;
    }

    public Set<String> cleanseRoutes(RouteDataMapper routeDataMapper) throws IOException {
        logger.info("**** Start cleansing routes");
        Set<String> routeCodes = new HashSet<>();
        Stream<RouteData> routes = dataReaderFactory.getForCleanser().getRoutes(routeDataMapper);

        TransportDataWriter writer = transportDataWriterFactory.getWriter("routes");
        routes.forEach(route -> addRoute(routeCodes, writer, route));

        writer.close();
        routes.close();
        logger.info("**** End cleansing routes.\n\n");
        return routeCodes;
    }

    private void addRoute(Set<String> routeCodes, TransportDataWriter writer, RouteData route) {
        String id = route.getId();
        String routeName = route.getLongName();

        if (config.getRemoveRouteNameSuffix()) {
            int indexOf = routeName.indexOf("(");
            if (indexOf > -1) {
                routeName = routeName.substring(0,indexOf).trim();
            }
        }
        writer.writeLine(String.format("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"",
                id,
                route.getAgency(),
                route.getShortName(),
                routeName, route.getRouteType()));
        routeCodes.add(id);
        logger.info("Added route " + id);
    }

    private String runsOnDay(boolean day) {
        return day ? "1" : "0";
    }

    public void cleanFeedInfo(FeedInfoDataMapper feedInfoDataMapper) throws IOException {
        logger.info("**** Start cleansing feed info.");
        Stream<FeedInfo> feedInfo = dataReaderFactory.getForCleanser().getFeedInfo(feedInfoDataMapper);

        TransportDataWriter writer = transportDataWriterFactory.getWriter("feed_info");

        feedInfo.forEach(info -> writer.writeLine(String.format("%s,%s,%s,%s,%s,%s,%s",
                info.getPublisherName(),
                info.getPublisherUrl(),
                info.getTimezone(),
                info.getLang(),
                info.validFrom().format(DATE_FORMAT),
                info.validUntil().format(DATE_FORMAT),
                info.getVersion())));
        writer.close();
        feedInfo.close();
        logger.info("**** End cleansing feed info.");

    }

}
