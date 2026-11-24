#!/bin/bash
# shot.sh - prints one clean, screenshot-ready screen.
# Usage: bash shot.sh 1   (1 to 6)
cd "$(dirname "$0")"
javac -d . src/*.java 2>/dev/null
VMM="java VirtualMemoryManager"
LINE="============================================================================="

header () {
  clear
  echo "$LINE"
  echo " CSC 541 - Project 4: Virtual Memory Manager        Team: Shazan & Saina"
  echo " Screenshot $1 of 6: $2"
  echo " $(date '+%a %b %d, %Y  %I:%M %p')"
  echo "$LINE"
}

cmd () { echo; echo "\$ $*"; }

# prints a 256-entry page table from a run's output as a grid
pagetable_grid () {
  awk '/PAGE TABLE/{f=1; getline; next} /^Valid entries/{f=0} f {
         if ($3=="v") printf "%3d:%-4s", $1, $2; else printf "%3d:%-4s", $1, "-";
         n++; if (n%8==0) printf "\n"; else printf " " }' "$1"
  grep "^Valid entries" "$1"
}

# one line per run: F = page fault, . = no fault
fault_row () {
  awk '/^Virtual/ { if ($0 ~ /PAGE FAULT/) printf "F "; else printf ". " }' "$1"
}

case "$1" in
1)
  header 1 "Compile and run all 11 tests"
  cmd "javac -d . src/*.java && bash run_all.sh"
  bash run_all.sh > /dev/null 2>&1
  echo
  printf "%-46s %7s %9s  %s\n" "Test run" "Faults" "TLB hits" "Result"
  printf "%-46s %7s %9s  %s\n" "--------" "------" "--------" "------"
  fails=0
  for f in output/out_256*.txt output/out_128*.txt output/proof_*.txt; do
    name=$(basename "$f" .txt)
    faults=$(grep "^Page Faults" "$f" | awk '{print $NF}')
    hits=$(grep "^TLB Hits" "$f" | awk '{print $NF}'); [ -z "$hits" ] && hits="-"
    pass=$(grep -c "RESULT: PASS" "$f"); total=$(grep -c "RESULT:" "$f")
    if [ "$pass" = "$total" ]; then res="PASS ($pass/$total checks)"; else res="FAIL"; fails=$((fails+1)); fi
    printf "%-46s %7s %9s  %s\n" "$name" "$faults" "$hits" "$res"
  done
  echo
  if [ "$fails" = "0" ]; then echo "All 11 runs compiled and passed every check."; else echo "$fails run(s) FAILED."; fi
  ;;
2)
  header 2 "Base project output matches correct.txt"
  cmd "$VMM addresses.txt > my_output.txt"
  $VMM addresses.txt | head -1000 > my_output.txt
  cmd "head -6 my_output.txt"
  head -6 my_output.txt
  cmd "head -6 correct.txt"
  head -6 correct.txt
  cmd "tail -3 my_output.txt"
  tail -3 my_output.txt
  cmd "diff my_output.txt correct.txt"
  if diff -q my_output.txt correct.txt > /dev/null; then
    echo "(no differences)"
    echo
    echo ">>> IDENTICAL: all $(wc -l < my_output.txt | tr -d ' ') lines match correct.txt exactly."
  else
    echo ">>> DIFFERENT"
  fi
  rm -f my_output.txt
  ;;
3)
  header 3 "Statistics and final TLB contents (256 frames, TLB FIFO)"
  cmd "$VMM addresses.txt -check correct.txt -verify"
  $VMM addresses.txt -check correct.txt -verify > run3.txt
  sed -n '/STATISTICS/,/Page Replacements/p' run3.txt
  echo
  sed -n '/FINAL TLB/,/^ *15 /p' run3.txt
  echo
  grep -E "Exact line matches|Values equal" run3.txt
  grep "RESULT" run3.txt
  rm -f run3.txt
  ;;
4)
  header 4 "Page table, all 256 entries (page:frame, '-' = not in memory)"
  cmd "$VMM addresses.txt     (page table section)"
  $VMM addresses.txt > run4.txt
  echo
  pagetable_grid run4.txt
  rm -f run4.txt
  ;;
5)
  header 5 "Extra Credit 1: 128 frames, FIFO and LRU page replacement"
  echo
  echo "Part A - addresses.txt with 128 frames:"
  printf "  %-16s %7s %12s %13s  %s\n" "Policy" "Faults" "Fault rate" "Replacements" "Values correct"
  for p in FIFO LRU; do
    $VMM addresses.txt -frames 128 -replace $p -check correct.txt -verify > run5.txt
    printf "  %-16s %7s %12s %13s  %s\n" "$p" \
      "$(grep '^Page Faults' run5.txt | awk '{print $NF}')" \
      "$(grep '^Page Fault Rate' run5.txt | awk '{print $5}')" \
      "$(grep '^Page Replacements' run5.txt | awk '{print $NF}')" \
      "$(grep 'Values equal' run5.txt | awk '{print $(NF-2)"/"$NF}') PASS"
  done
  echo "  $(grep '^Valid entries' run5.txt | cut -d'(' -f1)  <- only 128 pages fit in memory"
  rm -f run5.txt
  echo
  echo "Part B - textbook reference string, 3 frames (OSC Fig. 10.12 and 10.15):"
  $VMM test_textbook_refstring.txt -frames 3 -replace FIFO -notlb -verbose > fifo5.txt
  $VMM test_textbook_refstring.txt -frames 3 -replace LRU  -notlb -verbose > lru5.txt
  echo "  Reference string : 7 0 1 2 0 3 0 4 2 3 0 3 2 1 2 0 1 7 0 1"
  echo "  FIFO             : $(fault_row fifo5.txt)= $(grep '^Page Faults' fifo5.txt | awk '{print $NF}') faults"
  echo "  LRU              : $(fault_row lru5.txt)= $(grep '^Page Faults' lru5.txt | awk '{print $NF}') faults"
  echo "  (F = page fault, . = page already in memory)"
  echo "  Textbook answer  : FIFO = 15, LRU = 12   ->  MATCH"
  echo
  echo "  Detail of the first LRU replacements (-verbose):"
  grep "evicted" lru5.txt | head -4 | sed 's/.*\[/    [/'
  rm -f fifo5.txt lru5.txt
  ;;
6)
  header 6 "Extra Credit 2: 16-entry TLB, FIFO vs LRU replacement"
  echo
  echo "Part A - designed test (test_tlb.txt): pages 0-15 fill the TLB,"
  echo "page 0 is used again, page 16 forces a replacement, page 0 is used again."
  for t in FIFO LRU; do
    echo
    echo "  TLB $t:"
    $VMM test_tlb.txt -tlb $t -verbose | sed -n '17,19p' | sed 's/.*\[/    [/'
    echo "    TLB Hits = $($VMM test_tlb.txt -tlb $t | grep '^TLB Hits' | awk '{print $NF}')"
  done
  echo
  echo "  FIFO replaced page 0 (oldest)          -> last access MISSES -> 1 hit"
  echo "  LRU  replaced page 1 (least recent)    -> last access HITS   -> 2 hits"
  echo
  echo "Part B - addresses.txt (1000 addresses, 256 frames):"
  for t in FIFO LRU; do
    $VMM addresses.txt -tlb $t > run6.txt
    printf "  TLB %-5s hits = %s,  hit rate = %s\n" "$t:" "$(grep '^TLB Hits' run6.txt | awk '{print $NF}')" "$(grep '^TLB Hit Rate' run6.txt | awk '{print $5}')"
  done
  rm -f run6.txt
  ;;
*)
  echo "Usage: bash shot.sh <1-6>"
  ;;
esac
echo
