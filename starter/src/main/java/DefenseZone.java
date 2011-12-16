import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
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
    private List<Tile> _strongpoints = null;
    private final Tile _hill;
    private final List<Tile> _lookouts;
    private final List<Tile> _invisibleLookouts;
    private static final LogFacade _log = LogFacade.get(DefenseZone.class);

    public DefenseZone(Tile hill, int alarmRadius) {
        _hill = hill;
        _alarmRadius = alarmRadius;
        _lookouts = new ArrayList<Tile>(4);
        for (Aim aim : Aim.values()) {
            Tile candidate = null;
            for (int d = (int)Math.sqrt(Registry.Instance.getViewRadius2()); d > 0 ; d--) {
                candidate = Registry.Instance.getTile(_hill, aim, d);
                if (Registry.Instance.getIlk(candidate) != Ilk.WATER) {
                    _lookouts.add(candidate);
                    break;
                }
            }
        }
        _invisibleLookouts = new ArrayList<Tile>(_lookouts.size());
    }

    public final List<Tile> getInvisibleLookouts() {
        _invisibleLookouts.clear();
        for (Tile lookout : _lookouts) {
            if (!Registry.Instance.isVisible(lookout)) {
                _invisibleLookouts.add(lookout);
                _log.debug("Lookout for hill [%s] is out of visible range -- targeting [%s]",
                           _hill, lookout);
            }
        }
        return _invisibleLookouts;
    }

    public final List<Tile> getStrongpoints() {
        if (_strongpoints == null) {
            calculateStrongpoints();
        }
        return _strongpoints;
    }

    private void calculateStrongpoints() {
        // Find the hotspots where an optimally-routed invader would likely cross our
        // alarm radius.  We'll egress ants towards these hotspots to create "strongpoints"
        Map<Tile, Hotspot> alarmRadiusTraffic = new HashMap<Tile, Hotspot>();
        Circumference c = new Circumference(_hill, _alarmRadius);
        for (Tile t : c) {
            alarmRadiusTraffic.put(t, new Hotspot(t));
        }
        for (Tile t : new Circumference(_hill, _alarmRadius * 2)) {
            try {
                AStarRoute route = new AStarRoute(t, _hill);
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
                    LogFacade.get(DefenseZone.class)
                             .info("Defensive strongpoint for hill [%s]: [%s]", _hill, t);
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

    private final List<Tile> _invaders = new LinkedList<Tile>();

    public final List<Tile> getInvaders() {
        _invaders.clear();
        for (Tile delta : Registry.Instance.getOffsets(_alarmRadius * 4)) {
            Tile possibleAnt = Registry.Instance.getTile(_hill, delta);
            if (Registry.Instance.getIlk(possibleAnt) == Ilk.ENEMY_ANT) {
                _invaders.add(possibleAnt);
            }
        }
        return _invaders;
    }
}
