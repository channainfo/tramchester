package com.tramchester.unit.domain;

import com.google.common.collect.Sets;
import com.tramchester.domain.Timestamped;
import com.tramchester.domain.UpdateRecentJourneys;
import com.tramchester.domain.presentation.RecentJourneys;
import com.tramchester.domain.time.ProvidesLocalNow;
import com.tramchester.domain.time.ProvidesNow;
import com.tramchester.testSupport.Stations;
import com.tramchester.testSupport.TestEnv;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class UpdateRecentJourneysTest {

    private String altyId = Stations.Altrincham.getId();
    private UpdateRecentJourneys updater = new UpdateRecentJourneys(TestEnv.GET());
    private ProvidesNow providesNow = new ProvidesLocalNow();

    @Test
    public void shouldUpdateRecentFrom() {
        RecentJourneys recentJourneys = new RecentJourneys();
        recentJourneys.setTimestamps(ts("id1","id2"));

        RecentJourneys updated = updater.createNewJourneys(recentJourneys, providesNow, altyId);

        Set<Timestamped> from = updated.getRecentIds();
        assertEquals(3, from.size());
        assertTrue(from.containsAll(ts("id1","id2",altyId)));
    }

    @Test
    public void shouldNotAddIfPresent() {
        RecentJourneys recentJourneys = new RecentJourneys();
        recentJourneys.setTimestamps(ts("id1","id2","id3"));

        RecentJourneys updated = updater.createNewJourneys(recentJourneys, providesNow,"id2");
        Set<Timestamped> from = updated.getRecentIds();
        assertEquals(3, from.size());
        assertTrue(from.containsAll(ts("id1","id2","id3")));
    }

    @Test
    public void shouldRemoveOldestWhenLimitReached() throws InterruptedException {
        RecentJourneys recentJourneys = new RecentJourneys();
        recentJourneys.setTimestamps(Sets.newHashSet());
        RecentJourneys updated = updater.createNewJourneys(recentJourneys, providesNow,"id4");
        Thread.sleep(2);
        updated = updateWithPause(updated, "id3");
        updated = updateWithPause(updated, "id2");
        updated = updateWithPause(updated, "id1");

        Set<Timestamped> from = updated.getRecentIds();
        assertEquals(4, from.size());
        assertTrue(from.containsAll(ts("id1", "id2", "id3")));

        updated = updateWithPause(updated, "id5");
        from = updated.getRecentIds();
        assertEquals(5, from.size());
        assertTrue(from.containsAll(ts("id1", "id2", "id5")));
    }

    private RecentJourneys updateWithPause(RecentJourneys updated, String id1) throws InterruptedException {
        updated = updater.createNewJourneys(updated, providesNow, id1);
        Thread.sleep(2);
        return updated;
    }

    private Set<Timestamped> ts(String... ids) {
        Set<Timestamped> set = new HashSet<>();
        int count = 0;
        for (String id : ids) {
            set.add(new Timestamped(id, providesNow.getDateTime().plusSeconds(count++)));
        }
        return set;
    }
}
