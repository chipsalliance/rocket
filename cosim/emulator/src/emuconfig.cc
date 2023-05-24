#include "emuconfig.h"

emuconfig::emuconfig() {
}

int emuconfig::get_xlen(int xlen) {
  return xlen;
}

int emuconfig::get_beats(int xlen) {
  int beats = (xlen == 64) ? 8 : 16;
  return beats;
}

int emuconfig::get_xlenBytes(int xlen) {
  return xlen / 8;
}

int emuconfig::get_tlsize(int xlen) {
  int tlsize = (xlen == 64) ? 6 : 5;
  return tlsize;
}

std::string emuconfig::get_isa(int xlen) {
  std::string isa = (xlen == 64) ? "rv64gc_zfh" : "rv32gc_zfh";
  return isa;
}

uint64_t emuconfig::get_mask(int xlen) {
  uint64_t mask = (xlen == 64) ? 0xffffffffffffffff : 0xffffffff;
  return mask;
}

emuconfig emuConfig;