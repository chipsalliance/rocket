# Rocket

This is the undergoing refactor project of rocket core.

## Refactor Procedure
Here are some notes for refactoring:  
- There will be two projects: `rocket` and `diplomatic`, `rocket` will depend on `chisel3`, `tilelink` projects, `diplomatic` is the source code originally pulled from rocket-chip, and it will depend on rocket-chip for using diplomacy and cde.  
- There won't be any unrelated change during this refactoring.  
- Upstream rocket core from rocket-chip bug fixes will be cherry-picked to this project.  

Here are the milestones to be done:  
1. Add CI for diplomatic to pass the smoketest(hello world elf).  
1. Refactor out `cde` from `rocket`, start to `git mv` file by file from `diplomatic` to `rocket` project.  
1. Add CI for rocket for standalone test.

## Pending PRs
We might need some unmerged feature from upstream, they listed below. `make update-patch` will download them and store, `make patch` will apply them in sequence:
<!-- BEGIN-PATCH -->
<!-- END-PATCH -->
