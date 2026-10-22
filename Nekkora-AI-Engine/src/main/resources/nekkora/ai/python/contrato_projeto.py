"""Testes de CONTRATO do projeto gerado (STUDIO-014, camada V5) — sem navegador, sem rede, sem executar o processo.

Uso pelo Studio: ``python -I -B contrato_projeto.py <pasta-do-projeto>``. Responde um JSON com cada verificação
(id, ok, código do motivo). Nenhum conteúdo de arquivo, valor ou caminho do usuário vai para a saída.

Verifica: hashes do manifesto; registro de alvos válido para o nekkora_rpa instalado; toda chave usada nas Pages existe
no registro; todo StepId do código está no manifesto (e vice-versa para etapas com alvo); preenchimentos só recebem
parâmetro/variável ou segredo do provedor (nunca literal); o processo é um RpaProcess e declara os segredos que usa.

Dois layouts (CODEGEN-QUALITY-001 rodada 3): o pacote ``automation/`` (código em inglês, chaves tipadas
``SelectorKey.<NOME>.value``) e ``paginas.py``/``processo.py`` na raiz (projetos anteriores, que continuam valendo). No
pacote, também: nenhuma chave de alvo escrita como texto nas telas; ``automation/selectors.py`` igual ao
``selectors.json`` (mesmos alvos, mesmas chaves, mesma equivalência declarada no manifesto); cada ``SelectorKey`` mapeado
no manifesto para suas etapas; e nenhum módulo do projeto encobre a biblioteca padrão (``selectors``, ``subprocess``,
``asyncio`` continuam vindo dela com o projeto no ``sys.path``).

Com planilha de saída (SPEC-OPTIONAL-COLUMNS-001): manifesto, ``main.SPREADSHEET`` e ``REQUIRED_FIELDS`` do processo
concordam sobre cada coluna (obrigatória × opcional, cabeçalhos na ordem, chave obrigatória).
"""

from __future__ import annotations

import ast
import hashlib
import importlib
import importlib.util
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


def canonical(value: Any) -> str:
    return json.dumps(value, sort_keys=True, ensure_ascii=False, separators=(",", ":"))


def selector_members(tree: ast.Module) -> dict[str, str]:
    """Membros de ``class SelectorKey(StrEnum)`` → chave (só literais; nada é executado)."""
    out: dict[str, str] = {}
    for node in tree.body:
        if isinstance(node, ast.ClassDef) and node.name == "SelectorKey":
            for st in node.body:
                if isinstance(st, ast.Assign) and len(st.targets) == 1 and isinstance(st.targets[0], ast.Name):
                    value = literal(st.value)
                    if value is not None:
                        out[st.targets[0].id] = value
    return out


def key_member(node: ast.AST) -> str | None:
    """``SelectorKey.X.value`` (ou ``SelectorKey.X``) → ``X``."""
    if isinstance(node, ast.Attribute) and node.attr == "value":
        node = node.value
    if isinstance(node, ast.Attribute) and isinstance(node.value, ast.Name) and node.value.id == "SelectorKey":
        return node.attr
    return None


def stdlib_not_shadowed(root: Path) -> bool:
    """Com o projeto no ``sys.path``, os módulos da biblioteca padrão continuam vindo dela (nada do projeto os encobre)."""
    names = set(getattr(sys, "stdlib_module_names", ()))
    top = {p.stem for p in root.glob("*.py")} | {p.name for p in root.iterdir() if p.is_dir() and (p / "__init__.py").is_file()}
    if top & names:
        return False
    sys.path.insert(0, str(root))
    try:
        for name in ("selectors", "subprocess", "asyncio"):
            spec = importlib.util.find_spec(name)
            origin = Path(spec.origin).resolve() if spec is not None and spec.origin else None
            if origin is None or root.resolve() in origin.parents:
                return False
    finally:
        sys.path.remove(str(root))
    return True


