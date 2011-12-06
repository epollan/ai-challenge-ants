import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.Level;
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
        LogFacade.setProdConfig();
        _log = LogFacade.get(MyBot.class);
        _log.info("[[GAME START]]");
        new MyBot().readSystemInput();
    }

    // Policies, tuning parameters
    static {
        TargetingPolicy.add(TargetingPolicy.Type.Food, 1, 3, 15);
        TargetingPolicy.add(TargetingPolicy.Type.EnemyHill, 10, 3, null);
        TargetingPolicy.add(TargetingPolicy.Type.EnemyAnt, 3, 3, 15);
        TargetingPolicy.add(TargetingPolicy.Type.UnseenTile, 1, 3, 15);
    }

    private final static int TIME_ALLOCATION_PAD = 50;
    private final static int MY_HILL_RADIUS_OF_REPULSION = 6;
    private final static int UNSEEN_TILE_SAMPLING_RATE = 5;
    private final static int UNSEEN_TILE_RECALC_PERIOD = 5;
    private final static float BREADCRUMB_FOLLOWING_WEIGHT = 0.5f;
    private final static float TARGETING_ROUTE_SORT_STEP_WEIGHT = 0.4f;
    private final static float TARGETING_ROUTE_CALC_STEP_WEIGHT = 1.8f;
    private final static float TARGETING_ROUTE_MOVE_STEP_WEIGHT = 1.0f;
    private final static float HILL_REPULSION_STEP_WEIGHT = 1.0f;

    private final Set<Tile> _untargetedAnts = new HashSet<Tile>();
    private final Set<Tile> _destinations = new HashSet<Tile>();
    private final Set<Tile> _toMove = new HashSet<Tile>();
    private Set<Tile> _unseenTiles = null;
    private final Map<Tile, RepulsionPolicy> _myHillRepulsions = new HashMap<Tile, RepulsionPolicy>();
    private int _turn = 0;
    private static LogFacade _log;
    private TimeManager _timeManager = null;

    /**
     * For every ant check every direction in fixed order (N, E, S, W) and move it if the tile is
     * passable.
     */
    @Override
    public void doTurn() {
        try {

            avoidHills();
            followBreadcrumbs();
            if (Ants.Instance.getMyAnts().size() < 10) {
                seekFood();
                attackHills();
            } else {
                attackHills();
                seekFood();
            }
            attackAnts();
            exploreUnseenTiles();
            unblockHills();

            concludeTurn();
        } catch (Throwable t) {
            _log.error("Unexpected turn processing error", t);
            throw new RuntimeException("Unexpected", t);
        }
    }

    private void startTurn() {
        _log.info(String.format("[[...turn %d...]]", _turn));

        // Track targeted ants through turn
        _untargetedAnts.addAll(Ants.Instance.getMyAnts());

        // Time management
        int managedTimeAllocation = Ants.Instance.getTurnTime() - TIME_ALLOCATION_PAD;
        if (_timeManager == null || _timeManager.getTotalAllowed() != managedTimeAllocation) {
            _timeManager = new TimeManager(managedTimeAllocation);
        }
        _timeManager.turnStarted();

        TargetingHistory.Instance.syncState(_turn);

        for (Tile myHill : Ants.Instance.getMyHills()) {
            if (!_myHillRepulsions.containsKey(myHill)) {
                long repulseStart = System.currentTimeMillis();
                // Aim to keep ants at least 6 moves away from my hills
                _myHillRepulsions.put(myHill, new RepulsionPolicy(myHill, MY_HILL_RADIUS_OF_REPULSION));
                _log.debug("Created RepulsionPolicy in %d ms", System.currentTimeMillis() - repulseStart);
            }
        }

        if (_unseenTiles == null || _turn % UNSEEN_TILE_RECALC_PERIOD == 0) {
            _unseenTiles = new HashSet<Tile>();
            // Sparsely sample the board for unseen tiles
            for (int row = 0; row < Ants.Instance.getRows(); row += UNSEEN_TILE_SAMPLING_RATE) {
                for (int col = 0; col < Ants.Instance.getCols(); col += UNSEEN_TILE_SAMPLING_RATE) {
                    Tile t = new Tile(row, col);
                    if (!Ants.Instance.isVisible(t) && Ants.Instance.getIlk(t) != Ilk.WATER) {
                        _unseenTiles.add(t);
                    }
                }
            }
        } else {
            // remove any tiles that can be seen, run each turn
            for (Iterator<Tile> locIter = _unseenTiles.iterator(); locIter.hasNext(); ) {
                Tile next = locIter.next();
                if (Ants.Instance.isVisible(next)) {
                    locIter.remove();
                }
            }
        }

    }

    private void concludeTurn() {
        TargetingPolicy.clearAssignments();

        _timeManager.turnDone();
        long start = _timeManager.getTurnStartMs();
        long finish = System.currentTimeMillis();
        _log.info(String.format("[[ # turn %d processing took %d ms, allowed %d.  Overall remaining: %d # ]]",
                                _turn, finish - start, Ants.Instance.getTurnTime(), Ants.Instance.getTimeRemaining()));
        int numDestinations = _destinations.size();
        _destinations.clear();
        int numTargetedAnts = _toMove.size();
        _toMove.clear();
        int numUntargetedAnts = _untargetedAnts.size();
        _untargetedAnts.clear();
        if (numUntargetedAnts > 0) {
            _log.info(String.format("[[ # Moved %d out of %d ants # ]]", numTargetedAnts, numTargetedAnts + numUntargetedAnts));
        }
        _turn++;
    }

    private void avoidHills() {
        for (Tile myHill : Ants.Instance.getMyHills()) {
            _destinations.add(myHill);
        }
    }

    private void followBreadcrumbs() {
        // Shallow copy iteration in case an ant gets targeted
        _timeManager.nextStep(BREADCRUMB_FOLLOWING_WEIGHT, "FollowingBreadcrumbs");
        int initialCount = _untargetedAnts.size();
        for (Tile ant : new ArrayList<Tile>(_untargetedAnts)) {
            if (_timeManager.stepTimeOverrun()) {
                break;
            }
            TargetingHistory.Instance.followBreadcrumb(ant, new TargetingPolicy.TargetingHandler() {
                @Override
                public boolean move(Tile ant, Tile nextTile, Tile finalDestination, TargetingPolicy.Type type) {
                    TargetingPolicy policy = TargetingPolicy.get(type);
                    if (policy.canAssign(ant, finalDestination) && moveToLocation(ant, nextTile)) {
                        policy.assign(ant, finalDestination);
                        return true;
                    }
                    return false;
                }
            });
        }
        _log.info(String.format("Moved %d ants based on historical targeting",
                                initialCount - _untargetedAnts.size()));
    }

    private void seekFood() {
        targetTiles(Ants.Instance.getFoodTiles(), TargetingPolicy.get(TargetingPolicy.Type.Food));
    }

    private void attackHills() {
        targetTiles(Ants.Instance.getEnemyHills(), TargetingPolicy.get(TargetingPolicy.Type.EnemyHill));
    }

    private void attackAnts() {
        targetTiles(Ants.Instance.getEnemyAnts(), TargetingPolicy.get(TargetingPolicy.Type.EnemyAnt));
    }

    private void exploreUnseenTiles() {
        targetTiles(_unseenTiles, TargetingPolicy.get(TargetingPolicy.Type.UnseenTile));
    }

    private void unblockHills() {
        long start = System.currentTimeMillis();
        // local references need to be final to be passed to anonymous inner class
        _log.debug("Checking for own-hill repulsion on %d untargeted ants", _untargetedAnts.size());
        _timeManager.nextStep(HILL_REPULSION_STEP_WEIGHT, "HillRepulsion");
        for (final RepulsionPolicy policy : _myHillRepulsions.values()) {
            policy.evacuate(_untargetedAnts,
                            _timeManager,
                            new MovementHandler() {
                                @Override
                                public boolean move(Tile ant, Tile destination) {
                                    return moveToLocation(ant, destination);
                                }
                            });
        }
        _log.debug("Unblocked hills, %d elapsed, %d ms remaining in turn",
                   System.currentTimeMillis() - start, Ants.Instance.getTimeRemaining());
    }

    private void targetTiles(Collection<Tile> targets, TargetingPolicy policy) {
        long start = System.currentTimeMillis();
        _timeManager.nextStep(TARGETING_ROUTE_SORT_STEP_WEIGHT,
                              policy.toString() + ":Sorting");
        // We'll sort the list of targets relative to their distance to each ant
        // and attack them in phases to calculate optimal routes.  That way, each
        // ant will have a chance to plot *some* optimal routes to some number of targets
        // in order of naive distance ranking
        Map<Tile, ArrayList<Tile>> targetsRelativeToAntByAnt = new HashMap<Tile, ArrayList<Tile>>();
        ArrayList<Tile> targetableTargets = new ArrayList<Tile>(targets);
        for (int i = targetableTargets.size() - 1; i >= 0; i--) {
            if (!policy.canAssign(null, targetableTargets.get(i))) {
                targetableTargets.remove(i);
            }
        }
        if (targetableTargets.size() < targets.size()) {
            _log.debug("Skipping targeting of %d %s due to historical targeting routes",
                       (targets.size() - targetableTargets.size()), policy.getType());
        }
        ArrayList<Tile> toTarget = new ArrayList<Tile>(_untargetedAnts.size());
        if (policy.getAntLimit() != null) {
            for (Tile ant : _untargetedAnts) {
                toTarget.add(ant);
                if (toTarget.size() >= policy.getAntLimit().intValue()) {
                    break;
                }
            }
        } else {
            toTarget.addAll(_untargetedAnts);
        }
        for (final Tile ant : toTarget) {
            ArrayList<Tile> relativeToAnt = new ArrayList<Tile>(targetableTargets);
            Collections.sort(relativeToAnt, new Comparator<Tile>() {
                @Override
                public int compare(Tile o1, Tile o2) {
                    int d1 = Ants.Instance.getDistance(ant, o1);
                    int d2 = Ants.Instance.getDistance(ant, o2);
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
        List<AStarRoute> routes = new LinkedList<AStarRoute>();

        // Chunks of per-ant-sorted targets we'll walk through to compute A* routes
        final int targetStepSize = 3;
        for (int targetStart = 0; targetStart < targets.size(); targetStart += targetStepSize) {
            boolean timedOut = false;
            for (final Tile ant : toTarget) {
                int thisAntsRoutes = 0;
                if (routeCountByAnt.containsKey(ant)) {
                    thisAntsRoutes = routeCountByAnt.get(ant).intValue();
                    if (policy.getPerAntRouteLimit() != null &&
                        thisAntsRoutes >= policy.getPerAntRouteLimit().intValue()) {
                        break;
                    }
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
                        AStarRoute route = new AStarRoute(ant, target);
                        routes.add(route);
                        _log.debug("%s route candidate for ant [%s] to tile [%s] (dist=%d): %s",
                                   policy, ant, target, Ants.Instance.getDistance(ant, target), route);
                        ++thisAntsRoutes;
                        if (policy.getPerAntRouteLimit() != null &&
                            thisAntsRoutes >= policy.getPerAntRouteLimit().intValue()) {
                            _log.debug("Capping routes for ant [%s] at %d under %s consideration, %d ms remaining...",
                                       ant, thisAntsRoutes, policy, Ants.Instance.getTimeRemaining());
                            break;
                        }
                    } catch (NoRouteException ex) {
                        _log.debug("Cannot route from [%s] to [%s], spent %d ms looking",
                                   ant, target, System.currentTimeMillis() - routeStart);
                    }
                    if (_timeManager.stepTimeOverrun()) {
                        timedOut = true;
                        break;
                    }
                }
                routeCountByAnt.put(ant, thisAntsRoutes);
                if (timedOut) {
                    break;
                }
            }
            if (timedOut) {
                break;
            }
        }
        int candidateAnts = _untargetedAnts.size();
        _log.debug("Weighing %,d routes using %s, given %d candidate ants and %,d targets",
                   routes.size(), policy, candidateAnts, targets.size());
        _timeManager.nextStep(TARGETING_ROUTE_MOVE_STEP_WEIGHT,
                              policy.toString() + ":Movement");
        if (routes.isEmpty()) {
            return;
        }

        int currentUntargetedAntCount = _untargetedAnts.size();
        Collections.sort(routes);
        for (AStarRoute route : routes) {
            if (_toMove.contains(route.getStart())) {
                // More optimial route already assigned movement to this route's ant
                continue;
            }
            if (_timeManager.stepTimeOverrun()) {
                break;
            }
            if (policy.canAssign(route.getStart(), route.getEnd()) && move(route)) {
                policy.assign(route.getStart(), route.getEnd());
                Integer expireAfter =
                        policy.getType().equals(TargetingPolicy.Type.UnseenTile) ? 10 : null;
                TargetingHistory.Instance.create(route.getStart(), route.getEnd(), policy.getType(), route, expireAfter, false);
                --candidateAnts;
                _log.debug(String.format("Move successful -- route assigned using %s", policy));
                if (policy.totalAssignmentsLimitReached(targets.size())) {
                    _log.debug("Assigned the maximum number of %s routes (%s)",
                               policy, policy.getTotalAssignmentsLimit(targets.size()));
                    break;
                }
            }
        }
        if (candidateAnts > 0 &&
            currentUntargetedAntCount <= (targets.size() * policy.getPerTargetAssignmentLimit())) {
            _log.info(String.format("No %s targeting movement for %d ants.",
                                    policy, candidateAnts));
        }
        _log.debug("%s targeting complete, %d elapsed, %d ms remaining in turn...",
                   policy, System.currentTimeMillis() - start, Ants.Instance.getTimeRemaining());
    }

    private boolean moveInDirection(Tile antLoc, Aim direction) {
        // Track all moves, prevent collisions
        Tile newLoc = Ants.Instance.getTile(antLoc, direction);
        if (Ants.Instance.getIlk(newLoc).isUnoccupied() && !_destinations.contains(newLoc)) {
            Ants.Instance.issueOrder(antLoc, direction);
            _destinations.add(newLoc);
            _toMove.add(antLoc);
            _untargetedAnts.remove(antLoc);
            _log.debug("Moving ant at [%s] %s to [%s]", antLoc, direction, newLoc);
            return true;
        } else {
            return false;
        }
    }

    private boolean moveToLocation(Tile antLoc, Tile destLoc) {
        List<Aim> directions = Ants.Instance.getDirections(antLoc, destLoc);
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
