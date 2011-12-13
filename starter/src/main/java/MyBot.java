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
        TargetingPolicy.add(TargetingPolicy.Type.Food, 1, 3, 15, null);
        TargetingPolicy.add(TargetingPolicy.Type.EnemyHill, 10, 3, null, null);
        TargetingPolicy.add(TargetingPolicy.Type.EnemyAnt, 3, 3, 15, 3);
        TargetingPolicy.add(TargetingPolicy.Type.UnseenTile, 1, 3, 15, null);
        TargetingPolicy.add(TargetingPolicy.Type.DefensiveStation, 1, 2, 10, null);
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
    private final static float COMBAT_ZONE_COMBAT = 5.0f;
    private final static float INFLUENCE_MAP_SETUP = 1.0f;
    private final static float INFLUENCE_MAP_MOVEMENT = 1.0f;

    private List<CombatZone> _combatZones = new ArrayList<CombatZone>();
    private final Set<Tile> _untargetedAnts = new HashSet<Tile>();
    private final Set<Tile> _destinations = new HashSet<Tile>();
    private final Set<Tile> _toMove = new HashSet<Tile>();
    private final List<AStarRoute> _routesToTargets = new ArrayList<AStarRoute>(100);
    private Set<Tile> _unseenTiles = null;
    private final Map<Tile, RepulsionPolicy> _myHillRepulsions = new HashMap<Tile, RepulsionPolicy>();
    private int _turn = 0;
    private static LogFacade _log;
    private TimeManager _timeManager = null;
    private TargetInfluenceMap _influence;

    /**
     * For every ant check every direction in fixed order (N, E, S, W) and move it if the tile is
     * passable.
     */
    @Override
    public void doTurn() {
        try {
            startTurn();
            avoidHills();

            boolean antsCloseToHills = false;
            for (RepulsionPolicy hillPolicy : _myHillRepulsions.values()) {
                if (hillPolicy.getDefenseZone().hasAntsWithinAlarmRadius()) {
                    antsCloseToHills = true;
                }
            }
            /*
//            if (antsCloseToHills) {
//                attackAnts();
//            }

            followBreadcrumbs();

            if (Registry.Instance.getMyAnts().size() < 10) {
                seekFood();
                attackHills();
            } else {
                attackHills();
                seekFood();
            }

//            if (!antsCloseToHills) {
//                attackAnts();
//            }

            defendMyHills();

            exploreUnseenTiles();

            unblockHills();
            */
            engageInCombat();

            _timeManager.nextStep(INFLUENCE_MAP_MOVEMENT, "Influence Map Movement");
            long start = System.currentTimeMillis();
            for (Tile ant : new ArrayList<Tile>(_untargetedAnts)) {
                for (Iterator<Tile> moves = _influence.getTargets(ant); moves.hasNext(); ) {
                    if (moveToLocation(ant, moves.next())) {
                        break;
                    }
                }
                if (_timeManager.stepTimeOverrun()) {
                    break;
                }
            }
            _log.debug("Moved %d ants in %d ms",
                       Registry.Instance.getMyAnts().size() - _untargetedAnts.size(),
                       System.currentTimeMillis() - start);

            concludeTurn();
        } catch (Throwable t) {
            _log.error("Unexpected turn processing error", t);
            throw new RuntimeException("Unexpected", t);
        }
    }

    private void startTurn() {
        _log.info(String.format("[[...turn %d...]]", _turn));

        // Track targeted ants through turn
        _untargetedAnts.addAll(Registry.Instance.getMyAnts());

        // Time management
        int managedTimeAllocation = Registry.Instance.getTurnTime() - TIME_ALLOCATION_PAD;
        if (_timeManager == null || _timeManager.getTotalAllowed() != managedTimeAllocation) {
            _timeManager = new TimeManager(managedTimeAllocation);
        }
        _timeManager.turnStarted();

        /*
        TargetingHistory.Instance.syncState(_turn);

        // Don't defend old hills
        for (Iterator<Tile> oldHills = _myHillRepulsions.keySet().iterator();
             oldHills.hasNext(); ) {
            Tile oldHill = oldHills.next();
            if (!Registry.Instance.getMyHills().contains(oldHill)) {
                _log.debug("Removing repulsion/defense policy for dead hill [%s]", oldHill);
                oldHills.remove();
            }
        }
        for (Tile myHill : Registry.Instance.getMyHills()) {
            RepulsionPolicy hillRepulsion = _myHillRepulsions.get(myHill);
            if (hillRepulsion == null) {
                long repulseStart = System.currentTimeMillis();
                // Aim to keep ants at least 6 moves away from my hills
                _myHillRepulsions.put(myHill, new RepulsionPolicy(myHill, MY_HILL_RADIUS_OF_REPULSION));
                _log.debug("Created RepulsionPolicy in %d ms", System.currentTimeMillis() - repulseStart);
            } else {
                hillRepulsion.getDefenseZone().leaveInnerDefensesStaffed(_untargetedAnts);
            }
        }
        */

        if (_unseenTiles == null || _turn % UNSEEN_TILE_RECALC_PERIOD == 0) {
            _unseenTiles = new HashSet<Tile>();
            // Sparsely sample the board for unseen tiles
            for (int row = 0; row < Registry.Instance.getRows(); row += UNSEEN_TILE_SAMPLING_RATE) {
                for (int col = 0; col < Registry.Instance.getCols(); col += UNSEEN_TILE_SAMPLING_RATE) {
                    Tile t = new Tile(row, col);
                    if (!Registry.Instance.isVisible(t) && Registry.Instance.getIlk(t) != Ilk.WATER) {
                        _unseenTiles.add(t);
                    }
                }
            }
        } else {
            // remove any tiles that can be seen, run each turn
            for (Iterator<Tile> locIter = _unseenTiles.iterator(); locIter.hasNext(); ) {
                Tile next = locIter.next();
                if (Registry.Instance.isVisible(next)) {
                    locIter.remove();
                }
            }
        }

        if (_influence == null) {
            _influence = new TargetInfluenceMap();
        }
        _timeManager.nextStep(INFLUENCE_MAP_SETUP, "Influence Map Setup");
        long start = System.currentTimeMillis();
        _influence.reset(_unseenTiles, _timeManager);
        _log.debug("Set up influence map in %d ms", System.currentTimeMillis() - start);

        createCombatZones();
    }

    private void concludeTurn() {
        TargetingPolicy.clearAssignments();

        _timeManager.turnDone();
        long start = _timeManager.getTurnStartMs();
        long finish = System.currentTimeMillis();
        _log.info(String.format("[[ # turn %d processing took %d ms, allowed %d.  Overall remaining: %d # ]]",
                                _turn, finish - start, Registry.Instance.getTurnTime(), Registry.Instance.getTimeRemaining()));

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
        for (Tile myHill : Registry.Instance.getMyHills()) {
            _destinations.add(myHill);
        }
    }

    private void createCombatZones() {
        long start = System.currentTimeMillis();
        _combatZones.clear();
        Map<Tile, List<EnemyAnt>> enemiesInRange = null;
        Map<EnemyAnt, List<Tile>> alliesInRange = null;
        int range2 = Registry.Instance.getAttackRadius2() * 3 + 2;
        // Find each ant's close-by enemies
        for (Tile myPos : Registry.Instance.getMyAnts()) {
            List<EnemyAnt> inRange = null;
            for (Tile enemyPos : Registry.Instance.getEnemyAnts()) {
                if (Registry.Instance.getDistance2(myPos, enemyPos) <= range2) {
                    _log.debug("COMBAT: candidates within %d (sq):  me [%s], enemy [%s]",
                               range2, myPos, enemyPos);
                    EnemyAnt enemyAnt = Registry.Instance.getTeamedEnemyAnt(enemyPos);
                    // Map enemies by ally
                    if (inRange == null) {
                        if (enemiesInRange == null) {
                            enemiesInRange = new HashMap<Tile, List<EnemyAnt>>();
                        }
                        inRange = enemiesInRange.get(myPos);
                        if (inRange == null) {
                            inRange = new ArrayList<EnemyAnt>(Registry.Instance.getEnemyAnts().size());
                            enemiesInRange.put(myPos, inRange);
                        }
                    }
                    inRange.add(enemyAnt);
                    // Map allies by enemy
                    if (alliesInRange == null) {
                        alliesInRange = new HashMap<EnemyAnt, List<Tile>>();
                    }
                    List<Tile> allies = alliesInRange.get(enemyAnt);
                    if (allies == null) {
                        allies = new ArrayList<Tile>(Registry.Instance.getMyAnts().size());
                        alliesInRange.put(enemyAnt, allies);
                    }
                    allies.add(myPos);
                }
            }
        }
        // Any ants that share "regional" enemies should be included in the same CombatZone
        while (enemiesInRange != null && enemiesInRange.size() > 0) {
            Set<Ant> combatZoneAnts = new HashSet<Ant>(Registry.Instance.getMyAnts().size() +
                                                       Registry.Instance.getEnemyAnts().size());
            Tile me = enemiesInRange.keySet().iterator().next();
            if (combatZoneAnts.add(new Ant(me))) {
                addNearEnemies(combatZoneAnts, enemiesInRange, alliesInRange, me);
            }
            _combatZones.add(new CombatZone(combatZoneAnts));
        }
        _log.debug("Took %d ms to create %d combat zones",
                   System.currentTimeMillis() - start, _combatZones.size());
    }

    private void addNearEnemies(Set<Ant> combatZoneAnts,
                                Map<Tile, List<EnemyAnt>> enemiesByAlly,
                                Map<EnemyAnt, List<Tile>> alliesByEnemy,
                                Tile me) {
        // Ants in combat zones have their movement managed by the zone -- don't separately target them
        _untargetedAnts.remove(me);
        List<EnemyAnt> myEnemies = enemiesByAlly.get(me);
        enemiesByAlly.remove(me);
        for (EnemyAnt enemy : myEnemies) {
            if (combatZoneAnts.add(enemy)) {
                addNearAllies(combatZoneAnts, enemiesByAlly, alliesByEnemy, enemy);
            }
        }
    }

    private void addNearAllies(Set<Ant> combatZoneAnts,
                               Map<Tile, List<EnemyAnt>> enemiesByAlly,
                               Map<EnemyAnt, List<Tile>> alliesByEnemy,
                               EnemyAnt enemy) {
        List<Tile> alliesSharingEnemy = alliesByEnemy.get(enemy);
        alliesByEnemy.remove(enemy);
        for (Tile ally : alliesSharingEnemy) {
            if (combatZoneAnts.add(new Ant(ally))) {
                addNearEnemies(combatZoneAnts, enemiesByAlly, alliesByEnemy, ally);
            }
        }
    }

    private void engageInCombat() {
        _timeManager.nextStep(COMBAT_ZONE_COMBAT, "Combat");
        for (CombatZone zone : _combatZones) {
            long start = System.currentTimeMillis();
            zone.move(_timeManager,
                      new MovementHandler() {
                          @Override
                          public boolean move(Tile ant, Tile nextTile) {
                              return moveToLocation(ant, nextTile);
                          }
                      });
            _log.debug("Took %d ms to engage in combat between %d ants",
                       System.currentTimeMillis() - start, zone.getAntCount());
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
        targetTiles(Registry.Instance.getFoodTiles(), TargetingPolicy.get(TargetingPolicy.Type.Food));
    }

    private void attackHills() {
        targetTiles(Registry.Instance.getEnemyHills(), TargetingPolicy.get(TargetingPolicy.Type.EnemyHill));
    }

    private void attackAnts() {
        targetTiles(Registry.Instance.getEnemyAnts(), TargetingPolicy.get(TargetingPolicy.Type.EnemyAnt));
    }

    private void defendMyHills() {
        Set<Tile> unoccupiedStations = new HashSet<Tile>();
        for (RepulsionPolicy repulsion : _myHillRepulsions.values()) {
            for (Tile station : repulsion.getDefenseZone().getInnerDefenses()) {
                if (!Registry.Instance.getMyAnts().contains(station)) {
                    unoccupiedStations.add(station);
                }
            }
        }
        targetTiles(unoccupiedStations, TargetingPolicy.get(TargetingPolicy.Type.DefensiveStation));
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
                   System.currentTimeMillis() - start, Registry.Instance.getTimeRemaining());
    }

    private void targetTiles(Collection<Tile> targets, TargetingPolicy policy) {
        long start = System.currentTimeMillis();
        _timeManager.nextStep(TARGETING_ROUTE_SORT_STEP_WEIGHT,
                              policy.toString() + ":Sorting");
        // We'll sort the list of targets relative to their distance to each ant
        // and attack them in phases to calculate optimal routes.  That way, each
        // ant will have a chance to plot *some* optimal routes to some number of targets
        // in order of naive distance ranking
        Map<Tile, List<Tile>> targetsRelativeToAntByAnt = sortTargetsByAnt(targets, policy);

        _timeManager.nextStep(TARGETING_ROUTE_CALC_STEP_WEIGHT,
                              policy.toString() + ":Routing");

        // Track routes computed per-ant, to enable a bail on route calc's for a given ant when we hit
        // a policy-defined limit
        routeToTargets(targets, policy, targetsRelativeToAntByAnt);

        _timeManager.nextStep(TARGETING_ROUTE_MOVE_STEP_WEIGHT,
                              policy.toString() + ":Movement");
        moveToTargets(targets, policy);
        _log.debug("%s targeting complete, %d elapsed, %d ms remaining in turn...",
                   policy, System.currentTimeMillis() - start, Registry.Instance.getTimeRemaining());
    }

    private Map<Tile, List<Tile>> sortTargetsByAnt(final Collection<Tile> targets,
                                                   final TargetingPolicy policy) {
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
        Map<Tile, List<Tile>> targetsRelativeToAntByAnt = new HashMap<Tile, List<Tile>>();
        for (final Tile ant : toTarget) {
            ArrayList<Tile> relativeToAnt = new ArrayList<Tile>(targetableTargets);
            Collections.sort(relativeToAnt, new Comparator<Tile>() {
                @Override
                public int compare(Tile o1, Tile o2) {
                    int d1 = Registry.Instance.getDistance(ant, o1);
                    int d2 = Registry.Instance.getDistance(ant, o2);
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
        return targetsRelativeToAntByAnt;
    }

    private void routeToTargets(final Collection<Tile> targets,
                                final TargetingPolicy policy,
                                final Map<Tile, List<Tile>> targetsRelativeToAntByAnt) {

        final Map<Tile, Integer> routeCountByAnt = new HashMap<Tile, Integer>();
        _routesToTargets.clear();
        // Chunks of per-ant-sorted targets we'll walk through to compute A* routes
        final int targetStepSize = 3;
        for (int targetStart = 0; targetStart < targets.size(); targetStart += targetStepSize) {
            boolean timedOut = false;
            for (final Tile ant : targetsRelativeToAntByAnt.keySet()) {
                int thisAntsRoutes = 0;
                if (routeCountByAnt.containsKey(ant)) {
                    thisAntsRoutes = routeCountByAnt.get(ant).intValue();
                    if (policy.getPerAntRouteLimit() != null &&
                        thisAntsRoutes >= policy.getPerAntRouteLimit().intValue()) {
                        break;
                    }
                }
                // Look at routes in order of targets' distance from the current ant
                final List<Tile> relativeToAnt = targetsRelativeToAntByAnt.get(ant);
                // Start traversing the list based on the current step
                for (int i = targetStart; i < targetStart + targetStepSize && i < relativeToAnt.size(); i++) {
                    Tile target = relativeToAnt.get(i);
                    long routeStart = System.currentTimeMillis();
                    try {
                        final AStarRoute route = new AStarRoute(ant, target);
                        _routesToTargets.add(route);
                        _log.debug("%s route candidate for ant [%s] to tile [%s] (dist=%d): %s",
                                   policy, ant, target, Registry.Instance.getDistance(ant, target), route);
                        ++thisAntsRoutes;
                        if (policy.getPerAntRouteLimit() != null &&
                            thisAntsRoutes >= policy.getPerAntRouteLimit().intValue()) {
                            _log.debug("Capping routes for ant [%s] at %d under %s consideration, %d ms remaining...",
                                       ant, thisAntsRoutes, policy, Registry.Instance.getTimeRemaining());
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
        _log.debug("Weighing %,d routes using %s, given %d candidate ants and %,d targets",
                   _routesToTargets.size(), policy, _untargetedAnts.size(), targets.size());
    }

    private void moveToTargets(final Collection<Tile> targets,
                               final TargetingPolicy policy) {
        if (_routesToTargets.isEmpty()) {
            return;
        }
        final int startingUntargetedAntCount = _untargetedAnts.size();
        X.ReferenceInt candidateAnts = new X.ReferenceInt(startingUntargetedAntCount);
        Collections.sort(_routesToTargets);
        boolean timedOut = false;
        final int numTargets = targets.size();

        if (policy.getPerTargetAssignmentFloor() != null) {
            // Map to facilitate collation per target
            Map<Tile, List<AStarRoute>> targetToRoutes = new HashMap<Tile, List<AStarRoute>>();
            // Ordered list of collated routes (ordered list of pointers to lists in map),
            // where each sub-list represent different routes to the same target
            List<List<AStarRoute>> collatedRoutes = new ArrayList<List<AStarRoute>>(targets.size());
            for (AStarRoute r : _routesToTargets) {
                List<AStarRoute> targetRoutes = targetToRoutes.get(r.getEnd());
                if (targetRoutes == null) {
                    targetRoutes = new LinkedList<AStarRoute>();
                    targetToRoutes.put(r.getEnd(), targetRoutes);
                    collatedRoutes.add(targetRoutes);
                }
                targetRoutes.add(r);
            }
            final X.Function<Boolean, AStarRoute> floorSatisfied =
                    new X.Function<Boolean, AStarRoute>() {
                        @Override
                        public Boolean eval(AStarRoute... args) {
                            if (!policy.needsMoreAssignments(args[0].getEnd())) {
                                _log.info("Met targeting floor for [%s]", args[0].getEnd());
                                return true;
                            }
                            return false;
                        }
                    };
            // First pass to try to satisfy floors
            for (List<AStarRoute> forTarget : collatedRoutes) {
                for (AStarRoute r : forTarget) {
                    if (_toMove.contains(r.getStart())) {
                        continue;
                    }
                    if (_timeManager.stepTimeOverrun()) {
                        timedOut = true;
                        break;
                    }
                    if (moveToTarget(r, policy, numTargets, floorSatisfied, candidateAnts)) {
                        // Move to routes for the next target -- we've me the floor for this target
                        break;
                    }
                }
                if (timedOut || _timeManager.stepTimeOverrun()) {
                    timedOut = true;
                    break;
                }
            }
        }
        for (AStarRoute route : _routesToTargets) {
            if (_toMove.contains(route.getStart())) {
                // More optimial route already assigned movement to this route's ant
                continue;
            }
            if (timedOut || _timeManager.stepTimeOverrun()) {
                timedOut = true;
                break;
            }
            if (moveToTarget(route, policy, numTargets, null, candidateAnts)) {
                break;
            }
        }
        if (candidateAnts.Value > 0 &&
            startingUntargetedAntCount <= (targets.size() * policy.getPerTargetAssignmentLimit())) {
            _log.info(String.format("No %s targeting movement for %d ants.",
                                    policy, candidateAnts.Value));
        }
    }

    // Returns true if we can stop trying to move to the next-best route
    private boolean moveToTarget(final AStarRoute route,
                                 final TargetingPolicy policy,
                                 final int numTargets,
                                 final X.Function<Boolean, AStarRoute> finishedCondition,
                                 final X.ReferenceInt candidateAnts) {
        if (policy.canAssign(route.getStart(), route.getEnd()) && move(route)) {
            policy.assign(route.getStart(), route.getEnd());
            Integer expireAfter = null;
            switch (policy.getType()) {
                case EnemyAnt:
                    expireAfter = (int) Math.floor(route.getDistance() / 4.0d);
                    break;
                default:
                    expireAfter = (int) Math.floor(route.getDistance() / 2.0d);
                    break;
            }
            TargetingHistory.Instance.create(route.getStart(), route.getEnd(), policy.getType(),
                                             route, expireAfter, false);
            --candidateAnts.Value;
            _log.debug(String.format("Move successful -- route assigned using %s", policy));
            if (policy.totalAssignmentsLimitReached(numTargets)) {
                _log.debug("Assigned the maximum number of %s routes (%s)",
                           policy, policy.getTotalAssignmentsLimit(numTargets));
                return true;
            } else if (finishedCondition != null && finishedCondition.eval(route)) {
                return true;
            }
        }
        return false;
    }

    private boolean moveInDirection(Tile antLoc, Aim direction) {
        // Track all moves, prevent collisions
        Tile newLoc = Registry.Instance.getTile(antLoc, direction);
        if (Registry.Instance.getIlk(newLoc).isUnoccupied() && !_destinations.contains(newLoc)) {
            Registry.Instance.issueOrder(antLoc, direction);
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
        List<Aim> directions = Registry.Instance.getDirections(antLoc, destLoc);
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
