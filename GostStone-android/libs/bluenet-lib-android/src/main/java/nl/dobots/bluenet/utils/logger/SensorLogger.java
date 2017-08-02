package nl.dobots.bluenet.utils.logger;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.display.DisplayManager;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;
import android.view.Display;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import nl.dobots.bluenet.ble.extended.structs.BleDevice;
import nl.dobots.bluenet.ble.extended.callbacks.EventListener;
import nl.dobots.bluenet.service.callbacks.IntervalScanListener;
import nl.dobots.bluenet.service.callbacks.ScanDeviceListener;

/**
 * Copyright (c) 2015 Bart van Vliet <bart@dobots.nl>. All rights reserved.
 * <p/>
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 3, as
 * published by the Free Software Foundation.
 * <p/>
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 3 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 * <p/>
 * Created on 15-10-15
 *
 * @author Bart van Vliet
 */
public class SensorLogger extends BroadcastReceiver implements ScanDeviceListener, IntervalScanListener, EventListener  {
	private static final String TAG = SensorLogger.class.getCanonicalName();
	private static final int FLUSH_INTERVAL = 1000;
	private static final int COMPASS_AVERAGE_WINDOW = 3;

	private Context _context;

	private SensorManager _sensorManager;
	private Sensor _stepSensor;
	private Sensor _stepCountSensor;
	private Sensor _acceleroSensor;
	private Sensor _magnetoSensor;
	private List<float[]> _orientationHistory;
	private float[] _gravity;
	private float[] _geoMagnetic;

	public enum BleLogEvent {
		start,
		stop,
		onScan,
		startScan,
		stopScan,
		appStart,
		appForeGround,
		appBackGround,
		setLocation,
		stepDetected,
		stepCount,
		orientation,
		bluetoothState,
		bluetoothError,
		phoneInteractive,
	}

	private BufferedWriter _bufferedWriter = null;
	private boolean _initialized = false;
	private File _path;
	private File _logFile;
	private HandlerThread _handlerThread;
	private Handler _handler;

	// TODO: include date in filename, so that we get a file per day

	synchronized public void init(Context context, String logFileName) {
		_context = context;
		if (isExternalStorageWritable()) {
			// TODO: use external storage if possible
			Log.d(TAG, "Can write to external storage!");
			// Requires no permissions, but gets removed when app is uninstalled
//			path = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
//			path = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
			// Does not get removed when app is uninstalled
			_path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
//			path = Environment.getExternalStorageDirectory();
		}
		else {
			// Requires no permissions, but gets removed when app is uninstalled
			_path = context.getFilesDir();
		}
		//path = new File(path, "logs");

		if (!_path.exists() && !_path.mkdirs()) {
			Log.e(TAG, "Directory not created: " + _path.getAbsolutePath());
			return;
		}
		_logFile = new File(_path, logFileName);
		Log.d(TAG, "Logging to file: " + _logFile.getAbsolutePath());

		if (!_logFile.exists()) {
			try {
				Log.d(TAG, "Creating new file: " + _logFile.getAbsolutePath());
				_logFile.createNewFile();
			}
			catch (IOException e) {
				// TODO: handle exception
				Log.w(TAG, "Failed to create file: " + _logFile.getAbsolutePath());
				e.printStackTrace();
				return;
			}
		}
		init();
	}

	synchronized private void init() {
		try {
			_bufferedWriter = new BufferedWriter(new FileWriter(_logFile, true));

			_handlerThread = new HandlerThread("FileWriter");
			_handlerThread.start();
			_handler = new android.os.Handler(_handlerThread.getLooper());
			_handler.postDelayed(_flushRunnable, FLUSH_INTERVAL);

			initSensors();
			initBroadcastReceiver();

			_initialized = true;
			logLine(BleLogEvent.start);
			Log.d(TAG, "initialized");
		}
		catch (IOException e) {
			// TODO: handle exception
			e.printStackTrace();
			return;
		}
	}

	synchronized public void deinit() {
		Log.d(TAG, "deinitializing..");
		if (_initialized) {
			try {
				deinitSensors();
				deinitBroadcastReceiver();

				logLine(BleLogEvent.stop);

				// Closes the stream, flushing it first.
				_bufferedWriter.close();

				_handlerThread.quit();

				_initialized = false;
				Log.d(TAG, "deinitialized");
			} catch (IOException e) {
				// TODO: handle exception
				e.printStackTrace();
				return;
			}
		}
	}

