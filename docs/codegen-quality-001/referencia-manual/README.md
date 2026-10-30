# REFERENCIA_MANUAL_VALIDACAO — exceção ÚNICA da NEKKORA-CODEGEN-QUALITY-001

**Isto NÃO é código gerado pelo Studio.** É a única implementação Python escrita à mão autorizada pela titular, nesta
missão, para comparar organização e comportamento com o código que o Studio gera para o caso das biografias (E2E-002),
na **réplica sintética** do laboratório do framework (`lab/static/leitura/`, pessoas fictícias, `127.0.0.1:18766`).

- Usa só APIs da versão homologada do `nekkora_rpa` (0.3.0).
- Não é lida, copiada nem usada como modelo pelo gerador (há teste que confere que o gerador não referencia esta pasta).
- Não é entregue como automação, não é publicada como biblioteca e não vale como prova de geração.
- A permissão especial ENCERRA com esta missão: depois dela, só o Studio gera projetos finais.

## Arquivos e hashes (SHA-256)

Ver `hashes.txt` (gerado no fechamento do G2).

## Resultado no laboratório (G2)

| Execução | Resultado |
|---|---|
| 3 fichas (Ana, Fábio, Gina) | `COMPLETED`, 3 `SUCCEEDED`, planilha gravada; oráculo independente 18/18 células, 0 fórmulas |
| busca leva a outra ficha | `COMPLETED_WITH_FAILURES`, `BUSINESS_REJECTED` (`ENDERECO_DIFERENTE_DO_ESPERADO`), planilha NÃO gravada |

## O que a referência ensinou ao gerador (hipóteses testadas)

| Hipótese | Conclusão aplicada no GERADOR (Java), não neste código |
|---|---|
| Uma Page por tela real (pesquisa × ficha) | divisão por troca de documento registrada (`DOCUMENT_CHANGED`) |
| Leituras da ficha num método que devolve um `dataclass` com campos opcionais `str \| None` | `CodePlan.Model` + `_texto_se_existir` |
| Helpers privados de leitura na Page (`_texto`, `_texto_se_existir`, `_data`) | emitidos só quando usados |
| `step_id` legível `<tela>.<ação>_<objeto>` | traceId + registro de rastreio |
| Linha da planilha num dicionário explícito com `or` para a fonte alternativa | `linha_da_planilha(...)` |
| Data inválida = recusa com motivo próprio e `step_id` | `DATA_INCOMPLETA_OU_AMBIGUA` |
