package com.tramchester.graph;

import com.tramchester.domain.Route;
import com.tramchester.domain.RouteStation;
import com.tramchester.domain.Station;
import com.tramchester.repository.InterchangeRepository;
import org.neo4j.graphalgo.GraphAlgoFactory;
import org.neo4j.graphalgo.PathFinder;
import org.neo4j.graphalgo.WeightedPath;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.traversal.BranchOrderingPolicies;
import org.neo4j.graphdb.traversal.Evaluation;
import org.neo4j.graphdb.traversal.Evaluator;
import org.neo4j.graphdb.traversal.Traverser;
import org.neo4j.kernel.impl.traversal.MonoDirectionalTraversalDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static com.tramchester.graph.GraphStaticKeys.*;
import static com.tramchester.graph.TransportGraphBuilder.Labels.ROUTE_STATION;
import static com.tramchester.graph.TransportRelationshipTypes.*;

public class RouteReachable {
    private static final Logger logger = LoggerFactory.getLogger(RouteReachable.class);

    private final GraphDatabaseService graphDatabaseService;
    private final NodeIdQuery stationIndexQuery;
    private final InterchangeRepository interchangeRepository;

    public RouteReachable(GraphDatabaseService graphDatabaseService, NodeIdQuery stationIndexQuery,
                          InterchangeRepository interchangeRepository) {
        this.graphDatabaseService = graphDatabaseService;
        this.stationIndexQuery = stationIndexQuery;
        this.interchangeRepository = interchangeRepository;
    }

    // supports building tram station reachability matrix
    public boolean getRouteReachableWithInterchange(String startStationId, String endStationId, String routeId) {
        Evaluator evaluator = new MatchOrInterchangeEvaluator(endStationId, routeId, interchangeRepository);
        return hasAnyPaths(startStationId, evaluator);
    }

    // supports position inference on live data
    public List<Route> getRoutesFromStartToNeighbour(Station startStation, String endStationId) {
        List<Route> results = new ArrayList<>();
        Set<Route> firstRoutes = startStation.getRoutes();

        try (Transaction tx = graphDatabaseService.beginTx()) {
            firstRoutes.forEach(route -> {
                Node routeStation = stationIndexQuery.getRouteStationNode(RouteStation.formId(startStation, route));
                Iterable<Relationship> edges = routeStation.getRelationships(ON_ROUTE, Direction.OUTGOING);
                for (Relationship edge : edges) {
                    if (endStationId.equals(edge.getEndNode().getProperty(STATION_ID).toString())) {
                        results.add(route);
                    }
                }
            });

        }
        return results;
    }

    public Set<String> getRoutesSeenBetween(String startStationId, String endStationId) {
        Set<String> results = new HashSet<>();
        try (Transaction tx = graphDatabaseService.beginTx()) {
            Evaluator eval = new MatchOrInterchangeEvaluator(endStationId, interchangeRepository);
            ResourceIterator<Path> paths = generatePaths(startStationId, eval);
            paths.forEachRemaining(path -> path.nodes().forEach(node -> {
                if (node.hasLabel(ROUTE_STATION)) {
                    String routeId = node.getProperty(ROUTE_ID).toString();
                    results.add(routeId);
                }
            }));
            tx.success();
        }

        return results;
    }

    private boolean hasAnyPaths(String startStationId, Evaluator evaluator) {
        long number = 0;
        try (Transaction tx = graphDatabaseService.beginTx()) {
            Node found = stationIndexQuery.getStationNode(startStationId);
            if (found!=null) {
                number = generatePaths(startStationId, evaluator).stream().count();
            } else {
                // should only happen in testing when looking at subsets of the graph
                logger.warn("Cannot find node for station id " + startStationId);
            }
            tx.success();
        }
        return number>0;
    }

    public int getApproxCostBetween(String startStationId, Node endNode) {
        Node startNode = stationIndexQuery.getStationNode(startStationId);
        return getApproxCostBetween(startNode, endNode);
    }

