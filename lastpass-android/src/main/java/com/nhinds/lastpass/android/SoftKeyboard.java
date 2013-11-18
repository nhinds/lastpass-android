package com.nhinds.lastpass.android;

import org.apache.commons.lang.StringUtils;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.inputmethodservice.InputMethodService;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

import com.nhinds.lastpass.PasswordInfo;
import com.nhinds.lastpass.PasswordStore;

public class SoftKeyboard extends InputMethodService {
	private static final int AFTER_INSERTED_TEXT = 1;

	private InputMethodManager mInputMethodManager;

	private View mInputView;

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
	}

	@Override
	public View onCreateInputView() {
		// This is freaking ugly, but the only way I can see to make the code
		// wait until its input token is valid...
		this.mInputView = new View(this);
		this.mInputView.post(new Runnable() {
			@Override
			public void run() {
				Log.i(getPackageName(), "Bing");
				bing();
			}
		});
		return this.mInputView;
	}

	@Override
	public void onStartInputView(EditorInfo info, boolean restarting) {
		if (getPackageName().equals(getCurrentInputEditorInfo().packageName)) {
			Log.i(getPackageName(), "Run away away");
			switchToLastInputMethod();
		} else {
			Log.i(getPackageName(), "This input seems to be fine");
		}
	}

	private void bing() {
		if (passwordStore == null) {
			startActivity(new Intent(this, LoginActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
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
