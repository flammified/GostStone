package nl.dobots.bluenet.localization.locations;

import nl.dobots.bluenet.ble.extended.structs.BleDevice;
import nl.dobots.bluenet.ble.extended.structs.BleDeviceList;
import nl.dobots.bluenet.ble.extended.structs.BleDeviceMap;
//import nl.dobots.presence.R;
//import nl.dobots.presence.utils.Utils;

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
public class Location {

	private String _name;

	private BleDeviceMap _beaconsMap;
	private BleDeviceList _beaconsList;

	public Location(String name) {
		_name = name;

		_beaconsMap = new BleDeviceMap();
		_beaconsList = new BleDeviceList();
	}

//	private AdapterView.OnItemLongClickListener _onBeaconLongClickListener = new AdapterView.OnItemLongClickListener() {
//		@Override
//		public boolean onItemLongClick(final AdapterView<?> parent, final View view, final int position, long id) {
//
//			String beaconName = _beaconsList.get(position).getName();
//
//			final AlertDialog.Builder builder = new AlertDialog.Builder(parent.getContext());
//			builder.setTitle("Remove Beacon");
//			builder.setMessage("Do you want to remove the beacon " + beaconName + "?");
//			builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
//				public void onClick(DialogInterface dialog, int id) {
//					_beaconsList.remove(position);
//					((LocationBeaconsAdapter) parent.getAdapter()).notifyDataSetChanged();
//					setListViewHeightBasedOnChildren((ListView) view.getParent());
//				}
//			});
//			builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
//				public void onClick(DialogInterface dialog, int id) {
//						/* nothing to do */
//				}
//			});
//			builder.show();
//
//			return true;
//		}
//	};

	public String getName() {
		return _name;
	}

	public void setName(String name) {
		_name = name;
	}

	public void addBeacon(BleDevice device) {
		_beaconsMap.updateDevice(device);
		_beaconsList.add(device);
	}

	public void removeBeacon(BleDevice device) {
		_beaconsMap.remove(device.getAddress());
		_beaconsList.remove(device);
	}

	public boolean containsBeacon(String deviceAddress) {
		return _beaconsMap.containsKey(deviceAddress);
	}

//	public AdapterView.OnItemLongClickListener getOnBeaconLongClickListener() {
//		return _onBeaconLongClickListener;
//	}

	public BleDeviceList getBeaconsList() {
		return _beaconsList;
	}

//	// Regular inner class which act as the Adapter


}
