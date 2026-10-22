# Rascunho de PR — feat/codegen-quality-001 → main (NÃO ABERTO)

> Preparado só localmente. A branch nunca foi enviada ao remoto e nenhum PR existe. Abrir, enviar ou mergear depende de
> decisão separada da titular (repositório Nekkora-Studio é público; o código é proprietário).

## Título

feat(codegen): Python gerado legível e rastreável, em inglês, com chaves de alvo tipadas (CODEGEN-QUALITY-001)

## Descrição

O gerador oficial do Studio passa a produzir código que se lê em code review sem UUIDs, sem seletores no corpo das
telas e sem comentários por etapa:

- telas (`*Page`) nomeadas pelo PDD revisado, métodos pelo que fazem, dados lidos em dataclasses tipadas;
- `step_id` semântico e estável (registro de rastreio), com o mapa para o StepId do Studio no manifesto;
- `automation/selectors.py` (`SelectorKey` + `TARGETS`) com `selectors.json` derivado para o runtime 0.2.0/0.3.0;
- identificadores em inglês por glossário governado e versionado (`naming-pt-en/1.2.0`); colunas, cabeçalhos, rótulos,
  segredos e códigos de recusa do cliente ficam literais, com o mapeamento explícito no código e no manifesto;
- camada VQ (análise AST) e contrato V5 ampliado bloqueiam regressões (UUID no código, chave como texto, identificador
  em português, dataclass com repr de valores…);
- emissor tabular (RPA Challenge) no mesmo formato; regerar sobre projeto antigo guarda a geração anterior em
  `previous-generation/` e declara chaves e ids anteriores no manifesto.

Framework, extensão e Agent sem mudança. Decisões: `docs/codegen-quality-001/decisoes.md` (D-CQ-01…49).

## Checklist

- [x] Suíte Windows no commit `838e86b`: 767/767, 0 pulados
- [x] Suíte Linux no commit `838e86b`: 743 aprovados, 0 falhas, 24 pulados declarados (sem Python no contêiner / root)
- [x] Homologação pela interface real: Wikipédia 89/89 + conferidor 24/24, The Internet 12/12, RPA Challenge 27/27
- [x] Site real rpachallenge.com: 2× 10/10, placar 100%
- [x] Revisão técnica independente aprovada (rodada 5)
- [x] Aceite de legibilidade da titular (ver `encerramento.md`)
- [ ] Decisão da titular sobre visibilidade/publicação do repositório
- [ ] Push da branch e abertura do PR (bloqueado)

## Riscos residuais

- Glossário cobre o vocabulário dos processos conhecidos; palavra nova vira pendência `IDENTIFICADOR_SEM_TRADUCAO`
  (aviso), não tradução inventada.
- Projetos gerados antes continuam como estão até serem gerados de novo (sem migração automática).
- O registro de rastreio fica nos dados do Studio; em outra máquina a semente vem do manifesto da pasta.
- `repr=False` evita exposição acidental pelo repr; não substitui sanitização de logs nem impede `asdict`/acesso a campos.
- "Morte" continua obrigatória (NEKKORA-SPEC-OPTIONAL-COLUMNS-001).
- 24 testes pulados no Linux dependem de um ambiente com o Python do framework para rodar lá.

## Evidências

- `docs/codegen-quality-001/human-review/rodada-1` … `rodada-5` (código original do Studio com SHA-256)
- `docs/codegen-quality-001/human-review/rodada-5/evidencias/` (suítes, homologações, site real, réplica, sondas)
- `docs/codegen-quality-001/homologacao.md`, `limitacoes-e-seguranca.md`

🤖 Generated with [Claude Code](https://claude.com/claude-code)
