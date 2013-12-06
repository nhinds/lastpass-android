package com.nhinds.lastpass.android;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.inputmethodservice.InputMethodService;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

import com.nhinds.lastpass.PasswordInfo;
import com.nhinds.lastpass.PasswordStore;
import com.nhinds.lastpass.android.UserLoginTaskFactory.LoginFailureReason;
import com.nhinds.lastpass.android.UserLoginTaskFactory.UserLoginListener;
import com.nhinds.lastpass.android.UserLoginTaskFactory.UserLoginResult;

public class SoftKeyboard extends InputMethodService {
	private static final Logger LOGGER = LoggerFactory.getLogger(SoftKeyboard.class);
	
	private static final int AFTER_INSERTED_TEXT = 1;

	private InputMethodManager mInputMethodManager;

	private View mInputView;
	private Preferences preferences;

	private static PasswordStore passwordStore;

	static void setPasswordStore(PasswordStore passwordStore) {
		// TODO This sucks, why is this so difficult to accomplish in android?
		SoftKeyboard.passwordStore = passwordStore;
	}

	static void logout() {
		assert passwordStore != null;
		// TODO call a method to kill the session once this is implemented in lastpass-java
		passwordStore = null;
	}

	@Override
	public void onCreate() {
		super.onCreate();
		this.mInputMethodManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
		this.preferences = new Preferences(this);
	}

	@Override
	public View onCreateInputView() {
		// This is freaking ugly, but the only way I can see to make the code
		// wait until its input token is valid...
		this.mInputView = new View(this);
		this.mInputView.post(new Runnable() {
			@Override
			public void run() {
				LOGGER.trace("Input view token is now valid");
				bing();
			}
		});
		return this.mInputView;
	}

	@Override
	public void onStartInputView(EditorInfo info, boolean restarting) {
		final String applicationPackage = getPackageName();
		final String editorPackage = getCurrentInputEditorInfo().packageName;
		if (applicationPackage.equals(editorPackage)) {
			LOGGER.debug("Detected an editor from this application's package {}, switching to last input method", applicationPackage);
			switchToLastInputMethod();
		} else {
			LOGGER.debug("This editor seems to be fine: {}", editorPackage);
		}
	}

	private void bing() {
		if (passwordStore == null) {
			String rememberedEmail = preferences.getRememberedEmail();
			String rememberedPassword = preferences.getRememberedPassword();
			if (rememberedEmail != null && rememberedPassword != null) {
				UserLoginTaskFactory.create(rememberedEmail, rememberedPassword, getApplicationContext(), new UserLoginListener() {
					
					@Override
					public void loginCompleted(UserLoginResult result) {
						if (result.passwordStore != null) {
							setPasswordStore(result.passwordStore);
							bing();
						} else {
							LOGGER.debug("Error logging in: {} ({})", result.failureReason, result.reasonString);
							if (result.failureReason == LoginFailureReason.OTP)
								switchToLoginActivity(LoginActivity.CACHED_OTP_LOGIN, null);
							else
								switchToLoginActivity(null, result.reasonString);
						}
					}
					
					@Override
					public void progressDialogCreated(ProgressDialog dialog) {
						makeDialogWork(dialog);
					}
				}).loginWithoutOtp();
			} else {
				switchToLoginActivity(null, null);
			}
		} else {
			final String hostname = getHostname();
			final PasswordInfoListAdapter listAdapter = new PasswordInfoListAdapter(this, passwordStore.getPasswords(),
					passwordStore.getPasswordsByHostname(hostname));

			final AlertDialog dialog = new AlertDialog.Builder(this).setAdapter(listAdapter, new OnClickListener() {

				@Override
				public void onClick(DialogInterface dialog, int which) {
					PasswordInfo passwordInfo = listAdapter.getItem(which);
					final String text = isPasswordInput() ? passwordInfo.getPassword() : passwordInfo.getUsername();
					getCurrentInputConnection().commitText(text, AFTER_INSERTED_TEXT);
				}
			}).setOnDismissListener(new OnDismissListener() {

				@Override
				public void onDismiss(DialogInterface dialog) {
					switchToLastInputMethod();
				}
			}).create();
			dialog.setCustomTitle(getTitleBar(dialog));
			makeDialogWork(dialog);
			dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
			dialog.show();
		}
	}

	private void switchToLoginActivity(String action, String errorString) {
		startActivity(new Intent(action, null, this, LoginActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(LoginActivity.ERROR_EXTRA_KEY, errorString));
	}
	
	private View getTitleBar(final AlertDialog dialog) {
		final View titleBar = getLayoutInflater().inflate(R.layout.password_titlebar, null);
		if (!isPasswordInput()) {
			final TextView titleText = (TextView) titleBar.findViewById(R.id.popup_title);
			titleText.setText(R.string.choose_username);
		}

		final View logoutButton = titleBar.findViewById(R.id.logout_button);
		logoutButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				logout();
				SoftKeyboard.this.preferences.setRememberedPassword(null);
				dialog.cancel();
			}
		});
		return titleBar;
	}

	private String getHostname() {
		return StringUtils.reverseDelimited(getCurrentInputEditorInfo().packageName, '.');
	}

	private void makeDialogWork(AlertDialog dialog) {
		// http://stackoverflow.com/questions/5698700/how-to-launch-a-popupwindow-or-dialog-from-an-input-method-service
		LayoutParams attributes = dialog.getWindow().getAttributes();
		attributes.token = this.mInputView.getWindowToken();
		attributes.type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;
		dialog.getWindow().setAttributes(attributes);
	}

	protected static boolean isPassword(int inputType) {
		int variation = inputType & InputType.TYPE_MASK_VARIATION;
		return variation == InputType.TYPE_TEXT_VARIATION_PASSWORD || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
				|| variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD;
	}

	private void switchToLastInputMethod() {
		this.mInputMethodManager.switchToLastInputMethod(getWindow().getWindow().getAttributes().token);
	}

	private boolean isPasswordInput() {
		return isPassword(getCurrentInputEditorInfo().inputType);
	}
}
