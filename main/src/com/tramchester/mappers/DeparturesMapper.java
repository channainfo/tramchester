package com.tramchester.mappers;

import com.tramchester.domain.places.Station;
import com.tramchester.domain.liveUpdates.DueTram;
import com.tramchester.domain.presentation.DTO.DepartureDTO;

import java.util.*;
import java.util.stream.Collectors;

public class DeparturesMapper {
    public static String DUE = "Due";

    public DeparturesMapper() {
    }

    public Set<DepartureDTO> mapToDTO(Station station, Collection<DueTram> dueTrams) {
        return dueTrams.stream().
                    map(dueTram -> new DepartureDTO(station.getName(),dueTram))
                    .collect(Collectors.toSet());
    }
}
