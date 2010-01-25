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

import android.content.Context;
import android.content.SharedPreferences;
import android.telephony.TelephonyManager;

/**
 * This class handles the getting and setting of the variables used in the application
 * 
 * Large portions of code used in this program are taken from Evan Charlton's program, GV. The program, GV, 
 * is protected under the Apache License 2.0. A copy of Apache License 2.0 can be found in LICENSE-2.0.txt.
 * @author Brandon Chalk
 */
public class SettingsProvider
{
	private static final String PREFS_NAME = "com.bchalk.GVCallback_prefs";

	private static final String USE_GVCALLBACK = "use_gvcallback";
	private static final Boolean USE_GVCALLBACK_DEFAULT = true;

	private static final String GV_NUM = "gv_num";
	private static final String GVCALLBACK_NUM = "gvcallback_num";
	private static final String GV_USERNAME = "gv_username";
	private static final String GV_PASSWORD =  "gv_password";
	
	private static SettingsProvider instance = null;
	private SharedPreferences mySettings;
	private Context myContext;
	
	private SettingsProvider(Context context)
	{
		myContext = context;
		mySettings = myContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
	}
	
	/**
	 * Get an instance of the settings provider.
	 * @param context
	 * @return
	 */
	public static SettingsProvider getInstance(Context context) 
	{
		if (instance == null)
			instance = new SettingsProvider(context);
		
		return instance;
	}
	
	/**
	 * Returns boolean callback
	 * @return
	 */
	public boolean getUseCallback()
	{
		boolean test = mySettings.getBoolean(USE_GVCALLBACK, USE_GVCALLBACK_DEFAULT);
		return test;
	}

	/**
	 * Returns GV num
	 * @return
	 */
	public String getGVNum()
	{
		return mySettings.getString(GV_NUM, "");
	}
	/**
	 * Returns callback num
	 * @return
	 */
	public String getGVCallbackNum()
	{
		String defaultStr = ((TelephonyManager) myContext.getSystemService(Context.TELEPHONY_SERVICE)).getLine1Number();
		return mySettings.getString(GVCALLBACK_NUM, defaultStr);
	}
	
	/**
	 * Returns GV Username
	 * @return
	 */
	public String getGVUsername()
	{
		return mySettings.getString(GV_USERNAME, "");
	}
	
	/**
	 * Returns GV Password
	 * @return
	 */
	public String getGVPassword()
	{
		return mySettings.getString(GV_PASSWORD, "");
	}
	
	/**
	 * Sets the GV Callback boolean, returns success value
	 * @param value
	 * @return
	 */
	public boolean setUseCallback(boolean value)
	{
		return mySettings.edit().putBoolean(USE_GVCALLBACK, value).commit();
	}

	/**
	 * Sets the GV number, returns success value
	 * @param value
	 * @return
	 */
	public boolean setGVNum(String value)
	{
		return mySettings.edit().putString(GV_NUM, value).commit();
	}
	/**
	 * Sets the GV Callback number, returns success value
	 * @param value
	 * @return
	 */
	public boolean setGVCallbackNum(String value)
	{
		return mySettings.edit().putString(GVCALLBACK_NUM, value).commit();
	}
	
	/**
	 * Sets the GV username, returns success value
	 * @param value
	 * @return
	 */
	public boolean setGVUsername(String value)
	{
		return mySettings.edit().putString(GV_USERNAME, value).commit();
	}
	
	/**
	 * Sets the GV password, returns success value
	 * @param value
	 * @return
	 */
	public boolean setGVPassword(String value)
	{
		return mySettings.edit().putString(GV_PASSWORD, value).commit();
	}
}
