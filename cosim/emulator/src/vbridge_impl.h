#pragma once

#include <optional>
#include <queue>

#include "mmu.h"
#include <VTestBench__Dpi.h>
#include "verilated_fst_c.h"

#include "simple_sim.h"
#include "util.h"
#include "encoding.h"
#include "spike_event.h"

#include <svdpi.h>

class SpikeEvent;

struct TLReqRecord {
    uint64_t data;
    uint32_t size_by_byte;
    uint16_t source;

    /// when opType set to nil, it means this record is already sent back
    enum class opType {
        Nil, Get, PutFullData
    } op;
    int remaining_cycles;

    TLReqRecord(uint64_t data, uint32_t size_by_byte, uint16_t source, opType op, int cycles) : data(data),
                                                                                                size_by_byte(
                                                                                                    size_by_byte),
                                                                                                source(source), op(op),
                                                                                                remaining_cycles(
                                                                                                    cycles) {};
};

struct FetchRecord {
    uint64_t data;
    uint16_t source;
    bool remaining;
};
struct AquireRecord {
    uint64_t data;
    uint16_t param;
    uint16_t source;
    uint16_t size;
    bool remaining;
    bool is_releaseData;
};

class VBridgeImpl {
public:
    explicit VBridgeImpl();

    void dpiDumpWave();

    void dpiInitCosim();

    void dpiPokeTL(const TlPokeInterface &tl_poke);

    void dpiPeekTL(svBit miss, svBitVecVal pc, const TlAPeekInterface &tl_peek, const TlCPeekInterface &tl_c);

    void dpiRefillQueue();

    void dpiCommitPeek(CommitPeekInterface cmInterface);

    void init_spike();

    uint64_t get_t();

    uint8_t load(uint64_t address);

    int timeoutCheck();

    uint64_t getCycle() { return ctx->time(); }


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

    const uint64_t timeout = std::stoul(get_env_arg("COSIM_timeout"), nullptr, 10);

    const uint64_t pass_address = std::stoul(get_env_arg("passaddress"), nullptr, 16);

    //Spike
    const size_t to_rtl_queue_size = 10;
    std::list<SpikeEvent> to_rtl_queue;

    std::map<reg_t, TLReqRecord> tl_banks;
    //todo: configure it
    FetchRecord fetch_banks[16];
    AquireRecord aquire_banks[16];

    void loop_until_se_queue_full();

    std::optional<SpikeEvent> spike_step();

    std::optional<SpikeEvent> create_spike_event(insn_fetch_t fetch);

    // methods for TL channel
    void receive_tl_req();

    void record_rf_access(CommitPeekInterface cmInterface);

    int beforeReturnAquire;

    int afterReturnAquire;

    bool waitforMutiCycleInsn;
    bool mutiCycleInsnDone;
    uint64_t pendingInsn_pc;

    int get_mem_req_cycles() {
      return 1;
    };

};

extern VBridgeImpl vbridge_impl_instance;