def output_requirement(output: dict[str, Any], processo: Any) -> tuple[bool, str]:
    """Planilha de saída (SPEC-OPTIONAL-COLUMNS-001): o manifesto, o ``main.SPREADSHEET`` e o ``REQUIRED_FIELDS`` do
    processo dizem o MESMO sobre cada coluna — cabeçalhos na mesma ordem, opcional só onde o manifesto declara (sem
    declaração = obrigatória), a chave obrigatória, e a montagem da linha exigindo exatamente as obrigatórias."""
    try:
        main_module = importlib.import_module("main")
        schema = main_module.SPREADSHEET
        declared = [c for c in output.get("columns", []) if isinstance(c, dict)]
        required = {str(c.get("field")): c.get("requirement", "REQUIRED") == "REQUIRED" for c in declared}
        in_code = {c.field: c.required for c in schema.columns}
        headers = [c.header for c in schema.columns] == [c.get("header") for c in declared]
        expected = tuple(f for f, r in required.items() if r)
        listed = getattr(processo, "REQUIRED_FIELDS", None) if processo is not None else None
        # todas obrigatórias: a linha recusa qualquer coluna vazia (sem REQUIRED_FIELDS); com opcional: só as da tupla
        row = listed is None if all(required.values()) else listed is not None and tuple(listed) == expected
        key = in_code.get(schema.key_field) is True and required.get(schema.key_field) is True
        ok = headers and required == in_code and row and key
        return ok, "" if ok else "OUTPUT_REQUIREMENT_MISMATCH"
    except Exception:  # noqa: BLE001 - esquema ausente ou recusado é falha do contrato
        return False, "OUTPUT_SCHEMA_INVALID"


