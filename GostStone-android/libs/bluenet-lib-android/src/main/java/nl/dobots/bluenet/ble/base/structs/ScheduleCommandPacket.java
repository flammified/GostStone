package nl.dobots.bluenet.ble.base.structs;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;

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
 * Created on 11-7-17
 *
 * @author Bart van Vliet
 */
public class ScheduleCommandPacket {
	public static final int PACKET_SIZE = 1 + ScheduleEntryPacket.ENTRY_SIZE;

	public int _index;
	public ScheduleEntryPacket _entry;

	public ScheduleCommandPacket() {
		_entry = new ScheduleEntryPacket();
	}

	public boolean fromArray(byte[] bytes) {
		ByteBuffer bb = ByteBuffer.wrap(bytes);
		bb.order(ByteOrder.LITTLE_ENDIAN);

		if (bytes.length < PACKET_SIZE) {
			return false;
		}
		_index = BleUtils.toUint8(bb.get());
		return _entry.fromArray(bb);
	}

	public byte[] toArray() {
		ByteBuffer bb = ByteBuffer.allocate(PACKET_SIZE);
		bb.order(ByteOrder.LITTLE_ENDIAN);
		bb.put((byte)_index);
		bb.put(_entry.toArray());
		return bb.array();
	}

	public String toString() {
		return String.format(Locale.ENGLISH, "index=%d %s", _index, _entry.toString());
	}
}
