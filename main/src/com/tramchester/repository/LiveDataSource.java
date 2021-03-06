package com.tramchester.repository;

import com.tramchester.domain.HasId;
import com.tramchester.domain.places.Station;
import com.tramchester.domain.time.TramServiceDate;
import com.tramchester.domain.time.TramTime;
import com.tramchester.domain.liveUpdates.DueTram;
import com.tramchester.domain.liveUpdates.StationDepartureInfo;

import java.util.List;
import java.util.Optional;

public interface LiveDataSource {
    Optional<StationDepartureInfo> departuresFor(HasId platform, TramServiceDate tramServiceDate, TramTime queryTime);
    List<StationDepartureInfo> departuresFor(Station station, TramServiceDate tramServiceDate, TramTime queryTime);
    List<DueTram> dueTramsFor(Station station, TramServiceDate tramServiceDate, TramTime queryTime);
}
