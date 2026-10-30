# S6 — Consolidação no GitHub

## 1. Árvores e branches

| Referência | Commit | Árvore | Papel |
|---|---|---|---|
| `main` remota (no momento do PR) | `1cbf669` | `8abe57d4…` | base |
| `feat/studio-lean-001` (local) | `da7bb94` | `19973371…` | candidata comprovada em laboratório, intocada |
| `fix/studio-integration-001` (local) | `b83f04d` | `d97a985a…` | `da7bb94` mais as 5 correções (S3) |
| **`integracao/studio-consolidacao-001`** (PR) | `f879af5` (código) e o commit de documentação desta pasta | `3cec7230…` (antes dos docs) | os 27 commits reaplicados saneados sobre a `main`, mais esta documentação |

A diferença entre `f879af5` e `b83f04d` são exatamente **31 arquivos de evidência**: os 11 logs `.txt` com a pasta do usuário trocada por `%USERPROFILE%` e as 20 capturas tarjadas. **Nada fora de `docs/` difere.** As árvores de `nekkora-studio-codegen`, `Nekkora-AI-Engine`, `nekkora-studio-app` e `nekkora-studio-ui` são idênticas.

## 2. Reprodução saneada

Feita conforme a decisão da titular de 29/10, com o script `evidencias/reproduzir.py`:
- para cada um dos 27 commits (`1cbf669..b83f04d`), na ordem, usa a **mesma árvore** do commit original e troca só os blobs saneados, conferidos por caminho;
- autor, datas e mensagem são os originais;
- a mensagem ganha a linha `Origem-local: <sha>`.

| Origem local | Na integração | | Origem local | Na integração |
|---|---|---|---|---|
| edee926 | 2df97a6 | | 838e86b | 2302b71 |
| 4d27da2 | 55c5497 | | 9a842b9 | bae7630 |
| 5d09790 | 4d8ae1b | | 425be58 | 7e1372c |
| 65caaad | b0471b3 | | 4359c07 | 81247ee |
| b2ed26b | e34b754 | | 6828edc | db98e57 |
| 8c5de7b | dda1a7c | | a390664 | cad189d |
| a3e1a97 | 81ef696 | | 29fdeea | dbca71d |
| 56ed279 | 56dec3f | | 91040a5 | be2c2a1 |
| 81bc9c8 | 834f824 | | 811fedf | 14b67e8 |
| ad0e3d3 | dd10b57 | | da7bb94 | bdd8269 |
| 886850c | 662eb2e | | 1ebf6f6 (S3-1) | 3738b6c |
| ddd933a | 43d6713 | | 7328634 (S3-2) | af2962b |
| | | | c0777b8 (S3-5) | 4009ba7 |
| | | | 2cf559f (S3-3) | bbcda0e |
| | | | b83f04d (S3-4) | f879af5 |

Tudo foi conferido depois da reprodução:
- nenhum blob novo, em nenhum dos 27 commits, contém a pasta do usuário;
- o OCR das capturas tarjadas não acha caminho nem dado pessoal;
- nenhum `SHA256SUMS` ou manifesto cita os arquivos saneados.

Uma primeira versão da reprodução trocava capturas por SHA do blob. Ela alcançou também `docs/e2e-002/evidence/capturas/G6-01-escolher-lista.png`, que já está na `main` com o mesmo conteúdo. Essa branch, local e nunca publicada, foi descartada, e a troca passou a ser por caminho: **nenhum arquivo da `main` é alterado** além dos que os commits originais já alteravam.

## 3. Prova de que a árvore final contém o que deve conter

- **Linha de base anterior:** a `main` `1cbf669` é ancestral da branch, então não há conflito.
  - Os únicos arquivos da `main` removidos são `Emitter.java` e `Modern.java`, os mesmos removidos pelos commits originais da CODEGEN-QUALITY-001. A funcionalidade deles está em `SemanticEmitter`/`TabularEmitter` (S1 §2).
  - A regressão completa passa: Windows 788/0, Linux 788/0 com 29 pulados por ambiente.
- **Evoluções locais aprovadas:** CODEGEN-QUALITY-001, SPEC-OPTIONAL-COLUMNS-001 e STUDIO-LEAN-001, com os 22 commits na ordem e o mesmo código.
- **Correções desta missão:** os 5 commits de S3, com teste vermelho e verde.
- **Contratos de pacote homologados:**
  - manifesto 1.5.0, gerador 1.2.0 e framework 0.3.0;
  - pacote genuíno aceito pela API (`main` `e04a57c`) e pelo Agent (`main` `ce30397`);
  - jornada 9/9 (S2, S5).
- **Nenhuma alteração destrutiva ou não autorizada:**
  - nenhuma branch local apagada ou reescrita;
  - nenhum force-push, nenhuma migration, nenhum ambiente persistente alterado;
  - o Framework 0.4.0 não foi adotado;
  - Extensão, Agent e API intocados.

## 4. PR e checks

- **PR:** `integracao/studio-consolidacao-001` → `main`. Um PR principal, sem dividir: as correções já estão em commits próprios dentro dele.
- **Checks:** o repositório não tem CI configurado, então as evidências de verificação são as das S4 e S5.
- **Merge:** pela titular. Nada foi mesclado.
