==============================================================================
  CSC 541 - Programming Project 4
  Virtual Memory Manager in Java  (OSC 10th ed., Project P-51)
  Team: Shazan, Saina
==============================================================================

------------------------------------------------------------------------------
 SUMMARY
------------------------------------------------------------------------------
  Base project ............................................ COMPLETE
    Output is identical, line for line, to correct.txt (1000 / 1000).
  Extra Credit 1 - 128 frames, FIFO and LRU page replacement  IMPLEMENTED
  Extra Credit 2 - 16-entry TLB, FIFO and LRU replacement ... IMPLEMENTED

  Quick test of everything:   bash run_all.sh

------------------------------------------------------------------------------
 1. WHAT THE PROGRAM DOES
------------------------------------------------------------------------------
  The program reads 1000 logical addresses from addresses.txt. For each one it:
    1. Masks the address to 16 bits and splits it into an 8-bit page number
       and an 8-bit offset.
    2. Looks up the page in the TLB (16 entries).
    3. On a TLB miss, looks up the page table (256 entries).
    4. On a page fault, reads the 256-byte page from BACKING_STORE.bin into a
       free frame. If memory is full (128 frames), a victim page is chosen
       with FIFO or LRU, removed from the page table and from the TLB, and
       replaced.
    5. Builds the physical address  (frame << 8) | offset  and reads the
       signed byte stored there.

  Output:
    - One line per address, in the same format as correct.txt:
        Virtual address: 16916 Physical address: 20 Value: 0
    - Statistics: page faults, page-fault rate, TLB hits, TLB hit rate,
      page replacements
    - Final contents of the 16-entry TLB
    - Contents of the 256-entry page table (frame number and valid bit)
    - Optional PASS / FAIL checks:
        -check  compares with correct.txt
        -verify compares every value with the byte in BACKING_STORE.bin

  System parameters:
    Virtual address space   2^16 = 65,536 bytes
    Page / frame size       256 bytes
    Page table              256 entries
    Physical memory         256 frames (base) or 128 frames (Extra Credit 1)
    TLB                     16 entries (Extra Credit 2)

------------------------------------------------------------------------------
 2. FILES
------------------------------------------------------------------------------
  Source code (src/)
    VirtualMemoryManager.java   main program: translation, page faults,
                                page replacement, statistics, reports
    TLB.java                    16-entry TLB with FIFO / LRU replacement
    PageTable.java              256-entry page table with valid bits
    PhysicalMemory.java         physical memory as an array of frames
    BackingStore.java           reads pages from BACKING_STORE.bin
    Policy.java                 FIFO / LRU option

  Input files
    addresses.txt               1000 logical addresses (from instructor)
    BACKING_STORE.bin           backing store, 65,536 bytes (from instructor)
    correct.txt                 expected output (from instructor)
    RAF_READ_BACKING_STORE.java backing-store viewer (from instructor)
    test_textbook_refstring.txt EC1 test: textbook reference string
    test_tlb.txt                EC2 test: pattern where TLB FIFO and LRU differ

  Scripts
    run_all.sh                  compiles and runs all 11 test runs
    shot.sh                     prints the screens used for the screenshots

  Results and documentation
    output/                     output of all 11 test runs (section 6)
    screenshots/                6 screenshots of the program running
    README.txt, contribution.txt, genai.txt

------------------------------------------------------------------------------
 3. HOW TO COMPILE
