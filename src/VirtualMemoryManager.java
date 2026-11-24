import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * VirtualMemoryManager
 * ====================
 * CSC 541 - Programming Project 4 (OSC 10e, P-51: Designing a Virtual Memory Manager)
 * Team: Shazan, Saina
 *
 * Translates 16-bit logical addresses (from addresses.txt) to physical addresses
 * for a virtual address space of 2^16 = 65,536 bytes.
 *
 *   - 256 pages, 256 bytes per page  (8-bit page number, 8-bit offset)
 *   - Physical memory: 256 frames (base) or 128 frames (Extra Credit 1)
 *   - TLB: 16 entries with FIFO or LRU replacement (Extra Credit 2)
 *   - Page replacement when memory is full: FIFO or LRU (Extra Credit 1)
 *   - Demand paging from BACKING_STORE.bin
 *
 * For every address the program prints, in the same format as correct.txt:
 *   Virtual address: X Physical address: Y Value: Z
 * Then it prints the statistics (page-fault rate, TLB hit rate),
 * the final TLB contents and the full 256-entry page table.
 *
 * Usage:
 *   java VirtualMemoryManager <addresses file> [options]
 *
 * Options:
 *   -frames <n>        number of physical frames (default 256; use 128 for EC1)
 *   -replace FIFO|LRU  page-replacement policy when memory is full (default FIFO)
 *   -tlb FIFO|LRU      TLB replacement policy (default FIFO)
 *   -notlb             turn the TLB off (page table only)
 *   -store <file>      backing store file (default BACKING_STORE.bin)
 *   -check <file>      compare the results with correct.txt and report matches
 *   -verify            check every value directly against BACKING_STORE.bin
 *   -verbose           show TLB hit / page fault / eviction for every address
 */
public class VirtualMemoryManager {

    // ---- fixed parameters from the project specification ----
    static final int PAGE_SIZE   = 256;   // bytes per page / frame
    static final int NUM_PAGES   = 256;   // entries in the page table
    static final int TLB_SIZE    = 16;    // entries in the TLB
    static final int OFFSET_BITS = 8;
    static final int OFFSET_MASK = 0xFF;
    static final int ADDR_MASK   = 0xFFFF; // only the rightmost 16 bits are used

    // ---- components ----
    private final PageTable pageTable;
    private final PhysicalMemory memory;
    private final BackingStore store;
    private final TLB tlb;                 // null when -notlb is used
    private final Policy pagePolicy;

    // ---- frame bookkeeping for page replacement ----
    private int nextFreeFrame = 0;
    private final int[] pageInFrame;       // reverse map: frame -> page
    private final long[] frameLastUsed;    // for LRU page replacement
    private final ArrayDeque<Integer> fifoFrames = new ArrayDeque<>(); // for FIFO
    private long clock = 0;

    // ---- statistics ----
    private int translated = 0;
    private int pageFaults = 0;
    private int tlbHits = 0;
    private int replacements = 0;

    private final byte[] pageBuffer = new byte[PAGE_SIZE];

    public VirtualMemoryManager(int numFrames, Policy pagePolicy, Policy tlbPolicy,
                                boolean useTlb, String storePath) throws IOException {
        this.pageTable = new PageTable(NUM_PAGES);
        this.memory = new PhysicalMemory(numFrames, PAGE_SIZE);
        this.store = new BackingStore(storePath, PAGE_SIZE);
        this.tlb = useTlb ? new TLB(TLB_SIZE, tlbPolicy) : null;
        this.pagePolicy = pagePolicy;
        this.pageInFrame = new int[numFrames];
        this.frameLastUsed = new long[numFrames];
        for (int i = 0; i < numFrames; i++) pageInFrame[i] = -1;
    }

    /** Result of one translation. */
    static final class Translation {
        final int virtualAddress, physicalAddress, value;
        int page, frame;
        boolean tlbHit, pageFault, tlbUsed;
        int evictedPage = -1;              // page replaced in memory on this access (EC1)
        int tlbReplaced = -1;              // page replaced in the TLB on this access (EC2)
        Translation(int v, int p, int val) { virtualAddress = v; physicalAddress = p; value = val; }

