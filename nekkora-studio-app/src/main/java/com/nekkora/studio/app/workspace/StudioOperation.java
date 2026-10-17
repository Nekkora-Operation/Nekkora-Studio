package com.nekkora.studio.app.workspace;

/** Operações interativas (diálogos) — impede abrir uma segunda enquanto outra está em andamento. */
public enum StudioOperation {
    NEW_AUTOMATION, NEW_SESSION, RENAME_SESSION, NEW_STEP, STEP_DETAILS, EXIT,
    /** STUDIO-004 */
    EDIT_AUTOMATION, EDIT_SESSION, MOVE_STEP, CONFIRM,
    /** STUDIO-006 */
    OPEN_PROJECT, SAVE_PROJECT, CLOSE_PROJECT, RECOVERY,
    /** STUDIO-008 */
    VARIABLE
}
