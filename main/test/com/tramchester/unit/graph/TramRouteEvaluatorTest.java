package com.tramchester.unit.graph;

import com.tramchester.domain.time.ProvidesLocalNow;
import com.tramchester.config.TramchesterConfig;
import com.tramchester.domain.exceptions.TramchesterException;
import com.tramchester.domain.time.TramTime;
import com.tramchester.graph.*;
import com.tramchester.graph.search.*;
import com.tramchester.graph.states.NotStartedState;
import com.tramchester.testSupport.TestEnv;
import org.easymock.EasyMock;
import org.easymock.EasyMockSupport;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.traversal.BranchState;
import org.neo4j.graphdb.traversal.Evaluation;

import java.util.Arrays;
import java.util.List;

import static com.tramchester.graph.TransportRelationshipTypes.WALKS_TO;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class TramRouteEvaluatorTest extends EasyMockSupport {

    private final List<String> destinationStationIds = Arrays.asList("destinationStationId");
    private ServiceHeuristics serviceHeuristics;
    private CachedNodeOperations nodeOperations;
    private Path path;
    private Node node;
    private NodeIdLabelMap nodeIdLabelMap;
    private ServiceReasons reasons;
    private TramchesterConfig config;

    @Before
    public void onceBeforeEachTestRuns() {
        nodeIdLabelMap = createMock(NodeIdLabelMap.class);
        nodeOperations = new CachedNodeOperations(nodeIdLabelMap);
        ProvidesLocalNow providesLocalNow = new ProvidesLocalNow();
        reasons = new ServiceReasons(providesLocalNow);
        config = TestEnv.GET();

        serviceHeuristics = createMock(ServiceHeuristics.class);
        path = createMock(Path.class);
        node = createMock(Node.class);

        EasyMock.expect(path.endNode()).andReturn(node);
        EasyMock.expect(node.getId()).andStubReturn(42L);
    }

    @NotNull
    private TramRouteEvaluator getEvaluator(long destinationNodeId) {
        return new TramRouteEvaluator(serviceHeuristics, nodeOperations, destinationNodeId, reasons, config);
    }

    @Test
    public void shouldMatchDestination() {
        long destinationNodeId = 42;
        TramRouteEvaluator evaluator = getEvaluator(destinationNodeId);

        BranchState<JourneyState> state = new TestBranchState();

        TramTime time = TramTime.of(8, 15);
        NotStartedState traversalState = new NotStartedState(nodeOperations, 88L, destinationStationIds, config);
        state.setState(new JourneyState(time, traversalState));

        replayAll();
        Evaluation result = evaluator.evaluate(path, state);
        assertEquals(Evaluation.INCLUDE_AND_PRUNE, result);
        verifyAll();
    }

    @Test
    public void shouldPruneIfTooLong() {
        TramRouteEvaluator evaluator = getEvaluator(88L);
        EasyMock.expect(serviceHeuristics.getMaxPathLength()).andStubReturn(200);

        BranchState<JourneyState> state = new TestBranchState();
        TramTime time = TramTime.of(8, 15);
        NotStartedState traversalState = new NotStartedState(nodeOperations, 88L, destinationStationIds, config);
        state.setState(new JourneyState(time, traversalState));

        EasyMock.expect(path.length()).andReturn(201);

        replayAll();
        Evaluation result = evaluator.evaluate(path, state);
        assertEquals(Evaluation.EXCLUDE_AND_PRUNE, result);
        verifyAll();
    }

    @Test
    public void shouldExcludeIfServiceNotRunningToday() {
        TramRouteEvaluator evaluator = getEvaluator(88L);
        EasyMock.expect(serviceHeuristics.getMaxPathLength()).andStubReturn(400);
        EasyMock.expect(serviceHeuristics.checkNumberChanges(0, path)).andStubReturn(ServiceReason.IsValid(path));

        EasyMock.expect(path.length()).andReturn(50);
        BranchState<JourneyState> state = new TestBranchState();
        EasyMock.expect(nodeIdLabelMap.has(TransportGraphBuilder.Labels.ROUTE_STATION, 42)).andReturn(false);
        EasyMock.expect(nodeIdLabelMap.has(TransportGraphBuilder.Labels.SERVICE, 42)).andStubReturn(true);

        EasyMock.expect(serviceHeuristics.checkServiceDate(node,path)).
                andReturn(ServiceReason.DoesNotRunOnQueryDate("not running", path));

        TramTime time = TramTime.of(8, 15);
        NotStartedState traversalState = new NotStartedState(nodeOperations, 88L, destinationStationIds, config);
        state.setState(new JourneyState(time, traversalState));

        replayAll();
        Evaluation result = evaluator.evaluate(path, state);
        assertEquals(Evaluation.EXCLUDE_AND_PRUNE, result);
        verifyAll();
    }

    @Test
    public void shouldExcludeIfUnreachableNode() throws TramchesterException {
        TramRouteEvaluator evaluator = getEvaluator(88L);
        BranchState<JourneyState> state = new TestBranchState();
        EasyMock.expect(serviceHeuristics.getMaxPathLength()).andStubReturn(400);
        EasyMock.expect(serviceHeuristics.checkNumberChanges(0, path)).andStubReturn(ServiceReason.IsValid(path));

        TramTime time = TramTime.of(8, 15);
        NotStartedState traversalState = new NotStartedState(nodeOperations, 88L, destinationStationIds, config);
        JourneyState journeyState = new JourneyState(time, traversalState);
        journeyState.boardTram();
        state.setState(journeyState);

        EasyMock.expect(path.length()).andReturn(50);
        EasyMock.expect(nodeIdLabelMap.has(TransportGraphBuilder.Labels.ROUTE_STATION, 42)).andReturn(true);

        EasyMock.expect(serviceHeuristics.canReachDestination(node, path)).
                andReturn(ServiceReason.StationNotReachable(path));

        replayAll();
        Evaluation result = evaluator.evaluate(path, state);
        assertEquals(Evaluation.EXCLUDE_AND_PRUNE, result);
        verifyAll();
    }

    @Test
    public void shouldIncludeIfNotOnTramNode() throws TramchesterException {
        TramRouteEvaluator evaluator = getEvaluator(88L);
        BranchState<JourneyState> state = new TestBranchState();
        EasyMock.expect(serviceHeuristics.getMaxPathLength()).andStubReturn(400);
        EasyMock.expect(serviceHeuristics.checkNumberChanges(0, path)).andStubReturn(ServiceReason.IsValid(path));

        TramTime time = TramTime.of(8, 15);
        NotStartedState traversalState = new NotStartedState(nodeOperations, 88L, destinationStationIds, config);
        JourneyState journeyState = new JourneyState(time, traversalState);
        journeyState.boardBus();
        state.setState(journeyState);

        Relationship relationship = createMock(Relationship.class);

        EasyMock.expect(path.length()).andReturn(50);
        EasyMock.expect(nodeIdLabelMap.has(TransportGraphBuilder.Labels.ROUTE_STATION, 42)).andReturn(true);
        EasyMock.expect(nodeIdLabelMap.has(TransportGraphBuilder.Labels.SERVICE, 42)).andReturn(false);
        EasyMock.expect(relationship.isType(WALKS_TO)).andReturn(true);
        EasyMock.expect(nodeIdLabelMap.has(TransportGraphBuilder.Labels.MINUTE, 42)).andReturn(false);
        EasyMock.expect(nodeIdLabelMap.has(TransportGraphBuilder.Labels.HOUR, 42)).andReturn(false);
        
        EasyMock.expect(path.lastRelationship()).andReturn(relationship);

        EasyMock.expect(serviceHeuristics.canReachDestination(node, path)).
                andReturn(ServiceReason.IsValid(path));

        replayAll();
        Evaluation result = evaluator.evaluate(path, state);
        assertEquals(Evaluation.INCLUDE_AND_CONTINUE, result);
        verifyAll();
    }

    @Test
    public void shouldIncludeIfWalking() {
        TramRouteEvaluator evaluator = getEvaluator(88L);
        BranchState<JourneyState> state = new TestBranchState();
        EasyMock.expect(serviceHeuristics.getMaxPathLength()).andStubReturn(400);
        EasyMock.expect(serviceHeuristics.checkNumberChanges(0, path)).andStubReturn(ServiceReason.IsValid(path));

        EasyMock.expect(path.length()).andReturn(50);
        EasyMock.expect(nodeIdLabelMap.has(TransportGraphBuilder.Labels.SERVICE, 42)).andReturn(false);
        EasyMock.expect(nodeIdLabelMap.has(TransportGraphBuilder.Labels.ROUTE_STATION, 42)).andReturn(false);
        EasyMock.expect(nodeIdLabelMap.has(TransportGraphBuilder.Labels.MINUTE, 42)).andReturn(false);
        EasyMock.expect(nodeIdLabelMap.has(TransportGraphBuilder.Labels.HOUR, 42)).andReturn(false);

        Relationship relationship = createMock(Relationship.class);
        EasyMock.expect(relationship.isType(WALKS_TO)).andReturn(true);
        EasyMock.expect(path.lastRelationship()).andReturn(relationship);

        TramTime time = TramTime.of(8, 15);
        NotStartedState traversalState = new NotStartedState(nodeOperations, 88L, destinationStationIds, config);
        state.setState(new JourneyState(time, traversalState));

        replayAll();
        Evaluation result = evaluator.evaluate(path, state);
        assertEquals(Evaluation.INCLUDE_AND_CONTINUE, result);
        verifyAll();
    }

    @Test
    public void shouldExcludeIfTakingTooLong() {
        TramRouteEvaluator evaluator = getEvaluator(88L);
        BranchState<JourneyState> state = new TestBranchState();

        EasyMock.expect(path.length()).andReturn(50);
        EasyMock.expect(nodeIdLabelMap.has(TransportGraphBuilder.Labels.SERVICE, 42)).andReturn(false);
        EasyMock.expect(nodeIdLabelMap.has(TransportGraphBuilder.Labels.ROUTE_STATION, 42)).andReturn(false);
        EasyMock.expect(serviceHeuristics.getMaxPathLength()).andStubReturn(400);
        EasyMock.expect(serviceHeuristics.checkNumberChanges(0, path)).andStubReturn(ServiceReason.IsValid(path));

        Relationship relationship = createMock(Relationship.class);
        EasyMock.expect(relationship.isType(WALKS_TO)).andReturn(false);
        EasyMock.expect(path.lastRelationship()).andReturn(relationship);

        TramTime time = TramTime.of(8, 15);
        EasyMock.expect(serviceHeuristics.journeyDurationUnderLimit(0,path)).andReturn(ServiceReason.TookTooLong(time, path));

        NotStartedState traversalState = new NotStartedState(nodeOperations, 88L, destinationStationIds, config);
        state.setState(new JourneyState(time, traversalState));

        replayAll();
        Evaluation result = evaluator.evaluate(path, state);
        assertEquals(Evaluation.EXCLUDE_AND_PRUNE, result);
        verifyAll();
    }

    @Test
    public void shouldExcludeIfOverLimitOnChanges() {
        TramRouteEvaluator evaluator = getEvaluator(88L);
        BranchState<JourneyState> state = new TestBranchState();

        EasyMock.expect(path.length()).andReturn(50);
        EasyMock.expect(serviceHeuristics.getMaxPathLength()).andStubReturn(400);

        TramTime time = TramTime.of(8, 15);
        EasyMock.expect(serviceHeuristics.checkNumberChanges(0, path)).andStubReturn(ServiceReason.TooManyChanges(path));

        NotStartedState traversalState = new NotStartedState(nodeOperations, 88L, destinationStationIds, config);
        state.setState(new JourneyState(time, traversalState));

        replayAll();
        Evaluation result = evaluator.evaluate(path, state);
        assertEquals(Evaluation.EXCLUDE_AND_PRUNE, result);
        verifyAll();
    }

    @Test
    public void shouldExcludeIfServiceNotRunning() {
        TramRouteEvaluator evaluator = getEvaluator(88L);
        BranchState<JourneyState> state = new TestBranchState();
        EasyMock.expect(serviceHeuristics.getMaxPathLength()).andStubReturn(400);
        EasyMock.expect(serviceHeuristics.checkNumberChanges(0, path)).andStubReturn(ServiceReason.IsValid(path));

        EasyMock.expect(path.length()).andReturn(50);
        EasyMock.expect(nodeIdLabelMap.has(TransportGraphBuilder.Labels.SERVICE, 42)).andReturn(true);
        EasyMock.expect(nodeIdLabelMap.has(TransportGraphBuilder.Labels.ROUTE_STATION, 42)).andReturn(false);

        Relationship relationship = createMock(Relationship.class);
        EasyMock.expect(relationship.isType(WALKS_TO)).andReturn(false);
        EasyMock.expect(path.lastRelationship()).andReturn(relationship);

        TramTime time = TramTime.of(8, 15);
        NotStartedState traversalState = new NotStartedState(nodeOperations, 88L, destinationStationIds, config);

        state.setState(new JourneyState(time, traversalState));
        EasyMock.expect(serviceHeuristics.checkServiceDate(node, path)).andReturn(ServiceReason.IsValid(path));
        EasyMock.expect(serviceHeuristics.journeyDurationUnderLimit(0,path)).andReturn(ServiceReason.IsValid(path));
        EasyMock.expect(serviceHeuristics.checkServiceTime(path, node, time)).
                andReturn(ServiceReason.DoesNotOperateOnTime(time, path));

        replayAll();
        Evaluation result = evaluator.evaluate(path, state);
        assertEquals(Evaluation.EXCLUDE_AND_PRUNE, result);
        verifyAll();
    }

    @Test
    public void shouldExcludeIfServiceNotCorrectHour() {
        TramRouteEvaluator evaluator = getEvaluator(88L);
        BranchState<JourneyState> state = new TestBranchState();
        EasyMock.expect(serviceHeuristics.getMaxPathLength()).andStubReturn(400);
        EasyMock.expect(serviceHeuristics.checkNumberChanges(0, path)).andStubReturn(ServiceReason.IsValid(path));

        EasyMock.expect(path.length()).andReturn(50);
        EasyMock.expect(nodeIdLabelMap.has(TransportGraphBuilder.Labels.SERVICE, 42)).andReturn(false);
        EasyMock.expect(nodeIdLabelMap.has(TransportGraphBuilder.Labels.ROUTE_STATION, 42)).andReturn(false);
        EasyMock.expect(nodeIdLabelMap.has(TransportGraphBuilder.Labels.HOUR, 42)).andReturn(true);

        EasyMock.expect(node.getProperty("hour")).andReturn(8);

        Relationship relationship = createMock(Relationship.class);
        EasyMock.expect(relationship.isType(WALKS_TO)).andReturn(false);
        EasyMock.expect(path.lastRelationship()).andReturn(relationship);

        NotStartedState traversalState = new NotStartedState(nodeOperations, 88L, destinationStationIds, config);
        TramTime time = TramTime.of(8, 15);

        state.setState(new JourneyState(time, traversalState));
        EasyMock.expect(serviceHeuristics.journeyDurationUnderLimit(0,path)).andReturn(ServiceReason.IsValid(path));
        EasyMock.expect(serviceHeuristics.interestedInHour(path, 8, time)).
                andReturn(ServiceReason.DoesNotOperateOnTime(time, path));

        replayAll();
        Evaluation result = evaluator.evaluate(path, state);
        assertEquals(Evaluation.EXCLUDE_AND_PRUNE, result);
        verifyAll();
    }

    @Test
    public void shouldExcludeIfServiceNotCorrectMinute() {
        TramRouteEvaluator evaluator = getEvaluator(88L);
        BranchState<JourneyState> state = new TestBranchState();
        EasyMock.expect(serviceHeuristics.getMaxPathLength()).andStubReturn(400);
        EasyMock.expect(serviceHeuristics.checkNumberChanges(0, path)).andStubReturn(ServiceReason.IsValid(path));

        EasyMock.expect(path.length()).andReturn(50);
        EasyMock.expect(nodeIdLabelMap.has(TransportGraphBuilder.Labels.SERVICE, 42)).andReturn(false);
        EasyMock.expect(nodeIdLabelMap.has(TransportGraphBuilder.Labels.ROUTE_STATION, 42)).andReturn(false);
        EasyMock.expect(nodeIdLabelMap.has(TransportGraphBuilder.Labels.HOUR, 42)).andReturn(false);
        EasyMock.expect(nodeIdLabelMap.has(TransportGraphBuilder.Labels.MINUTE, 42)).andReturn(true);

        Relationship relationship = createMock(Relationship.class);
        EasyMock.expect(relationship.isType(WALKS_TO)).andReturn(false);
        EasyMock.expect(path.lastRelationship()).andReturn(relationship);

        NotStartedState traversalState = new NotStartedState(nodeOperations, 88L, destinationStationIds, config);
        TramTime time = TramTime.of(8, 15);
        state.setState(new JourneyState(time, traversalState));

        //TramTime tramTime = TramTime.of(time);
        EasyMock.expect(serviceHeuristics.journeyDurationUnderLimit(0 ,path)).andReturn(ServiceReason.IsValid(path));
        EasyMock.expect(serviceHeuristics.checkTime(path, node, time)).
                andReturn(ServiceReason.DoesNotOperateOnTime(time, path));

        replayAll();
        Evaluation result = evaluator.evaluate(path, state);
        assertEquals(Evaluation.EXCLUDE_AND_PRUNE, result);
        verifyAll();
    }

    @Test
    public void shouldIncludeIfMatchesNoRules() {
        TramRouteEvaluator evaluator = getEvaluator(88L);
        BranchState<JourneyState> state = new TestBranchState();
        EasyMock.expect(serviceHeuristics.getMaxPathLength()).andStubReturn(400);
        EasyMock.expect(serviceHeuristics.checkNumberChanges(0, path)).andStubReturn(ServiceReason.IsValid(path));

        EasyMock.expect(path.length()).andReturn(50);
        EasyMock.expect(nodeIdLabelMap.has(TransportGraphBuilder.Labels.SERVICE, 42)).andReturn(false);
        EasyMock.expect(nodeIdLabelMap.has(TransportGraphBuilder.Labels.ROUTE_STATION, 42)).andReturn(false);
        EasyMock.expect(nodeIdLabelMap.has(TransportGraphBuilder.Labels.HOUR, 42)).andStubReturn(false);
        EasyMock.expect(nodeIdLabelMap.has(TransportGraphBuilder.Labels.MINUTE, 42)).andStubReturn(false);
//        EasyMock.expect(nodeIdLabelMap.getLabel(42)).andStubReturn(TransportGraphBuilder.Labels.QUERY_NODE);

        Relationship relationship = createMock(Relationship.class);
        EasyMock.expect(relationship.isType(WALKS_TO)).andReturn(false);
        EasyMock.expect(path.lastRelationship()).andReturn(relationship);

        TramTime time = TramTime.of(8, 15);
        NotStartedState traversalState = new NotStartedState(nodeOperations, 88L, destinationStationIds, config);
        state.setState(new JourneyState(time, traversalState));

        EasyMock.expect(serviceHeuristics.journeyDurationUnderLimit(0,path)).andReturn(ServiceReason.IsValid(path));

        replayAll();
        Evaluation result = evaluator.evaluate(path, state);
        assertEquals(Evaluation.INCLUDE_AND_CONTINUE, result);
        verifyAll();
    }

    private class TestBranchState implements BranchState<JourneyState> {
        private JourneyState journeyState;

        @Override
        public JourneyState getState() {
            return journeyState;
        }

        @Override
        public void setState(JourneyState journeyState) {
            this.journeyState = journeyState;
        }
    }

}
