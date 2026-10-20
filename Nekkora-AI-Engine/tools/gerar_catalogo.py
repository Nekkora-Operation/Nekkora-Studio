"""Gera o catálogo de capacidades do nekkora_rpa a partir do pacote INSTALADO (STUDIO-014).

    <python-com-nekkora_rpa> tools/gerar_catalogo.py --framework-repo <Nekkora-RPA-Framework> [--check]

Lê a curadoria (finalidade, efeito, limitações, seção da documentação), introspecta cada objeto no pacote instalado
(assinatura, primeiro parágrafo da documentação do código, arquivo e linha dentro do pacote) e confere que cada seção de
documentação citada EXISTE no repositório do framework. Escreve ``catalog/nekkora-rpa-<versão>.json`` (ordem e
formatação determinísticas). ``--check`` não escreve: falha se o arquivo existente divergir do que seria gerado.
"""

from __future__ import annotations

import argparse
import importlib.util
import inspect
import json
import subprocess
import sys
from pathlib import Path
from typing import Any

AQUI = Path(__file__).resolve().parent
ENGINE = AQUI.parent
SONDA = ENGINE / "src" / "main" / "resources" / "nekkora" / "ai" / "python" / "sonda_framework.py"
EFFECTS = {"NONE", "READ", "NAVIGATION", "MAY_AFFECT_TARGET", "LOCAL_FILES"}


def carregar_sonda() -> Any:
    spec = importlib.util.spec_from_file_location("sonda_framework", SONDA)
    assert spec is not None and spec.loader is not None
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def resumo(doc: str | None) -> str:
    if not doc:
        return ""
    para = inspect.cleandoc(doc).split("\n\n", 1)[0]
    text = " ".join(para.split())
    return text if len(text) <= 400 else text[:397].rstrip() + "..."


def fonte(obj: Any) -> dict[str, Any]:
    import nekkora_rpa

    raiz = Path(nekkora_rpa.__file__).resolve().parent.parent
    alvo = obj.fget if isinstance(obj, property) else obj
    arquivo = Path(inspect.getsourcefile(alvo) or "").resolve()
    _, linha = inspect.getsourcelines(alvo)
    return {"file": arquivo.relative_to(raiz).as_posix(), "line": linha}


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--framework-repo", required=True, type=Path)
    ap.add_argument("--check", action="store_true")
    a = ap.parse_args()
    sonda = carregar_sonda()
    cur = json.loads((AQUI / "capacidades-curadoria.json").read_text(encoding="utf-8"))
    objetos = {c["id"]: c["object"] for c in cur["capabilities"]}
    desc = sonda.describe(objetos)
    if desc["missing"]:
        print("curadoria cita objetos que NÃO existem no pacote instalado:", desc["missing"], file=sys.stderr)
        return 1
    caps = []
    for c in cur["capabilities"]:
        if c["effect"] not in EFFECTS:
            print("efeito desconhecido:", c["id"], file=sys.stderr)
            return 1
        doc_file = a.framework_repo / c["docs"]["file"]
        if not doc_file.is_file() or c["docs"]["section"] not in doc_file.read_text(encoding="utf-8").splitlines():
            print("seção de documentação inexistente:", c["id"], c["docs"], file=sys.stderr)
            return 1
        obj = sonda.resolve(c["object"])
        caps.append({
            "id": c["id"],
            "name": c["object"].split(":", 1)[1],
            "group": c["group"],
            "import": c["import"],
            "signature": desc["signatures"][c["id"]],
            "summary": resumo(inspect.getdoc(obj)),
            "purpose": c["purpose"],
            "effect": c["effect"],
            "limitations": c["limitations"],
            "source": fonte(obj),
            "docs": c["docs"],
        })
    commit = subprocess.run(["git", "-C", str(a.framework_repo), "rev-parse", "HEAD"], capture_output=True, text=True, check=True).stdout.strip()
    catalogo = {
        "schema": "nekkora-ai-engine/framework-capabilities",
        "version": "1.0.0",
        "framework": {"name": "nekkora-rpa", "version": desc["version"], "fingerprint": desc["fingerprint"],
                      "repository": "Nekkora-Operation/Nekkora-RPA-Framework", "commit": commit},
        "extending": {"file": "docs/extending.md", "section": "## Nova operação (Lib)"},
        "capabilities": caps,
    }
    texto = json.dumps(catalogo, ensure_ascii=False, indent=2) + "\n"
    destino = ENGINE / "catalog" / f"nekkora-rpa-{desc['version']}.json"
    if a.check:
        atual = destino.read_text(encoding="utf-8") if destino.exists() else ""
        if atual.replace("\r\n", "\n") != texto:
            print("catálogo DESATUALIZADO:", destino.name, file=sys.stderr)
            return 1
        print("catálogo em dia:", destino.name, desc["fingerprint"])
        return 0
    destino.write_bytes(texto.encode("utf-8"))
    print("escrito:", destino.name, len(caps), "capacidades", desc["fingerprint"])
    return 0


if __name__ == "__main__":
    sys.exit(main())
