/*  This file is part of GV Callback.

    GV Callback is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    GV Callback is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with GV Callback.  If not, see <http://www.gnu.org/licenses/>.
*/
package com.bchalk.GVCallback;

import com.bchalk.GVCallback.callback.GVCommunicator;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.NotificationManager;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * This program initiates a callback from Google voice when calls are sent.
 * 
 * Large portions of code used in this program are taken from Evan Charlton's program, GV. The program, GV, 
 * is protected under the Apache License 2.0. A copy of Apache License 2.0 can be found in LICENSE-2.0.txt.
 * 
 * @author Brandon Chalk
 */
public class GVCallback extends Activity
{
	/**
	 * List of notifications used by the GV Callback program
	 */
	public enum Notification { CALL } //TODO add notifier for settings saved
	private enum Dialogs { ABOUT, BAD_LOGIN, BAD_CALLBACK, SUCCESS }
	private enum Menus { ABOUT }
	private enum SavedStateInfo { PROGRESS_MESSAGE, PROGRESS_OVERLAY, USE_GV, CALLBACK_NUM, USERNAME, PASSWORD }
	
	private CheckBox myUseGVCallback;
	private EditText myGVCallbackNum;
	private EditText myGVUsername;
	private EditText myGVPassword;
	
	private Button myCancelButton;
	private Button myFinishButton;
	
	private LinearLayout myBlockingOverlay;
	private TextView myProgressMessage;
	private GVCommunicator myComm;
	private SettingsProvider mySettings;
	private LoginTask myTask;
	
	/**
	 * Called when the activity is first created.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		// Kill any remaining notifications
		NotificationManager notificationMgr = (NotificationManager) this
				.getSystemService(Activity.NOTIFICATION_SERVICE);
		if (notificationMgr != null)
			notificationMgr.cancel(Notification.CALL.hashCode());
		
		// Set up initial layout
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		setContentView(R.layout.main);
		
		// Set up provider instances
		mySettings = SettingsProvider.getInstance(this);
		myComm = GVCommunicator.getInstance(this);
		myTask = (LoginTask) getLastNonConfigurationInstance();
		if (myTask != null) {
			myTask.activity = this;
		}

		// Set up settings ui component variables
		myBlockingOverlay = (LinearLayout) findViewById(R.id.progress_overlay);
		myProgressMessage = (TextView) findViewById(R.id.progress_message);
		myUseGVCallback = (CheckBox) findViewById(R.id.use_gv_callback);
		myGVCallbackNum = (EditText) findViewById(R.id.callback_number);
		myGVUsername = (EditText) findViewById(R.id.username);
		myGVPassword = (EditText) findViewById(R.id.password);
		myCancelButton = (Button) findViewById(R.id.cancel);
		myFinishButton = (Button) findViewById(R.id.finish);
		
		// Populate fields
		if (savedInstanceState != null) {
			myUseGVCallback.setChecked(savedInstanceState.getBoolean(SavedStateInfo.USE_GV.toString()));
			myGVCallbackNum.setText(savedInstanceState.getString(SavedStateInfo.CALLBACK_NUM.toString()));
			myGVUsername.setText(savedInstanceState.getString(SavedStateInfo.USERNAME.toString()));
			myGVPassword.setText(savedInstanceState.getString(SavedStateInfo.PASSWORD.toString()));
			
			if (myProgressMessage != null) {
				myProgressMessage.setText(savedInstanceState.getString(SavedStateInfo.PROGRESS_MESSAGE.toString()));
			}
			if (myBlockingOverlay != null) {
				myBlockingOverlay.setVisibility(savedInstanceState.getInt(SavedStateInfo.PROGRESS_OVERLAY.toString()));
			}
		} 
		else
		{
			myUseGVCallback.setChecked(mySettings.getUseCallback());
			myGVCallbackNum.setText(mySettings.getGVCallbackNum());
			myGVUsername.setText(mySettings.getGVUsername());
			myGVPassword.setText(mySettings.getGVPassword());
		}
		
		// Make sure all of our buttons are enabled
		myUseGVCallback.setEnabled(true);
		myGVCallbackNum.setEnabled(true);
		myGVUsername.setEnabled(true);
		myGVPassword.setEnabled(true);
		myFinishButton.setEnabled(true);
		
		// Set up button handlers
		myCancelButton.setOnClickListener(new View.OnClickListener()
		{
			public void onClick(View v)
			{
				finish();
			}
		});
		myFinishButton.setOnClickListener(new View.OnClickListener()
		{
			public void onClick(View v)
			{
				// Freeze fields/buttons
				myUseGVCallback.setEnabled(false);
				myGVCallbackNum.setEnabled(false);
				myGVUsername.setEnabled(false);
				myGVPassword.setEnabled(false);
				myFinishButton.setEnabled(false);
				
				// Set callback checkbox (no validation necessary)
				mySettings.setUseCallback(myUseGVCallback.isChecked());
				
				// Validate number (if it is true it will be saved in the checkNumber method)
				if (checkNumber())
					checkLogin(); // Likewise checkLogin will save login info
				else
				{
					showDialog(Dialogs.BAD_CALLBACK.hashCode());
					myUseGVCallback.setEnabled(true);
					myGVCallbackNum.setEnabled(true);
					myGVUsername.setEnabled(true);
					myGVPassword.setEnabled(true);
					myFinishButton.setEnabled(true);
				}
			}
		});
		
	}
	
	/**
	 * Helper method for finish button handler that checks and saves callback number and returns success
	 * @return
	 */
	private boolean checkNumber()
	{
		String number = GVCommunicator.normalizeNumber(myGVCallbackNum.getText().toString());
		
		if ((number.length() == 10 && number.charAt(0) != '1') ||
				(number.length() == 11 && number.charAt(0) == '1'))
		{
			mySettings.setGVCallbackNum(number);
			return true;
		}
		
		return false;
	}
	
