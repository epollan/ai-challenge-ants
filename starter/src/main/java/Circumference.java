import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

/**
 * Encapsulates the concept of a circumference about a central point
 * given a radius.
 *
 * Author: evan.pollan
 */
public class Circumference implements Iterable<Tile> {
    
    // Offsets to travel in a given direction along the "circumference" of a
    // "circle" (diamond)
    private static enum CircumferenceDirection {

        SouthEast(1, 1),
        SouthWest(1, -1),
        NorthWest(-1, -1),
        NorthEast(-1, 1);

        private final int _rowDelta;
        private final int _colDelta;

        CircumferenceDirection(int rowDelta, int colDelta) {
            _rowDelta = rowDelta;
            _colDelta = colDelta;
        }

        int getRowDelta() {
            return _rowDelta;
        }

        int getColDelta() {
            return _colDelta;
        }
    }
    
    public static int getMaximumRadius() {
        if (_maximumRadius == 0) {
            _maximumRadius = (int) Math.floor((Math.min(Registry.Instance.getRows(), Registry.Instance.getCols()) - 1) / 2.0);
        }
        return _maximumRadius;
    }
    
    private static int _maximumRadius = 0;
    private final Tile _center;
    private final int _radius;
    private final LinkedList<Tile> _tiles = new LinkedList<Tile>();
    
    public Circumference(Tile center, int radius) {
        _center = center;
        _radius = Math.min(radius, getMaximumRadius());

        // Direction changes will correspond to due east/south/west/north
        final Map<Tile, CircumferenceDirection> directionChanges = new HashMap<Tile, CircumferenceDirection>();
        directionChanges.put(getTile(_center, -_radius, 0),
                             CircumferenceDirection.SouthEast);
        directionChanges.put(getTile(_center, 0, _radius),
                             CircumferenceDirection.SouthWest);
        directionChanges.put(getTile(_center, _radius, 0),
                             CircumferenceDirection.NorthWest);
        directionChanges.put(getTile(_center, 0, -_radius),
                             CircumferenceDirection.NorthEast);

        // Start north, heading southeast
        Tile start = getTile(_center, -_radius, 0);
        CircumferenceDirection currentDirection = CircumferenceDirection.SouthEast;
        Tile current = start;
        do {
            if (Registry.Instance.getIlk(current).isPassable()) {
                _tiles.add(current);
            }
            current = getTile(current, currentDirection.getRowDelta(), currentDirection.getColDelta());
            CircumferenceDirection newDirection = directionChanges.get(current);
            if (newDirection != null) {
                currentDirection = newDirection;
            }
        } while (!start.equals(current));
    }
    
    public Iterator<Tile> iterator() {
        return _tiles.iterator();
    }

    // O(n) -- only use during game setup
    public Collection<Tile> getRegion(Tile onCircumference, int distance) {
        // _tiles isn't sorted, so we have to do a Log(n) search
        int position = 0;
        boolean found = false;
        for (; position < _tiles.size(); position++) {
            if (_tiles.get(position).equals(onCircumference)) {
                found = true;
                break;
            }
        }
        if (!found) {
            throw new IllegalArgumentException(String.format("[%s] not on circumference", onCircumference));
        }
        int start = position - distance;
        int finish = position + distance;
        ArrayList<Tile> region = new ArrayList<Tile>(distance * 2 + 1);
        for (int i = start; i <= finish; i++) {
            int adjusted = i;
            if (i < 0) {
                adjusted += _tiles.size();
            }
            if (i >= _tiles.size()) {
                adjusted -= _tiles.size();
            }
            region.add(_tiles.get(adjusted));
        }
        return region;
    }

    public int getRadius() { return _radius; }

    public Tile getCenter() { return _center; }

    private Tile getTile(Tile start, int rowDelta, int colDelta) {
        int row = start.getRow() + rowDelta;
        if (row < 0) {
            row += Registry.Instance.getRows();
        } else if (row >= Registry.Instance.getRows()) {
            row -= Registry.Instance.getRows();
        }
        int col = start.getCol() + colDelta;
        if (col < 0) {
            col += Registry.Instance.getCols();
        } else if (col >= Registry.Instance.getCols()) {
            col -= Registry.Instance.getCols();
        }
        return new Tile(row, col);
    }
}
