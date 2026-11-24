import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * BackingStore
 * ------------
 * Wraps BACKING_STORE.bin (65,536 bytes = 256 pages x 256 bytes).
 * Uses RandomAccessFile so any page can be read directly with seek(),
 * the same approach used in RAF_READ_BACKING_STORE.java.
 */
public class BackingStore implements AutoCloseable {

    private final RandomAccessFile file;
    private final int pageSize;

    public BackingStore(String path, int pageSize) throws IOException {
        this.file = new RandomAccessFile(path, "r");
        this.pageSize = pageSize;
    }

    /** Reads one full page from the backing store into the given buffer. */
    public void readPage(int pageNumber, byte[] buffer) throws IOException {
        file.seek((long) pageNumber * pageSize);
        file.readFully(buffer, 0, pageSize);
    }

    @Override
    public void close() throws IOException {
        file.close();
    }
}
