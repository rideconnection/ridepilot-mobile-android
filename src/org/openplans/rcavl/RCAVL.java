/* This program is free software: you can redistribute it and/or
 modify it under the terms of the GNU General Public License
 as published by the Free Software Foundation, either version 3 of
 the License, or (props, at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>. */

package org.openplans.rcavl;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.openplans.rcavl.ConfigDialog.Configured;
import org.openplans.rcavl.GpsService.LocalBinder;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class RCAVL extends Activity implements Configured {
	String TAG = "RCAVL";

	public String apiRequestUrl = "https://apps.rideconnection.org/ridepilot";

	private ProgressBar spinner;
	public GpsService gpsService;
	private AutoCompleteTextView emailField;
	private TextView passwordField;

	public int pingInterval = 60;

	private String userEmail;

	private GpsServiceConnection serviceConnection;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Intent intent = new Intent(RCAVL.this, GpsService.class);
		startService(intent);
		serviceConnection = new GpsServiceConnection();
		bindService(intent, serviceConnection, 0);
	}

	private class LoginTask extends AsyncTask<Void, Void, String> {
		
		protected String doInBackground(Void... params) {
			// make login request, which really is just a GET request for
			// the ping URL

			String postUrl = apiRequestUrl + "/device_pool_drivers.json";
			
			HttpClient client = HttpUtils.getNewHttpClient();
			HttpPost request = new HttpPost(postUrl);

			try {
				List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(2);
				String email = emailField.getText().toString();
				String password = passwordField.getText().toString();

				nameValuePairs.add(new BasicNameValuePair("user[email]", email));
				nameValuePairs.add(new BasicNameValuePair("user[password]", password));
				request.setEntity(new UrlEncodedFormEntity(nameValuePairs));
				
				HttpResponse response = client.execute(request);
				HttpEntity entity = response.getEntity();
				
				String json = EntityUtils.toString(entity);
				if (!json.startsWith("{")) {
					// not really json
					toast("Got an unexpected response from the server.  Are you sure you have the URL right?");
					return null;
				}
				
				JSONTokener tokener = new JSONTokener(json);
				JSONObject data = (JSONObject) tokener.nextValue();
				
				Object url = data.get("resource_url");
				if (url instanceof String) 
					return (String) url;
				else
					toast("Bad response from server: " + url);
				
			} catch (ClientProtocolException e) {
				Log.e(TAG, "exception logging in", e);
				toast("Error logging in.  Try again.");
			} catch (IOException e) {
				Log.e(TAG, "exception logging in", e);
				toast("Error logging in.  Try again.");
			} catch (JSONException e) {
				Log.e(TAG, "exception logging in", e);
				toast("Error logging in.  Check your password and try again.");
			} catch (Exception e) {
				Log.e(TAG, "exception logging in", e);
				toast("Error logging in.");
			}
			
			return null;
		}

		protected void onPostExecute(String url) {
			if (url == null) {
				spinner.setIndeterminate(false);
				spinner.setVisibility(View.INVISIBLE);
				return;
			}
			loggedIn();
			Intent intent = new Intent(RCAVL.this, GpsService.class);
			intent.putExtra("pingUrl", url);
			String email = emailField.getText().toString();
			String password = passwordField.getText().toString();
			intent.putExtra("email", email);
			intent.putExtra("password", password);
			intent.putExtra("pingInterval", pingInterval);
			gpsService.realStart(intent);
		}
	}

	void setupUI() {
		if (gpsService == null || gpsService.getStatus().equals(GpsService.INACTIVE) || gpsService.getStatus().equals(GpsService.NOT_STARTED)) {
			//the service is inactive or will shut down
			switchToLogin();
		} else {
			userEmail = gpsService.getEmail();
			switchToRunning();
		}
	}
	
	// this is called from the GpsService after its thread has been initialized and started.
	// The location update request has to be started from the UI thread (I am not sure why),
	// or no location updates are delivered to the GpsServiceThread instance!
	public void startLocation() {
		runOnUiThread(new Thread() {
			public void run() {
				gpsService.startReceivingLocation();
			}
		});
	}

	class GpsServiceConnection implements ServiceConnection {
		public void onServiceConnected(ComponentName className, IBinder service) {
			// We've bound to LocalService, cast the IBinder and get
			// LocalService instance
			LocalBinder binder = (LocalBinder) service;
			gpsService = binder.getService();
			gpsService.setActivity(RCAVL.this);

			//this happens a bit after startup, so we need to call a callback to set up the UI
			runOnUiThread(new Thread() {
				public void run() {
					setupUI();
				}
			});
		}

		public void onServiceDisconnected(ComponentName arg0) {
			// notify the user
			toast("GPS service disconnected");
		}

	}

	public void toast(final String text) {
		runOnUiThread(new Runnable() {
			public void run() {
				Context context = getApplicationContext();
				int duration = Toast.LENGTH_SHORT;

				Toast toast = Toast.makeText(context, text, duration);
				toast.show();
			}
		});
	}

	public void loggedIn() {
		spinner.setIndeterminate(false);
		spinner.setVisibility(View.INVISIBLE);

		/*
		 * Handle adding the user's email address to the frequently used email
		 * address list
		 */
		SharedPreferences preferences = getPreferences(MODE_PRIVATE);
		String emails = preferences.getString("emails", null);
		userEmail = emailField.getText().toString();
		if (emails == null) {
			emails = userEmail;
		} else {
			String[] past_emails = emails.split(",");
			if (!Arrays.asList(past_emails).contains(userEmail)) {
				emails = emails + "," + userEmail;
			}
		}
		Editor editor = preferences.edit();
		editor.putString("emails", emails);
		editor.commit();

		toast("Logged in");
		switchToRunning();
	}

	private void switchToLogin() {
		setContentView(R.layout.login);

		SharedPreferences preferences = getPreferences(MODE_PRIVATE);
		String emails = preferences.getString("emails", "");

		String[] past_emails = emails.split(",");
		if (past_emails == null) {
			past_emails = new String[0];
		}

		emailField = (AutoCompleteTextView) findViewById(R.id.emailField);
		passwordField = (TextView) findViewById(R.id.passwordField);

		ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
				R.layout.list_item, past_emails);
		emailField.setAdapter(adapter);
		spinner = (ProgressBar) findViewById(R.id.loginProgressBar);

		View loginButton = findViewById(R.id.loginButton);
		loginButton.setOnClickListener(new OnClickListener() {

			public void onClick(View v) {
				spinner.setIndeterminate(true);
				spinner.setVisibility(View.VISIBLE);
				new LoginTask().execute();
			}
		});

		View configButton = findViewById(R.id.configButton);
		configButton.setOnClickListener(new OnClickListener() {

			public void onClick(View v) {
				ConfigDialog dialog = new ConfigDialog(RCAVL.this);
				dialog.setConfigured(RCAVL.this);
				dialog.show();
			}
		});

		apiRequestUrl = preferences.getString("apiRequestUrl", apiRequestUrl);
		pingInterval = preferences.getInt("pingInterval", pingInterval);

	}

	private void switchToRunning() {
		setContentView(R.layout.running);

		TextView userField = (TextView) findViewById(R.id.loggedInAsLabel);
		userField.setText("Logged in as " + userEmail);

		final Button breakButton = (Button) findViewById(R.id.breakButton);
		if (gpsService.getStatus().equals(GpsService.BREAK))
			breakButton.setText(R.string.return_from_break);
		else
			breakButton.setText(R.string.take_a_break);
			

		breakButton.setOnClickListener(new OnClickListener() {

			public void onClick(View v) {
				runOnUiThread(new Thread() {
					public void run() {
						if (gpsService.isActive()) {
							gpsService.setStatus(GpsService.BREAK, false);
							breakButton.setText(R.string.return_from_break);
						} else {
							gpsService.setStatus(GpsService.ACTIVE, true);
							breakButton.setText(R.string.take_a_break);
						}
	}
				});
			}
		});

		final Button logoutButton = (Button) findViewById(R.id.logoutButton);
		logoutButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				gpsService.setStatus(GpsService.INACTIVE, false);
				stopService(new Intent(RCAVL.this, GpsService.class));
				switchToLogin();
			}
		});
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		unbindService(serviceConnection);
	}

	public void ping() {
		runOnUiThread(new Thread() {
			public void run() {
				TextView pingField = (TextView) findViewById(R.id.lastPingLabel);
				if (pingField != null) {
					String now = new SimpleDateFormat("HH:mm:ss")
							.format(new Date());
					pingField.setText("Last contacted server " + now);
				}
			}
		});
	}

	/* configuration dialog callbacks */
	public String getServerUrl() {
		return apiRequestUrl;
	}

	public int getPingInterval() {
		return pingInterval;
	}

	public void setConfig(String serverUrl, int pingInterval) {
		apiRequestUrl = serverUrl;
		this.pingInterval = pingInterval;
		SharedPreferences preferences = getPreferences(MODE_PRIVATE);
		Editor editor = preferences.edit();
		editor.putString("apiRequestUrl", apiRequestUrl);
		editor.putInt("pingInterval", pingInterval);
		editor.commit();
	}

}