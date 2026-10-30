# GERADO pelo Nekkora Studio (STUDIO-014, "Desenvolver automação") do modelo validado e do PDD pronto.
# NÃO EDITE: correções vão para o modelo ou o PDD e o código é gerado de novo.
# Arquivo editado à mão não é sobrescrito.
"""Processo gerado (execução única): INIT abre o início; PROCESS faz as etapas na ordem do modelo; END fecha."""

from __future__ import annotations

from collections.abc import Mapping
from dataclasses import dataclass

from nekkora_rpa import ProcessResult, RpaProcess, Runtime, TransactionItem
from nekkora_rpa.errors import BusinessRuleRejected, InvalidConfiguration
from nekkora_rpa.security.secrets import SecretProvider

from paginas import TelaAcesso, TelaConsulta

URL_INICIAL = "http://127.0.0.1:18765/lab/studio014/portal.html"
ENTRADAS = ("codigo_do_registro",)


@dataclass(frozen=True, repr=False)
class Entrada:
    """Entrada da execução (os valores nunca são registrados)."""

    codigo_do_registro: str

    @classmethod
    def de(cls, dados: Mapping[str, object]) -> Entrada:
        valores: dict[str, str] = {}
        for nome in ENTRADAS:
            valor = dados.get(nome)
            if not isinstance(valor, str) or not valor.strip():
                raise BusinessRuleRejected("ENTRADA_INVALIDA")
            valores[nome] = valor
        return cls(**valores)


def _segredos(rt: Runtime) -> SecretProvider:
    if rt.secrets is None:
        raise InvalidConfiguration(detail="sem_provedor_de_segredos")
    return rt.secrets


class Processo(RpaProcess):
    name = "consulta_de_registro_sintetica"
    requires_browser = True
    required_secrets = ("credencial.senha", "credencial.usuario")

    def init(self, rt: Runtime) -> None:
        rt.act.navigate(URL_INICIAL, step_id="INICIO")

    def process(self, rt: Runtime, item: TransactionItem) -> ProcessResult:
        entrada = Entrada.de(item.data)  # antes de qualquer ação: entrada inválida recusa o item
        segredos = _segredos(rt)
        saidas: dict[str, str] = {}
        TelaAcesso(rt.act).acesso(segredos)
        saidas.update(TelaConsulta(rt.act).consulta(entrada.codigo_do_registro))
        return ProcessResult(saidas)
