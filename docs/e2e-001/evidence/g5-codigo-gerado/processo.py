# GERADO pelo Nekkora Studio (STUDIO-011) a partir do modelo validado. NÃO EDITE: correções vão para o
# modelo (debug guiado), e o código é gerado de novo. Arquivo editado à mão não é sobrescrito.
"""Processo gerado: INIT (sessões de inicialização) → uma transação por linha da planilha → END (conclusão)."""

from __future__ import annotations

import json
from pathlib import Path

from paginas import Sessao1, Sessao2, Sessao3

from nekkora_rpa import ProcessResult, RpaProcess, Runtime, TransactionItem
from nekkora_rpa.errors import BusinessRuleRejected
from nekkora_rpa.tabular import PROBLEMS_KEY

URL_INICIAL = "https://rpachallenge.com/"


class Processo(RpaProcess):
    name = "rpa_challenge_input_forms"
    requires_browser = True
    required_secrets = ()

    def __init__(self, saida: Path) -> None:
        self.saida = saida
        self.entrada: Path | None = None
        self.entrada_sha256: str | None = None
        self.observacoes: dict[str, str] = {}

    def init(self, rt: Runtime) -> None:
        rt.act.navigate(URL_INICIAL, step_id="INICIO")
        destino = self.saida / "entrada" / rt.ctx.execution_id
        destino.mkdir(parents=True, exist_ok=True)
        baixado, obs = Sessao1(rt.act).executar(destino, rt.secrets)
        self.observacoes.update(obs)
        if baixado is not None:
            self.entrada, self.entrada_sha256 = baixado.path, baixado.sha256

    def process(self, rt: Runtime, item: TransactionItem) -> ProcessResult:
        if item.data.get(PROBLEMS_KEY):
            raise BusinessRuleRejected("TABULAR_ROW_INVALID")  # antes de qualquer ação
        obs = Sessao2(rt.act).executar(item.data, rt.secrets)
        return ProcessResult({"enviado": True, **({"observacoes": obs} if obs else {})})

    def end(self, rt: Runtime) -> None:
        self.observacoes.update(Sessao3(rt.act).executar(rt.secrets))

    def gravar_observacoes(self) -> None:
        """Observações lidas da página (etapas OBSERVE) + hash da planilha baixada; nunca valores de entrada."""
        doc = {"entrada_sha256": self.entrada_sha256, "observacoes": self.observacoes}
        (self.saida / "observacoes.json").write_text(json.dumps(doc, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
