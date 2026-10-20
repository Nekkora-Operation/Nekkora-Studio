"""Gera os ARTEFATOS HOMOLOGADOS do nekkora-rpa que o Studio serve ao Dependency Manager (NEKKORA-PACKAGING-001).

Para cada versão homologada (a mesma do catálogo de capacidades): exporta o commit EXATO do framework (git archive — o
checkout local não entra), constrói o wheel de forma reprodutível (backend travado de ``constraints/build.lock``,
``SOURCE_DATE_EPOCH`` = data do commit), constrói DUAS vezes e exige o mesmo SHA-256, confere versão e conteúdo, e grava
em ``Nekkora-AI-Engine/framework/``: o wheel, o lock por hash das dependências e o manifesto ``artefatos.json``.

Enquanto a versão não estiver publicada em índice aprovado pela titular, a origem registrada é ``BUNDLED`` (o wheel vai
dentro do Studio). Depois da publicação, a origem passa a ``INDEX`` com o mesmo SHA-256.

Uso (na raiz do Nekkora-Studio):
    python Nekkora-AI-Engine/tools/empacotar_framework.py --framework-repo ../Nekkora-RPA-Framework
"""

from __future__ import annotations

import argparse
import hashlib
import io
import json
import os
import shutil
import subprocess
import sys
import tarfile
import tempfile
import zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
ENGINE = HERE.parent
OUT = ENGINE / "framework"
CATALOG = ENGINE / "catalog"
SCHEMA = "nekkora-ai-engine/framework-artifacts"


def sh(cmd: list[str], **kw: object) -> subprocess.CompletedProcess[bytes]:
    r = subprocess.run(cmd, capture_output=True, check=False, **kw)  # type: ignore[call-overload]  # noqa: S603
    if r.returncode != 0:
        raise SystemExit(f"falhou: {' '.join(cmd[:4])}…\n{r.stdout.decode(errors='replace')[-2000:]}{r.stderr.decode(errors='replace')[-2000:]}")
    return r


def sha256(b: bytes) -> str:
    return hashlib.sha256(b).hexdigest()


def build_wheel(repo: Path, commit: str, build_py: Path, tmp: Path, n: int) -> Path:
    src = tmp / f"src-{n}"
    src.mkdir()
    # sem conversão de fim de linha (core.autocrlf do Windows): os bytes do repositório, iguais em qualquer máquina
    archive = sh(["git", "-c", "core.autocrlf=false", "-c", "core.eol=lf", "-C", str(repo), "archive", "--format=tar", commit]).stdout
    with tarfile.open(fileobj=io.BytesIO(archive)) as t:
        t.extractall(src, filter="data")
    epoch = sh(["git", "-C", str(repo), "log", "-1", "--format=%ct", commit]).stdout.decode().strip()
    env = {k: v for k, v in os.environ.items() if not k.startswith(("PYTHON", "PIP_"))}
    env["SOURCE_DATE_EPOCH"] = epoch
    out = tmp / f"dist-{n}"
    sh([str(build_py), "-m", "build", "--no-isolation", "--wheel", "--outdir", str(out), str(src)], env=env)
    return next(out.glob("*.whl"))


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--framework-repo", required=True)
    a = ap.parse_args()
    repo = Path(a.framework_repo).resolve()
    build_lock = repo / "constraints" / "build.lock"
    lab_lock = repo / "constraints" / "lab.lock"
    tools_lock = repo / "constraints" / "verificacao.lock"
    for f in (build_lock, lab_lock, tools_lock):
        if not f.is_file():
            raise SystemExit(f"falta {f} (framework com o PACKAGING-001)")
    OUT.mkdir(exist_ok=True)
    versions: dict[str, object] = {}
    with tempfile.TemporaryDirectory(prefix="nk-artefatos-") as d:
        tmp = Path(d)
        sh([sys.executable, "-m", "venv", str(tmp / "build-venv")])
        build_py = tmp / "build-venv" / ("Scripts/python.exe" if os.name == "nt" else "bin/python")
        sh([str(build_py), "-m", "pip", "install", "--require-hashes", "--only-binary=:all:", "--disable-pip-version-check", "-r", str(build_lock)])
        for cat in sorted(CATALOG.glob("nekkora-rpa-*.json")):
            fw = json.loads(cat.read_text(encoding="utf-8"))["framework"]
            version, commit = fw["version"], fw["commit"]
            w1 = build_wheel(repo, commit, build_py, tmp, 1 + 2 * len(versions))
            w2 = build_wheel(repo, commit, build_py, tmp, 2 + 2 * len(versions))
            b1, b2 = w1.read_bytes(), w2.read_bytes()
            if sha256(b1) != sha256(b2):
                raise SystemExit(f"{version}: wheel NÃO reprodutível ({sha256(b1)[:12]} ≠ {sha256(b2)[:12]})")
            if w1.name != f"nekkora_rpa-{version}-py3-none-any.whl":
                raise SystemExit(f"{version}: nome inesperado {w1.name}")
            with zipfile.ZipFile(w1) as z:
                meta = z.read(f"nekkora_rpa-{version}.dist-info/METADATA").decode().replace("\r\n", "\n")  # setuptools no Windows grava CRLF
                if f"\nVersion: {version}\n" not in meta or not any(n.endswith("py.typed") for n in z.namelist()):
                    raise SystemExit(f"{version}: metadados/conteúdo inesperados")
                schemas = sorted(n.rsplit("/", 1)[1] for n in z.namelist() if "/contracts/" in n and n.endswith(".json"))
            shutil.copyfile(w1, OUT / w1.name)
            versions[version] = {
                "wheel": w1.name,
                "sha256": sha256(b1),
                "bytes": len(b1),
                "commit": commit,
                "catalog": cat.name,
                "schemas": schemas,
                "source": "BUNDLED",
                "index": None,
            }
            print(version, w1.name, sha256(b1)[:16], "reprodutível")
    shutil.copyfile(lab_lock, OUT / "dependencias.lock")
    shutil.copyfile(tools_lock, OUT / "verificacao.lock")
    manifest = {
        "schema": SCHEMA,
        "version": "1.0.0",
        "distribution": "nekkora-rpa",
        "requiresPython": ">=3.12",
        "versions": versions,
        "dependencies": {"lock": "dependencias.lock", "sha256": sha256((OUT / "dependencias.lock").read_bytes()), "index": "https://pypi.org/simple/"},
        "tools": {"lock": "verificacao.lock", "sha256": sha256((OUT / "verificacao.lock").read_bytes()), "index": "https://pypi.org/simple/",
                  "ruff": "0.17.1", "mypy": "2.4.0"},
        "frameworkRepository": "Nekkora-Operation/Nekkora-RPA-Framework",
    }
    (OUT / "artefatos.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=1) + "\n", encoding="utf-8", newline="\n")
    print("artefatos.json:", ", ".join(versions))
    return 0


if __name__ == "__main__":
    sys.exit(main())
