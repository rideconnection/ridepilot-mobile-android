package org.openplans.rcavl;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

public class ConfigDialog extends Dialog {
	public interface Configured {
		public void setConfig(String serverUrl);
		public String getConfig();
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
		urlField.setText(configured.getConfig());

		View saveButton = findViewById(R.id.saveButton);
		saveButton.setOnClickListener(new android.view.View.OnClickListener() {

			public void onClick(View v) {
				String apiRequestUrl = urlField.getText().toString();
				if (apiRequestUrl.endsWith("/")) {
					apiRequestUrl = apiRequestUrl.substring(0, apiRequestUrl.length() - 1);
				}
				configured.setConfig(apiRequestUrl);
				ConfigDialog.this.dismiss();
			}
		});

		View cancelButton = findViewById(R.id.cancelButton);
		cancelButton.setOnClickListener(new android.view.View.OnClickListener() {

			public void onClick(View v) {
				ConfigDialog.this.dismiss();
			}
		});
	}
}
