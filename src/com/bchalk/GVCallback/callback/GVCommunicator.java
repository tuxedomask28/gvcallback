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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.DefaultRedirectHandler;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import com.bchalk.GVCallback.SettingsProvider;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.provider.CallLog;
import android.util.Log;

/**
 * This class communicates with GV. It handles all of the outgoing call sequence
 * as called by the receiver.
 * 
 * Large portions of code used in this program are taken from Evan Charlton's
 * program, GV. The program, GV, is protected under the Apache License 2.0. A
 * copy of Apache License 2.0 can be found in LICENSE-2.0.txt.
 * 
 * @author Brandon Chalk
 */
public class GVCommunicator
{
	private static final String TAG = "GV_GVC";
	public static final String DOMAIN = "www.google.com";
	public static final String BASE = "https://" + DOMAIN;
	public static final String VOICE = BASE + "/voice";
	public static final String MOBILE = VOICE + "/m";
	public static final String PDA_COOKIE_NAME = "gv-ph";

	private static GVCommunicator s_instance;

	private String myUsername = "";
	private String myPassword = "";
	private String myToken = "";
	private DefaultHttpClient myClient;
	private String myGVNumber = null;
	private String myError = "";
	private SettingsProvider mySettings;

	private static StringBuilder s_builder = new StringBuilder();

	protected GVCommunicator(Context context)
	{
		mySettings = SettingsProvider.getInstance(context);

		myUsername = mySettings.getGVUsername();
		myPassword = mySettings.getGVPassword();

		myClient = new DefaultHttpClient();
		myClient.setRedirectHandler(new DefaultRedirectHandler());
		myClient.getParams().setBooleanParameter("http.protocol.expect-continue", false);
	}

	public static GVCommunicator getInstance(Context context)
	{
		if (s_instance == null)
		{
			s_instance = new GVCommunicator(context);
		}
		return s_instance;
	}

	public boolean login()
	{
		if (!isLoggedIn())
		{
			return login(myUsername, myPassword);
		}
		return isLoggedIn();
	}

	public void logout()
	{
		myClient = new DefaultHttpClient();
		myToken = "";
	}

	private void storeToken(String token)
	{
		BasicClientCookie cookie = new BasicClientCookie("gv", token);
		cookie.setDomain("www.google.com");
		cookie.setPath("/voice");
		cookie.setSecure(true);
		myClient.getCookieStore().addCookie(cookie);
	}

	public boolean login(String username, String password)
	{
		myError = "";
		if (username.trim().length() == 0 || password.trim().length() == 0)
		{
			myError = "No Google Voice login information saved!";
			return false;
		}

		String token;

		myUsername = username;
		myPassword = password;

		myClient = new DefaultHttpClient();
		myClient.setRedirectHandler(new DefaultRedirectHandler());
		myClient.getParams().setBooleanParameter("http.protocol.expect-continue", false);

		List<NameValuePair> data = new ArrayList<NameValuePair>();
		data.add(new BasicNameValuePair("accountType", "GOOGLE"));
		data.add(new BasicNameValuePair("Email", username));
		data.add(new BasicNameValuePair("Passwd", password));
		data.add(new BasicNameValuePair("service", "grandcentral"));
		data.add(new BasicNameValuePair("source", "com-evancharlton-googlevoice-android-GV"));

		HttpPost post = new HttpPost("https://www.google.com/accounts/ClientLogin");
		myError = "";
		try
		{
			post.setEntity(new UrlEncodedFormEntity(data, HTTP.UTF_8));
			HttpResponse response = myClient.execute(post);
			BufferedReader is = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
			String line;
			while ((line = is.readLine()) != null)
			{
				if (line.startsWith("Auth"))
				{
					token = line.substring(5);
					storeToken(token);
					break;
				}
			}
			is.close();
			HttpGet get = new HttpGet("https://www.google.com/voice/m/i/voicemail?p=10000");
			response = myClient.execute(get);
			is = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
			Pattern rnrse = Pattern.compile("name=\"_rnr_se\"\\s*value=\"([^\"]+)\"");
			Pattern gvn = Pattern.compile("<b class=\"ms3\">([^<]+)</b>");
			Matcher m;
			boolean valid = false;
			boolean gvnvalid = false;
			while ((line = is.readLine()) != null)
			{
				if (line.indexOf("The username or password you entered is incorrect.") >= 0)
				{
					myError = "The username or password you entered is incorrect.";
					break;
				} else
				{
					if (line.indexOf("google.com/support/voice/bin/answer.py?answer=142423") >= 0)
					{
						myError = "This Google Account does not have a Google Voice account.";
						break;
					} else
					{
						if (!gvnvalid)
						{
							m = gvn.matcher(line);
							if (m.find())
							{
								myGVNumber = normalizeNumber(m.group(1));
								gvnvalid = true;
							}
						}
						
						if (!valid)
						{
							m = rnrse.matcher(line);
							if (m.find())
							{
								myError = "";
								myToken = m.group(1);
								valid = true;
							}
						}
						
						if (valid && gvnvalid)
							break;
					}
				}
			}
			is.close();
			return valid;
		} catch (Exception e)
		{
			e.printStackTrace();
			myError = "Network error! Try cycling wi-fi (if enabled).";
		}

		if (myError.length() > 0)
			Log.e("GV Login Error", myError);
		return false;
	}

