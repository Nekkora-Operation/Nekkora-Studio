package com.nekkora.studio.domain;

/**
 * Posição calculada de uma etapa na sequência da automação.
 *
 * @param sessionId     sessão da etapa
 * @param stepId        etapa
 * @param sessionNumber posição da sessão (1..n)
 * @param numberInSession posição da etapa na sessão (1..n)
 * @param number        número visual contínuo na automação (1..n) — calculado, não é identidade
 */
public record StepPosition(SessionId sessionId, StepId stepId, int sessionNumber, int numberInSession, int number) {}
