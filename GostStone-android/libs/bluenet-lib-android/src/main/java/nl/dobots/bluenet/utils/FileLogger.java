package nl.dobots.bluenet.utils;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Semaphore;

import nl.dobots.bluenet.ble.core.callbacks.IStatusCallback;

/**
 * Copyright (c) 2017 Dominik Egger <dominik@dobots.nl>. All rights reserved.
 * <p>
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 3, as
 * published by the Free Software Foundation.
 * <p>
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 3 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 * <p>
 * Created on 24-1-17
 *
 * Use:
 *
 * 1. Create FileLogger object
 * 2. Call checkPermissions, if result is true, continue with step 6, else
 * 3. Call requestPermissions with Activity
 * 4. In Activity, overload onRequestPermissionsResult and call there the
 *    handlePermissionResult of the FileLogger
 * 5. If handlePermissionResult calls the status callback's onSuccess, continue with step 6, else
 *    abort because we can't log to file without write permissions
 * 6. Assign FileLogger to BleLog with BleLog.addFileLogger();
 * 7. Done
 *
 * Note:
 *
 * Files are created per day, up to a max of MAX_LOG_FILE_SIZE. Once a log file exceeds that limit,
 * a new file is created.
 * If the free disk space drops below MIN_FREE_SPACE, logging to file will be disabled
 *
 * @author Dominik Egger <dominik@dobots.nl>
 */
public class FileLogger {

	public static final String TAG = "FileLogger";

	private static final int PERMISSIONS_REQUEST_WRITE_STORAGE = 105;

	private static final long MIN_FREE_SPACE = 10 * 1024 * 1024; // 10 MB
	private static final long MAX_LOG_FILE_SIZE = 500 * 1024 * 1024; // 500 MB
	private static final int  MAX_LOG_DAYS = 3; // Only keep up logs of the last 3 days

	private static final long CLEANUP_INTERVAL_MS = 60*1000; // Clean up every minute

	// log level identifier used by android log in logcat, first two are not used
	private static final char LOG_LEVELS_STR[] = { ' ', ' ', 'V', 'D', 'I', 'W', 'E' };

