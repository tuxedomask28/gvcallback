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

import android.app.Activity;
import android.app.NotificationManager;
import android.os.Bundle;

import com.bchalk.GVCallback.GVCallback;

/**
 * This activity cancels any ongoing call attempts.
 * 
 * Large portions of code used in this program are taken from Evan Charlton's program, GV. The program, GV, 
 * is protected under the Apache License 2.0. A copy of Apache License 2.0 can be found in LICENSE-2.0.txt.
 * 
 * @author Brandon Chalk
 */
public class CancelCallback extends Activity
{
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setVisible(false);
	}

	public void onStart()
	{
		super.onStart();
		OutgoingCallReceiver.cancel();
		GVCommunicator.getInstance(this).cancelCall();

		NotificationManager mgr = (NotificationManager) getSystemService(Activity.NOTIFICATION_SERVICE);
		if (mgr != null)
			mgr.cancel(GVCallback.Notification.CALL.hashCode());
	}
}
