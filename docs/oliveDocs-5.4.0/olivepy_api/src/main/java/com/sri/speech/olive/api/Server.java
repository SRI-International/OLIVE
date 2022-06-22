package com.sri.speech.olive.api;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.sri.speech.olive.api.Olive.*;
import com.sri.speech.olive.api.utils.CommonUtils;
import com.sri.speech.olive.api.utils.Pair;
import com.sri.speech.olive.api.workflow.BaseWorkflow;
import org.zeromq.ZMQ;
import org.zeromq.ZMQException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Class Server contains the necessary logic to monitor a SCENIC server's
 * heartbeat and send and received requests, either synchronously or
 * asynchronously.
 */

final public class Server {

    // The number of seconds being disconnected before this instance attempts to force a reconnection
    public static final int DEFAULT_FORCE_RECONNECT_SECONDS = 60;
    // the number of forced reconnection attempts to make after a prolonged disconnect
    public static final int DEFAULT_RECONNECT_ATTEMPTS = 60;
    private String client_id;

    private int timeout_seconds;
    private String address;
    private int request_port;
    private int status_port;

    // the number of seconds this client is allowed to be  disconnected before an attempt to force a reconnection is made
    private int reconnect_seconds;
    // the number of times a forced reconnection is attempted
    private int max_number_reconnect_attempts = DEFAULT_RECONNECT_ATTEMPTS;
    private int number_reconnects= 0;

    private final AtomicBoolean connected = new AtomicBoolean(false);
    // True if attempting to force a reconnect to a server that has been disconnected for too long (reconnect_seconds)
    private final AtomicBoolean reconnecting = new AtomicBoolean(false);
    private final AtomicBoolean broadcast_connected     = new AtomicBoolean(false);
    private final AtomicBoolean request_connected       = new AtomicBoolean(false);

    private long last_status;

    private ZMQ.Context context;
    private ZMQ.Socket  request_socket;
    private ZMQ.Socket  broadcast_socket;

    private boolean running = false;

    private String pending_status_request_id = null;

    private ConcurrentLinkedQueue<Pair<Message, ResultCallback>> pending_requests;
    private ConcurrentHashMap<String, Pair<Message, ResultCallback>> callback_map;

    private static final Logger logger = LoggerFactory.getLogger(Server.class);

    private Process server_process = null;

    private final List<ConnectedListener> listeners = new ArrayList<ConnectedListener>();

    private final ServerInfo serverInfo = new ServerInfo();

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

    public interface ResultCallback<REQ extends Message, REP> {
        public void call(Result<REQ, REP> r) throws InvalidProtocolBufferException;
    }

    public interface Callback<ARGTYPE> {
        public void call(ARGTYPE a);
    }

    public interface ConnectedListener {
        public void onConnected();
        public void onReconnected();
        public void onDisconnected();
    }

    public void addConnectedListener(ConnectedListener l) {
        synchronized (listeners) {
            listeners.add(l);
        }
    }

    public void removeConnectedListener(ConnectedListener l) {
        synchronized (listeners) {
            listeners.remove(l);
        }
    }

    public final class ServerInfo implements Cloneable {

        public float cpuPercent;
        public float cpuAverage;
        public float memPercent;
        public float maxMemPercent;
        public float swapPercent;
        public float maxSwapPercent;
        public int poolBusy;
        public int poolPending;
        public int poolFinished;
        public boolean poolReinit;
        public int numberWorkers;
        public String logdir;
        public String version;

        protected ServerInfo() {

        }

        protected void update(Heartbeat heartbeat) {
            if (heartbeat.hasStats()) {
                ServerStats stats = heartbeat.getStats();
                this.cpuPercent = stats.getCpuPercent();
                this.cpuAverage = stats.getCpuAverage();
                this.memPercent = stats.getMemPercent();
                this.maxMemPercent = stats.getMaxMemPercent();
                this.swapPercent = stats.getSwapPercent();
                this.maxSwapPercent = stats.getMaxSwapPercent();
                this.poolBusy = stats.getPoolBusy();
                this.poolPending = stats.getPoolPending();
                this.poolFinished = stats.getPoolFinished();
                this.poolReinit = stats.getPoolReinit();
                // Older servers won't report the number of jobs
                if(stats.hasMaxNumJobs()) {
                    this.numberWorkers = stats.getMaxNumJobs();
                }
                else {
                    this.numberWorkers = 0;
                }
                this.version = stats.getServerVersion();
                if(null == version){
                    logger.error("SERVER VERSION IS NULL!!!!!");
                }


            }
            // note we have not have received any stats


            if (heartbeat.hasLogdir()) {
                this.logdir = heartbeat.getLogdir();
            }
        }

        @Override
        public ServerInfo clone() {
            ServerInfo that = new ServerInfo();
            that.cpuPercent = this.cpuPercent;
            that.cpuAverage = this.cpuAverage;
            that.memPercent = this.memPercent;
            that.maxMemPercent = this.maxMemPercent;
            that.swapPercent = this.swapPercent;
            that.maxSwapPercent = this.maxSwapPercent;
            that.poolBusy = this.poolBusy;
            that.poolPending = this.poolPending;
            that.poolFinished = this.poolFinished;
            that.poolReinit = this.poolReinit;
            that.logdir = this.logdir;
            that.numberWorkers = this.numberWorkers;
            that.version = this.version;
            return that;
        }

