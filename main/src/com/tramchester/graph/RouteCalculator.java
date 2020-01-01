package com.tramchester.graph;

import com.tramchester.config.TramchesterConfig;
import com.tramchester.domain.*;
import com.tramchester.domain.exceptions.TramchesterException;
import com.tramchester.domain.presentation.LatLong;
import com.tramchester.graph.Nodes.NodeFactory;
import com.tramchester.graph.Nodes.TramNode;
import com.tramchester.graph.Relationships.RelationshipFactory;
import com.tramchester.graph.Relationships.TransportRelationship;
import com.tramchester.repository.ReachabilityRepository;
import com.tramchester.repository.RunningServices;
import com.tramchester.repository.TransportData;
import org.neo4j.gis.spatial.SpatialDatabaseService;
import org.neo4j.graphalgo.CostEvaluator;
import org.neo4j.graphalgo.WeightedPath;
import org.neo4j.graphalgo.impl.path.Dijkstra;
import org.neo4j.graphalgo.impl.util.PathInterestFactory;
import org.neo4j.graphdb.*;
import org.neo4j.kernel.impl.util.NoneStrictMath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.lang.String.format;

public class RouteCalculator extends StationIndexs {
    private static final Logger logger = LoggerFactory.getLogger(RouteCalculator.class);

    public static final int MAX_NUM_GRAPH_PATHS = 2; // todo into config

    private final String queryNodeName = "BEGIN";

    private final NodeFactory nodeFactory;
    private final MapPathToStages pathToStages;
    private final CostEvaluator<Double> costEvaluator;
    private final TramchesterConfig config;
    private final CachedNodeOperations nodeOperations;
    private final TransportData transportData;
    private final ReachabilityRepository reachabilityRepository;

    public RouteCalculator(GraphDatabaseService db, TransportData transportData, NodeFactory nodeFactory, RelationshipFactory relationshipFactory,
                           SpatialDatabaseService spatialDatabaseService, CachedNodeOperations nodeOperations, MapPathToStages pathToStages,
                           CostEvaluator<Double> costEvaluator, TramchesterConfig config, ReachabilityRepository reachabilityRepository) {
        super(db, relationshipFactory, spatialDatabaseService, true);
        this.transportData = transportData;
        this.nodeFactory = nodeFactory;
        this.nodeOperations = nodeOperations;
        this.pathToStages = pathToStages;
        this.costEvaluator = costEvaluator;
        this.config = config;
        this.reachabilityRepository = reachabilityRepository;
    }

    public Stream<RawJourney> calculateRoute(String startStationId, String endStationId, List<TramTime> queryTimes,
                                          TramServiceDate queryDate, int limit) {
        Node endNode = getStationNode(endStationId);

        logger.info(format("Finding shortest path for %s --> %s on %s at %s limit:%s",
                startStationId,
                endStationId, queryDate, queryTimes, limit));

        Node startNode = getStationNode(startStationId);
        return gatherJounerys(startNode, endNode, queryTimes, queryDate, limit, endStationId);
    }

//    public Set<RawJourney> calculateRoute(AreaDTO areaA, AreaDTO areaB, List<TramTime> queryTimes, TramServiceDate queryDate, int limit) {
//        Node endNode = getAreaNode(areaA.getAreaName());
//
//        Set<RawJourney> journeys;
//            Node startNode = getAreaNode(areaB.getAreaName());
//
//            journeys = gatherJounerys(startNode, endNode, queryTimes, queryDate, limit,
//                    "noEndStationId");
//
//        return journeys;
//    }

