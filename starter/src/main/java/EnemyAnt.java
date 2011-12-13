/**
 * Author: evan.pollan
 */
public class EnemyAnt extends Ant {

    private final int _team;

    public EnemyAnt(Tile position, int team) {
        super(position);
        _team = team;
    }

    public final int getTeam() {
        return _team;
    }
}
