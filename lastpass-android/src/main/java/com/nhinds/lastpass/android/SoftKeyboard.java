package com.nhinds.lastpass.android;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

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
import android.widget.Toast;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.nhinds.lastpass.PasswordInfo;
import com.nhinds.lastpass.PasswordStore;

public class SoftKeyboard extends InputMethodService {
	private static final int AFTER_INSERTED_TEXT = 1;

	private static final Comparator<PasswordInfo> BY_NAME = new Comparator<PasswordInfo>() {

		@Override
		public int compare(PasswordInfo lhs, PasswordInfo rhs) {
			return lhs.getName().compareTo(rhs.getName());
		}
	};

	private InputMethodManager mInputMethodManager;

	private View mInputView;

	private static PasswordStore passwordStore;

	static void setPasswordStore(PasswordStore passwordStore) {
		// TODO This sucks, why is this so difficult to accomplish in android?
		SoftKeyboard.passwordStore = passwordStore;
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
			final List<PasswordInfo> passwords = new ArrayList<PasswordInfo>(passwordStore.getPasswords());
			Collections.sort(passwords, BY_NAME);

			AlertDialog dialog = new AlertDialog.Builder(this).setItems(convert(passwords), new OnClickListener() {

				@Override
				public void onClick(DialogInterface dialog, int which) {
					PasswordInfo passwordInfo = passwords.get(which);
					Toast.makeText(SoftKeyboard.this, passwordInfo.getUrl(), Toast.LENGTH_LONG).show();
					boolean password = isPassword(getCurrentInputEditorInfo().inputType);

					final String text = password ? passwordInfo.getPassword() : passwordInfo.getUsername();
					getCurrentInputConnection().commitText(text, AFTER_INSERTED_TEXT);
				}
			}).setOnDismissListener(new OnDismissListener() {

				@Override
				public void onDismiss(DialogInterface dialog) {
					switchToLastInputMethod();
				}
			}).create();
			makeDialogWork(dialog);
			dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
			dialog.show();
		}
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

	private static String[] convert(Collection<? extends PasswordInfo> passwords) {
		return Collections2.transform(passwords, new Function<PasswordInfo, String>() {

			@Override
			public String apply(PasswordInfo passwordInfo) {
				return passwordInfo.getName();
			}
		}).toArray(new String[passwords.size()]);
	}

	private void switchToLastInputMethod() {
		this.mInputMethodManager.switchToLastInputMethod(getWindow().getWindow().getAttributes().token);
	}
}