    public Stream<RawJourney> calculateRoute(LatLong origin, List<StationWalk> stationWalks, Station endStation,
                                          List<TramTime> queryTimes, TramServiceDate queryDate, int numGraphPaths) {

        int limit = stationWalks.isEmpty() ? numGraphPaths : (numGraphPaths * stationWalks.size());
        String endStationId = endStation.getId();
        Node endNode = getStationNode(endStationId);
        List<Relationship> addedWalks = new LinkedList<>();

        Stream<RawJourney> journeys;
        if (config.getEdgePerTrip()) {
            journeys = getWalkingJourneyStream(origin, stationWalks, queryTimes, queryDate, limit, endStationId, endNode, addedWalks);
        } else {
            journeys = getWalkingJourneyStream(origin, stationWalks, queryTimes, queryDate, limit, endStationId, endNode, addedWalks);
        }
        return journeys;
    }

    private Stream<RawJourney> getWalkingJourneyStream(LatLong origin, List<StationWalk> stationWalks, List<TramTime> queryTimes, TramServiceDate queryDate, int limit, String endStationId, Node endNode, List<Relationship> addedWalks) {
        Stream<RawJourney> journeys;
        Node startNode = nodeOperations.createQueryNode(graphDatabaseService);
        startNode.setProperty(GraphStaticKeys.Station.LAT, origin.getLat());
        startNode.setProperty(GraphStaticKeys.Station.LONG, origin.getLon());
        startNode.setProperty(GraphStaticKeys.Station.NAME, queryNodeName);
        logger.info(format("Added start node at %s as node %s", origin, startNode));

        stationWalks.forEach(stationWalk -> {
            String walkStationId = stationWalk.getStationId();
            Node node = getStationNode(walkStationId);
            int cost = stationWalk.getCost();
            logger.info(format("Add walking relationship from %s to %s cost %s", startNode, walkStationId, cost));
            Relationship walkingRelationship = startNode.createRelationshipTo(node, TransportRelationshipTypes.WALKS_TO);
            walkingRelationship.setProperty(GraphStaticKeys.COST, cost);
            walkingRelationship.setProperty(GraphStaticKeys.STATION_ID, walkStationId);
            addedWalks.add(walkingRelationship);
        });

        journeys = gatherJounerys(startNode, endNode, queryTimes, queryDate, limit, endStationId);

        // must delete relationships first, otherwise may not delete node
        if (config.getEdgePerTrip()) {
            journeys.onClose(() -> {
                addedWalks.forEach(Relationship::delete);
                nodeOperations.deleteNode(startNode);
            });
        } else {
            addedWalks.forEach(Relationship::delete);
            nodeOperations.deleteNode(startNode);
        }
        return journeys;
    }

    private Stream<RawJourney> gatherJounerys(Node startNode, Node endNode, List<TramTime> queryTimes, TramServiceDate queryDate,
                                              int limit, String endStationId) {
        RunningServices runningServicesIds = new RunningServices(transportData.getServicesOnDate(queryDate));

        if (config.getEdgePerTrip()) {
            Stream<RawJourney> journeyStream = queryTimes.stream().
                    map(queryTime -> new ServiceHeuristics(costEvaluator, nodeOperations, reachabilityRepository, config,
                            queryTime, runningServicesIds, endStationId)).
                    map(serviceHeuristics -> findShortestPathEdgePerTrip(startNode, endNode, serviceHeuristics, endStationId)).
                    flatMap(Function.identity()).
                    map(path -> new RawJourney(pathToStages.mapDirect(path.getPath()), path.getQueryTime()));
            return journeyStream;
        }

        Set<RawJourney> journeys = new LinkedHashSet<>(); // order matters
        queryTimes.forEach(queryTime -> {
            ServiceHeuristics serviceHeuristics = new ServiceHeuristics(costEvaluator, nodeOperations, reachabilityRepository, config,
                    queryTime, runningServicesIds, endStationId);

            logger.info("Finding shortest path");
            logger.info(format("Finding shortest path for %s --> %s on %s at %s limit:%s",
                    startNode.getProperty(GraphStaticKeys.Station.NAME),
                    endNode.getProperty(GraphStaticKeys.Station.NAME), queryDate, queryTime, limit));

            Stream<WeightedPath> paths;
                LazyTimeBasedPathExpander pathExpander = new LazyTimeBasedPathExpander(relationshipFactory, serviceHeuristics);
                paths = findShortestPathNormal(startNode, endNode, pathExpander);
                journeys.addAll(mapStreamToJourneySet(paths, limit, queryTime));
                if (journeys.isEmpty()) {
                    serviceHeuristics.reportReasons();
                    pathExpander.reportVisits(MAX_NUM_GRAPH_PATHS);
                }
        });
        return journeys.stream();
    }

