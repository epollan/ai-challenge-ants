/**
 * Author: evan.pollan
 */
public class Ant {

    private Tile _currentPosition;
    private Tile _nextCombatPosition;

    public Ant(Tile currentPosition) {
        _currentPosition = currentPosition;
    }

    public final void setNextCombatPosition(Tile t) {
        _nextCombatPosition = t;
    }

    public final Tile getNextCombatPosition() {
        return _nextCombatPosition;
    }

    public final Tile getPosition() {
        return _currentPosition;
    }

    // Not an enemy ant -- team "zero"
    public int getTeam() {
        return 0;
    }

    public int hashCode() {
        return _currentPosition.hashCode();
    }

    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other instanceof Ant) {
            return _currentPosition.equals(((Ant) other)._currentPosition);
        }
        return false;
    }
}
