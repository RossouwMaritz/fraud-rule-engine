package com.fraudengine.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

import com.fraudengine.engine.FraudRulesProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The version of the bands and rules this app runs with: the first 12 hex characters of the SHA-256 of their JSON.
 * Stored in {@code rule_configs} at startup, so every decision can point at the exact config that produced it.
 */
@Component
public class RuleConfigVersion {

	private static final Logger log = LoggerFactory.getLogger(RuleConfigVersion.class);

	private final String version;

	public RuleConfigVersion(FraudRulesProperties properties, JsonMapper jsonMapper, JdbcClient jdbc)
			throws NoSuchAlgorithmException {
		// Map keys sorted, so the same config always gives the same JSON. The same shape as GET /api/v1/rules.
		var json = jsonMapper.rebuild()
			.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
			.build()
			.writeValueAsString(Map.of("bands", properties.bands(), "rules", properties.rules()));
		var hash = MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8));
		this.version = HexFormat.of().formatHex(hash).substring(0, 12);
		// Several instances can start with the same config, so an existing row is fine.
		var added = jdbc.sql("""
				insert into rule_configs (version, config) values (?, cast(? as jsonb))
				on conflict (version) do nothing""").params(version, json).update();
		log.info("Rule config version {} ({})", version, added == 1 ? "new" : "seen before");
	}

	public String version() {
		return version;
	}

}
