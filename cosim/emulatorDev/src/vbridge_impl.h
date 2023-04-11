#pragma once

#include <optional>
#include <queue>

#include "mmu.h"
#include <VTestBench__Dpi.h>
#include "verilated_fst_c.h"

class VBridgeImpl {
public:
  explicit VBridgeImpl();

  void dpiDumpWave();

  void dpiInitCosim();

  void timeoutCheck();


private:

  // verilator context
  VerilatedContext *ctx;

  VerilatedFstC tfp;

  uint64_t _cycles;

};

extern VBridgeImpl vbridge_impl_instance;
