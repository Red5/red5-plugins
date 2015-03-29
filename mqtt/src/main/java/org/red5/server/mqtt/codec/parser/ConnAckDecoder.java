package org.red5.server.mqtt.codec.parser;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolDecoderOutput;
import org.eclipse.moquette.proto.messages.ConnAckMessage;

/**
 * Connect ACK decoder.
 *
 * @author andrea
 * @author Paul Gregoire
 */
public class ConnAckDecoder extends DemuxDecoder {

	@Override
	public void decode(IoSession session, IoBuffer in, ProtocolDecoderOutput out) throws Exception {
		in.reset();
		//Common decoding part
		ConnAckMessage message = new ConnAckMessage();
		if (!decodeCommonHeader(message, 0x00, in)) {
			in.reset();
			return;
		}
		// skip reserved byte
		in.skip(1);

		//read  return code
		message.setReturnCode(in.get());
		out.write(message);
	}

}
