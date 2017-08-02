package nl.dobots.bluenet.ble.core;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;

public class LocationRequest extends AppCompatActivity {

	private static final String TAG = LocationRequest.class.getCanonicalName();

	static private boolean dialogShown = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// do not need to set a content view, we just use this activity to show the
		// alert dialog

		if (!dialogShown) {
			dialogShown = true;
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle("Location not enabled");  // GPS not found
			builder.setMessage("We need Location enabled to scan for devices"); // Want to enable?
			builder.setPositiveButton("Settings", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialogInterface, int i) {
					dialogShown = false;
					finish();
					Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
					startActivity(intent);
				}
			});
			builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					dialogShown = false;
					finish();
				}
			});
			builder.setCancelable(true);
			builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
				@Override
				public void onCancel(DialogInterface dialog) {
					dialogShown = false;
					finish();
				}
			});
			builder.create().show();

		} else {
			finish();
		}
	}

}
