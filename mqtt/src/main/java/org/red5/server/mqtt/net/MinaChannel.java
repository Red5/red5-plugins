package org.red5.server.mqtt.net;

import org.apache.mina.core.session.IoSession;
import org.red5.server.mqtt.ServerChannel;

public class MinaChannel implements ServerChannel {

	private final IoSession session;
	
	public MinaChannel(IoSession session) {
		this.session = session;
	}
	
	@Override
    public Object getAttribute(String key) {
	    return session.getAttribute(key);
    }

	@Override
    public void setAttribute(String key, Object value) {
	    session.setAttribute(key, value);
    }

	@Override
    public void setIdleTime(int idleTime) {
		// in seconds
		//session.getConfig().setIdleTime(status, idleTime);
		
		// from the Netty impl
//        if (m_channel.pipeline().names().contains("idleStateHandler")) {
//            m_channel.pipeline().remove("idleStateHandler");
//        }
//        if (m_channel.pipeline().names().contains("idleEventHandler")) {
//            m_channel.pipeline().remove("idleEventHandler");
//        }
//        m_channel.pipeline().addFirst("idleStateHandler", new IdleStateHandler(0, 0, idleTime));
//        m_channel.pipeline().addAfter("idleStateHandler", "idleEventHandler", new MoquetteIdleTimoutHandler());
    }

	@Override
    public void close(boolean immediately) {
		session.close(immediately);
    }

	@Override
    public void write(Object value) {
		session.write(value);
    }

}
