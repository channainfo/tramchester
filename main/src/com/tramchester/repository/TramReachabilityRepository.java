package com.tramchester.repository;

import com.tramchester.domain.Route;
import com.tramchester.domain.RouteStation;
import com.tramchester.domain.Station;
import com.tramchester.graph.RouteReachable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

import static java.lang.String.format;

public class TramReachabilityRepository {
    private static final Logger logger = LoggerFactory.getLogger(RoutesRepository.class);

    private final InterchangeRepository interchangeRepository;
    private final RouteReachable routeReachable;
    private final TransportData transportData;

    private List<String> tramStationIndexing; // a list as we need ordering and IndexOf
    private Map<String, boolean[]> matrix; // stationId -> boolean[]

    public TramReachabilityRepository(InterchangeRepository interchangeRepository, RouteReachable routeReachable,
                                      TransportData transportData) {
        this.interchangeRepository = interchangeRepository;
        this.routeReachable = routeReachable;
        this.transportData = transportData;
        tramStationIndexing = new ArrayList<>();
        matrix = new HashMap<>();
    }

    public void buildRepository() {
        logger.info("Build repository");

        Set<RouteStation> routeStations = transportData.getRouteStations().stream().filter(RouteStation::isTram).collect(Collectors.toSet());
        Set<Station> tramStations = transportData.getStations().stream().filter(Station::isTram).collect(Collectors.toSet());

        tramStations.forEach(uniqueStation -> tramStationIndexing.add(uniqueStation.getId()));

        int size = tramStations.size();
        routeStations.forEach(routeStation -> {
            boolean[] flags = new boolean[size];
            String startStationId = routeStation.getStationId();
            tramStations.forEach(destinationStation -> {
                String destinationStationId = destinationStation.getId();
                boolean result;
                if (destinationStationId.equals(startStationId)) {
                    result = true;
                } else {
                    result = routeReachable.getRouteReachableWithInterchange(startStationId,
                            destinationStationId, routeStation.getRouteId());
                }
                flags[tramStationIndexing.indexOf(destinationStationId)] = result;
            });
            matrix.put(routeStation.getId(), flags);
        });
        logger.info(format("Added %s entries", size));
    }

    public boolean stationReachable(String routeStationId, Station destinationStation) {
        RouteStation routeStation =  transportData.getRouteStation(routeStationId);

        if (routeStation.isTram() && destinationStation.isTram()) {
            // route station is a tram station
            int index = tramStationIndexing.indexOf(destinationStation.getId());
            if (index<0) {
                throw new RuntimeException(format("Failed to find index for %s routeStation was %s", destinationStation,
                        routeStation));
            }
            return matrix.get(routeStationId)[index];
        }
        throw new RuntimeException("Call for trams only");
    }

//    private boolean reachableForRouteCodeAndInterchange(RouteStation routeStation, String destinationStationId) {
//        String routeStationRouteId = routeStation.getRouteId();
//
//        // quick win: desintation shares a route with current location
//        Station destinationStation = transportData.getStation(destinationStationId);
//        Set<String> destinationRoutes = destinationStation.getRoutes().stream().map(Route::getId).collect(Collectors.toSet());
//        if (destinationRoutes.contains(routeStationRouteId)) {
//            return true;
//        }
//
//        // TODO factor out routeVia as don't change during a query
//        Set<Route> routesVia = interchangeRepository.findRoutesViaInterchangeFor(destinationStationId);
//        return routesVia.contains(routeStation.getRoute());
//    }

}