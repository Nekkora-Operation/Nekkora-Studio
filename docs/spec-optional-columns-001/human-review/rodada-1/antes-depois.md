# Antes (CODEGEN-QUALITY-001, rodada 5 aprovada) → depois ("Morte" opcional no PDD)

Diferença ÚNICA entre os arquivos ORIGINAIS gerados pelo Studio: `docs/codegen-quality-001/human-review/rodada-5/final/` → `final/`
(automação de biografias gravada de novo pela interface, mesmo gerador; a única decisão nova é "Morte" opcional).
`automation/selectors.py` e `automation/__init__.py` são byte a byte iguais; no `selectors.json` muda só a procedência
(`studioStepId` das etapas, que são novas porque a gravação é nova).

```diff
--- antes/automation/pages.py
+++ depois/automation/pages.py
@@ -53,18 +53,33 @@
             raise BusinessRuleRejected("DATA_INCOMPLETA_OU_AMBIGUA", step_id=step_id)
         return date
 
+    def _date_if_present(self, key: str, *, step_id: str) -> str | None:
+        """Like ``_date``; ``None`` if the element does not exist on this page or shows no text (ambiguous is still an
+        error, and a text that is not a complete date still rejects the item).
+        """
+        if not self.actions.exists(key, step_id=step_id):
+            return None
+        items = self.actions.read_items(key, step_id=step_id)
+        if not items:
+            return None
+        date = parse_long_date(" ".join(items))
+        if date is None:
+            raise BusinessRuleRejected("DATA_INCOMPLETA_OU_AMBIGUA", step_id=step_id)
+        return date
+
 
 @dataclass(frozen=True, repr=False)
 class ProfileData:
     """Data read by ``ProfilePage.read_profile_data()`` (``repr`` does not show the values).
 
-    ``None``: the value does not exist on this page (absence foreseen in the PDD, with an alternative source).
+    ``None``: the value does not exist on this page (absence foreseen in the PDD: an alternative source or an
+    optional column).
     """
 
     title: str | None
     full_name: str | None
     birth_date: str
-    death_date: str
+    death_date: str | None
     nationality: str
     occupation: str | None
     fields: str | None
@@ -112,15 +127,16 @@
 
         Returns ``ProfileData``. First waits for “Nascimento” (a required read): only with the page ready is a
         missing optional value conclusive. The title, “Nome completo”, “Ocupação” and “Campo(s)” may be missing on
-        this page (``None``; the alternative source is in the PDD). A written-out date becomes YYYY-MM-DD; an
-        incomplete or ambiguous date rejects the item (``DATA_INCOMPLETA_OU_AMBIGUA``).
+        this page (``None``; the alternative source is in the PDD). “Morte” may be missing on this page (``None``;
+        an optional column in the PDD). A written-out date becomes YYYY-MM-DD; an incomplete or ambiguous date
+        rejects the item (``DATA_INCOMPLETA_OU_AMBIGUA``).
         """
         self.wait_loaded(step_id="profile.read_birth_date")
         return ProfileData(
             title=self._text_if_present(SelectorKey.PROFILE_TITLE.value, step_id="profile.read_title"),
             full_name=self._text_if_present(SelectorKey.PROFILE_FULL_NAME.value, step_id="profile.read_full_name"),
             birth_date=self._date(SelectorKey.PROFILE_BIRTH_DATE.value, step_id="profile.read_birth_date"),
-            death_date=self._date(SelectorKey.PROFILE_DEATH_DATE.value, step_id="profile.read_death_date"),
+            death_date=self._date_if_present(SelectorKey.PROFILE_DEATH_DATE.value, step_id="profile.read_death_date"),
             nationality=self._text(SelectorKey.PROFILE_NATIONALITY.value, step_id="profile.read_nationality"),
             occupation=self._text_if_present(SelectorKey.PROFILE_OCCUPATION.value, step_id="profile.read_occupation"),
             fields=self._text_if_present(SelectorKey.PROFILE_FIELDS.value, step_id="profile.read_fields"),
--- antes/automation/process.py
+++ depois/automation/process.py
@@ -46,11 +46,16 @@
     return value if value is not None and value.strip() else None
 
 
+REQUIRED_FIELDS = ("url", "full_name", "birth_date", "nationality", "occupation")
+"""Row fields that cannot stay empty; the other columns are optional in the PDD."""
+
+
 def build_spreadsheet_row(page_address: str, profile_data: ProfileData) -> dict[str, object]:
-    """One spreadsheet row by the PDD rule; a column that stays empty (or only whitespace) rejects the item with the
-    field name (the content never goes into the reason) and nothing is invented.
+    """One spreadsheet row by the PDD rule; a required column that stays empty (or only whitespace) rejects the item
+    with the field name (the content never goes into the reason); an optional one stays empty. Nothing is invented.
 
     - “Nome completo”: if missing on the page (or only whitespace), the title is used.
+    - “Morte”: optional in the PDD (the data does not exist for some items): stays empty when missing.
     - “Ocupação”: if missing on the page (or only whitespace), “Campo(s)” is used.
     """
     row: dict[str, object] = {
@@ -61,8 +66,8 @@
         "nationality": _filled(profile_data.nationality),
         "occupation": _filled(profile_data.occupation) or _filled(profile_data.fields),
     }
-    for field, value in row.items():
-        if value is None:
+    for field in REQUIRED_FIELDS:
+        if row[field] is None:
             raise BusinessRuleRejected(f"DADO_OBRIGATORIO_VAZIO:{field}")
     return row
 
--- antes/main.py
+++ depois/main.py
@@ -32,7 +32,7 @@
         OutputColumn("URL", "url", ColumnKind.URL),
         OutputColumn("Nome completo", "full_name", ColumnKind.TEXT),
         OutputColumn("Nascimento", "birth_date", ColumnKind.ISO_DATE),
-        OutputColumn("Morte", "death_date", ColumnKind.ISO_DATE),
+        OutputColumn("Morte", "death_date", ColumnKind.ISO_DATE, required=False),
         OutputColumn("Nacionalidade", "nationality", ColumnKind.TEXT),
         OutputColumn("Ocupação", "occupation", ColumnKind.TEXT),
     ),
```
