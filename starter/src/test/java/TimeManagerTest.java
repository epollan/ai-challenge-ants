import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * TODO
 * Author: evan.pollan
 * Date: 11/26/11
 * Time: 1:53 PM
 */
@Test
public class TimeManagerTest extends BaseTest {

    public void positiveTest() throws Exception {
        TimeManager m = new TimeManager(150);
        for (int i = 0; i < 5; i++) {
            // Increasing weights
            m.nextStep(i+1);
        }
        m.turnDone();
        // Should have weighted steps of:
        // 1, 2, 3, 4, 5.
        // Total weight = 15
        // Step allocations should be mutiples of 150/15 -> 10
        // Allocations:  10, 20, 30, 40, 50
        for (int i = 0; i < 5; i++) {
            int allowedMs = 10 * (i+1);
            m.nextStep(i+1);
            Thread.sleep(allowedMs - 5);
            Assert.assertFalse(m.stepTimeOverrun());
        }
    }

    public void negativeTest() throws Exception {
        TimeManager m = new TimeManager(150);
        for (int i = 0; i < 5; i++) {
            // Increasing weights
            m.nextStep(i+1);
        }
        m.turnDone();
        // Should have weighted steps of:
        // 1, 2, 3, 4, 5.
        // Total weight = 15
        // Step allocations should be mutiples of 150/15 -> 10
        // Allocations:  10, 20, 30, 40, 50
        for (int i = 0; i < 5; i++) {
            int allowedMs = 10 * (i+1);
            m.nextStep(i+1);
            Thread.sleep(allowedMs + 5);
            Assert.assertTrue(m.stepTimeOverrun());
        }
    }
}
