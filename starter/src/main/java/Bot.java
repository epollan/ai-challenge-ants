/**
 * Provides basic game state handling.
 */
public abstract class Bot extends AbstractSystemInputParser {

    /**
     * {@inheritDoc}
     */
    @Override
    public void setup(int loadTime, int turnTime, int rows, int cols, int turns, int viewRadius2,
                      int attackRadius2, int spawnRadius2) {
        Registry.initialize(loadTime, turnTime, rows, cols, turns, viewRadius2, attackRadius2, spawnRadius2);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void beforeUpdate() {
        Registry.Instance.setTurnStartTime(System.currentTimeMillis());
        Registry.Instance.clearMyAnts();
        Registry.Instance.clearEnemyAnts();
        Registry.Instance.clearMyHills();
        Registry.Instance.clearEnemyHills();
        Registry.Instance.clearFood();
        Registry.Instance.clearDeadAnts();
        Registry.Instance.getOrders().clear();
        Registry.Instance.clearVision();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addWater(int row, int col) {
        Registry.Instance.update(Ilk.WATER, new Tile(row, col));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addAnt(int row, int col, int owner) {
        if (owner > 0) {
            Registry.Instance.update(Ilk.ENEMY_ANT, new Tile(row, col), owner);
        } else {
            Registry.Instance.update(Ilk.MY_ANT, new Tile(row, col));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addFood(int row, int col) {
        Registry.Instance.update(Ilk.FOOD, new Tile(row, col));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeAnt(int row, int col, int owner) {
        Registry.Instance.update(Ilk.DEAD, new Tile(row, col));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addHill(int row, int col, int owner) {
        Registry.Instance.updateHills(owner, new Tile(row, col));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void afterUpdate() {
        Registry.Instance.setVision();
    }
}
