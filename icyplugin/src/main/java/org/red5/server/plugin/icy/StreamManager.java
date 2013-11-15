package org.red5.server.plugin.icy;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.red5.logging.Red5LoggerFactory;
import org.red5.server.plugin.icy.stream.NSVConsumer;
import org.slf4j.Logger;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

/**
 * Provides a means to manage streams and threads.
 * 
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class StreamManager implements InitializingBean, DisposableBean {

	private static Logger log = Red5LoggerFactory.getLogger(StreamManager.class, "plugins");
	
	//executor thread pool size
	private int poolSize = 1;
	
	private static ExecutorService executor;
	
	private static final Set<NSVConsumer> consumers = new HashSet<NSVConsumer>();

	@Override
	public void afterPropertiesSet() throws Exception {
		executor = Executors.newFixedThreadPool(poolSize);
	}

	@Override
	public void destroy() throws Exception {
		//clear the set
		consumers.clear();
		//disable new tasks from being submitted
		executor.shutdown();
		try {
			//wait a while for existing tasks to terminate
			if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
				executor.shutdownNow(); // cancel currently executing tasks
				//wait a while for tasks to respond to being canceled
				if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
					System.err.println("Notifier pool did not terminate");
				}
			}
		} catch (InterruptedException ie) {
			// re-cancel if current thread also interrupted
			executor.shutdownNow();
			// preserve interrupt status
			Thread.currentThread().interrupt();
		}
	}
	
	public void addConsumer(final NSVConsumer consumer) {
		log.debug("Add consumer: {}", consumer);
		//add consumer to collection
		if (consumers.add(consumer)) {
			Runnable initer = new Runnable() {
				public void run() {
		    		//start the consumer
		    		consumer.init();	
				}
			};
			StreamManager.submit(initer);
		}
	}
	
	public void removeConsumer(NSVConsumer consumer) {
		log.debug("Remove consumer: {}", consumer);
		//remove it
		if (consumers.remove(consumer)) {
			consumer.stop();
		}
	}
	
	/**
	 * Adds a runnable to the executor service.
	 * 
	 * @param runnable
	 */
	public static void submit(Runnable runnable) {
		log.debug("Submit runnable");
		executor.execute(runnable);
	}

	public int getPoolSize() {
		return poolSize;
	}

	public void setPoolSize(int poolSize) {
		this.poolSize = poolSize;
	}
}
