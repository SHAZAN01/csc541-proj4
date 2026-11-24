/**
 * PageTable
 * ---------
 * 256 entries, one per virtual page. Each entry stores the frame number
 * and a valid bit. When a page is evicted (Extra Credit 1, 128 frames),
 * its entry is marked invalid so the next access causes a page fault.
 */
public class PageTable {

    private final int[] frameOf;
    private final boolean[] valid;

    public PageTable(int numPages) {
        frameOf = new int[numPages];
        valid = new boolean[numPages];
        for (int i = 0; i < numPages; i++) {
            frameOf[i] = -1;
            valid[i] = false;
        }
    }

    /** Returns the frame for a page, or -1 if the page is not in memory. */
    public int lookup(int page) {
        return valid[page] ? frameOf[page] : -1;
    }

    public void map(int page, int frame) {
        frameOf[page] = frame;
        valid[page] = true;
    }

    public void invalidate(int page) {
        valid[page] = false;
        frameOf[page] = -1;
    }

    public int size() {
        return frameOf.length;
    }

    public boolean isValid(int page) {
        return valid[page];
    }

    public int getFrame(int page) {
        return frameOf[page];
    }
}
