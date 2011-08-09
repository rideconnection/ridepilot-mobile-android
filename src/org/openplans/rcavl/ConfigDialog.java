package org.openplans.rcavl;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

public class ConfigDialog extends Dialog {
	public interface Configured {
		public void setConfig(String serverUrl, int pingInterval);

		public String getServerUrl();

		public int getPingInterval();
	}

	Configured configured;

	public ConfigDialog(Context context) {
		super(context);
	}

	public void setConfigured(Configured configured) {
		this.configured = configured;
	}

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.config_dialog);
		setTitle(R.string.config);

		final EditText urlField = (EditText) findViewById(R.id.serverUrlField);
		urlField.setText(configured.getServerUrl());

		final NumberPicker minutePicker = (NumberPicker) findViewById(R.id.minutePicker);
		final NumberPicker secondPicker = (NumberPicker) findViewById(R.id.secondPicker);

		minutePicker.setValue(configured.getPingInterval() / 60);
		secondPicker.setValue(configured.getPingInterval() % 60);

		View saveButton = findViewById(R.id.saveButton);
		saveButton.setOnClickListener(new android.view.View.OnClickListener() {

			public void onClick(View v) {
				String apiRequestUrl = urlField.getText().toString();
				if (apiRequestUrl.endsWith("/")) {
					apiRequestUrl = apiRequestUrl.substring(0,
							apiRequestUrl.length() - 1);
				}

				int pingInterval = minutePicker.getValue() * 60
						+ secondPicker.getValue();
				configured.setConfig(apiRequestUrl, pingInterval);
				ConfigDialog.this.dismiss();
			}
		});

		View cancelButton = findViewById(R.id.cancelButton);
		cancelButton
				.setOnClickListener(new android.view.View.OnClickListener() {

					public void onClick(View v) {
						ConfigDialog.this.dismiss();
					}
				});
	}
}
