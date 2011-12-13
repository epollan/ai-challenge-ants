import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Author: evan.pollan
 */
@Test
public class CombatTest extends BaseTest {

    public void testEgressRouteCount()
            throws Exception {
        final String map =
                "........................\n" +
                "........................\n" +
                "........................\n" +
                "........................\n" +
                "........................\n" +
                "........................\n" +
                "..........A.............\n" +
                ".......E................\n" +
                "........................\n" +
                "........................\n" +
                "........................\n" +
                "........................\n" +
                "........................\n" +
                "........................";
        buildState(map);
        List<Ant> ants = new ArrayList<Ant>(2);
        ants.add(new Ant(Registry.Instance.getMyAnts().iterator().next()));
        ants.add(Registry.Instance.getTeamedEnemyAnts().iterator().next());
        CombatZone zone = new CombatZone(ants);
        zone.move(_dummyManager, new MovementHandler() {
            @Override
            public boolean move(Tile ant, Tile destination) {
                System.out.format("Combat movement for [%s]: to [%s]\n", ant, destination);
                return true;
            }
        });
    }

}