        @Override
        public String toString() {
            return String.format("[ServerInfo: version: %s cpu-percent=%1.2f cpu-average=%1.2f mem-percent=%1.2f " +
                    "max-mem-percent=%1.2f swap-percent=%1.2f max-swap-percent=%1.2f max_pool-workers=%d pool-busy=%d " +
                    "pool-pending=%d pool-finished=%d pool-reinit=%b log-dir=%s]",
                    version, cpuPercent, cpuAverage, memPercent, maxMemPercent, swapPercent, maxSwapPercent, numberWorkers, poolBusy,
                    poolPending, poolFinished, poolReinit, logdir);
        }

    }

    /**
     * Return the most recent server info.  NOTE that the stats can be empty/uninitialized since a heartbeat can be
     * sent without any stats, so if numberWorks is 0 then the heartbeat message did not contain any status.  This is
     * likely due to a new server or server restart
     *
     * @return the most recent server info received from the server
     */
    public ServerInfo getServerInfo() {
        synchronized (serverInfo) {
            return serverInfo.clone();
        }
    }

    public Server() {

    }

    public void start(
            final String workDir,
            final int request_port,
            final int status_port,
            final int workers,
            final Callback<String> onCrash) throws IOException {

        String scenic_path = System.getenv("SCENIC");
        List<String> args = new ArrayList<String>();
        if(CommonUtils.getOS() != CommonUtils.OSType.MacOS) {
            args.add("setsid");
        }
        args.add(scenic_path + "/bin/scenicserver");
        args.add("--request_port");
        args.add(Integer.toString(request_port));
        args.add("--status_port");
        args.add(Integer.toString(status_port));
        args.add("--workers");
        args.add(Integer.toString(workers));

        File working_directory_f = new File(workDir);
        working_directory_f.mkdirs();

        ProcessBuilder builder = new ProcessBuilder(args);
        builder.directory(working_directory_f);
        builder.redirectOutput(new File(workDir + "/server-start.out"));
        builder.redirectError(new File(workDir  + "/server-start.err"));

        server_process = builder.start();

        Runnable r = new Runnable() {
            @Override
            public void run() {
                boolean died = false;

                while (!died) {
                    try {
                        server_process.waitFor();

                        died = true;

                        // If we're still supposed to be running, but we're not...
                        if (running) {
                            String msg = "Server process died unexpectedly";

                            try {
                                byte[] encoded = Files.readAllBytes(Paths.get(workDir + "/server-error.out"));
                                String s = Charset.defaultCharset().decode(ByteBuffer.wrap(encoded)).toString();

                                if (s.length() > 0)
                                    msg += ":\n\n " + s.substring(Math.max(0, s.length() - 256), s.length());

                            } catch (IOException e) {
                                logger.error("Failed to read server-error.out", e);
                            }

                            logger.error(msg);

                            if (onCrash != null) {
                                onCrash.call(msg);
                            }
                        }
                    } catch (InterruptedException e) {

                    }
                }
            }
        };

        Thread t = new Thread(r);
        t.setDaemon(true);
        t.setName("server_proc_mon");
        t.start();
    }

    public void connect(String client_id, String address, int request_port, int status_port, int timeout_seconds) {

        this.timeout_seconds = timeout_seconds;
        this.client_id = client_id;
        this.address = address;
        this.request_port = request_port;
        this.status_port = status_port;
        this.reconnect_seconds = DEFAULT_FORCE_RECONNECT_SECONDS;

        callback_map = new ConcurrentHashMap<String, Pair<Message, ResultCallback>>();
        pending_requests = new ConcurrentLinkedQueue<Pair<Message, ResultCallback>>();

        connectImpl();
    }

    public boolean equals(Object o) {

        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        // Compare server connections definitions
        return ( ((Server) o).address.equals(this.address) && ((Server) o).request_port == (this.request_port) && ((Server) o).status_port == (this.status_port));

    }


    private void connectImpl(){

        context = ZMQ.context(1);

        request_socket   = context.socket(ZMQ.DEALER);
        broadcast_socket = context.socket(ZMQ.SUB);

        request_connected.set(request_socket.connect("tcp://" + address + ":" + request_port));
        broadcast_connected.set(broadcast_socket.connect("tcp://" + address + ":" + status_port));

        broadcast_socket.subscribe("");

        running = true;

        last_status = System.currentTimeMillis();

        Runnable monitorBroadcast = new Runnable() {
            @Override
            public void run() {
                monitorBroadcasts();
            }
        };

        Runnable processRequestsAndResponses = new Runnable() {
            @Override
            public void run() {
                processRequestsAndResponses();
            }
        };

        startThread(monitorBroadcast, "ZMQ Broadcast Monitor");
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



        logger.debug("Server {}. Address: {}, Request Port: {}, Status Port: {}", reconnecting.get() ? "reconnected" : "connected", address, request_port, status_port);
    }


    public boolean hasPendingRequests(){
        return !callback_map.isEmpty() || !pending_requests.isEmpty();
    }

    public String getHostAddress(){
        return address;
    }

    public int getStatusPort(){
        return status_port;
    }

