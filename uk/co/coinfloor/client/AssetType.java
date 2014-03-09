/*
 * Created on Mar 8, 2014
 */
package uk.co.coinfloor.client;

enum AssetType {

	XBT("\u0243", 0xF800, 4), GBP("\u00A3", 0xFA20, 2);

	public final String symbol;
	public final int code;
	public final int scale;

	private AssetType(String symbol, int code, int scale) {
		this.symbol = symbol;
		this.code = code;
		this.scale = scale;
	}

	static AssetType forCode(int code) {
		switch (code) {
			case 0xF800:
				return XBT;
			case 0xFA20:
				return GBP;
		}
		return null;
	}

}
