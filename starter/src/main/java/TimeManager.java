import java.util.List;
import java.util.LinkedList;

/**
 * Author: evan.pollan
 * Date: 11/26/11
 * Time: 1:15 PM
 */
public final class TimeManager {

    private final long _totalAllowed;
    private int _currentStep = 0;
    private long _turnStartMs;
    private long _stepTimoutMs;
    private long[] _stepAllowedMs;
    private List<Float> _stepWeights = new LinkedList<Float>();
    private String[] _stepDescriptions;
    private List<String> _wipStepDescriptions = new LinkedList<String>();
    private LogFacade _log = LogFacade.get(TimeManager.class);

    /**
     * Budget time
     *
     * @param totalAllowed total budget allocation
     */
    public TimeManager(long totalAllowed) {
        _totalAllowed = totalAllowed;
    }

    /**
     * @return total allowed number of milliseconds for this turn
     */
    public long getTotalAllowed() {
        return _totalAllowed;
    }

    /**
     * @return true if the current step time allocation has been overrrun
     */
    public boolean stepTimeOverrun() {
        if (_stepAllowedMs == null) {
            // First pass -- haven't figured out step allocations
            return false;
        }
        if (System.currentTimeMillis() > _stepTimoutMs) {
            _log.info("Step '%s' (#%d of %d) overran allotted time of %d ms by %d ms",
                      _stepDescriptions[_currentStep], _currentStep + 1,
                      _stepAllowedMs.length, _stepAllowedMs[_currentStep],
                      System.currentTimeMillis() - _stepTimoutMs);
            return true;
        }
        return false;
    }

    /**
     * Move to the next processing step, overriding the default weight of 1.0
     *
     * @param weight          weight of this step, relative to the amount of time it should
     *                        be allotted relative to the other steps.  Default is 1.0
     * @param stepDescription descriptive text for the step
     */
    public void nextStep(float weight, String stepDescription) {
        ++_currentStep;
        if (_stepWeights != null) {
            _stepWeights.add(new Float(weight));
            _wipStepDescriptions.add(stepDescription);
        }
        else {
            _stepTimoutMs = System.currentTimeMillis() + _stepAllowedMs[_currentStep];
        }
    }

    /**
     * Move to the next processing step, overriding the default weight of 1.0
     *
     * @param weight weight of this step, relative to the amount of time it should
     *               be allotted relative to the other steps.  Default is 1.0
     */
    public void nextStep(float weight) {
        nextStep(weight, null);
    }

    /**
     * Move to the next processing step, assigning it the default weight of 1.0
     *
     * @param stepDescription descriptive text for the step
     */
    public void nextStep(String stepDescription) {
        nextStep(1.0f, stepDescription);
    }

    /**
     * Move to the next processing step, assigning it the default weight of 1.0
     */
    public void nextStep() {
        nextStep(null);
    }

    public void turnStarted() {
        _turnStartMs = System.currentTimeMillis();
    }

    public long getTurnStartMs() {
        return _turnStartMs;
    }

    /**
     * Indicate that the game turn is done
     */
    public void turnDone() {
        if (_stepAllowedMs == null) {
            // Sum up all step weights
            float totalWeight = 0.0f;
            for (Float weight : _stepWeights) {
                totalWeight += weight.floatValue();
            }

            // Initialize step description array
            _stepDescriptions = new String[_wipStepDescriptions.size()];
            for (int i = 0; i < _stepDescriptions.length; i++) {
                _stepDescriptions[i] = _wipStepDescriptions.get(i);
                if (_stepDescriptions[i] == null) {
                    _stepDescriptions[i] = "Step #" + i;
                }
            }
            _wipStepDescriptions = null;

            // Figure out what a weight of 1.0 would be allocated
            double weight1Allocation = (_totalAllowed * 1.0) / totalWeight;
            _stepAllowedMs = new long[_stepWeights.size()];
            for (int i = 0; i < _stepAllowedMs.length; i++) {
                // Assign each step's millisecond allocation as a factor of its step weight
                _stepAllowedMs[i] = (long) Math.floor(weight1Allocation * _stepWeights.get(i).floatValue());
                _log.info("Calculated step time allocation for step '%s' (#%d of %d) to be %d ms",
                          _stepDescriptions[i], (i + 1), _stepAllowedMs.length, _stepAllowedMs[i]);
            }
            // Clear reference to list of step weights -- not needed any more
            _stepWeights = null;
        }
        _currentStep = -1;
    }
}
