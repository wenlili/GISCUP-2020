package UserExamples;

import COMSETsystem.*;

import java.util.*;

/**
 * This fleet manager optimizes for agent utilization.
 */
public class FleetManagerForUtilization extends FleetManager {
    private final Map<Long, Long> agentLastAppearTime = new HashMap<>();
    private final Map<Long, LocationOnRoad> agentLastLocation = new HashMap<>();
    private final Map<Long, Resource> resourceAssignment = new HashMap<>();
    private final Map<Long, Resource> nextResourceAssignment = new HashMap<>();
    private final Set<Resource> waitingResources = new TreeSet<>(Comparator.comparingLong((Resource r) -> r.id));
    private final Set<Long> availableAgent = new TreeSet<>(Comparator.comparingLong((Long id) -> id));
    private final Set<Long> unavailableAgent = new TreeSet<>(Comparator.comparingLong((Long id) -> id));
    private final Map<Long, Boolean> agentPickingUp = new HashMap<>();
    private final Map<Long, Random> agentRnd = new HashMap<>();
    private final Map<Long, LinkedList<Intersection>> agentRoutes = new HashMap<>();

    private Weighting weighting;
    private Map<Long, Integer> weightingData = new HashMap<>();
    private int weightingDataCount = 0;
    private double travelTimeMultiplier = 3.75;

    /**
     * The simulation calls onAgentIntroduced to notify the **FleetManager** that a new agent has been randomly
     * placed and is available for assignment.
     * @param agentId a unique id for each agent and can be used to associated information with agents.
     * @param currentLoc the current location of the agent.
     * @param time the simulation time.
     */
    @Override
    public void onAgentIntroduced(long agentId, LocationOnRoad currentLoc, long time) {
        agentLastAppearTime.put(agentId, time);
        agentLastLocation.put(agentId, currentLoc);
        availableAgent.add(agentId);
        agentPickingUp.put(agentId, false);
    }

    /**
     * The simulation calls this method to notify the **FleetManager** that the resource's state has changed:
     * + resource becomes available for pickup
     * + resource expired
     * + resource has been dropped off by its assigned agent
     * + resource has been picked up by an agent.
     * @param resource This object contains information about the Resource useful to the fleet manager
     * @param state the new state of the resource
     * @param currentLoc current location of the resources
     * @param time the simulation time
     * @return AgentAction that tells the agents what to do.
     */
    @Override
    public AgentAction onResourceAvailabilityChange(Resource resource,
                                                    ResourceState state,
                                                    LocationOnRoad currentLoc,
                                                    long time) {

        AgentAction action = AgentAction.doNothing();

        if (state == ResourceState.AVAILABLE) {
            long fromId = resource.pickupLoc.road.from.id;
            weightingData.put(fromId, weightingData.getOrDefault(fromId, 0) + 1);
            weightingDataCount++;
            if (weightingDataCount == map.intersections().size() / 6) {
                /*
                for (Resource res : waitingResources) {
                    long id = res.pickupLoc.road.from.id;
                    weightingData.put(id, weightingData.getOrDefault(id, 0) + 180);
                }
                */
                Weighting newWeighting = new Weighting(map.intersections(), weightingData);
                weighting = new Weighting(weighting, newWeighting);
                weightingData = new HashMap<>();
                weightingDataCount = 0;
            }

            Long assignedAgent = getNearestAvailableAgent(resource, time);
            if (assignedAgent != null) {
                if (resourceAssignment.get(assignedAgent) == null) {
                    resourceAssignment.put(assignedAgent, resource);
                    agentRoutes.put(assignedAgent, new LinkedList<>());
                    availableAgent.remove(assignedAgent);
                    unavailableAgent.add(assignedAgent);
                    agentPickingUp.put(assignedAgent, true);
                    action = AgentAction.assignTo(assignedAgent, resource.id);
                } else {
                    nextResourceAssignment.put(assignedAgent, resource);
                }
            } else {
                waitingResources.add(resource);
                weightingData.put(fromId, weightingData.getOrDefault(fromId, 0) + 200);
            }
        } else if (state == ResourceState.DROPPED_OFF) {
            Resource bestResource = null;
            long earliest = Long.MAX_VALUE;
            for (Resource res : waitingResources) {
                // If res is in waitingResources, then it must have not expired yet
                // testing null pointer exception
                // Warning: map.travelTimeBetween returns the travel time based on speed limits, not
                // the dynamic travel time. Thus the travel time returned by map.travelTimeBetween may be different
                // than the actual travel time.
                long travelTime = (long)(travelTimeMultiplier * map.travelTimeBetween(currentLoc, res.pickupLoc));

                // if the resource is reachable before expiration
                long arriveTime = time + travelTime;
                if (arriveTime <= res.expirationTime && arriveTime < earliest) {
                    earliest = arriveTime;
                    bestResource = res;
                }
            }

            if (nextResourceAssignment.get(resource.assignedAgentId) != null) {
                bestResource = nextResourceAssignment.get(resource.assignedAgentId);
                nextResourceAssignment.remove(resource.assignedAgentId);
                agentPickingUp.put(resource.assignedAgentId, true);
                action = AgentAction.assignTo(resource.assignedAgentId, bestResource.id);
            } else if (bestResource != null) {
                waitingResources.remove(bestResource);
                agentPickingUp.put(resource.assignedAgentId, true);
                action = AgentAction.assignTo(resource.assignedAgentId, bestResource.id);
            } else {
                agentRoutes.put(resource.assignedAgentId, new LinkedList<>());
                availableAgent.add(resource.assignedAgentId);
                unavailableAgent.remove(resource.assignedAgentId);
                agentPickingUp.put(resource.assignedAgentId, false);
            }
            resourceAssignment.put(resource.assignedAgentId, bestResource);
            agentLastLocation.put(resource.assignedAgentId, currentLoc);
            agentLastAppearTime.put(resource.assignedAgentId, time);
        } else if (state == ResourceState.EXPIRED) {
            waitingResources.remove(resource);
            if (resource.assignedAgentId != -1) {
                if (nextResourceAssignment.get(resource.assignedAgentId) == null) {
                    agentRoutes.put(resource.assignedAgentId, new LinkedList<>());
                    availableAgent.add(resource.assignedAgentId);
                    unavailableAgent.remove(resource.assignedAgentId);
                    agentPickingUp.put(resource.assignedAgentId, false);
                    resourceAssignment.remove(resource.assignedAgentId);
                    action = AgentAction.abort(resource.assignedAgentId);
                } else {
                    Resource bestResource = nextResourceAssignment.get(resource.assignedAgentId);
                    nextResourceAssignment.remove(resource.assignedAgentId);
                    agentRoutes.put(resource.assignedAgentId, new LinkedList<>());
                    agentPickingUp.put(resource.assignedAgentId, true);
                    resourceAssignment.put(resource.assignedAgentId, bestResource);
                    action = AgentAction.assignTo(resource.assignedAgentId, bestResource.id);
                }
            }
        } else if (state == ResourceState.PICKED_UP) {
            agentRoutes.put(resource.assignedAgentId, new LinkedList<>());
            agentPickingUp.put(resource.assignedAgentId, false);
        }

        return action;
    }

