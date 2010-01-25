package com.bchalk.GVCallback.callback;

import com.bchalk.GVCallback.GVCallback;
import com.bchalk.GVCallback.SettingsProvider;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

public class GVPhoneStateListener extends PhoneStateListener
{
	private Context myContext;

	public GVPhoneStateListener(Context context) {
		myContext = context;
	}
	
	@Override
	public void onCallStateChanged(int state, String incomingNumber) {
		NotificationManager mgr = (NotificationManager) myContext.getSystemService(Activity.NOTIFICATION_SERVICE);
		
		switch (state)
		{
		// TODO possibly implement this so that it doesn't cancel the waiting msg
		case TelephonyManager.CALL_STATE_IDLE:
			if (mgr != null)
				mgr.cancel(GVCallback.Notification.CALL.hashCode());
			break;
			
		case TelephonyManager.CALL_STATE_RINGING:
			if (mgr != null)
				mgr.cancel(GVCallback.Notification.CALL.hashCode());

			SettingsProvider settings = SettingsProvider.getInstance(myContext);
			String gv = settings.getGVNum();
			
			String in = incomingNumber.replaceAll("[^0-9]", "");
			if (in.length() < 10 || gv.length() < 10) {
				return;
			}
			
			if (in.charAt(0) == '1')
				in = in.substring(1);
			
			if (gv.charAt(0) == '1')
				gv = gv.substring(1);
			
			if (gv.endsWith(in) || in.endsWith(gv)) {
				mgr.notify(GVCallback.Notification.CALL.hashCode(), OutgoingCallReceiver.buildOngoing(myContext, "Connected by Google Voice", "Connected by Google Voice", "You are connected through the Google Voice service"));
			}
			break;
		}
	}
}