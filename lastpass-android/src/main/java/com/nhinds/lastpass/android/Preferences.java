package com.nhinds.lastpass.android;

import java.security.GeneralSecurityException;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nhinds.lastpass.encryption.AES256EncryptionProvider;
import com.nhinds.lastpass.encryption.EncryptionProvider;
import com.nhinds.lastpass.encryption.KeyProvider;
import com.nhinds.lastpass.encryption.PBKDF2SHA256KeyProvider;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

public class Preferences {
	private static final Logger LOGGER = LoggerFactory.getLogger(Preferences.class);
	
	private static final String PREFERENCES_NAME = Preferences.class.getSimpleName();
	
	private static final String REMEMBERED_EMAIL_PREF = "REMEMBERED_EMAIL";
	private static final String REMEMBERED_PASSWORD_PREF = "REMEMBERED_PASSWORD";
	
	private static final KeyProvider KEY_PROVIDER = new PBKDF2SHA256KeyProvider();
	private static final int KEY_ITERATIONS = 100;
	
	private final Context context;
	private EncryptionProvider encryptionProvider;
	
	public Preferences(final Context context) {
		this.context = context;
	}
	
	private EncryptionProvider getEncryptionProvider() {
		if (this.encryptionProvider == null) {
			this.encryptionProvider = new AES256EncryptionProvider(getKey());
		}
		return this.encryptionProvider;
	}
	
	private byte[] getKey() {
		String deviceId = LastPassDeviceId.get(context);
		try {
			return KEY_PROVIDER.getKey(deviceId, deviceId, KEY_ITERATIONS);
		} catch (GeneralSecurityException e) {
			throw new RuntimeException("Error generating key", e);
		}
	}

	private SharedPreferences getPreferences() {
		return this.context.getSharedPreferences(PREFERENCES_NAME, Activity.MODE_PRIVATE);
	}
	
	private String getPreference(String preference) {
		return getPreferences().getString(preference, null);
	}
	
	/** @return the remembered email for login, or null if the email is not set */
	public String getRememberedEmail() {
		return getPreference(REMEMBERED_EMAIL_PREF);
	}

	/** 
	 * Set the remembered email for login
	 *
	 * @param email The email to remember. May be null.
	 */
	public void setRememberedEmail(final String email) {
		getPreferences().edit().putString(REMEMBERED_EMAIL_PREF, email).apply();
	}
	
	/** @return the remembered password for login, or null if the password is not set */
	public String getRememberedPassword() {
		return decrypt(getPreference(REMEMBERED_PASSWORD_PREF));
	}

	/** 
	 * Set the remembered password for login.
	 * <p>
	 * This is encrypted internally to prevent casual observation, although a user with full control over the device would be able to gain access to the decryption key.
	 *
	 * @param password The encrypted password to remember. May be null.
	 */
	public void setRememberedPassword(final String password) {
		getPreferences().edit().putString(REMEMBERED_PASSWORD_PREF, encrypt(password)).apply();
	}

	/**
	 * Convenience method to set both the remembered email and password for login at once
	 * 
	 * @see #setRememberedEmail(String)
	 * @see #setRememberedPassword(String)
	 */
	public void setRememberedEmailAndPassword(final String email, final String password) {
		getPreferences().edit().putString(REMEMBERED_EMAIL_PREF, email).putString(REMEMBERED_PASSWORD_PREF, encrypt(password)).apply();
	}

	private String decrypt(String preference) {
		if (preference == null)
			return null;
		try {
			return getEncryptionProvider().decrypt(Hex.decodeHex(preference.toCharArray()));
		} catch (final DecoderException e) {
			LOGGER.error("Error decrypting string, returning null", e);
			return null;
		}
	}

	private String encrypt(String password) {
		if (password == null)
			return null;
		return new String(Hex.encodeHex(getEncryptionProvider().encrypt(password)));
	}
}
