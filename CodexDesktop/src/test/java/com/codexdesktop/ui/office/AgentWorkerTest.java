package com.codexdesktop.ui.office;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The office is a status display, so its movement rules are worth pinning down: an agent must end a
 * walk in the pose the caller asked for, and must not burn frames when nothing is happening.
 */
class AgentWorkerTest {

    private AgentWorker worker() {
        return new AgentWorker("thread-1", "Agent 1", "Fix pagination", 0, 0, 2.0, 12.0);
    }

    @Test
    void walkingEndsInTheRequestedPose() {
        AgentWorker worker = worker();
        worker.walkTo(4.0, 4.0, AgentWorker.State.WORKING);
        assertEquals(AgentWorker.State.WALKING, worker.state());

        // 8 tiles of travel at 2.6 tiles/s needs ~3.1 s; step it in frames.
        for (int i = 0; i < 400 && worker.state() == AgentWorker.State.WALKING; i++) {
            worker.update(0.016);
        }
        assertEquals(AgentWorker.State.WORKING, worker.state());
        assertEquals(4.0, worker.x(), 0.01);
        assertEquals(4.0, worker.y(), 0.01);
    }

    @Test
    void walkingToTheCurrentTileSettlesImmediately() {
        AgentWorker worker = worker();
        worker.walkTo(2.0, 12.0, AgentWorker.State.IDLE);
        assertEquals(AgentWorker.State.IDLE, worker.state());
    }

    @Test
    void facingFollowsTheDirectionOfTravel() {
        AgentWorker worker = worker();
        worker.walkTo(8.0, 12.0, AgentWorker.State.LOUNGE);
        worker.update(0.1);
        assertTrue(worker.facingRight());

        worker.walkTo(0.5, 12.0, AgentWorker.State.LOUNGE);
        worker.update(0.1);
        assertFalse(worker.facingRight());
    }

    /** Idle and lounging agents must not keep the animation timer alive. */
    @Test
    void onlyMovingOrBusyAgentsNeedFrames() {
        AgentWorker worker = worker();
        worker.setState(AgentWorker.State.IDLE);
        assertFalse(worker.needsAnimation());
        worker.setState(AgentWorker.State.LOUNGE);
        assertFalse(worker.needsAnimation());

        worker.setState(AgentWorker.State.WORKING);
        assertTrue(worker.needsAnimation());
        worker.setState(AgentWorker.State.WAITING);
        assertTrue(worker.needsAnimation());
    }

    /** The cooldown is what paces wandering, and it only runs down while the agent is settled. */
    @Test
    void cooldownCountsDownWhenSettledButNotWhileWalking() {
        AgentWorker worker = worker();
        worker.setState(AgentWorker.State.IDLE);
        worker.setWanderCooldown(1.0);
        worker.update(0.4);
        assertEquals(0.6, worker.wanderCooldown(), 0.001);

        worker.walkTo(6.0, 6.0, AgentWorker.State.IDLE);
        worker.setWanderCooldown(1.0);
        worker.update(0.4);
        assertEquals(1.0, worker.wanderCooldown(), 0.001);
    }

    @Test
    void seatedPosesAreReportedForRendering() {
        AgentWorker worker = worker();
        worker.setState(AgentWorker.State.WORKING);
        assertTrue(worker.isSeated());
        worker.setState(AgentWorker.State.LOUNGE);
        assertFalse(worker.isSeated());
    }

    @Test
    void wanderSpotsExcludeDesksSeatsAndWalls() {
        OfficeMap map = new OfficeMap();
        assertFalse(map.wanderSpots().isEmpty());
        for (double[] spot : map.wanderSpots()) {
            int x = (int) spot[0];
            int y = (int) spot[1];
            char tile = map.tile(x, y);
            assertTrue(tile == '.' || tile == '~',
                    "wander spot on tile '" + tile + "' at " + x + "," + y);
            assertFalse(map.tile(x, y - 1) == '=', "wander spot in a seat lane at " + x + "," + y);
        }
    }

    @Test
    void everyDeskHasASeatBelowIt() {
        OfficeMap map = new OfficeMap();
        assertEquals(8, map.desks().size());
        assertEquals(4, map.meetingSeats().size());
        assertEquals(12, map.capacity());
        for (OfficeMap.Desk desk : map.desks()) {
            assertEquals(desk.deskY() + 1.0, desk.seatY(), 0.001);
            assertEquals('o', map.tile((int) desk.seatX(), (int) desk.seatY()));
        }
    }
}