	/**
	 * Launch the login task
	 */
	private void checkLogin()
	{
		myTask = new LoginTask();
		myTask.activity = this;
		myTask.execute(getText(myGVUsername), getText(myGVPassword));
	}
	
	/**
	 * Helper method for check login
	 * @param e
	 * @return
	 */
	private String getText(EditText e) {
		return e.getText().toString().trim();
	}
	
	/**
	 * Save all instance data
	 */
	@Override
	protected void onSaveInstanceState(Bundle icicle) {
		super.onSaveInstanceState(icicle);
		if (myProgressMessage != null) {
			icicle.putString(SavedStateInfo.PROGRESS_MESSAGE.toString(), myProgressMessage.getText().toString());
			icicle.putInt(SavedStateInfo.PROGRESS_OVERLAY.toString(), myBlockingOverlay.getVisibility());
			icicle.putBoolean(SavedStateInfo.USE_GV.toString(), myUseGVCallback.isChecked());
			icicle.putString(SavedStateInfo.CALLBACK_NUM.toString(), myGVCallbackNum.getText().toString());
			icicle.putString(SavedStateInfo.USERNAME.toString(), myGVUsername.getText().toString());
			icicle.putString(SavedStateInfo.PASSWORD.toString(), myGVPassword.getText().toString());
		}
	}

	/**
	 * Helper function for the login task, toggles display of the progress dialog
	 * @param visible
	 */
	private void showProgressOverlay(boolean visible) {
		if (visible) {
			myBlockingOverlay.setVisibility(View.VISIBLE);
		} else {
			myBlockingOverlay.setVisibility(View.GONE);
		}
	}

	/**
	 * Helper function for the login task, shows text in our dialog
	 * @param msg
	 */
	private void setProgressMessage(String msg) {
		myProgressMessage.setText(msg);
	}

	/**
	 * Helper function for the login task, shows text in our dialog from strings xml
	 * @param stringId
	 */
	private void setProgressMessage(int stringId) {
		setProgressMessage(getString(stringId));
	}
	
