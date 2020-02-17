package com.tramchester.resources;

import com.javadocmd.simplelatlng.LatLng;
import com.javadocmd.simplelatlng.LatLngTool;
import com.javadocmd.simplelatlng.util.LengthUnit;
import com.tramchester.config.TramchesterConfig;
import com.tramchester.domain.*;
import com.tramchester.domain.presentation.LatLong;
import com.tramchester.domain.time.TramServiceDate;
import com.tramchester.domain.time.TramTime;
import com.tramchester.graph.*;
import com.tramchester.repository.StationRepository;
import com.tramchester.services.SpatialService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.tramchester.graph.GraphStaticKeys.COST;
import static java.lang.String.format;

public class LocationJourneyPlanner {
    private static final Logger logger = LoggerFactory.getLogger(LocationJourneyPlanner.class);

    private final String queryNodeName = "BEGIN";

    private final SpatialService spatialService;
    private TramchesterConfig config;
    private final RouteCalculator routeCalculator;
    private final RouteCalculatorArriveBy routeCalculatorArriveBy;
    private final StationRepository stationRepository;
    private final CachedNodeOperations nodeOperations;
    private final StationIndexs stationIndexs;

    public LocationJourneyPlanner(SpatialService spatialService, TramchesterConfig config,
                                  RouteCalculator routeCalculator, RouteCalculatorArriveBy routeCalculatorArriveBy, StationRepository stationRepository,
                                  CachedNodeOperations nodeOperations, StationIndexs stationIndexs) {
        this.spatialService = spatialService;
        this.config = config;
        this.routeCalculator = routeCalculator;
        this.routeCalculatorArriveBy = routeCalculatorArriveBy;
        this.stationRepository = stationRepository;
        this.nodeOperations = nodeOperations;
        this.stationIndexs = stationIndexs;
    }

    public Stream<Journey> quickestRouteForLocation(LatLong latLong, String destinationId, TramTime queryTime,
                                                    TramServiceDate queryDate, boolean arriveBy) {
        logger.info(format("Finding shortest path for %s --> %s on %s at %s", latLong, destinationId, queryDate, queryTime));
        List<StationWalk> walksToStart = getStationWalks(latLong);

        Node startOfWalkNode = createWalkingNode(latLong);
        List<Relationship> addedRelationships = new LinkedList<>();

        walksToStart.forEach(stationWalk -> addedRelationships.add(createWalkRelationship(startOfWalkNode, stationWalk,
                TransportRelationshipTypes.WALKS_TO)));

        Stream<Journey> journeys;
        if (arriveBy) {
            journeys = routeCalculatorArriveBy.calculateRouteWalkAtStart(startOfWalkNode, destinationId,
                    queryTime, queryDate);
        } else {
            journeys = routeCalculator.calculateRouteWalkAtStart(startOfWalkNode, destinationId,
                    queryTime, queryDate);
        }

        //noinspection ResultOfMethodCallIgnored
        journeys.onClose(() -> removeWalkNodeAndRelationships(addedRelationships, startOfWalkNode));

        return journeys;
    }

