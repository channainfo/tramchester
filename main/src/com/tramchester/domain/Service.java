package com.tramchester.domain;


import com.tramchester.domain.input.Trip;
import com.tramchester.domain.time.DaysOfWeek;
import com.tramchester.domain.time.TramServiceDate;
import com.tramchester.domain.time.TramTime;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Set;

import static com.tramchester.domain.time.DaysOfWeek.*;

public class Service implements HasId {

    private final String serviceId;
    private final Route route;
    private final Set<Trip> trips;

    private HashMap<DaysOfWeek, Boolean> days = new HashMap<>();
    private TramServiceDate startDate;
    private TramServiceDate endDate;

    private TramTime earliestDepart;
    private TramTime latestDepart;

    public Service(String serviceId, Route route) {
        this.serviceId = serviceId.intern();
        this.route = route;
        this.trips = new LinkedHashSet<>();
        earliestDepart = null;
        latestDepart = null;
    }

    public String getId() {
        return serviceId;
    }

    public Set<Trip> getTrips() {
        return trips;
    }

    public void addTrip(Trip trip) {
        trips.add(trip);
    }

    public void setDays(boolean monday, boolean tuesday, boolean wednesday, boolean thursday, boolean friday, boolean saturday, boolean sunday) {
        days.put(Monday, monday);
        days.put(Tuesday, tuesday);
        days.put(Wednesday, wednesday);
        days.put(Thursday, thursday);
        days.put(Friday, friday);
        days.put(Saturday, saturday);
        days.put(Sunday, sunday);
    }

    public HashMap<DaysOfWeek, Boolean> getDays() {
        return days;
    }

    @Override
    public String toString() {
        return "Service{" +
                "routeId='" + route.getId() + '\'' +
                ", serviceId='" + serviceId + '\'' +
                ", trips=" + HasId.asIds(trips) +
                ", days=" + days +
                ", startDate=" + startDate +
                ", endDate=" + endDate +
                '}';
    }

    public Route getRoute() {
        return route;
    }

    public String getRouteId() {
        return route.getId();
    }

    public void setServiceDateRange(LocalDate startDate, LocalDate endDate) {
        this.startDate = new TramServiceDate(startDate);
        this.endDate = new TramServiceDate(endDate);
    }

    public TramServiceDate getStartDate() {
        return startDate;
    }

    public TramServiceDate getEndDate() {
        return endDate;
    }

    public boolean isRunning() {
        return days.get(Monday) ||
                days.get(Tuesday) ||
                days.get(Wednesday) ||
                days.get(Thursday) ||
                days.get(Friday) ||
                days.get(Saturday) ||
                days.get(Sunday);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Service service = (Service) o;

        return !(serviceId != null ? !serviceId.equals(service.serviceId) : service.serviceId != null);
    }

    @Override
    public int hashCode() {
        return serviceId != null ? serviceId.hashCode() : 0;
    }

    public boolean operatesOn(LocalDate date) {
        return operatesOn(startDate, endDate, date);
    }

    private static boolean operatesOn(TramServiceDate startDate, TramServiceDate endDate, LocalDate date) {
        LocalDate begin = startDate.getDate();
        LocalDate end = endDate.getDate();
        if  (date.isAfter(begin) && date.isBefore(end)) {
            return true;
        }
        if (date.equals(begin) || date.equals(end)) {
            return true;
        }
        return false;
    }

    public TramTime earliestDepartTime() {
        if (earliestDepart!=null) {
            return earliestDepart;
        }

        trips.forEach(trip -> {
            TramTime departureTime = trip.earliestDepartTime();
            if (earliestDepart==null) {
                earliestDepart = departureTime;
            } else if (departureTime.isBefore(earliestDepart)) {
                earliestDepart = departureTime;
            }
        });
        return earliestDepart;
    }

    public TramTime latestDepartTime() {
        if (latestDepart!=null) {
            return latestDepart;
        }

        trips.forEach(trip -> {
            TramTime departureTime = trip.latestDepartTime();
            if (latestDepart==null) {
                latestDepart = departureTime;
            } else  if (departureTime.isAfter(latestDepart)) {
                latestDepart = departureTime;
            }
        });
        return latestDepart;
    }
}
