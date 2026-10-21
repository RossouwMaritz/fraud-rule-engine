package com.fraudengine.api;

import com.fraudengine.engine.FraudRulesProperties;

/** Only the bands and rules, so other settings under {@code fraud.*} (like the API key) never leak. */
public record RulesResponse(String configVersion, FraudRulesProperties.Bands bands, FraudRulesProperties.Rules rules) {
}