	public boolean connect(String number)
	{
		if (!isLoggedIn())
		{
			login();
			if (!isLoggedIn())
			{
				myError = "Could not log in!";
				return false;
			}
		}

		String lineNumber = mySettings.getGVCallbackNum();
		if (lineNumber == null || lineNumber.length() == 0)
		{
			myError = "No callback number available! Please set this in 'GV Settings'";
			return false;
		}
		HttpPost post = new HttpPost(BASE + "/voice/call/connect/");
		List<BasicNameValuePair> data = new ArrayList<BasicNameValuePair>();
		data.add(new BasicNameValuePair("_rnr_se", myToken));
		data.add(new BasicNameValuePair("forwardingNumber", lineNumber));
		data.add(new BasicNameValuePair("outgoingNumber", number));
		data.add(new BasicNameValuePair("remember", "0"));
		data.add(new BasicNameValuePair("subscriberNumber", "undefined"));
		data.add(new BasicNameValuePair("phoneType", "2"));
		try
		{
			post.setEntity(new UrlEncodedFormEntity(data, HTTP.UTF_8));
			HttpResponse response = myClient.execute(post);
			String responseText = getJSON(response.getEntity());
			JSONObject json = (JSONObject) JSONValue.parse(responseText);
			boolean success = (Boolean) json.get("ok");
			if (!success)
			{
				myError = responseText;
			}
			return success;
		} catch (ClientProtocolException e)
		{
			e.printStackTrace();
		} catch (UnsupportedEncodingException e)
		{
			e.printStackTrace();
		} catch (IOException e)
		{
			e.printStackTrace();
		} catch (Exception e)
		{
			e.printStackTrace();
		}
		myError = "Unknown exception!";
		return false;
	}

	public void setUsername(String username)
	{
		myUsername = username;
	}

	public void setPassword(String password)
	{
		myPassword = password;
	}

	public static String getContent(HttpEntity entity) throws ClientProtocolException, IOException
	{
		s_builder.setLength(0);
		InputStream is = entity.getContent();
		BufferedReader buffer = new BufferedReader(new InputStreamReader(is));
		String line = null;
		while ((line = buffer.readLine()) != null)
		{
			s_builder.append(line).append("\n");
		}
		buffer.close();
		return s_builder.toString().trim();
	}

	/**
	 * Strip non-numbers from a phone number
	 * 
	 * @param number
	 *            A number of any format
	 * @return a number of just digits
	 */
	public static String normalizeNumber(String number)
	{
		return number.trim().replaceAll("[^0-9+]", "");
	}

	public String getJSON(HttpEntity entity) throws ClientProtocolException, IOException
	{
		s_builder.setLength(0);
		InputStream is = entity.getContent();
		BufferedReader buffer = new BufferedReader(new InputStreamReader(is));
		String line = null;
		while ((line = buffer.readLine()) != null)
		{
			s_builder.append(line).append("\n");
			if (line.indexOf("</json>") != -1)
			{
				break;
			}
		}
		buffer.close();
		entity.consumeContent();
		return s_builder.toString().substring(s_builder.indexOf("{"), s_builder.lastIndexOf("}") + 1);
	}

