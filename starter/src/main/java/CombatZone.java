import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Author: evan.pollan
 */
public class CombatZone {

    private Ant[][] _antMap;
    private List<Ant> _myAnts = new ArrayList<Ant>();
    private List<Ant> _allAnts = new ArrayList<Ant>();
    private int[] _indexes;
    private float _myAntLossFactor = -1.02f;

    private static final int HILL_PROXIMITY = 8;
    public static final int MAX_ANTS = 8; // 5^8 ~ 300K possible permutations
    private static final LogFacade _log = LogFacade.get(CombatZone.class);
    private static final X.ReferenceAim[] _moveDirections = {
            null,
            new X.ReferenceAim(Aim.NORTH),
            new X.ReferenceAim(Aim.EAST),
            new X.ReferenceAim(Aim.SOUTH),
            new X.ReferenceAim(Aim.WEST)
    };

    public CombatZone(Iterable<Ant> ants) {
        _antMap = new Ant[Registry.Instance.getRows()][Registry.Instance.getCols()]; // initialized to null
        boolean closeToHill = false;
        for (Ant a : ants) {
            if (a.getTeam() == 0) {
                _myAnts.add(a);
            } else {
                // Only put enemies in _allAnts for now
                if (!closeToHill) {
                    // Slightly different scoring heuristics when we're too close to a hill
                    for (Tile hill : Registry.Instance.getMyHills()) {
                        if (Registry.Instance.getDistance(a.getPosition(), hill) <= HILL_PROXIMITY) {
                            closeToHill = true;
                        }
                    }
                }
                _allAnts.add(a);
            }
        }
        // Whittle down the size of the list of ants considered for combat.  Err on the side
        // of retaining my ants.
        while (_myAnts.size() + _allAnts.size() > MAX_ANTS) {
            if (_allAnts.size() > _myAnts.size()) {
                _log.debug("COMBAT:  too many ants, removing enemy at [%s]", _allAnts.remove(0).getPosition());
                _log.debug("COMBAT:  too many ants, removing enemy at [%s]", _allAnts.remove(0).getPosition());
            }
            if (_myAnts.size() > _allAnts.size() + 3) {
                _log.debug("COMBAT:  too many ants, removing my ant at [%s]", _myAnts.remove(0).getPosition());
            }
        }
        _allAnts.addAll(0, _myAnts);
        for (Ant a : _allAnts) {
            _antMap[a.getPosition().getRow()][a.getPosition().getCol()] = a;
        }
        _indexes = new int[_allAnts.size()];
        if (closeToHill) {
            _myAntLossFactor = -0.95f;
        }
        else if ((1.0 * (_allAnts.size() - _myAnts.size())) / (1.0 * _allAnts.size()) <= 0.25) {
            _log.info("Incentivizing attacks -- 4-to-1 advantage");
            _myAntLossFactor = -0.50f;
        }
    }

    public int getAntCount() {
        return _allAnts.size();
    }

    public Iterable<Ant> getMyAntsInCombat() {
        return _myAnts;
    }

    private void incrementIndexes() {
        for (int pos = 0; pos < _indexes.length; ++pos) {
            _indexes[pos] += 1;
            if (_indexes[pos] >= _moveDirections.length) {
                _indexes[pos] = 0;
            } else {
                break;
            }
        }
    }

    private static class MoveKey {

        private X.ReferenceAim[] _move;
        private long _representation = 0L;
        private int _hash;
        private float _score;

        public MoveKey(int[] indexes, int myAntCount) {
            for (int i = 0; i < myAntCount; i++) {
                if (indexes[i] != 0) {
                    _representation += (indexes[i] * Math.pow(_moveDirections.length, i));
                }
            }
            _hash = new Long(_representation).hashCode();
        }

        public int hashCode() {
            return _hash;
        }

        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (other instanceof MoveKey) {
                return _representation == ((MoveKey) other)._representation;
            }
            return false;
        }

        public void makeStorable(X.ReferenceAim[] moveMatrix, int myAntCount, float score) {
            // Only store a copy of the move matrix once.  Each equivalent move from then on
            // will simply update this move's score
            _move = Arrays.copyOf(moveMatrix, myAntCount);
            _score = score;
        }

        public void updateScore(float score) {
            _score += score;
        }

        public float getScore() {
            return _score;
        }

