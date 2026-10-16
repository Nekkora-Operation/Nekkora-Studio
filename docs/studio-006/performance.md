# STUDIO-006 — Desempenho

Medição: `ProjectPerformanceTest` (roda em toda `./mvnw verify`) grava `target/studio-006-performance.json`. Resultado
do Windows em `reports/studio-006/performance-windows.json`; o do Linux de referência em `reports/studio-006/performance-linux.json`.

## Projeto medido

Etapas **realistas** (não vazias): elemento capturado com 3 localizadores, trajetória, contexto, nota de
estabilidade e correlação; 1 em cada 3 etapas com 3 evidências; 1 em cada 10 com comentário e edição manual; uma
sessão a cada 25 etapas. Mediana de 3 execuções depois de um aquecimento. As quantidades são cenários de medição, não
limites do produto (§39).

## Windows 11, JDK 21.0.12 — regressão do commit `3ddff58`

| Etapas | Sessões | Evidências | Arquivo | Fotografia (EDT) | Serialização | Salvar (com releitura) | Ler pacote | Abrir completo | Validação + reconstrução | Memória do projeto aberto | Gravar recuperação | Varrer recuperação |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 10 | 1 | 12 | 17,3 KiB | 0,6 ms | 2,2 ms | 14,6 ms | 1,9 ms | 6,1 ms | 4,1 ms | 0,04 MiB | 9,3 ms | 7,4 ms |
| 100 | 4 | 102 | 153 KiB | 1,6 ms | 7,0 ms | 38,8 ms | 6,2 ms | 22,6 ms | 16,4 ms | 0,35 MiB | 27,4 ms | 20,0 ms |
| 1.000 | 40 | 1.002 | 1,49 MiB | 6,0 ms | 63,5 ms | 232 ms | 44,3 ms | 151 ms | 107 ms | 3,5 MiB | 199 ms | 115 ms |
| 5.000 | 200 | 5.001 | 7,42 MiB | 20,5 ms | 311 ms | 878 ms | 230 ms | 588 ms | 359 ms | 17,4 MiB | 884 ms | 583 ms |

Uma execução anterior no mesmo computador (antes do commit) deu valores da mesma ordem (5.000 etapas: fotografia
27,8 ms, salvar 1.038 ms, abrir 670 ms): a variação entre execuções é de ±20%.

## Linux de referência (contêiner Temurin 21.0.12.1, `tools/golden-linux.sh verify`, commit `d2e0eec`)

| Etapas | Arquivo | Fotografia (EDT) | Serialização | Salvar (com releitura) | Ler pacote | Abrir completo | Validação + reconstrução | Memória | Gravar recuperação | Varrer recuperação |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 10 | 17,0 KiB | 1,3 ms | 3,7 ms | 20,5 ms | 2,9 ms | 11,4 ms | 8,5 ms | 0,04 MiB | 16,6 ms | 10,4 ms |
| 100 | 150,9 KiB | 3,2 ms | 8,0 ms | 56,9 ms | 5,8 ms | 17,8 ms | 12,0 ms | 0,35 MiB | 40,1 ms | 27,3 ms |
| 1.000 | 1,46 MiB | 7,9 ms | 88,6 ms | 236 ms | 41,0 ms | 130 ms | 89,4 ms | 3,48 MiB | 213 ms | 233 ms |
| 5.000 | 7,31 MiB | 19,9 ms | 399 ms | 876 ms | 169 ms | 567 ms | 398 ms | 17,42 MiB | 1.023 ms | 735 ms |

O arquivo é um pouco menor no Linux (zlib diferente; o conteúdo descompactado é o mesmo). Arquivo bruto:
`reports/studio-006/performance-linux.json`.

Leitura dos números:

- **EDT**: só a fotografia roda na thread de interface — 20 a 28 ms com 5.000 etapas. Todo o resto (serialização,
  compactação, gravação com `force`, releitura completa, leitura, validação, reconstrução, recuperação) roda no worker.
  O teste falha se a fotografia de 5.000 etapas passar de 1 s. Na homologação real (3 etapas gravadas), 5 ms.
- **Salvar** inclui a releitura completa do temporário antes de substituir (por isso ≈ 1,5× abrir). É o preço de nunca
  confirmar um arquivo que o Studio não conseguiria abrir.
- **Crescimento linear**: ~1,5 KiB e ~0,2 ms de salvamento por etapa realista.
- **Memória**: ~3,5 KiB por etapa aberta (medição por diferença de heap, aproximada).

## Limites operacionais definidos a partir das medições

| Limite | Valor | Motivo |
|---|---|---|
| etapas por projeto (leitor) | 20.000 | 4× o maior cenário medido; ~30 MiB e ~4 s para salvar, ainda fora da EDT |
| entrada descompactada | 8 MiB | a maior entrada realista é uma etapa (~3 KiB) ou o manifesto (~1,7 MiB com 5.000 etapas) |
| total descompactado | 512 MiB | teto contra bomba de descompressão |
| arquivo | 128 MiB | ~17× o projeto de 5.000 etapas |
| fotografias de recuperação | 3 por automação | gravar uma recuperação de 5.000 etapas ≈ 1 s no worker |

Projetos com milhares de etapas continuam utilizáveis; acima de ~5.000 etapas o salvamento leva mais de 1 s (no
worker; a interface segue respondendo e mostra "Salvando…").