	@SuppressWarnings("unchecked")
	public Map<String, Long> getUnreadCounts()
	{
		if (!isLoggedIn())
		{
			login(myUsername, myPassword);
		}

		try
		{
			// go to an extremely high page so that it loads faster. Examine the
			// JSON feeds if you don't know what I mean by this.
			HttpGet get = new HttpGet(BASE + "/voice/inbox/recent?page=p1000");
			HttpResponse response = myClient.execute(get);
			String rsp = getJSON(response.getEntity());
			Object obj = JSONValue.parse(rsp);
			JSONObject msgs = (JSONObject) obj;

			Map<String, Long> unread = new HashMap<String, Long>();
			msgs = (JSONObject) msgs.get("unreadCounts");
			for (String key : (Set<String>) msgs.keySet())
			{
				Object data = msgs.get(key);
				unread.put(key, (Long) data);
			}
			return unread;
		} catch (ClientProtocolException e)
		{
			e.printStackTrace();
		} catch (IOException e)
		{
			e.printStackTrace();
		} catch (Exception e)
		{
			e.printStackTrace();
		}
		return new HashMap<String, Long>();
	}

	protected String getJSONString(JSONObject json, String key)
	{
		return getJSONString(json, key, "");
	}

	protected String getJSONString(JSONObject json, String key, String defValue)
	{
		String value = (String) json.get(key);
		return value == null ? defValue : value;
	}

	public boolean isLoggedIn()
	{
		return myToken.length() > 0;
	}

	public String getUsername()
	{
		return myUsername;
	}

	public String getGVNumber()
	{
		if (!isLoggedIn())
			login();
		return myGVNumber;
	}

	public String getToken()
	{
		return myToken;
	}

	public void setToken(String token)
	{
		myToken = token;
	}

	public String getError()
	{
		return myError;
	}

	public DefaultHttpClient getHttpClient()
	{
		return myClient;
	}

	public boolean sendSMS(String to, String message)
	{
		return sendSMS(to, message, "undefined");
	}

	public boolean sendSMS(String to, String message, String threadId)
	{
		if (!isLoggedIn())
		{
			login();
			if (!isLoggedIn())
			{
				return false;
			}
		}
		String c = "1";
		if (threadId.equals("undefined"))
		{
			c = "undefined";
		}
		if (message.trim().length() == 0)
		{
			return false;
		}
		HttpPost post = new HttpPost(BASE + "/voice/m/sendsms");

		List<BasicNameValuePair> data = new ArrayList<BasicNameValuePair>();
		data.add(new BasicNameValuePair("_rnr_se", myToken));
		data.add(new BasicNameValuePair("smstext", message));
		data.add(new BasicNameValuePair("number", normalizeNumber(to)));
		data.add(new BasicNameValuePair("id", threadId));
		data.add(new BasicNameValuePair("c", c));

		try
		{
			post.setEntity(new UrlEncodedFormEntity(data, HTTP.UTF_8));
			HttpResponse response = myClient.execute(post);
			HttpEntity entity = response.getEntity();
			String rsp = getContent(entity);
			return rsp.toLowerCase().indexOf("sent") != -1;
		} catch (ClientProtocolException e)
		{
			e.printStackTrace();
		} catch (IOException e)
		{
			e.printStackTrace();
		}
		return false;
	}

	public boolean messageMarkRead(String id, boolean read)
	{
		HttpPost post = new HttpPost(BASE + "/voice/inbox/mark/");
		List<BasicNameValuePair> data = new ArrayList<BasicNameValuePair>();
		data.add(new BasicNameValuePair("messages", id));
		data.add(new BasicNameValuePair("read", read ? "1" : "0"));
		data.add(new BasicNameValuePair("_rnr_se", myToken));
		try
		{
			post.setEntity(new UrlEncodedFormEntity(data, HTTP.UTF_8));
			HttpResponse response = myClient.execute(post);
			String rsp = getContent(response.getEntity());
			JSONObject val = (JSONObject) JSONValue.parse(rsp);
			if (val == null)
			{
				return true;
			}
			return (Boolean) val.get("ok");
		} catch (ClientProtocolException e)
		{
			e.printStackTrace();
		} catch (IOException e)
		{
			e.printStackTrace();
		}
		return false;
	}

