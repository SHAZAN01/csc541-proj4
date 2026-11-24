#!/bin/bash
# Compiles the project and runs every test configuration.
# Results are written to the output/ folder.
set -e
cd "$(dirname "$0")"

javac -d . src/*.java
mkdir -p output

echo "Running base project (256 frames)..."
java VirtualMemoryManager addresses.txt -notlb    -check correct.txt -verify > output/out_256frames_noTLB.txt
java VirtualMemoryManager addresses.txt -tlb FIFO -check correct.txt -verify > output/out_256frames_TLB_FIFO.txt
java VirtualMemoryManager addresses.txt -tlb LRU  -check correct.txt -verify > output/out_256frames_TLB_LRU.txt

echo "Running Extra Credit 1 (128 frames, page replacement)..."
for p in FIFO LRU; do
  for t in FIFO LRU; do
    java VirtualMemoryManager addresses.txt -frames 128 -replace $p -tlb $t -check correct.txt -verify \
      > output/out_128frames_replace${p}_TLB_${t}.txt
  done
done

echo "Running Extra Credit 1 proof (textbook reference string, 3 frames)..."
java VirtualMemoryManager test_textbook_refstring.txt -frames 3 -replace FIFO -notlb -verbose -verify > output/proof_EC1_textbook_FIFO.txt
java VirtualMemoryManager test_textbook_refstring.txt -frames 3 -replace LRU  -notlb -verbose -verify > output/proof_EC1_textbook_LRU.txt

echo "Running Extra Credit 2 proof (TLB FIFO vs LRU)..."
java VirtualMemoryManager test_tlb.txt -tlb FIFO -verbose -verify > output/proof_EC2_tlb_FIFO.txt
java VirtualMemoryManager test_tlb.txt -tlb LRU  -verbose -verify > output/proof_EC2_tlb_LRU.txt

echo
echo "================ SUMMARY ================"
for f in output/*.txt; do
  echo "--- $f"
  grep -E "Page Faults  |Page Fault Rate|TLB Hit Rate|RESULT" "$f"
done
echo
echo "Textbook check (expected FIFO = 15 faults, LRU = 12 faults):"
grep -H "Page Faults  " output/proof_EC1_textbook_*.txt
echo "TLB check (expected FIFO = 1 hit, LRU = 2 hits):"
grep -H "TLB Hits" output/proof_EC2_tlb_*.txt
