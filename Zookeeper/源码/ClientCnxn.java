package org.apache.zookeeper;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.Thread.UncaughtExceptionHandler;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.LinkedBlockingQueue;
import javax.security.auth.login.LoginException;
import javax.security.sasl.SaslException;
import org.apache.jute.BinaryInputArchive;
import org.apache.jute.BinaryOutputArchive;
import org.apache.jute.Record;
import org.apache.zookeeper.AsyncCallback.ACLCallback;
import org.apache.zookeeper.AsyncCallback.Children2Callback;
import org.apache.zookeeper.AsyncCallback.ChildrenCallback;
import org.apache.zookeeper.AsyncCallback.DataCallback;
import org.apache.zookeeper.AsyncCallback.StatCallback;
import org.apache.zookeeper.AsyncCallback.StringCallback;
import org.apache.zookeeper.AsyncCallback.VoidCallback;
import org.apache.zookeeper.KeeperException.Code;
import org.apache.zookeeper.Watcher.Event.EventType;
import org.apache.zookeeper.Watcher.Event.KeeperState;
import org.apache.zookeeper.ZooKeeper.States;
import org.apache.zookeeper.ZooKeeper.WatchRegistration;
import org.apache.zookeeper.client.HostProvider;
import org.apache.zookeeper.client.ZooKeeperSaslClient;
import org.apache.zookeeper.client.ZooKeeperSaslClient.SaslState;
import org.apache.zookeeper.data.Stat;
import org.apache.zookeeper.proto.AuthPacket;
import org.apache.zookeeper.proto.ConnectRequest;
import org.apache.zookeeper.proto.CreateResponse;
import org.apache.zookeeper.proto.ExistsResponse;
import org.apache.zookeeper.proto.GetACLResponse;
import org.apache.zookeeper.proto.GetChildren2Response;
import org.apache.zookeeper.proto.GetChildrenResponse;
import org.apache.zookeeper.proto.GetDataResponse;
import org.apache.zookeeper.proto.GetSASLRequest;
import org.apache.zookeeper.proto.ReplyHeader;
import org.apache.zookeeper.proto.RequestHeader;
import org.apache.zookeeper.proto.SetACLResponse;
import org.apache.zookeeper.proto.SetDataResponse;
import org.apache.zookeeper.proto.SetWatches;
import org.apache.zookeeper.proto.WatcherEvent;
import org.apache.zookeeper.server.ByteBufferInputStream;
import org.apache.zookeeper.server.ZooTrace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientCnxn {
    private static final Logger LOG = LoggerFactory.getLogger(ClientCnxn.class);
    private static final String ZK_SASL_CLIENT_USERNAME = "zookeeper.sasl.client.username";
    private static boolean disableAutoWatchReset = Boolean.getBoolean("zookeeper.disableAutoWatchReset");	// 是否禁用自动监听重置
    private final CopyOnWriteArraySet<ClientCnxn.AuthData> authInfo;		// 认证信息 
    private final LinkedList<ClientCnxn.Packet> pendingQueue;				// 待发送
    private final LinkedList<ClientCnxn.Packet> outgoingQueue;				// 发送后等待响应
    private int connectTimeout;												// 连接超时时间
    private volatile int negotiatedSessionTimeout;							// 协调会话超时时间
    private int readTimeout;												// 读超时时间
    private final int sessionTimeout;										// 会话超时时间
    private final ZooKeeper zooKeeper;
    private final ClientWatchManager watcher;								// 客户端watcher管理器
    private long sessionId;													// 会话id 
    private byte[] sessionPasswd;											// 密码
    private boolean readOnly;												// 是否只读
    final String chrootPath;
    final ClientCnxn.SendThread sendThread;									// 数据线程
    final ClientCnxn.EventThread eventThread;								// 事件线程
    private volatile boolean closing;										// 正在关闭的标识
    private final HostProvider hostProvider;
    volatile boolean seenRwServerBefore;
    public ZooKeeperSaslClient zooKeeperSaslClient;
    private Object eventOfDeath;
    private static final UncaughtExceptionHandler uncaughtExceptionHandler;	// 未捕获异常处理器
    private volatile long lastZxid;
    public static final int packetLen;
    private int xid;
    private volatile States state;				// 状态 

    public long getSessionId() {
        return this.sessionId;
    }

    public byte[] getSessionPasswd() {
        return this.sessionPasswd;
    }

    public int getSessionTimeout() {
        return this.negotiatedSessionTimeout;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        SocketAddress local = this.sendThread.getClientCnxnSocket().getLocalSocketAddress();
        SocketAddress remote = this.sendThread.getClientCnxnSocket().getRemoteSocketAddress();
        sb.append("sessionid:0x").append(Long.toHexString(this.getSessionId())).append(" local:").append(local).append(" remoteserver:").append(remote).append(" lastZxid:").append(this.lastZxid).append(" xid:").append(this.xid).append(" sent:").append(this.sendThread.getClientCnxnSocket().getSentCount()).append(" recv:").append(this.sendThread.getClientCnxnSocket().getRecvCount()).append(" queuedpkts:").append(this.outgoingQueue.size()).append(" pendingresp:").append(this.pendingQueue.size()).append(" queuedevents:").append(this.eventThread.waitingEvents.size());
        return sb.toString();
    }

    public ClientCnxn(String chrootPath, HostProvider hostProvider, int sessionTimeout, ZooKeeper zooKeeper, ClientWatchManager watcher, ClientCnxnSocket clientCnxnSocket, boolean canBeReadOnly) throws IOException {
        this(chrootPath, hostProvider, sessionTimeout, zooKeeper, watcher, clientCnxnSocket, 0L, new byte[16], canBeReadOnly);
    }

    public ClientCnxn(String chrootPath, HostProvider hostProvider, int sessionTimeout, ZooKeeper zooKeeper, ClientWatchManager watcher, ClientCnxnSocket clientCnxnSocket, long sessionId, byte[] sessionPasswd, boolean canBeReadOnly) {
        this.authInfo = new CopyOnWriteArraySet();
        this.pendingQueue = new LinkedList();
        this.outgoingQueue = new LinkedList();
        this.sessionPasswd = new byte[16];
        this.closing = false;
        this.seenRwServerBefore = false;
        this.eventOfDeath = new Object();
        this.xid = 1;
        this.state = States.NOT_CONNECTED;
        this.zooKeeper = zooKeeper;
        this.watcher = watcher;
        this.sessionId = sessionId;
        this.sessionPasswd = sessionPasswd;
        this.sessionTimeout = sessionTimeout;
        this.hostProvider = hostProvider;
        this.chrootPath = chrootPath;
        this.connectTimeout = sessionTimeout / hostProvider.size();
        this.readTimeout = sessionTimeout * 2 / 3;
        this.readOnly = canBeReadOnly;
        this.sendThread = new ClientCnxn.SendThread(clientCnxnSocket);
        this.eventThread = new ClientCnxn.EventThread();
    }

    public static boolean getDisableAutoResetWatch() {
        return disableAutoWatchReset;
    }

    public static void setDisableAutoResetWatch(boolean b) {
        disableAutoWatchReset = b;
    }

    public void start() {
        this.sendThread.start();
        this.eventThread.start();
    }

    private static String makeThreadName(String suffix) {
        String name = Thread.currentThread().getName().replaceAll("-EventThread", "");
        return name + suffix;
    }

    private void finishPacket(ClientCnxn.Packet p) {
        if (p.watchRegistration != null) {
            p.watchRegistration.register(p.replyHeader.getErr());
        }

        if (p.cb == null) {
            synchronized(p) {
                p.finished = true;
                p.notifyAll();
            }
        } else {
            p.finished = true;
            this.eventThread.queuePacket(p);
        }

    }

    private void conLossPacket(ClientCnxn.Packet p) {
        if (p.replyHeader != null) {
            switch(this.state) {
            case AUTH_FAILED:
                p.replyHeader.setErr(Code.AUTHFAILED.intValue());
                break;
            case CLOSED:
                p.replyHeader.setErr(Code.SESSIONEXPIRED.intValue());
                break;
            default:
                p.replyHeader.setErr(Code.CONNECTIONLOSS.intValue());
            }

            this.finishPacket(p);
        }
    }

    public long getLastZxid() {
        return this.lastZxid;
    }

    public void disconnect() {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Disconnecting client for session: 0x" + Long.toHexString(this.getSessionId()));
        }

        this.sendThread.close();
        this.eventThread.queueEventOfDeath();
    }

    public void close() throws IOException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Closing client for session: 0x" + Long.toHexString(this.getSessionId()));
        }

        try {
            RequestHeader h = new RequestHeader();
            h.setType(-11);
            this.submitRequest(h, (Record)null, (Record)null, (WatchRegistration)null);
        } catch (InterruptedException var5) {
        } finally {
            this.disconnect();
        }

    }

    public synchronized int getXid() {
        return this.xid++;
    }

    public ReplyHeader submitRequest(RequestHeader h, Record request, Record response, WatchRegistration watchRegistration) throws InterruptedException {
        ReplyHeader r = new ReplyHeader();
        ClientCnxn.Packet packet = this.queuePacket(h, r, request, response, (AsyncCallback)null, (String)null, (String)null, (Object)null, watchRegistration);
        synchronized(packet) {
            while(!packet.finished) {
                packet.wait();
            }

            return r;
        }
    }

    public void enableWrite() {
        this.sendThread.getClientCnxnSocket().enableWrite();
    }

    public void sendPacket(Record request, Record response, AsyncCallback cb, int opCode) throws IOException {
        int xid = this.getXid();
        RequestHeader h = new RequestHeader();
        h.setXid(xid);
        h.setType(opCode);
        ReplyHeader r = new ReplyHeader();
        r.setXid(xid);
        ClientCnxn.Packet p = new ClientCnxn.Packet(h, r, request, response, (WatchRegistration)null, false);
        p.cb = cb;
        this.sendThread.sendPacket(p);
    }

    ClientCnxn.Packet queuePacket(RequestHeader h, ReplyHeader r, Record request, Record response, AsyncCallback cb, String clientPath, String serverPath, Object ctx, WatchRegistration watchRegistration) {
        ClientCnxn.Packet packet = null;
        synchronized(this.outgoingQueue) {
            packet = new ClientCnxn.Packet(h, r, request, response, watchRegistration);
            packet.cb = cb;
            packet.ctx = ctx;
            packet.clientPath = clientPath;
            packet.serverPath = serverPath;
            if (this.state.isAlive() && !this.closing) {
                if (h.getType() == -11) {
                    this.closing = true;
                }

                this.outgoingQueue.add(packet);
            } else {
                this.conLossPacket(packet);
            }
        }

        this.sendThread.getClientCnxnSocket().wakeupCnxn();
        return packet;
    }

    public void addAuthInfo(String scheme, byte[] auth) {
        if (this.state.isAlive()) {
            this.authInfo.add(new ClientCnxn.AuthData(scheme, auth));
            this.queuePacket(new RequestHeader(-4, 100), (ReplyHeader)null, new AuthPacket(0, scheme, auth), (Record)null, (AsyncCallback)null, (String)null, (String)null, (Object)null, (WatchRegistration)null);
        }
    }

    States getState() {
        return this.state;
    }

    static {
        if (LOG.isDebugEnabled()) {
            LOG.debug("zookeeper.disableAutoWatchReset is " + disableAutoWatchReset);
        }

		// 设置未捕获异常处理器
        uncaughtExceptionHandler = new UncaughtExceptionHandler() {
            public void uncaughtException(Thread t, Throwable e) {
                ClientCnxn.LOG.error("from " + t.getName(), e);
            }
        };
		// packet长度
        packetLen = Integer.getInteger("jute.maxbuffer", 4194304);
    }

	// 发送线程
    class SendThread extends Thread {
        private long lastPingSentNs;
        private final ClientCnxnSocket clientCnxnSocket;
        private Random r = new Random(System.nanoTime());
        private boolean isFirstConnect = true;					// 是否第一次连接 默认是
        private InetSocketAddress rwServerAddress = null;		// socket地址
        private static final int minPingRwTimeout = 100;
        private static final int maxPingRwTimeout = 60000;
        private int pingRwTimeout = 100;
        private boolean saslLoginFailed = false;
        private static final String RETRY_CONN_MSG = ", closing socket connection and attempting reconnect";

        void readResponse(ByteBuffer incomingBuffer) throws IOException {
            ByteBufferInputStream bbis = new ByteBufferInputStream(incomingBuffer);
            BinaryInputArchive bbia = BinaryInputArchive.getArchive(bbis);
            ReplyHeader replyHdr = new ReplyHeader();
            replyHdr.deserialize(bbia, "header");
            if (replyHdr.getXid() == -2) {
                if (ClientCnxn.LOG.isDebugEnabled()) {
                    ClientCnxn.LOG.debug("Got ping response for sessionid: 0x" + Long.toHexString(ClientCnxn.this.sessionId) + " after " + (System.nanoTime() - this.lastPingSentNs) / 1000000L + "ms");
                }

            } else if (replyHdr.getXid() == -4) {
                if (replyHdr.getErr() == Code.AUTHFAILED.intValue()) {
                    ClientCnxn.this.state = States.AUTH_FAILED;
                    ClientCnxn.this.eventThread.queueEvent(new WatchedEvent(EventType.None, KeeperState.AuthFailed, (String)null));
                }

                if (ClientCnxn.LOG.isDebugEnabled()) {
                    ClientCnxn.LOG.debug("Got auth sessionid:0x" + Long.toHexString(ClientCnxn.this.sessionId));
                }

            } else if (replyHdr.getXid() == -1) {
                if (ClientCnxn.LOG.isDebugEnabled()) {
                    ClientCnxn.LOG.debug("Got notification sessionid:0x" + Long.toHexString(ClientCnxn.this.sessionId));
                }

                WatcherEvent event = new WatcherEvent();
                event.deserialize(bbia, "response");
                if (ClientCnxn.this.chrootPath != null) {
                    String serverPath = event.getPath();
                    if (serverPath.compareTo(ClientCnxn.this.chrootPath) == 0) {
                        event.setPath("/");
                    } else if (serverPath.length() > ClientCnxn.this.chrootPath.length()) {
                        event.setPath(serverPath.substring(ClientCnxn.this.chrootPath.length()));
                    } else {
                        ClientCnxn.LOG.warn("Got server path " + event.getPath() + " which is too short for chroot path " + ClientCnxn.this.chrootPath);
                    }
                }

                WatchedEvent we = new WatchedEvent(event);
                if (ClientCnxn.LOG.isDebugEnabled()) {
                    ClientCnxn.LOG.debug("Got " + we + " for sessionid 0x" + Long.toHexString(ClientCnxn.this.sessionId));
                }

                ClientCnxn.this.eventThread.queueEvent(we);
            } else if (this.clientTunneledAuthenticationInProgress()) {
                GetSASLRequest request = new GetSASLRequest();
                request.deserialize(bbia, "token");
                ClientCnxn.this.zooKeeperSaslClient.respondToServer(request.getToken(), ClientCnxn.this);
            } else {
                ClientCnxn.Packet packet;
                synchronized(ClientCnxn.this.pendingQueue) {
                    if (ClientCnxn.this.pendingQueue.size() == 0) {
                        throw new IOException("Nothing in the queue, but got " + replyHdr.getXid());
                    }

                    packet = (ClientCnxn.Packet)ClientCnxn.this.pendingQueue.remove();
                }

                try {
                    if (packet.requestHeader.getXid() != replyHdr.getXid()) {
                        packet.replyHeader.setErr(Code.CONNECTIONLOSS.intValue());
                        throw new IOException("Xid out of order. Got Xid " + replyHdr.getXid() + " with err " + replyHdr.getErr() + " expected Xid " + packet.requestHeader.getXid() + " for a packet with details: " + packet);
                    }

                    packet.replyHeader.setXid(replyHdr.getXid());
                    packet.replyHeader.setErr(replyHdr.getErr());
                    packet.replyHeader.setZxid(replyHdr.getZxid());
                    if (replyHdr.getZxid() > 0L) {
                        ClientCnxn.this.lastZxid = replyHdr.getZxid();
                    }

                    if (packet.response != null && replyHdr.getErr() == 0) {
                        packet.response.deserialize(bbia, "response");
                    }

                    if (ClientCnxn.LOG.isDebugEnabled()) {
                        ClientCnxn.LOG.debug("Reading reply sessionid:0x" + Long.toHexString(ClientCnxn.this.sessionId) + ", packet:: " + packet);
                    }
                } finally {
                    ClientCnxn.this.finishPacket(packet);
                }

            }
        }

        SendThread(ClientCnxnSocket clientCnxnSocket) {
            super(ClientCnxn.makeThreadName("-SendThread()"));
            ClientCnxn.this.state = States.CONNECTING;
            this.clientCnxnSocket = clientCnxnSocket;
            this.setUncaughtExceptionHandler(ClientCnxn.uncaughtExceptionHandler);
            this.setDaemon(true);
        }

        States getZkState() {
            return ClientCnxn.this.state;
        }

        ClientCnxnSocket getClientCnxnSocket() {
            return this.clientCnxnSocket;
        }

        void primeConnection() throws IOException {
            ClientCnxn.LOG.info("Socket connection established to " + this.clientCnxnSocket.getRemoteSocketAddress() + ", initiating session");
            this.isFirstConnect = false;
            long sessId = ClientCnxn.this.seenRwServerBefore ? ClientCnxn.this.sessionId : 0L;
            ConnectRequest conReq = new ConnectRequest(0, ClientCnxn.this.lastZxid, ClientCnxn.this.sessionTimeout, sessId, ClientCnxn.this.sessionPasswd);
            synchronized(ClientCnxn.this.outgoingQueue) {
                if (!ClientCnxn.disableAutoWatchReset) {
                    List<String> dataWatches = ClientCnxn.this.zooKeeper.getDataWatches();
                    List<String> existWatches = ClientCnxn.this.zooKeeper.getExistWatches();
                    List<String> childWatches = ClientCnxn.this.zooKeeper.getChildWatches();
                    if (!dataWatches.isEmpty() || !existWatches.isEmpty() || !childWatches.isEmpty()) {
                        SetWatches sw = new SetWatches(ClientCnxn.this.lastZxid, this.prependChroot(dataWatches), this.prependChroot(existWatches), this.prependChroot(childWatches));
                        RequestHeader h = new RequestHeader();
                        h.setType(101);
                        h.setXid(-8);
                        ClientCnxn.Packet packet = new ClientCnxn.Packet(h, new ReplyHeader(), sw, (Record)null, (WatchRegistration)null);
                        ClientCnxn.this.outgoingQueue.addFirst(packet);
                    }
                }

                Iterator i$ = ClientCnxn.this.authInfo.iterator();

                while(true) {
                    if (!i$.hasNext()) {
                        ClientCnxn.this.outgoingQueue.addFirst(new ClientCnxn.Packet((RequestHeader)null, (ReplyHeader)null, conReq, (Record)null, (WatchRegistration)null, ClientCnxn.this.readOnly));
                        break;
                    }

                    ClientCnxn.AuthData id = (ClientCnxn.AuthData)i$.next();
                    ClientCnxn.this.outgoingQueue.addFirst(new ClientCnxn.Packet(new RequestHeader(-4, 100), (ReplyHeader)null, new AuthPacket(0, id.scheme, id.data), (Record)null, (WatchRegistration)null));
                }
            }

            this.clientCnxnSocket.enableReadWriteOnly();
            if (ClientCnxn.LOG.isDebugEnabled()) {
                ClientCnxn.LOG.debug("Session establishment request sent on " + this.clientCnxnSocket.getRemoteSocketAddress());
            }

        }

        private List<String> prependChroot(List<String> paths) {
            if (ClientCnxn.this.chrootPath != null && !paths.isEmpty()) {
                for(int i = 0; i < paths.size(); ++i) {
                    String clientPath = (String)paths.get(i);
                    String serverPath;
                    if (clientPath.length() == 1) {
                        serverPath = ClientCnxn.this.chrootPath;
                    } else {
                        serverPath = ClientCnxn.this.chrootPath + clientPath;
                    }

                    paths.set(i, serverPath);
                }
            }

            return paths;
        }

        private void sendPing() {
            this.lastPingSentNs = System.nanoTime();
            RequestHeader h = new RequestHeader(-2, 11);
            ClientCnxn.this.queuePacket(h, (ReplyHeader)null, (Record)null, (Record)null, (AsyncCallback)null, (String)null, (String)null, (Object)null, (WatchRegistration)null);
        }

        private void startConnect() throws IOException {
            ClientCnxn.this.state = States.CONNECTING;
            InetSocketAddress addr;
            if (this.rwServerAddress != null) {
                addr = this.rwServerAddress;
                this.rwServerAddress = null;
            } else {
                addr = ClientCnxn.this.hostProvider.next(1000L);
            }

            this.setName(this.getName().replaceAll("\\(.*\\)", "(" + addr.getHostName() + ":" + addr.getPort() + ")"));
            if (ZooKeeperSaslClient.isEnabled()) {
                try {
                    String principalUserName = System.getProperty("zookeeper.sasl.client.username", "zookeeper");
                    ClientCnxn.this.zooKeeperSaslClient = new ZooKeeperSaslClient(principalUserName + "/" + addr.getHostName());
                } catch (LoginException var3) {
                    ClientCnxn.LOG.warn("SASL configuration failed: " + var3 + " Will continue connection to Zookeeper server without " + "SASL authentication, if Zookeeper server allows it.");
                    ClientCnxn.this.eventThread.queueEvent(new WatchedEvent(EventType.None, KeeperState.AuthFailed, (String)null));
                    this.saslLoginFailed = true;
                }
            }

            this.logStartConnect(addr);
            this.clientCnxnSocket.connect(addr);
        }

        private void logStartConnect(InetSocketAddress addr) {
            String msg = "Opening socket connection to server " + addr;
            if (ClientCnxn.this.zooKeeperSaslClient != null) {
                msg = msg + ". " + ClientCnxn.this.zooKeeperSaslClient.getConfigStatus();
            }

            ClientCnxn.LOG.info(msg);
        }

        public void run() {
            this.clientCnxnSocket.introduce(this, ClientCnxn.this.sessionId);
            this.clientCnxnSocket.updateNow();
            this.clientCnxnSocket.updateLastSendAndHeard();
            long lastPingRwServer = System.currentTimeMillis();
            boolean var4 = true;

            while(ClientCnxn.this.state.isAlive()) {
                try {
                    if (!this.clientCnxnSocket.isConnected()) {
                        if (!this.isFirstConnect) {
                            try {
                                Thread.sleep((long)this.r.nextInt(1000));
                            } catch (InterruptedException var9) {
                                ClientCnxn.LOG.warn("Unexpected exception", var9);
                            }
                        }

                        if (ClientCnxn.this.closing || !ClientCnxn.this.state.isAlive()) {
                            break;
                        }

                        this.startConnect();
                        this.clientCnxnSocket.updateLastSendAndHeard();
                    }

                    int to;
                    if (ClientCnxn.this.state.isConnected()) {
                        if (ClientCnxn.this.zooKeeperSaslClient != null) {
                            boolean sendAuthEvent = false;
                            if (ClientCnxn.this.zooKeeperSaslClient.getSaslState() == SaslState.INITIAL) {
                                try {
                                    ClientCnxn.this.zooKeeperSaslClient.initialize(ClientCnxn.this);
                                } catch (SaslException var8) {
                                    ClientCnxn.LOG.error("SASL authentication with Zookeeper Quorum member failed: " + var8);
                                    ClientCnxn.this.state = States.AUTH_FAILED;
                                    sendAuthEvent = true;
                                }
                            }

                            KeeperState authState = ClientCnxn.this.zooKeeperSaslClient.getKeeperState();
                            if (authState != null) {
                                if (authState == KeeperState.AuthFailed) {
                                    ClientCnxn.this.state = States.AUTH_FAILED;
                                    sendAuthEvent = true;
                                } else if (authState == KeeperState.SaslAuthenticated) {
                                    sendAuthEvent = true;
                                }
                            }

                            if (sendAuthEvent) {
                                ClientCnxn.this.eventThread.queueEvent(new WatchedEvent(EventType.None, authState, (String)null));
                            }
                        }

                        to = ClientCnxn.this.readTimeout - this.clientCnxnSocket.getIdleRecv();
                    } else {
                        to = ClientCnxn.this.connectTimeout - this.clientCnxnSocket.getIdleRecv();
                    }

                    if (to <= 0) {
                        throw new ClientCnxn.SessionTimeoutException("Client session timed out, have not heard from server in " + this.clientCnxnSocket.getIdleRecv() + "ms" + " for sessionid 0x" + Long.toHexString(ClientCnxn.this.sessionId));
                    }

                    if (ClientCnxn.this.state.isConnected()) {
                        int timeToNextPing = ClientCnxn.this.readTimeout / 2 - this.clientCnxnSocket.getIdleSend() - (this.clientCnxnSocket.getIdleSend() > 1000 ? 1000 : 0);
                        if (timeToNextPing > 0 && this.clientCnxnSocket.getIdleSend() <= 10000) {
                            if (timeToNextPing < to) {
                                to = timeToNextPing;
                            }
                        } else {
                            this.sendPing();
                            this.clientCnxnSocket.updateLastSend();
                        }
                    }

                    if (ClientCnxn.this.state == States.CONNECTEDREADONLY) {
                        long now = System.currentTimeMillis();
                        int idlePingRwServer = (int)(now - lastPingRwServer);
                        if (idlePingRwServer >= this.pingRwTimeout) {
                            lastPingRwServer = now;
                            idlePingRwServer = 0;
                            this.pingRwTimeout = Math.min(2 * this.pingRwTimeout, 60000);
                            this.pingRwServer();
                        }

                        to = Math.min(to, this.pingRwTimeout - idlePingRwServer);
                    }

                    this.clientCnxnSocket.doTransport(to, ClientCnxn.this.pendingQueue, ClientCnxn.this.outgoingQueue, ClientCnxn.this);
                } catch (Throwable var10) {
                    if (ClientCnxn.this.closing) {
                        if (ClientCnxn.LOG.isDebugEnabled()) {
                            ClientCnxn.LOG.debug("An exception was thrown while closing send thread for session 0x" + Long.toHexString(ClientCnxn.this.getSessionId()) + " : " + var10.getMessage());
                        }
                        break;
                    }

                    if (var10 instanceof ClientCnxn.SessionExpiredException) {
                        ClientCnxn.LOG.info(var10.getMessage() + ", closing socket connection");
                    } else if (var10 instanceof ClientCnxn.SessionTimeoutException) {
                        ClientCnxn.LOG.info(var10.getMessage() + ", closing socket connection and attempting reconnect");
                    } else if (var10 instanceof ClientCnxn.EndOfStreamException) {
                        ClientCnxn.LOG.info(var10.getMessage() + ", closing socket connection and attempting reconnect");
                    } else if (var10 instanceof ClientCnxn.RWServerFoundException) {
                        ClientCnxn.LOG.info(var10.getMessage());
                    } else {
                        ClientCnxn.LOG.warn("Session 0x" + Long.toHexString(ClientCnxn.this.getSessionId()) + " for server " + this.clientCnxnSocket.getRemoteSocketAddress() + ", unexpected error" + ", closing socket connection and attempting reconnect", var10);
                    }

                    this.cleanup();
                    if (ClientCnxn.this.state.isAlive()) {
                        ClientCnxn.this.eventThread.queueEvent(new WatchedEvent(EventType.None, KeeperState.Disconnected, (String)null));
                    }

                    this.clientCnxnSocket.updateNow();
                    this.clientCnxnSocket.updateLastSendAndHeard();
                }
            }

            this.cleanup();
            this.clientCnxnSocket.close();
            if (ClientCnxn.this.state.isAlive()) {
                ClientCnxn.this.eventThread.queueEvent(new WatchedEvent(EventType.None, KeeperState.Disconnected, (String)null));
            }

            ZooTrace.logTraceMessage(ClientCnxn.LOG, ZooTrace.getTextTraceLevel(), "SendThread exitedloop.");
        }

        private void pingRwServer() throws ClientCnxn.RWServerFoundException {
            String result = null;
            InetSocketAddress addr = ClientCnxn.this.hostProvider.next(0L);
            ClientCnxn.LOG.info("Checking server " + addr + " for being r/w." + " Timeout " + this.pingRwTimeout);
            Socket sock = null;
            BufferedReader br = null;

            try {
                sock = new Socket(addr.getHostName(), addr.getPort());
                sock.setSoLinger(false, -1);
                sock.setSoTimeout(1000);
                sock.setTcpNoDelay(true);
                sock.getOutputStream().write("isro".getBytes());
                sock.getOutputStream().flush();
                sock.shutdownOutput();
                br = new BufferedReader(new InputStreamReader(sock.getInputStream()));
                result = br.readLine();
            } catch (ConnectException var21) {
            } catch (IOException var22) {
                ClientCnxn.LOG.warn("Exception while seeking for r/w server " + var22.getMessage(), var22);
            } finally {
                if (sock != null) {
                    try {
                        sock.close();
                    } catch (IOException var20) {
                        ClientCnxn.LOG.warn("Unexpected exception", var20);
                    }
                }

                if (br != null) {
                    try {
                        br.close();
                    } catch (IOException var19) {
                        ClientCnxn.LOG.warn("Unexpected exception", var19);
                    }
                }

            }

            if ("rw".equals(result)) {
                this.pingRwTimeout = 100;
                this.rwServerAddress = addr;
                throw new ClientCnxn.RWServerFoundException("Majority server found at " + addr.getHostName() + ":" + addr.getPort());
            }
        }

        private void cleanup() {
            this.clientCnxnSocket.cleanup();
            Iterator i$;
            ClientCnxn.Packet p;
            synchronized(ClientCnxn.this.pendingQueue) {
                i$ = ClientCnxn.this.pendingQueue.iterator();

                while(true) {
                    if (!i$.hasNext()) {
                        ClientCnxn.this.pendingQueue.clear();
                        break;
                    }

                    p = (ClientCnxn.Packet)i$.next();
                    ClientCnxn.this.conLossPacket(p);
                }
            }

            synchronized(ClientCnxn.this.outgoingQueue) {
                i$ = ClientCnxn.this.outgoingQueue.iterator();

                while(i$.hasNext()) {
                    p = (ClientCnxn.Packet)i$.next();
                    ClientCnxn.this.conLossPacket(p);
                }

                ClientCnxn.this.outgoingQueue.clear();
            }
        }

        void onConnected(int _negotiatedSessionTimeout, long _sessionId, byte[] _sessionPasswd, boolean isRO) throws IOException {
            ClientCnxn.this.negotiatedSessionTimeout = _negotiatedSessionTimeout;
            if (ClientCnxn.this.negotiatedSessionTimeout <= 0) {
                ClientCnxn.this.state = States.CLOSED;
                ClientCnxn.this.eventThread.queueEvent(new WatchedEvent(EventType.None, KeeperState.Expired, (String)null));
                ClientCnxn.this.eventThread.queueEventOfDeath();
                throw new ClientCnxn.SessionExpiredException("Unable to reconnect to ZooKeeper service, session 0x" + Long.toHexString(ClientCnxn.this.sessionId) + " has expired");
            } else {
                if (!ClientCnxn.this.readOnly && isRO) {
                    ClientCnxn.LOG.error("Read/write client got connected to read-only server");
                }

                ClientCnxn.this.readTimeout = ClientCnxn.this.negotiatedSessionTimeout * 2 / 3;
                ClientCnxn.this.connectTimeout = ClientCnxn.this.negotiatedSessionTimeout / ClientCnxn.this.hostProvider.size();
                ClientCnxn.this.hostProvider.onConnected();
                ClientCnxn.this.sessionId = _sessionId;
                ClientCnxn.this.sessionPasswd = _sessionPasswd;
                ClientCnxn.this.state = isRO ? States.CONNECTEDREADONLY : States.CONNECTED;
                ClientCnxn var10000 = ClientCnxn.this;
                var10000.seenRwServerBefore |= !isRO;
                ClientCnxn.LOG.info("Session establishment complete on server " + this.clientCnxnSocket.getRemoteSocketAddress() + ", sessionid = 0x" + Long.toHexString(ClientCnxn.this.sessionId) + ", negotiated timeout = " + ClientCnxn.this.negotiatedSessionTimeout + (isRO ? " (READ-ONLY mode)" : ""));
                KeeperState eventState = isRO ? KeeperState.ConnectedReadOnly : KeeperState.SyncConnected;
                ClientCnxn.this.eventThread.queueEvent(new WatchedEvent(EventType.None, eventState, (String)null));
            }
        }

        void close() {
            ClientCnxn.this.state = States.CLOSED;
            this.clientCnxnSocket.wakeupCnxn();
        }

        void testableCloseSocket() throws IOException {
            this.clientCnxnSocket.testableCloseSocket();
        }

        public boolean clientTunneledAuthenticationInProgress() {
            if (!ZooKeeperSaslClient.isEnabled()) {
                return false;
            } else if (this.saslLoginFailed) {
                return false;
            } else {
                return ClientCnxn.this.zooKeeperSaslClient == null ? true : ClientCnxn.this.zooKeeperSaslClient.clientTunneledAuthenticationInProgress();
            }
        }

        public void sendPacket(ClientCnxn.Packet p) throws IOException {
            this.clientCnxnSocket.sendPacket(p);
        }
    }

    private static class RWServerFoundException extends IOException {
        private static final long serialVersionUID = 90431199887158758L;

        public RWServerFoundException(String msg) {
            super(msg);
        }
    }

    private static class SessionExpiredException extends IOException {
        private static final long serialVersionUID = -1388816932076193249L;

        public SessionExpiredException(String msg) {
            super(msg);
        }
    }

    private static class SessionTimeoutException extends IOException {
        private static final long serialVersionUID = 824482094072071178L;

        public SessionTimeoutException(String msg) {
            super(msg);
        }
    }

    static class EndOfStreamException extends IOException {
        private static final long serialVersionUID = -5438877188796231422L;

        public EndOfStreamException(String msg) {
            super(msg);
        }

        public String toString() {
            return "EndOfStreamException: " + this.getMessage();
        }
    }

    class EventThread extends Thread {
        private final LinkedBlockingQueue<Object> waitingEvents = new LinkedBlockingQueue();
        private volatile KeeperState sessionState;
        private volatile boolean wasKilled;
        private volatile boolean isRunning;

        EventThread() {
            super(ClientCnxn.makeThreadName("-EventThread"));
            this.sessionState = KeeperState.Disconnected;
            this.wasKilled = false;
            this.isRunning = false;
            this.setUncaughtExceptionHandler(ClientCnxn.uncaughtExceptionHandler);
            this.setDaemon(true);
        }

        public void queueEvent(WatchedEvent event) {
            if (event.getType() != EventType.None || this.sessionState != event.getState()) {
                this.sessionState = event.getState();
                ClientCnxn.WatcherSetEventPair pair = new ClientCnxn.WatcherSetEventPair(ClientCnxn.this.watcher.materialize(event.getState(), event.getType(), event.getPath()), event);
                this.waitingEvents.add(pair);
            }
        }

        public void queuePacket(ClientCnxn.Packet packet) {
            if (this.wasKilled) {
                synchronized(this.waitingEvents) {
                    if (this.isRunning) {
                        this.waitingEvents.add(packet);
                    } else {
                        this.processEvent(packet);
                    }
                }
            } else {
                this.waitingEvents.add(packet);
            }

        }

        public void queueEventOfDeath() {
            this.waitingEvents.add(ClientCnxn.this.eventOfDeath);
        }

        public void run() {
            try {
                this.isRunning = true;

                while(true) {
                    Object event = this.waitingEvents.take();
                    if (event == ClientCnxn.this.eventOfDeath) {
                        this.wasKilled = true;
                    } else {
                        this.processEvent(event);
                    }

                    if (this.wasKilled) {
                        synchronized(this.waitingEvents) {
                            if (this.waitingEvents.isEmpty()) {
                                this.isRunning = false;
                                break;
                            }
                        }
                    }
                }
            } catch (InterruptedException var5) {
                ClientCnxn.LOG.error("Event thread exiting due to interruption", var5);
            }

            ClientCnxn.LOG.info("EventThread shut down");
        }

        private void processEvent(Object event) {
            try {
                if (event instanceof ClientCnxn.WatcherSetEventPair) {
                    ClientCnxn.WatcherSetEventPair pair = (ClientCnxn.WatcherSetEventPair)event;
                    Iterator i$ = pair.watchers.iterator();

                    while(i$.hasNext()) {
                        Watcher watcher = (Watcher)i$.next();

                        try {
                            watcher.process(pair.event);
                        } catch (Throwable var7) {
                            ClientCnxn.LOG.error("Error while calling watcher ", var7);
                        }
                    }
                } else {
                    ClientCnxn.Packet p = (ClientCnxn.Packet)event;
                    int rc = 0;
                    String clientPath = p.clientPath;
                    if (p.replyHeader.getErr() != 0) {
                        rc = p.replyHeader.getErr();
                    }

                    if (p.cb == null) {
                        ClientCnxn.LOG.warn("Somehow a null cb got to EventThread!");
                    } else if (!(p.response instanceof ExistsResponse) && !(p.response instanceof SetDataResponse) && !(p.response instanceof SetACLResponse)) {
                        if (p.response instanceof GetDataResponse) {
                            DataCallback cb = (DataCallback)p.cb;
                            GetDataResponse rspx = (GetDataResponse)p.response;
                            if (rc == 0) {
                                cb.processResult(rc, clientPath, p.ctx, rspx.getData(), rspx.getStat());
                            } else {
                                cb.processResult(rc, clientPath, p.ctx, (byte[])null, (Stat)null);
                            }
                        } else if (p.response instanceof GetACLResponse) {
                            ACLCallback cbx = (ACLCallback)p.cb;
                            GetACLResponse rsp = (GetACLResponse)p.response;
                            if (rc == 0) {
                                cbx.processResult(rc, clientPath, p.ctx, rsp.getAcl(), rsp.getStat());
                            } else {
                                cbx.processResult(rc, clientPath, p.ctx, (List)null, (Stat)null);
                            }
                        } else if (p.response instanceof GetChildrenResponse) {
                            ChildrenCallback cbxx = (ChildrenCallback)p.cb;
                            GetChildrenResponse rspxx = (GetChildrenResponse)p.response;
                            if (rc == 0) {
                                cbxx.processResult(rc, clientPath, p.ctx, rspxx.getChildren());
                            } else {
                                cbxx.processResult(rc, clientPath, p.ctx, (List)null);
                            }
                        } else if (p.response instanceof GetChildren2Response) {
                            Children2Callback cbxxxx = (Children2Callback)p.cb;
                            GetChildren2Response rspxxx = (GetChildren2Response)p.response;
                            if (rc == 0) {
                                cbxxxx.processResult(rc, clientPath, p.ctx, rspxxx.getChildren(), rspxxx.getStat());
                            } else {
                                cbxxxx.processResult(rc, clientPath, p.ctx, (List)null, (Stat)null);
                            }
                        } else if (p.response instanceof CreateResponse) {
                            StringCallback cbxxxxx = (StringCallback)p.cb;
                            CreateResponse rspxxxx = (CreateResponse)p.response;
                            if (rc == 0) {
                                cbxxxxx.processResult(rc, clientPath, p.ctx, ClientCnxn.this.chrootPath == null ? rspxxxx.getPath() : rspxxxx.getPath().substring(ClientCnxn.this.chrootPath.length()));
                            } else {
                                cbxxxxx.processResult(rc, clientPath, p.ctx, (String)null);
                            }
                        } else if (p.cb instanceof VoidCallback) {
                            VoidCallback cbxxxxxx = (VoidCallback)p.cb;
                            cbxxxxxx.processResult(rc, clientPath, p.ctx);
                        }
                    } else {
                        StatCallback cbxxx = (StatCallback)p.cb;
                        if (rc == 0) {
                            if (p.response instanceof ExistsResponse) {
                                cbxxx.processResult(rc, clientPath, p.ctx, ((ExistsResponse)p.response).getStat());
                            } else if (p.response instanceof SetDataResponse) {
                                cbxxx.processResult(rc, clientPath, p.ctx, ((SetDataResponse)p.response).getStat());
                            } else if (p.response instanceof SetACLResponse) {
                                cbxxx.processResult(rc, clientPath, p.ctx, ((SetACLResponse)p.response).getStat());
                            }
                        } else {
                            cbxxx.processResult(rc, clientPath, p.ctx, (Stat)null);
                        }
                    }
                }
            } catch (Throwable var8) {
                ClientCnxn.LOG.error("Caught unexpected throwable", var8);
            }

        }
    }

    private static class WatcherSetEventPair {
        private final Set<Watcher> watchers;
        private final WatchedEvent event;

        public WatcherSetEventPair(Set<Watcher> watchers, WatchedEvent event) {
            this.watchers = watchers;
            this.event = event;
        }
    }

    static class Packet {
        RequestHeader requestHeader;
        ReplyHeader replyHeader;
        Record request;
        Record response;
        ByteBuffer bb;
        String clientPath;
        String serverPath;
        boolean finished;
        AsyncCallback cb;
        Object ctx;
        WatchRegistration watchRegistration;
        public boolean readOnly;

        Packet(RequestHeader requestHeader, ReplyHeader replyHeader, Record request, Record response, WatchRegistration watchRegistration) {
            this(requestHeader, replyHeader, request, response, watchRegistration, false);
        }

        Packet(RequestHeader requestHeader, ReplyHeader replyHeader, Record request, Record response, WatchRegistration watchRegistration, boolean readOnly) {
            this.requestHeader = requestHeader;
            this.replyHeader = replyHeader;
            this.request = request;
            this.response = response;
            this.readOnly = readOnly;
            this.watchRegistration = watchRegistration;
        }

        public void createBB() {
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                BinaryOutputArchive boa = BinaryOutputArchive.getArchive(baos);
                boa.writeInt(-1, "len");
                if (this.requestHeader != null) {
                    this.requestHeader.serialize(boa, "header");
                }

                if (this.request instanceof ConnectRequest) {
                    this.request.serialize(boa, "connect");
                    boa.writeBool(this.readOnly, "readOnly");
                } else if (this.request != null) {
                    this.request.serialize(boa, "request");
                }

                baos.close();
                this.bb = ByteBuffer.wrap(baos.toByteArray());
                this.bb.putInt(this.bb.capacity() - 4);
                this.bb.rewind();
            } catch (IOException var3) {
                ClientCnxn.LOG.warn("Ignoring unexpected exception", var3);
            }

        }

        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("clientPath:" + this.clientPath);
            sb.append(" serverPath:" + this.serverPath);
            sb.append(" finished:" + this.finished);
            sb.append(" header:: " + this.requestHeader);
            sb.append(" replyHeader:: " + this.replyHeader);
            sb.append(" request:: " + this.request);
            sb.append(" response:: " + this.response);
            return sb.toString().replaceAll("\r*\n+", " ");
        }
    }

    static class AuthData {
        String scheme;
        byte[] data;

        AuthData(String scheme, byte[] data) {
            this.scheme = scheme;
            this.data = data;
        }
    }
}
