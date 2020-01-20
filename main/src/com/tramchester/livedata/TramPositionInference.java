package com.tramchester.livedata;

import com.tramchester.domain.Platform;
import com.tramchester.domain.Route;
import com.tramchester.domain.Station;
import com.tramchester.domain.liveUpdates.DueTram;
import com.tramchester.domain.liveUpdates.StationDepartureInfo;
import com.tramchester.graph.TramRouteReachable;
import com.tramchester.repository.LiveDataSource;
import com.tramchester.repository.StationAdjacenyRepository;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

import static java.lang.String.format;

public class TramPositionInference {
    private static final Logger logger = LoggerFactory.getLogger(TramPositionInference.class);

    private final LiveDataSource liveDataSource;
    private final StationAdjacenyRepository adjacenyRepository;
    private final TramRouteReachable routeReachable;

    public TramPositionInference(LiveDataSource liveDataSource, StationAdjacenyRepository adjacenyRepository, TramRouteReachable routeReachable) {
        this.liveDataSource = liveDataSource;
        this.adjacenyRepository = adjacenyRepository;
        this.routeReachable = routeReachable;
    }

    public List<TramPosition> inferWholeNetwork() {
        logger.info("Infer tram positions for whole network");
        List<TramPosition> results = new ArrayList<>();
        Set<Pair<Station, Station>> pairs = adjacenyRepository.getStationParis();
        pairs.forEach(pair -> {
            TramPosition result = findBetween(pair.getLeft(), pair.getRight());
            results.add(result);

//            if (!result.getTrams().isEmpty()) {
//                results.add(result);
//            }
        });
        logger.info(format("Found %s pairs with trams", results.size()));
        return results;
    }

    public TramPosition findBetween(Station start, Station neighbour) {
        int cost = adjacenyRepository.getAdjacent(start, neighbour);
        if (cost<0) {
            logger.info(format("Not adjacent %s to %s", start, neighbour));
            return new TramPosition(start, neighbour, Collections.emptySet());
        }

        List<Route> routesBetween = routeReachable.getRoutesFromStartToNeighbour(start, neighbour.getId());

        // get departure info at neighbouring station for relevant routes
        Set<StationDepartureInfo> departureInfos = new HashSet<>();
        routesBetween.forEach(route -> {
            List<Platform> platforms = neighbour.getPlatformsForRoute(route);
            platforms.forEach(platform -> departureInfos.add(liveDataSource.departuresFor(platform)));
        });

        if (departureInfos.isEmpty()) {
            logger.warn("Unable to find departure information for " + neighbour.getPlatforms());
            return new TramPosition(start, neighbour, Collections.emptySet());
        }

        Set<DueTram> dueTrams = departureInfos.stream().
                map(mapEntry -> mapEntry.getDueTramsWithinWindow(cost)).
                flatMap(Collection::stream).
                collect(Collectors.toSet());
        logger.info(format("Found %s trams between %s and %s",dueTrams.size(), start, neighbour));
        return new TramPosition(start, neighbour, dueTrams);
    }
}