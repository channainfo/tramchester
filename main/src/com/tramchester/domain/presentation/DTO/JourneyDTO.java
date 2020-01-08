package com.tramchester.domain.presentation.DTO;


import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.tramchester.domain.TramTime;
import com.tramchester.mappers.serialisation.TramTimeJsonDeserializer;
import com.tramchester.mappers.serialisation.TramTimeJsonSerializer;

import java.util.List;

public class JourneyDTO implements Comparable<JourneyDTO> {
    @Override
    public String toString() {
        return "JourneyDTO{" +
                "begin=" + begin +
                ", end=" + end +
                ", stages=" + stages +
                ", expectedArrivalTime=" + expectedArrivalTime +
                ", firstDepartureTime=" + firstDepartureTime +
                ", dueTram='" + dueTram + '\'' +
                ", isDirect=" + isDirect +
                ", changeStations=" + changeStations +
                '}';
    }

    private LocationDTO begin;
    private LocationDTO end;
    private List<StageDTO> stages;
    private TramTime expectedArrivalTime;
    private TramTime firstDepartureTime;
    private String dueTram;
    private boolean isDirect;
    private List<String> changeStations;

    public JourneyDTO() {
        // Deserialization
    }

    public JourneyDTO(LocationDTO begin, LocationDTO end, List<StageDTO> stages,
                      TramTime expectedArrivalTime, TramTime firstDepartureTime,
                      boolean isDirect, List<String> changeStations) {
        this.begin = begin;
        this.end = end;
        this.stages = stages;
        this.expectedArrivalTime = expectedArrivalTime;
        this.firstDepartureTime = firstDepartureTime;
        this.isDirect = isDirect;
        this.changeStations = changeStations;
    }

    public List<StageDTO> getStages() {
        return stages;
    }

    @JsonSerialize(using = TramTimeJsonSerializer.class)
    @JsonDeserialize(using = TramTimeJsonDeserializer.class)
    public TramTime getFirstDepartureTime() {
        return firstDepartureTime;
    }

    @JsonSerialize(using = TramTimeJsonSerializer.class)
    @JsonDeserialize(using = TramTimeJsonDeserializer.class)
    public TramTime getExpectedArrivalTime() {
        return expectedArrivalTime;
    }

    public LocationDTO getBegin() {
        return begin;
    }

    public LocationDTO getEnd() {
        return end;
    }

    @Override
    public int compareTo(JourneyDTO other) {
        // arrival first
        int compare = checkArrival(other);
        // then departure time
        if (compare==0) {
            compare = checkDeparture(other);
        }
        // then number of stages
        if (compare==0) {
            // if arrival times match, put journeys with fewer stages first
            if (this.stages.size()<other.stages.size()) {
                compare = -1;
            } else if (other.stages.size()>stages.size()) {
                compare = 1;
            }
        }
        return compare;
    }

    private int checkDeparture(JourneyDTO other) {
        return getFirstDepartureTime().compareTo(other.getFirstDepartureTime());
    }

    private int checkArrival(JourneyDTO other) {
        return getExpectedArrivalTime().compareTo(other.getExpectedArrivalTime());
    }

    public String getDueTram() {
        return dueTram;
    }

    public void setDueTram(String dueTram) {
        this.dueTram = dueTram;
    }

    public boolean getIsDirect() {
        return isDirect;
    }

    public List<String> getChangeStations() {
        return changeStations;
    }
}
