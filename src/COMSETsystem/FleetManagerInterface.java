package COMSETsystem;

/**
 * Contestants should implement this interface
 */
public interface FleetManagerInterface {

    /**
     * Tells the fleet manager that a resource is now available at `time` and that it will be expire after
     * `life_time_duration` from now. We expect the fleet manager to select an agent to
     * service the resource. The Fleet Manager will then direct that agent to the pick-up, when it
     * reports back to the fleet manager in one of its `DestinationReached` calls.
     *
     * @param resource_id ID to identify t he resource
     * @param time when resource becomes available, it's the current simulation time.
     * @param lifetime_duration expires after this duration has passed
     */
    void NotifyResourceAvailability(long resource_id, long time, long lifetime_duration);

    enum AgentState { CRUISING, TO_PICK_UP, TO_DROP_OFF }

    class AgentAction {
    }

    /**
     * Travel to destination.  State represents the intention of the agent.
     */
    class TravelAction extends AgentAction {
        LocationOnRoad destination;
        AgentState state;
    }

    /**
     * Pick-up the resource now.
     */
    class PickupAction extends AgentAction {
        long resource_id;
    }

    /**
     * Drop-off the agent now.
     */
    class DropoffAction extends AgentAction {
        long resource_id;
    }

    /**
     * Tells the Fleet Manager the agent has reached its destination: an intersection, a pick-up or drop-off point.
     * The Fleet Manager show know which kidn of destination because it should be tracking the state of each agent.
     * The Fleet Manager can also tell learn the traffic patterns from the time parameter.
     *
     * @param agent_id ID that identifies the agent. Each agent is number form 0 to N-1, where N is the number of agents
     * @param time     Current simulation time.
     * @param loc      Current location of agent.
     * @return The next action to take for the agent.
     */
    AgentAction NotifyAgentReachedDestination(long agent_id, long time, LocationOnRoad loc);
}
