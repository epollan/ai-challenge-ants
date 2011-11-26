import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Represents a policy of repulsion from some point on the map
 * Author: evan.pollan
 * Date: 11/23/11
 * Time: 10:50 PM
 */
public class RepulsionPolicy {

    /**
     * Callback used to notify of repulsion
     */
    public interface HandleRepulsion {
        void repulse(Tile ant, Tile destination);
    }

    // Threshold, relative to median egress route distance, above which
    // egress routes will be discarded
    private static final float EGRESS_MEDIAN_THRESHOLD = 1.5f;
    // Mutiplier used to expand the radius of repulsion to come up with
    // egress route targets.
    private static final float REPULSION_RADIUS_MULTIPLIER = 1.5f;
    private static final Logger _log = Logger.getLogger(RepulsionPolicy.class);

    private Tile _epicenter;
    private int _radiusOfRepulsion;
    private List<Tile> _egressTargets;
    private Ants _ants;
    private final int _maximumRadius;

    public RepulsionPolicy(Ants ants, Tile epicenter, int radiusOfRepulsion) {
        _ants = ants;
        // width/height of, e.g. 11, means our max radius is 5 ((11-1)/2).
        _maximumRadius = (int) Math.floor((Math.min(_ants.getRows(), _ants.getCols()) - 1) / 2.0);
        _epicenter = epicenter;
        _radiusOfRepulsion = Math.min(radiusOfRepulsion, _maximumRadius);
        calculateEgressTargets();
    }

    private void calculateEgressTargets() {
        // Start due north of the epicenter, and walk the 'circle' (really, diamond) that's
        // radiusOfRepulsion * REPULSION_RADIUS_MULTIPLIER from the epicenter
        int egressRadius = (int) Math.ceil(_radiusOfRepulsion * REPULSION_RADIUS_MULTIPLIER);
        egressRadius = Math.min(egressRadius, _maximumRadius);
        List<AStarRoute> egressRouteCandidates = new ArrayList<AStarRoute>(egressRadius * 4);

        // Direction changes will correspond to due east/south/west/north
        Map<Tile, CircumferenceDirection> directionChanges = new HashMap<Tile, CircumferenceDirection>();
        directionChanges.put(getTile(_epicenter, -egressRadius, 0),
                             CircumferenceDirection.SouthEast);
        directionChanges.put(getTile(_epicenter, 0, egressRadius),
                             CircumferenceDirection.SouthWest);
        directionChanges.put(getTile(_epicenter, egressRadius, 0),
                             CircumferenceDirection.NorthWest);
        directionChanges.put(getTile(_epicenter, 0, -egressRadius),
                             CircumferenceDirection.NorthEast);

        // Start north, heading southeast
        Tile start = getTile(_epicenter, -egressRadius, 0);
        CircumferenceDirection currentDirection = CircumferenceDirection.SouthEast;
        Tile current = start;
        do {
            if (_ants.getIlk(current).isPassable()) {
                try {
                    egressRouteCandidates.add(new AStarRoute(_ants, _epicenter, current));
                } catch (NoRouteException ex) {
                }
            }
            current = getTile(current, currentDirection.getRowDelta(), currentDirection.getColDelta());
            CircumferenceDirection newDirection = directionChanges.get(current);
            if (newDirection != null) {
                currentDirection = newDirection;
            }
        } while (!start.equals(current));

        // Find the median route distance and extract the endpoints of all those routes
        // that are less than EGRESS_MEDIAN_THRESHOLD times that distance
        Collections.sort(egressRouteCandidates);
        int medianPos = (int) Math.floor(egressRouteCandidates.size() / 2);
        int medianDistance = egressRouteCandidates.get(medianPos).getDistance();
        int thresholdDistance = (int) Math.ceil(medianDistance * EGRESS_MEDIAN_THRESHOLD);
        int i = medianPos + 1;
        for (; i < egressRouteCandidates.size(); i++) {
            if (egressRouteCandidates.get(i).getDistance() >= thresholdDistance) {
                break;
            }
        }

        // Valid targets are those whose distance is less than the median egress
        // route distance times the EGRESS_MEDIAN_THRESHOLD
        _egressTargets = new ArrayList<Tile>(i);
        for (int j = 0; j < i; j++) {
            _egressTargets.add(egressRouteCandidates.get(j).getEnd());
        }
        if (_log.isDebugEnabled()) {
            _log.debug(String.format("Calculated %d egress targets with radius %d for repulsion zone [%s] +/- %d",
                                     _egressTargets.size(), egressRadius, _epicenter, _radiusOfRepulsion));
        }
    }

