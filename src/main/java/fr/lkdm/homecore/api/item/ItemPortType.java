package fr.lkdm.homecore.api.item;

/** Direction of item movement relative to the machine exposing a port. */
public enum ItemPortType {
    /** Accepts items into the machine, without exposing extraction. */
    INPUT,
    /** Offers items from the machine, without accepting insertion. */
    OUTPUT,
    /** Accepts insertion and exposes extraction. */
    BOTH;

    /** Checks whether this direction accepts insertion.
     * @return whether items may enter the machine
     */
    public boolean canReceive() { return this != OUTPUT; }

    /** Checks whether this direction exposes extraction.
     * @return whether items may leave the machine
     */
    public boolean canSend() { return this != INPUT; }
}