	/* Checks if external storage is available for read and write */
	private boolean isExternalStorageWritable() {
		if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
			return true;
		}
		return false;
	}


	synchronized public void flush() {
		if (_initialized) {
			try {
				_bufferedWriter.flush();
			} catch (IOException e) {
				// TODO: handle exception
				e.printStackTrace();
			}
		}
	}

	public void logLine(BleLogEvent event) {
		logLine(event, "");
	}

	public void logLine(BleLogEvent event, String text) {
		logLine(System.currentTimeMillis(), event, text);
	}

	synchronized public void logLine(long timeStampMs, BleLogEvent event, String text) {
		if (!_initialized) {
			return;
		}
		try {
			_bufferedWriter.append(timeStampMs + " " + event.toString() + " " + text);
			_bufferedWriter.append('\n');
		} catch (IOException e) {
			// TODO: handle exception
			e.printStackTrace();
		}
	}

	private Runnable _flushRunnable = new Runnable() {
		@Override
		public void run() {
			flush();
			_handler.postDelayed(this, FLUSH_INTERVAL);
		}
	};


	/////////////
	// Sensors //
	/////////////

	void initSensors() {
		_sensorManager = (SensorManager) _context.getSystemService(Context.SENSOR_SERVICE);

		if (!_context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_SENSOR_STEP_DETECTOR)) {
			Log.w(TAG, "No step detector!");
		}
		else {
			Log.d(TAG, "Has step detector");
			_stepSensor = _sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
//			_sensorManager.requestTriggerSensor(_triggerEventListener, _stepSensor);
			_sensorManager.registerListener(_sensorEventListener, _stepSensor, SensorManager.SENSOR_DELAY_NORMAL);
		}

		if (!_context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_SENSOR_STEP_COUNTER)) {
			Log.w(TAG, "No step counter!");
		}
		else {
			Log.d(TAG, "Has step counter");
			_stepCountSensor = _sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
			_sensorManager.registerListener(_sensorEventListener, _stepCountSensor, SensorManager.SENSOR_DELAY_NORMAL);
		}

		_acceleroSensor = _sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		_magnetoSensor = _sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
		_orientationHistory = new ArrayList<>();

		_sensorManager.registerListener(_sensorEventListener, _acceleroSensor, SensorManager.SENSOR_DELAY_NORMAL);
		_sensorManager.registerListener(_sensorEventListener, _magnetoSensor, SensorManager.SENSOR_DELAY_NORMAL);

	}

	void deinitSensors() {
		if (_sensorManager == null) {
			return;
		}

		_sensorManager.unregisterListener(_sensorEventListener);
	}