def run(root: Path) -> list[dict[str, Any]]:
    out: list[dict[str, Any]] = []
    manifest = json.loads((root / "manifest.json").read_text(encoding="utf-8"))
    files: dict[str, str] = manifest.get("files", {})
    bad = [f for f, h in files.items() if "sha256:" + hashlib.sha256((root / f).read_bytes()).hexdigest() != h]
    check(out, "MANIFEST_HASHES", not bad and bool(files), "MANIFEST_HASH_MISMATCH")
    package = (root / "automation" / "__init__.py").is_file()
    pages_file = root / "automation" / "pages.py" if package else root / "paginas.py"
    config_file = root / "config" / ("execution.toml" if (root / "config" / "execution.toml").is_file() else "execucao.toml")

    from nekkora_rpa.selectors.registry import SelectorRegistry

    env = "lab"
    toml = config_file.read_text(encoding="utf-8")
    if 'environment = "benchmark"' in toml:
        env = "benchmark"
    try:
        registry = SelectorRegistry.load(root / "selectors.json", environment=env)
        keys = set(registry.keys())
        check(out, "SELECTORS_VALID", True)
    except Exception:  # noqa: BLE001 - qualquer recusa do framework é falha do contrato
        keys = set()
        check(out, "SELECTORS_VALID", False, "SELECTORS_REJECTED_BY_FRAMEWORK")

    members: dict[str, str] = {}
    if package:
        members = selector_members(ast.parse((root / "automation" / "selectors.py").read_text(encoding="utf-8")))
    pages = ast.parse(pages_file.read_text(encoding="utf-8"))
    used: set[str] = set()
    step_ids: set[str] = set()
    fills_ok = True
    typed_ok = True
    system = str(json.loads((root / "selectors.json").read_text(encoding="utf-8")).get("system", ""))
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
            if isinstance(st, ast.Assign) and any(isinstance(t, ast.Name) and t.id == "identity_key" for t in st.targets):
                member = key_member(st.value)
                used.add(members.get(member, f"<membro desconhecido {member}>") if member else "<identidade sem SelectorKey>")
        for node in ast.walk(cls):
            member = key_member(node) if isinstance(node, ast.Attribute) else None
            if member is not None and package:
                used.add(members.get(member, f"<membro desconhecido {member}>"))
            # no pacote, nenhuma chave de alvo escrita como texto (nem em pós-condição): só SelectorKey.<NOME>.value
            text = literal(node) if isinstance(node, ast.Constant) else None
            if package and text is not None and system and text.startswith(system + "."):
                typed_ok = False
        for c in calls(cls):
            f = c.func
            name = f.attr if isinstance(f, ast.Attribute) else f.id if isinstance(f, ast.Name) else ""
            # chave relativa (layout anterior): self.k("x"), self.elemento("x") ou um leitor da Page (self._texto("x", ...))
            relative = name in ("k", "elemento") or (name.startswith("_") and name.lstrip("_").split("_")[0] in ("texto", "data"))
            if not package and relative and c.args and literal(c.args[0]) is not None and prefix:
                used.add(f"{prefix}.{literal(c.args[0])}")
            if not package and name in ("exists", "mark", "is_visible") and c.args and literal(c.args[0]) is not None:
                used.add(str(literal(c.args[0])))
            for kw in c.keywords:
                if kw.arg == "step_id" and literal(kw.value) is not None:
                    step_ids.add(str(literal(kw.value)))
            if name == "fill" and len(c.args) >= 2 and isinstance(c.args[1], (ast.Constant, ast.JoinedStr)):
                fills_ok = False  # valor escrito no código (o valor vem de parâmetro, da planilha ou do provedor de segredos)
    check(out, "KEYS_IN_REGISTRY", bool(used) and used <= keys, "KEY_NOT_IN_REGISTRY")
    # 1.3.0+: o código usa traceIds legíveis; o manifesto mapeia cada um a UM StepId (bijetivo). Antes: o próprio StepId.
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

    if package:
        check(out, "SELECTOR_KEYS_TYPED", typed_ok, "LITERAL_TARGET_KEY_IN_PAGES")
        check(out, "STDLIB_NOT_SHADOWED", stdlib_not_shadowed(root), "PROJECT_SHADOWS_STDLIB")

    sys.path.insert(0, str(root))
    try:
        module = "automation.process" if package else "processo"
        processo = importlib.import_module(module)
        from nekkora_rpa import RpaProcess

        # a classe do processo tem o nome de negócio: a ÚNICA subclasse de RpaProcess do módulo do processo
        candidates = [
            c for c in vars(processo).values()
            if isinstance(c, type) and issubclass(c, RpaProcess) and c is not RpaProcess and c.__module__ == module
        ]
        proc_cls = candidates[0] if len(candidates) == 1 else None
        is_process = proc_cls is not None
        check(out, "PROCESS_IS_RPA_PROCESS", is_process, "PROCESS_NOT_RPA_PROCESS")
        declared = set(getattr(proc_cls, "required_secrets", ())) if proc_cls is not None else set()
        expected = sum(1 for s in manifest.get("steps", []) if s.get("credential"))
        check(out, "SECRETS_DECLARED", (expected == 0) == (not declared), "SECRETS_NOT_DECLARED")
    except Exception:  # noqa: BLE001 - importação recusada é falha do contrato
        check(out, "PROCESS_IS_RPA_PROCESS", False, "PROCESS_IMPORT_FAILED")
        processo = None
    output = manifest.get("output") if isinstance(manifest.get("output"), dict) else {}
    if package and output.get("format") == "xlsx":
        check(out, "OUTPUT_REQUIREMENT_CONSISTENT", *output_requirement(output, processo))
    if package:
        try:
            selectors_py = importlib.import_module("automation.selectors")
            json_targets = json.loads((root / "selectors.json").read_text(encoding="utf-8"))["targets"]
            clean = {k: {f: v for f, v in t.items() if f != "provenance"} for k, t in json_targets.items()}
            py_targets = dict(selectors_py.TARGETS)
            enum_keys = {m.name: m.value for m in selectors_py.SelectorKey}
            declared_eq = (manifest.get("selectors") or {}).get("equivalence")
            same = (
                canonical(py_targets) == canonical(clean)
                and set(enum_keys.values()) == set(json_targets)
                and enum_keys == members
                and declared_eq == "sha256:" + hashlib.sha256(canonical(clean).encode("utf-8")).hexdigest()
            )
            check(out, "SELECTORS_PY_JSON_EQUAL", same, "SELECTORS_PY_JSON_DIFFER")
            listed = {k.get("member"): k for k in (manifest.get("selectors") or {}).get("keys", [])}
            mapped = set(listed) == set(enum_keys) and all(
                listed[m].get("key") == enum_keys[m]
                and listed[m].get("traceIds")
                and all(t in to_step and to_step[t] in listed[m].get("studioStepIds", []) for t in listed[m]["traceIds"])
                and (json_targets[enum_keys[m]].get("provenance", {}).get("studioStepId") in (None, *listed[m].get("studioStepIds", [])))
                for m in enum_keys
            )
            check(out, "SELECTOR_KEYS_MAPPED", mapped, "SELECTOR_KEY_NOT_MAPPED")
        except Exception:  # noqa: BLE001 - módulo de alvos recusado é falha do contrato
            check(out, "SELECTORS_PY_JSON_EQUAL", False, "SELECTORS_MODULE_INVALID")
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
