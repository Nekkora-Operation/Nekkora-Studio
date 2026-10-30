"""Equivalência de comportamento entre dois projetos gerados (NEKKORA-CODEGEN-QUALITY-001, teste).

Uso: python -I -B equivalencia.py <pasta-do-projeto> <cenário.json>
Importa o processo do projeto (``automation/process.py`` no código em inglês da rodada 3; ``processo.py`` antes) com um
``Actions`` FALSO (nada de navegador, nada de rede) e roda UMA transação. A linha devolvida é comparada pelo CABEÇALHO
da planilha (o contrato; o nome interno do campo mudou para inglês), via ``output.columns`` do manifesto. Cada chamada ao framework vira ``[ação, índice da etapa no modelo]`` — o índice sai do PRÓPRIO manifesto
(``step_id`` = StepId no gerador antigo; traceId → StepId pelo ``trace`` no novo), então dois geradores diferentes
podem ser comparados chamada a chamada. O cenário diz o que cada leitura devolve (pelo índice da etapa) e quais alvos
não existem. Saída: JSON com as chamadas e o desfecho (linha devolvida ou recusa).
"""

from __future__ import annotations

import importlib
import json
import sys
import types
from pathlib import Path
from typing import Any


def main() -> int:
    root = Path(sys.argv[1]).resolve()
    scenario = json.loads(Path(sys.argv[2]).read_text(encoding="utf-8"))
    manifest = json.loads((root / "manifest.json").read_text(encoding="utf-8"))
    steps = manifest["steps"]
    index_of_step = {s["stepId"]: i for i, s in enumerate(steps)}
    trace = manifest.get("trace") or {}
    step_of_trace = {m["traceId"]: m["studioStepId"] for m in trace.get("mappings", [])}
    index_of_key: dict[str, int] = {}
    for i, s in enumerate(steps):
        if s.get("key") and s["key"] not in index_of_key:
            index_of_key[s["key"]] = i
    targets = json.loads((root / "selectors.json").read_text(encoding="utf-8"))["targets"]
    values = {int(k): v for k, v in scenario["values"].items()}
    absent = set(scenario.get("absent", []))
    # SPEC-OPTIONAL-COLUMNS-001: alvo com mais de um elemento — o framework recusa (nunca "não existe")
    ambiguous = set(scenario.get("ambiguous", []))
    calls: list[list[Any]] = []

    def idx(step_id: str | None) -> Any:
        if step_id is None:
            return None
        sid = step_of_trace.get(step_id, step_id)
        return index_of_step.get(sid, step_id)

    def key_index(key: str) -> int:
        return index_of_key[key]

    def locator(key: str) -> str:
        """O ELEMENTO usado (localizador preferido do registro): compara chaves diferentes para o mesmo alvo."""
        preferred = targets[key]["preferred"]
        return str(preferred.get("expression") or preferred)

    class Fake:
        def fill(self, key: str, value: str, *, step_id: str | None = None, **_: Any) -> None:
            calls.append(["fill", idx(step_id), locator(key)])

        def press(self, key: str, name: str, *, step_id: str | None = None, postcondition: Any = None, **_: Any) -> None:
            calls.append(["press", idx(step_id), locator(key)])
            if postcondition is not None:
                scenario["navigated"] = True
                assert postcondition()

        def click(self, key: str, *, step_id: str | None = None, postcondition: Any = None, **_: Any) -> None:
            calls.append(["click", idx(step_id), locator(key)])
            if postcondition is not None:
                assert postcondition()

        def current_address(self, *, step_id: str | None = None) -> str:
            if step_id is not None:
                calls.append(["current_address", idx(step_id)])
            return str(scenario["address"] if scenario.get("navigated", True) else "about:blank")

        def wait_visible(self, key: str, *, step_id: str | None = None, **_: Any) -> None:
            calls.append(["wait_visible", idx(step_id), locator(key)])

        def exists(self, key: str, *, step_id: str | None = None) -> bool:
            calls.append(["exists", idx(step_id), locator(key)])
            if key_index(key) in ambiguous:
                raise RuntimeError("TARGET_AMBIGUOUS")
            return key_index(key) not in absent

        def read_items(self, key: str, *, step_id: str | None = None, **_: Any) -> tuple[str, ...]:
            calls.append(["read_items", idx(step_id), locator(key)])
            if key_index(key) in ambiguous:
                raise RuntimeError("TARGET_AMBIGUOUS")
            if key_index(key) in absent:
                raise RuntimeError("TARGET_NOT_FOUND")
            # como o framework 0.3.0: cada item normalizado (espaços colapsados) e itens vazios fora
            return tuple(i for i in (" ".join(str(x).split()) for x in values.get(key_index(key), [])) if i)

        def read_text(self, key: str, *, step_id: str | None = None, **_: Any) -> str:
            calls.append(["read_text", idx(step_id), locator(key)])
            return "; ".join(values.get(key_index(key), []))

        def navigate(self, url: str, *, step_id: str | None = None, **_: Any) -> None:
            # abertura sintética do endereço inicial: "INICIO" (rodada 1), "processo.abrir_pagina_inicial" (rodada 2) e
            # "process.open_start_page" (rodada 3) são a mesma
            start = step_id in ("INICIO", "processo.abrir_pagina_inicial", "process.open_start_page")
            calls.append(["navigate", "INICIO" if start else idx(step_id)])

    sys.path.insert(0, str(root))
    module = "automation.process" if (root / "automation" / "__init__.py").is_file() else "processo"
    processo = importlib.import_module(module)
    from nekkora_rpa import RpaProcess, TransactionItem
    from nekkora_rpa.errors import BusinessRuleRejected

    cls = next(c for c in vars(processo).values() if isinstance(c, type) and issubclass(c, RpaProcess) and c is not RpaProcess
               and c.__module__ == module)
    header_of = {c["field"]: c["header"] for c in (manifest.get("output") or {}).get("columns", [])}
    rt = types.SimpleNamespace(act=Fake(), secrets=None)
    outcome: dict[str, Any]
    try:
        result = cls().process(rt, TransactionItem("x", scenario["input"]))  # type: ignore[arg-type]
        outcome = {"status": "SUCCEEDED", "row": {header_of.get(k, k): v for k, v in dict(result.output).items()}}
    except BusinessRuleRejected as err:
        field = err.reason.split(":", 1)[1] if err.reason.startswith("DADO_OBRIGATORIO_VAZIO:") else None
        outcome = {"status": "BUSINESS_REJECTED", "reason": err.reason, "column": header_of.get(field) if field else None}
    except RuntimeError as err:
        outcome = {"status": "TECHNICAL", "detail": str(err)}
    print(json.dumps({"calls": calls, "outcome": outcome}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())
