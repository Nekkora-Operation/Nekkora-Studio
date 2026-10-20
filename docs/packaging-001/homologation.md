# NEKKORA-PACKAGING-001 — Homologação

Data: 2026-10-20. Máquina: Windows 11 Pro 10.0.26200, tela 1920×1032, Java 21.0.12. Operador: Claude como
`TEST_AGENT` (`-Dnekkora.studio.decisionActor=TEST_AGENT`), mouse e teclado do sistema (`java.awt.Robot`), com
proteção de primeiro plano (a rodada para SEM clicar se outro programa estiver à frente).

## 1. Montagem (G5)

- **Studio real** (`StudioApplication`) com dados isolados (`NEKKORA_BRIDGE_DIR`, `NEKKORA_DATA_DIR`) numa área
  `C:\Users\<usuária>\Nekkora Homologação – pacote <rodada>` (espaço, acento e travessão no caminho).
- **Python do sistema** 3.12.10, SEM `nekkora_rpa` (conferido antes), `NEKKORA_PYTHON` e `VIRTUAL_ENV` ausentes,
  nenhum clone do framework no caminho de busca.
- **Dois projetos reais já homologados** (cópias, sem editar o Python gerado):
  `the-internet` (execução única 1.1.0 → `nekkora-rpa==0.2.0`) e `wikipedia-biografias`
  (lista → planilha 1.2.0 → `nekkora-rpa[excel]==0.3.0`).
- Harness: `nekkora-studio-app/src/test/java/com/nekkora/studio/app/evidence/Packaging001Homologation.java`
  (`<saída> <área> principal|rede`).
- Execução real em sistema-alvo: **não** feita (não autorizada nesta missão); V6 fica "nenhum teste autorizado".

## 2. Rodada principal (t6): 42 verificações, 0 falhas

Log: [evidence/g5/homologacao-log-t6.txt](evidence/g5/homologacao-log-t6.txt).

| Grupo | Verif. | Observado | Capturas |
|---|---|---|---|
| Pré | 2 | sem `NEKKORA_PYTHON`; Python do sistema sem `nekkora_rpa` | — |
| P1 projeto 0.2.0 | 14 | código exige exatamente `nekkora-rpa==0.2.0`; aviso "Ambiente Python da automação" com a versão exata; V4/V5 não aprovados sem ambiente; plano com pacote/versão, origem com SHA-256, Python base, destino e o que NÃO é enviado; **Cancelar a confirmação não cria venv**; preparar → progresso por etapa → `CODE_VALIDATED`; versão visível (`nekkora-rpa 0.2.0 (venv da automação)`); venv na pasta de dados do Studio; `ambiente.json` com requisito, origem homologada e hash do wheel; Validar automação V4/V5 sem falha | P1-01…P1-05 |
| P2 projeto 0.3.0[excel] | 16 | código exige `nekkora-rpa[excel]==0.3.0`; plano cita o extra excel; **Cancelar DURANTE a instalação** → `CANCELLED`, nenhum `ambiente.json`, projeto e código intactos; preparar de novo → `CODE_VALIDATED` (compilação, importação, ruff check/format, mypy estrito, testes de contrato); V2 "13 capacidades do nekkora_rpa 0.3.0"; Validar V4/V5 sem falha | P2-01…P2-04 |
| Compatibilidade | 1 | 0.2.0 e 0.3.0[excel] em venvs **separadas**; nenhuma migração silenciosa | — |
| N1 venv corrompida | 4 | `nekkora_rpa/xlsx.py` apagado da venv → "Ambiente danificado (arquivos instalados ausentes ou alterados…)", código NÃO dado por verificado; **Preparar ambiente** (mesmo fluxo) refaz e volta a `CODE_VALIDATED` | N1-01, N1-02 |
| N2 Python sem pacote | 1 | Python do sistema indicado em Opções avançadas → "Python recusado… Nada foi alterado" | N2-01 |
| N3 versão errada | 2 | venv 0.3.0 indicada para código 0.2.0 → diagnóstico "Versão diferente da exigida", sem troca silenciosa; código NÃO verificado | N3-01 |
| Pós | 2 | `pip freeze --all` do Python do sistema idêntico, sem `nekkora_rpa`; nenhuma venv dentro das pastas dos projetos | — |

## 3. Rodada sem rede (r1): 10 verificações, 0 falhas

`HTTPS_PROXY`/`HTTP_PROXY` apontando para uma porta fechada, só para o processo do Studio. Log:
[evidence/g5/homologacao-log-r1-sem-rede.txt](evidence/g5/homologacao-log-r1-sem-rede.txt).

O projeto, o PDD e o código abrem e são gerados; V4/V5 ficam "não verificado"; preparar o ambiente termina em
**PROXY_OR_SSL** ("Proxy ou certificado bloqueou o download… O projeto, o PDD e o código gerado continuam como
estavam"), nenhum ambiente é dado como pronto e a pendência continua com **Preparar ambiente…** (recuperável).
Python do sistema intocado. Captura: R1-01.

## 4. Defeitos encontrados pela homologação (todos corrigidos e re-homologados)

1. Diálogo do plano com estilo destrutivo e pequeno: título cortado, texto cobria "Cancelar" (→ D9).
2. V2 citava "capacidades do nekkora_rpa 0.3.0" para projeto 0.2.0: a validação passou a usar o catálogo da versão do
   código gerado.
3. Módulo apagado da venv passava como ambiente pronto (no Python 3.12, `Distribution.files` omite arquivos
   ausentes): o `RECORD` passou a ser conferido diretamente, com hash nos arquivos do framework.
4. Texto de status sem ambiente pronto ainda falava em "Python de verificação".
5. **Pré-existente (desde STUDIO-014, também na `main`)**: o botão **"Python para verificação…"** (Opções avançadas)
   não abria nada — `FileNameExtensionFilter` recusa a extensão vazia passada junto com `exe` e a exceção morria na
   EDT. Corrigido em `SwingDevelopmentHost.choosePython` (filtro `*.exe` só no Windows; sem filtro nos demais).

Rodadas t1–t5 (descartadas como evidência): t1/t2 revelaram 1–4; t3 parou na proteção de primeiro plano (outro
programa à frente; nada clicado); t4 parou num defeito do harness (Opções avançadas virou aba); t5 revelou o 5.
Entre t3 e t4 a máquina desligou sem aviso; a suíte foi refeita depois do reinício.

## 5. Testes automatizados

| Plataforma | Resultado |
|---|---|
| Windows (`./mvnw -o verify`) | **713 testes, 0 falhas, 0 pulados** (16 módulos; app repetido após a correção 5: 179 + 3). `SupervisedRunTest.executaEConfere` falhou UMA vez (`INIT_FAILED`) na primeira suíte logo após o reinício da máquina e passou nas três execuções seguintes (isolado, módulo inteiro, suíte retomada) — registrado como instabilidade de arranque, não reproduzida |
| Linux (contêiner, JDK fixado por digest, `tools/golden-linux.sh verify`) | ver seção 6 |
| `DependencyManagerTest` (Python e PyPI reais) | 7/7, 0 pulados: venvs separadas em caminho com acento/travessão, reuso, VERSION_MISMATCH, venv danificada, cancelamento, índice adversarial → HASH_MISMATCH, rede fora, disco, caminho longo |
| Framework (PR #5, CI GitHub) | qualidade + distribuição, Windows e Linux, 4 jobs verdes (run `39489164509`) |

## 6. Linux

Pendente de registro nesta seção até a execução no contêiner sobre o commit deste PR.
