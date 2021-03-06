package com.tramchester.dataimport.data;

import com.tramchester.domain.time.TramTime;

import java.util.Optional;

public class StopTimeData {
    private final String tripId;
    private final TramTime arrivalTime ;
    private final TramTime departureTime;
    private final String stopId;
    private final String stopSequence;
    private final String pickupType;
    private final String dropOffType;

    public static final String COMMA = ",";


    public StopTimeData(String tripId, TramTime arrivalTime, TramTime departureTime, String stopId,
                        String stopSequence, String pickupType, String dropOffType) {
        this.tripId = tripId;
        this.stopId = stopId;

        this.arrivalTime = arrivalTime;
        this.departureTime = departureTime;
        this.stopSequence = stopSequence;
        this.pickupType = pickupType;
        this.dropOffType = dropOffType;
    }

    @Override
    public String toString() {
        return "StopTimeData{" +
                "tripId='" + tripId + '\'' +
                ", arrivalTime=" + arrivalTime +
                ", departureTime=" + departureTime +
                ", stopId='" + stopId + '\'' +
                ", stopSequence='" + stopSequence + '\'' +
                ", pickupType='" + pickupType + '\'' +
                ", dropOffType='" + dropOffType + '\'' +
                '}';
    }

    public String getTripId() {
        return tripId;
    }

    public TramTime getArrivalTime() {
        return arrivalTime;
    }

    public TramTime getDepartureTime() {
        return departureTime;
    }

    public String getStopId() {
        return stopId;
    }

    public String getStopSequence() {
        return stopSequence;
    }

    public String getPickupType() {
        return pickupType;
    }

    public String getDropOffType() {
        return dropOffType;
    }

    public String asOutputLine() {
        return tripId+ COMMA +
                arrivalTime.tramDataFormat()+ COMMA +
                departureTime.tramDataFormat()+ COMMA +
                stopId+ COMMA +
                stopSequence + COMMA +
                pickupType+ COMMA +
                dropOffType;
    }

}
