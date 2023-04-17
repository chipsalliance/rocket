#ifdef COSIM_VERILATOR
#include <VTestBench__Dpi.h>
#endif

#include <csignal>

#include <glog/logging.h>
#include <fmt/core.h>

#include "svdpi.h"
#include "vbridge_impl.h"
#include "exceptions.h"

static bool terminated = false;


void sigint_handler(int s) {
  terminated = true;
  dpiFinish();
}


#define TRY(action) \
  try {             \
    if (!terminated) {action}          \
  } catch (ReturnException &e) { \
    terminated = true;                \
    LOG(INFO) << fmt::format("detect returning instruction, gracefully quit simulation");                  \
    dpiFinish();    \
  } catch (std::runtime_error &e) { \
    terminated = true;                \
    LOG(ERROR) << fmt::format("detect exception ({}), gracefully abort simulation", e.what());                 \
    dpiError(e.what());  \
  }

#if VM_TRACE

void VBridgeImpl::dpiDumpWave() {
  TRY({
        ::dpiDumpWave((wave + ".fst").c_str());
      })
}

#endif

[[maybe_unused]] void dpiInitCosim() {
  std::signal(SIGINT, sigint_handler);
  svSetScope(svGetScopeFromName("TOP.TestBench.verificationModule.verbatim"));
  TRY({
        vbridge_impl_instance.dpiInitCosim();
      })
}

[[maybe_unused]] void dpiTimeoutCheck() {
  if (vbridge_impl_instance.timeoutCheck())
    dpiFinish();
}

[[maybe_unused]] void dpiBasePoke(svBitVecVal *resetVector) {
  uint32_t v = 0x1000;
  *resetVector = v;
}

[[maybe_unused]] void dpiPeekTL(
    const svBitVecVal *pc,
    const svBitVecVal *a_opcode,
    const svBitVecVal *a_param,
    const svBitVecVal *a_size,
    const svBitVecVal *a_source,
    const svBitVecVal *a_address,
    const svBitVecVal *a_mask,
    const svBitVecVal *a_data,
    svBit a_corrupt,
    svBit a_valid,
    svBit d_ready,
    svBit miss
) {
  TRY({
        vbridge_impl_instance.dpiPeekTL(miss, *pc,
                                        TlPeekInterface{*a_opcode, *a_param, *a_size, *a_source, *a_address, *a_mask,
                                                        *a_data,
                                                        a_corrupt, a_valid, d_ready});
      })
}

[[maybe_unused]] void dpiPokeTL(
    svBitVecVal *d_bits_data_high,
    svBitVecVal *d_bits_data_low,
    svBitVecVal *d_opcode,
    svBitVecVal *d_param,
    svBitVecVal *d_size,
    svBitVecVal *d_source,
    svBitVecVal *d_sink,
    svBitVecVal *d_denied,
    svBit *d_corrupt,
    svBit *d_valid,
    svBit d_ready
) {
  TRY({
        vbridge_impl_instance.dpiPokeTL(
            TlPokeInterface{d_bits_data_high, d_bits_data_low, d_opcode, d_param, d_size, d_source, d_sink, d_denied,
                            d_corrupt, d_valid, d_ready});
      })


}

[[maybe_unused]] void dpiRefillQueue(

) {
  TRY({
        vbridge_impl_instance.dpiRefillQueue();
      })


}

[[maybe_unused]] void dpiCommitPeek(
    svBit rf_wen,
    svBit wb_valid,
    const svBitVecVal *rf_waddr,
    const svBitVecVal *rf_wdata_high,
    const svBitVecVal *rf_wdata_low,
    const svBitVecVal *wb_reg_pc,
    const svBitVecVal *wb_reg_inst
) {
  TRY({
        vbridge_impl_instance.dpiCommitPeek(CommitPeekInterface{
            rf_wen,
            wb_valid,
            *rf_waddr,
            *rf_wdata_high,
            *rf_wdata_low,
            *wb_reg_pc,
            *wb_reg_inst
        });
      })


}




