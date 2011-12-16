import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * TODO
 * Author: evan.pollan
 */
public class CombatInfluenceMap {

    private final int _team;
    private final Focus[][] _map;
    private final List<Tile> _moveBuffer = new ArrayList<Tile>(4);

    public CombatInfluenceMap(int team) {
        _team = team;
        Registry r = Registry.Instance;
        // Allocate heap for focus objects once
        _map = new Focus[r.getRows()][r.getCols()];
        for (Focus[] row : _map) {
            for (int col = 0; col < row.length; col++) {
                row[col] = new Focus();
            }
        }
    }

    public final int getTeam() {
        return _team;
    }

    public void reset(Iterable<Ant> current) {
        // Zero everything out
        for (Focus[] row : _map) {
            for (int col = 0; col < row.length; col++) {
                row[col].Value = 0.0;
                if (row[col].Contributors != null) {
                    row[col].Contributors.clear();
                }
            }
        }
        // Apply influence for each ant, then apply probablistic
        // influence for each possible move
        for (Ant a : current) {
            assignInfluence(a, a.getPosition(), 1.0);
            _moveBuffer.clear();
            for (Aim aim : Aim.values()) {
                Tile target = Registry.Instance.getTile(a.getPosition(), aim);
                if (Registry.Instance.getIlk(target).isPassable()) {
                    _moveBuffer.add(target);
                }
            }
            if (_moveBuffer.size() > 0) {
                for (Tile move : _moveBuffer) {
                    assignInfluence(a, move, 1.0 / (_moveBuffer.size() * 1.0));
                }
            }
        }
    }

    private void assignInfluence(Ant a, Tile hypothetical, double influence) {
        for (Tile delta : Registry.Instance.getOffsets(Registry.Instance.getAttackRadius2())) {
            Tile point = Registry.Instance.getTile(hypothetical, delta);
            Focus f = _map[point.getRow()][point.getCol()];
            if (f.Value < 1.0) {
                // Only bump up the invfluence for those squares that don't have full focus
                f.Value += influence;
                f.addContributor(a);
            }
        }
    }

    private static class Focus {

        public double Value = 0.0;
        public List<Ant> Contributors;

        public void addContributor(Ant a) {
            if (Contributors == null) {
                Contributors = new ArrayList<Ant>(5);
            }
            Contributors.add(a);
        }

        public boolean containsContributor(Ant a) {
            return Contributors != null && Contributors.contains(a);
        }
    }
}
