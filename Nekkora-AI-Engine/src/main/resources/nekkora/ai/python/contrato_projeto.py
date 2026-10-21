"""Testes de CONTRATO do projeto gerado (STUDIO-014, camada V5) — sem navegador, sem rede, sem executar o processo.

Uso pelo Studio: ``python -I -B contrato_projeto.py <pasta-do-projeto>``. Responde um JSON com cada verificação
(id, ok, código do motivo). Nenhum conteúdo de arquivo, valor ou caminho do usuário vai para a saída.

Verifica: hashes do manifesto; registro de alvos válido para o nekkora_rpa instalado; toda chave usada nas Pages existe
no registro; todo StepId do código está no manifesto (e vice-versa para etapas com alvo); preenchimentos só recebem
parâmetro/variável ou segredo do provedor (nunca literal); o processo é um RpaProcess e declara os segredos que usa.
"""

from __future__ import annotations

import ast
import hashlib
import importlib
import json
import sys
from pathlib import Path
from typing import Any


def check(results: list[dict[str, Any]], cid: str, ok: bool, code: str = "") -> None:
    results.append({"id": cid, "ok": bool(ok), "code": "" if ok else code})


def calls(tree: ast.AST) -> list[ast.Call]:
    return [n for n in ast.walk(tree) if isinstance(n, ast.Call)]


def literal(node: ast.AST) -> str | None:
    return node.value if isinstance(node, ast.Constant) and isinstance(node.value, str) else None


def run(root: Path) -> list[dict[str, Any]]:
    out: list[dict[str, Any]] = []
    manifest = json.loads((root / "manifest.json").read_text(encoding="utf-8"))
    files: dict[str, str] = manifest.get("files", {})
    bad = [f for f, h in files.items() if "sha256:" + hashlib.sha256((root / f).read_bytes()).hexdigest() != h]
    check(out, "MANIFEST_HASHES", not bad and bool(files), "MANIFEST_HASH_MISMATCH")

    from nekkora_rpa.selectors.registry import SelectorRegistry

    env = "lab"
    toml = (root / "config" / "execucao.toml").read_text(encoding="utf-8")
    if 'environment = "benchmark"' in toml:
        env = "benchmark"
    try:
        registry = SelectorRegistry.load(root / "selectors.json", environment=env)
        keys = set(registry.keys())
        check(out, "SELECTORS_VALID", True)
    except Exception:  # noqa: BLE001 - qualquer recusa do framework é falha do contrato
        keys = set()
        check(out, "SELECTORS_VALID", False, "SELECTORS_REJECTED_BY_FRAMEWORK")

    pages = ast.parse((root / "paginas.py").read_text(encoding="utf-8"))
    used: set[str] = set()
    step_ids: set[str] = set()
    fills_ok = True
    for cls in [n for n in pages.body if isinstance(n, ast.ClassDef)]:
        prefix = None
        for st in cls.body:
            if isinstance(st, ast.Assign) and any(isinstance(t, ast.Name) and t.id == "prefix" for t in st.targets):
                prefix = literal(st.value)
        for st in cls.body:
            # a identidade da tela (wait_loaded/is_current) também precisa existir no registro
            if isinstance(st, ast.Assign) and any(isinstance(t, ast.Name) and t.id == "identity" for t in st.targets):
                if prefix and literal(st.value):
                    used.add(f"{prefix}.{literal(st.value)}")
        for c in calls(cls):
            f = c.func
            name = f.attr if isinstance(f, ast.Attribute) else f.id if isinstance(f, ast.Name) else ""
            # chave relativa: self.k("x") ou um leitor da Page (1.3.0: self._texto("x", ...), self._data("x", ...))
            relative = name == "k" or (name.startswith("_") and name.lstrip("_").split("_")[0] in ("texto", "data"))
            if relative and c.args and literal(c.args[0]) is not None and prefix:
                used.add(f"{prefix}.{literal(c.args[0])}")
            if name in ("exists", "mark", "is_visible") and c.args and literal(c.args[0]) is not None:
                used.add(str(literal(c.args[0])))
            for kw in c.keywords:
                if kw.arg == "step_id" and literal(kw.value) is not None:
                    step_ids.add(str(literal(kw.value)))
            if name == "fill" and len(c.args) >= 2 and isinstance(c.args[1], (ast.Constant, ast.JoinedStr)):
                fills_ok = False  # valor escrito no código (o valor vem de parâmetro, da planilha ou do provedor de segredos)
    check(out, "KEYS_IN_REGISTRY", bool(used) and used <= keys, "KEY_NOT_IN_REGISTRY")
    # 1.3.0: o código usa traceIds legíveis; o manifesto mapeia cada um a UM StepId (bijetivo). Antes: o próprio StepId.
    trace = manifest.get("trace") or {}
    mappings = trace.get("mappings", []) if isinstance(trace, dict) else []
    to_step = {m["traceId"]: m["studioStepId"] for m in mappings}
    synthetic = {x.get("traceId") for x in trace.get("synthetic", [])} if isinstance(trace, dict) else set()
    bijective = len(to_step) == len(mappings) and len(set(to_step.values())) == len(to_step)
    code_steps = {to_step.get(t, t) for t in step_ids if t not in synthetic}
    manifest_steps = {s["stepId"] for s in manifest.get("steps", [])}
    targeted = {s["stepId"] for s in manifest.get("steps", []) if s.get("key")}
    unmapped = bool(mappings) and any(t not in to_step for t in step_ids if t not in synthetic)
    check(
        out,
        "STEP_IDS_TRACEABLE",
        bijective and not unmapped and code_steps <= manifest_steps and targeted <= code_steps,
        "STEP_ID_NOT_TRACEABLE",
    )
    check(out, "FILL_VALUES_NOT_LITERAL", fills_ok, "FILL_LITERAL_VALUE")

    sys.path.insert(0, str(root))
    try:
        processo = importlib.import_module("processo")
        from nekkora_rpa import RpaProcess

        # a classe do processo tem o nome de negócio (1.3.0) ou "Processo" (antes): a ÚNICA subclasse de RpaProcess
        candidates = [
            c for c in vars(processo).values()
            if isinstance(c, type) and issubclass(c, RpaProcess) and c is not RpaProcess and c.__module__ == "processo"
        ]
        proc_cls = candidates[0] if len(candidates) == 1 else None
        is_process = proc_cls is not None
        check(out, "PROCESS_IS_RPA_PROCESS", is_process, "PROCESS_NOT_RPA_PROCESS")
        declared = set(getattr(proc_cls, "required_secrets", ())) if proc_cls is not None else set()
        expected = sum(1 for s in manifest.get("steps", []) if s.get("credential"))
        check(out, "SECRETS_DECLARED", (expected == 0) == (not declared), "SECRETS_NOT_DECLARED")
    except Exception:  # noqa: BLE001 - importação recusada é falha do contrato
        check(out, "PROCESS_IS_RPA_PROCESS", False, "PROCESS_IMPORT_FAILED")
    finally:
        sys.path.remove(str(root))
    return out


def main() -> int:
    if len(sys.argv) != 2 or not Path(sys.argv[1]).is_dir():
        print(json.dumps({"error": "ARGS"}))
        return 2
    try:
        results = run(Path(sys.argv[1]))
    except Exception as exc:  # noqa: BLE001
        print(json.dumps({"error": "CONTRACT_RUN_FAILED", "type": type(exc).__name__}))
        return 3
    print(json.dumps({"checks": results, "total": len(results), "failed": sum(1 for r in results if not r["ok"])}))
    return 0


if __name__ == "__main__":
    sys.exit(main())
