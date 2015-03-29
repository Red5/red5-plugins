/*
 * RED5 Open Source Flash Server - https://github.com/red5
 * 
 * Copyright 2006-2015 by respective authors (see below). All rights reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.red5.server.mqtt.codec;

import java.util.HashMap;
import java.util.Map;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.CumulativeProtocolDecoder;
import org.apache.mina.filter.codec.ProtocolDecoderOutput;
import org.eclipse.moquette.proto.messages.AbstractMessage;
import org.red5.server.mqtt.codec.exception.CorruptedFrameException;
import org.red5.server.mqtt.codec.parser.ConnAckDecoder;
import org.red5.server.mqtt.codec.parser.ConnectDecoder;
import org.red5.server.mqtt.codec.parser.DemuxDecoder;
import org.red5.server.mqtt.codec.parser.DisconnectDecoder;
import org.red5.server.mqtt.codec.parser.PingReqDecoder;
import org.red5.server.mqtt.codec.parser.PingRespDecoder;
import org.red5.server.mqtt.codec.parser.PubAckDecoder;
import org.red5.server.mqtt.codec.parser.PubCompDecoder;
import org.red5.server.mqtt.codec.parser.PubRecDecoder;
import org.red5.server.mqtt.codec.parser.PubRelDecoder;
import org.red5.server.mqtt.codec.parser.PublishDecoder;
import org.red5.server.mqtt.codec.parser.SubAckDecoder;
import org.red5.server.mqtt.codec.parser.SubscribeDecoder;
import org.red5.server.mqtt.codec.parser.UnsubAckDecoder;
import org.red5.server.mqtt.codec.parser.UnsubscribeDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class handles the MQTT decoding.
 * 
 * @author Paul Gregoire
 */
public class MQTTDecoder extends CumulativeProtocolDecoder {

	private static final Logger log = LoggerFactory.getLogger(MQTTDecoder.class);

	private final Map<Byte, DemuxDecoder> decoderMap = new HashMap<Byte, DemuxDecoder>();

	public static final String PROTOCOL_VERSION = "version";

	public MQTTDecoder() {
		decoderMap.put(AbstractMessage.CONNECT, new ConnectDecoder());
		decoderMap.put(AbstractMessage.CONNACK, new ConnAckDecoder());
		decoderMap.put(AbstractMessage.PUBLISH, new PublishDecoder());
		decoderMap.put(AbstractMessage.PUBACK, new PubAckDecoder());
		decoderMap.put(AbstractMessage.SUBSCRIBE, new SubscribeDecoder());
		decoderMap.put(AbstractMessage.SUBACK, new SubAckDecoder());
		decoderMap.put(AbstractMessage.UNSUBSCRIBE, new UnsubscribeDecoder());
		decoderMap.put(AbstractMessage.DISCONNECT, new DisconnectDecoder());
		decoderMap.put(AbstractMessage.PINGREQ, new PingReqDecoder());
		decoderMap.put(AbstractMessage.PINGRESP, new PingRespDecoder());
		decoderMap.put(AbstractMessage.UNSUBACK, new UnsubAckDecoder());
		decoderMap.put(AbstractMessage.PUBCOMP, new PubCompDecoder());
		decoderMap.put(AbstractMessage.PUBREC, new PubRecDecoder());
		decoderMap.put(AbstractMessage.PUBREL, new PubRelDecoder());
	}

	@Override
	protected boolean doDecode(IoSession session, IoBuffer in, ProtocolDecoderOutput out) throws Exception {
		//ConnectionDescriptor conn = (ConnectionDescriptor) session.getAttribute(Constants.CONNECTION);
		// set position mark
		in.mark();
		// look for an expected header
		if (!MQTTProtocol.checkHeaderAvailability(in)) {
			// reset position to the mark
			in.reset();
			return false;
		}
		// reset position to the mark
		in.reset();
		byte messageType = MQTTProtocol.readMessageType(in);
		DemuxDecoder decoder = decoderMap.get(messageType);
		if (decoder == null) {
			throw new CorruptedFrameException("Can't find any suitable decoder for message type: " + messageType);
		}
		decoder.decode(session, in, out);
		return true;
	}
	
}