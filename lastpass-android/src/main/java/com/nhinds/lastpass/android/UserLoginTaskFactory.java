package com.nhinds.lastpass.android;

import java.io.File;

import org.apache.commons.lang.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;

import com.nhinds.lastpass.GoogleAuthenticatorRequired;
import com.nhinds.lastpass.LastPassException;
import com.nhinds.lastpass.LastPassFactory;
import com.nhinds.lastpass.PasswordStore;
import com.nhinds.lastpass.LastPass.PasswordStoreBuilder;
import com.nhinds.lastpass.LastPass.ProgressListener;
import com.nhinds.lastpass.LastPass.ProgressStatus;

/**
 * Represents an asynchronous login task used to authenticate the user.
 */
public class UserLoginTaskFactory {
	
	public static UserLoginTaskFactory create(final String email, final String password, final Context context, final UserLoginListener listener) {
		final PasswordStoreBuilder passwordStoreBuilder = LastPassFactory.getCachingLastPass(getCacheFile(context)).getPasswordStoreBuilder(email, password,	LastPassDeviceId.get(context));
		return new UserLoginTaskFactory(passwordStoreBuilder, listener, context);
	}

	private static File getCacheFile(final Context context) {
		return new File(context.getCacheDir(), "login.dat");
	}

	private static final Logger LOGGER = LoggerFactory.getLogger(UserLoginTaskFactory.class);
	
	private final PasswordStoreBuilder passwordStoreBuilder;
	private final Context context;
	private final UserLoginListener listener;

	public UserLoginTaskFactory(final PasswordStoreBuilder passwordStoreBuilder, final UserLoginListener listener, final Context context) {
		this.passwordStoreBuilder = passwordStoreBuilder;
		this.listener = listener;
		this.context = context;
	}
	
	public UserLoginTask loginWithoutOtp() {
		UserLoginTask userLoginTask = new UserLoginTask();
		userLoginTask.execute();
		return userLoginTask;
	}

	public UserLoginTask loginWithOtp(final String otp, final String trustLabel) {
		UserLoginTask userLoginTask = new UserLoginTask();
		userLoginTask.execute(otp, trustLabel);
		return userLoginTask;
	}
	
	public static class UserLoginResult {
		public final LoginFailureReason failureReason;
		public final PasswordStore passwordStore;
		public final String reasonString;

		public UserLoginResult(PasswordStore passwordStore) {
			Validate.notNull(passwordStore);
			this.passwordStore = passwordStore;
			this.failureReason = null;
			this.reasonString = null;
		}

		public UserLoginResult(LoginFailureReason failureReason, String reasonString) {
			Validate.notNull(failureReason);
			this.failureReason = failureReason;
			this.reasonString = reasonString;
			this.passwordStore = null;
		}

		public UserLoginResult(LoginFailureReason failureReason) {
			this(failureReason, null);
		}
	}
	
	public interface UserLoginListener {
		/** 
		 * Called when the login completed - the result may be successful or a failure
		 * 
		 * @param result The result of the login, never null 
		 */
		void loginCompleted(UserLoginResult result);
		

		/** Called when a {@link ProgressDialog} is created if it needs to be modified to display correctly */
		void progressDialogCreated(ProgressDialog dialog);
	}

	public enum LoginFailureReason {
		FAIL, OTP, CANCEL
	}
	
	public class UserLoginTask  extends AsyncTask<String, ProgressStatus, UserLoginResult> implements ProgressListener { 
		private ProgressDialog progressDialog;
		@Override
		protected void onPreExecute() {
			this.progressDialog = new ProgressDialog(context);
			this.progressDialog.setTitle(context.getString(R.string.login_progress_signing_in));
			listener.progressDialogCreated(this.progressDialog);
			this.progressDialog.show();
		}
	
		@Override
		protected UserLoginResult doInBackground(String... params) {
			try {
				PasswordStore passwordStore;
				if (params.length == 0)
					passwordStore = passwordStoreBuilder.getPasswordStore(this);
				else
					passwordStore = passwordStoreBuilder.getPasswordStore(params[0], params[1], this);
				return new UserLoginResult(passwordStore);
			} catch (final GoogleAuthenticatorRequired authenticatorRequired) {
				LOGGER.debug("Google authenticator required", authenticatorRequired);
				return new UserLoginResult(LoginFailureReason.OTP);
			} catch (final LastPassException failure) {
				LOGGER.debug("Error logging in", failure);
				return new UserLoginResult(LoginFailureReason.FAIL, failure.getMessage());
			}
		}
	
		@Override
		protected void onPostExecute(final UserLoginResult loginResult) {
			this.progressDialog.dismiss();
			listener.loginCompleted(loginResult);
		}
	
		@Override
		public void statusChanged(ProgressStatus status) {
			// Queue progress update to happen on the UI thread from the background thread
			publishProgress(status);
		}
	
		@Override
		protected void onProgressUpdate(final ProgressStatus... statuses) {
			assert statuses.length == 1;
			// Update the progress on the UI thread
			final ProgressStatus status = statuses[0];
			final int stringId;
			switch (status) {
			case LOGGING_IN:
				stringId = R.string.login_progress_signing_in;
				break;
			case RETRIEVING:
				stringId = R.string.login_progress_retrieving;
				break;
			case DECRYPTING:
				stringId = R.string.login_progress_decrypting;
				break;
			default:
				throw new IllegalStateException();
			}
			this.progressDialog.setMessage(context.getString(stringId));
		}
		
		@Override
		protected void onCancelled() {
			onPostExecute(new UserLoginResult(LoginFailureReason.CANCEL));
		}
	}
}