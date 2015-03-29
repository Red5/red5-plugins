package org.red5.server.mqtt.codec.exception;

public class CorruptedFrameException extends Exception {

	private static final long serialVersionUID = -5910143462397574572L;

	public CorruptedFrameException(String message) {
		super(message);
    }

}
