package com.tramchester.graph;

import org.neo4j.graphdb.Transaction;
import org.picocontainer.Disposable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class NodeIdLabelMap implements Disposable {
    private static final Logger logger = LoggerFactory.getLogger(TransportGraphBuilder.class);

    // map from the NodeId to the Label
    private final Map<TransportGraphBuilder.Labels, Set<Long>> map;
    private final ConcurrentMap<Long, Boolean> queryNodes;

    public NodeIdLabelMap() {
        map = new EnumMap<>(TransportGraphBuilder.Labels.class);
        for (TransportGraphBuilder.Labels label: TransportGraphBuilder.Labels.values()) {
            if (label != TransportGraphBuilder.Labels.QUERY_NODE) {
                map.put(label, new HashSet<>(getCapacity(label), 1.0F));
            }
        }
        queryNodes = new ConcurrentHashMap<>();
    }

    // called when loaded from disc, instead of rebuild
    public void populateNodeLabelMap(GraphDatabase graphDatabase) {
        logger.info("Rebuilding node->label index");
        TransportGraphBuilder.Labels[] labels = TransportGraphBuilder.Labels.values();
        try (Transaction tx = graphDatabase.beginTx()) {
            for (TransportGraphBuilder.Labels label : labels) {
                graphDatabase.findNodes(label).stream().forEach(node -> put(node.getId(), label));
            }
            tx.success();
        }
    }

    @Override
    public void dispose() {
        queryNodes.clear();
        map.clear();
    }

    private int getCapacity(TransportGraphBuilder.Labels label) {
        // approx. sizings
        switch (label) {
            case ROUTE_STATION: return 282;
            case TRAM_STATION: return 93;
            case PLATFORM: return 185;
            case SERVICE: return 8680;
            case HOUR: return 62525;
            case MINUTE: return 314150;
            default: return 0;
        }
    }

    public void put(long id, TransportGraphBuilder.Labels label) {
        map.get(label).add(id);
    }

    public boolean has(final TransportGraphBuilder.Labels label, final long nodeId) {
        if (label == TransportGraphBuilder.Labels.QUERY_NODE) {
            return queryNodes.containsKey(nodeId);
        }
        return map.get(label).contains(nodeId);
    }

    public void putQueryNode(long id) {
        queryNodes.put(id,true);
    }

    public void removeQueryNode(long id) {
        queryNodes.remove(id);
    }

}
