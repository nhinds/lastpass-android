package com.nhinds.lastpass.android;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.app.ProgressDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.TextView;

import com.nhinds.lastpass.android.UserLoginTaskFactory.UserLoginListener;
import com.nhinds.lastpass.android.UserLoginTaskFactory.UserLoginResult;

/**
 * Activity which displays a login screen to the user.
 */
public class LoginActivity extends Activity implements UserLoginListener {
	private static final Logger LOGGER = LoggerFactory.getLogger(LoginActivity.class);
	
	public static final String CACHED_OTP_LOGIN = LoginActivity.class.getName()+"#CachedOtpLogin";
	public static final String ERROR_EXTRA_KEY = LoginActivity.class.getName()+"#Error";

	/**
	 * Keep track of the login task to ensure we can cancel it if requested.
	 */
	private UserLoginTaskFactory mAuthTaskFactory;

	// UI references.
	private EditText mEmailView;
	private EditText mPasswordView;
	private CheckBox mRememberEmailView;
	private CheckBox mRememberPasswordView;

	private EditText mOtpView;
	private CheckBox mTrustDeviceView;
	private EditText mTrustedDeviceLabelView;

	private Preferences preferences;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		this.preferences = new Preferences(this);

		setContentView(R.layout.activity_login);
		
		// Set up the login form.
		this.mEmailView = findTypedViewById(R.id.email);
		this.mPasswordView = findTypedViewById(R.id.password);
		this.mOtpView = findTypedViewById(R.id.otp);
		this.mTrustDeviceView = findTypedViewById(R.id.trust_device);
		this.mTrustedDeviceLabelView = findTypedViewById(R.id.trusted_device_label);
		this.mRememberEmailView = findTypedViewById(R.id.remember_email);
		this.mRememberPasswordView = findTypedViewById(R.id.remember_password);

		String rememberedEmail = this.preferences.getRememberedEmail();
		if (rememberedEmail != null) {
			this.mEmailView.setText(rememberedEmail);
			this.mRememberEmailView.setChecked(true);
			this.mRememberPasswordView.setEnabled(true);
			this.mPasswordView.requestFocus();
		}
		String rememberedPassword = this.preferences.getRememberedPassword();
		if (rememberedPassword != null) {
			this.mPasswordView.setText(rememberedPassword);
			this.mRememberPasswordView.setChecked(true);
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
		
		this.mRememberEmailView.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				LoginActivity.this.mRememberPasswordView.setEnabled(isChecked);
			}
		});
		
		// If we were launched with a "cached OTP login" intent, show the OTP form to login with the cached/remembered email and password
		if (CACHED_OTP_LOGIN.equals(getIntent().getAction()) && rememberedEmail != null && rememberedPassword != null) {
			createAuthTaskFactory(rememberedEmail, rememberedPassword);
			setState(FormState.OTP);
		} else {
			// Set the password error if one was specified in the intent
			final CharSequence errorFromIntent = getIntent().getCharSequenceExtra(ERROR_EXTRA_KEY);
			if (errorFromIntent != null) {
				this.mPasswordView.setError(errorFromIntent);
			}
		}
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
		if (this.mAuthTaskFactory != null) {
			return;
		}

		if (validateNotEmpty(this.mEmailView, this.mPasswordView)) {
			String email = this.mEmailView.getText().toString();
			String password = this.mPasswordView.getText().toString();

			boolean rememberEmail = this.mRememberEmailView.isChecked();
			boolean rememberPassword = rememberEmail && this.mRememberPasswordView.isChecked();
			this.preferences.setRememberedEmailAndPassword(rememberEmail ? email : null, rememberPassword ? password : null);

			// kick off a background task to perform the user login attempt.
			createAuthTaskFactory(email, password);
			this.mAuthTaskFactory.loginWithoutOtp();
		}
	}

	private void createAuthTaskFactory(final String email, final String password) {
		this.mAuthTaskFactory = UserLoginTaskFactory.create(email, password, this, this);
	}

	private void attemptOtpLogin() {
		if (this.mAuthTaskFactory != null) {
			throw new IllegalStateException("No login task factory found");
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

				this.mAuthTaskFactory.loginWithOtp(otp, trustLabel);
			}
		}
	}
	
	@Override
	public void loginCompleted(final UserLoginResult loginResult) {
		this.mAuthTaskFactory = null;
		if (loginResult.passwordStore != null) {
			SoftKeyboard.setPasswordStore(loginResult.passwordStore);
			finish();
		} else {
			switch (loginResult.failureReason) {
			case OTP:
				LOGGER.debug("OTP Required");
				setState(FormState.OTP);
				break;
			case FAIL:
			case CANCEL:
				setState(FormState.LOGIN);
				this.mPasswordView.setError(loginResult.reasonString);
			}
		}
	}
	
	@Override
	public void progressDialogCreated(ProgressDialog dialog) {
		// nothing to do
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
		LOGIN(R.id.login_form, R.id.password), OTP(R.id.login_otp_form, R.id.otp);

		public final int viewId;
		public final int afterLoginEditId;

		private FormState(final int viewId, final int afterLoginEditId) {
			this.viewId = viewId;
			this.afterLoginEditId = afterLoginEditId;
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
		findViewById(desiredState.afterLoginEditId).requestFocus();
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

	/** Helper method to avoid casts when looking up views */
	@SuppressWarnings("unchecked")
	private <V extends View> V findTypedViewById(final int id) {
		return (V) findViewById(id);
	}

	/** Convert a boolean into a visibility constant for {@link View#setVisibility(int)} */
	private static int toVisibility(final boolean visible) {
		return visible ? View.VISIBLE : View.GONE;
	}
}
