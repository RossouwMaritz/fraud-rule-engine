package com.fraudengine.api;

import java.util.List;

import com.fraudengine.engine.Decision;
import com.fraudengine.engine.RuleHit;
import com.fraudengine.service.EvaluationResult;

public record EvaluationResponse(String transactionId, Decision decision, int totalScore, List<RuleHit> hits,
		List<String> rulesErrored, String configVersion) {

	static EvaluationResponse of(EvaluationResult result) {
		var evaluation = result.evaluation();
		return new EvaluationResponse(result.transactionId(), evaluation.decision(), evaluation.totalScore(),
				evaluation.hits(), evaluation.rulesErrored(), result.configVersion());
	}

}
