package com.tramchester.router;

import com.tramchester.config.TramchesterConfig;
import com.tramchester.domain.Journey;
import com.tramchester.domain.places.Location;
import com.tramchester.domain.places.MyLocationFactory;
import com.tramchester.domain.places.PostcodeLocation;
import com.tramchester.domain.places.Station;
import com.tramchester.domain.presentation.DTO.JourneyDTO;
import com.tramchester.domain.presentation.DTO.JourneyPlanRepresentation;
import com.tramchester.domain.presentation.LatLong;
import com.tramchester.domain.presentation.Note;
import com.tramchester.domain.presentation.ProvidesNotes;
import com.tramchester.domain.time.TramServiceDate;
import com.tramchester.graph.search.JourneyRequest;
import com.tramchester.graph.search.RouteCalculator;
import com.tramchester.graph.search.RouteCalculatorArriveBy;
import com.tramchester.mappers.JourneysMapper;
import com.tramchester.repository.PostcodeRepository;
import com.tramchester.repository.TransportData;
import com.tramchester.resources.LocationJourneyPlanner;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.SortedSet;
import java.util.stream.Stream;

import static java.lang.String.format;

public class ProcessPlanRequest {
    private static final Logger logger = LoggerFactory.getLogger(ProcessPlanRequest.class);

    private final TramchesterConfig config;
    private final LocationJourneyPlanner locToLocPlanner;
    private final RouteCalculator routeCalculator;
    private final RouteCalculatorArriveBy routeCalculatorArriveBy;
    private final JourneysMapper journeysMapper;
    private final ProvidesNotes providesNotes;
    private final TransportData transportData;
    private final PostcodeRepository postcodeRepository;

    public ProcessPlanRequest(TramchesterConfig config, LocationJourneyPlanner locToLocPlanner, RouteCalculator routeCalculator,
                              RouteCalculatorArriveBy routeCalculatorArriveBy, JourneysMapper journeysMapper,
                              ProvidesNotes providesNotes, TransportData transportData, PostcodeRepository postcodeRepository) {
        this.config = config;
        this.locToLocPlanner = locToLocPlanner;

        this.routeCalculator = routeCalculator;
        this.routeCalculatorArriveBy = routeCalculatorArriveBy;
        this.journeysMapper = journeysMapper;
        this.providesNotes = providesNotes;
        this.transportData = transportData;
        this.postcodeRepository = postcodeRepository;
    }

    public JourneyPlanRepresentation directRequest(String startId, String endId, JourneyRequest journeyRequest,
                                                   String lat, String lon) {
        JourneyPlanRepresentation planRepresentation;
        if (isFromUserLocation(startId)) {
            LatLong latLong = decodeLatLong(lat, lon);
            planRepresentation = startsWithPosition(latLong, endId, journeyRequest);
        } else if (isFromUserLocation(endId)) {
            LatLong latLong = decodeLatLong(lat, lon);
            planRepresentation = endsWithPosition(startId, latLong, journeyRequest);
        } else {
            planRepresentation = createJourneyPlan(startId, endId, journeyRequest);
        }
        return planRepresentation;
    }

    private JourneyPlanRepresentation createJourneyPlan(String startId, String endId, JourneyRequest journeyRequest) {
        logger.info(format("Plan journey from %s to %s on %s", startId, endId, journeyRequest));

        boolean firstIsStation = startsWithDigit(startId);
        boolean secondIsStation = startsWithDigit(endId);

        if (firstIsStation && secondIsStation) {
            Station start = getStation(startId, "start");
            Station dest = getStation(endId, "end");
            return stationToStation(start, dest, journeyRequest);
        }

        // Station -> Postcode
        if (firstIsStation) {
            PostcodeLocation dest = getPostcode(endId, "end");
            return endsWithPosition(startId, dest.getLatLong(), journeyRequest);
        }

        // Postcode -> Station
        if (secondIsStation) {
            PostcodeLocation start = getPostcode(startId, "start");
            return startsWithPosition(start.getLatLong(), endId, journeyRequest);
        }

        return postcodeToPostcode(startId, endId, journeyRequest);
    }

