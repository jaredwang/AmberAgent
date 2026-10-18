"""Run one task-owned device fixture; keep screenshots from different runs separate."""
import argparse
import json
from pathlib import Path
import re
import shlex
import subprocess
import sys
import time

parser = argparse.ArgumentParser()
parser.add_argument("label")
parser.add_argument("selector")
parser.add_argument("output_folder")
parser.add_argument("--argument", action="append", default=[])
args = parser.parse_args()
if not re.fullmatch(r"[a-zA-Z0-9_-]+", args.label):
    parser.error("label must be a simple task name")
if not re.fullmatch(r"modularization-phase-[a-zA-Z0-9_-]+", args.output_folder):
    parser.error("only task-owned fixture evidence folders are supported")
root = Path(__file__).resolve().parents[3]
destination = root / ".workflow/android-modularization-20261018/evidence" / args.label
destination.mkdir(parents=True, exist_ok=False)
adb = ["/opt/homebrew/bin/adb", "-s", "emulator-5580"]
remote = "/sdcard/Android/data/app.amber.agent.graphite/files/" + args.output_folder
archive = remote + "-archive-" + args.label
if subprocess.run(adb + ["shell", "test", "-d", remote]).returncode == 0:
    subprocess.run(adb + ["shell", shlex.join(["mv", remote, archive])], check=True)
for name, command in (("size", ["wm", "size"]), ("density", ["wm", "density"]),
                      ("font-scale", ["settings", "get", "system", "font_scale"])):
    result = subprocess.run(adb + ["shell", *command], capture_output=True, text=True, check=True)
    (destination / (name + ".txt")).write_text(result.stdout)
command = adb + ["shell", "am", "instrument", "-w", "-r", "-e", "class", args.selector]
for argument in args.argument:
    key, value = argument.split("=", 1)
    command += ["-e", key, value]
command += ["app.amber.agent.graphite.test/app.amber.agent.AmberAgentUiSmokeTestRunner"]
started = time.time()
print(json.dumps({"label": args.label, "destination": str(destination)}), flush=True)
with (destination / "instrumentation.txt").open("w") as log:
    result = subprocess.run(command, stdout=log, stderr=subprocess.STDOUT)
output = (destination / "instrumentation.txt").read_text()
ok = re.search(r"OK \((\d+) tests?\)", output)
skipped = output.count("INSTRUMENTATION_STATUS_CODE: -3")
pull = subprocess.run(adb + ["pull", remote, str(destination)], capture_output=True, text=True)
execution = {"startedAt": started, "command": command, "adbExit": result.returncode,
             "passed": bool(ok) and result.returncode == 0 and "FAILURES!!!" not in output,
             "reportedTests": int(ok.group(1)) if ok else None, "skipped": skipped,
             "previousEvidenceArchivedAt": archive, "pullExit": pull.returncode,
             "pullOutput": pull.stdout + pull.stderr}
(destination / "execution.json").write_text(json.dumps(execution, indent=2) + "\n")
print(json.dumps(execution), flush=True)
print(output[-7000:], flush=True)
sys.exit(0 if execution["passed"] and skipped == 0 else 1)
