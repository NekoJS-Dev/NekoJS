"""Keep bulky historical evidence outside Git; run --help for recovery commands."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

EVIDENCE = Path("docs/architecture-refactor/evidence")
CATALOG = EVIDENCE / "archive-index.json"
LIMIT = 256 * 1024
RAW = {".json", ".txt", ".log", ".patch", ".diff", ".jsonl"}
ARCHIVES = {".zip", ".jfr", ".gz", ".7z", ".tar", ".hprof"}


def git(root, *args):
    return subprocess.check_output(["git", *args], cwd=root)


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def bulky(path, size):
    return path.suffix.lower() in ARCHIVES or (path.suffix.lower() in RAW and size > LIMIT)


def contained(base, relative):
    path = (base / relative).resolve()
    if not path.is_relative_to(base.resolve()):
        raise ValueError("Path escapes its storage root: " + str(relative))
    return path


def candidates(root):
    raw = git(root, "ls-tree", "-rz", "-l", "HEAD", "--", EVIDENCE.as_posix())
    rows = []
    for entry in raw.split(b"\0"):
        if not entry:
            continue
        meta, name = entry.split(b"\t", 1)
        mode, kind, oid, size = meta.split()
        path = name.decode("utf-8")
        if kind != b"blob" or not bulky(Path(path), int(size)):
            continue
        file = contained(root, path)
        if not file.is_file() or file.is_symlink():
            raise ValueError("Expected a regular evidence file: " + path)
        rows.append({"path": path, "gitBlob": oid.decode(), "gitBytes": int(size),
                     "bytes": file.stat().st_size, "sha256": digest(file)})
    return rows


def external_root(root, archive):
    if not archive:
        raise ValueError("Provide --archive-dir or NEKOJS_EVIDENCE_ARCHIVE")
    target = Path(archive).resolve()
    common = Path(os.fsdecode(git(root, "rev-parse", "--git-common-dir")).strip())
    common = (root / common).resolve()
    for forbidden in (root.resolve(), common.parent):
        if target.is_relative_to(forbidden):
            raise ValueError("Archive storage must be outside the repository and its worktrees")
    return target


def atomic_copy(source, target, expected):
    target.parent.mkdir(parents=True, exist_ok=True)
    if target.exists():
        if not target.is_file() or digest(target) != expected:
            raise ValueError("Existing destination has different content: " + str(target))
        return
    handle, temporary = tempfile.mkstemp(prefix=".evidence-", dir=target.parent)
    os.close(handle)
    try:
        shutil.copyfile(source, temporary)
        if digest(Path(temporary)) != expected:
            raise ValueError("Copied artifact failed SHA-256 verification")
        os.replace(temporary, target)
    finally:
        Path(temporary).unlink(missing_ok=True)


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    text = json.dumps(data, indent=2, ensure_ascii=False) + "\n"
    handle, temporary = tempfile.mkstemp(prefix=".evidence-index-", dir=path.parent)
    try:
        with os.fdopen(handle, "w", encoding="utf-8", newline="\n") as stream:
            stream.write(text)
        os.replace(temporary, path)
    finally:
        Path(temporary).unlink(missing_ok=True)


def load_catalog(root):
    data = json.loads((root / CATALOG).read_text(encoding="utf-8"))
    if data.get("schemaVersion") != 1:
        raise ValueError("Unsupported evidence catalog schema")
    seen = set()
    for row in data["files"]:
        path = Path(row["path"])
        if not path.is_relative_to(EVIDENCE) or row["path"] in seen:
            raise ValueError("Invalid or duplicate evidence catalog path")
        contained(root / EVIDENCE, path.relative_to(EVIDENCE))
        sha = row["sha256"]
        if len(sha) != 64 or any(c not in "0123456789abcdef" for c in sha):
            raise ValueError("Invalid evidence SHA-256")
        seen.add(row["path"])
    return data


def selected(data, prefix):
    if not prefix:
        return data["files"]
    name = (EVIDENCE / prefix).as_posix().rstrip("/")
    rows = [r for r in data["files"] if r["path"] == name or r["path"].startswith(name + "/")]
    if not rows:
        raise ValueError("No archived evidence matches --path " + prefix)
    return rows


def verify(archive, rows):
    for row in rows:
        source = archive / "sha256" / row["sha256"]
        if not source.is_file() or source.stat().st_size != row["bytes"] or digest(source) != row["sha256"]:
            raise ValueError("Missing or corrupt external evidence: " + row["path"])


def prune(root, archive, rows):
    # Validate the entire selection before removing any original or restored file.
    verify(archive, rows)
    for row in rows:
        file = contained(root, row["path"])
        if file.exists() and (not file.is_file() or digest(file) != row["sha256"]):
            raise ValueError("Refusing to remove changed evidence: " + row["path"])
    for row in rows:
        contained(root, row["path"]).unlink(missing_ok=True)


def check(root):
    failures = []
    # Only tracked/index or nonignored additions belong in the next commit.
    raw = git(root, "ls-files", "-z", "--cached", "--others", "--exclude-standard", "--", EVIDENCE.as_posix())
    for name in sorted(set(raw.split(b"\0")) - {b""}):
        relative = os.fsdecode(name)
        file = contained(root, relative)
        if not file.exists():
            continue
        size = file.stat().st_size
        if bulky(file, size):
            failures.append(relative)
    if failures:
        raise ValueError("Bulky evidence must use external storage:\n" + "\n".join(failures))
    if (root / CATALOG).exists():
        load_catalog(root)
    print("Evidence storage check passed")


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, epilog="Restore an entire evidence folder before running its historical verify.py; some verifiers also need sibling folders. No network upload or Git history mutation is performed.")
    parser.add_argument("command", choices=["plan", "migrate", "verify", "restore", "prune", "check"])
    parser.add_argument("--archive-dir", default=os.environ.get("NEKOJS_EVIDENCE_ARCHIVE"))
    parser.add_argument("--path", help="Optional file/folder relative to docs/architecture-refactor/evidence")
    args = parser.parse_args(argv)
    root = Path(__file__).resolve().parent.parent
    if args.command == "check":
        check(root)
        return
    if args.command in {"plan", "migrate"}:
        rows = candidates(root)
        print(json.dumps({"files": len(rows), "bytes": sum(r["bytes"] for r in rows)}))
        if args.command == "plan":
            return
        if (root / CATALOG).exists():
            raise ValueError("Catalog already exists; verify/restore/prune the existing archive instead")
        subprocess.run(["git", "diff", "--exit-code", "HEAD", "--", EVIDENCE.as_posix()], cwd=root, check=True, stdout=subprocess.DEVNULL)
        archive = external_root(root, args.archive_dir)
        for row in rows:
            atomic_copy(contained(root, row["path"]), archive / "sha256" / row["sha256"], row["sha256"])
        verify(archive, rows)
        data = {"schemaVersion": 1, "sourceCommit": git(root, "rev-parse", "HEAD").decode().strip(),
                "storage": {"kind": "external-directory", "status": "local-only; not published", "environmentVariable": "NEKOJS_EVIDENCE_ARCHIVE", "objectPath": "sha256/<sha256>"},
                "recovery": "python tools/evidence_archive.py restore --archive-dir <archive-directory> [--path <evidence-folder>]",
                "files": rows}
        write_json(archive / "archive-index.json", data)
        write_json(root / CATALOG, data)
        prune(root, archive, rows)
    else:
        archive = external_root(root, args.archive_dir)
        rows = selected(load_catalog(root), args.path)
        verify(archive, rows)
        if args.command == "restore":
            # Preflight all destinations so a changed local file is never overwritten.
            for row in rows:
                target = contained(root, row["path"])
                if target.exists() and (not target.is_file() or digest(target) != row["sha256"]):
                    raise ValueError("Refusing to overwrite local evidence: " + row["path"])
            for row in rows:
                atomic_copy(archive / "sha256" / row["sha256"], contained(root, row["path"]), row["sha256"])
        elif args.command == "prune":
            prune(root, archive, rows)
        print(json.dumps({"command": args.command, "files": len(rows), "bytes": sum(r["bytes"] for r in rows)}))


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, subprocess.CalledProcessError) as error:
        raise SystemExit(str(error)) from error
