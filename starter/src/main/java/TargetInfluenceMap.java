import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;

/**
 * Author: evan.pollan
 */
public class TargetInfluenceMap {

    private final double[][] _influence;
    private final boolean[][] _seeded;
    private static final LogFacade _log = LogFacade.get(TargetInfluenceMap.class);
    private static final float FOOD_TO_ANT_SCENT_RATIO = 0.1f;

    public TargetInfluenceMap() {
        Registry r = Registry.Instance;
        _influence = new double[r.getRows()][r.getCols()];
        _seeded = new boolean[r.getRows()][r.getCols()];
    }

    public void reset(Iterable<Tile> unseenTiles,
                      Iterable<Tile> enemyHills,
                      TimeManager time,
                      Iterable<DefenseZone> defenses,
                      Iterable<CombatZone> combatZones) {
        for (double[] row : _influence) {
            Arrays.fill(row, 0, row.length, 0.0);
        }
        for (boolean[] row : _seeded) {
            Arrays.fill(row, 0, row.length, false);
        }
        Registry r = Registry.Instance;
        seedInfluence(enemyHills, Integer.MAX_VALUE);
        // Food influence will decay as our number of ants increases
        double foodInfluence =
                Math.min(Integer.MAX_VALUE / 1.5,
                         Integer.MAX_VALUE / (r.getMyAnts().size() * FOOD_TO_ANT_SCENT_RATIO));
        seedInfluence(r.getFoodTiles(), foodInfluence);
        seedInfluence(unseenTiles, Integer.MAX_VALUE / 3.0);
        seedInfluence(r.getEnemyAnts(), Integer.MAX_VALUE / 5.0);

        for (DefenseZone defenseZone : defenses) {
            // Draw attention to invaders
            seedInfluence(defenseZone.getInvaders(), Integer.MAX_VALUE);
            // Draw ants towards lookout posts that have become invisible (try to
            // maintain some visibility of each hill)
            seedInfluence(defenseZone.getInvisibleLookouts(), Integer.MAX_VALUE / 2.0);
        }

        diffuse(time);

        for (Tile hill : r.getMyHills()) {
            // Discourage hill-squatting -- do this after diffusion
            _influence[hill.getRow()][hill.getCol()] = 0.0;
        }
        if (_log.isDebugEnabled() && false) {
            for (int row = 0; row < _influence.length; row++) {
                for (int col = 0; col < _influence[row].length; col++) {
                    if (r.isVisible(new Tile(row, col))) {
                        double alpha = _influence[row][col] / (Integer.MAX_VALUE * 1.0);
                        if (alpha > 1.0) {
                            alpha = 1.0;
                        }
                        if (alpha > 0.0 && alpha < 0.1) {
                            alpha = 0.1;
                        }
                        String state = String.format("v sfc 0 240 0 %.1f", alpha);
                        System.out.println(state);
                        System.out.format("v t %d %d\n", row, col);
                    }
                }
            }
        }
    }

    private void seedInfluence(Iterable<Tile> targets, double influence) {
        for (Tile t : targets) {
            _seeded[t.getRow()][t.getCol()] = true;
            _influence[t.getRow()][t.getCol()] = influence;
        }
    }

    private void diffuse(TimeManager time) {
        for (int rep = 0; rep < 50; rep++) {
            for (int row = 0; row < _influence.length; row++) {
                for (int col = 0; col < _influence[row].length; col++) {
                    propagate(row, col);
                }
            }
            for (int row = _influence.length - 1; row >= 0; row--) {
                for (int col = _influence[row].length - 1; col >= 0; col--) {
                    propagate(row, col);
                }
            }
            if (rep % 15 == 0 && time.stepTimeOverrun()) {
                break;
            }
        }
    }

    private void propagate(int row, int col) {
        if (_seeded[row][col] || Registry.Instance.getIlk(row, col) == Ilk.WATER) {
            return;
        }
        double diffused =
                get(row - 1, col, 0.25) +
                get(row + 1, col, 0.25) +
                get(row, col - 1, 0.25) +
                get(row, col + 1, 0.25);
        if (!_seeded[row][col] || diffused > _influence[row][col]) {
            _influence[row][col] = diffused;
        }
    }

    private double get(int row, int col, double contribution) {
        if (row < 0) {
            row = Registry.Instance.getRows() - 1;
        }
        if (row == Registry.Instance.getRows()) {
            row = 0;
        }
        if (col < 0) {
            col = Registry.Instance.getCols() - 1;
        }
        if (col == Registry.Instance.getCols()) {
            col = 0;
        }
        return _influence[row][col] * contribution;
    }

    private ArrayList<Tile> _moveBuffer = new ArrayList<Tile>(5);

    public Iterator<Tile> getTargets(Tile myAnt) {
        // Get possible move tiles (including no movement), ordered by influence
        _moveBuffer.clear();
        _moveBuffer.add(myAnt);
        addTargets(myAnt.getRow() - 1, myAnt.getCol());
        addTargets(myAnt.getRow() + 1, myAnt.getCol());
        addTargets(myAnt.getRow(), myAnt.getCol() - 1);
        addTargets(myAnt.getRow(), myAnt.getCol() + 1);
        Collections.sort(_moveBuffer, new Comparator<Tile>() {
            @Override
            public int compare(Tile o1, Tile o2) {
                int comparison = -Double.compare(_influence[o1.getRow()][o1.getCol()],
                                                 _influence[o2.getRow()][o2.getCol()]);
                if (comparison == 0) {
                    comparison = o1.compareTo(o2);
                }
                return comparison;
            }
        });
        if (_log.isDebugEnabled()) {
            for (Tile t : _moveBuffer) {
                _log.debug("[%s] move: [%s], influence=%f",
                           myAnt, t, _influence[t.getRow()][t.getCol()]);
            }
        }
        return _moveBuffer.iterator();
    }

    private void addTargets(int row, int col) {
        if (row < 0) {
            row = Registry.Instance.getRows() - 1;
        }
        if (row == Registry.Instance.getRows()) {
            row = 0;
        }
        if (col < 0) {
            col = Registry.Instance.getCols() - 1;
        }
        if (col == Registry.Instance.getCols()) {
            col = 0;
        }
        if (Registry.Instance.getIlk(row, col) != Ilk.WATER) {
            _moveBuffer.add(new Tile(row, col));
        }
    }
}
