package com.tramchester.graph.search;

import com.tramchester.domain.time.TramTime;
import com.tramchester.graph.states.TraversalState;

public interface ImmutableJourneyState {
    TraversalState getTraversalState();
    TramTime getJourneyClock();
    boolean onTram();
    int getNumberChanges();
}