	public boolean messageBlockCaller(String id, boolean block)
	{
		HttpPost post = new HttpPost(BASE + "/voice/inbox/block/");
		List<BasicNameValuePair> data = new ArrayList<BasicNameValuePair>();
		data.add(new BasicNameValuePair("messages", id));
		data.add(new BasicNameValuePair("blocked", block ? "1" : "0"));
		data.add(new BasicNameValuePair("_rnr_se", myToken));
		try
		{
			post.setEntity(new UrlEncodedFormEntity(data, HTTP.UTF_8));
			HttpResponse response = myClient.execute(post);
			String rsp = getContent(response.getEntity());
			JSONObject val = (JSONObject) JSONValue.parse(rsp);
			return (Boolean) val.get("ok");
		} catch (ClientProtocolException e)
		{
			e.printStackTrace();
		} catch (IOException e)
		{
			e.printStackTrace();
		}
		return false;
	}

	public boolean deleteMessageById(String id)
	{
		HttpPost post = new HttpPost(BASE + "/voice/inbox/deleteMessages");
		List<BasicNameValuePair> data = new ArrayList<BasicNameValuePair>();
		data.add(new BasicNameValuePair("_rnr_se", myToken));
		data.add(new BasicNameValuePair("messages", id));
		data.add(new BasicNameValuePair("trash", "1"));
		try
		{
			post.setEntity(new UrlEncodedFormEntity(data, HTTP.UTF_8));
			HttpResponse response = myClient.execute(post);
			String rsp = getContent(response.getEntity());
			JSONObject val = (JSONObject) JSONValue.parse(rsp);
			return (Boolean) val.get("ok");
		} catch (ClientProtocolException e)
		{
			e.printStackTrace();
		} catch (IOException e)
		{
			e.printStackTrace();
		}
		return false;
	}

	public JSONObject getSettings()
	{
		if (!isLoggedIn())
		{
			if (!login())
			{
				return null;
			}
		}
		try
		{
			HttpGet get = new HttpGet(BASE + "/voice/settings/tab/phones");
			HttpResponse response = myClient.execute(get);
			String json = getJSON(response.getEntity());
			return (JSONObject) JSONValue.parse(json);
		} catch (ClientProtocolException e)
		{
			e.printStackTrace();
		} catch (IOException e)
		{
			e.printStackTrace();
		}
		return null;
	}

	public boolean deletePhone(long phoneId)
	{
		if (!isLoggedIn())
		{
			if (!login())
			{
				return false;
			}
		}
		try
		{
			HttpPost post = new HttpPost(BASE + "/voice/settings/deleteForwarding");
			List<BasicNameValuePair> data = new ArrayList<BasicNameValuePair>();
			data.add(new BasicNameValuePair("id", String.valueOf(phoneId)));
			data.add(new BasicNameValuePair("_rnr_se", myToken));
			post.setEntity(new UrlEncodedFormEntity(data));

			HttpResponse response = myClient.execute(post);
			return Boolean.parseBoolean(getContent(response.getEntity()));
		} catch (ClientProtocolException e)
		{
			e.printStackTrace();
		} catch (IOException e)
		{
			e.printStackTrace();
		} catch (Exception e)
		{
			e.printStackTrace();
		}
		return false;
	}

	public boolean verifyPhone(String number, long phoneId, int verificationCode)
	{
		if (!isLoggedIn())
		{
			if (!login())
			{
				return false;
			}
		}
		try
		{
			HttpPost post = new HttpPost(BASE + "/voice/call/verifyForwarding");
			List<BasicNameValuePair> data = new ArrayList<BasicNameValuePair>();

			data.add(new BasicNameValuePair("_rnr_se", myToken));
			data.add(new BasicNameValuePair("code", String.valueOf(verificationCode)));
			data.add(new BasicNameValuePair("forwardingNumber", number));
			data.add(new BasicNameValuePair("phoneId", String.valueOf(phoneId)));
			data.add(new BasicNameValuePair("subscriberNumber", "undefined"));

			post.setEntity(new UrlEncodedFormEntity(data));

			HttpResponse response = myClient.execute(post);
			String rsp = getContent(response.getEntity());
			JSONObject val = (JSONObject) JSONValue.parse(rsp);
			return (Boolean) val.get("ok");
		} catch (ClientProtocolException e)
		{
			e.printStackTrace();
		} catch (IOException e)
		{
			e.printStackTrace();
		} catch (Exception e)
		{
			e.printStackTrace();
		}
		return false;
	}

