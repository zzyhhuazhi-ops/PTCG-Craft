package dev.hua.ptcglive.engine;

/** Replayable audit record. It exposes results only when game rules make them public. */
public record RandomAuditEvent(long sequence, String operation, int bound, int result) { }
