import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Author: evan.pollan
 */
public class DefenseZone {

    private static class Hotspot implements MedianThreshold.Measurable<Hotspot> {

        public int Hits = 0;
        private Tile Tile = null;

        public Hotspot(Tile t) {
            Tile = t;
        }

        @Override
        public int compareTo(Hotspot other) {
            int comparison = Comparisons.compare(Hits, other.Hits);
            if (comparison == 0) {
                comparison = this.Tile.compareTo(other.Tile);
            }
            return comparison;
        }

        @Override
        public int getMetric() {
            return Hits;
        }
    }

    private final int _alarmRadius;
    private final List<Tile> _strongpoints;
    private final Tile _hill;

    public DefenseZone(Tile hill, int alarmRadius) {
        _hill = hill;
        _alarmRadius = alarmRadius;
        Map<Tile, Hotspot> alarmRadiusTraffic = new HashMap<Tile, Hotspot>();
        Circumference c = new Circumference(hill, _alarmRadius);
        for (Tile t : c) {
            alarmRadiusTraffic.put(t, new Hotspot(t));
        }
        for (Tile t : new Circumference(hill, _alarmRadius * 2)) {
            try {
                AStarRoute route = new AStarRoute(t, hill);
                boolean passedAlarmRadius = false;
                for (Tile routeTile : route.routeTiles()) {
                    if (alarmRadiusTraffic.containsKey(routeTile)) {
                        alarmRadiusTraffic.get(routeTile).Hits++;
                        passedAlarmRadius = true;
                    } else if (passedAlarmRadius) {
                        // We've already passed through the alarm radius, move to next route
                        break;
                    }
                }
            } catch (NoRouteException ex) {
            }
        }
        List<Hotspot> spots = new ArrayList<Hotspot>(alarmRadiusTraffic.values());
        // Gather hotspots + some neighbors
        Set<Tile> defenses = new HashSet<Tile>();
        for (Hotspot h : MedianThreshold.aboveMedianThreshold(spots, 1.0f)) {
            for (Tile t : c.getRegion(h.Tile, 2)) {
                if (!defenses.contains(t)) {
                    LogFacade.get(DefenseZone.class).info("Defensive strongpoint for hill [%s]: [%s]", hill, t);
                    defenses.add(t);
                }
            }
        }
        // Build strongpoints list according to proximity so we can sample later
        _strongpoints = new ArrayList<Tile>(defenses.size());
        for (Tile t : c) {
            if (defenses.contains(t)) {
                _strongpoints.add(t);
            }
        }
    }

    public List<Tile> getStrongpoints() {
        return _strongpoints;
    }

    public boolean hasAntsWithinAlarmRadius() {
        for (Tile enemy : Ants.Instance.getEnemyAnts()) {
            if (Ants.Instance.getDistance(_hill, enemy) <= _alarmRadius) {
                return true;
            }
        }
        return false;
    }
}