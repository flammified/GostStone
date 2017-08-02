package nl.dobots.bluenet.ble.mesh.structs.reply;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;

import nl.dobots.bluenet.ble.mesh.structs.MeshPayload;
import nl.dobots.bluenet.utils.BleUtils;

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
 * Created on 27-1-17
 *
 * @author Dominik Egger <dominik@dobots.nl>
 */
public class MeshCommandReplyPacket implements MeshPayload {

	public static final String TAG = MeshCommandReplyPacket.class.getCanonicalName();

	// 2B reply type + 4B for message counter + 1B number of replies
	protected static final int COMMAND_REPLY_PACKET_HEADER_SIZE = 7;

	private int _replyType;

	private long _messageCounter;

	protected int _numberOfReplies;

	protected byte[] _payload;


	/**
	 * Create an empty, invalid, packet.
	 */
	public MeshCommandReplyPacket() {
		_replyType = 0;
		_messageCounter = 0;
		_numberOfReplies = 0;
		_payload = null;
	}

	public MeshCommandReplyPacket(int replyType, long messageCounter) {
		_replyType = replyType;
		_messageCounter = messageCounter;
		_numberOfReplies = 0;
	}

//	public MeshCommandReplyPacket(byte[] bytes) {
//	}

	@Override
	public boolean fromArray(byte[] bytes) {
		ByteBuffer bb = ByteBuffer.wrap(bytes);
		bb.order(ByteOrder.LITTLE_ENDIAN);

		if (bytes.length < COMMAND_REPLY_PACKET_HEADER_SIZE) {
			return false;
		}
		_replyType = BleUtils.toUint16(bb.getShort());
		_messageCounter = BleUtils.toUint32(bb.getInt());
		_numberOfReplies = BleUtils.toUint8(bb.get());
		_payload = new byte[bb.remaining()];
		bb.get(_payload);
		return true;
	}

	@Override
	public byte[] toArray() {
		ByteBuffer bb = ByteBuffer.allocate(COMMAND_REPLY_PACKET_HEADER_SIZE + _payload.length);
		bb.order(ByteOrder.LITTLE_ENDIAN);

		bb.putShort((short) _replyType);
		bb.putInt((int) _messageCounter);
		bb.put((byte) _numberOfReplies);
		bb.put(_payload);

		return bb.array();
	}

	public byte[] getPayload() {
		return _payload;
	}

	public void setPayload(byte[] payload) {
		_payload = payload;
	}

	public int getNumberOfReplies() {
		return _numberOfReplies;
	}

	public void setNumberOfReplies(int numberOfReplies) {
		_numberOfReplies = numberOfReplies;
	}

	protected String payloadToString() {
		return "";
	}

	@Override
	public String toString() {
		return String.format(Locale.ENGLISH, "{replyType: %d, numReplies: %d [%s], payload: %s}",
				_replyType, _numberOfReplies, payloadToString(), BleUtils.bytesToString(_payload));
	}
}