    public void evacuate(Iterable<Tile> untargeted, HandleRepulsion handler) {
        List<EgressCandidate> toEvacuate = new LinkedList<EgressCandidate>();
        for (Tile ant : untargeted) {
            int distance = _ants.getDistance(_epicenter, ant);
            if (distance <= _radiusOfRepulsion) {
                toEvacuate.add(new EgressCandidate(ant, distance));
            }
        }
        // Sort ants to be evacuated by distance from epicenter, with those
        // furthest first.
        Collections.sort(toEvacuate);
        for (EgressCandidate ant : toEvacuate) {
            // Sort to handle blocking -- might need this to fall-back on *slightly*
            // less optimal egress routes...
            AStarRoute shortest = null;
            for (Tile target : _egressTargets) {
                try {
                    AStarRoute route = new AStarRoute(_ants, ant._ant, target);
                    if (_log.isDebugEnabled()) {

                    }
                    if (shortest == null || route.getDistance() < shortest.getDistance()) {
                        shortest = route;
                    }
                } catch (NoRouteException ex) {
                }
            }
            if (shortest != null) {
                if (_log.isDebugEnabled()) {
                    _log.debug(String.format("Repulsing ant at [%s] away from [%s], using route: %s",
                                             shortest.getStart(), _epicenter, shortest));
                }
                handler.repulse(shortest.getStart(), shortest.nextTile());
            }
        }
    }

    private Tile getTile(Tile start, int rowDelta, int colDelta) {
        int row = start.getRow() + rowDelta;
        if (row < 0) {
            row += _ants.getRows();
        } else if (row >= _ants.getRows()) {
            row -= _ants.getRows();
        }
        int col = start.getCol() + colDelta;
        if (col < 0) {
            col += _ants.getCols();
        } else if (col >= _ants.getCols()) {
            col -= _ants.getCols();
        }
        return new Tile(row, col);
    }

    // Offsets to travel in a given direction along the "circumference" of a
    // "circle" (diamond)
    private static enum CircumferenceDirection {

        SouthEast(1, 1),
        SouthWest(1, -1),
        NorthWest(-1, -1),
        NorthEast(-1, 1);

        private final int _rowDelta;
        private final int _colDelta;

        CircumferenceDirection(int rowDelta, int colDelta) {
            _rowDelta = rowDelta;
            _colDelta = colDelta;
        }

        int getRowDelta() {
            return _rowDelta;
        }

        int getColDelta() {
            return _colDelta;
        }
    }

    private class EgressCandidate implements Comparable<EgressCandidate> {

        private Integer _distFromEpicenter;
        private Tile _ant;

        public EgressCandidate(Tile ant, int distance) {
            _ant = ant;
            _distFromEpicenter = distance;
        }

        public int compareTo(EgressCandidate other) {
            // Negate all comparisons -- we want those "furthest" from the epicenter
            // to sort lowest
            int comparison = -(_distFromEpicenter.compareTo(other._distFromEpicenter));
            if (comparison == 0) {
                // Same distance, discriminate based upon Tile location
                comparison = -(_ant.compareTo(other._ant));
            }
            return comparison;
        }
    }
}
