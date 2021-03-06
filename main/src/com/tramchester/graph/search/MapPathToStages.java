package com.tramchester.graph.search;


import com.tramchester.domain.places.Station;
import com.tramchester.domain.*;
import com.tramchester.domain.input.Trip;
import com.tramchester.domain.places.Location;
import com.tramchester.domain.places.MyLocationFactory;
import com.tramchester.domain.presentation.LatLong;
import com.tramchester.domain.presentation.TransportStage;
import com.tramchester.domain.time.TramTime;
import com.tramchester.graph.GraphStaticKeys;
import com.tramchester.graph.TransportRelationshipTypes;
import com.tramchester.repository.PlatformRepository;
import com.tramchester.repository.TransportData;
import com.tramchester.resources.RouteCodeToClassMapper;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.Relationship;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.tramchester.graph.GraphStaticKeys.*;
import static com.tramchester.graph.TransportRelationshipTypes.WALKS_FROM;
import static com.tramchester.graph.TransportRelationshipTypes.WALKS_TO;
import static java.lang.String.format;


public class MapPathToStages {
    private static final Logger logger = LoggerFactory.getLogger(MapPathToStages.class);

    private final RouteCodeToClassMapper routeIdToClass;
    private final TransportData transportData;
    private final MyLocationFactory myLocationFactory;
    private final PlatformRepository platformRepository;

    public MapPathToStages(RouteCodeToClassMapper routeIdToClass, TransportData transportData,
                           MyLocationFactory myLocationFactory, PlatformRepository platformRepository) {
        this.routeIdToClass = routeIdToClass;
        this.transportData = transportData;
        this.myLocationFactory = myLocationFactory;
        this.platformRepository = platformRepository;
    }

    public List<TransportStage> mapDirect(Path path, TramTime queryTime) {
        logger.info(format("Mapping path length %s to transport stages for %s", path.length(), queryTime));
        ArrayList<TransportStage> results = new ArrayList<>();

        List<Relationship> relationships = new ArrayList<>();
        path.relationships().forEach(relationships::add);

        if (relationships.size()==0) {
            return results;
        }
        if (relationships.size()==1) {
            // ASSUME: direct walk
            results.add(directWalk(relationships.get(0), queryTime));
            return results;
        }

        State state = new State(transportData, platformRepository, queryTime);
        for(Relationship relationship : relationships) {
            TransportRelationshipTypes type = TransportRelationshipTypes.valueOf(relationship.getType().name());
            logger.debug("Mapping type " + type);

            switch (type) {
                case BOARD:
                case INTERCHANGE_BOARD:
                    state.board(relationship);
                    break;
                case DEPART:
                case INTERCHANGE_DEPART:
                    results.add(state.depart(relationship));
                    break;
                case TO_MINUTE:
                    state.beginTrip(relationship).ifPresent(results::add);
                    break;
                case TRAM_GOES_TO:
                case BUS_GOES_TO:
                    state.passStop(relationship);
                    break;
                case WALKS_TO:
                    state.walk(relationship);
                    break;
                case WALKS_FROM:
                    results.add(state.walkFrom(relationship));
                    break;
                case TO_SERVICE:
//                    state.toService(relationship);
                    break;
                case ENTER_PLATFORM:
                    state.enterPlatform(relationship);
                    break;
                case LEAVE_PLATFORM:
                    state.leavePlatform(relationship);
                    break;
                case TO_HOUR:
                case FINISH_WALK:
                    break;
                default:
                    throw new RuntimeException(format("Unexpected relationship %s in path %s", type, path));
            }
        }
        logger.info(format("Mapped path length %s to transport %s stages", path.length(), results.size()));
        return results;
    }

    private TransportStage directWalk(Relationship relationship, TramTime timeWalkStarted) {
        if (relationship.isType(WALKS_TO)) {
            WalkStarted walkStarted = walkStarted(relationship);
            return new WalkingStage(walkStarted.start, walkStarted.destination,
                    walkStarted.cost, timeWalkStarted, false);
        } else if (relationship.isType(WALKS_FROM)) {
            return createWalkFrom(relationship, timeWalkStarted);
        } else {
            throw new RuntimeException("Unexpected single relationship: " +relationship);
        }
    }

    private int getCost(Relationship relationship) {
        return (int)relationship.getProperty(COST);
    }

    private WalkStarted walkStarted(Relationship relationship) {
        int cost = getCost(relationship);
        Node startNode = relationship.getStartNode();

        String stationId = relationship.getProperty(STATION_ID).toString();
        Location destination = transportData.getStation(stationId);

        double lat = (double)startNode.getProperty(GraphStaticKeys.Station.LAT);
        double lon =  (double)startNode.getProperty(GraphStaticKeys.Station.LONG);
        Location start = myLocationFactory.create(new LatLong(lat,lon));
        return new WalkStarted(start, destination, cost);
    }

