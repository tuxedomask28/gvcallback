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
package com.bchalk.GVCallback.callback;

import com.bchalk.GVCallback.GVCallback;
import com.bchalk.GVCallback.R;
import com.bchalk.GVCallback.SettingsProvider;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

/**
 * Outgoing Call Receiver extends the native Broadcast Receiver to catch all
 * outgoing calls. We catch the call, check the number and instead of letting
 * the phone call it, we pass it off to GV to give us a callback.
 * 
 * Large portions of code used in this program are taken from Evan Charlton's
 * program, GV. The program, GV, is protected under the Apache License 2.0. A
 * copy of Apache License 2.0 can be found in LICENSE-2.0.txt.
 * 
 * @author Brandon Chalk
 */
public class OutgoingCallReceiver extends BroadcastReceiver
{
// TODO: CLEANUP THIS WHOLE FILE
	private static CallTask myTask;

	private Context myContext;
	private SettingsProvider mySettings;

	/**
	 * Override the onReceive method of the BroadcastReceiver to include our GV
	 * Callback code.
	 */
	@Override
	public void onReceive(Context context, Intent outgoing)
	{
		// Set up settings, and don't do anything if not enabled
		mySettings = SettingsProvider.getInstance(context);

		if (!mySettings.getUseCallback())
			return;

		// don't do anything because Google Voice doesn't support numbers
		// less than 10 digits anyway. Also, it might be an emergency.
		String number = outgoing.getStringExtra(Intent.EXTRA_PHONE_NUMBER).replaceAll("[^0-9]", "");
		if (number.length() < 10)
		{
			return;
		}

		// don't mess with voicemail numbers
		TelephonyManager tmgr = (TelephonyManager) context.getSystemService(Activity.TELEPHONY_SERVICE);
		String voicemailNumber = tmgr.getVoiceMailNumber();
		if (voicemailNumber != null && voicemailNumber.equals(number))
		{
			return;
		}

		// TODO don't try and call our own GV number from GV

		// Set up GV call
		GVCommunicator.insertPlaceholderCall(context.getContentResolver(), number);

		myContext = context;
		cancel();
		myTask = new CallTask();
		myTask.execute(number);

		GVPhoneStateListener phoneListener = new GVPhoneStateListener(context);
		
		TelephonyManager telephony = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
		telephony.listen(phoneListener, PhoneStateListener.LISTEN_CALL_STATE);
		
		setResultData(null);
	}

	private void showOngoing(String tickerText, String title, String description)
	{
		NotificationManager mgr = (NotificationManager) myContext.getSystemService(Activity.NOTIFICATION_SERVICE);
		if (mgr != null)
		{
			mgr.notify(GVCallback.Notification.CALL.hashCode(), OutgoingCallReceiver.buildOngoing(myContext,
					tickerText, title, description));
		}
	}

	public static Notification buildOngoing(Context context, String tickerText, String title, String description)
	{
		Notification calling = new Notification(R.drawable.contacticon, tickerText, System.currentTimeMillis());
		Intent intent = new Intent(context, com.bchalk.GVCallback.callback.CancelCallback.class);
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		PendingIntent settings = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
		calling.flags = Notification.FLAG_ONGOING_EVENT | Notification.FLAG_NO_CLEAR;
		calling.setLatestEventInfo(context, title, description, settings);
		calling.vibrate = new long[] { 250, 250 };
		return calling;
	}

	private void clear(int id)
	{
		NotificationManager mgr = (NotificationManager) myContext.getSystemService(Activity.NOTIFICATION_SERVICE);
		if (mgr != null)
		{
			mgr.cancel(id);
		}
	}

	private void showError(String tickerText, String title, String description)
	{
		Notification callFailed = new Notification(R.drawable.statusicon_error, tickerText, System.currentTimeMillis());
		Intent intent = new Intent(myContext, CancelCallback.class);
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		PendingIntent settings = PendingIntent.getActivity(myContext, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
		callFailed.flags = Notification.FLAG_ONLY_ALERT_ONCE | Notification.FLAG_AUTO_CANCEL;
		callFailed.ledARGB = 0xFFFF0000;
		callFailed.ledOffMS = 100;
		callFailed.ledOnMS = 100;
		callFailed.vibrate = new long[] { 250, 250, 250, 250, 250, 250 };
		callFailed.setLatestEventInfo(myContext, title, description, settings);

		NotificationManager mgr = (NotificationManager) myContext.getSystemService(Activity.NOTIFICATION_SERVICE);
		if (mgr != null)
		{
			mgr.notify(GVCallback.Notification.CALL.hashCode(), callFailed);
		}
	}

	public static void cancel()
	{
		if (myTask != null)
		{
			myTask.cancel(true);
		}
	}

	private class CallTask extends AsyncTask<String, Integer, Integer>
	{
		private static final int PROGRESS_LOGGING_IN = 1;
		private static final int PROGRESS_LOGIN_FAILED = 2;
		private static final int PROGRESS_REGISTERING = 3;
		private static final int PROGRESS_WAITING = 4;
		private static final int PROGRESS_CALL_FAILED = 5;
		private static final int PROGRESS_CANCELLED = 6;

		private boolean myIsCancelled = false;

		@Override
		protected void onPreExecute()
		{
			myIsCancelled = false;
		}

		@Override
		protected void onCancelled()
		{
			myIsCancelled = true;
			clear(GVCallback.Notification.CALL.hashCode());
		}

		@Override
		protected Integer doInBackground(String... params)
		{
			GVCommunicator comm = GVCommunicator.getInstance(myContext);
			// first we have to log in
			if (!comm.isLoggedIn())
			{
				publishProgress(PROGRESS_LOGGING_IN);
				if (!comm.login())
				{
					return PROGRESS_LOGIN_FAILED;
				}
			}
			if (!myIsCancelled)
			{
				// and then we have to try and register the call
				publishProgress(PROGRESS_REGISTERING);
				if (!comm.connect(params[0]))
				{
					// damn it, something failed. Abort.
					return PROGRESS_CALL_FAILED;
				}
				return PROGRESS_WAITING;
			}
			return PROGRESS_CANCELLED;
		}

		@Override
		protected void onProgressUpdate(Integer... updates)
		{
			show(updates[0]);
		}

		private void show(int which)
		{
			clear(GVCallback.Notification.CALL.hashCode());
			switch (which)
			{
			case PROGRESS_LOGGING_IN:
				showOngoing("Logging in...", "Logging in...", "Logging in to Google Voice");
				break;
			case PROGRESS_LOGIN_FAILED:
				showError("Login failed!", "Login failed!", "You could not be logged into Google Voice!");
				break;
			case PROGRESS_REGISTERING:
				showOngoing("Placing call...", "Placing call...", "Select to cancel call");
				break;
			case PROGRESS_WAITING:
				showOngoing("Waiting for call...", "Waiting for call...", "Select to cancel call");
				break;
			case PROGRESS_CALL_FAILED:
				showError("Call failed!", "Call failed!", "The call could not be completed!");
				break;
			}
		}

		@Override
		protected void onPostExecute(Integer result)
		{
			show(result);
		}
	}
}