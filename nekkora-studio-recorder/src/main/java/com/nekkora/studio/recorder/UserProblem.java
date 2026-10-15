package com.nekkora.studio.recorder;

import com.nekkora.studio.bridge.BridgeError;

/** Problema explicado ao usuário (código técnico estável + texto). */
public record UserProblem(BridgeError code, String title, String message) {
}
