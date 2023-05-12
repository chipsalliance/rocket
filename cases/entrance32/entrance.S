.global _start
_start:
    auipc t0, 0x0
    addi a1, t0, 32
    csrr a0, mhartid
    lw t0, 24(t0)
    jr t0