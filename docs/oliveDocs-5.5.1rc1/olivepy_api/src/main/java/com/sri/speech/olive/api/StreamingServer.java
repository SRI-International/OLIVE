package com.sri.speech.olive.api;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.sri.speech.olive.api.Olive.*;
import com.sri.speech.olive.api.workflow.WorkflowUtils;
import com.sri.speech.olive.api.workflow.wrapper.JobResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.ZMQ;
import org.zeromq.ZMQException;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Class StreamingServer is used to stream audio to an OliveStreaming server/process.  Results may or may not come back
 * on this socket... likely won't us protobuf over ZMQ as we will use some other socket for streaming audio.  If
 * message results do come back on this socket then they will be very asynchronous!
 */

final public class StreamingServer {

    // The number of seconds being disconnected before this instance attempts to force a reconnection
    public static final int DEFAULT_FORCE_RECONNECT_SECONDS = 60;
    // the number of forced reconnection attempts to make after a prolonged disconnect
    public static final int DEFAULT_RECONNECT_ATTEMPTS = 60;
    private String clientId;

    private int timeout_seconds;
    private String address;
    private int dataPort;

    // the number of seconds this client is allowed to be  disconnected before an attempt to force a reconnection is made
    private int reconnect_seconds;
    // the number of times a forced reconnection is attempted
    private int max_number_reconnect_attempts = DEFAULT_RECONNECT_ATTEMPTS;
    private int number_reconnects= 0;


    private final AtomicBoolean connected = new AtomicBoolean(false);
    // True if attempting to force a reconnect to a server that has been disconnected for too long (reconnect_seconds)
    private final AtomicBoolean reconnecting = new AtomicBoolean(false);
    private final AtomicBoolean audioConnected = new AtomicBoolean(false);

    private ZMQ.Context context;
    private ZMQ.Socket streamSocket;

    private boolean running = false;

    private ConcurrentLinkedQueue<Message> pending_requests;

    private static final Logger logger = LoggerFactory.getLogger(StreamingServer.class);

    private final List<StreamingMessageListener> listeners = new ArrayList<StreamingMessageListener>();


    public class Result<REQ extends Message, REP> {
        private final REQ req;
        private final REP rep;
        private final String error;

        private Result(REQ req, REP rep, String error) {
            this.req   = req;
            this.rep   = rep;
            this.error = error;
        }

        public REQ getReq() {
            return req;
        }

        public REP getRep() {
            return rep;
        }

        public String getError() {
            return error;
        }

        public boolean hasError() {
            if (error != null && error.length() > 0)
                return true;

            return false;
        }
    }

    // todo remove
    public interface ResultCallback<REQ extends Message, REP> {
        public void call(Result<REQ, REP> r) throws InvalidProtocolBufferException;
    }

    public interface Callback<ARGTYPE> {
        public void call(ARGTYPE a);
    }

    public interface StreamingMessageListener {
        public void receivedError(String error);
        public void receivedMessage(Map<String, JobResult> streamResult);
    }

    public void addMessageListener(StreamingMessageListener l) {
        synchronized (listeners) {
            listeners.add(l);
        }
    }

    public void removeMessageListener(StreamingMessageListener l) {
        synchronized (listeners) {
            listeners.remove(l);
        }
    }

    public void connect(String client_id, String address, int data_port, int timeout_seconds) {
        this.timeout_seconds = timeout_seconds;
        this.clientId = client_id;
        this.address = address;
        this.dataPort = data_port;
        this.reconnect_seconds = DEFAULT_FORCE_RECONNECT_SECONDS;

        pending_requests = new ConcurrentLinkedQueue<Message>();

        connectImpl();
    }

