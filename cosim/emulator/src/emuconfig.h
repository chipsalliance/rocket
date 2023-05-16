#pragma once

#include <optional>
#include <queue>

#include "mmu.h"
#include "processor.h"

class emuconfig {
public:
    explicit emuconfig();
    std::string get_isa(int);
    int get_xlen(int);
    int get_beats(int);
    int get_xlenBytes(int);
    int get_tlsize(int);
    uint64_t get_mask(int);

};

extern emuconfig emuConfig;