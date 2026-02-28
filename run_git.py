import subprocess
import sys

commands = [
    ["git", "status"],
    ["git", "branch", "-M", "main"],
    ["git", "remote", "add", "origin", "https://github.com/DiscoveringRohit/Raksha.git"],
    ["git", "push", "-u", "origin", "main"]
]

for cmd in commands:
    print(f"Running: {' '.join(cmd)}")
    result = subprocess.run(cmd, capture_output=True, text=True)
    print("STDOUT:", result.stdout)
    if result.stderr:
        print("STDERR:", result.stderr)
    if result.returncode != 0:
        print(f"Failed with code {result.returncode}")
        break
