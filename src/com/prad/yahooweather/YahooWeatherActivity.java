/**
 * Copyright 2010-present Facebook.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.prad.yahooweather;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import com.facebook.AppEventsLogger;
import com.facebook.FacebookAuthorizationException;
import com.facebook.FacebookException;
import com.facebook.FacebookOperationCanceledException;
import com.facebook.FacebookRequestError;
import com.facebook.Session;
import com.facebook.SessionState;
import com.facebook.UiLifecycleHelper;
import com.facebook.model.GraphObject;
import com.facebook.widget.FacebookDialog;
import com.facebook.widget.WebDialog;
import com.facebook.widget.WebDialog.OnCompleteListener;

public class YahooWeatherActivity extends FragmentActivity {

	private static final String PERMISSION = "publish_actions";

	String jsonResult;
	EditText searchText;
	RadioGroup radioGroup;
	Button searchButton;
	private final String PENDING_ACTION_BUNDLE_KEY = "com.facebook.samples.hellofacebook:PendingAction";

	private TextView postStatusUpdateButton;
	private TextView postPhotoButton;

	private PendingAction pendingAction = PendingAction.NONE;

	private boolean canPresentShareDialog;

	private ImageView mImageView;

	private enum PendingAction {
		NONE, CURRENT_WEATHER, WEATHER_FORECAST
	}

	private UiLifecycleHelper uiHelper;

	private Session.StatusCallback callback = new Session.StatusCallback() {
		@Override
		public void call(Session session, SessionState state,
				Exception exception) {
			onSessionStateChange(session, state, exception);

		}
	};

	private FacebookDialog.Callback dialogCallback = new FacebookDialog.Callback() {
		@Override
		public void onError(FacebookDialog.PendingCall pendingCall,
				Exception error, Bundle data) {
			Log.d("HelloFacebook", String.format("Error: %s", error.toString()));
		}

		@Override
		public void onComplete(FacebookDialog.PendingCall pendingCall,
				Bundle data) {
			Log.d("HelloFacebook", "Success!");
		}
	};

	private void hideKeyboard() {
		InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
		imm.hideSoftInputFromWindow(searchText.getWindowToken(), 0);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		uiHelper = new UiLifecycleHelper(this, callback);
		uiHelper.onCreate(savedInstanceState);

		if (savedInstanceState != null) {
			String name = savedInstanceState
					.getString(PENDING_ACTION_BUNDLE_KEY);
			pendingAction = PendingAction.valueOf(name);
		}

		setContentView(R.layout.weather_layout);
		mImageView = (ImageView) findViewById(R.id.feed_image);

		// Add code to print out the key hash
		try {
			PackageInfo info = getPackageManager().getPackageInfo(
					"com.prad.yahooweather", PackageManager.GET_SIGNATURES);
			// 9Q4TYYAtIO1mNDFa+Y57Ausm5lE=

			// Yahoo Weather
			// App ID: 612655528781332
			// App Secret: c6ae83d7cf1aeba1c01045bd37f1db31

			for (Signature signature : info.signatures) {
				MessageDigest md = MessageDigest.getInstance("SHA");
				md.update(signature.toByteArray());
				Log.d("KeyHash:",
						Base64.encodeToString(md.digest(), Base64.DEFAULT));
			}
		} catch (Exception e) {

		}

		View f = (View) findViewById(R.id.resultTable);
		f.setVisibility(View.GONE);

		searchText = (EditText) findViewById(R.id.searchText);
		searchButton = (Button) findViewById(R.id.search_button);
		radioGroup = (RadioGroup) findViewById(R.id.searchTypeRadioGroup);

		mImageView.setLongClickable(true);
		mImageView.setOnLongClickListener(new OnLongClickListener() {

			@Override
			public boolean onLongClick(View v) {
				// TODO Auto-generated method stub
				Session session = Session.getActiveSession();
				if (session != null) {
					session.closeAndClearTokenInformation();
				} else {
					Session session2 = Session.openActiveSession(
							YahooWeatherActivity.this, false, null);
					if (session2 != null)
						session2.closeAndClearTokenInformation();
				}

				return true;
			}
		});

		searchButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				String searchType = "";
				boolean isValidInput = false;
				hideKeyboard();
				String searchString = searchText.getText().toString();

				if (searchString == null || "".equals(searchString)) {
					Toast.makeText(
							YahooWeatherActivity.this,
							"Text field is empty. Please enter zip or city name.",
							Toast.LENGTH_SHORT).show();
				} else {

					if (checkIfValidZip(searchString)) {
						searchType = "zip";
						isValidInput = true;
					} else {
						if (checkIfValidCity(searchString)) {
							searchType = "city";
							isValidInput = true;
						}
					}

				}

				if (isValidInput) {

					new GetJSON().execute(searchString, searchType,
							getTempTyep());
				}

			}
		});

		postStatusUpdateButton = (TextView) findViewById(R.id.current_weather);
		postStatusUpdateButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) {
				pendingAction = PendingAction.CURRENT_WEATHER;

				showFacebookshareDialog("Post Current Weather");

			}
		});

		postPhotoButton = (TextView) findViewById(R.id.weather_forecast);
		postPhotoButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) {

				pendingAction = PendingAction.WEATHER_FORECAST;

				showFacebookshareDialog("Post Weather Forecast");

			}
		});

		canPresentShareDialog = FacebookDialog.canPresentShareDialog(this,
				FacebookDialog.ShareDialogFeature.SHARE_DIALOG);
		canPresentShareDialog = true;
	}

	private void showFacebookshareDialog(String okButotnText) {

		final Dialog dialog = new Dialog(this);
		dialog.setContentView(R.layout.dialog_layout);
		dialog.setTitle("Post to Facebook");


		Button dialogButton = (Button) dialog.findViewById(R.id.cancel_dialog);
		// if button is clicked, close the custom dialog
		dialogButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				dialog.dismiss();
			}
		});

		Button postButton = (Button) dialog.findViewById(R.id.post_to_facebook);
		postButton.setText(okButotnText);
		// if button is clicked, close the custom dialog
		postButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				onClickPostStatusUpdate();
				dialog.dismiss();
			}
		});

		dialog.show();

	}

	@Override
	protected void onResume() {
		super.onResume();
		uiHelper.onResume();

		AppEventsLogger.activateApp(this);

	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		uiHelper.onSaveInstanceState(outState);

		outState.putString(PENDING_ACTION_BUNDLE_KEY, pendingAction.name());
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		uiHelper.onActivityResult(requestCode, resultCode, data, dialogCallback);

		System.out.println("Came here");
	}

	@Override
	public void onPause() {
		super.onPause();
		uiHelper.onPause();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		uiHelper.onDestroy();
	}

	private void onSessionStateChange(Session session, SessionState state,
			Exception exception) {
		if (pendingAction != PendingAction.NONE
				&& (exception instanceof FacebookOperationCanceledException || exception instanceof FacebookAuthorizationException)) {
			new AlertDialog.Builder(YahooWeatherActivity.this)
					.setTitle(R.string.cancelled)
					.setMessage(R.string.permission_not_granted)
					.setPositiveButton(R.string.ok, null).show();
			pendingAction = PendingAction.NONE;
		} else if (state == SessionState.OPENED_TOKEN_UPDATED) {
			handlePendingAction();
			Log.d("handlePendingAction in callback 1", pendingAction.name());
		} else if (state == SessionState.OPENED) {

			if (previouslyPendingAction != PendingAction.NONE) {
				Log.d("handlePendingAction in callback 2",
						previouslyPendingAction.name() + " "
								+ pendingAction.name());
				// pendingAction = previouslyPendingAction;
				// previouslyPendingAction = PendingAction.NONE;
				// handlePendingAction();
				// postStatusUpdate(previouslyPendingAction);
				performPublish(previouslyPendingAction, canPresentShareDialog);
			}
		}
		// updateUI();
	}

	PendingAction previouslyPendingAction = PendingAction.NONE;

	@SuppressWarnings("incomplete-switch")
	private void handlePendingAction() {
		previouslyPendingAction = pendingAction;
		pendingAction = PendingAction.NONE;
		switch (previouslyPendingAction) {
		case CURRENT_WEATHER:

			postStatusUpdate(PendingAction.CURRENT_WEATHER);
			// previouslyPendingAction =PendingAction.NONE;
			break;
		case WEATHER_FORECAST:
			postStatusUpdate(PendingAction.WEATHER_FORECAST);
			// previouslyPendingAction =PendingAction.NONE;
			break;
		}
		//
	}

	private interface GraphObjectWithId extends GraphObject {
		String getId();
	}

	private void showPublishResult(String message, GraphObject result,
			FacebookRequestError error) {
		String title = null;
		String alertMessage = null;
		if (error == null) {
			title = getString(R.string.success);
			String id = result.cast(GraphObjectWithId.class).getId();
			alertMessage = getString(R.string.successfully_posted_post,
					message, id);
		} else {
			title = getString(R.string.error);
			alertMessage = error.getErrorMessage();
		}

		new AlertDialog.Builder(this).setTitle(title).setMessage(alertMessage)
				.setPositiveButton(R.string.ok, null).show();
	}

	private void onClickPostStatusUpdate() {
		performPublish(pendingAction, canPresentShareDialog);
	}

	private Bundle getBuilder(PendingAction action) {
		Bundle params = new Bundle();

		String title = "";
		String caption = "";
		String city = "";
		String text = "";
		String description = "";
		String link = "";
		String image_url = "";
		String imageURL = "";
		String conditionTemp = "";
		String temperature = "";
		String feed = "";
		JSONArray forecasatArray = null;
		try {
			JSONObject object = new JSONObject(jsonResult);
			JSONObject weather_object = object.getJSONObject("weather");

			imageURL = weather_object.getString("image");
			feed = weather_object.getString("feed");
			link = weather_object.getString("link");

			JSONObject location_object = weather_object
					.getJSONObject("location");

			String country = location_object.getString("country");
			String region = location_object.getString("region");
			city = location_object.getString("city");
			title = city + ", " + region + ", " + country;

			JSONObject condition_object = weather_object
					.getJSONObject("condition");
			text = condition_object.getString("text");
			conditionTemp = condition_object.getString("temp");

			JSONObject units_object = weather_object.getJSONObject("units");
			temperature = units_object.getString("temperature");

			forecasatArray = weather_object.getJSONArray("forecast");

		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		params.putString("name", title);
		if (action == PendingAction.CURRENT_WEATHER) {

			caption = "The current condition for " + city + " is " + text;
			image_url = imageURL;
			description = "Temperature is " + conditionTemp + "�" + temperature
					+ ".";

		} else {
			caption = "Weather forecat for " + city;
			image_url = "http://www-scf.usc.edu/~csci571/2013Fall/hw8/weather.jpg";

			for (int i = 0; i < forecasatArray.length(); i++) {

				try {
					JSONObject forecast = forecasatArray.getJSONObject(i);
					String high = forecast.getString("high");
					String day = forecast.getString("day");
					String weather = forecast.getString("text");
					String low = forecast.getString("low");

					description = description + day + ':' + weather + ','
							+ high + '/' + low + "�" + temperature;
					if (i < 4) {
						description = description + ';';
					} else {
						description = description + '.';
					}

				} catch (JSONException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

			}

		}
		JSONObject property = new JSONObject();
		JSONObject properties = new JSONObject();
		try {

			property.put("text", "here");
			property.put("href", link);
			properties.put("Look at details", property);

		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		params.putString("properties", properties.toString());
		params.putString("caption", caption);

		params.putString("description", description);

		params.putString("link", feed);

		params.putString("picture", image_url);

		return params;
	}

	private void postStatusUpdate(PendingAction action) {
		// if (hasPublishPermission()) {
		if (Session.getActiveSession().isOpened()) {

			Bundle params = new Bundle();
			params = getBuilder(action);

			WebDialog feedDialog = (new WebDialog.FeedDialogBuilder(this,
					Session.getActiveSession(), params)).setOnCompleteListener(
					new OnCompleteListener() {

						@Override
						public void onComplete(Bundle values,
								FacebookException error) {
							if (error == null) {
								// When the story is posted, echo the success
								// and the post Id.
								final String postId = values
										.getString("post_id");
								if (postId != null) {
									Toast.makeText(YahooWeatherActivity.this,
											"Posted story, id: " + postId,
											Toast.LENGTH_SHORT).show();
								} else {
									// User clicked the Cancel button
									Toast.makeText(
											YahooWeatherActivity.this
													.getApplicationContext(),
											"Publish cancelled",
											Toast.LENGTH_SHORT).show();
								}
							} else if (error instanceof FacebookOperationCanceledException) {
								// User clicked the "x" button
								Toast.makeText(getApplicationContext(),
										"Publish cancelled", Toast.LENGTH_SHORT)
										.show();
							} else {
								// Generic, ex: network error
								Toast.makeText(getApplicationContext(),
										"Error posting story",
										Toast.LENGTH_SHORT).show();
							}
						}

					}).build();
			feedDialog.show();

		} else {
			// pendingAction = PendingAction.WEATHER_FORECAST;

			if (!Session.getActiveSession().isOpened()
					&& !Session.getActiveSession().isClosed()) {
				Session.getActiveSession().openForRead(
						new Session.OpenRequest(this).setPermissions(
								Arrays.asList("basic_info")).setCallback(
								callback));
			} else {
				Session.openActiveSession(this, true, callback);
			}
		}
	}

	private boolean hasPublishPermission() {
		Session session = Session.getActiveSession();
		return session != null
				&& session.getPermissions().contains("publish_actions");
	}

	private void performPublish(PendingAction action, boolean allowNoSession) {
		Session session = Session.getActiveSession();
		if (session != null) {
			pendingAction = action;
			if (hasPublishPermission()) {
				// We can do the action right away.
				handlePendingAction();
				return;
			} else if (session.isOpened()) {
				// We need to get new permissions, then complete the action when
				// we get called back.
				session.requestNewPublishPermissions(new Session.NewPermissionsRequest(
						this, PERMISSION));
				return;
			}
		}

		if (allowNoSession) {
			pendingAction = action;
			handlePendingAction();
		}
	}

	private String getTempTyep() {
		int id = radioGroup.getCheckedRadioButtonId();
		if (id == R.id.celRadioButton) {
			return "C";
		} else {
			return "F";
		}

	}

	private boolean checkIfValidZip(String searchString) {
		if (searchString.matches("^[0-9]*$")) {
			if (searchString.length() == 5) {
				return true;
			} else {
				Toast.makeText(this, "Invalid zip", Toast.LENGTH_SHORT).show();
				return false;
			}

		} else {
			return false;
		}

	}

	private boolean checkIfValidCity(String searchString) {
		Pattern pattern = Pattern.compile("[\\w\\s.,]+(\\s*,\\s*\\w+)+$");
		Matcher compare = pattern.matcher(searchString);

		if (compare.find()) {
			return true;
		} else {
			Toast.makeText(this, "Invalid city", Toast.LENGTH_SHORT).show();
			return false;
		}

	}

	class GetJSON extends AsyncTask<String, Void, String> {

		InputStream inputStream = null;
		String servletUrl = "http://cs-server.usc.edu:29040/YahooWeather/WeatherServlet";
		private ProgressDialog progressDialog = new ProgressDialog(
				YahooWeatherActivity.this);

		@Override
		protected String doInBackground(String... params) {

			ArrayList<NameValuePair> postParams = new ArrayList<NameValuePair>();

			postParams.add(new BasicNameValuePair("location", params[0]));
			postParams.add(new BasicNameValuePair("type", params[1]));
			postParams.add(new BasicNameValuePair("tempType", params[2]));
			StringBuilder stringBuilder = null;
			try {

				DefaultHttpClient defaultHttpClient = new DefaultHttpClient();
				HttpPost post = new HttpPost(servletUrl);
				post.setEntity(new UrlEncodedFormEntity(postParams));

				HttpResponse httpResponse = defaultHttpClient.execute(post);
				HttpEntity httpEntity = httpResponse.getEntity();

				inputStream = httpEntity.getContent();

				BufferedReader bufferReader = new BufferedReader(
						new InputStreamReader(inputStream));
				stringBuilder = new StringBuilder();
				String line = null;

				while ((line = bufferReader.readLine()) != null) {
					stringBuilder.append(line);
				}

			} catch (Exception e) {
				e.printStackTrace();
				stringBuilder
						.append("There is some problem with network. Please check the network connectivity.");
				progressDialog.dismiss();
			}

			return stringBuilder.toString();
		}

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			progressDialog.show();
			progressDialog.setMessage("Loading...");
			progressDialog.setCancelable(true);
		}

		@TargetApi(Build.VERSION_CODES.HONEYCOMB)
		@Override
		protected void onPostExecute(String result) {
			super.onPostExecute(result);
			jsonResult = result;
			View f = (View) findViewById(R.id.resultTable);

			System.out.println(result);

			TextView errorMessage = (TextView) YahooWeatherActivity.this
					.findViewById(R.id.errorMessage);

			try {
				JSONObject object = new JSONObject(result);

				JSONObject weather_object = null;
				int errorCode;
				String message = null;
				String status;
				try {
					weather_object = object.getJSONObject("weather");
					errorCode = weather_object.getInt("errorcode");
					status = weather_object.getString("status");
				} catch (Exception e) {
					errorCode = object.getInt("errorcode");
					message = object.getString("message");
				}

				if (errorCode == 0) {
					errorMessage.setVisibility(View.GONE);
					f.setVisibility(View.VISIBLE);
					TextView cityName = (TextView) YahooWeatherActivity.this
							.findViewById(R.id.cityName);
					TextView region_and_country = (TextView) YahooWeatherActivity.this
							.findViewById(R.id.region_and_country);
					ImageView image = (ImageView) YahooWeatherActivity.this
							.findViewById(R.id.feed_image);
					TextView feed_type = (TextView) YahooWeatherActivity.this
							.findViewById(R.id.feed_type);
					TextView temperatureText = (TextView) YahooWeatherActivity.this
							.findViewById(R.id.temperatureText);

					String imageURL = weather_object.getString("image");
					String feed = weather_object.getString("feed");
					String link = weather_object.getString("link");

					JSONObject location_object = weather_object
							.getJSONObject("location");

					String country = location_object.getString("country");
					String region = location_object.getString("region");
					String city = location_object.getString("city");
					region_and_country.setText(region + ", " + country);
					cityName.setText(city);
					JSONObject condition_object = weather_object
							.getJSONObject("condition");
					String text = condition_object.getString("text");
					String conditionTemp = condition_object.getString("temp");

					JSONObject units_object = weather_object
							.getJSONObject("units");
					String temperature = units_object.getString("temperature");

					feed_type.setText(text);
					temperatureText.setText(conditionTemp + "�" + temperature);

					JSONArray forecasatArray = weather_object
							.getJSONArray("forecast");

					TableLayout table = (TableLayout) YahooWeatherActivity.this
							.findViewById(R.id.forecast_table);
					ViewGroup container = ((ViewGroup) table.getParent());

					table.removeAllViews();
					container.invalidate();

					// new LoadImage().execute(imageURL);

					new DownloadImage().execute(imageURL);

					LayoutInflater inflater = (LayoutInflater) YahooWeatherActivity.this
							.getSystemService(YahooWeatherActivity.this.LAYOUT_INFLATER_SERVICE);

					TableRow header = (TableRow) inflater.inflate(
							R.layout.table_row, null);

					TextView day_header = ((TextView) header
							.findViewById(R.id.table_day));
					day_header.setText("Day");
					day_header.setTypeface(null, Typeface.BOLD);

					TextView weather_header = ((TextView) header
							.findViewById(R.id.table_weather));
					weather_header.setText("Weather");
					weather_header.setTypeface(null, Typeface.BOLD);

					TextView high_header = ((TextView) header
							.findViewById(R.id.table_high));
					high_header.setText("High");
					high_header.setTypeface(null, Typeface.BOLD);

					TextView lowHeader = ((TextView) header
							.findViewById(R.id.table_low));
					lowHeader.setText("Low");
					lowHeader.setTypeface(null, Typeface.BOLD);

					header.setBackgroundDrawable(getResources().getDrawable(
							R.drawable.header_style));
					table.addView(header);

					for (int i = 0; i < forecasatArray.length(); i++) {
						JSONObject forecast = forecasatArray.getJSONObject(i);
						String high = forecast.getString("high");
						String day = forecast.getString("day");
						String weather = forecast.getString("text");
						String low = forecast.getString("low");

						TableRow row = (TableRow) inflater.inflate(
								R.layout.table_row, null);
						((TextView) row.findViewById(R.id.table_high))
								.setText(high + "�" + temperature);
						((TextView) row.findViewById(R.id.table_low))
								.setText(low + "�" + temperature);
						((TextView) row.findViewById(R.id.table_weather))
								.setText(weather);
						((TextView) row.findViewById(R.id.table_day))
								.setText(day);

						if (i % 2 == 0) {
							row.setBackgroundDrawable(getResources()
									.getDrawable(R.drawable.even_row));
						} else {
							row.setBackgroundDrawable(getResources()
									.getDrawable(R.drawable.odd_row));
						}
						table.addView(row);
					}
					table.requestLayout();

				} else {
					errorMessage.setVisibility(View.VISIBLE);
					f.setVisibility(View.GONE);
					errorMessage.setText(message);
				}

			} catch (Exception e) {
				e.printStackTrace();
			}

			progressDialog.dismiss();
		}
	}

	private void setImage(Drawable drawable) {
		mImageView.setBackgroundDrawable(drawable);
	}

	public class DownloadImage extends AsyncTask<String, Integer, Drawable> {

		@Override
		protected Drawable doInBackground(String... arg0) {
			// This is done in a background thread
			return downloadImage(arg0[0]);
		}

		/**
		 * Called after the image has been downloaded -> this calls a function
		 * on the main thread again
		 */
		protected void onPostExecute(Drawable image) {
			setImage(image);
		}

		/**
		 * Actually download the Image from the _url
		 * 
		 * @param _url
		 * @return
		 */
		private Drawable downloadImage(String _url) {
			// Prepare to download image
			URL url;
			BufferedOutputStream out;
			InputStream in;
			BufferedInputStream buf;

			// BufferedInputStream buf;
			try {
				url = new URL(_url);
				in = url.openStream();

				buf = new BufferedInputStream(in);

				Bitmap bMap = BitmapFactory.decodeStream(buf);
				if (in != null) {
					in.close();
				}
				if (buf != null) {
					buf.close();
				}

				return new BitmapDrawable(bMap);

			} catch (Exception e) {
				Log.e("Error reading file", e.toString());
			}

			return null;
		}

	}

	private boolean getNetworkState() {

		return true;
	}

}
