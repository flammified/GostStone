package nl.dobots.bluenet.ble.base.structs;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import nl.dobots.bluenet.utils.BleLog;

/**
 * Copyright (c) 2016 Dominik Egger <dominik@dobots.nl>. All rights reserved.
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
 * Created on 5-7-16
 *
 * @author Dominik Egger
 */
public class PowerSamples {

	private static final String TAG = PowerSamples.class.getCanonicalName();

	private static final int NUM_SAMPLES = 75;

	public class Samples {

		private int length;
		private short[] samples = new short[NUM_SAMPLES];

		public Samples(ByteBuffer buffer) throws BufferUnderflowException {
			length = buffer.getShort();
			buffer.asShortBuffer().get(samples);
			buffer.position(buffer.position() + NUM_SAMPLES*2);
		}

		public int getCount() {
			return length;
		}

		public int getSample(int index) {
			return samples[index];
		}
	}

	public class Timestamps {

		int length;
		int firstValue;
		int lastValue;
		byte[] timestampDiffs = new byte[NUM_SAMPLES-1];
		int[] timestamps = new int[NUM_SAMPLES];

		public Timestamps(ByteBuffer buffer) throws BufferUnderflowException {
			length = buffer.getShort();
			firstValue = buffer.getInt();
			lastValue = buffer.getInt();
			buffer.get(timestampDiffs);

			timestamps[0] = firstValue;
			for (int i = 0; i < timestampDiffs.length; ++i) {
				timestamps[i+1] = timestamps[i] + timestampDiffs[i];
			}

			if (timestamps[timestamps.length - 1] != lastValue) {
				BleLog.getInstance().LOGe(TAG, "ERROR?");
			}
		}

		public int getCount() {
			return  length;
		}

		public int getTimestamp(int index) {
			return timestamps[index];
		}

		public int getFirst() {
			return firstValue;
		}
	}

	Samples currentSamples;
	Samples voltageSamples;
	Timestamps currentTimestamps;
	Timestamps voltageTimestamps;


	/**
	 * Parses the given byte array into a mesh message
	 * @param bytes byte array containing the mesh message
	 */
	public PowerSamples(byte[] bytes) throws BufferUnderflowException {
		ByteBuffer bb = ByteBuffer.wrap(bytes);
		bb.order(ByteOrder.LITTLE_ENDIAN);

		currentSamples = new Samples(bb);
		voltageSamples = new Samples(bb);
		currentTimestamps = new Timestamps(bb);
		voltageTimestamps = new Timestamps(bb);
	}

	public Samples getCurrentSamples() {
		return currentSamples;
	}

	public Samples getVoltageSamples() {
		return voltageSamples;
	}

	public Timestamps getCurrentTimestamps() {
		return currentTimestamps;
	}

	public Timestamps getVoltageTimestamps() {
		return voltageTimestamps;
	}
}
