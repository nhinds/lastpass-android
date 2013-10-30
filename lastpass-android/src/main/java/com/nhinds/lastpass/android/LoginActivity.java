package com.nhinds.lastpass.android;

import org.apache.commons.lang.Validate;

import com.nhinds.lastpass.LastPass.PasswordStoreBuilder;
import com.nhinds.lastpass.GoogleAuthenticatorRequired;
import com.nhinds.lastpass.LastPassException;
import com.nhinds.lastpass.PasswordStore;
import com.nhinds.lastpass.impl.LastPassImpl;
import com.sun.jersey.spi.StringReader.ValidateDefaultValue;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

/**
 * Activity which displays a login screen to the user, offering registration as
 * well.
 */
public class LoginActivity extends Activity {
	
	/**
	 * Keep track of the login task to ensure we can cancel it if requested.
	 */
	private UserLoginTask mAuthTask = null;

	// Values for email and password at the time of the login attempt.
	private String mEmail;
	private String mPassword;

	// UI references.
	private EditText mEmailView;
	private EditText mPasswordView;
	private View mLoginFormView;
	private View mLoginStatusView;
	private TextView mLoginStatusMessageView;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_login);

		// Set up the login form.
		mEmailView = (EditText) findViewById(R.id.email);

		mPasswordView = (EditText) findViewById(R.id.password);
		mPasswordView
				.setOnEditorActionListener(new TextView.OnEditorActionListener() {
					@Override
					public boolean onEditorAction(TextView textView, int id,
							KeyEvent keyEvent) {
						if (id == R.id.login || id == EditorInfo.IME_NULL) {
							attemptLogin();
							return true;
						}
						return false;
					}
				});

		mLoginFormView = findViewById(R.id.login_form);
		mLoginStatusView = findViewById(R.id.login_status);
		mLoginStatusMessageView = (TextView) findViewById(R.id.login_status_message);

		findViewById(R.id.sign_in_button).setOnClickListener(
				new View.OnClickListener() {
					@Override
					public void onClick(View view) {
						attemptLogin();
					}
				});
	}

	/**
	 * Attempts to sign in or register the account specified by the login form.
	 * If there are form errors (invalid email, missing fields, etc.), the
	 * errors are presented and no actual login attempt is made.
	 */
	private void attemptLogin() {
		if (mAuthTask != null) {
			return;
		}

		// Reset errors.
		mEmailView.setError(null);
		mPasswordView.setError(null);

		// Store values at the time of the login attempt.
		mEmail = mEmailView.getText().toString();
		mPassword = mPasswordView.getText().toString();

		boolean cancel = false;
		View focusView = null;

		// Check for a valid password.
		if (TextUtils.isEmpty(mPassword)) {
			mPasswordView.setError(getString(R.string.error_field_required));
			focusView = mPasswordView;
			cancel = true;
		}

		// Check for a valid email address.
		if (TextUtils.isEmpty(mEmail)) {
			mEmailView.setError(getString(R.string.error_field_required));
			focusView = mEmailView;
			cancel = true;
		}

		if (cancel) {
			// There was an error; don't attempt login and focus the first
			// form field with an error.
			focusView.requestFocus();
		} else {
			// Show a progress spinner, and kick off a background task to
			// perform the user login attempt.
			mLoginStatusMessageView.setText(R.string.login_progress_signing_in);
			showProgress(true);
			mAuthTask = new UserLoginTask();
			mAuthTask.execute(new LastPassImpl().getPasswordStoreBuilder(mEmail, mPassword, null));//TODO cache file...
		}
	}

	/**
	 * Shows the progress UI and hides the login form. TODO make this less crazy
	 */
	private void showProgress(final boolean show) {
			int shortAnimTime = getResources().getInteger(
					android.R.integer.config_shortAnimTime);

			mLoginStatusView.setVisibility(View.VISIBLE);
			mLoginStatusView.animate().setDuration(shortAnimTime)
					.alpha(show ? 1 : 0)
					.setListener(new AnimatorListenerAdapter() {
						@Override
						public void onAnimationEnd(Animator animation) {
							mLoginStatusView.setVisibility(show ? View.VISIBLE
									: View.GONE);
						}
					});

			mLoginFormView.setVisibility(View.VISIBLE);
			mLoginFormView.animate().setDuration(shortAnimTime)
					.alpha(show ? 0 : 1)
					.setListener(new AnimatorListenerAdapter() {
						@Override
						public void onAnimationEnd(Animator animation) {
							mLoginFormView.setVisibility(show ? View.GONE
									: View.VISIBLE);
						}
					});
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
			this(failureReason,null);
		}
	}
	
	private enum LoginFailureReason {
		FAIL,
		OTP
	}

	/**
	 * Represents an asynchronous login/registration task used to authenticate
	 * the user.
	 */
	public class UserLoginTask extends AsyncTask<PasswordStoreBuilder, Void, LoginResult> {
		@Override
		protected LoginResult doInBackground(PasswordStoreBuilder... params) {
			PasswordStoreBuilder passwordStoreBuilder = params[0];
			try {
				return new LoginResult(passwordStoreBuilder.getPasswordStore());
			} catch (GoogleAuthenticatorRequired authenticatorRequired) {
				return new LoginResult(LoginFailureReason.OTP);
			} catch (LastPassException failure) {
				return new LoginResult(LoginFailureReason.FAIL, failure.getMessage());
			}
		}

		@Override
		protected void onPostExecute(final LoginResult loginResult) {
			mAuthTask = null;
			showProgress(false);

			if (loginResult.passwordStore != null) {
				SoftKeyboard.setPasswordStore(loginResult.passwordStore);
				finish();
			} else if (loginResult.failureReason == LoginFailureReason.OTP) {
				//TODO
			} else {
				mPasswordView.setError(loginResult.reasonString);
				mPasswordView.requestFocus();
			}
		}

		@Override
		protected void onCancelled() {
			//TODO who calls me?
			mAuthTask = null;
			showProgress(false);
		}
	}
}
