/**
 * PhysicalMemory
 * --------------
 * Physical memory modeled as an array of frames, each frame = 256 bytes.
 * Number of frames is configurable: 256 (base project) or 128 (Extra Credit 1).
 */
public class PhysicalMemory {

    private final byte[][] frames;
    private final int pageSize;

    public PhysicalMemory(int numFrames, int pageSize) {
        this.frames = new byte[numFrames][pageSize];
        this.pageSize = pageSize;
    }

    /** Copies a page (read from the backing store) into a frame. */
    public void loadFrame(int frameNumber, byte[] pageData) {
        System.arraycopy(pageData, 0, frames[frameNumber], 0, pageSize);
    }

    /** Returns the signed byte stored at (frame, offset). */
    public byte read(int frameNumber, int offset) {
        return frames[frameNumber][offset];
    }

    public int getNumFrames() {
        return frames.length;
    }
}
