package com.nhinds.lastpass.android;

import org.apache.commons.codec.digest.DigestUtils;

import android.content.Context;
import android.provider.Settings.Secure;

public class LastPassDeviceId {
	/**
	 * Get a device identifier for lastpass based off this android's device id.
	 */
	public static String get(Context context) {
		String androidId = Secure.getString(context.getContentResolver(), Secure.ANDROID_ID);
		// Include this application's package name so the id would be different if 2 applications on the same android device used the
		// library
		return DigestUtils.shaHex(context.getPackageName() + "-" + androidId);
	}
}