    public Stream<Journey> quickestRouteForLocation(String startId, LatLong destination, TramTime queryTime,
                                                    TramServiceDate queryDate, boolean arriveBy) {
        logger.info(format("Finding shortest path for %s --> %s on %s at %s", startId, destination, queryDate, queryTime));
        List<StationWalk> walksToDest = getStationWalks(destination);

        List<Relationship> addedRelationships = new LinkedList<>();
        List<String> destinationStationIds = new ArrayList<>();
        Node midWalkNode = createWalkingNode(destination);

        walksToDest.forEach(stationWalk -> {
            String walkStationId = stationWalk.getStationId();
            destinationStationIds.add(walkStationId);
            addedRelationships.add(createWalkRelationship(midWalkNode, stationWalk, TransportRelationshipTypes.WALKS_FROM));
        });
        Node endWalk = createWalkingNode(destination);
        Relationship relationshipTo = midWalkNode.createRelationshipTo(endWalk, TransportRelationshipTypes.FINISH_WALK);
        relationshipTo.setProperty(COST,0);
        addedRelationships.add(relationshipTo);

        Stream<Journey> journeys;
        if (arriveBy) {
            journeys = routeCalculatorArriveBy.calculateRouteWalkAtEnd(startId, endWalk, destinationStationIds,
                    queryTime, queryDate);
        } else {
            journeys = routeCalculator.calculateRouteWalkAtEnd(startId, endWalk, destinationStationIds,
                    queryTime, queryDate);
        }

        //noinspection ResultOfMethodCallIgnored
        journeys.onClose(() -> removeWalkNodeAndRelationships(addedRelationships, midWalkNode, endWalk));

        return journeys;
    }

//    public Stream<Journey> quickestRouteForLocation(String startId, LatLong destination, TramTime queryTime,
//                                                    TramServiceDate queryDate, boolean arriveBy) {
//        logger.info(format("Finding shortest path for %s --> %s on %s at %s", startId, destination, queryDate, queryTime));
//        List<StationWalk> walksToDest = getStationWalks(destination);
//
//        Node midWalkNode = createWalkingNode(destination);
//        Node endWalk = createWalkingNode(destination);
//        Relationship relationshipTo = midWalkNode.createRelationshipTo(endWalk, TransportRelationshipTypes.FINISH_WALK);
//        relationshipTo.setProperty(COST,0);
//
//        TramRouteCalculator tramRouteCalculator = arriveBy ? routeCalculatorArriveBy : routeCalculator;
//
//        Stream<Journey> journeys = walksToDest.stream().map(stationWalk -> {
//            Relationship singleWalkRelationship = createWalkRelationship(midWalkNode, stationWalk, TransportRelationshipTypes.WALKS_FROM);
//            Set<Journey> journeyForOneWalks = tramRouteCalculator.calculateRouteWalkAtEnd(startId, endWalk, Collections.singletonList(stationWalk.getStationId()),
//                    queryTime, queryDate).collect(Collectors.toSet());
//            singleWalkRelationship.delete();
//            return journeyForOneWalks;
//        }).map(Collection::stream).flatMap(Function.identity());
//
//        //noinspection ResultOfMethodCallIgnored
//        journeys.onClose(() -> removeWalkNodeAndRelationships(Collections.singletonList(relationshipTo), midWalkNode, endWalk));
//
//        return journeys;
//    }

    private Relationship createWalkRelationship(Node walkNode, StationWalk stationWalk, TransportRelationshipTypes direction) {
        String walkStationId = stationWalk.getStationId();
        int cost = stationWalk.getCost();
        logger.info(format("Add walking relationship from %s to %s cost %s direction %s",
                walkStationId, walkNode,  cost, direction));

        Relationship walkingRelationship;
        Node stationNode = stationIndexs.getStationNode(walkStationId);
        if (direction==TransportRelationshipTypes.WALKS_FROM) {
            walkingRelationship = stationNode.createRelationshipTo(walkNode, direction);
        } else {
            walkingRelationship = walkNode.createRelationshipTo(stationNode, direction);
        }

        walkingRelationship.setProperty(COST, cost);
        walkingRelationship.setProperty(GraphStaticKeys.STATION_ID, walkStationId);
        return walkingRelationship;
    }

    private Node createWalkingNode(LatLong origin) {
        Node startOfWalkNode = nodeOperations.createQueryNode(stationIndexs);
        startOfWalkNode.setProperty(GraphStaticKeys.Station.LAT, origin.getLat());
        startOfWalkNode.setProperty(GraphStaticKeys.Station.LONG, origin.getLon());
        startOfWalkNode.setProperty(GraphStaticKeys.Station.NAME, queryNodeName);
        logger.info(format("Added walking node at %s as node %s", origin, startOfWalkNode));
        return startOfWalkNode;
    }

    private void removeWalkNodeAndRelationships(List<Relationship> relationshipsToDelete, Node... nodesToDelete) {
        logger.info("Removed added walks and start of walk node");
        relationshipsToDelete.forEach(Relationship::delete);
        for (int i = 0; i <nodesToDelete.length; i++) {
            nodeOperations.deleteNode(nodesToDelete[i]);
        }
    }

    private List<StationWalk> getStationWalks(LatLong latLong) {
        List<String> nearbyStationIds = spatialService.getNearestStationsTo(latLong, config.getNumOfNearestStopsForWalking());
        return nearestStations(latLong, nearbyStationIds);
    }

    private List<StationWalk> nearestStations(LatLong latLong, List<String> startIds) {
        List<Location> stations = startIds.stream().map(stationRepository::getStation).
                filter(Optional::isPresent).map(Optional::get).collect(Collectors.toList());

        return stations.stream().map(station ->
                new StationWalk(station, findCostInMinutes(latLong, station))).collect(Collectors.toList());
    }

    private int findCostInMinutes(LatLong latLong, Location station) {
        LatLng point1 = LatLong.getLatLng(latLong);
        LatLng point2 = LatLong.getLatLng(station.getLatLong());

        double distanceInMiles = LatLngTool.distance(point1, point2, LengthUnit.MILE);
        double hours = distanceInMiles / config.getWalkingMPH();
        return (int)Math.ceil(hours * 60D);
    }
}