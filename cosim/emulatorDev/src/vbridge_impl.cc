#include <fmt/core.h>
#include <glog/logging.h>

#include "disasm.h"

#include "verilated.h"

#include "glog_exception_safe.h"
#include "exceptions.h"
#include "util.h"
#include "vbridge_impl.h"

uint64_t getCycle() {
    return ctx->time();
  }

/// convert TL style size to size_by_bytes
inline uint32_t decode_size(uint32_t encoded_size) {
  return 1 << encoded_size;
}

VBridgeImpl::VBridgeImpl() : _cycles(100){}


void VBridgeImpl::timeoutCheck() {
    if (get_t() > 10000) {
     LOG(FATAL_S) << fmt::format("Simulation timeout, t={}", get_t());
     }

}

void VBridgeImpl::dpiInitCosim() {
  google::InitGoogleLogging("emulator");

  ctx = Verilated::threadContextp();

  printf("InitCosim");

  dpiDumpWave();
}

VBridgeImpl vbridge_impl_instance;