------------------------------------------------------------------------------
  Requires Java JDK 8 or newer. In the project folder:

      javac -d . src/*.java

------------------------------------------------------------------------------
 4. HOW TO RUN
------------------------------------------------------------------------------
      java VirtualMemoryManager <address file> [options]

  Options
    -frames <n>          physical frames: 256 (default) or 128 for Extra Credit 1
    -replace FIFO|LRU    page-replacement policy (default FIFO)
    -tlb FIFO|LRU        TLB replacement policy (default FIFO)
    -notlb               run without the TLB
    -check correct.txt   compare output with correct.txt, print PASS / FAIL
    -verify              compare every value with BACKING_STORE.bin
    -verbose             for every address, show TLB hit / page fault,
                         which page was evicted and which TLB entry was replaced
    -store <file>        a different backing store (default BACKING_STORE.bin)

  Examples
    Base project:
      java VirtualMemoryManager addresses.txt
    Base project with automatic checks:
      java VirtualMemoryManager addresses.txt -check correct.txt -verify
    Save the output to a file:
      java VirtualMemoryManager addresses.txt > output.txt
    All tests at once:
      bash run_all.sh

------------------------------------------------------------------------------
 5. EXTRA CREDIT
------------------------------------------------------------------------------
  EXTRA CREDIT 1: 128 physical frames with FIFO and LRU page replacement
  ----------------------------------------------------------------------
  How it works
    - Frames are used in order until all 128 are full.
    - After that, each page fault replaces a victim:
        FIFO  the page that has been in memory the longest (queue of frames)
        LRU   the page that was used least recently (each frame stores the
              time of its last access, updated on every access, including
              TLB hits)
    - The victim's page-table entry is set to invalid, and the victim is also
      removed from the TLB, so an old translation is never used again.

  How to test
    a) Full input with 128 frames:
         java VirtualMemoryManager addresses.txt -frames 128 -replace FIFO -check correct.txt -verify
         java VirtualMemoryManager addresses.txt -frames 128 -replace LRU  -check correct.txt -verify
       Expected: all 1000 values correct (PASS), and 128 of 256 page-table
       entries valid. Physical addresses differ from correct.txt because pages
       are placed in different frames; this is expected.

    b) Textbook example (Sections 10.4.2 and 10.4.4, Figures 10.12 and 10.15):
       reference string 7,0,1,2,0,3,0,4,2,3,0,3,2,1,2,0,1,7,0,1 with 3 frames.
         java VirtualMemoryManager test_textbook_refstring.txt -frames 3 -replace FIFO -notlb -verbose
         java VirtualMemoryManager test_textbook_refstring.txt -frames 3 -replace LRU  -notlb -verbose
       Expected (textbook): FIFO = 15 page faults, LRU = 12 page faults.
       Result:              FIFO = 15,             LRU = 12.

  EXTRA CREDIT 2: 16-entry TLB with FIFO and LRU replacement
  ----------------------------------------------------------
  How it works
    - Empty TLB entries are filled first. When the TLB is full:
        FIFO  replaces the entry that was inserted first
        LRU   replaces the entry that was used least recently
    - The TLB is on by default. Choose the policy with -tlb FIFO or -tlb LRU.

  How to test
    a) Designed test (test_tlb.txt): pages 0-15 fill the TLB, page 0 is used
       again, page 16 forces a replacement, then page 0 is used again.
         java VirtualMemoryManager test_tlb.txt -tlb FIFO -verbose
         java VirtualMemoryManager test_tlb.txt -tlb LRU  -verbose
       FIFO replaces page 0 (oldest)        -> last access misses -> 1 TLB hit
       LRU  replaces page 1 (least recent)  -> last access hits   -> 2 TLB hits

    b) Full input:
         java VirtualMemoryManager addresses.txt -tlb FIFO
         java VirtualMemoryManager addresses.txt -tlb LRU
       Result: FIFO = 54 hits (0.054), LRU = 55 hits (0.055).

------------------------------------------------------------------------------
 6. TEST RESULTS  (all files are in output/)
------------------------------------------------------------------------------
  addresses.txt, 1000 addresses
  Frames  Page repl.  TLB    Faults  Fault rate  TLB hits  Hit rate  Check
  256     -           none    244     0.244        -          -      PASS, identical
  256     -           FIFO    244     0.244       54        0.054    PASS, identical
  256     -           LRU     244     0.244       55        0.055    PASS, identical
  128     FIFO        FIFO    538     0.538       54        0.054    PASS, all values
  128     FIFO        LRU     538     0.538       55        0.055    PASS, all values
  128     LRU         FIFO    539     0.539       54        0.054    PASS, all values
  128     LRU         LRU     539     0.539       55        0.055    PASS, all values

  Extra-credit tests
  test_textbook_refstring.txt, 3 frames, FIFO ......... 15 faults (book: 15)
  test_textbook_refstring.txt, 3 frames, LRU .......... 12 faults (book: 12)
  test_tlb.txt, TLB FIFO .............................. 1 TLB hit  (expected 1)
  test_tlb.txt, TLB LRU ............................... 2 TLB hits (expected 2)

  Observations
    - addresses.txt contains random addresses with almost no locality, so the
      TLB hit rate is low (about 5%) and LRU is not much better than FIFO.
      On the textbook string, which has locality, LRU clearly wins (12 vs 15).
    - With 128 frames instead of 256, page faults more than double
      (244 -> about 538), because only half of the pages fit in memory.

------------------------------------------------------------------------------
 7. SCREENSHOTS  (screenshots/ folder, made with: bash shot.sh 1 ... 6)
------------------------------------------------------------------------------
  01_all_tests.png          all 11 test runs compile and pass
  02_matches_correct.png    output is identical to correct.txt
  03_stats_tlb.png          statistics and final TLB contents
  04_page_table.png         all 256 page-table entries
  05_extra_credit_1.png     128 frames FIFO/LRU + textbook 15 vs 12 faults
  06_extra_credit_2.png     TLB FIFO vs LRU (1 vs 2 hits) + hit rates
