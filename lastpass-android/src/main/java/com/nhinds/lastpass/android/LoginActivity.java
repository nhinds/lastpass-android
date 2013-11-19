package com.nhinds.lastpass.android;

import java.io.File;

import org.apache.commons.lang.Validate;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.app.ProgressDialog;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.TextView;

import com.nhinds.lastpass.GoogleAuthenticatorRequired;
import com.nhinds.lastpass.LastPass.PasswordStoreBuilder;
import com.nhinds.lastpass.LastPass.ProgressListener;
import com.nhinds.lastpass.LastPass.ProgressStatus;
import com.nhinds.lastpass.LastPassException;
import com.nhinds.lastpass.LastPassFactory;
import com.nhinds.lastpass.PasswordStore;

/**
 * Activity which displays a login screen to the user.
 */
public class LoginActivity extends Activity {
	private static final String REMEMBERED_EMAIL_PREF = "REMEMBERED_EMAIL";

	/**
	 * Keep track of the login task to ensure we can cancel it if requested.
	 */
	private UserLoginTask mAuthTask = null;

	// UI references.
	private EditText mEmailView;
	private EditText mPasswordView;
	private CheckBox mRememberEmailView;

	private EditText mOtpView;
	private CheckBox mTrustDeviceView;
	private EditText mTrustedDeviceLabelView;

