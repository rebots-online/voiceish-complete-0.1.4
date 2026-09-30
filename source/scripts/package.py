#!/usr/bin/env python3
"""Package the complete immutable release, including binaries and standalone HTML."""
from pathlib import Path
import runpy
runpy.run_path(str(Path(__file__).with_name("package-complete.py")),run_name="__main__")
