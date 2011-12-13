import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * Represents a policy of repulsion from some point on the map
 * Author: evan.pollan
 * Date: 11/23/11
 * Time: 10:50 PM
 */
public class RepulsionPolicy {

    // Threshold, relative to median egress route distance, above which
    // egress routes will be discarded
    private static final float EGRESS_MEDIAN_THRESHOLD = 1.5f;
    // Mutiplier used to expand the radius of repulsion to come up with
    // egress route targets.
    private static final float REPULSION_RADIUS_MULTIPLIER = 1.5f;
    private static final LogFacade _log = LogFacade.get(RepulsionPolicy.class);

    private final Tile _epicenter;
    private final int _radiusOfRepulsion;
    private final DefenseZone _defenseZone;
    private int _sampleOffset = 0;

    public RepulsionPolicy(Tile epicenter, int radiusOfRepulsion) {
        _epicenter = epicenter;
        _radiusOfRepulsion = Math.min(radiusOfRepulsion, Circumference.getMaximumRadius());
        _defenseZone = new DefenseZone(epicenter,  radiusOfRepulsion+1);
    }

    public Tile getEpicenter() {
        return _epicenter;
    }

    public DefenseZone getDefenseZone() {
        return _defenseZone;
    }

    public void evacuate(Set<Tile> untargeted, TimeManager manager, MovementHandler handler) {
        if (untargeted.size() == 0) {
            // Nothing to evacuate
            return;
        }
        // Only evacuate those ants within the radius of repulsion
        Set<Tile> toEvacuate = new HashSet<Tile>();
        for (Tile ant : untargeted) {
            int distance = Registry.Instance.getDistance(_epicenter, ant);
            if (distance <= _radiusOfRepulsion) {
                toEvacuate.add(ant);
            }
        }
        _log.debug("Hill [%s] has %d ant(s) to be repulsed", _epicenter, toEvacuate.size());

        int numStrongpoints = _defenseZone.getStrongpoints().size();
        int sampleSize = (int)Math.floor(numStrongpoints / untargeted.size());
        if (sampleSize == 0) {
            // Simple size should at least be one so we can progress through the strongpoints
            sampleSize = 1;
        }
        boolean timedout = false;
        for (int index = _sampleOffset++ % numStrongpoints;
                index < numStrongpoints && !timedout;
                index += sampleSize) {
            // Shallow copy -- successful move will remove the ant from the to-be-evacuated set
            AStarRoute shortest = null;
            for (Tile ant : new ArrayList<Tile>(toEvacuate)) {
                if (manager.stepTimeOverrun()) {
                    timedout = true;
                    break;
                }
                try {
                    AStarRoute route = new AStarRoute(ant, _defenseZone.getStrongpoints().get(index));
                    if (shortest == null || route.getDistance() < shortest.getDistance()) {
                        shortest = route;
                    }
                }
                catch (NoRouteException ex) {}
            }
            if (shortest != null && handler.move(shortest.getStart(), shortest.nextTile())) {
                TargetingHistory.Instance.create(shortest.getStart(),
                                                 shortest.getEnd(),
                                                 TargetingPolicy.Type.Unmanaged,
                                                 shortest,
                                                 shortest.getDistance(),
                                                 true);
                toEvacuate.remove(shortest.getStart());
                _log.debug("Repulsing ant at [%s] away from [%s], using route: %s",
                           shortest.getStart(), _epicenter, shortest);

            }
            if (toEvacuate.size() == 0) {
                break;
            }
        }
    }
}
