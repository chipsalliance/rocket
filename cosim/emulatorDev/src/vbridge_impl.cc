#include <fmt/core.h>
#include <glog/logging.h>

#include "disasm.h"

#include "verilated.h"

#include "glog_exception_safe.h"
#include "exceptions.h"
#include "util.h"
#include "vbridge_impl.h"

#include "simple_sim.h"
#include "util.h"


/// convert TL style size to size_by_bytes
inline uint32_t decode_size(uint32_t encoded_size) {
  return 1 << encoded_size;
}

VBridgeImpl::VBridgeImpl() : sim(1 << 30),
                             isa("rv64gc", "msu"),
                             _cycles(100),
                             proc(
                                 /*isa*/ &isa,
                                 /*varch*/ fmt::format("").c_str(),
                                 /*sim*/ &sim,
                                 /*id*/ 0,
                                 /*halt on reset*/ true,
                                 /* endianness*/ memif_endianness_little,
                                 /*log_file_t*/ nullptr,
                                 /*sout*/ std::cerr) {
}

uint64_t VBridgeImpl::get_t() {
  return getCycle();
}

void VBridgeImpl::timeoutCheck() {
  if (get_t() > 1000) {
    LOG(FATAL_S) << fmt::format("Simulation timeout, t={}", get_t());
  }
}

void VBridgeImpl::dpiInitCosim() {
  google::InitGoogleLogging("emulator");
  FLAGS_logtostderr=true;

  ctx = Verilated::threadContextp();

  init_spike();

  LOG(INFO) << fmt::format("[{}] dpiInitCosim", getCycle());

  dpiDumpWave();
}

void VBridgeImpl::init_spike() {
  proc.reset();
  auto state = proc.get_state();
  LOG(INFO) << fmt::format("Spike reset misa={:08X}", state->misa->read());
  LOG(INFO) << fmt::format("Spike reset mstatus={:08X}", state->mstatus->read());
  // load binary to reset_vector
  sim.load(bin, ebin, reset_vector);
  LOG(INFO) << fmt::format("Simulation Environment Initialized: bin={}, wave={}, reset_vector={:#x}",
                           bin, wave, reset_vector);
}

void VBridgeImpl::dpiPeekTL(const TlInterface &tl_peek) {
  VLOG(3) << fmt::format("[{}] dpiPeekTL", get_t());
  LOG(INFO) << fmt::format("DpiTLPeek address ={:08X}, valid = {}",tl_peek.a_bits_address,tl_peek.a_valid);

}

void VBridgeImpl::dpiPokeTL(const TlInterfacePoke &tl_poke) {
  VLOG(3) << fmt::format("[{}] dpiPokeTL", get_t());
  *tl_poke.d_valid=0;
  *tl_poke.d_bits_param = 0;
  *tl_poke.d_bits_size=0;
  *tl_poke.d_bits_source=0;
  *tl_poke.d_bits_sink=0;
  *tl_poke.d_bits_denied=0;
  *tl_poke.d_bits_data=0;
  *tl_poke.d_corrupt=0;
  *tl_poke.d_valid=0;
  *tl_poke.a_ready=true;
}

VBridgeImpl vbridge_impl_instance;