    public int getRequestPort(){
        return request_port;
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

    private void reconnect(){

        logger.info("Attempting to recover a connection to server");
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
                .setMessageType(type_map.get(msg.getClass()))
                .build();

        return sm;
    }

    public boolean enqueueRequest(final Message msg, final ResultCallback callback) {
        return pending_requests.add(new Pair<Message, ResultCallback>(msg, callback));
    }

    public <REQ extends Message, REP> Result<REQ, REP> synchRequest(REQ req) {
        final Wrapper<Result<REQ, REP>> result = new Wrapper<>();

        ResultCallback cb = new ResultCallback() {
            @Override
            public void call(Result res) {
                synchronized(result) {
                    result.set(res);
                    result.notify();
                }
            }
        };

        enqueueRequest(req, cb);

        while (result.get() == null) {
            synchronized(result) {
                try {
                    result.wait();
                } catch (InterruptedException e) {
                    // Loop has us covered
                }
            }
        }

        return result.get();
    }

    private void issueStatusRequest() {
        pending_status_request_id = UUID.randomUUID().toString();

        GetActiveRequest request = GetActiveRequest.newBuilder().build();

        ScenicMessage sm = ScenicMessage.newBuilder()
                .setMessageId(pending_status_request_id)
                .addMessageData(request.toByteString())
                .setMessageType(MessageType.GET_ACTIVE_REQUEST)
                .build();

        Envelope.Builder builder = Envelope.newBuilder().setSenderId(client_id);
        builder.addMessage(sm);

        Envelope env = builder.build();

        logger.debug("Sending get active request");
        logger.debug(env.toString());

        request_socket.send(env.toByteArray());

        last_status = System.currentTimeMillis();
    }


    protected  void processDisconnect(){

            // Make sure we're running before doing anything
            while (running || reconnecting.get()) {

                synchronized(connected) {
                    while (request_connected.get() || broadcast_connected.get()) {
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

            //logger.info("CLG processDisconnect exiting.  Running: {}, reconnecting: {}", running, reconnecting.get());
    }


    public void processRequestsAndResponses() {

        ZMQ.Poller items = context.poller(1);
        items.register(request_socket, ZMQ.Poller.POLLIN);

        while (running) {
            boolean reconnected = false;
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
                // At this point, we must be reconnected.
                reconnected = true;
            }

            try {
                // Must always send requests before issuing a status request
                Envelope.Builder sender = Envelope.newBuilder().setSenderId(client_id);
                int added = 0;

                Pair<Message, ResultCallback> ready_pair = null;

                // The order of the following conditionals is VERY important. The first two must short-circuit the third
                // if they fail, otherwise you'll pop one of the queue and not process it.
                while (pending_status_request_id == null && added < 500 && (ready_pair = pending_requests.poll()) != null) {
                    Message msg = ready_pair.getFirst();
                    ScenicMessage wrapped = wrapMessage(msg);

                    logScenicMessage(wrapped, "Adding request message: ");
                    sender.addMessage(wrapped);

                    callback_map.put(wrapped.getMessageId(), ready_pair);
                    added++;
                }

                if (added > 0) {
                    logger.debug("Sending " + added + " messages");
                    request_socket.send(sender.build().toByteArray());
                }

                if (System.currentTimeMillis() - last_status > 60000 || reconnected)
                    issueStatusRequest();

                items.poll(100);

                if (items.pollin(0)) {
                    Envelope receiver = Envelope.parseFrom(request_socket.recv());

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

        // Secret squirrel message to cause the daemon to shut down
        if (server_process != null  && connected.get())
            request_socket.send("ShUtDoWn");

        request_socket.close();

        items.unregister(request_socket);
        callback_map.clear();
        items.close();
        request_connected.set(false);
        synchronized (connected){
            connected.notifyAll();
        }
    }

    public void shutdownServer(){
        // Force a clean shutdown
        if (connected.get())
            request_socket.send("ShUtDoWn");
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
                    Message m = deserializers[scenicMessage.getMessageType().getNumber()].run(bs);
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
        if (rep.getMessageType() == MessageType.GET_ACTIVE_RESULT) {
            // Make sure it's the one we're expecting, otherwise we aren't guaranteed
            // it will reflect our callback_map accurately
            if (rep.getMessageId().equals(pending_status_request_id)) {

                GetActiveResult active = GetActiveResult.parseFrom(rep.getMessageData(0));

                logger.debug("Received get active response");
                logger.debug("Active requests: " + active.getMessageIdList().toString());

                for (String id : callback_map.keySet()) {
                    if (!active.getMessageIdList().contains(id)) {
                        logger.warn("Response for " + id + " lost!");

                        Pair<Message, ResultCallback> pair = callback_map.remove(id);
                        Message req = pair.getFirst();
                        ResultCallback cb = pair.getSecond();

                        Result res = new Result(req, null, "Message lost somewhere in network traffic");
                        if (cb != null)
                            cb.call(res);
                    }
                }

                // No longer need to block pending requests
                pending_status_request_id = null;
                return;

            }
            // Otherwise
        }

        if (callback_map.containsKey(rep.getMessageId())) {
            Pair<Message, ResultCallback> pair = callback_map.remove(rep.getMessageId());

            Message req = pair.getFirst();
            ResultCallback cb = pair.getSecond();

            logScenicMessage(rep, "Received response message: ");

            if (cb != null) {
                String error  = rep.hasError() ? rep.getError() : null;
                // TODO DO THE SAME THING FOR INFO String info = rep.hasInfo() ? rep.getInfo() : null;

                Result res = null;

                switch (type_map.get(req.getClass())) {
                default:
                    if (rep.getMessageDataCount() > 0)
                        res = new Result(req, deserializers[rep.getMessageType().getNumber()].run(rep.getMessageData(0)), error);
                    else
                        res = new Result(req, null, error);
                    break;
                }

                cb.call(res);
            }
        } else {
            logger.warn("Unexpected message: " + rep);
        }
    }

    public static Message deserialzieMessage(MessageType type,  ByteString bs) throws InvalidProtocolBufferException {
        return deserializers[type.getNumber()].run(bs);
    }

    public void monitorBroadcasts() {
        long last_heartbeat = System.currentTimeMillis();

        ZMQ.Poller items = context.poller(1);
        items.register(broadcast_socket, ZMQ.Poller.POLLIN);

        boolean disconnected = false;

        while (running ) {
            try {

                if (!connected.get() && System.currentTimeMillis() - last_heartbeat  > (reconnect_seconds * 1000)) {
                    // reset heartbeat
                    last_heartbeat =  System.currentTimeMillis() - (timeout_seconds *1000);
                    this.reconnect();

                }
                else if (connected.get() && System.currentTimeMillis() - last_heartbeat  > (timeout_seconds * 1000)) {
                    logger.info("Disconnected from server");
                    connected.set(false);
                    disconnected = true;
                    for (ConnectedListener l : listeners)
                        l.onDisconnected();
                }

                items.poll(100);

                if (items.pollin(0)) {

                    byte[] beatbytes = broadcast_socket.recv();
                    ByteArrayInputStream bis = new ByteArrayInputStream(beatbytes);

                    Heartbeat heartbeat = Heartbeat.parseFrom(bis);
                    synchronized (serverInfo) {
                        serverInfo.update(heartbeat);
                    }
                    bis.close();
                    logger.debug("server-info={}", getServerInfo());

                    last_heartbeat = System.currentTimeMillis();
                    if (!connected.get()) {
                        boolean reconnected = reconnecting.get();
                        synchronized(connected) {
                            connected.set(true);
                            reconnecting.set(false);
                            number_reconnects = 0;
                            connected.notifyAll();
                        }

                        String servStr = address + " - request port: " + request_port + " status_port: " + status_port;
                        logger.info((disconnected ? "Reconnected" : "Connected") + " to " + servStr);
                        synchronized(listeners) {
                            for (ConnectedListener l : listeners)
                                if (disconnected || reconnected) l.onReconnected(); else l.onConnected();
                        }
                        disconnected = false;
                    }
                }

            } catch (ZMQException e) {
                // Presumably this just means the UI is closing down. Not an error.
            } catch (Exception e) {
                logger.error("Exception on status monitor thread", e);
            }
        }
        broadcast_socket.close();
        items.unregister(broadcast_socket);
        items.close();
        broadcast_connected.set(false);
        synchronized (connected){
            connected.notifyAll();
        }
    }

    public void forcibleShutdown() {
        running = false;
        if (server_process != null) {
            try {
                Field f = server_process.getClass().getDeclaredField("pid");
                f.setAccessible(true);
                int pid = (Integer) f.get(server_process);
                Runtime.getRuntime().exec("kill -SIGINT " + Integer.toString(pid));
            } catch (Exception e) {
                logger.error("Error during forcible shutdown", e);
            }
        }
    }

    public AtomicBoolean getConnected() {
        return connected;
    }

    public boolean isRunning() {
        return running;
    }

    class ServerException extends Exception {
        private static final long serialVersionUID = 1L;

        public ServerException(String msg) {
            super(msg);
        }
    }

    // Silly utility classes that should be included in Java proper!
//    public static class Pair<T1, T2> {
//        private final T1 v1;
//        private final T2 v2;
//
//        public Pair(T1 v1, T2 v2) {
//            this.v1 = v1;
//            this.v2 = v2;
//        }
//
//        public T1 getKey() {
//            return v1;
//        }
//
//        public T2 getValue() {
//            return v2;
//        }
//    }

    class Wrapper<T> {
        protected T thing;

        public T get() {
            return thing;
        }

        public void set(T thing) {
            this.thing = thing;
        }
    }

    interface Deserializer {
        public Message run(ByteString bytestr) throws InvalidProtocolBufferException;
    }

    public abstract class MetadataWrapper<T> extends Wrapper<T> {

        public abstract String toString();
        public abstract T getValue();
    }

    public static final Deserializer[]  deserializers = {
            null,
            new Deserializer() { //1
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return PluginDirectoryRequest.parseFrom(bytestr);
                }
            },
            new Deserializer() {//2
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return PluginDirectoryResult.parseFrom(bytestr);
                }
            },
            new Deserializer() {//3
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return GlobalScorerRequest.parseFrom(bytestr);
                }
            },
            new Deserializer() {//4
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return GlobalScorerResult.parseFrom(bytestr);
                }
            },
            new Deserializer() {//5
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return RegionScorerRequest.parseFrom(bytestr);
                }
            },
            new Deserializer() {//6
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return RegionScorerResult.parseFrom(bytestr);
                }
            },
            new Deserializer() {//7
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return FrameScorerRequest.parseFrom(bytestr);
                }
            },
            new Deserializer() {//8
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return FrameScorerResult.parseFrom(bytestr);
                }
            },
            new Deserializer() {//9
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return ClassModificationRequest.parseFrom(bytestr);
                }
            },
            new Deserializer() {//10
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return ClassModificationResult.parseFrom(bytestr);
                }
            },
            new Deserializer() {//11
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return ClassRemovalRequest.parseFrom(bytestr);
                }
            },
            new Deserializer() {//12
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return ClassRemovalResult.parseFrom(bytestr);
                }
            },
            new Deserializer() {//13
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return GetActiveRequest.parseFrom(bytestr);
                }
            },
            new Deserializer() {//14
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return GetActiveResult.parseFrom(bytestr);
                }
            },
            new Deserializer() {//15
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return LoadPluginDomainRequest.parseFrom(bytestr);
                }
            },
            new Deserializer() {//16
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return LoadPluginDomainResult.parseFrom(bytestr);
                }
            },

            new Deserializer() {//17
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return GetStatusRequest.parseFrom(bytestr);
                }
            },
            new Deserializer() {//18
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return GetStatusResult.parseFrom(bytestr);
                }
            },


            ///////
            new Deserializer() { //19
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return Heartbeat.parseFrom(bytestr);
                }
            },
            new Deserializer() {//20
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return PreprocessAudioTrainRequest.parseFrom(bytestr);
                }
            },
            new Deserializer() {//21
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return PreprocessAudioTrainResult.parseFrom(bytestr);
                }
            },
            new Deserializer() {//22
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return PreprocessAudioAdaptRequest.parseFrom(bytestr);
                }
            },
            new Deserializer() {//23
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return PreprocessAudioAdaptResult.parseFrom(bytestr);
                }
            },
            new Deserializer() {//24
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return SupervisedTrainingRequest.parseFrom(bytestr);
                }
            },
            new Deserializer() {//25
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return SupervisedTrainingResult.parseFrom(bytestr);
                }
            },
            new Deserializer() {//26
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return SupervisedAdaptationRequest.parseFrom(bytestr);
                }
            },
            new Deserializer() {//27
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return SupervisedAdaptationResult.parseFrom(bytestr);
                }
            },
            new Deserializer() {//28
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return UnsupervisedAdaptationRequest.parseFrom(bytestr);
                }
            },
            new Deserializer() {//29
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return UnsupervisedAdaptationResult.parseFrom(bytestr);
                }
            },
            new Deserializer() {//30
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return ClassAnnotation.parseFrom(bytestr);
                }
            },
            new Deserializer() {//31
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return AudioAnnotation.parseFrom(bytestr);
                }
            },
            new Deserializer() {//32
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return AnnotationRegion.parseFrom(bytestr);
                }
            },
            new Deserializer() {//33
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return RemovePluginDomainRequest.parseFrom(bytestr);
                }
            },
            new Deserializer() {//34
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return RemovePluginDomainResult.parseFrom(bytestr);
                }
            },
            new Deserializer() {//35 *
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return AudioModificationRequest.parseFrom(bytestr);
                }
            },
            new Deserializer() {//36 *
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return AudioModificationResult.parseFrom(bytestr);
                }
            },
            new Deserializer() {//37 *
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return PluginAudioVectorRequest.parseFrom(bytestr);
                }
            },
            new Deserializer() {//38 *
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return PluginAudioVectorResult.parseFrom(bytestr);
                }
            },

            new Deserializer() {//39 *
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return ClassExportRequest.parseFrom(bytestr);
                }
            },
            new Deserializer() {//40 *
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return ClassExportResult.parseFrom(bytestr);
                }
            },
            new Deserializer() {//41 *
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return ClassImportRequest.parseFrom(bytestr);
                }
            },
            new Deserializer() {//42 *
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return ClassImportResult.parseFrom(bytestr);
                }
            },

            // Gap for stereo messages removed in 5.0
            new Deserializer() {//43 *
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return ApplyUpdateRequest.parseFrom(bytestr);
                }
            },
            new Deserializer() {//44 *
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return ApplyUpdateResult.parseFrom(bytestr);
                }
            },
            new Deserializer() {//45 *
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return GetUpdateStatusRequest.parseFrom(bytestr);
                }
            },
            new Deserializer() {//46 *
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return GetUpdateStatusResult.parseFrom(bytestr);
                }
            },
            new Deserializer() {//47 *
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return GlobalComparerRequest.parseFrom(bytestr);
                }
            },
            new Deserializer() {//48 *
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return GlobalComparerResult.parseFrom(bytestr);
                }
            },
            // ==== WORKFLOW ======================

            new Deserializer() {//49 *
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return WorkflowActualizeRequest.parseFrom(bytestr);
                }
            },new Deserializer() {//50 *
        @Override
            public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                return WorkflowActualizeResult.parseFrom(bytestr);
                }
            },new Deserializer() {//51 *
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return WorkflowAnalysisRequest.parseFrom(bytestr);
                }
            },new Deserializer() {//52 *
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return WorkflowAnalysisResult.parseFrom(bytestr);
                }
            },new Deserializer() {//53 *
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return WorkflowEnrollRequest.parseFrom(bytestr);
                }
            },new Deserializer() {//54 *
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return WorkflowUnenrollRequest.parseFrom(bytestr);
                }
            },new Deserializer() {//55 *
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return WorkflowAdaptRequest.parseFrom(bytestr);
                }
            },new Deserializer() {//56 *
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return WorkflowAdaptResult.parseFrom(bytestr);
                }
            },new Deserializer() {//57 *
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return WorkflowDefinition.parseFrom(bytestr);
                }
            },new Deserializer() {//58 *
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return WorkflowTask.parseFrom(bytestr);
                }
            },new Deserializer() {//59 *
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return AbstractWorkflowPluginTask.parseFrom(bytestr);
                }
            },new Deserializer() {//60 *
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return ConditionalWorkflowPluginTask.parseFrom(bytestr);
                }
            },new Deserializer() {//61 *
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return OliveNodeWorkflow.parseFrom(bytestr);
                }

            },new Deserializer() {//62 *
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return WorkflowJobResult.parseFrom(bytestr);
                }
            },new Deserializer() {//63 *
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return WorkflowTaskResult.parseFrom(bytestr);
                }
            },new Deserializer() {//64 *
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return WorkflowDataRequest.parseFrom(bytestr);
                }
            },new Deserializer() {//65 *
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return WorkflowDataResult.parseFrom(bytestr);
                }
            },new Deserializer() {//66 *
            @Override
            public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                return WorkflowClassStatusRequest.parseFrom(bytestr);
            }
            },new Deserializer() {//67 *
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return WorkflowClassStatusResult.parseFrom(bytestr);
                }
            }



            ,new Deserializer() {//68 *
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return AudioAlignmentScoreRequest.parseFrom(bytestr);
                }
            }
            ,new Deserializer() {//69 *
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return AudioAlignmentScoreResult.parseFrom(bytestr);
                }
            },new Deserializer() {//70 *
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return TextTransformationRequest.parseFrom(bytestr);
                }
            },new Deserializer() {//71 *
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return TextTransformationResult.parseFrom(bytestr);
                }
            },new Deserializer() {//72 *
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return PreprocessedAudioResult.parseFrom(bytestr);
                }
            },new Deserializer() {//73 *
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return DynamicPluginRequest.parseFrom(bytestr);
                }
            },new Deserializer() {//74 *
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return Plugin2PluginRequest.parseFrom(bytestr);
                }
            },new Deserializer() {//75 *
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return Plugin2PluginResult.parseFrom(bytestr);
                }
            },new Deserializer() {//76 *
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return WorkflowTextResult.parseFrom(bytestr);
                }
            },
           new Deserializer() {//77 *
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return ScoreOutputTransformRequest.parseFrom(bytestr);
                }
            },new Deserializer() {//78 *
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return ScoreOutputTransformResult.parseFrom(bytestr);
                }
            },new Deserializer() {//79 *
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return DataOutputTransformRequest.parseFrom(bytestr);
                }
            },new Deserializer() {//80 *
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return DataOutputTransformResult.parseFrom(bytestr);
                }
            },new Deserializer() {//81 *
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return BoundingBoxScorerRequest.parseFrom(bytestr);
                }
            },new Deserializer() {//82 *
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return BoundingBoxScorerResult.parseFrom(bytestr);
                }
            },new Deserializer() {//83 *
                @Override
                public Message run(ByteString bytestr) throws InvalidProtocolBufferException {
                    return WorkflowBinaryMediaResult.parseFrom(bytestr);
                }
            },

            null, // INVALID_MESSAGE (error only) // 84
    };

    public static final Map<Class<?>, MessageType> type_map = new HashMap<Class<?>, MessageType>();
    public static final Map<MessageType, Class<?>> class_map = new HashMap<MessageType, Class<?>>();

    static
    {
        type_map.put(PluginDirectoryRequest.class, MessageType.PLUGIN_DIRECTORY_REQUEST);
        type_map.put(PluginDirectoryResult.class, MessageType.PLUGIN_DIRECTORY_RESULT);
        type_map.put(GlobalScorerRequest.class, MessageType.GLOBAL_SCORER_REQUEST);
        type_map.put(GlobalScorerResult.class,  MessageType.GLOBAL_SCORER_RESULT);
        type_map.put(RegionScorerRequest.class, MessageType.REGION_SCORER_REQUEST);
        type_map.put(RegionScorerResult.class, MessageType.REGION_SCORER_RESULT);
        type_map.put(FrameScorerRequest.class, MessageType.FRAME_SCORER_REQUEST);
        type_map.put(FrameScorerResult.class, MessageType.FRAME_SCORER_RESULT);
        type_map.put(ClassModificationRequest.class, MessageType.CLASS_MODIFICATION_REQUEST);
        type_map.put(ClassModificationResult.class, MessageType.CLASS_MODIFICATION_RESULT);
        type_map.put(ClassRemovalRequest.class, MessageType.CLASS_REMOVAL_REQUEST);
        type_map.put(ClassRemovalResult.class, MessageType.CLASS_REMOVAL_RESULT);
        type_map.put(GetActiveRequest.class, MessageType.GET_ACTIVE_REQUEST);
        type_map.put(GetActiveResult.class, MessageType.GET_ACTIVE_RESULT);
        type_map.put(LoadPluginDomainRequest.class, MessageType.LOAD_PLUGIN_REQUEST);
        type_map.put(LoadPluginDomainResult.class, MessageType.LOAD_PLUGIN_RESULT);
        type_map.put(GetStatusRequest.class, MessageType.GET_STATUS_REQUEST);
        type_map.put(GetStatusResult.class, MessageType.GET_STATUS_RESULT);
        type_map.put(Heartbeat.class, MessageType.HEARTBEAT);
        type_map.put(PreprocessAudioTrainRequest.class,     MessageType.PREPROCESS_AUDIO_TRAIN_REQUEST);
        type_map.put(PreprocessAudioTrainResult.class,      MessageType.PREPROCESS_AUDIO_TRAIN_RESULT);
        type_map.put(PreprocessAudioAdaptRequest.class,     MessageType.PREPROCESS_AUDIO_ADAPT_REQUEST);
        type_map.put(PreprocessAudioAdaptResult.class,      MessageType.PREPROCESS_AUDIO_ADAPT_RESULT);
        type_map.put(SupervisedTrainingRequest.class,       MessageType.SUPERVISED_TRAINING_REQUEST);
        type_map.put(SupervisedTrainingResult.class,        MessageType.SUPERVISED_TRAINING_RESULT);
        type_map.put(SupervisedAdaptationRequest.class, MessageType.SUPERVISED_ADAPTATION_REQUEST);
        type_map.put(SupervisedAdaptationResult.class, MessageType.SUPERVISED_ADAPTATION_RESULT);
        type_map.put(UnsupervisedAdaptationRequest.class, MessageType.UNSUPERVISED_ADAPTATION_REQUEST);
        type_map.put(UnsupervisedAdaptationResult.class, MessageType.UNSUPERVISED_ADAPTATION_RESULT);
        type_map.put(ClassAnnotation.class, MessageType.CLASS_ANNOTATION);
        type_map.put(AudioAnnotation.class, MessageType.AUDIO_ANNOTATION);
        type_map.put(AnnotationRegion.class, MessageType.ANNOTATION_REGION);
        type_map.put(RemovePluginDomainRequest.class, MessageType.REMOVE_PLUGIN_REQUEST);
        type_map.put(RemovePluginDomainResult.class, MessageType.REMOVE_PLUGIN_RESULT);
        type_map.put(AudioModificationRequest.class, MessageType.AUDIO_MODIFICATION_REQUEST);
        type_map.put(AudioModificationResult.class, MessageType.AUDIO_MODIFICATION_RESULT);
        type_map.put(PluginAudioVectorRequest.class, MessageType.PLUGIN_AUDIO_VECTOR_REQUEST);
        type_map.put(PluginAudioVectorResult.class, MessageType.PLUGIN_AUDIO_VECTOR_RESULT);
        type_map.put(ClassExportRequest.class, MessageType.CLASS_EXPORT_REQUEST);
        type_map.put(ClassExportResult.class, MessageType.CLASS_EXPORT_RESULT);
        type_map.put(ClassImportRequest.class, MessageType.CLASS_IMPORT_REQUEST);
        type_map.put(ClassImportResult.class, MessageType.CLASS_IMPORT_RESULT);

        type_map.put(ApplyUpdateRequest.class, MessageType.APPLY_UPDATE_REQUEST);
        type_map.put(ApplyUpdateResult.class, MessageType.APPLY_UPDATE_RESULT);
        type_map.put(GetUpdateStatusRequest.class, MessageType.GET_UPDATE_STATUS_REQUEST);
        type_map.put(GetUpdateStatusResult.class, MessageType.GET_UPDATE_STATUS_RESULT)
        ;
        type_map.put(GlobalComparerRequest.class, MessageType.GLOBAL_COMPARER_REQUEST);
        type_map.put(GlobalComparerResult.class, MessageType.GLOBAL_COMPARER_RESULT);

        // Workflow messages
        type_map.put(WorkflowActualizeRequest.class, MessageType.WORKFLOW_ACTUALIZE_REQUEST);
        type_map.put(WorkflowActualizeResult.class, MessageType.WORKFLOW_ACTUALIZE_RESULT);
        type_map.put(WorkflowAnalysisRequest.class, MessageType.WORKFLOW_ANALYSIS_REQUEST);
        type_map.put(WorkflowAnalysisResult.class, MessageType.WORKFLOW_ANALYSIS_RESULT);
        type_map.put(WorkflowEnrollRequest.class, MessageType.WORKFLOW_ENROLL_REQUEST);
        type_map.put(WorkflowUnenrollRequest.class, MessageType.WORKFLOW_UNENROLL_REQUEST);
        type_map.put(WorkflowAdaptRequest.class, MessageType.WORKFLOW_ADAPT_REQUEST);
        type_map.put(WorkflowAdaptResult.class, MessageType.WORKFLOW_ADAPT_RESULT);
        type_map.put(WorkflowDefinition.class, MessageType.WORKFLOW_DEFINITION);
        type_map.put(WorkflowTask.class, MessageType.WORKFLOW_TASK);
        type_map.put(AbstractWorkflowPluginTask.class, MessageType.ABSTRACT_WORKFLOW_TASK);
        type_map.put(ConditionalWorkflowPluginTask.class, MessageType.CONDITIONAL_WORKFLOW_TASK);
        type_map.put(OliveNodeWorkflow.class, MessageType.OLIVE_NODE);
        type_map.put(WorkflowJobResult.class, MessageType.WORKFLOW_JOB_RESULT);
        type_map.put(WorkflowTaskResult.class, MessageType.WORKFLOW_TASK_RESULT);
        type_map.put(WorkflowDataRequest.class, MessageType.WORKFLOW_DATA_REQUEST);
        type_map.put(WorkflowDataResult.class, MessageType.WORKFLOW_DATA_RESULT);
        type_map.put(WorkflowClassStatusRequest.class, MessageType.WORKFLOW_CLASS_REQUEST);
        type_map.put(WorkflowClassStatusResult.class, MessageType.WORKFLOW_CLASS_RESULT);

        // Messages added after workflows added
        type_map.put(AudioAlignmentScoreRequest.class, MessageType.AUDIO_ALIGN_REQUEST);
        type_map.put(AudioAlignmentScoreResult.class, MessageType.AUDIO_ALIGN_RESULT);
        type_map.put(TextTransformationRequest.class, MessageType.TEXT_TRANSFORM_REQUEST);
        type_map.put(TextTransformationResult.class, MessageType.TEXT_TRANSFORM_RESULT);
        type_map.put(PreprocessedAudioResult.class, MessageType.PREPROCESSED_AUDIO_RESULT);
        type_map.put(DynamicPluginRequest.class, MessageType.DYNAMIC_PLUGIN_REQUEST);
        type_map.put(Plugin2PluginRequest.class, MessageType.PLUGIN_2_PLUGIN_REQUEST);
        type_map.put(Plugin2PluginResult.class, MessageType.PLUGIN_2_PLUGIN_RESULT);
        type_map.put(WorkflowTextResult.class, MessageType.WORKFlOW_TEXT_RESULT);
        type_map.put(ScoreOutputTransformRequest.class, MessageType.SCORE_OUTPUT_TRANSFORMER_REQUEST);
        type_map.put(ScoreOutputTransformResult.class, MessageType.SCORE_OUTPUT_TRANSFORMER_RESULT);
        type_map.put(BoundingBoxScorerRequest.class, MessageType.BOUNDING_BOX_REQUEST);
        type_map.put(BoundingBoxScorerResult.class, MessageType.BOUNDING_BOX_RESULT);

    };

    static {
        for (Class m : type_map.keySet()){
            class_map.put(type_map.get(m), m);
        }
    }

    // Plugin analysis messages
    public static final List< MessageType> analysis_list = new ArrayList<>();

    static
    {
        analysis_list.add(MessageType.REGION_SCORER_REQUEST);
        analysis_list.add(MessageType.GLOBAL_SCORER_REQUEST);
        analysis_list.add(MessageType.GLOBAL_COMPARER_REQUEST);
        analysis_list.add(MessageType.FRAME_SCORER_REQUEST);
        analysis_list.add(MessageType.TEXT_TRANSFORM_REQUEST);
        analysis_list.add(MessageType.AUDIO_ALIGN_REQUEST);
        analysis_list.add(MessageType.AUDIO_ALIGN_REQUEST);
        analysis_list.add(MessageType.BOUNDING_BOX_REQUEST);
    };

    /**
     * Deserialized MetadataScore's value into a MetadataWrapper based on the type of the MetadataScore.
     *
     * @param ms the MetadataScore object whose value is deserialized
     *
     * @return a typed instance of the score's value
     *
     * @throws InvalidProtocolBufferException if the value could not be deserialized
     *
     */
    public  MetadataWrapper deserializeMetadata(Metadata ms) throws InvalidProtocolBufferException {

        return deserializeMetadataImpl(ms.getType(), ms.getValue());
       /* switch (ms.getType()){
            case LIST_META:
                MetadataWrapper<List> mwl = new MetadataWrapper<List>() {
                    @Override
                    public String toString() {
                        // todo better formatting of values...
                        return this.thing.toString();
                    }
                    @Override
                    public List getValue() {
                        return this.thing;
                    }
                };
                List values = new ArrayList();
                ListMetadata  lm = ListMetadata.parseFrom(ms.getValue());
                for(int i = 0; i < lm.getTypeCount(); i++){
                    values.add(deserializeMetadataImpl(lm.getType(i), lm.getValue(i)));
                }

                mwl.set(values);
                return mwl;
            default:
                return deserializeMetadataImpl(ms.getType(), ms.getValue());
        }*/

    }


    protected  MetadataWrapper deserializeMetadataImpl(MetadataType type, ByteString data) throws InvalidProtocolBufferException {

        switch (type){
            case STRING_META:
                MetadataWrapper<String> mw = new MetadataWrapper<String>() {
                    @Override
                    public String toString() {
                        return this.thing;
                    }
                    @Override
                    public String getValue() {
                        return this.thing;
                    }
                };
                mw.set(StringMetadata.parseFrom(data).getValue());
                return mw;
            case INTEGER_META:
                MetadataWrapper<Integer> mwi = new MetadataWrapper<Integer>() {
                    @Override
                    public String toString() {
                        return Integer.toString(this.thing);
                    }
                    @Override
                    public Integer getValue() {
                        return this.thing;
                    }
                };
                mwi.set(IntegerMetadata.parseFrom(data).getValue());
                return mwi;

            case DOUBLE_META:
                MetadataWrapper<Double> mwd = new MetadataWrapper<Double>() {
                    @Override
                    public String toString() {
                        return Double.toString(this.thing);
                    }
                    @Override
                    public Double getValue() {
                        return this.thing;
                    }
                };
                mwd.set(DoubleMetadata.parseFrom(data).getValue());
                return mwd;
            case BOOLEAN_META:
                MetadataWrapper<Boolean> mwb = new MetadataWrapper<Boolean>() {
                    @Override
                    public String toString() {
                        return this.thing.toString();
                    }
                    @Override
                    public Boolean getValue() {
                        return this.thing;
                    }
                };
                mwb.set(BooleanMetadata.parseFrom(data).getValue());
                return mwb;
            case LIST_META:

                MetadataWrapper<List> mwl = new MetadataWrapper<List>() {
                    @Override
                    public String toString() {
                        // todo better formatting of values...
                        return this.thing.toString();
                    }
                    @Override
                    public List getValue() {
                        return this.thing;
                    }
                };

                List values = new ArrayList();
                ListMetadata  lm = ListMetadata.parseFrom(data);
                for(int i = 0; i < lm.getTypeCount(); i++){
                    //values.add(deserializeMetadataImpl(lm.getType(i), lm.getValue(i)));
                    values.add(deserializeMetadataImpl(lm.getType(i), lm.getValue(i)));
                }

                mwl.set(values);
                return mwl;




            // A list can not contain a list...
            /*case LIST_META:
                MetadataWrapper<List> mwl = new MetadataWrapper<List>() {
                    @Override
                    public String toString() {
                        return this.thing.toString();
                    }
                    @Override
                    public List getValue() {
                        return this.thing;
                    }
                };
                List values = new ArrayList();
                ListMetadata  lm = ListMetadata.parseFrom(ms.getValue());


                ms.getValue()
                mwl.set(ListMetadata.parseFrom(ms.getValue()).getValue());
                return mwl;*/
        }

        // Or throw exception?
        throw new InvalidProtocolBufferException("Unrecognized metadata type:" + type.toString());
    }





}
