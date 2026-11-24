/** Replacement policy used for page replacement and for the TLB. */
public enum Policy {
    FIFO,
    LRU;

    public static Policy parse(String s) {
        return Policy.valueOf(s.trim().toUpperCase());
    }
}