    public int getApproxCostBetween(Node startNode, String endStationId) {
        Node endNode = stationIndexQuery.getStationNode(endStationId);
        return getApproxCostBetween(startNode, endNode);
    }

    public int getApproxCostBetween(String startStationId, String endStationId) {
        Node startNode = stationIndexQuery.getStationNode(startStationId);
        Node endNode = stationIndexQuery.getStationNode(endStationId);
        return getApproxCostBetween(startNode, endNode);
    }

    private int getApproxCostBetween(Node startNode, Node endNode) {

        // follow the ON_ROUTE relationships to quickly find a route without any timing information or check
        PathExpander<Object> forTypesAndDirections = PathExpanders.forTypesAndDirections(
                ON_ROUTE, Direction.OUTGOING,
                ENTER_PLATFORM, Direction.OUTGOING,
                LEAVE_PLATFORM, Direction.OUTGOING,
                BOARD, Direction.OUTGOING,
                DEPART, Direction.OUTGOING,
                INTERCHANGE_BOARD, Direction.OUTGOING,
                INTERCHANGE_DEPART, Direction.OUTGOING,
                WALKS_TO, Direction.OUTGOING,
                WALKS_FROM, Direction.OUTGOING,
                FINISH_WALK, Direction.OUTGOING
        );

        PathFinder<WeightedPath> finder = GraphAlgoFactory.dijkstra(forTypesAndDirections, COST);
        WeightedPath path = finder.findSinglePath(startNode, endNode);
        double weight  = Math.floor(path.weight());
        return (int) weight;
    }

    private ResourceIterator<Path> generatePaths(String startStationId, Evaluator evaluator) {
        Node startNode = stationIndexQuery.getStationNode(startStationId);
        Traverser traverser = new MonoDirectionalTraversalDescription().
                relationships(ON_ROUTE, Direction.OUTGOING).
                relationships(ENTER_PLATFORM, Direction.OUTGOING).
                relationships(BOARD, Direction.OUTGOING).
                relationships(INTERCHANGE_BOARD, Direction.OUTGOING).
                order(BranchOrderingPolicies.PREORDER_DEPTH_FIRST)
                .evaluator(evaluator)
                .traverse(startNode);
        return traverser.iterator();
    }

    private static class MatchOrInterchangeEvaluator implements Evaluator {
        private final String endStationId;
        private final String routeId;
        private final InterchangeRepository interchangeRepository;

        public MatchOrInterchangeEvaluator(String endStationId, String routeId, InterchangeRepository interchangeRepository) {
            this.endStationId = endStationId;
            this.routeId = routeId;
            this.interchangeRepository = interchangeRepository;
        }

        public MatchOrInterchangeEvaluator(String endStationId, InterchangeRepository interchangeRepository) {
            this.endStationId = endStationId;
            this.routeId = "";
            this.interchangeRepository = interchangeRepository;
        }

        @Override
        public Evaluation evaluate(Path path) {

            Node queryNode = path.endNode();

            if (queryNode.hasLabel(ROUTE_STATION)) {
                String currentStationId = queryNode.getProperty(STATION_ID).toString();
                if (endStationId.equals(currentStationId)) {
                    return Evaluation.INCLUDE_AND_PRUNE; // finished, at dest
                }
                if (interchangeRepository.isInterchange(currentStationId)) {
                    return Evaluation.INCLUDE_AND_PRUNE; // finished, at interchange
                }
            }

            if (queryNode.hasRelationship(Direction.OUTGOING, ON_ROUTE)) {
                Relationship routeRelat = queryNode.getSingleRelationship(ON_ROUTE, Direction.OUTGOING);
                if (routeId.isEmpty()) {
                    return Evaluation.EXCLUDE_AND_CONTINUE;
                }
                String id = routeRelat.getProperty(ROUTE_ID).toString();
                if (routeId.equals(id)) {
                    return Evaluation.EXCLUDE_AND_CONTINUE; // if have routeId then only follow if on same route
                } else {
                    return Evaluation.EXCLUDE_AND_PRUNE;
                }
            }

            return Evaluation.EXCLUDE_AND_CONTINUE;
        }
    }
}