# STUDIO-004 — Privacidade no editor

As garantias das missões anteriores continuam (`DisplaySafety`, sanitização na extensão, nenhum valor digitado na
gravação). O editor acrescenta campos, parâmetros e histórico. Por isso:

| Item | Proteção |
|---|---|
| Valor literal | `LiteralValue` recusa aparência de segredo (e-mail, CPF, CNPJ, cartão, JWT, Bearer). Em elemento sensível, o literal é recusado e nem é oferecido |
| Variável e credencial | Só nomes ou apelidos (`REFERENCE_NAME`). O conteúdo real nunca é guardado, porque não há cofre nesta missão |
| Comentários | Recusam aparência de dado sensível; limite de tamanho; caracteres de controle tratados |
| Localizadores manuais | Recusam aparência de dado sensível na expressão e na observação |
| Opção (rótulo e valor) | Exibidos pela barreira de apresentação (`StudioText.technical`) |
| Baseline capturado | Só o que o Recorder entregou, já sanitizado. Restaurar nunca devolve um segredo, porque nunca houve um |
| Histórico (desfazer/refazer) | Só guarda estados aceitos pelo domínio. O que a privacidade recusou nunca entra (`EditorStudioTest.privacidade`) |
| Interface | Nenhum identificador interno (gravação, evento, alvo, nó, captura, documento) é exibido. `derivedFrom` é interno |
| Persistência | Nada vai para o disco: o histórico fica em memória e as automações continuam sendo rascunho em memória |

Não são armazenados senhas literais, tokens, códigos OTP, credenciais em texto, payloads da página, PageMaps nem dados
de autenticação do Bridge.

## Evidência

- `EditorStudioTest.privacidade`: nenhum UUID ou identificador técnico no painel, no editor e na aba de origem;
  comentário com token recusado sem entrar no histórico.
- `EditorStudioTest.parametros` e `AutomationEditingTest.parametros`: literal sensível recusado.
- `StudioPrivacyTest` e `StudioScopeTest` (STUDIO-001/003) continuam passando.
- Homologação real: o usuário e a senha digitados na gravação não aparecem no domínio nem na tela, e não há
  identificadores internos na tela.
