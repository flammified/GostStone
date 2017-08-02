package nl.dobots.bluenet.localization.locations;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;

import nl.dobots.bluenet.ble.extended.structs.BleDevice;

/**
 * Copyright (c) 2015 Dominik Egger <dominik@dobots.nl>. All rights reserved.
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
 * Created on 6-8-15
 *
 * @author Dominik Egger
 */
public class LocationsDbAdapter {

	///////////////////////////////////////////////////////////////////////////////////////////
	/// Variables
	///////////////////////////////////////////////////////////////////////////////////////////

	private static final String TAG = LocationsDbAdapter.class.getCanonicalName();

	// key names of the database fields
	public static final String KEY_DEVICE_ADDRESS = "address";
	public static final String KEY_LOCATION_NAME = "location";
	public static final String KEY_DEVICE_NAME = "name";
	public static final String KEY_ROWID = "_id";

	// table name
	public static final String TABLE_NAME = "locations";

	// database helper to manage database creation and version management.
	private DatabaseHelper mDbHelper;

	// database object to read and write database
	private SQLiteDatabase mDb;

	// define query used to create the database
	public static final String DATABASE_CREATE =
			"create table " + TABLE_NAME + " (" +
					KEY_ROWID + " integer primary key autoincrement, " +
					KEY_LOCATION_NAME + " text not null," +
					KEY_DEVICE_ADDRESS + " text not null," +
					KEY_DEVICE_NAME + " text not null" +
					" )";

	// application context
	private final Context mContext;

	///////////////////////////////////////////////////////////////////////////////////////////
	/// Code
	///////////////////////////////////////////////////////////////////////////////////////////

	// helper class to manage database creation and version management, see SQLiteOpenHelper
	private static class DatabaseHelper extends SQLiteOpenHelper {

		// default constructor
		DatabaseHelper(Context context, String dbName, int dbVersion) {
			super(context, dbName, null, dbVersion);
		}

		// called when database should be created
		@Override
		public void onCreate(SQLiteDatabase db) {
			db.execSQL(DATABASE_CREATE);
		}

		// called if version changed and database needs to be upgraded
		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			Log.w(TAG, "Upgrading database from version " + oldVersion + " to " +
					newVersion + ", which will destroy all old data");
			db.execSQL("DROP TABLE IF EXISTS notes");
			onCreate(db);
		}

	}

	// default constructor, assigns context and initializes date formats
	public LocationsDbAdapter(Context context) {
		mContext = context;
//		context.deleteDatabase(DATABASE_NAME);
	}

	/**
	 * Open the database. If it cannot be opened, try to create a new
	 * instance of the database. If it cannot be created, throw an exception to
	 * signal the failure
	 *
	 * @return this (self reference, allowing this to be chained in an
	 *         initialization call)
	 * @throws SQLException if the database could be neither opened or created
	 */
	public LocationsDbAdapter open(String dbName, int dbVersion) throws SQLException {
		mDbHelper = new DatabaseHelper(mContext, dbName, dbVersion);
		mDb = mDbHelper.getWritableDatabase();
		return this;
	}

	/**
	 * Close the database
	 */
	public void close() {
		mDbHelper.close();
	}

	public void clear() {
		mDb.delete(TABLE_NAME, null, null);
	}

	public boolean saveAll(LocationsList list) {
		clear();

		boolean success = true;
		for (Location location : list) {
			success &= addLocation(location);
		}
		return success;
	}

