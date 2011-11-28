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

    // Policies, tuning parameters
    private final TargetingPolicy _foodPolicy = new TargetingPolicy("FoodPolicy", 1, 2);
    private final TargetingPolicy _hillPolicy = new TargetingPolicy("HillPolicy", 5, 2);
    private final TargetingPolicy _unseenPolicy = new TargetingPolicy("UnseenTilePolicy", 1, 1);
    private final static int TIME_ALLOCATION_PAD = 50;
    private final static int MY_HILL_RADIUS_OF_REPULSION = 6;
    private final static int UNSEEN_TILE_SAMPLING_RATE = 5;
    private final static int UNSEEN_TILE_RECALC_PERIOD = 5;
    private final static float TARGETING_ROUTE_SORT_STEP_WEIGHT = 0.4f;
    private final static float TARGETING_ROUTE_CALC_STEP_WEIGHT = 1.6f;
    private final static float TARGETING_ROUTE_MOVE_STEP_WEIGHT = 1.0f;
    private final static float HILL_REPULSION_STEP_WEIGHT = 1.0f;

    private final Set<Tile> _untargetedAnts = new HashSet<Tile>();
    private final Set<Tile> _destinations = new HashSet<Tile>();
    private final Set<Tile> _toMove = new HashSet<Tile>();
    private Set<Tile> _unseenTiles = null;
    private final Map<Tile, RepulsionPolicy> _myHillRepulsions = new HashMap<Tile, RepulsionPolicy>();
    private int _turn = 0;
    private final static Logger _log = Logger.getLogger(MyBot.class);
    private TimeManager _timeManager = null;

    /**
     * For every ant check every direction in fixed order (N, E, S, W) and move it if the tile is
     * passable.
     */
    @Override
    public void doTurn() {
        Ants ants = getAnts();
        _untargetedAnts.addAll(ants.getMyAnts());
        int managedTimeAllocation = ants.getTurnTime() - TIME_ALLOCATION_PAD;
        if (_timeManager == null || _timeManager.getTotalAllowed() != managedTimeAllocation) {
            _timeManager = new TimeManager(managedTimeAllocation);
        }
        _log.info(String.format("[[...turn %d...]]", _turn++));
        try {
            _timeManager.turnStarted();

            for (Tile myHill : ants.getMyHills()) {
                if (!_myHillRepulsions.containsKey(myHill)) {
                    // Aim to keep ants at least 6 moves away from my hills
                    _myHillRepulsions.put(myHill, new RepulsionPolicy(ants, myHill, MY_HILL_RADIUS_OF_REPULSION));
                }
            }

            if (_unseenTiles == null) {
                _unseenTiles = new HashSet<Tile>();
                // Sparsely sample the board for unseen tiles
                for (int row = 0; row < ants.getRows(); row += UNSEEN_TILE_SAMPLING_RATE) {
                    for (int col = 0; col < ants.getCols(); col += UNSEEN_TILE_SAMPLING_RATE) {
                        Tile t = new Tile(row, col);
                        if (!ants.isVisible(t) && ants.getIlk(t) != Ilk.WATER) {
                            _unseenTiles.add(t);
                        }
                    }
                }
            } else {
                // remove any tiles that can be seen, run each turn
                for (Iterator<Tile> locIter = _unseenTiles.iterator(); locIter.hasNext(); ) {
                    Tile next = locIter.next();
                    if (ants.isVisible(next) || ants.getIlk(next) == Ilk.WATER) {
                        locIter.remove();
                    }
                }
            }

            avoidHills();
            seekFood();
            attackHills();
            exploreUnseenTiles();
            unblockHills();

            concludeTurn();
        } catch (Throwable t) {
            _log.error("Unexpected turn processing error", t);
            throw new RuntimeException("Unexpected", t);
        }
    }

    private void concludeTurn() {
        Ants ants = getAnts();
        TargetingPolicy.clearAssignments();
        _timeManager.turnDone();
        long start = _timeManager.getTurnStartMs();
        long finish = System.currentTimeMillis();
        _log.info(String.format("[[ # turn %d processing took %d ms, allowed %d.  Overall remaining: %d # ]]",
                                _turn - 1, finish - start, ants.getTurnTime(), ants.getTimeRemaining()));
        int numDestinations = _destinations.size();
        _destinations.clear();
        int numTargetedAnts = _toMove.size();
        _toMove.clear();
        int numUntargetedAnts = _untargetedAnts.size();
        _untargetedAnts.clear();
        if (numUntargetedAnts > 0) {
            _log.info(String.format("[[ # Moved %d out of %d ants # ]]", numTargetedAnts, numTargetedAnts + numUntargetedAnts));
        }

        if (_turn % UNSEEN_TILE_RECALC_PERIOD == 0) {
            // Resample unseen tiles next turn
            _unseenTiles = null;
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
        if (_log.isDebugEnabled()) {
            _log.debug(String.format("Checking for own-hill repulsion on %d untargeted ants", _untargetedAnts.size()));
        }
        _timeManager.nextStep(HILL_REPULSION_STEP_WEIGHT, "HillRepulsion");
        for (final RepulsionPolicy policy : _myHillRepulsions.values()) {
            policy.evacuate(_untargetedAnts,
                            _timeManager,
                            new RepulsionPolicy.HandleRepulsion() {
                                @Override
                                public void repulse(Tile ant, Tile destination) {
                                    moveToLocation(ant, destination);
                                }
                            });
        }
        if (_log.isDebugEnabled()) {
            _log.debug(String.format("Unblocked hills, %d elapsed, %d ms remaining in turn",
                                     System.currentTimeMillis() - start, getAnts().getTimeRemaining()));
        }
    }

    private void targetTiles(Collection<Tile> targets, TargetingPolicy policy) {
        final Ants ants = getAnts();
        long start = System.currentTimeMillis();
        _timeManager.nextStep(TARGETING_ROUTE_SORT_STEP_WEIGHT,
                              policy.toString() + ":Sorting");
        // We'll sort the list of targets relative to their distance to each ant
        // and attack them in phases to calculate optimal routes.  That way, each
        // ant will have a chance to plot *some* optimal routes to some number of targets
        // in order of naive distance ranking
        Map<Tile, ArrayList<Tile>> targetsRelativeToAntByAnt = new HashMap<Tile, ArrayList<Tile>>();
        for (final Tile ant : _untargetedAnts) {
            ArrayList<Tile> relativeToAnt = new ArrayList<Tile>(targets);
            Collections.sort(relativeToAnt, new Comparator<Tile>() {
                @Override
                public int compare(Tile o1, Tile o2) {
                    int d1 = ants.getDistance(ant, o1);
                    int d2 = ants.getDistance(ant, o2);
                    if (d1 == d2) {
                        return o1.compareTo(o2);
                    }
                    return d1 < d2 ? -1 : 1;
                }
            });
            targetsRelativeToAntByAnt.put(ant, relativeToAnt);
            if (_timeManager.stepTimeOverrun()) {
                break;
            }
        }

        _timeManager.nextStep(TARGETING_ROUTE_CALC_STEP_WEIGHT,
                              policy.toString() + ":Routing");

        // Track routes computed per-ant, to enable a bail on route calc's for a given ant when we hit
        // a policy-defined limit
        Map<Tile, Integer> routeCountByAnt = new HashMap<Tile, Integer>();
        List<Route> routes = new LinkedList<Route>();

        // Chunks of per-ant-sorted targets we'll walk through to compute A* routes
        final int targetStepSize = 3;
        for (int targetStart = 0; targetStart < targets.size(); targetStart += targetStepSize) {
            for (final Tile ant : _untargetedAnts) {
                int thisAntsRoutes = 0;
                if (routeCountByAnt.containsKey(ant)) {
                    thisAntsRoutes = routeCountByAnt.get(ant).intValue();
                }
                // Look at routes in order of targets' distance from the current ant
                ArrayList<Tile> relativeToAnt = targetsRelativeToAntByAnt.get(ant);
                if (relativeToAnt == null) {
                    _log.info(String.format("Timed out naively sorting targets for ant [%s], no routes...", ant));
                    break;
                }
                // Start traversing the list based on the current step
                for (int i = targetStart; i < targetStart + targetStepSize && i < relativeToAnt.size(); i++) {
                    Tile target = relativeToAnt.get(i);
                    long routeStart = System.currentTimeMillis();
                    try {
                        /*
                        if (ant.equals(target)) {
                            // Ant's already on target
                            continue;
                        }
                        */
                        Route route = new AStarRoute(ants, ant, target);
                        routes.add(route);
                        if (_log.isDebugEnabled()) {
                            _log.debug(String.format("%s route candidate for ant [%s] to tile [%s] (dist=%d): %s",
                                                     policy, ant, target, ants.getDistance(ant, target), route));
                        }
                        ++thisAntsRoutes;
                        if (policy.getPerAntRouteLimit() != null &&
                            thisAntsRoutes >= policy.getPerAntRouteLimit().intValue()) {
                            if (_log.isDebugEnabled()) {
                                _log.debug(String.format(
                                        "Capping routes for ant [%s] at %d under %s consideration, %d ms remaining...",
                                        ant, thisAntsRoutes, policy, ants.getTimeRemaining()));
                            }
                            break;
                        }
                    } catch (NoRouteException ex) {
                        if (_log.isDebugEnabled()) {
                            long elapsed = System.currentTimeMillis() - routeStart;
                            _log.debug(String.format("Cannot route from [%s] to [%s], spent %d ms looking",
                                                     ant, target, elapsed));
                        }
                    }
                    routeCountByAnt.put(ant, thisAntsRoutes);
                }
            }
            if (_timeManager.stepTimeOverrun()) {
                break;
            }
        }
        int candidateAnts = _untargetedAnts.size();
        if (_log.isDebugEnabled()) {
            _log.debug(String.format("Weighing %,d routes using %s, given %d candidate ants and %,d targets",
                                     routes.size(), policy, candidateAnts, targets.size()));
        }
        _timeManager.nextStep(TARGETING_ROUTE_MOVE_STEP_WEIGHT,
                              policy.toString() + ":Movement");
        if (routes.isEmpty()) {
            return;
        }

        int currentUntargetedAntCount = _untargetedAnts.size();
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
                --candidateAnts;
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
        if (candidateAnts > 0 &&
            currentUntargetedAntCount < (targets.size() * policy.getPerTargetAssignmentLimit())) {
            _log.info(String.format("No %s targeting movement for %d ants.",
                                    policy, candidateAnts));
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
            _untargetedAnts.remove(antLoc);
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
