package com.fraudengine.engine;

import java.util.List;

public record Evaluation(int totalScore, Decision decision, List<RuleHit> hits, List<String> rulesErrored) {
}