//	public void loadAll() {
	public void loadAll(LocationsList list) {

		HashMap<String, Location> hashMap = new HashMap<>();

//		LocationsList result = new ArrayList<>();
		Cursor cursor = fetchAllEntries();

		Location location = null;
//		String lastLocationStr = "";

		// as long as there are entries
		while (!cursor.isAfterLast()) {

			String locationStr = cursor.getString(cursor.getColumnIndexOrThrow(KEY_LOCATION_NAME));

//			if (!locationStr.matches(lastLocationStr)) {
//				location = new Fingerprint(locationStr);
//				list.add(location);
//			}
			if (hashMap.containsKey(locationStr)) {
				location = hashMap.get(locationStr);
			} else {
				location = new Location(locationStr);
				hashMap.put(locationStr, location);
			}

			String address = cursor.getString(cursor.getColumnIndexOrThrow(KEY_DEVICE_ADDRESS));
			String name = cursor.getString(cursor.getColumnIndexOrThrow(KEY_DEVICE_NAME));

			// dummy value -1 for rssi, because we don't need the rssi for the locations
			location.addBeacon(new BleDevice(address, name, -1));

			cursor.moveToNext();
		}

		list.addAll(hashMap.values());
//		return result;
	}

	public boolean addLocation(Location location) {
		ContentValues values = new ContentValues();

		for (BleDevice device : location.getBeaconsList()) {
			values.put(KEY_LOCATION_NAME, location.getName());
			values.put(KEY_DEVICE_ADDRESS, device.getAddress());
			values.put(KEY_DEVICE_NAME, device.getName());

			if (replaceEntry(values) == -1) {
				return false;
			}
		}

		return true;
	}

	private long createEntry(String locationName, String deviceName, String deviceAddress) {
		ContentValues values = new ContentValues();

		values.put(KEY_LOCATION_NAME, locationName);
		values.put(KEY_DEVICE_ADDRESS, deviceAddress);
		values.put(KEY_DEVICE_NAME, deviceName);

		return replaceEntry(values);
	}

	public long createEntry(ContentValues values) {
		return mDb.insert(TABLE_NAME, null, values);
	}

	public long replaceEntry(ContentValues values) {
		return mDb.replace(TABLE_NAME, null, values);
	}

	/**
	 * Update existing entry. Return true if entry was updated
	 * successfully
	 *
	 * @param id the row id of the entry to be updated
	 * @return true if updated successfully, false otherwise
	 */
	public boolean updateEntry(long id, String location, String address, String name) {
		ContentValues values = new ContentValues();

		values.put(KEY_LOCATION_NAME, location);
		values.put(KEY_DEVICE_ADDRESS, address);
		values.put(KEY_DEVICE_NAME, name);

		int num = mDb.update(TABLE_NAME, values, "_id " + "=" + id, null);
		return num == 1;
	}

	/**
	 * Delete the entry with the given rowId
	 *
	 * @param rowId id of note to delete
	 * @return true if deleted, false otherwise
	 */
	public boolean deleteEntry(long rowId) {
		return mDb.delete(TABLE_NAME, KEY_ROWID + "=" + rowId, null) > 0;
	}

	/**
	 * Fetch all entries in the database
	 *
	 * @return cursor to access the entries
	 */
	public Cursor fetchAllEntries() {
		Cursor mCursor = mDb.query(TABLE_NAME, new String[] {KEY_ROWID, KEY_LOCATION_NAME, KEY_DEVICE_ADDRESS, KEY_DEVICE_NAME},
				null, null, null, null, null);
		if (mCursor != null) {
			mCursor.moveToFirst();
		}
		return mCursor;
	}

	/**
	 * Fetch entry defined by row id
	 *
	 * @param rowId the id of the entry which should be returned
	 * @return cursor to access the entry
	 */
	public Cursor fetchEntry(long rowId) {
		Cursor mCursor = mDb.query(TABLE_NAME, new String[] {KEY_ROWID, KEY_LOCATION_NAME, KEY_DEVICE_ADDRESS, KEY_DEVICE_NAME},
				KEY_ROWID + "=" + rowId, null, null, null, null);
		if (mCursor != null) {
			mCursor.moveToFirst();
		}
		return mCursor;
	}

	public boolean exportDB(String fileName) {

		File exportFile = new File(fileName);
		File directory = exportFile.getParentFile();

		if (!directory.exists()) {
			Log.i(TAG, "creating export directory");
			directory.mkdirs();
		}

		DataOutputStream dos;
		try {
			 dos = new DataOutputStream(new FileOutputStream(exportFile));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return false;
		}

		try {
//			dos.writeChars(String.format("%s,%s,%s,%s\n", KEY_ROWID, KEY_LOCATION_NAME, KEY_DEVICE_NAME, KEY_DEVICE_ADDRESS));
			dos.write(String.format("%s,%s,%s,%s\n", KEY_ROWID, KEY_LOCATION_NAME, KEY_DEVICE_NAME, KEY_DEVICE_ADDRESS).getBytes());
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}

		Cursor cursor = fetchAllEntries();

		// as long as there are entries
		while (!cursor.isAfterLast()) {

			String locationName = cursor.getString(cursor.getColumnIndexOrThrow(KEY_LOCATION_NAME));
			String deviceAddress = cursor.getString(cursor.getColumnIndexOrThrow(KEY_DEVICE_ADDRESS));
			String deviceName = cursor.getString(cursor.getColumnIndexOrThrow(KEY_DEVICE_NAME));
			int rowId = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_ROWID));

			try {
//				dos.writeChars(String.format("%d,%s,%s,%s\n", rowId, locationName, deviceName, deviceAddress));
				dos.write(String.format("%d,%s,%s,%s\n", rowId, locationName, deviceName, deviceAddress).getBytes());
			} catch (IOException e) {
				e.printStackTrace();
				return false;
			}

			cursor.moveToNext();
		}

		try {
			dos.close();
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}

		return true;
	}

	public boolean importDB(String fileName) {
		Log.i(TAG, "importing db from " + fileName);

		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new InputStreamReader(new FileInputStream(fileName)));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return false;
		}
		try {
			String line = reader.readLine(); // skip first line (header information)
			while ((line = reader.readLine()) != null) {
				if (!line.equals("")) {
					String[] data = line.split(",");
					String locationName = data[1];
					String deviceName = data[2];
					String deviceAddress = data[3];
					createEntry(locationName, deviceName, deviceAddress);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
			return  false;
		} finally {
			try {
				reader.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		return true;
	}

}

