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

    private final static int TIME_ALLOCATION_PAD = 50;
    private final static int MY_HILL_RADIUS_OF_REPULSION = 5;
    private final static int UNSEEN_TILE_SAMPLING_RATE = 5;
    private final static int UNSEEN_TILE_RECALC_PERIOD = 5;
    private final static float COMBAT_ZONE_SETUP = 1.0f;
    private final static float COMBAT_ZONE_COMBAT = 5.0f;
    private final static float INFLUENCE_MAP_SETUP = 1.0f;
    private final static float INFLUENCE_MAP_MOVEMENT = 1.0f;

    private List<CombatZone> _combatZones = new ArrayList<CombatZone>();
    private final Set<Tile> _untargetedAnts = new HashSet<Tile>();
    private final Set<Tile> _destinations = new HashSet<Tile>();
    private final Set<Tile> _toMove = new HashSet<Tile>();
    private Set<Tile> _unseenTiles = null;
    private Set<Tile> _enemyHills = new HashSet<Tile>();
    private final Map<Tile, DefenseZone> _myHillDefenses = new HashMap<Tile, DefenseZone>();
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

            engageInCombat();

            _timeManager.nextStep(INFLUENCE_MAP_MOVEMENT, "Influence Map Movement");
            long start = System.currentTimeMillis();
            // For those combat zones that got to do their thing fully, without timing out,
            // leave the ants they didn't move (presumably intentionally) out of consideration
            // for influence map-based movement.
            for (CombatZone z : _combatZones) {
                if (!z.getTimedOut()) {
                    for (Ant myAnt : z.getMyAntsInCombat()) {
                        _untargetedAnts.remove(myAnt.getPosition());
                    }
                }
            }
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
        long setupStart = System.currentTimeMillis();
        _log.info(String.format("[[...turn %d...]]", _turn));

        // Track targeted ants through turn
        _untargetedAnts.addAll(Registry.Instance.getMyAnts());

        // Time management
        int managedTimeAllocation = Registry.Instance.getTurnTime() - TIME_ALLOCATION_PAD;
        if (_timeManager == null || _timeManager.getTotalAllowed() != managedTimeAllocation) {
            _timeManager = new TimeManager(managedTimeAllocation);
        }
        _timeManager.turnStarted();

        // Don't defend old hills
        for (Iterator<Tile> oldHills = _myHillDefenses.keySet().iterator(); oldHills.hasNext(); ) {
            Tile oldHill = oldHills.next();
            if (!Registry.Instance.getMyHills().contains(oldHill)) {
                _log.debug("Removing defense policy for dead hill [%s]", oldHill);
                oldHills.remove();
            }
        }
        for (Tile myHill : Registry.Instance.getMyHills()) {
            DefenseZone defense = _myHillDefenses.get(myHill);
            if (defense == null) {
                // Aim to keep ants at least 6 moves away from my hills
                defense = new DefenseZone(myHill, MY_HILL_RADIUS_OF_REPULSION);
                _myHillDefenses.put(myHill, defense);
            }
        }

        if (_unseenTiles == null || _turn % UNSEEN_TILE_RECALC_PERIOD == 0) {
            _unseenTiles = new HashSet<Tile>();
            // Sparsely sample the board for unseen tiles
            for (int row = 0; row < Registry.Instance.getRows(); row += UNSEEN_TILE_SAMPLING_RATE) {
                for (int col = 0; col < Registry.Instance.getCols(); col += UNSEEN_TILE_SAMPLING_RATE) {
                    if (!Registry.Instance.isVisible(row, col) &&
                        Registry.Instance.getIlk(row, col) != Ilk.WATER) {
                        _unseenTiles.add(new Tile(row, col));
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

        // Manage enemy hills set
        for (Iterator<Tile> enemyHills = _enemyHills.iterator(); enemyHills.hasNext(); ) {
            Tile enemyHill = enemyHills.next();
            // Remove those that we've seen, but are confirmed dead/missing
            if (!Registry.Instance.getEnemyHills().contains(enemyHill) &&
                Registry.Instance.isVisible(enemyHill)) {
                enemyHills.remove();
            }
        }
        for (Tile enemyHill : Registry.Instance.getEnemyHills()) {
            _enemyHills.add(enemyHill);
        }

        _log.info("Unmanaged setup operations completed in %d ms", System.currentTimeMillis() - setupStart);

        if (_influence == null) {
            _influence = new TargetInfluenceMap();
        }
        _timeManager.nextStep(INFLUENCE_MAP_SETUP, "Influence Map Setup");
        long start = System.currentTimeMillis();
        _influence.reset(_unseenTiles,
                         _enemyHills,
                         _timeManager,
                         _myHillDefenses.values());
        _log.info("Set up influence map in %d ms", System.currentTimeMillis() - start);

        createCombatZones();
    }

    private void concludeTurn() {

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
        boolean timedOut = false;
        _combatZones.clear();
        _timeManager.nextStep(COMBAT_ZONE_SETUP, "Combat Zone Setup");
        Map<Tile, List<EnemyAnt>> enemiesInRange = null;
        Map<EnemyAnt, List<Tile>> alliesInRange = null;
        int range2 = Registry.Instance.getAttackRadius2() * 3 + 2;
        // Find each ant's close-by enemies
        for (Tile myPos : Registry.Instance.getMyAnts()) {
            List<EnemyAnt> inRange = null;
            for (Tile delta : Registry.Instance.getOffsets(range2)) {
                Tile possibleAnt = Registry.Instance.getTile(myPos, delta);
                if (Registry.Instance.getIlk(possibleAnt) == Ilk.ENEMY_ANT) {
                    _log.debug("COMBAT: candidates within %d (sq):  me [%s], enemy [%s]",
                               range2, myPos, possibleAnt);
                    EnemyAnt enemyAnt = Registry.Instance.getTeamedEnemyAnt(possibleAnt);
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
                if (_timeManager.stepTimeOverrun()) {
                    timedOut = true;
                    break;
                }
            }
            if (timedOut || _timeManager.stepTimeOverrun()) {
                timedOut = true;
                break;
            }
        }
        // Any ants that share "regional" enemies should be included in the same CombatZone
        while (enemiesInRange != null && enemiesInRange.size() > 0) {
            if (timedOut || _timeManager.stepTimeOverrun()) {
                timedOut = true;
                break;
            }
            Set<Ant> combatZoneAnts = new HashSet<Ant>(Registry.Instance.getMyAnts().size() +
                                                       Registry.Instance.getEnemyAnts().size());
            Tile me = enemiesInRange.keySet().iterator().next();
            if (combatZoneAnts.add(new Ant(me))) {
                timedOut = addNearEnemies(combatZoneAnts, enemiesInRange, alliesInRange, me);
                if (timedOut) {
                    break;
                }
            }
            CombatZone zone = new CombatZone(combatZoneAnts);
            _combatZones.add(zone);
        }
        _log.info("Took %d ms to create %d combat zones (timed out?: %b)",
                  System.currentTimeMillis() - start, _combatZones.size(), timedOut);
    }

    private boolean addNearEnemies(Set<Ant> combatZoneAnts,
                                   Map<Tile, List<EnemyAnt>> enemiesByAlly,
                                   Map<EnemyAnt, List<Tile>> alliesByEnemy,
                                   Tile me) {
        if (_timeManager.stepTimeOverrun()) {
            return true;
        }
        List<EnemyAnt> myEnemies = enemiesByAlly.get(me);
        enemiesByAlly.remove(me);
        for (EnemyAnt enemy : myEnemies) {
            if (combatZoneAnts.add(enemy)) {
                if (addNearAllies(combatZoneAnts, enemiesByAlly, alliesByEnemy, enemy)) {
                    return true;
                }
            }
            if (_timeManager.stepTimeOverrun()) {
                return true;
            }
        }
        return false;
    }

    private boolean addNearAllies(Set<Ant> combatZoneAnts,
                                  Map<Tile, List<EnemyAnt>> enemiesByAlly,
                                  Map<EnemyAnt, List<Tile>> alliesByEnemy,
                                  EnemyAnt enemy) {
        if (_timeManager.stepTimeOverrun()) {
            return true;
        }
        List<Tile> alliesSharingEnemy = alliesByEnemy.get(enemy);
        alliesByEnemy.remove(enemy);
        for (Tile ally : alliesSharingEnemy) {
            if (combatZoneAnts.add(new Ant(ally))) {
                if (addNearEnemies(combatZoneAnts, enemiesByAlly, alliesByEnemy, ally)) {
                    return true;
                }
            }
            if (_timeManager.stepTimeOverrun()) {
                return true;
            }
        }
        return false;
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
            if (_timeManager.stepTimeOverrun()) {
                break;
            }
            _log.debug("Took %d ms to engage in combat between %d ants",
                       System.currentTimeMillis() - start, zone.getAntCount());
        }

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