	public boolean saveSettings(Map<String, String> map)
	{
		if (!isLoggedIn())
		{
			if (!login())
			{
				return false;
			}
		}
		try
		{
			HttpPost post = new HttpPost(BASE + "/voice/settings/editGeneralSettings");
			List<BasicNameValuePair> data = new ArrayList<BasicNameValuePair>();

			data.add(new BasicNameValuePair("_rnr_se", myToken));
			for (String key : map.keySet())
			{
				data.add(new BasicNameValuePair(key, map.get(key)));
			}

			post.setEntity(new UrlEncodedFormEntity(data));

			HttpResponse response = myClient.execute(post);
			String rsp = getContent(response.getEntity());
			JSONObject val = (JSONObject) JSONValue.parse(rsp);
			return (Boolean) val.get("ok");
		} catch (ClientProtocolException e)
		{
			e.printStackTrace();
		} catch (IOException e)
		{
			e.printStackTrace();
		} catch (Exception e)
		{
			e.printStackTrace();
		}
		return true;
	}

	public static void insertPlaceholderCall(ContentResolver contentResolver, String number)
	{
		ContentValues values = new ContentValues();
		values.put(CallLog.Calls.NUMBER, number);
		values.put(CallLog.Calls.DATE, System.currentTimeMillis());
		values.put(CallLog.Calls.DURATION, 0);
		values.put(CallLog.Calls.TYPE, CallLog.Calls.OUTGOING_TYPE);
		values.put(CallLog.Calls.NEW, 1);
		values.put(CallLog.Calls.CACHED_NAME, "");
		values.put(CallLog.Calls.CACHED_NUMBER_TYPE, 0);
		values.put(CallLog.Calls.CACHED_NUMBER_LABEL, "");
		Log.d(TAG, "Inserting call log placeholder for " + number);
		contentResolver.insert(CallLog.Calls.CONTENT_URI, values);
	}

	public Boolean cancelCall()
	{
		if (!isLoggedIn())
		{
			if (!login())
			{
				return false;
			}
		}
		try
		{
			HttpPost post = new HttpPost(BASE + "/voice/call/cancel/");
			List<BasicNameValuePair> data = new ArrayList<BasicNameValuePair>();

			// outgoingNumber=undefined&forwardingNumber=undefined&cancelType=C2C&_rnr_se=P1%2F529GFlankX5n7H838tWwjiOM%3D
			data.add(new BasicNameValuePair("_rnr_se", myToken));
			data.add(new BasicNameValuePair("cancelType", "C2C"));
			data.add(new BasicNameValuePair("forwardingNumber", "undefined"));
			data.add(new BasicNameValuePair("outgoingNumber", "undefined"));

			post.setEntity(new UrlEncodedFormEntity(data));

			HttpResponse response = myClient.execute(post);
			String rsp = getContent(response.getEntity());
			return (rsp.indexOf("ok") == 2);
		} catch (ClientProtocolException e)
		{
			e.printStackTrace();
		} catch (IOException e)
		{
			e.printStackTrace();
		} catch (Exception e)
		{
			e.printStackTrace();
		}
		return true;
	}

	public Boolean setDoNotDisturb(boolean enabled)
	{
		if (!isLoggedIn())
		{
			if (!login())
			{
				return false;
			}
		}
		try
		{
			HttpPost post = new HttpPost(MOBILE + "/savednd");
			List<BasicNameValuePair> data = new ArrayList<BasicNameValuePair>();

			data.add(new BasicNameValuePair("_rnr_se", myToken));
			data.add(new BasicNameValuePair("doNotDisturb", enabled ? "1" : "0"));

			post.setEntity(new UrlEncodedFormEntity(data));

			myClient.execute(post);
			return true;
		} catch (ClientProtocolException e)
		{
			e.printStackTrace();
		} catch (IOException e)
		{
			e.printStackTrace();
		} catch (Exception e)
		{
			e.printStackTrace();
		}
		return false;
	}
}
