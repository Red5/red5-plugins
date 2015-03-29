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
import org.apache.mina.filter.codec.ProtocolEncoderAdapter;
import org.apache.mina.filter.codec.ProtocolEncoderOutput;
import org.eclipse.moquette.proto.messages.AbstractMessage;
import org.red5.server.mqtt.codec.exception.CorruptedFrameException;
import org.red5.server.mqtt.codec.parser.ConnAckEncoder;
import org.red5.server.mqtt.codec.parser.ConnectEncoder;
import org.red5.server.mqtt.codec.parser.DemuxEncoder;
import org.red5.server.mqtt.codec.parser.DisconnectEncoder;
import org.red5.server.mqtt.codec.parser.PingReqEncoder;
import org.red5.server.mqtt.codec.parser.PingRespEncoder;
import org.red5.server.mqtt.codec.parser.PubAckEncoder;
import org.red5.server.mqtt.codec.parser.PubCompEncoder;
import org.red5.server.mqtt.codec.parser.PubRecEncoder;
import org.red5.server.mqtt.codec.parser.PubRelEncoder;
import org.red5.server.mqtt.codec.parser.PublishEncoder;
import org.red5.server.mqtt.codec.parser.SubAckEncoder;
import org.red5.server.mqtt.codec.parser.SubscribeEncoder;
import org.red5.server.mqtt.codec.parser.UnsubAckEncoder;
import org.red5.server.mqtt.codec.parser.UnsubscribeEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Encodes MQTT data.
 * 
 * @author Paul Gregoire
 */
public class MQTTEncoder extends ProtocolEncoderAdapter {

	private static final Logger log = LoggerFactory.getLogger(MQTTEncoder.class);
    
    private Map<Byte, DemuxEncoder<?>> encoderMap = new HashMap<Byte, DemuxEncoder<? extends AbstractMessage>>();
    
	public MQTTEncoder() {
		encoderMap.put(AbstractMessage.CONNECT, new ConnectEncoder());
        encoderMap.put(AbstractMessage.CONNACK, new ConnAckEncoder());
        encoderMap.put(AbstractMessage.PUBLISH, new PublishEncoder());
        encoderMap.put(AbstractMessage.PUBACK, new PubAckEncoder());
        encoderMap.put(AbstractMessage.SUBSCRIBE, new SubscribeEncoder());
        encoderMap.put(AbstractMessage.SUBACK, new SubAckEncoder());
        encoderMap.put(AbstractMessage.UNSUBSCRIBE, new UnsubscribeEncoder());
        encoderMap.put(AbstractMessage.DISCONNECT, new DisconnectEncoder());
        encoderMap.put(AbstractMessage.PINGREQ, new PingReqEncoder());
        encoderMap.put(AbstractMessage.PINGRESP, new PingRespEncoder());
        encoderMap.put(AbstractMessage.UNSUBACK, new UnsubAckEncoder());
        encoderMap.put(AbstractMessage.PUBCOMP, new PubCompEncoder());
        encoderMap.put(AbstractMessage.PUBREC, new PubRecEncoder());
        encoderMap.put(AbstractMessage.PUBREL, new PubRelEncoder());
     }
     
	@SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
	public void encode(IoSession session, Object message, ProtocolEncoderOutput out) throws Exception {
		AbstractMessage msg = (AbstractMessage) message;
        DemuxEncoder encoder = encoderMap.get(msg.getMessageType());
        if (encoder != null) {
            IoBuffer bb = encoder.encode(session, msg);
            out.write(bb);
            out.flush();
        } else {
            throw new CorruptedFrameException("Can't find any suitable decoder for message type: " + msg.getMessageType());
        }
	}

}
