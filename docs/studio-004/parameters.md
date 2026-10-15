# STUDIO-004 — Parâmetros das ações

O editor usa os modelos que já existiam: `ActionParameters`, `ValueSource` (`LiteralValue`, `VariableReference`,
`CredentialReference`) e `OptionChoice`. Não há um segundo modelo.

## Aba Parâmetros

O que aparece depende do **tipo de ação** (os metadados do `ActionType`):

| Tipo aceita | Controles |
|---|---|
| Valor (`FILL`, `NAVIGATE`, `PRESS_KEY`, `UNKNOWN`) | Origem: Nenhum, Valor literal, Variável ou Credencial; campo "Referência ou valor"; explicação de cada origem |
| Opção (`SELECT_OPTION`, `UNKNOWN`) | Rótulo da opção e valor técnico, que são dados distintos |
| Nenhum dos dois | "Este tipo de ação não recebe parâmetros." Se a etapa tinha parâmetros, o editor avisa que vai perguntar ao salvar |

## Origens do valor

| Origem | Regra |
|---|---|
| **Valor literal** | Só texto não sensível digitado pelo usuário. O campo nunca é preenchido com o que a gravação observou (ela não transmite valores). Recusado se tiver aparência de dado sensível (`LITERAL_LOOKS_SENSITIVE`) ou se o elemento for sensível (`LITERAL_ON_SENSITIVE_TARGET`). Em elemento sensível, a opção nem fica disponível |
| **Variável** | Nome como `cliente.nome`, `pedido.numero` ou `consulta.filtro` (`ValueSource.REFERENCE_NAME`: letras, números, ponto, hífen e sublinhado; até 64 caracteres) |
| **Credencial** | Só um apelido, como `sistema.usuario` ou `sistema.senha`. O Studio **não** guarda credenciais, e a interface diz: "a referência ainda não está vinculada a um cofre de credenciais" |

Na tela, as referências aparecem pelo nome ("Variável cliente.usuario", "Credencial sistema.senha"), nunca por um
conteúdo.

## Opção (`SELECT_OPTION`)

- Rótulo ("São Paulo") e valor técnico ("SP") são campos distintos. A opção continua sendo parâmetro de **uma** etapa:
  nenhuma etapa extra representa o `<option>`.
- O valor capturado continua no baseline e pode ser restaurado.

## Incompatibilidade com o tipo

Trocar para um tipo que não aceita os parâmetros atuais exige uma decisão explícita ("Remover parâmetros incompatíveis?").
O domínio recusa qualquer edição incompatível (`VALUE_NOT_ACCEPTED`, `OPTION_NOT_ACCEPTED`). Ver
[step-editor.md](step-editor.md).

## Evidência

- Domínio: `AutomationEditingTest.parametros`, `opcao` e `editarEtapa`.
- Aplicação: `EditorStudioTest.parametros`, `opcao` e `editarEtapa`.
- Homologação real, cenário 4: "Preencher campo Usuário" → Variável `cliente.usuario`; "Preencher campo Senha" →
  Credencial `sistema.senha`, com o literal indisponível. Nem o usuário nem a senha digitados na gravação aparecem no
  domínio ou na tela.
