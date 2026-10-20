#!/usr/bin/env python3
"""Reject private signing code, runtime data, and credential-shaped values."""

from __future__ import annotations

import pathlib
import re
import subprocess
import sys


ROOT = pathlib.Path(__file__).resolve().parents[1]
FORBIDDEN_NAMES = {
    "ProductIssuedLicense.java",
    "ProductLicenseProperties.java",
    "ProductLicenseRegistrar.java",
}
FORBIDDEN_SUFFIXES = (".mv.db", ".trace.db", ".lock.db", ".p12", ".pfx", ".lic")
CONTENT_RULES = {
    "private key": re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----"),
    "GitHub token": re.compile(r"(?:ghp|github_pat)_[A-Za-z0-9_]{20,}"),
    "Stripe secret": re.compile(r"(?:sk|rk)_(?:live|test)_[A-Za-z0-9]{16,}"),
    "AWS access key": re.compile(r"AKIA[0-9A-Z]{16}"),
    "private registrar implementation": re.compile(
        r"ProductLicenseRegistrar|ProductLicenseProperties|registrar-grant|WASM-HS256-v1"
    ),
    "private review login": re.compile(
        r"ReviewPasswordLogin|/password-login|review@flyfish[.]group"
    ),
    "production secret path": re.compile(
        r"office-preview-wasm-seal-2026-06[.]secret[.]json"
    ),
    "known production host": re.compile(r"101[.]200[.]59[.]238|20[.]89[.]60[.]66"),
}


def tracked_files() -> list[pathlib.Path]:
    result = subprocess.run(
        ["git", "ls-files", "--cached", "--others", "--exclude-standard", "-z"],
        cwd=ROOT,
        check=True,
        capture_output=True,
    )
    return [ROOT / path.decode() for path in result.stdout.split(b"\0") if path]


def main() -> int:
    violations: list[str] = []
    for path in tracked_files():
        relative = path.relative_to(ROOT).as_posix()
        if path.name in FORBIDDEN_NAMES:
            violations.append(f"forbidden source: {relative}")
        if relative != ".env.example" and (path.name == ".env" or path.name.startswith(".env.")):
            violations.append(f"environment file: {relative}")
        if relative.endswith(FORBIDDEN_SUFFIXES) or relative.endswith(".secret.json"):
            violations.append(f"runtime or secret artifact: {relative}")
        if relative == "scripts/check-public-boundary.py":
            continue
        try:
            data = path.read_bytes()
        except OSError:
            continue
        if b"\0" in data or len(data) > 5_000_000:
            continue
        text = data.decode("utf-8", errors="ignore")
        for name, pattern in CONTENT_RULES.items():
            if pattern.search(text):
                violations.append(f"{name}: {relative}")

    if violations:
        print("Public boundary check failed:", file=sys.stderr)
        for violation in sorted(set(violations)):
            print(f"- {violation}", file=sys.stderr)
        return 1
    print("Public boundary check passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
