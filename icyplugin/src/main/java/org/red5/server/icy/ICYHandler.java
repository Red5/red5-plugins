package org.red5.server.icy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.params.HttpClientParams;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.filterchain.IoFilter;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFactory;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.filter.codec.ProtocolDecoder;
import org.apache.mina.filter.codec.ProtocolEncoder;
import org.apache.mina.filter.logging.LoggingFilter;
import org.apache.mina.transport.socket.SocketAcceptor;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;
import org.red5.server.icy.codec.ICYDecoder;
import org.red5.server.icy.codec.ICYEncoder;
import org.red5.server.icy.codec.ICYDecoder.ReadState;
import org.red5.server.icy.message.AACFrame;
import org.red5.server.icy.message.MP3Frame;
import org.red5.server.icy.message.NSVFrame;
import org.red5.server.icy.nsv.NSVStreamConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SHOUTcast / ICY protocol handler.
 * 
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class ICYHandler extends IoHandlerAdapter {

	private static Logger log = LoggerFactory.getLogger(ICYHandler.class);
		
	private static final String userAgent = "Mozilla/4.0 (compatible; Red5 Server/NSV plugin)";

	private static ProtocolCodecFactory codecFactory = new ProtocolCodecFactory() {
		//coders can be shared
		private ProtocolEncoder icyEncoder = new ICYEncoder();
		private ProtocolDecoder icyDecoder = new ICYDecoder();

		public ProtocolEncoder getEncoder(IoSession session) {
			return icyEncoder;
		}

		public ProtocolDecoder getDecoder(IoSession session) {
			return icyDecoder;
		}
	};
	
	private static IoFilter codecFilter = new ProtocolCodecFilter(codecFactory);
	
	private String host = "0.0.0.0";

	private int port = 8001;

	private int mode = 0;
	
	private SocketAcceptor acceptor;
	
	private IoBuffer outBuffer;
	
	private IICYHandler handler;
	
	public NSVStreamConfig config;
		
	private boolean connected;

	//private long lastDataTs;
	
	private String password;

	//thread sleep period
	private int waitTime = 50;
	
	//data timeout in milliseconds
	private long dataTimeout = 10000;

	//password has been accepted
	private boolean validated;
	
	//determines how to notify players that the video is upside down
	private boolean notifyFlipped;

	private String audioType;
	
	public void start() {
		log.debug("Starting icy socket handler");
        switch (mode) {
			case 1: // client mode
				// create a singular HttpClient object
				HttpClient client = new HttpClient();

				// use proxy if specified
				if (System.getProperty("http.proxyHost") != null && System.getProperty("http.proxyPort") != null) {
					HostConfiguration config = client.getHostConfiguration();
					config.setProxy(System.getProperty("http.proxyHost").toString(), Integer.parseInt( System.getProperty("http.proxyPort")));
				}

				// establish a connection within 5 seconds
				client.getHttpConnectionManager().getParams().setConnectionTimeout(5000);
				//get the params for the client
				HttpClientParams params = client.getParams();
				params.setParameter(HttpMethodParams.USER_AGENT, userAgent);
				//get registry file
				HttpMethod method = new GetMethod(host);
				//follow any 302's although there should not be any
				method.setFollowRedirects(true);
				// execute the method
				try {
					int code = client.executeMethod(method);
					log.debug("HTTP response code: {}", code);
					String resp = method.getResponseBodyAsString();
					log.trace("Response: {}", resp);
				
					//TODO pipe input stream into mina
					//input = method.getResponseBodyAsStream();
					
				} catch (HttpException he) {
					log.error("Http error connecting to {}", host, he);
				} catch (IOException ioe) {
					log.error("Unable to connect to {}", host, ioe);
				} finally {
					//client mode is automatically validated
					validated = true;
					
					if (method != null) {
						method.releaseConnection();
					}
				}
				break;
			
			case 0: // server mode
				try {
		        	acceptor = new NioSocketAcceptor();
		        	acceptor.setReuseAddress(true);
		        	acceptor.setHandler(this);
		        	
		        	if (log.isDebugEnabled()) {
		        		acceptor.getFilterChain().addLast("logger", new LoggingFilter());
		        	}
		        	acceptor.getFilterChain().addLast("codec", codecFilter);
		            acceptor.getSessionConfig().setReadBufferSize(1024);
		            acceptor.getSessionConfig().setIdleTime(IdleStatus.BOTH_IDLE, 10);
					
		        	if ("".equals(host)) {
		        		acceptor.setDefaultLocalAddress(new InetSocketAddress(port));
		        		acceptor.bind();
		        	} else {
    					Set<SocketAddress> addresses = new HashSet<SocketAddress>();			
    					addresses.add(new InetSocketAddress(host, port));	
    					acceptor.bind(addresses);
		        	}
					
					log.info("icy listening on port {}", port);	    					
					
					connected = true;
				} catch (IOException ioe) {
					log.debug("Unable to setup connector on host: {} port: {}", host, port);
					log.error("Unable to setup connector", ioe);
				}
				break;
				
			default:
				log.debug("Unhandled mode: {}", mode);
		}		
        
        outBuffer = IoBuffer.allocate(16);
        outBuffer.setAutoExpand(true);
        
	}

	public void reset() {
		log.debug("Resetting icy socket");
    	connected = false;
    	validated = false;
    	//lastDataTs = 0L;
    }
	
	public void stop() {
		log.debug("Stopping icy socket");
		reset();
		acceptor.unbind();
	}
	
	@Override
	public void sessionOpened(IoSession session) throws Exception {
		super.sessionOpened(session);
		//add the password so it can be retrieved in the decoder
		log.debug("Adding password to session");
		session.setAttribute("password", password);
	}

	@Override
	public void sessionClosed(IoSession session) throws Exception {
		//reset local props
		reset();
		super.sessionClosed(session);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void messageReceived(IoSession session, Object message) throws Exception {
		log.info("Incomming: {}", session.getRemoteAddress().toString());
		log.trace("Message: {}", message.getClass().getName());
		
		/*
		if (lastDataTs > 0) {
        	long delta = System.currentTimeMillis() - lastDataTs;
        	if (delta > dataTimeout) {
        		log.debug("Data too late exit time: {} > timeout: {}", delta, dataTimeout);
        		//disconnect if late?
        		stop();
        	}	
		}
		lastDataTs = System.currentTimeMillis();
		log.debug("Data ts: {}", lastDataTs);
		*/
		
		//check state
		ReadState state = (ReadState) session.getAttribute("state");
		log.debug("Current state: {}", state);

		//stream config
		NSVStreamConfig config = null;
		
		switch (state) {
			case Header: 
				if (validated) {
					break;
				}
				validated = true;
			case Failed: 
				outBuffer.put((byte[]) message);
        		//flip it!
        		outBuffer.flip();	
        		//respond to the client
        		session.write(outBuffer);
				break;
			case Ready:
				//handle meta
				log.debug("Pulling metadata");
				Map<String, Object> metaData = (Map<String, Object>) session.getAttribute("meta");
				if (metaData != null) {
					handler.onMetaData(metaData);
				} else {
					log.debug("Metadata was null for the session");
				}
				
				//reset mode based on type
				log.debug("Checking type, resetting mode. Current mode: {}", mode);
				String[] type = ((String) metaData.get("type")).split("/");
				if (mode == 3 || mode == 2) {
					if (type[0].equals("video")) {
						mode = (mode == 3) ? 0 : 1;
					} else {
						audioType = type[1];
					}
				} else {
					if (type[0].equals("audio")) {
						if (mode == 0) {
							mode = 3;
						} else {
							mode = 2;
						}
						audioType = type[1];
					}
				}
				//notify handler of mode change
				handler.reset(type[0], type[1]);
				
				//audio only
				if (mode == 2 || mode == 3) {
					//handler.onAudioData(bits);
				}
				
				//lookup stream config
				config = (NSVStreamConfig) session.getAttribute("nsvconfig");
				if (config != null) {
					log.debug("NSV stream config found in session. Previous audio type: {}", audioType);
					log.debug("NSV types - audio: {} video: {}", config.audioFormat, config.videoFormat);
					handler.onConnected(config.videoFormat, config.audioFormat);
										
					//use standard codec meta tags.
					if (metaData == null) {
						metaData = new HashMap<String, Object>();
					}
					
					//upside down format. Send negative values?
					if (notifyFlipped) {
						metaData.put("width", config.videoWidth);
						metaData.put("height", config.videoHeight);
						metaData.put("flipped", "true");
					} else {
						metaData.put("width", config.videoWidth * -1);
						metaData.put("height", config.videoHeight * -1);			
					}
					metaData.put("frameRate", config.frameRate);
					metaData.put("videoCodec", config.videoFormat);
					metaData.put("audioCodec", config.audioFormat);
					
					//send updated meta data
					handler.onMetaData(metaData);

					//get any aux data
					Map<String, IoBuffer> aux = (Map<String, IoBuffer>) session.removeAttribute("aux");
					if (aux != null) {
						for (Map.Entry<String, IoBuffer> entry : aux.entrySet()) {
							handler.onAuxData(entry.getKey(), entry.getValue());
						}
					}
				
				}
			
				//set to packet state
				session.setAttribute("state", ReadState.Packet);
				
				//allow fall through to packet
				
			case Packet:			
				//lookup stream config
				config = (NSVStreamConfig) session.getAttribute("nsvconfig");
				
				//check for a frame
				if (message instanceof NSVFrame) {
					//got a frame, writing to config
					handler.onFrameData((NSVFrame) message);
				} else if (message instanceof AACFrame) {
					//got a frame
					handler.onAudioData(((AACFrame) message).getPayload());
				} else if (message instanceof MP3Frame) {
					//got a frame
					handler.onAudioData(((MP3Frame) message).getPayload());
				}
				
				break;
				
			default: 
				log.debug("Unhandled state");
				
		}
		
		log.trace("Buffered frames: {}", handler.queueSize());
	
	}

	@Override
	public void exceptionCaught(IoSession session, Throwable ex) throws Exception {
		log.debug("Exception occurred {}", session.getRemoteAddress().toString());
		if (log.isDebugEnabled()) {
			//we want the stacktrace only if debugging
			log.warn("Exception: {}", ex);
		}
		session.close(true);
		//if we "stop" here then the port will need to be re-established
		reset();
	}	

	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public int getMode() {
		return mode;
	}

	public void setMode(int mode) {
		this.mode = mode;
	}

	public IICYHandler getHandler() {
		return handler;
	}

	public void setHandler(IICYHandler handler) {
		this.handler = handler;
	}	
	
	public void setPassword(String password) {
		this.password = password;
	}

	public int getWaitTime() {
		return waitTime;
	}

	public void setWaitTime(int waitTime) {
		this.waitTime = waitTime;
	}

	public long getDataTimeout() {
		return dataTimeout;
	}

	public void setDataTimeout(long dataTimeout) {
		this.dataTimeout = dataTimeout;
	}
	
	public boolean isNotifyFlipped() {
		return notifyFlipped;
	}

	public void setNotifyFlipped(boolean notifyFlipped) {
		this.notifyFlipped = notifyFlipped;
	}

	public boolean isConnected() {
		return connected;
	}	
	
}