        public void move(List<Ant> ants, MovementHandler handler) {
            List<Map.Entry<Tile, Tile>> details = new ArrayList<Map.Entry<Tile, Tile>>(ants.size());
            for (int i = 0; i < _move.length; i++) {
                Tile start = ants.get(i).getPosition();
                if (_move[i] == null) {
                    _log.debug("COMBAT: score %f move calls for [%s] stay put",
                               _score, start);
                    // Ignore non-moves
                    continue;
                }
                Tile end = Registry.Instance.getTile(start, _move[i].Value);
                details.add(new AbstractMap.SimpleEntry<Tile, Tile>(start, end));
            }
            while (details.size() > 0) {
                for (Iterator<Map.Entry<Tile, Tile>> iterator = details.iterator(); iterator.hasNext(); ) {
                    Map.Entry<Tile, Tile> detail = iterator.next();
                    // See if there's another tile blocking it
                    boolean blocked = false;
                    for (Map.Entry<Tile, Tile> other : details) {
                        if (other.getKey().equals(detail.getValue())) {
                            _log.debug("COMBAT: waiting to enact move of [%s] to [%s] so that tile is cleared",
                                       detail.getKey(), detail.getValue());
                            blocked = true;
                            break;
                        }
                    }
                    if (!blocked) {
                        if (handler.move(detail.getKey(), detail.getValue())) {
                            _log.debug("COMBAT: moving [%s] to [%s] (score=%f)",
                                       detail.getKey(), detail.getValue(), _score);
                        } else {
                            _log.info("COMBAT: couldn't move [%s] to [%s] (score=%f)",
                                      detail.getKey(), detail.getValue(), _score);
                        }
                        // We've enacted this move -- whack it.
                        iterator.remove();
                    }
                }
            }
        }
    }

    public void move(TimeManager timeManager, MovementHandler handler) {
        // Each bucket represents the aim each ant will take
        // for a particular set of possible moves.  Take _allAnts.size()-length
        // permutations given the set of 5 possible move directions, score each
        // one per the combat rules, and update the influence map with each
        // permutation's "score"
        X.ReferenceAim[] moveMatrix = new X.ReferenceAim[_allAnts.size()];
        List<Set<Aim>> invalidMoves = new ArrayList<Set<Aim>>(_allAnts.size());
        for (X.ReferenceAim moves : moveMatrix) {
            // no known invalid moves, yet
            invalidMoves.add(null);
        }

        Map<MoveKey, MoveKey> moves =
                new HashMap<MoveKey, MoveKey>((int) Math.pow(_moveDirections.length, _myAnts.size()));

        int permutations = (int) Math.pow(_moveDirections.length, _allAnts.size());
        for (int permutation = 0; permutation < permutations; permutation++) {
            boolean validMove = true;
            for (int matrixPos = 0; matrixPos < moveMatrix.length; matrixPos++) {
                X.ReferenceAim aim = _moveDirections[_indexes[matrixPos]];
                if (aim != null) {
                    Set<Aim> invalid = invalidMoves.get(matrixPos);
                    if (invalid != null && invalid.contains(aim.Value)) {
                        // Don't both with current move b/c it's calling for one of the ants to
                        // do something it can't do
                        validMove = false;
                        break;
                    }
                }
                moveMatrix[matrixPos] = aim;
            }
            if (!validMove) {
                continue;
            }
            try {
                float score = updateInfluenceMap(moveMatrix, invalidMoves);
                MoveKey key = new MoveKey(_indexes, _myAnts.size());
                MoveKey stored = moves.get(key);
                if (stored == null) {
                    key.makeStorable(moveMatrix, _myAnts.size(), score);
                    moves.put(key, key);
                } else {
                    stored.updateScore(score);
                }
            } catch (IllegalArgumentException ex) {
                // Illegal move
            }
            if (timeManager.stepTimeOverrun()) {
                _log.info("Timed out after scoring %d of %d combat move permutations",
                          permutation, permutations);
                break;
            }
            incrementIndexes();
        }

        MoveKey bestMove = null;
        permutations = 0;
        for (MoveKey key : moves.keySet()) {
            if (bestMove == null || key.getScore() > bestMove.getScore()) {
                bestMove = key;
            }
            permutations++;
            if (timeManager.stepTimeOverrun()) {
                _log.info("Timed out after comparing %d of %d combat moves",
                          permutations, moves.size());
                break;
            }
        }
        if (bestMove != null) {
            bestMove.move(_myAnts, handler);
            _log.debug("COMBAT: executed score=%f move", bestMove.getScore());
        } else {
            _log.info("COMBAT: could not compute a best combat move");
        }
    }

    // Reusable containers to support scoring algorithm
    private Map<Ant, Ant[]> _antToNearbyEnemiesBuffer = new HashMap<Ant, Ant[]>();
    private Set<Tile> _movePositionBuffer = new HashSet<Tile>();

    private float updateInfluenceMap(X.ReferenceAim[] move, List<Set<Aim>> invalidMoves) {
        try {
            int moveCount = 0;
            _movePositionBuffer.clear();
            for (int i = 0; i < move.length; i++) {
                Ant ant = _allAnts.get(i);
                if (move[i] == null) {
                    // This ant is to stay put for this move
                    ant.setNextCombatPosition(ant.getPosition());
                    continue;
                }
                moveCount++;
                Tile next = Registry.Instance.getTile(ant.getPosition(), move[i].Value);
                if (Registry.Instance.getIlk(next) == Ilk.WATER) {
                    Set<Aim> invalidMove = invalidMoves.get(i);
                    if (invalidMove == null) {
                        invalidMove = new HashSet<Aim>();
                        invalidMoves.set(i, invalidMove);
                    }
                    invalidMove.add(move[i].Value);
                    throw new IllegalArgumentException(String.format("COMBAT: Cannot move [%s] %s due to water",
                                                                     ant.getPosition(), move[i].Value));
                }
                // Swap according to move
                _antMap[next.getRow()][next.getCol()] = ant;
                _antMap[ant.getPosition().getRow()][ant.getPosition().getCol()] = null;
                _movePositionBuffer.add(next);
                ant.setNextCombatPosition(next);
            }
            if (_movePositionBuffer.size() < moveCount) {
                throw new IllegalArgumentException("COMBAT: Illegal move -- multiple ants sharing tile");
            }

            _antToNearbyEnemiesBuffer.clear();
            for (Ant a : _allAnts) {
                // Exclude either my "team" or an enemy ant's team to get enemies
                getNearbyAnts(a.getNextCombatPosition(), // next position is relative to _antMap
                              a.getTeam());
                if (_nearbyBuffer.size() > 0) {
                    Ant[] enemies = new Ant[_nearbyBuffer.size()];
                    _nearbyBuffer.toArray(enemies);
                    _antToNearbyEnemiesBuffer.put(a, enemies);
                }
            }

            float score = 0.0f;
            for (Ant a : _allAnts) {
                Ant[] enemies = _antToNearbyEnemiesBuffer.get(a);
                int weakness = (enemies != null) ? enemies.length : 0;
                if (weakness == 0) {
                    // No enemies nearby -- can't be attacked
                    continue;
                }
                int minEnemyWeakness = Integer.MAX_VALUE;
                for (Ant enemy : enemies) {
                    Ant[] enemyEnemies = _antToNearbyEnemiesBuffer.get(enemy);
                    int enemyWeakness = (enemyEnemies != null) ? enemyEnemies.length : 0;
                    minEnemyWeakness = Math.min(minEnemyWeakness, enemyWeakness);
                }
                if (minEnemyWeakness <= weakness) {
                    // Ant dies -- there exists at least one enemy as strong or stronger within range
                    score += (a.getTeam() != 0) ? 1.0f : _myAntLossFactor;
                    _log.debug("COMBAT:  [%s] (team=%d) dies when in tile [%s]",
                               a.getPosition(), a.getTeam(), a.getNextCombatPosition());
                }
            }
            return score;
        } finally {
            // Restore ants to their previous location
            for (Ant a : _allAnts) {
                if (!a.getPosition().equals(a.getNextCombatPosition())) {
                    // Null out their 'next' position
                    Tile next = a.getNextCombatPosition();
                    _antMap[next.getRow()][next.getCol()] = null;
                    // Place in original homes
                    _antMap[a.getPosition().getRow()][a.getPosition().getCol()] = a;
                }
            }
        }
    }

    // Reusable container to support "nearby" calculation
    private ArrayList<Ant> _nearbyBuffer;

    private void getNearbyAnts(Tile antMapLoc, int teamToExclude) {
        if (_nearbyBuffer == null) {
            _nearbyBuffer = new ArrayList<Ant>(_allAnts.size());
        } else {
            _nearbyBuffer.clear();
        }
        for (Tile delta : Registry.Instance.getOffsets(Registry.Instance.getAttackRadius2())) {
            Tile inRadius = Registry.Instance.getTile(antMapLoc, delta);
            Ant a = _antMap[inRadius.getRow()][inRadius.getCol()];
            if (a != null && a.getTeam() != teamToExclude) {
                _nearbyBuffer.add(a);
            }
        }
    }
}