    private WalkingStage createWalkFrom(Relationship relationship, TramTime walkStartTime) {
        int cost = getCost(relationship);

        String stationId = relationship.getProperty(STATION_ID).toString();
        Location start = transportData.getStation(stationId);

        Node endNode = relationship.getEndNode();
        double lat = (double)endNode.getProperty(GraphStaticKeys.Station.LAT);
        double lon =  (double)endNode.getProperty(GraphStaticKeys.Station.LONG);
        Location walkEnd = myLocationFactory.create(new LatLong(lat,lon));

        return new WalkingStage(start, walkEnd, cost, walkStartTime, true);
    }

    private class State {
        private final TransportData transportData;
        private final PlatformRepository platformRepository;
        private final TramTime queryTime;

        private WalkStarted walkStarted;
        private TramTime boardingTime;
        private TramTime departTime;
        private Station boardingStation;
        private String routeCode;
        private String tripId;
        private int stopsSeen;
        private int tripCost;
        private Optional<Platform> boardingPlatform;
        private Route route;

        private int boardCost;
        private int platformEnterCost;
        private int platformLeaveCost;
        private int departCost;

        private State(TransportData transportData, PlatformRepository platformRepository, TramTime queryTime) {
            this.transportData = transportData;
            this.platformRepository = platformRepository;
            this.queryTime = queryTime;
            reset();
        }

        public void board(Relationship relationship) {
            boardCost = getCost(relationship);
            boardingStation = transportData.getStation(relationship.getProperty(STATION_ID).toString());
            routeCode = relationship.getProperty(ROUTE_ID).toString();
            route = transportData.getRoute(routeCode);
            if (route.isTram()) {
                String stopId = relationship.getProperty(PLATFORM_ID).toString();
                boardingPlatform = platformRepository.getPlatformById(stopId);
            }
            departTime = null;
        }

        public VehicleStage depart(Relationship relationship) {
            String stationId = relationship.getProperty(STATION_ID).toString();
            Station departStation = transportData.getStation(stationId);
            Trip trip = transportData.getTrip(tripId);

            int passedStops = stopsSeen - 1;
            VehicleStage vehicleStage = new VehicleStage(boardingStation, route,
                    route.getMode(), routeIdToClass.map(route.getId()), trip, boardingTime,
                    departStation, passedStops);

            if (stopsSeen == 0) {
                logger.error("Zero passed stops " + vehicleStage);
            }

            boardingPlatform.ifPresent(vehicleStage::setPlatform);
            vehicleStage.setCost(tripCost);
            reset();

            departCost = getCost(relationship);
            departTime = vehicleStage.getExpectedArrivalTime();

            return vehicleStage;
        }

        private void reset() {
            stopsSeen = 0; // don't count single stops
            tripId = "";
            routeCode = "";
            tripCost = 0;
            boardingPlatform = Optional.empty();
        }

        public Optional<WalkingStage> beginTrip(Relationship relationship) {
            String newTripId = relationship.getProperty(TRIP_ID).toString();

            if (tripId.isEmpty()) {
                this.tripId = newTripId;
                LocalTime property = (LocalTime) relationship.getProperty(TIME);
                boardingTime = TramTime.of(property);
            } else if (!tripId.equals(newTripId)){
                throw new RuntimeException(format("Mid flight change of trip from %s to %s", tripId, newTripId));
            }

            if (walkStarted==null) {
                return Optional.empty();
            }

            int totalCostOfWalk = walkStarted.cost + boardCost + platformEnterCost;
            TramTime timeWalkStarted = boardingTime.minusMinutes(totalCostOfWalk);
            if (timeWalkStarted.isBefore(queryTime)) {
                logger.error("Computed walk start ahead of query time");
            }
            WalkingStage walkingStage = new WalkingStage(walkStarted.start, walkStarted.destination,
                    walkStarted.cost, timeWalkStarted, false);
            walkStarted = null;
            return Optional.of(walkingStage);
        }

        public void passStop(Relationship relationship) {
            tripCost = tripCost + getCost(relationship);
            stopsSeen = stopsSeen + 1;
        }

        public void walk(Relationship relationship) {
            walkStarted = walkStarted(relationship);
        }

        public void enterPlatform(Relationship relationship) {
            platformEnterCost = getCost(relationship);
        }

        public TransportStage walkFrom(Relationship relationship) {
            TramTime walkStartTime;
            if (departTime==null) {
                walkStartTime = queryTime.plusMinutes(platformLeaveCost + departCost);
            } else {
                walkStartTime = departTime.plusMinutes(platformLeaveCost + departCost);

            }
            return createWalkFrom(relationship, walkStartTime);
        }

        public void leavePlatform(Relationship relationship) {
            platformLeaveCost = getCost(relationship);
        }
    }

    private static class WalkStarted {
        private final Location start;
        private final Location destination;
        private final int cost;

        public WalkStarted(Location start, Location destination, int cost) {

            this.start = start;
            this.destination = destination;
            this.cost = cost;
        }
    }

}
