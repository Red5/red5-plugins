/*
 * Copyright (c) 2012-2015 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * The Apache License v2.0 is available at
 * http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */
package org.eclipse.moquette.spi.impl;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.eclipse.moquette.proto.messages.AbstractMessage;
import org.eclipse.moquette.spi.IMessagesStore;
import org.eclipse.moquette.spi.IMessaging;
import org.eclipse.moquette.spi.ISessionsStore;
import org.eclipse.moquette.spi.impl.events.LostConnectionEvent;
import org.eclipse.moquette.spi.impl.events.MessagingEvent;
import org.eclipse.moquette.spi.impl.events.ProtocolEvent;
import org.eclipse.moquette.spi.impl.events.StopEvent;
import org.eclipse.moquette.spi.impl.subscriptions.SubscriptionsStore;
import org.eclipse.moquette.spi.persistence.MapDBPersistentStore;
import org.red5.server.mqtt.IAuthenticator;
import org.red5.server.mqtt.ServerChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;

/**
 * Singleton class that orchestrate the execution of the protocol.
 *
 * Uses the LMAX Disruptor to serialize the incoming, requests, because it work in a evented fashion; the requests come from connectors and are dispatched to the ProtocolProcessor.
 *
 * @author andrea
 */
public class SimpleMessaging implements IMessaging, EventHandler<ValueEvent> {

	private static final Logger LOG = LoggerFactory.getLogger(SimpleMessaging.class);
	
	private static SimpleMessaging INSTANCE;

	private final ProtocolProcessor processor = new ProtocolProcessor();

	private final AnnotationSupport annotationSupport = new AnnotationSupport();
	
	private SubscriptionsStore subscriptions;

	private RingBuffer<ValueEvent> ringBuffer;

	private IMessagesStore storageService;

	private ISessionsStore sessionsStore;

	private ExecutorService executor;

	private Disruptor<ValueEvent> disruptor;
	
	private IAuthenticator authenticator;

	private MapDBPersistentStore mapStorage;

	CountDownLatch stopLatch;

	private SimpleMessaging() {
	}

	public static SimpleMessaging getInstance() {
		if (INSTANCE == null) {
			INSTANCE = new SimpleMessaging();
		}
		return INSTANCE;
	}

	public void init() {
		subscriptions = new SubscriptionsStore();
		executor = Executors.newFixedThreadPool(1);
		disruptor = new Disruptor<>(ValueEvent.EVENT_FACTORY, 1024 * 32, executor);
		/*Disruptor<ValueEvent> m_disruptor = new Disruptor<ValueEvent>(ValueEvent.EVENT_FACTORY, 1024 * 32, m_executor, ProducerType.MULTI, new BusySpinWaitStrategy());*/
		disruptor.handleEventsWith(this);
		disruptor.start();
		// Get the ring buffer from the Disruptor to be used for publishing
		ringBuffer = disruptor.getRingBuffer();
		// 
		annotationSupport.processAnnotations(processor);
		// link to storage
		storageService = mapStorage;
		sessionsStore = mapStorage;
		// init storage
		storageService.initStore();
		// init subscriptions
		subscriptions.init(sessionsStore);
		// init processor
		processor.init(subscriptions, storageService, sessionsStore, authenticator);
		//        disruptorPublish(new InitEvent(configProps));
	}

	private void disruptorPublish(MessagingEvent msgEvent) {
		LOG.debug("disruptorPublish publishing event {}", msgEvent);
		long sequence = ringBuffer.next();
		ValueEvent event = ringBuffer.get(sequence);
		event.setEvent(msgEvent);
		ringBuffer.publish(sequence);
	}

	@Override
	public void lostConnection(ServerChannel session, String clientID) {
		disruptorPublish(new LostConnectionEvent(session, clientID));
	}

	@Override
	public void handleProtocolMessage(ServerChannel session, AbstractMessage msg) {
		disruptorPublish(new ProtocolEvent(session, msg));
	}

	@Override
	public void stop() {
		stopLatch = new CountDownLatch(1);
		disruptorPublish(new StopEvent());
		try {
			//wait the callback notification from the protocol processor thread
			LOG.debug("waiting 10 sec to m_stopLatch");
			boolean elapsed = !stopLatch.await(10, TimeUnit.SECONDS);
			LOG.debug("after m_stopLatch");
			executor.shutdown();
			disruptor.shutdown();
			if (elapsed) {
				LOG.error("Can't stop the server in 10 seconds");
			}
		} catch (InterruptedException ex) {
			LOG.error(null, ex);
		}
	}

	@Override
	public void onEvent(ValueEvent t, long l, boolean bln) throws Exception {
		MessagingEvent evt = t.getEvent();
		LOG.info("onEvent processing messaging event from input ringbuffer {}", evt);
		if (evt instanceof StopEvent) {
			processStop();
			return;
		}
		if (evt instanceof LostConnectionEvent) {
			LostConnectionEvent lostEvt = (LostConnectionEvent) evt;
			processor.processConnectionLost(lostEvt);
			return;
		}
		if (evt instanceof ProtocolEvent) {
			ServerChannel session = ((ProtocolEvent) evt).getSession();
			AbstractMessage message = ((ProtocolEvent) evt).getMessage();
			try {
				annotationSupport.dispatch(session, message);
			} catch (Throwable th) {
				LOG.error("Grave error processing the message {} for {}", message, session, th);
			}
		}
	}

	private void processStop() {
		LOG.debug("processStop invoked");
		storageService.close();
		LOG.debug("subscription tree {}", subscriptions.dumpTree());
		subscriptions = null;
		stopLatch.countDown();
	}

	public void setAuthenticator(IAuthenticator authenticator) {
		this.authenticator = authenticator;
	}

	public void setMapStorage(MapDBPersistentStore mapStorage) {
		this.mapStorage = mapStorage;
	}
	
}