	private PasswordStoreBuilder passwordStoreBuilder;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_login);

		// Set up the login form.
		this.mEmailView = findTypedViewById(R.id.email);
		this.mPasswordView = findTypedViewById(R.id.password);
		this.mOtpView = findTypedViewById(R.id.otp);
		this.mTrustDeviceView = findTypedViewById(R.id.trust_device);
		this.mTrustedDeviceLabelView = findTypedViewById(R.id.trusted_device_label);
		this.mRememberEmailView = findTypedViewById(R.id.remember_email);

		String rememberedEmail = getRememberedEmail();
		if (rememberedEmail != null) {
			this.mEmailView.setText(rememberedEmail);
			this.mRememberEmailView.setChecked(true);
			this.mPasswordView.requestFocus();
		}

		addListener(R.id.password, R.id.sign_in_button, new Runnable() {
			@Override
			public void run() {
				attemptLogin();
			}
		});
		addListener(R.id.otp, R.id.sign_in_button_otp, new Runnable() {
			@Override
			public void run() {
				attemptOtpLogin();
			}
		});

		this.mTrustDeviceView.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				LoginActivity.this.mTrustedDeviceLabelView.setVisibility(toVisibility(isChecked));
			}
		});
	}

	private void addListener(final int textId, final int buttonId, final Runnable action) {
		((EditText) findViewById(textId)).setOnEditorActionListener(new TextView.OnEditorActionListener() {
			@Override
			public boolean onEditorAction(TextView textView, int id, KeyEvent keyEvent) {
				if (id == R.id.login || id == EditorInfo.IME_NULL) {
					action.run();
					return true;
				}
				return false;
			}
		});
		findViewById(buttonId).setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				action.run();
			}
		});
	}

	/**
	 * Attempts to sign in specified by the login form. If there are form errors
	 * (invalid email, missing fields, etc.), the errors are presented and no
	 * actual login attempt is made.
	 */
	private void attemptLogin() {
		if (this.mAuthTask != null) {
			return;
		}

		if (validateNotEmpty(this.mEmailView, this.mPasswordView)) {
			String email = this.mEmailView.getText().toString();
			String password = this.mPasswordView.getText().toString();

			setRememberedEmail(this.mRememberEmailView.isChecked() ? email : null);

			// kick off a background task to perform the user login attempt.
			this.passwordStoreBuilder = LastPassFactory.getCachingLastPass(getCacheFile()).getPasswordStoreBuilder(email, password,
					LastPassDeviceId.get(this));
			this.mAuthTask = new UserLoginTask(this.passwordStoreBuilder);
			this.mAuthTask.execute();
		}
	}

	private File getCacheFile() {
		return new File(getCacheDir(), "login.dat");
	}

	private void attemptOtpLogin() {
		if (this.mAuthTask != null) {
			return;
		}
		if (this.passwordStoreBuilder == null) {
			throw new IllegalStateException("No password store builder found");
		}

		if (validateNotEmpty(this.mOtpView)) {
			final String otp = this.mOtpView.getText().toString();
			boolean trustDevice = this.mTrustDeviceView.isChecked();
			if (!trustDevice || validateNotEmpty(this.mTrustedDeviceLabelView)) {
				final String trustLabel;
				if (!trustDevice) {
					trustLabel = null;
				} else {
					trustLabel = this.mTrustedDeviceLabelView.getText().toString();
				}

				this.mAuthTask = new UserLoginTask(this.passwordStoreBuilder);
				this.mAuthTask.execute(otp, trustLabel);
			}
		}
	}

	private boolean validateNotEmpty(final EditText... views) {
		boolean valid = true;
		for (final EditText view : views) {
			if (TextUtils.isEmpty(view.getText())) {
				view.setError(getString(R.string.error_field_required));
				if (valid) {
					valid = false;
					// focus the first field with an error
					view.requestFocus();
				}
			} else {
				// reset errors for valid fields
				view.setError(null);
			}
		}
		return valid;
	}

	private enum FormState {
		LOGIN(R.id.login_form), OTP(R.id.login_otp_form);

		public final int viewId;

		private FormState(final int viewId) {
			this.viewId = viewId;
		}
	}

	/**
	 * Shows the progress UI and hides the login form. TODO push some of this
	 * into the enum
	 */
	private void setState(final FormState desiredState) {
		for (FormState formState : FormState.values()) {
			setVisible(formState.viewId, formState == desiredState);
		}
	}

	private void setVisible(final int viewId, final boolean show) {
		final View view = findViewById(viewId);
		if (show || view.getVisibility() == View.VISIBLE) {
			int shortAnimTime = getResources().getInteger(android.R.integer.config_shortAnimTime);

			view.setVisibility(View.VISIBLE);
			view.animate().setDuration(shortAnimTime).alpha(show ? 1 : 0).setListener(new AnimatorListenerAdapter() {
				@Override
				public void onAnimationEnd(Animator animation) {
					view.setVisibility(toVisibility(show));
				}
			});
		}
	}

	private String getRememberedEmail() {
		return getPreferences(Activity.MODE_PRIVATE).getString(REMEMBERED_EMAIL_PREF, null);
	}

	private void setRememberedEmail(String email) {
		getPreferences(Activity.MODE_PRIVATE).edit().putString(REMEMBERED_EMAIL_PREF, email).apply();
	}

	/** Helper method to avoid casts when looking up views */
	@SuppressWarnings("unchecked")
	private <V extends View> V findTypedViewById(final int id) {
		return (V) findViewById(id);
	}

	/** Convert a boolean into a visibility constant for {@link View#setVisibility(int)} */
	private static int toVisibility(final boolean visible) {
		return visible ? View.VISIBLE : View.GONE;
	}

	private static class LoginResult {
		public final LoginFailureReason failureReason;
		public final PasswordStore passwordStore;
		public final String reasonString;

		public LoginResult(PasswordStore passwordStore) {
			Validate.notNull(passwordStore);
			this.passwordStore = passwordStore;
			this.failureReason = null;
			this.reasonString = null;
		}

		public LoginResult(LoginFailureReason failureReason, String reasonString) {
			Validate.notNull(failureReason);
			this.failureReason = failureReason;
			this.reasonString = reasonString;
			this.passwordStore = null;
		}

		public LoginResult(LoginFailureReason failureReason) {
			this(failureReason, null);
		}
	}

	private enum LoginFailureReason {
		FAIL, OTP
	}

	/**
	 * Represents an asynchronous login task used to authenticate the user.
	 */
	public class UserLoginTask extends AsyncTask<String, ProgressStatus, LoginResult> implements ProgressListener {
		private final PasswordStoreBuilder passwordStoreBuilder;
		private ProgressDialog progressDialog;

		public UserLoginTask(PasswordStoreBuilder passwordStoreBuilder) {
			this.passwordStoreBuilder = passwordStoreBuilder;
		}

		@Override
		protected void onPreExecute() {
			this.progressDialog = new ProgressDialog(LoginActivity.this);
			this.progressDialog.setTitle(getString(R.string.login_progress_signing_in));
			this.progressDialog.show();
		}

		@Override
		protected LoginResult doInBackground(String... params) {
			try {
				PasswordStore passwordStore;
				if (params.length == 0)
					passwordStore = this.passwordStoreBuilder.getPasswordStore(this);
				else
					passwordStore = this.passwordStoreBuilder.getPasswordStore(params[0], params[1], this);
				return new LoginResult(passwordStore);
			} catch (final GoogleAuthenticatorRequired authenticatorRequired) {
				Log.d(getPackageName(), "Google authenticator required", authenticatorRequired);
				return new LoginResult(LoginFailureReason.OTP);
			} catch (final LastPassException failure) {
				Log.e(getPackageName(), "Error logging in", failure);
				return new LoginResult(LoginFailureReason.FAIL, failure.getMessage());
			}
		}

		@Override
		protected void onPostExecute(final LoginResult loginResult) {
			this.progressDialog.dismiss();
			LoginActivity.this.mAuthTask = null;
			if (loginResult.passwordStore != null) {
				SoftKeyboard.setPasswordStore(loginResult.passwordStore);
				finish();
			} else if (loginResult.failureReason == LoginFailureReason.OTP) {
				android.util.Log.i(getPackageName(), "OTP Required");
				setState(FormState.OTP);
				LoginActivity.this.mOtpView.requestFocus();
			} else {
				setState(FormState.LOGIN);
				LoginActivity.this.mPasswordView.setError(loginResult.reasonString);
				LoginActivity.this.mPasswordView.requestFocus();
			}
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
			this.progressDialog.setMessage(getString(stringId));
		}

		@Override
		protected void onCancelled() {
			// TODO who calls me?
			LoginActivity.this.mAuthTask = null;
			setState(FormState.LOGIN);
		}
	}
}
