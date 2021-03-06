package com.tramchester.graph.states;

import com.tramchester.graph.search.JourneyState;
import com.tramchester.graph.TransportGraphBuilder;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.Relationship;

import static com.tramchester.graph.TransportRelationshipTypes.BOARD;
import static com.tramchester.graph.TransportRelationshipTypes.INTERCHANGE_BOARD;
import static org.neo4j.graphdb.Direction.OUTGOING;

public class TramStationState extends TraversalState {
    private final long stationNodeId;

    public TramStationState(TraversalState parent, Iterable<Relationship> relationships, int cost, long stationNodeId) {
        super(parent, relationships, cost);
        this.stationNodeId = stationNodeId;
    }

    @Override
    public String toString() {
        return "TramStationState{" +
                "stationNodeId=" + stationNodeId +
                ", cost=" + super.getCurrentCost() +
                ", parent=" + parent +
                '}';
    }

    @Override
    public TraversalState createNextState(Path path, TransportGraphBuilder.Labels nodeLabel, Node node,
                                          JourneyState journeyState, int cost) {
        long nodeId = node.getId();
        if (nodeId == destinationNodeId) {
            // TODO Cost of platform depart?
            return new DestinationState(this, cost);
        }

        if (nodeLabel == TransportGraphBuilder.Labels.PLATFORM) {
            return new PlatformState(this,
                    node.getRelationships(OUTGOING, INTERCHANGE_BOARD, BOARD), nodeId, cost);
        }
        if (nodeLabel == TransportGraphBuilder.Labels.QUERY_NODE) {
            return new WalkingState(this, node.getRelationships(OUTGOING), cost);
        }

        throw new RuntimeException("Unexpected node type: "+nodeLabel);

    }
}