    private Stream<WeightedPath> findShortestPathNormal(Node startNode, Node endNode, PathExpander<Double> pathExpander) {

        if (startNode.getProperty(GraphStaticKeys.Station.NAME).equals(queryNodeName)) {
            logger.info("Query node based search, setting start time to actual query time");
        }

        Dijkstra pathFinder = new Dijkstra(pathExpander, costEvaluator,
                PathInterestFactory.allShortest(NoneStrictMath.EPSILON));
        Iterable<WeightedPath> pathIterator = pathFinder.findAllPaths(startNode, endNode);

        return StreamSupport.stream(pathIterator.spliterator(), false);
    }

    private Stream<TimedWeightedPath> findShortestPathEdgePerTrip(Node startNode, Node endNode,
                                                             ServiceHeuristics serviceHeutistics, String endStationId) {
        if (startNode.getProperty(GraphStaticKeys.Station.NAME).equals(queryNodeName)) {
            logger.info("Query node based search, setting start time to actual query time");
        }

        TramNetworkTraverser tramNetworkTraverser = new TramNetworkTraverser(serviceHeutistics, nodeOperations,
                endNode, endStationId);

        return tramNetworkTraverser.findPaths(startNode).map(path -> new TimedWeightedPath(path, serviceHeutistics.getQueryTime()));
    }

    private Set<RawJourney> mapStreamToJourneySet(Stream<WeightedPath> paths, int limit, TramTime queryTime) {

        Set<RawJourney> journeys = new LinkedHashSet<>(); // order matters

        if (config.getEdgePerTrip()) {
            paths.sorted(Comparator.comparingDouble(WeightedPath::weight)).forEach(path -> {
                logger.info(format("edge per trip parse graph path of length %s with limit of %s ", path.length(), limit));
                List<RawStage> stages = pathToStages.mapDirect(path);
                RawJourney journey = new RawJourney(stages, queryTime);
                journeys.add(journey);
            });
        } else {
            paths.limit(limit).forEach(path -> {
                logger.info(format("parse graph path of length %s with limit of %s ", path.length(), limit));
                List<RawStage> stages = pathToStages.map(path, queryTime);
                RawJourney journey = new RawJourney(stages, queryTime);
                journeys.add(journey);
            });
        }

        paths.close();

        if (journeys.size()==0) {
            logger.warn(format("Did not create any journeys from the limit:%s queryTime:%s",
                    limit, queryTime));
        }
        return journeys;
    }

    public TramNode getStation(String stationId) {
        Node node = getStationNode(stationId);
        return nodeFactory.getNode(node);
    }

    // should be a set
    public List<TransportRelationship> getOutboundRouteStationRelationships(String routeStationId) throws TramchesterException {
        return graphQuery.getRouteStationRelationships(routeStationId, Direction.OUTGOING);
    }

    // should be a set
    public List<TransportRelationship> getInboundRouteStationRelationships(String routeStationId) throws TramchesterException {
        return graphQuery.getRouteStationRelationships(routeStationId, Direction.INCOMING);
    }

    private class TimedWeightedPath {
        private final WeightedPath path;
        private final TramTime queryTime;

        public TimedWeightedPath(WeightedPath path, TramTime queryTime) {

            this.path = path;
            this.queryTime = queryTime;
        }

        public WeightedPath getPath() {
            return path;
        }

        public TramTime getQueryTime() {
            return queryTime;
        }
    }
}