    @NotNull
    private JourneyPlanRepresentation postcodeToPostcode(String startId, String endId, JourneyRequest journeyRequest) {
        Location start = getPostcode(startId, "start");
        Location dest = getPostcode(endId, "end");
        Stream<Journey> journeys =  locToLocPlanner.quickestRouteForLocation(start.getLatLong(), dest.getLatLong(), journeyRequest);
        JourneyPlanRepresentation plan = createPlan(journeyRequest.getDate(), journeys);
        journeys.close();
        return plan;
    }

    private JourneyPlanRepresentation startsWithPosition(LatLong latLong, String destId,
                                                         JourneyRequest journeyRequest) {
        logger.info(format("Plan journey from %s to %s on %s", latLong, destId, journeyRequest));

        Station dest = getStation(destId, "end");

        Stream<Journey> journeys = locToLocPlanner.quickestRouteForLocation(latLong, dest, journeyRequest);
        JourneyPlanRepresentation plan = createPlan(journeyRequest.getDate(), journeys);
        journeys.close();
        return plan;
    }

    private JourneyPlanRepresentation endsWithPosition(String startId, LatLong latLong, JourneyRequest journeyRequest) {
        logger.info(format("Plan journey from %s to %s on %s", startId, latLong, journeyRequest));

        Station start = getStation(startId, "start");

        Stream<Journey> journeys = locToLocPlanner.quickestRouteForLocation(start, latLong, journeyRequest);
        JourneyPlanRepresentation plan = createPlan(journeyRequest.getDate(), journeys);
        journeys.close();
        return plan;
    }

    private JourneyPlanRepresentation stationToStation(Station start, Station dest, JourneyRequest journeyRequest) {
        Stream<Journey> journeys;
        if (journeyRequest.getArriveBy()) {
            journeys = routeCalculatorArriveBy.calculateRoute(start, dest, journeyRequest);
        } else {
            journeys = routeCalculator.calculateRoute(start, dest, journeyRequest);
        }
        // ASSUME: Limit here rely's on search giving lowest cost routes first
        JourneyPlanRepresentation journeyPlanRepresentation = createPlan(journeyRequest.getDate(),
                journeys.limit(config.getMaxNumResults()));
        journeys.close();
        return journeyPlanRepresentation;
    }

    private PostcodeLocation getPostcode(String locationId, String diagnostic) {
        if (!postcodeRepository.hasPostcode(locationId)) {
            String msg = "Unable to find " + diagnostic +" postcode from:  "+ locationId;
            logger.warn(msg);
            throw new RuntimeException(msg);
        }
        return postcodeRepository.getPostcode(locationId);
    }

    private Station getStation(String locationId, String diagnostic) {
        if (!transportData.hasStationId(locationId)) {
            String msg = "Unable to find " + diagnostic + " station from id: "+ locationId;
            logger.warn(msg);
            throw new RuntimeException(msg);
        }
        return transportData.getStation(locationId);
    }

    private boolean startsWithDigit(String startId) {
        return Character.isDigit(startId.charAt(0));
    }

    private JourneyPlanRepresentation createPlan(TramServiceDate queryDate, Stream<Journey> journeys) {
        SortedSet<JourneyDTO> journeyDTOs = journeysMapper.createJourneyDTOs(journeys, queryDate, config.getMaxNumResults());
        List<Note> notes = providesNotes.createNotesForJourneys(journeyDTOs, queryDate);
        return new JourneyPlanRepresentation(journeyDTOs, notes);
    }


    private boolean isFromUserLocation(String startId) {
        return MyLocationFactory.MY_LOCATION_PLACEHOLDER_ID.equals(startId);
    }

    private LatLong decodeLatLong(String lat, String lon) {
        double latitude = Double.parseDouble(lat);
        double longitude = Double.parseDouble(lon);
        return new LatLong(latitude,longitude);
    }

}
