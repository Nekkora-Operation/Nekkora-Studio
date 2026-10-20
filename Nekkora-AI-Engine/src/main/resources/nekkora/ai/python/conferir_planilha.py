"""Conferência da planilha gravada pelo TESTE AUTORIZADO (NEKKORA-E2E-002) — sem navegador, sem rede.

Uso pelo Studio: ``python -E -s -B conferir_planilha.py <pasta-do-projeto-gerado> <planilha.xlsx>``. Reabre o arquivo com
o ``read_back`` do nekkora_rpa e o esquema DECLARADO no próprio projeto gerado (``main.PLANILHA``): uma aba, cabeçalho
idêntico, nenhuma fórmula, nenhuma célula fora das colunas, chave sem repetição, nenhuma célula obrigatória vazia.
Responde UM objeto JSON com ``ok``, ``code``, ``rows``, ``headers`` (rótulos de interface) e ``sha256`` — nunca o
conteúdo de uma célula.
"""

from __future__ import annotations

import hashlib
import importlib
import json
import sys
from pathlib import Path


def conferir(projeto: Path, planilha: Path) -> dict[str, object]:
    sys.path.insert(0, str(projeto))
    try:
        principal = importlib.import_module("main")
    finally:
        sys.path.remove(str(projeto))
    from nekkora_rpa.xlsx import XlsxOutputError, XlsxSchema, read_back

    schema = getattr(principal, "PLANILHA", None)
    if not isinstance(schema, XlsxSchema):
        return {"ok": False, "code": "SCHEMA_NOT_DECLARED", "rows": 0, "headers": [], "sha256": ""}
    sha = "sha256:" + hashlib.sha256(planilha.read_bytes()).hexdigest()
    headers = list(schema.headers)
    try:
        rows = read_back(planilha, schema)
    except XlsxOutputError as err:
        return {"ok": False, "code": err.code, "rows": 0, "headers": headers, "sha256": sha}
    fields = [c.field for c in schema.columns]
    key = fields.index(schema.key_field)
    chaves = [r[key] for r in rows]
    if not rows:
        code = "NO_ROWS"
    elif len(set(chaves)) != len(chaves):
        code = "DUPLICATE_KEY"
    elif any(c.required and not r[i] for r in rows for i, c in enumerate(schema.columns)):
        code = "REQUIRED_CELL_EMPTY"
    else:
        code = ""
    return {"ok": code == "", "code": code, "rows": len(rows), "headers": headers, "sha256": sha}


def main() -> int:
    if len(sys.argv) != 3 or not Path(sys.argv[1]).is_dir() or not Path(sys.argv[2]).is_file():
        print(json.dumps({"ok": False, "code": "ARGS", "rows": 0, "headers": [], "sha256": ""}))
        return 2
    try:
        out = conferir(Path(sys.argv[1]), Path(sys.argv[2]))
    except Exception as exc:  # noqa: BLE001 - qualquer falha vira código, nunca conteúdo
        out = {"ok": False, "code": "VERIFY_FAILED_" + type(exc).__name__, "rows": 0, "headers": [], "sha256": ""}
    print(json.dumps(out, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())
