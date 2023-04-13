#pragma once

#include <optional>
#include <queue>

#include "mmu.h"
#include <VTestBench__Dpi.h>
#include "verilated_fst_c.h"

#include "simple_sim.h"
#include "util.h"
#include "encoding.h"

#include <svdpi.h>

class VBridgeImpl {
public:
  explicit VBridgeImpl();

  void dpiDumpWave();

  void dpiInitCosim();

  void timeoutCheck();

  void init_spike();

  uint64_t getCycle() {
    return ctx->time();
  }

  uint64_t get_t();

  void dpiPokeTL(const TlInterfacePoke &tl_poke);
  void dpiPeekTL(const TlInterface &tl_peek);


private:

  simple_sim sim;
  isa_parser_t isa;
  processor_t proc;

  // verilator context
  VerilatedContext *ctx;

  VerilatedFstC tfp;

  uint64_t _cycles;

  /// file path of executable binary file, which will be executed.
  const std::string bin = get_env_arg("COSIM_bin");

  const std::string ebin = get_env_arg("COSIM_entrance_bin");

  /// generated waveform path.
  const std::string wave = get_env_arg("COSIM_wave");

  /// reset vector of
  const uint64_t reset_vector = std::stoul(get_env_arg("COSIM_reset_vector"), nullptr, 16);


  /// RTL timeout cycles
  /// note: this is not the real system cycles, scalar instructions is evaulated via spike, which is not recorded.
//  const uint64_t timeout = std::stoul(get_env_arg("COSIM_timeout"));


};

extern VBridgeImpl vbridge_impl_instance;
