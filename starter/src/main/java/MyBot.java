import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.*;

/**
 *
 */
public class MyBot extends Bot {
    /**
     * Main method executed by the game engine for starting the bot.
     *
     * @param args command line arguments
     * @throws IOException if an I/O error occurs
     */
    public static void main(String[] args)
            throws IOException {
        _log.info("[[GAME START]]");
        new MyBot().readSystemInput();
    }

    private Set<Tile> _destinations = new HashSet<Tile>();
    private Set<Tile> _toMove = new HashSet<Tile>();
    private Set<Tile> _unseenTiles = null;
    private TargetingPolicy _foodPolicy = new TargetingPolicy("FoodPolicy", 1, 3);
    private TargetingPolicy _hillPolicy = new TargetingPolicy("HillPolicy", 5, 3);
    private TargetingPolicy _unseenPolicy = new TargetingPolicy("UnseenTilePolicy", 1, 3);
    private Map<Tile, RepulsionPolicy> _myHillRepulsions = new HashMap<Tile, RepulsionPolicy>();
    private int _turn = 0;
    private static Logger _log = Logger.getLogger(MyBot.class);
    private TimeManager _timeManager = null;

    /**
     * For every ant check every direction in fixed order (N, E, S, W) and move it if the tile is
     * passable.
     */
    @Override
    public void doTurn() {
        Ants ants = getAnts();
        int managedTimeAllocation = ants.getTurnTime() - 50;
        if (_timeManager == null || _timeManager.getTotalAllowed() != managedTimeAllocation) {
            _timeManager = new TimeManager(managedTimeAllocation);
        }
        _log.info(String.format("[[...turn %d...]]", _turn++));
        try {
            long start = System.currentTimeMillis();

            for (Tile myHill : ants.getMyHills()) {
                if (!_myHillRepulsions.containsKey(myHill)) {
                    // Aim to keep ants at least 6 moves away from my hills
                    _myHillRepulsions.put(myHill, new RepulsionPolicy(ants, myHill, 6));
                }
            }

            if (_unseenTiles == null) {
                _unseenTiles = new HashSet<Tile>();
                // Sparsely sample the board for unseen tiles
                final int sampleSparseness = 5;
                for (int row = 0; row < ants.getRows(); row += sampleSparseness) {
                    for (int col = 0; col < ants.getCols(); col += sampleSparseness) {
                        _unseenTiles.add(new Tile(row, col));
                    }
                }
            }

            // remove any tiles that can be seen, run each turn
            for (Iterator<Tile> locIter = _unseenTiles.iterator(); locIter.hasNext(); ) {
                Tile next = locIter.next();
                if (ants.isVisible(next)) {
                    locIter.remove();
                }
            }

            avoidHills();
            seekFood();
            attackHills();
            exploreUnseenTiles();
            unblockHills();

            _destinations.clear();
            _toMove.clear();
            TargetingPolicy.clearAssignments();
            _timeManager.turnDone();
            long finish = System.currentTimeMillis();
            _log.info(String.format("[[ # turn %d processing took %d ms, allowed %d.  Overall remaining: %d # ]]",
                                    _turn - 1, finish - start, ants.getTurnTime(), ants.getTimeRemaining()));
        } catch (Throwable t) {
            _log.error("Unexpected turn processing error", t);
            throw new RuntimeException("Unexpected", t);
        }
    }

    private void avoidHills() {
        for (Tile myHill : getAnts().getMyHills()) {
            _destinations.add(myHill);
        }
    }

    private void seekFood() {
        // find close food
        targetTiles(getAnts().getFoodTiles(), _foodPolicy);
    }

    private void attackHills() {
        // attack hills
        targetTiles(getAnts().getEnemyHills(), _hillPolicy);
    }

    private void exploreUnseenTiles() {
        targetTiles(_unseenTiles, _unseenPolicy);
    }

    private void unblockHills() {
        long start = System.currentTimeMillis();
        // local references need to be final to be passed to anonymous inner class
        final Set<Tile> untargeted = new HashSet<Tile>(getAnts().getMyAnts());
        untargeted.removeAll(_toMove);
        if (_log.isDebugEnabled()) {
            _log.debug(String.format("Checking for own-hill repulsion on %d untargeted ants", untargeted.size()));
        }
        _timeManager.nextStep();
        for (final RepulsionPolicy policy : _myHillRepulsions.values()) {
            policy.evacuate(untargeted,
                            _timeManager,
                            new RepulsionPolicy.HandleRepulsion() {
                                @Override
                                public void repulse(Tile ant, Tile destination) {
                                    if (moveToLocation(ant, destination)) {
                                        untargeted.remove(ant);
                                    }
                                }
                            });
        }
        if (_log.isDebugEnabled()) {
            _log.debug(String.format("Unblocked hills, %d elapsed, %d ms remaining in turn",
                                     System.currentTimeMillis() - start, getAnts().getTimeRemaining()));
        }
    }

