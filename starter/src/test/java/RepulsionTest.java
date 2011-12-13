import org.testng.annotations.Test;

/**
 * TODO
 * Author: evan.pollan
 * Date: 11/25/11
 * Time: 12:10 AM
 */
@Test
public class RepulsionTest extends BaseTest {

    public void testEgressRouteCount()
            throws Exception {
        final String map =
                "........................\n" +
                "........................\n" +
                "........................\n" +
                "........................\n" +
                "........WWW.............\n" +
                "........................\n" +
                "......A.X.A.WWW.........\n" +
                "............WW..........\n" +
                ".........A..WW..........\n" +
                ".........WWWW...........\n" +
                "........................\n" +
                "........................\n" +
                "........................\n" +
                "........................";
        // 4,8
        buildState(map);
        RepulsionPolicy policy = new RepulsionPolicy(new Tile(6, 8), 4);
        policy.evacuate(Registry.Instance.getMyAnts(),
                        _dummyManager,
                        new MovementHandler() {
                            @Override
                            public boolean move(Tile ant, Tile destination) {
                                System.out.format("Evacuating [%s] by way of [%s]\n", ant, destination);
                                return true;
                            }
                        });
    }

}