	private static final SimpleDateFormat _logTimestampFormat = new SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss.SSS", Locale.ENGLISH);
	private static final SimpleDateFormat _fileNameTimestampFormat = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ENGLISH);
	private static final String _filenamePrefix = "log_";
	private static final String _filenamePostfix = ".txt";


	private boolean _hasWritePermissions;
	private DataOutputStream _logFileDos;
	private Date _logFileDate;
	private File _logFile;
	private File _logDir;
	private boolean _enabled = true;

	private Handler _handler;

	public FileLogger(Context context) {
		_logDir = context.getExternalFilesDir(null);
		_hasWritePermissions = true;

		HandlerThread handlerThread = new HandlerThread("FileLogger");
		handlerThread.start();
		_handler = new Handler(handlerThread.getLooper());
		_handler.postDelayed(_cleanupRunnable, CLEANUP_INTERVAL_MS);
	}

	public boolean checkPermissions(Activity context) {
		if (Build.VERSION.SDK_INT >= 23) {
			_hasWritePermissions = ContextCompat.checkSelfPermission(context,
					Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
		} else {
			_hasWritePermissions = true;
		}
		return _hasWritePermissions;
	}

	public boolean hasRequestedPermissions() {
		return _hasWritePermissions;
	}

	public void requestPermissions(Activity context) {
		ActivityCompat.requestPermissions(context,
				new String[] {Manifest.permission.WRITE_EXTERNAL_STORAGE},
				PERMISSIONS_REQUEST_WRITE_STORAGE);
	}

	public boolean handlePermissionResult(int requestCode, String[] permissions, int[] grantResults, IStatusCallback callback) {
		switch (requestCode) {
			case PERMISSIONS_REQUEST_WRITE_STORAGE: {
				if (grantResults.length > 0 &&	grantResults[0] == PackageManager.PERMISSION_GRANTED) {
					_hasWritePermissions = true;
					callback.onSuccess();
				} else {
					callback.onError(PackageManager.PERMISSION_DENIED);
				}
				return true;
			}
		}
		return false;
	}

	public void enable(boolean enable) {
		_enabled = enable;
	}

	public boolean isEnabled() {
		return _enabled;
	}


	public void logToFile(int level, String tag, String line) {
		if (_enabled && checkFile()) {
			try {
				String log = String.format("%s %s/%s: %s\r\n",
						_logTimestampFormat.format(new Date()),
						LOG_LEVELS_STR[level],
						tag,
						line);
				_logFileDos.write(log.getBytes());
			} catch (IOException e) {
				Log.e(TAG, "Failed to write to log file");
				e.printStackTrace();
			}
		}
	}

	private boolean createLogFile() {

		_logFileDate = new Date();
		String fileName = _filenamePrefix + _fileNameTimestampFormat.format(_logFileDate) + _filenamePostfix;

//		File path = new File(Environment.getExternalStorageDirectory().getPath() + "/" + _logDir);
		_logFile = new File(_logDir, fileName);

//		path.mkdirs();
		try {
			_logFileDos = new DataOutputStream(new FileOutputStream(_logFile));
		} catch (FileNotFoundException e) {
			Log.e(TAG, "Error creating " + fileName, e);
			return false;
		}

		return true;
	}

	private Semaphore _semaphore = new Semaphore(1);

	private boolean checkFile() {

		try {
			_semaphore.acquire();

			boolean result = true;

			if (_logFileDos == null) {
				result = createLogFile();
			} else {
				try {
					if (_logFileDate.getDay() != new Date().getDay()) {
						_logFileDos.close();
						result = createLogFile();
					}

					if (_logFile.length() > MAX_LOG_FILE_SIZE) {
						_logFileDos.close();
						result = createLogFile();
					}

					if (_logFile.getFreeSpace() < MIN_FREE_SPACE) {
						_logFileDos.close();
						_enabled = false;
						result = false;
					}
				} catch (IOException e) {
					Log.e("BleLog", "Error closing logfile", e);
					result = false;
				}
			}

			_semaphore.release();

			return result;

		} catch (InterruptedException e) {
			Log.e("BleLog", "Failed to acquire semaphore", e);

			return false;
		}

	}

	private Runnable _cleanupRunnable = new Runnable() {
		@Override
		public void run() {
			cleanupFiles();
			_handler.postDelayed(_cleanupRunnable, CLEANUP_INTERVAL_MS);
		}
	};

	private File[] getLogFiles() {
		return _logDir.listFiles(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String filename) {
				if (!filename.startsWith(_filenamePrefix) || !filename.endsWith(_filenamePostfix)) {
					return false;
				}
				return true;
			}
		});
	}

	private boolean cleanupFiles() {
//		File[] files = _logDir.listFiles(new FilenameFilter() {
//			@Override
//			public boolean accept(File dir, String filename) {
//				if (!filename.startsWith(_filenamePrefix) || !filename.endsWith(_filenamePostfix)) {
//					return false;
//				}
//				String timestampString = filename.substring(_filenamePrefix.length(), filename.length() - _filenamePostfix.length());
//				try {
//					Date currentDate = new Date();
//					Date fileDate = _fileNameTimestampFormat.parse(timestampString);
//					if ((currentDate.getTime() - fileDate.getTime()) > 24*3600*1000*MAX_LOG_DAYS) {
//						return true;
//					}
//				} catch (java.text.ParseException e) {
//					return false;
//				}
//				return false;
//			}
//		});

		File[] files = getLogFiles();
		long currentTime = new Date().getTime();
		for (File file : files) {
			if (currentTime - file.lastModified() > 24*3600*1000*MAX_LOG_DAYS) {
				if (!file.delete()) {
					return false;
				}
			}
		}
		return true;
	}

	public boolean clearLogFiles() {
		try {
			if (_logFileDos != null) {
				_logFileDos.close();
			}
		} catch (IOException e) {
			Log.e("BleLog", "Error closing logfile", e);
			return false;
		}

		File[] files = getLogFiles();
		for (File file : files) {
			if (!file.delete()) {
				return false;
			}
		}
		return true;
	}

}