    private void targetTiles(Collection<Tile> targets, TargetingPolicy policy) {
        List<Route> routes = new LinkedList<Route>();
        int candidateAnts = 0;
        final Ants ants = getAnts();

        _timeManager.nextStep(2.0f);
        long start = System.currentTimeMillis();

        // We'll sort the list of targets relative to their distance to each ant
        ArrayList<Tile> relativeToAnt = new ArrayList<Tile>(targets);
        final Set<Tile> untargeted = new HashSet<Tile>(getAnts().getMyAnts());
        untargeted.removeAll(_toMove);
        for (final Tile antLoc : untargeted) {
            candidateAnts++;
            int thisAntsRoutes = 0;
            // Look at routes in order of targets' distance from the current ant
            Collections.sort(relativeToAnt, new Comparator<Tile>() {
                @Override
                public int compare(Tile o1, Tile o2) {
                    int d1 = ants.getDistance(antLoc, o1);
                    int d2 = ants.getDistance(antLoc, o2);
                    if (d1 == d2) {
                        return o1.compareTo(o2);
                    }
                    return d1 < d2 ? -1 : 1;
                }
            });
            for (Tile target : relativeToAnt) {
                try {
                    if (antLoc.equals(target)) {
                        // Ant's already on target
                        continue;
                    }
                    Route route = new AStarRoute(ants, antLoc, target);
                    routes.add(route);
                    ++thisAntsRoutes;
                    if (policy.getPerAntRouteLimit() != null &&
                        thisAntsRoutes >= policy.getPerAntRouteLimit().intValue()) {
                        if (_log.isDebugEnabled()) {
                            _log.debug(String.format(
                                    "Capping routes for ant [%s] at %d under %s consideration, %d ms remaining...",
                                    antLoc, thisAntsRoutes, policy, ants.getTimeRemaining()));
                        }
                        break;
                    }
                } catch (NoRouteException ex) {
                    //_log.error(String.format("Cannot route from [%s] to [%s]", antLoc, target), ex);
                }
            }
            if (_timeManager.stepTimeOverrun()) {
                break;
            }
        }
        if (_log.isDebugEnabled()) {
            _log.debug(String.format("Weighing %,d routes using %s, given %d candidate ants and %,d targets",
                                     routes.size(), policy, candidateAnts, targets.size()));
        }
        _timeManager.nextStep();
        if (routes.isEmpty()) {
            return;
        }
        Collections.sort(routes);
        for (Route route : routes) {
            if (_toMove.contains(route.getStart())) {
                // More optimial route already assigned movement to this route's ant
                continue;
            }
            if (_timeManager.stepTimeOverrun()) {
                break;
            }
            if (policy.canAssign(route.getStart(), route.getEnd()) && move(route)) {
                policy.assign(route.getStart(), route.getEnd());
                if (_log.isDebugEnabled()) {
                    _log.debug(String.format("Move successful -- route assigned using %s", policy));
                }
                if (policy.totalAssignmentsLimitReached(targets.size())) {
                    if (_log.isDebugEnabled()) {
                        _log.debug(String.format("Assigned the maximum number of %s routes (%s)",
                                                 policy, policy.getTotalAssignmentsLimit(targets.size())));
                    }
                    break;
                }
            }
        }
        if (_log.isDebugEnabled()) {
            _log.debug(String.format("%s targeting complete, %d elapsed, %d ms remaining in turn...",
                                     policy, System.currentTimeMillis() - start, getAnts().getTimeRemaining()));
        }
    }

    private boolean moveInDirection(Tile antLoc, Aim direction) {
        Ants ants = getAnts();
        // Track all moves, prevent collisions
        Tile newLoc = ants.getTile(antLoc, direction);
        if (ants.getIlk(newLoc).isUnoccupied() && !_destinations.contains(newLoc)) {
            ants.issueOrder(antLoc, direction);
            _destinations.add(newLoc);
            _toMove.add(antLoc);
            if (_log.isDebugEnabled()) {
                _log.debug(String.format("Moving ant at [%s] %s to [%s]", antLoc, direction, newLoc));
            }
            return true;
        } else {
            return false;
        }
    }

    private boolean moveToLocation(Tile antLoc, Tile destLoc) {
        Ants ants = getAnts();
        List<Aim> directions = ants.getDirections(antLoc, destLoc);
        for (Aim direction : directions) {
            if (moveInDirection(antLoc, direction)) {
                return true;
            }
        }
        return false;
    }

    private boolean move(Route r) {
        if (r instanceof AStarRoute) {
            return moveToLocation(r.getStart(), ((AStarRoute) r).nextTile());
        } else {
            return moveToLocation(r.getStart(), r.getEnd());
        }
    }
}
