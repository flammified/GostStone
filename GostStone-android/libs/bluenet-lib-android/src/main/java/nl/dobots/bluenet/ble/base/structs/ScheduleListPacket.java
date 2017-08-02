package nl.dobots.bluenet.ble.base.structs;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;

import nl.dobots.bluenet.utils.BleLog;
import nl.dobots.bluenet.utils.BleUtils;

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
 * Created on 6-7-17
 *
 * @author Bart van Vliet
 */
public class ScheduleListPacket {
	private static final String TAG = ScheduleListPacket.class.getCanonicalName();
	public static final int HEADER_SIZE = 1;
	public static final int MAX_LIST_ELEMENTS = 10;

	private int _size;
	private ScheduleEntryPacket[] _list = new ScheduleEntryPacket[MAX_LIST_ELEMENTS];

	public ScheduleListPacket() {
		_size = 0;
	}

	public int getSize() {
		return _size;
	}

	public boolean fromArray(byte[] bytes) {
		ByteBuffer bb = ByteBuffer.wrap(bytes);
		bb.order(ByteOrder.LITTLE_ENDIAN);

		if (bytes.length < HEADER_SIZE) {
			BleLog.getInstance().LOGd(TAG, "invalid size: " + bytes.length);
			return false;
		}
		_size = BleUtils.toUint8(bb.get());

		if (_size > MAX_LIST_ELEMENTS || bytes.length < HEADER_SIZE + _size * ScheduleEntryPacket.ENTRY_SIZE) {
			BleLog.getInstance().LOGd(TAG, "invalid size: " + bytes.length);
			_size = 0;
			return false;
		}

		for (int i=0; i < _size; i++) {
			_list[i] = new ScheduleEntryPacket();
			if (!_list[i].fromArray(bb)) {
				BleLog.getInstance().LOGd(TAG, "invalid entry: " + i + " buffer position:" + bb.position());
				_size = 0;
				return false;
			}
		}
		return true;
	}

	public byte[] toArray() {
		ByteBuffer bb = ByteBuffer.allocate(HEADER_SIZE + _size * ScheduleEntryPacket.ENTRY_SIZE);
		bb.order(ByteOrder.LITTLE_ENDIAN);
		bb.put((byte)_size);
		for (int i=0; i<_size; ++i) {
			bb.put(_list[i].toArray());
		}
		return bb.array();
	}

	public String toString() {
		String str = "Schedule list:";
		for (int i=0; i<_size; ++i) {
			str += "\n" + i + " ";
			str += _list[i].toString();
		}
		if (_size == 0) {
			str += " empty";
		}
		return str;
	}
}