//	private TriggerEventListener _triggerEventListener = new TriggerEventListener() {
//		@Override
//		public void onTrigger(TriggerEvent event) {
//			Log.d(TAG, "Sensor event " + event.toString());
//			logLine(BleLogEvent.stepDetected);
//		}
//	};

	private SensorEventListener _sensorEventListener = new SensorEventListener() {
		@Override
		public void onSensorChanged(SensorEvent event) {
			if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
				_gravity = event.values;
			}
			else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
				_geoMagnetic = event.values;
			}
			else if (event.sensor.getType() == Sensor.TYPE_STEP_DETECTOR) {
				// Event timestamp is recorded in us accuracy, compare with: SystemClock.elapsedRealtimeNanos()
				long timeStampMs = System.currentTimeMillis() - ((SystemClock.elapsedRealtimeNanos() - event.timestamp) / 1000000L);
				logLine(timeStampMs, BleLogEvent.stepDetected, "");
			}
			else if (event.sensor.getType() == Sensor.TYPE_STEP_COUNTER) {
				long timeStampMs = System.currentTimeMillis() - ((SystemClock.elapsedRealtimeNanos() - event.timestamp) / 1000000L);
				logLine(timeStampMs, BleLogEvent.stepCount, "" + event.values[0]);
			}
			else {
				return;
			}
			if (_gravity != null && _geoMagnetic != null) {
				float R[] = new float[9];
				float I[] = new float[9];
				boolean success = SensorManager.getRotationMatrix(R, I, _gravity, _geoMagnetic);
				if (success) {
					float orientation[] = new float[3];
					SensorManager.getOrientation(R, orientation); // orientation contains: azimut, pitch and roll
//					Log.d(TAG, "orientation = " + orientation[0] + " " + orientation[1] + " " + orientation[2]);

					_orientationHistory.add(orientation);
					if (_orientationHistory.size() >= COMPASS_AVERAGE_WINDOW) {
						int size = _orientationHistory.size();
						// Calculate average angle
						float[] prevOrientation = _orientationHistory.get(0);
						float[] sum = prevOrientation.clone();
						for (int i=1; i<size; ++i) {
							for (int j=0; j<3; j++) {
								float diff = (_orientationHistory.get(i))[j] - prevOrientation[j];
								// Make sure -pi < diff < pi
								while (diff > Math.PI) {
									diff -= (float) (2 * Math.PI);
								}
								while (diff < -Math.PI) {
									diff += (float) (2 * Math.PI);
								}
								prevOrientation[j] += diff;
								sum[j] += prevOrientation[j];
							}
						}
						float[] average = new float[3];
						for (int j=0; j<3; j++) {
							average[j] = sum[j] / size;
							// Make sure angle is between -pi and pi
							while (average[j] > Math.PI) {
								average[j] -= 2 * Math.PI;
							}
							while (average[j] < -Math.PI) {
								average[j] += 2 * Math.PI;
							}
						}
						logLine(BleLogEvent.orientation, average[0] + " " + average[1] + " " + average[2]);
//						Log.d(TAG, "orientation average = " + average[0] + " " + average[1] + " " + average[2]);
						_orientationHistory.clear();
					}
				}
			}
		}

		@Override
		public void onAccuracyChanged(Sensor sensor, int accuracy) {

		}
	};



	/////////////////////
	// Ble scan events //
	/////////////////////

	@Override
	public void onDeviceScanned(BleDevice device) {
		String text = device.getAddress() + " " + device.getRssi() + " " + device.getCalibratedRssi();
		logLine(BleLogEvent.onScan, text);
		Log.d(TAG, text);
	}

	@Override
	public void onScanStart() {
		Log.d(TAG, "onScanStart");
		logLine(BleLogEvent.startScan);
	}

	@Override
	public void onScanEnd() {
		Log.d(TAG, "onScanEnd");
		logLine(BleLogEvent.stopScan);
	}

	@Override
	public void onEvent(Event event) {
		switch (event) {
			case BLUETOOTH_TURNED_OFF:
			case BLUETOOTH_TURNED_ON:
			case BLUETOOTH_NOT_ENABLED:
				logLine(BleLogEvent.bluetoothState, event.toString());
				break;
			case BLUETOOTH_START_SCAN_ERROR:
			case BLUETOOTH_STOP_SCAN_ERROR:
				logLine(BleLogEvent.bluetoothError, event.toString());
				break;
		}
	}


	////////////////////////
	// Power state events //
	////////////////////////

	DisplayManager _displayManager;
	Display _display;

	void initBroadcastReceiver() {
		_displayManager = (DisplayManager) _context.getSystemService(Context.DISPLAY_SERVICE);
		_display = _displayManager.getDisplay(Display.DEFAULT_DISPLAY);
		IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
		filter.addAction(Intent.ACTION_SCREEN_OFF);
		_context.registerReceiver(this, filter);
	}

	void deinitBroadcastReceiver() {
		_context.unregisterReceiver(this);
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
			// This broadcast is sent when the device becomes non-interactive which may have nothing to do with the screen turning off.

/* TODO: this uses API level 20, but would be nice to have, check https://developer.android.com/about/dashboards/index.html
			String displayState;
			switch (_display.getState()) {
				case Display.STATE_OFF:
					displayState = "OFF";
					break;
				case Display.STATE_ON:
					displayState = "ON";
					break;
				case Display.STATE_DOZE_SUSPEND:
					displayState = "DOZE_SUSPEND";
					break;
				case Display.STATE_UNKNOWN:
				default:
					displayState = "UNKNOWN";
					break;
			}
			logLine(BleLogEvent.phoneInteractive, "0 " + displayState);
*/
			logLine(BleLogEvent.phoneInteractive, "0");
		}
		else if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
			// This broadcast is sent when the device becomes interactive which may have nothing to do with the screen turning on.
			logLine(BleLogEvent.phoneInteractive, "1");
		}
	}

}
