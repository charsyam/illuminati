package com.leekyoungil.illuminati.client.prossor.executor;

import com.leekyoungil.illuminati.client.prossor.config.IlluminatiProperties;
import com.leekyoungil.illuminati.client.prossor.init.IlluminatiClientInit;
import com.leekyoungil.illuminati.client.prossor.model.IlluminatiModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static com.leekyoungil.illuminati.client.prossor.init.IlluminatiClientInit.ILLUMINATI_TEMPLATE;

public class IlluminatiExecutor {

    private static final Logger ILLUMINATI_TEMPLATE_IMPL_LOGGER = LoggerFactory.getLogger(IlluminatiExecutor.class);

    private static final int ILLUMINATI_BAK_LOG = 10000;
    private static final long ILLUMINATI_DEQUEUING_TIMEOUT_MS = 1000;
    private static final long ILLUMINATI_ENQUEUING_TIMEOUT_MS = 0;

    private static Thread ILLUMINATI_SENDER_THREAD;

    public final static BlockingQueue<IlluminatiModel> ILLUMINATI_MODEL_BLOCKING_QUEUE = new LinkedBlockingQueue<IlluminatiModel>(ILLUMINATI_BAK_LOG);

    public synchronized static void init () {
        final Runnable runnableFirst = new Runnable() {
            public void run() {
                while (true) {
                    try {
                        final IlluminatiModel illuminatiModel = ILLUMINATI_MODEL_BLOCKING_QUEUE.poll(ILLUMINATI_DEQUEUING_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                        if (illuminatiModel != null) {
                            if (IlluminatiProperties.ILLUMINATI_DEBUG == false) {
                                ILLUMINATI_TEMPLATE.sendToIlluminati(illuminatiModel.getJsonString());
                            } else {
                                IlluminatiExecutor.sendToIlluminatiByDebug(illuminatiModel);
                            }
                        }
                    } catch (InterruptedException e) {
                        // ignore
                    } catch (Exception e) {
                        ILLUMINATI_TEMPLATE_IMPL_LOGGER.error("Failed to enqueuing the rabbitmq queue.. ("+e.getMessage()+")");
                    }
                }
            }
        };

        ILLUMINATI_SENDER_THREAD = new Thread(runnableFirst);
        ILLUMINATI_SENDER_THREAD.setName("ILLUMINATI_SENDER_THREAD");
        ILLUMINATI_SENDER_THREAD.setDaemon(true);
        ILLUMINATI_SENDER_THREAD.start();

        IlluminatiExecutor.createDebugThread();
    }

    public static void executeToIlluminati (final IlluminatiModel illuminatiModel) {
        if (IlluminatiProperties.ILLUMINATI_DEBUG == false) {
            try {
                ILLUMINATI_MODEL_BLOCKING_QUEUE.offer(illuminatiModel, ILLUMINATI_ENQUEUING_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                ILLUMINATI_TEMPLATE_IMPL_LOGGER.error("Failed to enqueuing the Illuminati queue.. ("+e.getMessage()+")");
            }
        } else {
            IlluminatiExecutor.executeToIlluminatiByDebug(illuminatiModel);
        }
    }

    /**
     * only execute at debug
     * @param illuminatiModel
     */
    private static void executeToIlluminatiByDebug (final IlluminatiModel illuminatiModel) {
        // debug illuminati buffer queue
        if (IlluminatiProperties.ILLUMINATI_DEBUG == true) {
            try {
                final long start = IlluminatiClientInit.ILLUMINATI_TIME_DATA.get();
                ILLUMINATI_MODEL_BLOCKING_QUEUE.offer(illuminatiModel, ILLUMINATI_ENQUEUING_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                final long elapsedTime = IlluminatiClientInit.ILLUMINATI_TIME_DATA.get() - start;
                ILLUMINATI_TEMPLATE_IMPL_LOGGER.info("elapsed time of enqueueing queue is "+elapsedTime+" millisecond");
            } catch (InterruptedException e) {
                ILLUMINATI_TEMPLATE_IMPL_LOGGER.error("Failed to enqueuing the Illuminati queue.. ("+e.getMessage()+")");
            }
        }
    }

    /**
     * only execute at debug
     * @param illuminatiModel
     */
    private static void sendToIlluminatiByDebug (final IlluminatiModel illuminatiModel) {
        // debug illuminati rabbitmq queue
        if (IlluminatiProperties.ILLUMINATI_DEBUG == true) {
            final long start = IlluminatiClientInit.ILLUMINATI_TIME_DATA.get();
            //## sendToIlluminati
            ILLUMINATI_TEMPLATE.sendToIlluminati(illuminatiModel.getJsonString());
            final long elapsedTime = IlluminatiClientInit.ILLUMINATI_TIME_DATA.get() - start;
            ILLUMINATI_TEMPLATE_IMPL_LOGGER.info("elapsed time of Illuminati sent is "+elapsedTime+" millisecond");
        }
    }

    private static void createDebugThread () {
        // debug illuminati buffer queue
        if (IlluminatiProperties.ILLUMINATI_DEBUG == true) {
            final Runnable queueCheckThread = new Runnable() {
                public void run() {
                    while (true) {
                        ILLUMINATI_TEMPLATE_IMPL_LOGGER.info("current queue count : "+ILLUMINATI_MODEL_BLOCKING_QUEUE.size());

                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            // ignore
                        }
                    }
                }
            };

            final Thread queueCheckerThread = new Thread(queueCheckThread);
            queueCheckerThread.setName("ILLUMINATI_QUEUE_CHECK_THREAD");
            queueCheckerThread.setDaemon(true);
            queueCheckerThread.start();
        }
    }
}