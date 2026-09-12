@echo off
rem Detached sampler launcher (long sessions survive the parent shell).
rem Usage: run-mode.cmd <Mode> [extra sample.ps1 args...]
rem   run-mode.cmd startup -Warmup 2 -Samples 5
rem   run-mode.cmd bench   -Rounds 3
rem   run-mode.cmd reload  -Reloads 5
rem   run-mode.cmd probe   -ProbeSamples 5
rem Logs go to bench\perf\out\sampler-<Mode>.log (out/ is gitignored).
rem Edit PERF02_PROJECT / GRADLE_USER_HOME below if your checkout differs.
set PERF02_PROJECT=D:\mcmodDemo\NekoJS-perf02\NekoJS-mult
set GRADLE_USER_HOME=D:\mcmodDemo\NekoJS\.gradle-perf02
set MODE=%1
if "%MODE%"=="" (echo usage: run-mode.cmd Mode [args...] & exit /b 2)
shift
cd /d %PERF02_PROJECT%
powershell -NoProfile -ExecutionPolicy Bypass -File "%PERF02_PROJECT%\bench\perf\sample.ps1" -Mode %MODE% %1 %2 %3 %4 %5 %6 > "%PERF02_PROJECT%\bench\perf\out\sampler-%MODE%.log" 2>&1