    /**
     * Calls to this method notifies that an agent has reach an intersection and is ready for new travel directions.
     * This is called whenever any agent without an assigned resources reaches an intersection. This method allows
     * the **FleetManager** to plan any agent's cruising path, the path it takes when it has no assigned resource.
     * The intention is that the **FleetManager** will plan the cruising, to minimize the time it takes to
     * reach resources for pickup.
     * @param agentId unique id of the agent
     * @param time current simulation time.
     * @param currentLoc current location of the agent.
     * @return the next intersection for the agent to navigate to.
     */
    @Override
    public Intersection onReachIntersection(long agentId, long time, LocationOnRoad currentLoc) {
        if (agentId == 240902L && time == 1464800008L) {
            System.out.println("here");
        }
        agentLastAppearTime.put(agentId, time);

        LinkedList<Intersection> route = agentRoutes.getOrDefault(agentId, new LinkedList<>());

        if (route.isEmpty()) {
            route = planRoute(agentId, currentLoc);
            agentRoutes.put(agentId, route);
        }

        Intersection nextLocation = route.poll();
        Road nextRoad = currentLoc.road.to.roadTo(nextLocation);
        LocationOnRoad locationOnRoad = LocationOnRoad.createFromRoadStart(nextRoad);
        agentLastLocation.put(agentId, locationOnRoad);
        return nextLocation;
    }

    /**
     * Calls to this method notifies that an agent with an picked up resource reaches an intersection.
     * This method allows the **FleetMangaer** to plan the route of the agent to the resource's dropoff point.
     * @param agentId the unique id of the agent
     * @param time current simulation time
     * @param currentLoc current location of agent
     * @param resource information of the resource associated with the agent.
     * @return the next intersection for the agent to navigate to.
     */
    @Override
    public Intersection onReachIntersectionWithResource(long agentId, long time, LocationOnRoad currentLoc,
                                                        Resource resource) {
        agentLastAppearTime.put(agentId, time);

        LinkedList<Intersection> route = agentRoutes.getOrDefault(agentId, new LinkedList<>());

        if (route.isEmpty()) {
            route = planRouteToTarget(currentLoc, resource.dropOffLoc);
            agentRoutes.put(agentId, route);
        }

        Intersection nextLocation = route.poll();
        Road nextRoad = currentLoc.road.to.roadTo(nextLocation);
        LocationOnRoad locationOnRoad = LocationOnRoad.createFromRoadStart(nextRoad);
        agentLastLocation.put(agentId, locationOnRoad);
        return nextLocation;
    }