	@Override
	protected Dialog onCreateDialog(int dialog) {
		if (dialog == Dialogs.ABOUT.hashCode())
		{ // TODO make neater, get rid of spaces after links
			final TextView message = new TextView(this);
			final SpannableString s = new SpannableString(getString(R.string.about_message,getString(R.string.version)));
			Linkify.addLinks(s, Linkify.WEB_URLS);
			message.setText(s);
			message.setMovementMethod(LinkMovementMethod.getInstance());
			
			return new AlertDialog.Builder(this).setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					removeDialog(Dialogs.ABOUT.hashCode());
				}
			}).setTitle(R.string.about_title).setView(message).create();
		}
			
		if (dialog == Dialogs.BAD_LOGIN.hashCode())
			return new AlertDialog.Builder(this).setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						removeDialog(Dialogs.BAD_LOGIN.hashCode());
					}
				}).setTitle(R.string.login_failed_title).setMessage(myComm.getError()).create();

		if (dialog == Dialogs.BAD_CALLBACK.hashCode())
			return new AlertDialog.Builder(this).setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					removeDialog(Dialogs.BAD_LOGIN.hashCode());
				}
			}).setTitle(R.string.bad_callback_title).setMessage(R.string.bad_callback_message).create();
		
		if (dialog == Dialogs.SUCCESS.hashCode())
			return new AlertDialog.Builder(this).setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					removeDialog(Dialogs.SUCCESS.hashCode());
					finish();
				}
			}).setTitle(R.string.success_title).setMessage(R.string.success_message).create();
			
		return null;
	}
	
	@Override
	public Object onRetainNonConfigurationInstance() {
		return myTask;
	}
	
	public boolean onCreateOptionsMenu(Menu menu) {
	    menu.add(0, Menus.ABOUT.hashCode(), 0, "About").setIcon(android.R.drawable.ic_dialog_info);
	    return true;
	}

	public boolean onOptionsItemSelected(MenuItem item) {
	    if (item.getItemId() == Menus.ABOUT.hashCode())
	        showDialog(Dialogs.ABOUT.hashCode());
	    
	    return false;
	}
	
	/**
	 * AsyncTask that checks login info and saves/finishes or errors/cancels. It also updates the progress bar.
	 *
	 */
	private static class LoginTask extends AsyncTask<String, Integer, Boolean> {
		private Boolean myCancelled = false;
		private GVCallback activity = null;
		private SettingsProvider mySettings;
		private GVCommunicator myComm;

		@Override
		protected void onPreExecute() {
			activity.setProgressBarIndeterminateVisibility(true);
			activity.setProgressMessage(R.string.setup_testing_login);
			activity.showProgressOverlay(true);
			myCancelled = false;
			mySettings = activity.mySettings;
			myComm = activity.myComm;
		}

		@Override
		protected void onCancelled() {
			myCancelled = true;
		}

		@Override
		protected Boolean doInBackground(String... params) {
			return myComm.login(params[0], params[1]);
		}

		@Override
		protected void onPostExecute(Boolean result) {
			if (!myCancelled) {
				activity.showProgressOverlay(false);
				if (result) {
					mySettings.setGVUsername(activity.myGVUsername.getText().toString().trim());
					mySettings.setGVPassword(activity.myGVPassword.getText().toString().trim());
					mySettings.setGVNum(myComm.getGVNumber().trim());
					activity.showDialog(Dialogs.SUCCESS.hashCode());
				} else {
					activity.setProgressBarIndeterminateVisibility(false);
					activity.showDialog(Dialogs.BAD_LOGIN.hashCode());
					activity.myUseGVCallback.setEnabled(true);
					activity.myGVCallbackNum.setEnabled(true);
					activity.myGVUsername.setEnabled(true);
					activity.myGVPassword.setEnabled(true);
					activity.myFinishButton.setEnabled(true);
				}
			}
		}
	}
}