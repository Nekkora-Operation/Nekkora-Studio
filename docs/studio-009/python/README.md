# Caso Python do laboratório (STUDIO-009, G9)

**O que é:** a prova de que o modelo homologado no debug guiado vira um script Python + Playwright executável. O teste é feito contra o laboratório local com dados fictícios, e o resultado é conferido por um oráculo independente.

**O que não é:**
- não é um gerador geral do Nekkora Studio;
- o gerador (`gerar.py`) foi escrito pelo Claude só para este caso.

| Arquivo | Papel |
|---|---|
| `gerar.py` | plano congelado (plano + registro da mesma `planRevision`) → script |
| `plano-congelado.json`, `registro-validacao.json` | entrada da geração final (depois do reparo) |
| `consulta_registro.v1.py` | script gerado antes da quebra |
| `consulta_registro.py` | script gerado do modelo reparado |
| `reparo.diff` | o que mudou entre os dois |
| `requirements.txt` | dependências fixadas |

## Preparar

```sh
python -m venv .venv
.venv/Scripts/python -m pip install -r requirements.txt   # Windows; em Linux: .venv/bin/python
```

O Playwright conecta por CDP ao Chrome de teste que já está aberto. Por isso, não é preciso baixar navegador do Playwright.

## Gerar

```sh
python gerar.py --plano plano-congelado.json --registro registro-validacao.json --saida consulta_registro.py --diff mudancas.diff
```

| Código de saída | Situação |
|---|---|
| 0 | gerado |
| 2 | bloqueado (lista de bloqueios no JSON da saída) |
| 3 | o script de saída foi editado à mão. Nada é sobrescrito; veja `.novo.py` e `.diff` |

## Executar

```sh
set NK_ENTRADA_REGISTRO_CODIGO=<código da chave lab:registro-ativo>   # catálogo fictício do laboratório (Studio009.java); nunca registrado
python consulta_registro.py --cdp http://127.0.0.1:<porta> --url-aba http://127.0.0.1:18765/lab/studio009/consulta.html?v=base \
       --debug depuracao --saida resultado.json
curl http://127.0.0.1:18765/api/studio009/estado   # ORÁCULO: o resultado de negócio é decidido aqui, não pelo código de saída
```

`--debug` gera, para cada etapa (`StepId`), o mesmo destaque do debug do Studio, a captura (campos mascarados) e `depuracao.jsonl`.

## Reparar

Quando uma etapa falha, o reparo é feito **no modelo** pelo debug guiado do Studio: Corrigir → selecionar no navegador → comparar → Aplicar. Depois:
1. valide de novo, numa nova passagem;
2. exporte plano e registro;
3. gere o script de novo.

Nunca edite o script para "consertar" o processo. O modelo `.nekkora` é a fonte da verdade.
