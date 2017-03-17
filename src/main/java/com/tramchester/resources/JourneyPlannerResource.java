package com.tramchester.resources;

import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tramchester.config.TramchesterConfig;
import com.tramchester.domain.CreateQueryTimes;
import com.tramchester.domain.RawJourney;
import com.tramchester.domain.TramServiceDate;
import com.tramchester.domain.UpdateRecentJourneys;
import com.tramchester.domain.exceptions.TramchesterException;
import com.tramchester.domain.presentation.DTO.JourneyPlanRepresentation;
import com.tramchester.graph.RouteCalculator;
import com.tramchester.mappers.JourneyResponseMapper;
import com.tramchester.services.DateTimeService;
import io.dropwizard.jersey.caching.CacheControl;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.joda.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static java.lang.String.format;

@Api
@Path("/journey")
@Produces(MediaType.APPLICATION_JSON)
public class JourneyPlannerResource extends UsesRecentCookie {

    private static final Logger logger = LoggerFactory.getLogger(JourneyPlannerResource.class);
    private final TramchesterConfig config;
    private LocationToLocationJourneyPlanner locToLocPlanner;
    private RouteCalculator routeCalculator;
    private DateTimeService dateTimeService;
    private JourneyResponseMapper journeyResponseMapper;
    private CreateQueryTimes createQueryTimes;

    public JourneyPlannerResource(RouteCalculator routeCalculator, DateTimeService dateTimeService,
                                  JourneyResponseMapper journeyResponseMapper, TramchesterConfig config,
                                  LocationToLocationJourneyPlanner locToLocPlanner, CreateQueryTimes createQueryTimes,
                                  UpdateRecentJourneys updateRecentJourneys, ObjectMapper objectMapper) {
        super(updateRecentJourneys, objectMapper);
        this.routeCalculator = routeCalculator;
        this.dateTimeService = dateTimeService;
        this.journeyResponseMapper = journeyResponseMapper;
        this.config = config;
        this.locToLocPlanner = locToLocPlanner;
        this.createQueryTimes = createQueryTimes;
    }

    @GET
    @Timed
    @ApiOperation(value = "Find quickest route", response = JourneyPlanRepresentation.class)
    @CacheControl(maxAge = 1, maxAgeUnit = TimeUnit.DAYS)
    public Response quickestRoute(@QueryParam("start") String startId,
                                  @QueryParam("end") String endId,
                                  @QueryParam("departureTime") String departureTime,
                                  @QueryParam("departureDate") String departureDate,
                                  @CookieParam(StationResource.TRAMCHESTER_RECENT) Cookie cookie){
        logger.info(format("Plan journey from %s to %s at %s on %s", startId, endId,departureTime, departureDate));

        LocalDate date = new LocalDate(departureDate);
        TramServiceDate queryDate = new TramServiceDate(date);

        try {
            int minutesFromMidnight = dateTimeService.getMinutesFromMidnight(departureTime);
            JourneyPlanRepresentation planRepresentation = createJourneyPlan(startId, endId, queryDate, minutesFromMidnight);
            Response.ResponseBuilder responseBuilder = Response.ok(planRepresentation);
            responseBuilder.cookie(createRecentCookie(cookie, startId, endId));
            Response response = responseBuilder.build();
            return response;
        } catch (TramchesterException exception) {
            logger.error("Unable to plan journey",exception);
        } catch(Exception exception) {
            logger.error("Problem processing response", exception);
        }

        return Response.serverError().build();
    }

    public JourneyPlanRepresentation createJourneyPlan(String startId, String endId,
                                                       TramServiceDate queryDate, int initialQueryTime)
            throws TramchesterException {
        logger.info(format("Plan journey from %s to %s on %s %s at %s", startId, endId,queryDate.getDay(),
                queryDate,initialQueryTime));
        Set<RawJourney> journeys;
        List<Integer> queryTimes = createQueryTimes.generate(initialQueryTime);
        if (isFromMyLocation(startId)) {
            journeys = locToLocPlanner.quickestRouteForLocation(startId, endId, queryTimes, queryDate);
        } else {
            journeys = routeCalculator.calculateRoute(startId, endId, queryTimes, queryDate);
        }
        logger.info("number of journeys: " + journeys.size());
        return journeyResponseMapper.map(journeys, config.getTimeWindow(), queryDate);
    }

}
