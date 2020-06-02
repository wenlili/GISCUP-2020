package COMSETsystem;

public interface FleetManagerInterface {

    void NotifyResourceAvailability(long resource_id, long time, long lifetime_duration);

    class AgentAction {

        enum Kind {
            TRAVEL, PICK_UP, DROP_OFF
        }

        Kind op;
        LocationOnRoad location;
    }

    AgentAction NotifyAgentReachedDestination(long agent_id, long time, LocationOnRoad loc);
}
