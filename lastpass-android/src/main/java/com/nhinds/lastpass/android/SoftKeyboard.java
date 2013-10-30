/*
 * Copyright (C) 2008-2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nhinds.lastpass.android;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.DialogInterface.OnShowListener;
import android.content.Intent;
import android.inputmethodservice.InputMethodService;
import android.os.IBinder;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
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

/**
 * Example of writing an input method for a soft keyboard.  This code is
 * focused on simplicity over completeness, so it should in no way be considered
 * to be a complete soft keyboard implementation.  Its purpose is to provide
 * a basic example for how you would get started writing an input method, to
 * be fleshed out as appropriate.
 */
public class SoftKeyboard extends InputMethodService {
    private static final int AFTER_INSERTED_TEXT = 1;

	static final boolean DEBUG = false;
    
    /**
     * This boolean indicates the optional example code for performing
     * processing of hard keys in addition to regular text generation
     * from on-screen interaction.  It would be used for input methods that
     * perform language translations (such as converting text entered on 
     * a QWERTY keyboard to Chinese), but may not be used for input methods
     * that are primarily intended to be used for on-screen text entry.
     */
    static final boolean PROCESS_HARD_KEYS = true;

	private static final Comparator<PasswordInfo> BY_NAME = new Comparator<PasswordInfo>() {

		@Override
		public int compare(PasswordInfo lhs, PasswordInfo rhs) {
			return lhs.getName().compareTo(rhs.getName());
		}
	};

    private InputMethodManager mInputMethodManager;

    private View mInputView;
    private IBinder token;

	private static PasswordStore passwordStore;

	static void setPasswordStore(PasswordStore passwordStore) {
		// TODO This fucking sucks, why is this so difficult to accomplish in android? 
		SoftKeyboard.passwordStore = passwordStore;
	}
    
    /**
     * Main initialization of the input method component.  Be sure to call
     * to super class.
     */
    @Override public void onCreate() {
        super.onCreate();
        mInputMethodManager = (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
    }
    
    /**
     * This is the point where you can do all of your UI initialization.  It
     * is called after creation and any configuration change.
     */
    @Override public void onInitializeInterface() {
    }
    
    @Override
    public AbstractInputMethodImpl onCreateInputMethodInterface() {
    	return new InputMethodImpl() {
    		@Override
    		public void attachToken(IBinder token) {
    			super.attachToken(token);
    			if (SoftKeyboard.this.token == null) {
    				SoftKeyboard.this.token = token;
    			}
    		}
    	};
    }
    
    @Override
    public View onCreateInputView() {
    	// This is freaking ugly, but the only way I can see to make the code wait until its input token is valid...
    	mInputView = new View(this);
    	mInputView.post(new Runnable() {
			@Override
			public void run() {
//				if (getPackageName().equals(getCurrentInputEditorInfo().packageName)) {
//					Log.i(getPackageName(), "Run away");
//					switchToLastInputMethod();
//				} else {
					Log.i(getPackageName(), "Bing");
					bing();
//				}
			}
		});
		return mInputView;
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
    

    /**
     * This is the main point where we do our initialization of the input method
     * to begin operating on an application.  At this point we have been
     * bound to the client, and are now receiving all of the detailed information
     * about the target of our edits.
     * <p>
     * {@inheritDoc}
     */
    @Override public void onStartInput(EditorInfo attribute, boolean restarting) {
    }

	private void bing() {
		if (passwordStore == null) {
			startActivity(new Intent(this, LoginActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
		} else {
			//		ArrayAdapter<String> itemAdaptor = new ArrayAdapter<String>(this, R.layout.apparentlyweneedlotsofxml, Arrays.asList("I","like","candy"));
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
		attributes.token = mInputView.getWindowToken();
		attributes.type=WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;
		dialog.getWindow().setAttributes(attributes);
	}

	protected static boolean isPassword(int inputType) {
		int variation = inputType & InputType.TYPE_MASK_VARIATION;
		return variation == InputType.TYPE_TEXT_VARIATION_PASSWORD
				|| variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
				|| variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD;
	}

    private static String[] convert(Collection<? extends PasswordInfo> passwords) {
		return Collections2.transform(passwords,
				new Function<PasswordInfo, String>() {

					@Override
					public String apply(PasswordInfo passwordInfo) {
						return passwordInfo.getName();
					}
				}).toArray(new String[passwords.size()]);
	}


	/**
     * This is called when the user is done editing a field.  We can use
     * this to reset our state.
     */
    @Override public void onFinishInput() {
    }

    private void switchToLastInputMethod() {
		mInputMethodManager.switchToLastInputMethod(getWindow().getWindow().getAttributes().token);
	}
}
