package com.tramchester.repository;

import com.tramchester.domain.places.Station;
import com.tramchester.domain.input.StopCall;
import com.tramchester.domain.time.TramTime;
import com.tramchester.domain.input.StopCalls;
import com.tramchester.domain.input.Trip;
import org.apache.commons.lang3.tuple.Pair;
import org.picocontainer.Disposable;
import org.picocontainer.Startable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class StationAdjacenyRepository implements Startable, Disposable {
    private static final Logger logger = LoggerFactory.getLogger(StationAdjacenyRepository.class);

    private final Map<Pair<Station,Station>, Integer> matrix;
    private final TransportDataSource transportDataSource;

    public StationAdjacenyRepository(TransportDataSource transportDataSource) {
        this.transportDataSource = transportDataSource;
        matrix = new HashMap<>();
    }

    @Override
    public void start() {
        logger.info("Build adjacency matrix");
        Collection<Trip> trips = transportDataSource.getTrips();
        trips.forEach(trip -> {
            StopCalls stops = trip.getStops();
            for (int i = 0; i < stops.size() - 1; i++) {
                StopCall currentStop = stops.get(i);
                StopCall nextStop = stops.get(i + 1);
                Pair<Station, Station> pair = formId(currentStop.getStation(), nextStop.getStation());
                if (!matrix.containsKey(pair)) {
                    int cost = TramTime.diffenceAsMinutes(currentStop.getDepartureTime(), nextStop.getArrivalTime());
                    matrix.put(pair, cost);
                }
            }
        });
        logger.info("Finished building adjacency matrix");
    }

    @Override
    public void stop() {

    }

    @Override
    public void dispose() {
        matrix.clear();
    }

    private Pair<Station,Station> formId(Station first, Station second) {
        return Pair.of(first,second);
    }

    public int getAdjacent(Station firstStation, Station secondStation) {
        Pair<Station, Station> id = formId(firstStation, secondStation);
        if (matrix.containsKey(id)) {
            return matrix.get(id);
        }
        return -1;
    }

    public Set<Pair<Station, Station>> getStationParis() {
        return matrix.keySet();
    }

}
