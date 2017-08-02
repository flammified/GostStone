package nl.dobots.bluenet.ble.base.structs;

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
 * Created on 15-7-15
 * Updated on 9-6-16 for protocol version 0.4.0
 *
 * This class is an object to hold and ease conversion of a Bluenet Configuration message. A
 * Configuration message is used to read and write to a configuration characteristic. A configuration
 * message contains the following 4 fields:
 * 		* Type
 * 		* OpCode
 * 		* Length
 * 		* Payload
 *
 * And the format is as follows:
 * 		-----------------------------------------------------------------------------
 * 		| Type | OpCode | Length (LSB) | Length (MSB) | Payload (Length bytes) .... |
 * 		-----------------------------------------------------------------------------
 *
 * 	The reserved byte is used for byte alignment of the length and payload.
 * 	Type defines the type of configuration and defines the layout of the payload, see
 * 	  @BluenetConfig for a list of possible types
 * 	Length defines the number of bytes used by the payload
 *
 * 	Note: Byte Order is Little-Endian, i.e. Least Significant Bit comes first
 *
 * @author Dominik Egger
 */
public class ConfigurationMsg extends StreamMsg {

	public ConfigurationMsg() {
		super();
	}

	public ConfigurationMsg(int type, int opCode, int length, byte[] payload) {
		super(type, opCode, length, payload);
	}

	public ConfigurationMsg(int type, int length, byte[] payload) {
		super(type, length, payload);
	}

//	public ConfigurationMsg(byte[] bytes) {
//	}


	@Override
	public boolean fromArray(byte[] bytes) {
		// to do: parsing for errors?
		return super.fromArray(bytes);
	}

	@Override
	public int getOpCode() {
		return super.getOpCode();
	}
}
