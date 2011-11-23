/**
 * Represents a route from one tile to another.
 */
public class Route implements Comparable<Route> {

    protected final Tile _start;
    protected final Tile _end;
    protected int _distance;
    protected int _hash;
    
    public Route(Tile start, Tile end, int distance) {
        _start = start;
        _end = end;
        _distance = distance;
        _hash = _start.hashCode() * Ants.MAX_MAP_SIZE * Ants.MAX_MAP_SIZE + _end.hashCode();
    }

    protected Route(Tile start, Tile end) {
        _start = start;
        _end = end;
    }
    
    public Tile getStart() {
    	return _start;
    }
    
    public Tile getEnd() {
    	return _end;
    }
    
    public int getDistance() {
        return _distance;
    }
    
    @Override
    public int compareTo(Route route) {
        return _distance - route._distance;
    }
    
    @Override
    public int hashCode() {
    	return _hash;
    }
    
    @Override
    public boolean equals(Object o) {
        boolean result = false;
        if (o instanceof Route) {
            Route route = (Route)o;
            result = _start.equals(route._start) && _end.equals(route._end);
        }
        return result;
    }
}