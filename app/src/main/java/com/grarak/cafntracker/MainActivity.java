/*
 * Copyright (C) 2016 Willi Ye
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
package com.grarak.cafntracker;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.google.firebase.iid.FirebaseInstanceId;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.util.LinkedHashMap;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Sending token");
        progressDialog.setCancelable(false);
        progressDialog.show();

        new AsyncTask<Void, Void, Void>() {

            private boolean fail;
            private LinkedHashMap<String, String> mRepos = new LinkedHashMap<>();

            @Override

            protected Void doInBackground(Void... voids) {
                String t = FirebaseInstanceId.getInstance().getToken();
                byte[] token;
                if (t != null) {
                    token = t.getBytes();
                } else {
                    Log.e(MainActivity.this.getClass().getSimpleName(), "Failed to get token");
                    fail = true;
                    return null;
                }

                SSLContext sc;
                try {
                    KeyStore ks = KeyStore.getInstance("PKCS12");
                    ks.load(getResources().getAssets().open("keystore"), "somepass".toCharArray());

                    KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                    kmf.init(ks, "somepass".toCharArray());

                    TrustManagerFactory tmf = TrustManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                    tmf.init(ks);

                    sc = SSLContext.getInstance("TLS");
                    TrustManager[] trustManagers = tmf.getTrustManagers();
                    sc.init(kmf.getKeyManagers(), trustManagers, null);
                } catch (Exception e) {
                    e.printStackTrace();
                    fail = true;
                    return null;
                }

                try {
                    SSLSocketFactory ssf = sc.getSocketFactory();
                    SSLSocket socket = (SSLSocket) ssf.createSocket("10.0.0.14", 5000);
                    socket.startHandshake();

                    DataOutputStream dataOutputStream = new DataOutputStream(socket.getOutputStream());
                    dataOutputStream.write(token);
                    dataOutputStream.flush();

                    DataInputStream dataInputStream = new DataInputStream(socket.getInputStream());
                    byte[] buffer = new byte[8192];
                    dataInputStream.read(buffer);
                    String repos = new String(buffer);

                    JSONArray jsonArray = new JSONArray(repos);
                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject object = jsonArray.getJSONObject(i);
                        mRepos.put(object.getString("name"), object.getString("content_name"));
                    }

                    dataOutputStream.close();
                    dataInputStream.close();
                    socket.close();
                } catch (IOException | JSONException e) {
                    e.printStackTrace();
                    fail = true;
                    return null;
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                super.onPostExecute(aVoid);
                progressDialog.dismiss();

                if (fail) {
                    Toast.makeText(MainActivity.this, "Couldn't send token to server", Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }

                JSONArray array = new JSONArray();
                for (String name : mRepos.keySet()) {
                    array.put(name);
                }

                setContentView(R.layout.activity_main);
                getFragmentManager().beginTransaction()
                        .replace(R.id.content_frame, MainFragment.newInstance(mRepos)).commit();
            }
        }.execute();
    }

    public static class MainFragment extends PreferenceFragment {

        public static MainFragment newInstance(LinkedHashMap<String, String> repos) {
            MainFragment fragment = new MainFragment();
            fragment.mRepos = repos;
            return fragment;
        }

        private LinkedHashMap<String, String> mRepos;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            PreferenceScreen preferenceScreen = getPreferenceManager().createPreferenceScreen(getActivity());
            for (final String name : mRepos.keySet()) {
                PreferenceCategory category = new PreferenceCategory(getActivity());
                category.setTitle(name);

                preferenceScreen.addPreference(category);

                SwitchPreference switchCompat = new SwitchPreference(getActivity());
                switchCompat.setSummary("Receive notification");
                switchCompat.setChecked(PreferenceManager
                        .getDefaultSharedPreferences(getActivity()).getBoolean(name, false));
                switchCompat.setKey(name);

                category.addPreference(switchCompat);

                Preference filter = new Preference(getActivity());
                filter.setTitle("Filter (leave blank to receive all)");
                filter.setSummary(PreferenceManager.getDefaultSharedPreferences(getActivity())
                        .getString(name + "_filter", ""));
                filter.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(final Preference preference) {
                        FrameLayout frameLayout = new FrameLayout(getActivity());
                        int padding = Math.round(getResources().getDisplayMetrics().density * 30);
                        frameLayout.setPadding(padding, padding, padding, padding);

                        final EditText editText = new EditText(getActivity());
                        editText.setHint(mRepos.get(name));
                        editText.setMaxLines(1);
                        editText.setLayoutParams(new ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT));
                        frameLayout.addView(editText);

                        new AlertDialog.Builder(getActivity()).setView(frameLayout)
                                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                    }
                                }).setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                preference.setSummary(editText.getText());
                                PreferenceManager.getDefaultSharedPreferences(getActivity()).edit()
                                        .putString(name + "_filter", editText.getText().toString()).apply();
                            }
                        }).show();
                        return true;
                    }
                });

                category.addPreference(filter);
            }
            setPreferenceScreen(preferenceScreen);
        }
    }

}
