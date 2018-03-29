/*
 * Created on Mar 8, 2014
 */
package uk.co.coinfloor.client;

import java.math.BigDecimal;

enum AssetType {

	XBT("\u0243", false, 0xF800, 4), EUR("\u20AC", false, 0xFA00, 2), GBP("\u00A3", false, 0xFA20, 2),
	USD("$", false, 0xFA80, 2), PLN("\u00A0z\u0142", true, 0xFDA8, 2);

	static final class Pair {

		final AssetType base, counter;

		Pair(AssetType base, AssetType counter) {
			assert base != null && counter != null;
			this.base = base;
			this.counter = counter;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (!(obj instanceof Pair)) {
				return false;
			}
			Pair pair = (Pair) obj;
			return base == pair.base && counter == pair.counter;
		}

		@Override
		public int hashCode() {
			return (base == null ? 0 : base.hashCode() * 65521) + (counter == null ? 0 : counter.hashCode());
		}

		@Override
		public String toString() {
			return String.valueOf(base) + ':' + String.valueOf(counter);
		}

	}

	public final String symbol;
	public final boolean symbolAfter;
	public final int code;
	public final int scale;

	private AssetType(String symbol, boolean symbolAfter, int code, int scale) {
		this.symbol = symbol;
		this.symbolAfter = symbolAfter;
		this.code = code;
		this.scale = scale;
	}

	static AssetType forCode(int code) {
		switch (code) {
			case 0xF800:
				return XBT;
			case 0xFA00:
				return EUR;
			case 0xFA20:
				return GBP;
			case 0xFA80:
				return USD;
			case 0xFDA8:
				return PLN;
		}
		return null;
	}

	StringBuilder append(StringBuilder sb, BigDecimal amount) {
		return symbolAfter ? insertThousandsSeparators(sb, amount.toPlainString()).append(symbol) : insertThousandsSeparators(sb.append(symbol), amount.toPlainString());
	}

	String format(BigDecimal amount) {
		return append(new StringBuilder(), amount).toString();
	}

	private static StringBuilder insertThousandsSeparators(StringBuilder sb, String numberStr) {
		int end = numberStr.indexOf('.');
		if (end < 0) {
			end = numberStr.length();
		}
		int n = end % 3;
		if (n > 0) {
			sb.append(numberStr, 0, n);
		}
		while (n < end) {
			if (n > 0) {
				sb.append(',');
			}
			sb.append(numberStr, n, n += 3);
		}
		if (n < (end = numberStr.length())) {
			sb.append(numberStr, n, end);
		}
		return sb;
	}

}
