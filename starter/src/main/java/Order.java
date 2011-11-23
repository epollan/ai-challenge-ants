/**
 * Represents an order to be issued.
 */
public class Order {

    private final int _row;
    private final int _col;
    private final char _direction;
    private final int _hash;
    
    /**
     * Creates new {@link Order} object.
     * 
     * @param tile map tile with my ant
     * @param direction direction in which to move my ant
     */
    public Order(Tile tile, Aim direction) {
        _row = tile.getRow();
        _col = tile.getCol();
        _direction = direction.getSymbol();
        _hash = tile.hashCode() ^ direction.hashCode();
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object other) {
        boolean result = false;
        if (other instanceof Order) {
            Order order = (Order)other;
            result = _row == order._row && _col == order._col && _direction == order._direction;
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return _hash;
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "o " + _row + " " + _col + " " + _direction;
    }
}