    Long getNearestAvailableAgent(Resource resource, long currentTime) {
        long earliest = Long.MAX_VALUE;
        Long bestAgent = null;
        for (Long id : availableAgent) {
            if (!agentLastLocation.containsKey(id)) continue;

            LocationOnRoad curLoc = getCurrentLocation(
                    agentLastAppearTime.get(id),
                    agentLastLocation.get(id),
                    currentTime);
            // Warning: map.travelTimeBetween returns the travel time based on speed limits, not
            // the dynamic travel time. Thus the travel time returned by map.travelTimeBetween may be different
            // than the actual travel time.
            long travelTime = (long)(travelTimeMultiplier * map.travelTimeBetween(curLoc, resource.pickupLoc));
            long arriveTime = travelTime + currentTime;
            if (arriveTime < earliest) {
                bestAgent = id;
                earliest = arriveTime;
            }
        }
        for (Long id : unavailableAgent) {
            if (!agentLastLocation.containsKey(id) || nextResourceAssignment.get(id) != null) continue;

            LocationOnRoad curLoc = getCurrentLocation(
                    agentLastAppearTime.get(id),
                    agentLastLocation.get(id),
                    currentTime);
            // Warning: map.travelTimeBetween returns the travel time based on speed limits, not
            // the dynamic travel time. Thus the travel time returned by map.travelTimeBetween may be different
            // than the actual travel time.
            long travelTime;
            Resource res = resourceAssignment.get(id);
            if (agentPickingUp.get(id)) {
                travelTime = map.travelTimeBetween(curLoc, res.pickupLoc) + map.travelTimeBetween(res.pickupLoc, res.dropOffLoc) + map.travelTimeBetween(res.dropOffLoc, resource.pickupLoc);
            } else {
                travelTime = map.travelTimeBetween(curLoc, res.dropOffLoc) + map.travelTimeBetween(res.dropOffLoc, resource.pickupLoc);
            }
            travelTime = (long)(travelTimeMultiplier * travelTime);
            long arriveTime = travelTime + currentTime;
            if (arriveTime < earliest) {
                bestAgent = id;
                earliest = arriveTime;
            }
        }
        if (earliest <= resource.expirationTime) {
            return bestAgent;
        } else {
            return null;
        }
    }

    LinkedList<Intersection> planRoute(long agentId, LocationOnRoad currentLocation) {
        Resource assignedRes = resourceAssignment.get(agentId);

        if (assignedRes != null) {
            Intersection sourceIntersection = currentLocation.road.to;
            Intersection destinationIntersection = assignedRes.pickupLoc.road.from;
            if (sourceIntersection.id == destinationIntersection.id) {
                destinationIntersection = assignedRes.pickupLoc.road.to;
            }
            LinkedList<Intersection> shortestTravelTimePath = map.shortestTravelTimePath(sourceIntersection,
                    destinationIntersection);
            shortestTravelTimePath.poll(); // Ensure that route.get(0) != currentLocation.road.to.
            return shortestTravelTimePath;
        } else {
            return getRandomRoute(agentId, currentLocation);
        }
    }

    LinkedList<Intersection> planRouteToTarget(LocationOnRoad source, LocationOnRoad destination) {
        Intersection sourceIntersection = source.road.to;
        Intersection destinationIntersection = destination.road.from;
        LinkedList<Intersection> shortestTravelTimePath = map.shortestTravelTimePath(sourceIntersection,
                destinationIntersection);
        shortestTravelTimePath.poll(); // Ensure that route.get(0) != currentLocation.road.to.
        return shortestTravelTimePath;
    }

    LinkedList<Intersection> getRandomRoute(long agentId, LocationOnRoad currentLocation) {
        Random random = agentRnd.getOrDefault(agentId, new Random(agentId));
        agentRnd.put(agentId, random);

        Intersection sourceIntersection = currentLocation.road.to;
        Intersection destinationIntersection = weighting.getRandomIntersection();
        int count = 0;
        while (map.travelTimeBetween(sourceIntersection, destinationIntersection) > 400000000L && count < map.intersections().size()) {
            destinationIntersection = weighting.getRandomIntersection();
            count++;
        }

        if (destinationIntersection == sourceIntersection) {
            // destination cannot be the source
            // if destination is the source, choose a neighbor to be the destination
            Road[] roadsFrom = sourceIntersection.roadsMapFrom.values().toArray(new Road[0]);
            destinationIntersection = roadsFrom[random.nextInt(roadsFrom.length)].to;
        }
        while (destinationIntersection == sourceIntersection) {
            Road[] roadsFrom = sourceIntersection.roadsMapFrom.values().toArray(new Road[0]);
            destinationIntersection = roadsFrom[random.nextInt(roadsFrom.length)].to;
        }

        LinkedList<Intersection> shortestTravelTimePath = map.shortestTravelTimePath(sourceIntersection, destinationIntersection);
        shortestTravelTimePath.poll(); // Ensure that route.get(0) != currentLocation.road.to.
        return shortestTravelTimePath;
    }

    public FleetManagerForUtilization(CityMap map) {
        super(map);
        weighting = new Weighting(map.intersections());
    }
}
