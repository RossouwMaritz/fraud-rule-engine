package com.fraudengine.engine.rules;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Currency;
import java.util.Locale;

/** Turns rule numbers into the words a reason uses. */
final class Format {

	private Format() {
	}

	/** Minor units in major units, e.g. 125000 ZAR is "ZAR 1,250.00". */
	static String money(long minor, String currency) {
		int digits;
		try {
			digits = Math.max(0, Currency.getInstance(currency).getDefaultFractionDigits());
		}
		catch (IllegalArgumentException ex) {
			digits = 2; // unknown code: a reason must never make a rule fail
		}
		return String.format(Locale.ROOT, "%s %,." + digits + "f", currency, BigDecimal.valueOf(minor, digits));
	}

	/** Whole days or hours when the duration is exact, otherwise minutes. */
	static String duration(Duration d) {
		if (d.toHours() > 0 && d.toMinutesPart() == 0 && d.toSecondsPart() == 0) {
			return d.toHours() % 24 == 0 ? plural(d.toDays(), "day") : plural(d.toHours(), "hour");
		}
		return plural(d.toMinutes(), "minute");
	}

	private static String plural(long n, String unit) {
		return n + " " + unit + (n == 1 ? "" : "s");
	}

}