        /** Same format as correct.txt. */
        @Override public String toString() {
            return "Virtual address: " + virtualAddress
                 + " Physical address: " + physicalAddress
                 + " Value: " + value;
        }

        /** Extra detail printed with -verbose. */
        String detail() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("   [page %3d", page));
            if (tlbHit)         sb.append(" | TLB HIT");
            else if (!pageFault) sb.append(tlbUsed ? " | TLB miss, page table hit" : " | page table hit");
            else                 sb.append(" | PAGE FAULT");
            if (pageFault) {
                if (evictedPage >= 0) sb.append(String.format(" -> evicted page %d from frame %d", evictedPage, frame));
                sb.append(String.format(" -> loaded into frame %d", frame));
            } else {
                sb.append(String.format(" -> frame %d", frame));
            }
            if (tlbReplaced >= 0) sb.append(String.format(" | TLB replaced page %d", tlbReplaced));
            return sb.append("]").toString();
        }
    }

    private int lastEvictedPage = -1;

    /** Translates one logical address and reads the byte stored there. */
    public Translation translate(int logicalAddress) throws IOException {
        int address = logicalAddress & ADDR_MASK;
        int page    = (address >> OFFSET_BITS) & 0xFF;
        int offset  = address & OFFSET_MASK;
        translated++;
        clock++;

        int frame = -1;
        boolean hit = false, fault = false;
        int tlbReplaced = -1;
        lastEvictedPage = -1;

        // Step 1: consult the TLB
        if (tlb != null) {
            frame = tlb.lookup(page);
            if (frame != -1) { tlbHits++; hit = true; }
        }

        // Step 2: TLB miss -> consult the page table
        if (frame == -1) {
            frame = pageTable.lookup(page);

            // Step 3: page fault -> bring the page in from the backing store
            if (frame == -1) {
                pageFaults++;
                fault = true;
                frame = handlePageFault(page);
            }

            // Step 4: put the translation into the TLB
            if (tlb != null) tlbReplaced = tlb.insert(page, frame);
        }

        frameLastUsed[frame] = clock;      // every access counts for LRU

        int physicalAddress = (frame << OFFSET_BITS) | offset;
        int value = memory.read(frame, offset);   // byte is signed, like correct.txt
        Translation t = new Translation(address, physicalAddress, value);
        t.page = page; t.frame = frame; t.tlbHit = hit; t.pageFault = fault;
        t.evictedPage = lastEvictedPage;
        t.tlbUsed = (tlb != null);
        t.tlbReplaced = tlbReplaced;
        return t;
    }

    /** Loads a page into a free frame, or evicts a victim when memory is full. */
    private int handlePageFault(int page) throws IOException {
        int frame;

        if (nextFreeFrame < memory.getNumFrames()) {
            frame = nextFreeFrame++;                       // free frame available
        } else {
            frame = selectVictimFrame();                   // memory is full
            int victimPage = pageInFrame[frame];
            lastEvictedPage = victimPage;
            pageTable.invalidate(victimPage);
            if (tlb != null) tlb.invalidate(victimPage);   // keep the TLB consistent
            replacements++;
        }

        store.readPage(page, pageBuffer);
        memory.loadFrame(frame, pageBuffer);
        pageTable.map(page, frame);
        pageInFrame[frame] = page;
        fifoFrames.addLast(frame);
        return frame;
    }

    /** Chooses which frame to replace, based on the page-replacement policy. */
    private int selectVictimFrame() {
        if (pagePolicy == Policy.FIFO) {
            return fifoFrames.pollFirst();                 // oldest loaded page
        }
        // LRU: frame whose page was used least recently
        int victim = 0;
        for (int f = 1; f < frameLastUsed.length; f++) {
            if (frameLastUsed[f] < frameLastUsed[victim]) victim = f;
        }
        fifoFrames.remove(victim);
        return victim;
    }

    // ------------------------------------------------------------------
    // Reporting
    // ------------------------------------------------------------------

    private void printStatistics(String config) {
        System.out.println();
        System.out.println("==================== STATISTICS ====================");
        System.out.println("Configuration            = " + config);
        System.out.println("Number of Translated Addresses = " + translated);
        System.out.println("Page Faults              = " + pageFaults);
        System.out.printf ("Page Fault Rate          = %.3f  (%.1f%%)%n",
                pageFaults / (double) translated, 100.0 * pageFaults / translated);
        if (tlb != null) {
            System.out.println("TLB Hits                 = " + tlbHits);
            System.out.printf ("TLB Hit Rate             = %.3f  (%.1f%%)%n",
                    tlbHits / (double) translated, 100.0 * tlbHits / translated);
        } else {
            System.out.println("TLB                      = disabled");
        }
        System.out.println("Page Replacements        = " + replacements);
    }

    private void printTlb() {
        if (tlb == null) return;
        System.out.println();
        System.out.println("============== FINAL TLB CONTENTS (" + TLB_SIZE + " entries) ==============");
        System.out.println("Entry   Page   Frame   Valid");
        for (int i = 0; i < tlb.size(); i++) {
            if (tlb.isValid(i)) {
                System.out.printf("%5d   %4d   %5d   %s%n", i, tlb.getPage(i), tlb.getFrame(i), "yes");
            } else {
                System.out.printf("%5d   %4s   %5s   %s%n", i, "-", "-", "no");
            }
        }
    }

    private void printPageTable() {
        System.out.println();
        System.out.println("=============== PAGE TABLE (" + NUM_PAGES + " entries) ===============");
        System.out.println("Page   Frame   Valid");
        int validCount = 0;
        for (int p = 0; p < pageTable.size(); p++) {
            if (pageTable.isValid(p)) {
                validCount++;
                System.out.printf("%4d   %5d   %s%n", p, pageTable.getFrame(p), "v");
            } else {
                System.out.printf("%4d   %5s   %s%n", p, "-", "i");
            }
        }
        System.out.println("Valid entries = " + validCount + " / " + NUM_PAGES
                + "   (v = valid / in memory, i = invalid / not in memory)");
    }

    /** Compares results with correct.txt. Values must always match;
     *  physical addresses match only with 256 frames (no replacement). */
    private static void checkAgainst(String path, List<Translation> results) throws IOException {
        List<String> expected = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.trim().isEmpty()) expected.add(line.trim());
            }
        }
        int exact = 0, valueMatch = 0, n = Math.min(expected.size(), results.size());
        int firstMismatch = -1;
        for (int i = 0; i < n; i++) {
            String exp = expected.get(i);
            Translation t = results.get(i);
            if (exp.equals(t.toString())) exact++;
            String[] tok = exp.split("\\s+");   // Virtual address: V Physical address: P Value: X
            int expVirtual = Integer.parseInt(tok[2]);
            int expValue   = Integer.parseInt(tok[tok.length - 1]);
            if (expVirtual == t.virtualAddress && expValue == t.value) valueMatch++;
            else if (firstMismatch == -1) firstMismatch = i;
        }
        System.out.println();
        System.out.println("============ CHECK AGAINST " + path + " ============");
        System.out.println("Lines compared                    = " + n);
        System.out.println("Exact line matches                = " + exact + " / " + n);
        System.out.println("Virtual address + Value matches   = " + valueMatch + " / " + n);
        if (valueMatch == n && expected.size() == results.size()) {
            System.out.println("RESULT: PASS - every value read is correct.");
            if (exact != n) {
                System.out.println("(Physical addresses differ from correct.txt only because pages were "
                        + "placed in different frames by page replacement; this is expected.)");
            }
        } else {
            System.out.println("RESULT: FAIL - first mismatch at line " + (firstMismatch + 1));
        }
    }

    /** Independent check: each value must equal the byte stored at the same
     *  virtual address in BACKING_STORE.bin. Works for any input file and any
     *  number of frames, so it proves page replacement never returns wrong data. */
    private static void verifyAgainstStore(String storePath, List<Translation> results) throws IOException {
        int ok = 0;
        int firstBad = -1;
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(storePath, "r")) {
            for (int i = 0; i < results.size(); i++) {
                Translation t = results.get(i);
                raf.seek(t.virtualAddress);
                byte expected = raf.readByte();
                if (expected == t.value) ok++;
                else if (firstBad == -1) firstBad = i;
            }
        }
        System.out.println();
        System.out.println("============ VERIFY AGAINST " + storePath + " ============");
        System.out.println("Values equal to backing-store byte = " + ok + " / " + results.size());
        System.out.println(ok == results.size()
                ? "RESULT: PASS - every value matches the backing store."
                : "RESULT: FAIL - first mismatch at line " + (firstBad + 1));
    }

    // ------------------------------------------------------------------
    // main
    // ------------------------------------------------------------------

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java VirtualMemoryManager <addresses file> "
                    + "[-frames 256|128] [-replace FIFO|LRU] [-tlb FIFO|LRU] [-notlb] "
                    + "[-store BACKING_STORE.bin] [-check correct.txt] [-verify] [-verbose]");
            System.exit(1);
        }

        String addressFile = args[0];
        int frames = 256;
        Policy pagePolicy = Policy.FIFO;
        Policy tlbPolicy = Policy.FIFO;
        boolean useTlb = true;
        String storePath = "BACKING_STORE.bin";
        String checkPath = null;
        boolean verbose = false;
        boolean verify = false;

        try {
            for (int i = 1; i < args.length; i++) {
                switch (args[i].toLowerCase()) {
                    case "-frames":  frames = Integer.parseInt(args[++i]); break;
                    case "-replace": pagePolicy = Policy.parse(args[++i]); break;
                    case "-tlb":     tlbPolicy = Policy.parse(args[++i]); break;
                    case "-notlb":   useTlb = false; break;
                    case "-store":   storePath = args[++i]; break;
                    case "-check":   checkPath = args[++i]; break;
                    case "-verbose": verbose = true; break;
                    case "-verify":  verify = true; break;
                    default:
                        System.err.println("Unknown option: " + args[i]);
                        System.exit(1);
                }
            }
            if (frames < 1 || frames > NUM_PAGES) {
                System.err.println("-frames must be between 1 and " + NUM_PAGES);
                System.exit(1);
            }
        } catch (ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            System.err.println("Invalid arguments: " + e.getMessage());
            System.exit(1);
        }

        String config = frames + " frames, page replacement " + pagePolicy
                + (useTlb ? ", TLB " + TLB_SIZE + " entries " + tlbPolicy : ", no TLB");

        List<Translation> results = new ArrayList<>();
        VirtualMemoryManager vmm = null;
        try {
            vmm = new VirtualMemoryManager(frames, pagePolicy, tlbPolicy, useTlb, storePath);
            try (BufferedReader br = new BufferedReader(new FileReader(addressFile))) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    Translation t = vmm.translate(Integer.parseInt(line));
                    results.add(t);
                    System.out.println(verbose ? t + t.detail() : t.toString());
                }
            }
            vmm.printStatistics(config);
            vmm.printTlb();
            vmm.printPageTable();
            if (checkPath != null) checkAgainst(checkPath, results);
            if (verify) verifyAgainstStore(storePath, results);
        } catch (IOException e) {
            System.err.println("I/O error: " + e.getMessage());
            System.exit(1);
        } catch (NumberFormatException e) {
            System.err.println("Bad address in input file: " + e.getMessage());
            System.exit(1);
        } finally {
            if (vmm != null) {
                try { vmm.store.close(); } catch (IOException ignored) { }
            }
        }
    }
}