    private void connectImpl(){

        context = ZMQ.context(1);

//        audioSocket = context.socket(ZMQ.SUB);
        streamSocket = context.socket(ZMQ.PAIR);
        String audioAddr = "tcp://" + address + ":" + dataPort;

        boolean tmp = streamSocket.connect(audioAddr);
        audioConnected.set(tmp);
        connected.set(audioConnected.get());
        logger.debug(
                "Streaming audio socket {} to : {} for client: {} ",
                audioConnected.get() ? " connected " : " NOT connected ",
                audioAddr, clientId);

//        audioSocket.subscribe("");

        running = true;

        Runnable processRequestsAndResponses = new Runnable() {
            @Override
            public void run() {
                processRequestsAndResponses();
            }
        };

        startThread(processRequestsAndResponses, "ZMQ Request/Reply");

        // don't restart the disconnect thread, as it is managing the reconnect attempts
        if(!reconnecting.get()){
            Runnable processDisconnect = new Runnable() {
                @Override
                public void run() {
                    processDisconnect();
                }
            };
            startThread(processDisconnect, "Server Disconnect");
        }


        logger.debug("Server {}. Address: {}, Request Port: {}", reconnecting.get() ? "reconnected" : "connected", address, dataPort);
    }

    public boolean equals(Object o) {

        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        // Compare server connections definitions
        return ( ((StreamingServer) o).address.equals(this.address) && ((StreamingServer) o).dataPort == (this.dataPort));

    }

    public boolean hasPendingRequests(){
        return  !pending_requests.isEmpty();
    }

    public String getHostAddress(){
        return address;
    }

    public int getDataPort(){
        return dataPort;
    }

    public void disconnect() {
        logger.info("Disconnecting from server");
        running = false;
        reconnecting.set(false);

        synchronized (connected) {
            connected.notifyAll();
        }
        connected.set(false);
    }

    // TODO WILL WE SUPPORT THIS... WE WON'T KNOW IF DISCONNECTED
    private void reconnect(){

        logger.info("Attempting to recover a connection to the streaming server");
        reconnecting.set(true);
        running = false;
        synchronized (connected) {
            connected.notifyAll();
        }
    }

    public int getReconnectSeconds(){
        return reconnect_seconds;
    }

    public void setReconnectSeconds(int sec){
        reconnect_seconds = sec;
    }

    public int getNumberReconnectAttempts() {
        return max_number_reconnect_attempts;
    }

    public void setNumberReconnectAttempts(int numberReconnectAttempts) {
        this.max_number_reconnect_attempts = numberReconnectAttempts;
    }

    private void startThread(Runnable run, String name) {
        Thread t = new Thread(run);
        t.setName(name);
        t.setDaemon(false);
        t.start();
    }

    private ScenicMessage wrapMessage(Message msg) {
        String id = Integer.toString(System.identityHashCode(msg)); // TODO may not be safe

        ScenicMessage sm = ScenicMessage.newBuilder()
                .setMessageId(id)
                .addMessageData(msg.toByteString())
                .setMessageType(Server.type_map.get(msg.getClass()))
                .build();

        return sm;
    }

    public boolean enqueueAudio(final Message msg) {
        return pending_requests.add(msg);
    }

    protected  void processDisconnect(){

            // Make sure we're running before doing anything
            while (running || reconnecting.get()) {

                synchronized(connected) {
                    while (audioConnected.get()) {
                        try {
                            connected.wait();
                        } catch (InterruptedException e) {
                            // ignore
                        }
                    }
                }

                // If we are
                if(reconnecting.get()){
                    if(!running) {
                        if(number_reconnects < max_number_reconnect_attempts ) {
                            logger.info("Reconnect");
                            number_reconnects++;
                            context.close();
                            context.term();

                            // Force a reconnection
                            connectImpl();
                        }
                        else {
                            reconnecting.set(false);
                        }
                    }

                }
                else {
                    // No longer connected or running
                    context.close();
                    context.term();
                }

            }

    }

