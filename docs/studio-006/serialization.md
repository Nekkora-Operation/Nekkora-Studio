# STUDIO-006 — Serialização

## Princípios

1. **Código próprio, campo a campo** (`ProjectCodec`). Nenhuma serialização automática de objetos Java: mudar uma
   classe do domínio não muda o arquivo sem que alguém mude o codec — e o teste de contrato (`ProjectFormatContractTest`)
   acusa qualquer diferença em relação à fixture congelada 1.0.0.
2. **Todo campo existe sempre**. Ausência é `null` (`Json.NULL`). O leitor exige o conjunto exato de campos de cada
   objeto: campo a mais (ex.: `"script"`) ou a menos é `INVALID_CONTENT`. Assim, um arquivo de versão futura com campos
   novos nunca é lido "pela metade" e salvo de novo sem eles (§27).
3. **Formas canônicas**: UUID em minúsculas, instantes como `Instant.toString()`, enums pelo nome, inteiros como
   inteiros. Fora da forma canônica = recusa (não há normalização silenciosa).
4. **JSON estrito RFC 8259** (o mesmo `Json` do Bridge): UTF-8 válido, chaves duplicadas recusadas, profundidade
   limitada a 64.

## Pipeline de escrita

```text
EDT     ProjectCodec.documents(automação)        árvores JSON novas, desligadas do agregado (fotografia)
worker  Json.write → UTF-8                        um documento por entrada
worker  ProjectPackage.write                      metadata + documentos, SHA-256 e tamanho de cada um, manifesto primeiro
worker  ZipOutputStream (nível 6, data fixa)      bytes determinísticos para o mesmo conteúdo
worker  ProjectRepository.save                    temporário → sync → releitura completa → substituição
```

A fotografia é o único trabalho na EDT: com 5.000 etapas realistas, 27,8 ms ([performance.md](performance.md)). O
worker nunca toca o agregado (o Recorder pode estar mudando outra automação enquanto isso).

## Releitura antes de confirmar

O temporário é relido com o **mesmo caminho de leitura** da abertura (pacote → documentos → reconstrução → privacidade
v2) e precisa devolver a mesma automação, o mesmo projeto e o mesmo número de etapas. Um arquivo que o próprio Studio
não conseguiria abrir nunca substitui o anterior.

## Pipeline de leitura

Ver [hydration.md](hydration.md). Resumo: tamanho do arquivo → nomes das entradas → bytes lidos com limite →
manifesto (versão primeiro) → checksums → JSON → documentos com campos exatos → reconstrução → privacidade v2 → o
arquivo não mudou durante a leitura.

## Determinismo

`ProjectFormatContractTest.fixturesValidas` lê a fixture congelada, reconstrói a automação e a escreve de novo: os
documentos produzidos são idênticos aos do arquivo. Isso prova que ler e salvar de novo não perde nem altera nada
(dois ciclos completos estão em `ProjectRoundTripTest` §36).
