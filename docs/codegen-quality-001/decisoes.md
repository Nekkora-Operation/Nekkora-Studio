# NEKKORA-CODEGEN-QUALITY-001 — Decisões técnicas (do agente; reversíveis)

| # | Decisão | Motivo |
|---|---|---|
| D-CQ-01 | Nomes de Page/método vêm de títulos **REVISADOS** do PDD (texto derivado automaticamente não conta) e de variáveis do modelo; sem evidência → nome provisório + `NOME_SEM_EVIDENCIA` | "não fantasiar"; o PDD revisado é o vocabulário aprovado |
| D-CQ-02 | Tela nova também quando a etapa anterior registrou troca de documento/rota, mesmo com o mesmo modelo de endereço; sessão nova sozinha não cria tela | a gravação PROVA a troca (E2E-002: `DOCUMENT_CHANGED newDocument=true`); sites de página única continuam com uma Page |
| D-CQ-03 | `step_id` = traceId legível `<tela>.<ação>_<objeto>`; mapa traceId ↔ StepId no manifesto 1.3.0; registro de rastreio por automação fixa o traceId depois da 1ª geração; aposentados nunca reaproveitados | legibilidade sem perder a correlação; o framework aceita o token; checkpoints não guardam `step_id` |
| D-CQ-04 | IR `CodePlan` montado antes da emissão; emissores só traduzem | arquitetura revisável e testável; o manifesto descreve o mesmo plano |
| D-CQ-05 | Métodos que só LEEM/CONFEREM são nomeados pelo que leem (`conferir_endereco_da_pagina`, `ler_dados`); os que AGEM usam verbo + objeto do título do bloco | o título do bloco pode descrever a seção inteira; o nome não pode prometer o que o método não faz |
| D-CQ-06 | Leitura opcional devolve `None` só para alvo AUSENTE (ambíguo e identidade divergente continuam erro do framework); data incompleta/ambígua → `DATA_INCOMPLETA_OU_AMBIGUA` com `step_id`; coluna vazia → `DADO_OBRIGATORIO_VAZIO`; conferência → `ENDERECO_DIFERENTE_DO_ESPERADO` | separar ausência prevista, não localizado, ambíguo, vazio e parse inválido (antes tudo virava `""`) |
| D-CQ-07 | Dataclass só para método que lê 2+ dados; helpers de leitura privados na Page só quando usados | sem árvore de diretórios nem abstração decorativa |
| D-CQ-08 | Mesmo elemento (mesma identidade e localizador) na mesma tela = mesma chave do registro (preencher e Enter no campo de pesquisa) | uma chave por elemento, não por ação |
| D-CQ-09 | Localizador com posição disfarçada (`nth-*`, XPath absoluto, índice fora de eixo de irmão, `position()`, `last()`) é recusado na geração (`LOCATOR_POSITIONAL`), também no gerador tabular legado | Q03; a saída do tabular só muda se antes geraria código posicional |
| D-CQ-10 | Camada nova **VQ — Qualidade da geração** (manifesto em Java + AST em Python), sem substituir V0–V6; critério objetivo quebrado bloqueia `CODE_VALIDATED`; aviso de nome não; D13 deixa VQ `NOT_CHECKED` | Q03/G6; legibilidade continua sendo aceite humano |
| D-CQ-11 | Classe do processo pelo nome de negócio (`BiografiasDaWikipedia`); o contrato V5 acha a ÚNICA subclasse de `RpaProcess` | "Processo" genérico banido quando há nome validado |
| D-CQ-12 | Árvore de arquivos e CLI de `main.py` inalteradas; requisitos do framework inalterados; tabular 1.0.0 fora do escopo | `OutputWriter` sem migração de caminhos; nenhuma troca de versão |
| D-CQ-13 | Única referência manual em `docs/codegen-quality-001/referencia-manual/` (réplica sintética), rotulada e nunca lida pelo gerador | exceção ÚNICA autorizada pela titular |