    public void processRequestsAndResponses() {

        ZMQ.Poller items = context.poller(1);
        items.register(streamSocket, ZMQ.Poller.POLLIN);

        while (running) {
            // Make sure we're connected before doing anything
            if (!connected.get()) {
                synchronized(connected) {
                    while (!connected.get() && running) {
                        try {
                            connected.wait();
                        } catch (InterruptedException e) {
                            // Loop has us covered
                        }
                    }
                }

                // SCENIC-930: don't poll if disconnected
                if(!running){
                    continue;
                }
            }

            try {
                // Must always send requests before issuing a status request
//                Envelope.Builder sender = Envelope.newBuilder().setSenderId(clientId);
                Message msg = null;

                // The order of the following conditionals is VERY important. The first two must short-circuit the third
                // if they fail, otherwise you'll pop one of the queue and not process it.
                while ( (msg = pending_requests.poll()) != null) {
                    // We assume we have an audio message only, that we send directly to avoid any unnecessary overhead
//                    ScenicMessage wrapped = wrapMessage(msg);
//                    logScenicMessage(wrapped, "Adding request message: ");
//                    sender.addMessage(wrapped);
//
                    logger.debug("Sending message");
                    streamSocket.send(msg.toByteArray());
                }

                items.poll(100);

                if (items.pollin(0)) {
                    Envelope receiver = Envelope.parseFrom(streamSocket.recv());

                    for (ScenicMessage sm : receiver.getMessageList()){
                        logScenicMessage(sm, "Received message: ");
                        processIncomingMessage(sm);
                    }
                }

            } catch (InvalidProtocolBufferException e) {
                logger.error("Exception in request/reply thread: ", e);
            } catch (ZMQException e) {
                // Presumably this just means the UI is closing down. Not an error.
            } catch (Exception e) {
                logger.error("Exception in request/reply thread: ", e);
            }
        }

        streamSocket.close();

        items.unregister(streamSocket);
        items.close();
        audioConnected.set(false);
        synchronized (connected){
            connected.notifyAll();
        }
    }

    private void logScenicMessage(ScenicMessage scenicMessage, String logMsg){

        if(logger.isDebugEnabled()){

            // Don't log active result messages unless TRACE level logging enabled (just too many of 'em)
            if(scenicMessage.getMessageType() == MessageType.GET_ACTIVE_RESULT ){
                logger.trace(logMsg + scenicMessage.getMessageType());
                return;
            }

            StringBuilder builder = new StringBuilder(logMsg);
            builder.append('\n');
            builder.append("\tMessage ID: ").append(scenicMessage.getMessageId()).append('\n');
            builder.append("\tChild Message(s): ").append(scenicMessage.getMessageDataCount()).append('\n');
            builder.append("\tMessage Type: ").append(scenicMessage.getMessageType()).append('\n');

            // log the actual message(s):
            for(ByteString bs : scenicMessage.getMessageDataList()) {
                try {
                    Message m = Server.deserializers[scenicMessage.getMessageType().getNumber()].run(bs);
                    // dumps the whole message (very verbose)
                    if (logger.isTraceEnabled()){
                        builder.append("\t\tChild Message: \n").append(m.toString()).append('\n');
                    }
                    else{
                        // dumps part of the message
                        builder.append("\t\tChild Message: \n").append(m.getDescriptorForType().toString()).append('\n');
                    }
                } catch (InvalidProtocolBufferException e) {
                    logger.warn(e.getMessage());
                }
            }

            logger.debug(builder.toString());

        }
    }

    private void processIncomingMessage(ScenicMessage rep) throws InvalidProtocolBufferException {

        String error  = rep.hasError() ? rep.getError() : null;
        //logScenicMessage(rep, "Received stream response message: ");

        if (rep.getMessageType() != MessageType.WORKFLOW_ANALYSIS_RESULT){
            logger.error("Unexpected message type received from OLIVE Streaming: {}", rep.getMessageType().name());
            return;
        }
        // Decode the message
        Workflow.WorkflowAnalysisResult war = (Workflow.WorkflowAnalysisResult)Server.deserializers[rep.getMessageType().getNumber()].run(rep.getMessageData(0));
        if (war.hasError()){
            for (StreamingMessageListener ml : listeners) {
                ml.receivedError(war.getError());
            }
        }
        else {
            Map<String, JobResult> results = WorkflowUtils.extractWorkflowAnalysis(war);
            for (StreamingMessageListener ml : listeners){
                ml.receivedMessage(results);
            }
        }

    }

    public AtomicBoolean getConnected() {
        return connected;
    }

    public boolean isRunning() {
        return running;
    }


}
