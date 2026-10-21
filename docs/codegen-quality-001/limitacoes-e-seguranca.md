# NEKKORA-CODEGEN-QUALITY-001 — Limitações conhecidas e segurança

## Limitações (declaradas, não escondidas)

1. **Nomes dependem do PDD revisado.** Sem título REVISADO de seção, a Page fica `Pagina<N>` (provisória) e a camada VQ
   aponta `NOME_SEM_EVIDENCIA`; sem título de passo, o método é nomeado pelo que faz (`ler_dados`, `clicar_sair`,
   `preencher_e_ler_…`). A heurística de português (1ª palavra de conteúdo; verbo no infinitivo) é simples e testada,
   não é análise linguística.
2. **Variáveis antigas mantêm o nome.** Projetos gravados antes desta missão podem ter variáveis como `campo_s`
   (derivadas antes da regra do marcador de plural): o código usa o nome da variável do modelo; a VQ não renomeia nada.
   Renomeie em Variáveis e dependências e desenvolva de novo.
3. **Âncora de região para linhas de tabela.** O localizador de linha (`//tr[th[…]]//td`) não é restrito a uma tabela
   específica: com dois quadros com o mesmo rótulo, o framework recusa (`TARGET_AMBIGUOUS`, testado) em vez de escolher.
   Restringir à região exigiria a extensão gravar a região da tabela (fora do escopo; nenhuma mudança na extensão).
4. **Gerador tabular 1.0.0** (configuração declarada, Automação → Avançado) não recebeu nomes/traceId (fora do escopo;
   byte a byte igual); a camada VQ o marca como `NOT_CHECKED`.
5. **Registro de rastreio fica nos dados do Studio** (como a especificação): em outra máquina, o `trace` do manifesto
   anterior da pasta de saída é a semente. Sem nenhum dos dois, o traceId é derivado de novo (pode mudar se o PDD mudou).
6. **Coluna opcional na planilha (CQ-06, fora do escopo — limitação real).** Toda coluna da planilha é obrigatória:
   `morte: str` e `_data("morte")` recusam a ficha de uma pessoa viva (`DATA_INCOMPLETA_OU_AMBIGUA`/`DADO_OBRIGATORIO_VAZIO`).
   É o contrato do PDD atual (preservado), não um defeito de legibilidade. O framework 0.3.0 já aceita coluna não
   obrigatória (`OutputColumn(required=False)`); falta o CONTRATO da especificação: a pessoa declarar "esta coluna pode
   ficar vazia" (pergunta e registro no Studio, fingerprint da decisão), o gerador ler com `_data_se_existir` e excluir
   a coluna da recusa por vazio, e testes de ausência real × ambíguo × data incompleta. Proposta de missão posterior:
   **NEKKORA-SPEC-OPTIONAL-COLUMNS-001**. Nada foi inventado: sem a decisão, ausência continua recusando o item.
7. **Execução real** (V6) só pelo teste autorizado pela interface, na origem autorizada; nada é dado por executado por
   inspeção estática.
8. **Glossário PT→EN (rodada 3) cobre o vocabulário dos processos conhecidos, não o idioma inteiro.** São 271 entradas
   governadas (telas, dados, pessoas, documentos, verbos de ação) e regras fixas de ordem; não há análise linguística.
   Palavra fora dele fica como está no nome (ASCII) e é apontada (`IDENTIFICADOR_SEM_TRADUCAO`); a correção é usar no
   PDD/variável uma palavra do glossário ou incluí-la no glossário (nova versão do gerador). Traduções literais
   (`birth`, `death` para "Nascimento", "Morte") são escolhas do glossário, revisáveis pela titular.
9. **Emissor tabular 1.0.0 continua em português** (D-CQ-33): lote declarado da E2E-001, fora do escopo desde a rodada 1.
10. **Framework sem mudança.** O runtime 0.2.0/0.3.0 continua lendo só o `selectors.json`; `automation/selectors.py` é o
    registro revisável e a equivalência é conferida pelo Studio. Uma versão futura do framework poderá importar o módulo
    Python diretamente — em missão própria, com versão, catálogo, lock e hashes novos.

## Segurança e privacidade

- Nenhum valor de negócio no código: entradas por parâmetro/lista, segredos pelo provedor (`NK_SECRET_*`), leituras só
  em variáveis do processo. Docstrings usam títulos REVISADOS do PDD (já filtrados pela política de texto) e rótulos de
  interface que já estavam no registro de alvos — nunca texto de página.
- O UUID das etapas sai do corpo do código mas fica no manifesto e no `selectors.json` (`provenance.studioStepId`): a
  correlação com gravação, debug, PDD e execução continua exata (mapa bijetivo testado).
- Nenhum canal Studio ↔ Platform; nenhuma mudança no Agent, no framework ou na extensão; nenhuma publicação.
- D13 (`FRAMEWORK_CATALOG_MISSING`) continua soberano: a VQ fica `NOT_CHECKED` e nada é dado por validado.
- Repetição depois de resultado incerto (rodada 2, CQ-04): clique de login e de saída nunca são repetidos, mesmo com
  "sem efeito" declarado; a decisão fica em `manifest.steps[].retry` e na docstring do método.
- A proveniência do teste autorizado é conferida pela estrutura (CQ-09): nenhuma folha fora dos conjuntos permitidos e
  nenhum valor-sentinela; o diagnóstico de falha cita só caminho e regra.
- A análise AST roda no mesmo intérprete isolado de V4/V5 (`-I -B`, sem rede, sem executar o projeto) e só devolve
  códigos e identificadores gerados.
- Rodada 3: `automation/selectors.py` só tem dados (literais), nunca é executado pelo runtime e não leva UUID; o contrato
  V5 o lê pela AST e pelo import isolado. Nenhum módulo do projeto encobre a biblioteca padrão (checado). O glossário é
  um recurso do gerador: nada é enviado a serviço externo para nomear código.
