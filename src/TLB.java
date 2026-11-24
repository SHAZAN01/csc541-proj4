/**
 * TLB  (Extra Credit 2)
 * ---------------------
 * Translation Look-aside Buffer with 16 entries (configurable).
 * Supports two replacement policies:
 *   FIFO - evict the entry that was inserted earliest
 *   LRU  - evict the entry that was used (hit or inserted) least recently
 * Empty (invalid) slots are always filled before anything is evicted.
 */
public class TLB {

    private final int[] pages;
    private final int[] frames;
    private final boolean[] valid;
    private final long[] insertedAt;  // used by FIFO
    private final long[] lastUsed;    // used by LRU
    private final Policy policy;
    private long clock = 0;

    public TLB(int size, Policy policy) {
        pages = new int[size];
        frames = new int[size];
        valid = new boolean[size];
        insertedAt = new long[size];
        lastUsed = new long[size];
        this.policy = policy;
    }

    /** Returns the frame number on a TLB hit, or -1 on a miss. */
    public int lookup(int page) {
        clock++;
        for (int i = 0; i < pages.length; i++) {
            if (valid[i] && pages[i] == page) {
                lastUsed[i] = clock;          // a hit refreshes recency (LRU)
                return frames[i];
            }
        }
        return -1;
    }

    /** Adds a page->frame translation, replacing a victim if the TLB is full.
     *  Returns the page that was replaced, or -1 if an empty slot was used. */
    public int insert(int page, int frame) {
        clock++;
        int slot = -1;

        // 1) use an empty slot if there is one
        for (int i = 0; i < pages.length; i++) {
            if (!valid[i]) { slot = i; break; }
        }

        int replaced = -1;
        // 2) otherwise choose a victim based on the policy
        if (slot == -1) {
            slot = 0;
            for (int i = 1; i < pages.length; i++) {
                long a = (policy == Policy.FIFO) ? insertedAt[i] : lastUsed[i];
                long b = (policy == Policy.FIFO) ? insertedAt[slot] : lastUsed[slot];
                if (a < b) slot = i;
            }
            replaced = pages[slot];
        }

        pages[slot] = page;
        frames[slot] = frame;
        valid[slot] = true;
        insertedAt[slot] = clock;
        lastUsed[slot] = clock;
        return replaced;
    }

    /** Removes a page from the TLB (needed when that page is evicted from memory). */
    public void invalidate(int page) {
        for (int i = 0; i < pages.length; i++) {
            if (valid[i] && pages[i] == page) {
                valid[i] = false;
            }
        }
    }

    public int size() {
        return pages.length;
    }

    public boolean isValid(int i) { return valid[i]; }
    public int getPage(int i)     { return pages[i]; }
    public int getFrame(int i)    { return frames[i]; }
}
