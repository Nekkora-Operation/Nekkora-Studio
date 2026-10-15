package com.nekkora.studio.bridge;

import java.util.Map;

/** Evento recebido do navegador ({@code studio.recording.*}): tipo e payload já validados na fronteira. */
public record BridgeEvent(String type, Map<String, Object> payload) {
}
