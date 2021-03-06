package com.tramchester.dataimport.parsers;

import com.tramchester.dataimport.data.RouteData;
import com.tramchester.dataimport.datacleanse.DataCleanser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

import static com.tramchester.dataimport.data.RouteData.BUS_TYPE;
import static com.tramchester.dataimport.data.RouteData.TRAM_TYPE;
import static java.lang.String.format;

public class RouteDataMapper implements CSVEntryMapper<RouteData> {
    private static final Logger logger = LoggerFactory.getLogger(RouteDataMapper.class);

    private final Set<String> agencies;
    private boolean includeAll;

    public RouteDataMapper(Set<String> agencies) {
        this.agencies = agencies;
        if (agencies.size()==0) {
            logger.warn("Loading all agencies and routes");
            includeAll = true;
        }
    }

    public RouteData parseEntry(CSVRecord data) {
        String id = data.get(0);
        String agency = data.get(1);
        String shortName = data.get(2);
        String longName = data.get(3);
        String routeType = data.get(4);

        if (! ( BUS_TYPE.equals(routeType) || TRAM_TYPE.equals(routeType))) {
            throw new RuntimeException(format("Unexepcted tram type %s from %s ", routeType, data));
        }

        return new RouteData(id, agency, shortName, longName, routeType);
    }

    @Override
    public boolean shouldInclude(CSVRecord data) {
        if (includeAll) {
            return true;
        }
        return agencies.contains(data.get(1));
    }
}