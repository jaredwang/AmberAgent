"""Run one serialized gate and archive only test reports produced by this run."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[3]
workflow = root / ".workflow/android-modularization-20261018"
label, *arguments = sys.argv[1:]
destination = workflow / "evidence" / label
destination.mkdir(parents=True, exist_ok=False)
log = workflow / "results" / f"{label}.log"
environment = os.environ.copy()
environment["JAVA_HOME"] = "/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
environment["PATH"] = environment["JAVA_HOME"] + "/bin:" + environment["PATH"]
started = time.time()
command = ["./gradlew", "--offline", *arguments]
print(json.dumps({"label": label, "log": str(log)}), flush=True)
with log.open("w") as output:
    result = subprocess.run(command, cwd=root, env=environment, stdout=output, stderr=subprocess.STDOUT)
classes = []
for report in root.glob("**/build/test-results/**/TEST-*.xml"):
    if report.stat().st_mtime < started - 1:
        continue
    xml = ET.parse(report).getroot()
    classes.append({"name": xml.get("name"), "tests": int(xml.get("tests", 0)),
                    "failures": int(xml.get("failures", 0)), "errors": int(xml.get("errors", 0)),
                    "skipped": int(xml.get("skipped", 0)), "source": str(report.relative_to(root))})
    module_destination = destination / report.relative_to(root).parent
    module_destination.mkdir(parents=True, exist_ok=True)
    shutil.copy2(report, module_destination / report.name)
execution = {"startedAt": started, "exitCode": result.returncode,
             "command": command, "freshXmlCount": len(classes), "classes": classes}
(destination / "_execution.json").write_text(json.dumps(execution, indent=2) + "\n")
print(json.dumps({"label": label, "exitCode": result.returncode, "freshXmlCount": len(classes),
                  "totals": {key: sum(item[key] for item in classes)
                             for key in ("tests", "failures", "errors", "skipped")},
                  "failedClasses": [item for item in classes if item["failures"] or item["errors"]]}), flush=True)
if result.returncode:
    lines = log.read_text().splitlines()
    diagnostics = [line for line in lines if line.startswith("e: ") or " FAILED" in line]
    print("\n".join(diagnostics + lines[-24:]), flush=True)
sys.exit(result.returncode)
