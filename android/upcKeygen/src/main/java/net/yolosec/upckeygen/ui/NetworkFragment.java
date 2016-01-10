/*
 * Copyright 2012 Rui Araújo, Luís Fonseca
 *
 * This file is part of Router Keygen.
 *
 * Router Keygen is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Router Keygen is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Router Keygen.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yolosec.upckeygen.ui;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.AsyncTask.Status;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewSwitcher;

import net.yolosec.upckeygen.BuildConfig;
import net.yolosec.upckeygen.R;
import net.yolosec.upckeygen.algorithms.Keygen;
import net.yolosec.upckeygen.algorithms.KeygenMonitor;
import net.yolosec.upckeygen.algorithms.WiFiNetwork;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class NetworkFragment extends Fragment {
	public static final String NETWORK_ID = "vulnerable_network";
	private static final String TAG = "NetworkFragment";
	private static final String PASSWORD_LIST = "password_list";
	private WiFiNetwork wifiNetwork;
	private KeygenThread thread;
	private ViewSwitcher root;
	private TextView messages;
	private List<String> passwordList;

	public NetworkFragment() {
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (getArguments().containsKey(NETWORK_ID)) {
			wifiNetwork = getArguments().getParcelable(NETWORK_ID);
			restoreMissingKeygens();
			thread = new KeygenThread(wifiNetwork);
		}
		if (savedInstanceState != null) {
			String[] passwords = savedInstanceState
					.getStringArray(PASSWORD_LIST);
			if (passwords != null) {
				passwordList = new ArrayList<>();
				passwordList.addAll(Arrays.asList(passwords));
			}
		}
		setHasOptionsMenu(true);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
							 Bundle savedInstanceState) {
		root = (ViewSwitcher) inflater.inflate(R.layout.fragment_network,
				container, false);
		messages = (TextView) root.findViewById(R.id.loading_text);
		final View autoConnect = root.findViewById(R.id.auto_connect);
		// Auto connect service unavailable for manual calculations
		if (wifiNetwork.getScanResult() == null)
			autoConnect.setVisibility(View.GONE);
		else {
			final int level = wifiNetwork.getLevel();
		}
		if (passwordList != null)
			displayResults();
		return root;
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		if (passwordList != null) {
			outState.putStringArray(PASSWORD_LIST,
					passwordList.toArray(new String[passwordList.size()]));
		}
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		if (passwordList == null) {
			if (thread.getStatus() == Status.FINISHED
					|| thread.getStatus() == Status.RUNNING)
				thread = new KeygenThread(wifiNetwork);
			if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.GINGERBREAD_MR1) {
				thread.execute();
			} else {
				thread.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
			}
		}
	}

	@Override
	public void onDetach() {
		super.onDetach();
		if (thread != null) {
			//This thread can be null if there was a previosly calculated
			//password list
			thread.cancel();
		}
	}

	/**
	 * Some devices seem to have bugs with the parcelable implementation
	 * So we try to restore missing objects here.
	 */
	private void restoreMissingKeygens() {
		boolean foundMissingKeygen = false;
		for (Keygen keygen : wifiNetwork.getKeygens()) {
			if (keygen == null) {
				foundMissingKeygen = true;
				break;
			}
		}
		if (foundMissingKeygen) {
			//If any is missing, simply replace them all.
			wifiNetwork.setKeygens(null);
		}
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		if (wifiNetwork.getSupportState() != Keygen.UNSUPPORTED)
			inflater.inflate(R.menu.share_keys, menu);
		super.onCreateOptionsMenu(menu, inflater);
	}

	private String buildShareString(){
		final StringBuilder message = new StringBuilder(wifiNetwork.getSsidName());
		message.append("\n");
		for (String password : passwordList) {
			message.append(password);
			message.append('\n');
		}
		return message.toString();
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.menu_share:
				try {
					if (passwordList == null)
						return true;
					Intent i = new Intent(Intent.ACTION_SEND);
					i.setType("text/plain");
					i.putExtra(Intent.EXTRA_SUBJECT, wifiNetwork.getSsidName()
							+ getString(R.string.share_msg_begin));
					i.putExtra(Intent.EXTRA_TEXT, buildShareString());
					startActivity(Intent.createChooser(i,
							getString(R.string.share_title)));
				} catch (Exception e) {
					Toast.makeText(getActivity(), R.string.msg_err_sendto,
							Toast.LENGTH_SHORT).show();
				}
				return true;
			case R.id.menu_save_sd:
				if (passwordList == null)
					return true;

				final String toCopy = buildShareString();
				try {
					if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.HONEYCOMB) {
						android.text.ClipboardManager clipboard = (android.text.ClipboardManager) getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
						clipboard.setText(toCopy);
					} else {
						android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
						android.content.ClipData clip = android.content.ClipData.newPlainText("Copied Text", toCopy);
						clipboard.setPrimaryClip(clip);
					}

					Toast.makeText(
							getActivity(),
							getString(R.string.msg_copied, wifiNetwork.getSsidName()),
							Toast.LENGTH_SHORT).show();
				} catch(Exception e){
					Log.e(TAG, "Copy to clipboard failed", e);
				}
				return true;
		}
		return super.onOptionsItemSelected(item);
	}

	private void displayResults() {
		if (passwordList.isEmpty()) {
			root.findViewById(R.id.loading_spinner).setVisibility(View.GONE);
			messages.setText(R.string.msg_errnomatches);
		} else {
			final ListView list = (ListView) root.findViewById(R.id.list_keys);
			list.setOnItemClickListener(new OnItemClickListener() {
				public void onItemClick(AdapterView<?> parent, View view,
										int position, long id) {
					final String key = ((TextView) view).getText().toString();
					Toast.makeText(getActivity(),
							getString(R.string.msg_copied, key),
							Toast.LENGTH_SHORT).show();

					if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.HONEYCOMB) {
						android.text.ClipboardManager clipboard = (android.text.ClipboardManager) getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
						clipboard.setText(key);
					} else {
						android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
						android.content.ClipData clip = android.content.ClipData.newPlainText("key", key);
						clipboard.setPrimaryClip(clip);
					}

					openWifiSettings();
				}
			});
			list.setAdapter(new ArrayAdapter<>(getActivity(),
					android.R.layout.simple_list_item_1, passwordList));
			root.showNext();
		}
	}

	/**
	 * Try to open wifi settings activity.
	 * Tries to different actions.
	 */
	private void openWifiSettings(){
		Intent intent = new Intent(Settings.ACTION_WIFI_SETTINGS);
		final PackageManager packageManager = getActivity().getPackageManager();
		if (packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null) {
			startActivity(intent);
			return;
		}
		intent = new Intent(Settings.ACTION_WIRELESS_SETTINGS);
		if (packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null) {
			startActivity(intent);
		}
	}

	private void getPrefs() {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
	}

	private class KeygenThread extends AsyncTask<Void, Integer, List<String>> implements KeygenMonitor {
		private final static int SHOW_TOAST = 0;
		private final static int SHOW_MESSAGE_WITH_SPINNER = 1;
		private final static int SHOW_MESSAGE_NO_SPINNER = 2;
		private final static int CHANGE_DETERMINATE = 3;
		private final static int KEYGEN_PROGRESSED = 4;
		private final static int KEY_COMPUTED = 5;
		private final WiFiNetwork wifiNetwork;
		private volatile int numKeys = 0;
		private boolean spinnerDeterminate = false;

		private KeygenThread(WiFiNetwork wifiNetwork) {
			this.wifiNetwork = wifiNetwork;
		}

		@Override
		protected void onPostExecute(List<String> result) {
			if (getActivity() == null)
				return;
			if (result == null)
				return;
			passwordList = result;
			displayResults();
		}

		@Override
		protected void onPreExecute() {
			if (wifiNetwork.getSupportState() == Keygen.UNSUPPORTED) {
				root.findViewById(R.id.loading_spinner)
						.setVisibility(View.GONE);
				messages.setText(R.string.msg_unspported);
				cancel(true);
			}
		}

		@Override
		protected void onProgressUpdate(Integer... values) {
			if (getActivity() == null)
				return;
			for (int i = 0; i < values.length; i += 2) {
				switch (values[i]) {
					case SHOW_TOAST:
						Toast.makeText(getActivity(), values[i + 1],
								Toast.LENGTH_SHORT).show();

						break;
					case SHOW_MESSAGE_NO_SPINNER:
						messages.setText(values[i + 1]);
						root.findViewById(R.id.loading_spinner).setVisibility(
								View.GONE);
						break;

					case SHOW_MESSAGE_WITH_SPINNER:
						messages.setText(values[i + 1]);
						root.findViewById(R.id.loading_spinner).setVisibility(
								View.VISIBLE);
						break;

					case CHANGE_DETERMINATE: {
						spinnerDeterminate = values[i + 1] > 0;
						final ProgressBar spinner = (ProgressBar)root.findViewById(R.id.loading_spinner);
						final ProgressBar progressBar = (ProgressBar)root.findViewById(R.id.loading_progress);
						spinner.setVisibility(spinnerDeterminate ? View.GONE : View.VISIBLE);
						progressBar.setVisibility(spinnerDeterminate ? View.VISIBLE : View.GONE);
						if (spinnerDeterminate) {
							progressBar.setMax(1000);
							progressBar.setProgress(0);
						}
					}
					break;

					case KEY_COMPUTED:
						messages.setText(
								NetworkFragment.this.getString(R.string.dialog_nativecalc_msg) + " " +
										NetworkFragment.this.getString(R.string.key_computed, values[i + 1])
						);
						break;

					case KEYGEN_PROGRESSED:{
						if (spinnerDeterminate){
							final ProgressBar spinner = (ProgressBar)root.findViewById(R.id.loading_progress);
							spinner.setProgress(values[i+1]);
						}
					}
					break;
				}
			}
		}

		public void cancel() {
			for (Keygen keygen : wifiNetwork.getKeygens())
				keygen.setStopRequested(true);
			cancel(true);
		}

		@Override
		protected List<String> doInBackground(Void... params) {
			final List<String> result = new ArrayList<>();
			for (Keygen keygen : wifiNetwork.getKeygens()) {
				try {
					final List<String> keygenResult = calcKeys(keygen);
					if (keygenResult != null)
						result.addAll(keygenResult);
				} catch (Exception e) {
					Log.e(NetworkFragment.class.getSimpleName(), String.format("Error, ssid=%s, mac=%s", wifiNetwork.getSsidName(), wifiNetwork.getMacAddress()));
				}
			}
			return result;
		}

		private List<String> calcKeys(Keygen keygen) {
			if (keygen.keygenSupportsProgress()){
				keygen.setMonitor(this);
				publishProgress(CHANGE_DETERMINATE, 1);
			} else {
				publishProgress(CHANGE_DETERMINATE, 0);
			}

			long begin = System.currentTimeMillis();
			final List<String> result = keygen.getKeys();
			long end = System.currentTimeMillis() - begin;
			if (BuildConfig.DEBUG)
				Log.d(TAG, "Time to solve:" + end);

			final int errorCode = keygen.getErrorCode();
			if (errorCode != 0) {
				if (result == null)
					publishProgress(SHOW_MESSAGE_NO_SPINNER, errorCode);
				else
					publishProgress(SHOW_TOAST, errorCode);
			}
			return result;
		}

		@Override
		public void onKeyComputed() {
			numKeys += 1;
			publishProgress(KEY_COMPUTED, numKeys);
		}

		@Override
		public void onKeygenProgressed(double progress) {
			publishProgress(KEYGEN_PROGRESSED, (int)(progress*1000));
		}
	}
}
