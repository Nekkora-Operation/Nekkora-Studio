package com.nekkora.studio.app.workspace;

import com.nekkora.studio.domain.AutomationId;
import com.nekkora.studio.domain.SessionId;
import com.nekkora.studio.domain.StepId;

/** Item selecionado na área de trabalho (por automação). As identidades ficam internas. */
public sealed interface Selection permits Selection.None, Selection.OfAutomation, Selection.OfSession, Selection.OfStep {

    Selection NONE = new None();

    record None() implements Selection {}

    record OfAutomation(AutomationId automation) implements Selection {}

    record OfSession(AutomationId automation, SessionId session) implements Selection {}

    record OfStep(AutomationId automation, SessionId session, StepId step) implements Selection {}
}
