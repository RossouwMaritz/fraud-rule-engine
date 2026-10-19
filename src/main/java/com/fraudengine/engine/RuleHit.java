package com.fraudengine.engine;

public record RuleHit(String ruleId, int score, String reason) {
}
