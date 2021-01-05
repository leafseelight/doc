package org.apache.zookeeper;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.jute.Record;
import org.apache.zookeeper.AsyncCallback.ACLCallback;
import org.apache.zookeeper.AsyncCallback.Children2Callback;
import org.apache.zookeeper.AsyncCallback.ChildrenCallback;
import org.apache.zookeeper.AsyncCallback.DataCallback;
import org.apache.zookeeper.AsyncCallback.StatCallback;
import org.apache.zookeeper.AsyncCallback.StringCallback;
import org.apache.zookeeper.AsyncCallback.VoidCallback;
import org.apache.zookeeper.KeeperException.Code;
import org.apache.zookeeper.KeeperException.InvalidACLException;
import org.apache.zookeeper.OpResult.ErrorResult;
import org.apache.zookeeper.Watcher.Event.EventType;
import org.apache.zookeeper.Watcher.Event.KeeperState;
import org.apache.zookeeper.client.ConnectStringParser;
import org.apache.zookeeper.client.HostProvider;
import org.apache.zookeeper.client.StaticHostProvider;
import org.apache.zookeeper.client.ZooKeeperSaslClient;
import org.apache.zookeeper.common.PathUtils;
import org.apache.zookeeper.data.ACL;
import org.apache.zookeeper.data.Stat;
import org.apache.zookeeper.proto.CreateRequest;
import org.apache.zookeeper.proto.CreateResponse;
import org.apache.zookeeper.proto.DeleteRequest;
import org.apache.zookeeper.proto.ExistsRequest;
import org.apache.zookeeper.proto.GetACLRequest;
import org.apache.zookeeper.proto.GetACLResponse;
import org.apache.zookeeper.proto.GetChildren2Request;
import org.apache.zookeeper.proto.GetChildren2Response;
import org.apache.zookeeper.proto.GetChildrenRequest;
import org.apache.zookeeper.proto.GetChildrenResponse;
import org.apache.zookeeper.proto.GetDataRequest;
import org.apache.zookeeper.proto.GetDataResponse;
import org.apache.zookeeper.proto.ReplyHeader;
import org.apache.zookeeper.proto.RequestHeader;
import org.apache.zookeeper.proto.SetACLRequest;
import org.apache.zookeeper.proto.SetACLResponse;
import org.apache.zookeeper.proto.SetDataRequest;
import org.apache.zookeeper.proto.SetDataResponse;
import org.apache.zookeeper.proto.SyncRequest;
import org.apache.zookeeper.proto.SyncResponse;
import org.apache.zookeeper.server.DataTree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZooKeeper {
    public static final String ZOOKEEPER_CLIENT_CNXN_SOCKET = "zookeeper.clientCnxnSocket";
    protected final ClientCnxn cnxn;
    private static final Logger LOG = LoggerFactory.getLogger(ZooKeeper.class);
    private final ZooKeeper.ZKWatchManager watchManager;

    public ZooKeeperSaslClient getSaslClient() {
        return this.cnxn.zooKeeperSaslClient;
    }

    List<String> getDataWatches() {
        synchronized(this.watchManager.dataWatches) {
            List<String> rc = new ArrayList(this.watchManager.dataWatches.keySet());
            return rc;
        }
    }

    List<String> getExistWatches() {
        synchronized(this.watchManager.existWatches) {
            List<String> rc = new ArrayList(this.watchManager.existWatches.keySet());
            return rc;
        }
    }

    List<String> getChildWatches() {
        synchronized(this.watchManager.childWatches) {
            List<String> rc = new ArrayList(this.watchManager.childWatches.keySet());
            return rc;
        }
    }

    public ZooKeeper(String connectString, int sessionTimeout, Watcher watcher) throws IOException {
        this(connectString, sessionTimeout, watcher, false);
    }

    public ZooKeeper(String connectString, int sessionTimeout, Watcher watcher, boolean canBeReadOnly) throws IOException {
        this.watchManager = new ZooKeeper.ZKWatchManager();	// 初始化watch管理器
        LOG.info("Initiating client connection, connectString=" + connectString + " sessionTimeout=" + sessionTimeout + " watcher=" + watcher);
        this.watchManager.defaultWatcher = watcher;
        ConnectStringParser connectStringParser = new ConnectStringParser(connectString);              // 连接字符串解析器
        HostProvider hostProvider = new StaticHostProvider(connectStringParser.getServerAddresses());
        this.cnxn = new ClientCnxn(connectStringParser.getChrootPath(), hostProvider, sessionTimeout, this, this.watchManager, getClientCnxnSocket(), canBeReadOnly);
        this.cnxn.start();
    }

    public ZooKeeper(String connectString, int sessionTimeout, Watcher watcher, long sessionId, byte[] sessionPasswd) throws IOException {
        this(connectString, sessionTimeout, watcher, sessionId, sessionPasswd, false);
    }

    public ZooKeeper(String connectString, int sessionTimeout, Watcher watcher, long sessionId, byte[] sessionPasswd, boolean canBeReadOnly) throws IOException {
        this.watchManager = new ZooKeeper.ZKWatchManager();
        LOG.info("Initiating client connection, connectString=" + connectString + " sessionTimeout=" + sessionTimeout + " watcher=" + watcher + " sessionId=" + Long.toHexString(sessionId) + " sessionPasswd=" + (sessionPasswd == null ? "<null>" : "<hidden>"));
        this.watchManager.defaultWatcher = watcher;
        ConnectStringParser connectStringParser = new ConnectStringParser(connectString);
        HostProvider hostProvider = new StaticHostProvider(connectStringParser.getServerAddresses());
        this.cnxn = new ClientCnxn(connectStringParser.getChrootPath(), hostProvider, sessionTimeout, this, this.watchManager, getClientCnxnSocket(), sessionId, sessionPasswd, canBeReadOnly);
        this.cnxn.seenRwServerBefore = true;
        this.cnxn.start();
    }

    public long getSessionId() {
        return this.cnxn.getSessionId();
    }

    public byte[] getSessionPasswd() {
        return this.cnxn.getSessionPasswd();
    }

    public int getSessionTimeout() {
        return this.cnxn.getSessionTimeout();
    }

    public void addAuthInfo(String scheme, byte[] auth) {
        this.cnxn.addAuthInfo(scheme, auth);
    }

	// 注册默认的监听器
    public synchronized void register(Watcher watcher) {
        this.watchManager.defaultWatcher = watcher;
    }

	// 关闭连接
    public synchronized void close() throws InterruptedException {
        if (!this.cnxn.getState().isAlive()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Close called on already closed client");
            }

        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Closing session: 0x" + Long.toHexString(this.getSessionId()));
            }

            try {
                this.cnxn.close();
            } catch (IOException var2) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Ignoring unexpected exception during close", var2);
                }
            }

            LOG.info("Session: 0x" + Long.toHexString(this.getSessionId()) + " closed");
        }
    }

    private String prependChroot(String clientPath) {
        if (this.cnxn.chrootPath != null) {
            return clientPath.length() == 1 ? this.cnxn.chrootPath : this.cnxn.chrootPath + clientPath;
        } else {
            return clientPath;
        }
    }

	// 创建节点
	// CreateMode创建模式：PERSISTENT、PERSISTENT_SEQUENTIAL、EPHEMERAL、EPHEMERAL_SEQUENTIAL
    public String create(String path, byte[] data, List<ACL> acl, CreateMode createMode) throws KeeperException, InterruptedException {
        PathUtils.validatePath(path, createMode.isSequential());
        String serverPath = this.prependChroot(path);
        RequestHeader h = new RequestHeader();
        h.setType(1);
        CreateRequest request = new CreateRequest();
        CreateResponse response = new CreateResponse();
        request.setData(data);
        request.setFlags(createMode.toFlag());
        request.setPath(serverPath);
        if (acl != null && acl.size() == 0) {
            throw new InvalidACLException();
        } else {
            request.setAcl(acl);
            ReplyHeader r = this.cnxn.submitRequest(h, request, response, (ZooKeeper.WatchRegistration)null);
            if (r.getErr() != 0) {
                throw KeeperException.create(Code.get(r.getErr()), path);
            } else {
                return this.cnxn.chrootPath == null ? response.getPath() : response.getPath().substring(this.cnxn.chrootPath.length());
            }
        }
    }

    public void create(String path, byte[] data, List<ACL> acl, CreateMode createMode, StringCallback cb, Object ctx) {
        PathUtils.validatePath(path, createMode.isSequential());
        String serverPath = this.prependChroot(path);
        RequestHeader h = new RequestHeader();
        h.setType(1);
        CreateRequest request = new CreateRequest();
        CreateResponse response = new CreateResponse();
        ReplyHeader r = new ReplyHeader();
        request.setData(data);
        request.setFlags(createMode.toFlag());
        request.setPath(serverPath);
        request.setAcl(acl);
        this.cnxn.queuePacket(h, r, request, response, cb, path, serverPath, ctx, (ZooKeeper.WatchRegistration)null);
    }

	// 删除节点
    public void delete(String path, int version) throws InterruptedException, KeeperException {
        PathUtils.validatePath(path);
        String serverPath;
        if (path.equals("/")) {
            serverPath = path;
        } else {
            serverPath = this.prependChroot(path);
        }

        RequestHeader h = new RequestHeader();
        h.setType(2);
        DeleteRequest request = new DeleteRequest();
        request.setPath(serverPath);
        request.setVersion(version);
        ReplyHeader r = this.cnxn.submitRequest(h, request, (Record)null, (ZooKeeper.WatchRegistration)null);
        if (r.getErr() != 0) {
            throw KeeperException.create(Code.get(r.getErr()), path);
        }
    }

    public List<OpResult> multi(Iterable<Op> ops) throws InterruptedException, KeeperException {
        Iterator i$ = ops.iterator();

        while(i$.hasNext()) {
            Op op = (Op)i$.next();
            op.validate();
        }

        List<Op> transaction = new ArrayList();
        Iterator i$ = ops.iterator();

        while(i$.hasNext()) {
            Op op = (Op)i$.next();
            transaction.add(this.withRootPrefix(op));
        }

        return this.multiInternal(new MultiTransactionRecord(transaction));
    }

    private Op withRootPrefix(Op op) {
        if (null != op.getPath()) {
            String serverPath = this.prependChroot(op.getPath());
            if (!op.getPath().equals(serverPath)) {
                return op.withChroot(serverPath);
            }
        }

        return op;
    }

    protected List<OpResult> multiInternal(MultiTransactionRecord request) throws InterruptedException, KeeperException {
        RequestHeader h = new RequestHeader();
        h.setType(14);
        MultiResponse response = new MultiResponse();
        ReplyHeader r = this.cnxn.submitRequest(h, request, response, (ZooKeeper.WatchRegistration)null);
        if (r.getErr() != 0) {
            throw KeeperException.create(Code.get(r.getErr()));
        } else {
            List<OpResult> results = response.getResultList();
            ErrorResult fatalError = null;
            Iterator i$ = results.iterator();

            while(i$.hasNext()) {
                OpResult result = (OpResult)i$.next();
                if (result instanceof ErrorResult && ((ErrorResult)result).getErr() != Code.OK.intValue()) {
                    fatalError = (ErrorResult)result;
                    break;
                }
            }

            if (fatalError != null) {
                KeeperException ex = KeeperException.create(Code.get(fatalError.getErr()));
                ex.setMultiResults(results);
                throw ex;
            } else {
                return results;
            }
        }
    }

    public Transaction transaction() {
        return new Transaction(this);
    }

    public void delete(String path, int version, VoidCallback cb, Object ctx) {
        PathUtils.validatePath(path);
        String serverPath;
        if (path.equals("/")) {
            serverPath = path;
        } else {
            serverPath = this.prependChroot(path);
        }

        RequestHeader h = new RequestHeader();
        h.setType(2);
        DeleteRequest request = new DeleteRequest();
        request.setPath(serverPath);
        request.setVersion(version);
        this.cnxn.queuePacket(h, new ReplyHeader(), request, (Record)null, cb, path, serverPath, ctx, (ZooKeeper.WatchRegistration)null);
    }

	// 判断节点是否存在
    public Stat exists(String path, Watcher watcher) throws KeeperException, InterruptedException {
        PathUtils.validatePath(path);
        ZooKeeper.WatchRegistration wcb = null;
        if (watcher != null) {
            wcb = new ZooKeeper.ExistsWatchRegistration(watcher, path);
        }

        String serverPath = this.prependChroot(path);
        RequestHeader h = new RequestHeader();
        h.setType(3);
        ExistsRequest request = new ExistsRequest();
        request.setPath(serverPath);
        request.setWatch(watcher != null);
        SetDataResponse response = new SetDataResponse();
        ReplyHeader r = this.cnxn.submitRequest(h, request, response, wcb);
        if (r.getErr() != 0) {
            if (r.getErr() == Code.NONODE.intValue()) {
                return null;
            } else {
                throw KeeperException.create(Code.get(r.getErr()), path);
            }
        } else {
            return response.getStat().getCzxid() == -1L ? null : response.getStat();
        }
    }

    public Stat exists(String path, boolean watch) throws KeeperException, InterruptedException {
        return this.exists(path, watch ? this.watchManager.defaultWatcher : null);
    }

    public void exists(String path, Watcher watcher, StatCallback cb, Object ctx) {
        PathUtils.validatePath(path);
        ZooKeeper.WatchRegistration wcb = null;
        if (watcher != null) {
            wcb = new ZooKeeper.ExistsWatchRegistration(watcher, path);
        }

        String serverPath = this.prependChroot(path);
        RequestHeader h = new RequestHeader();
        h.setType(3);
        ExistsRequest request = new ExistsRequest();
        request.setPath(serverPath);
        request.setWatch(watcher != null);
        SetDataResponse response = new SetDataResponse();
        this.cnxn.queuePacket(h, new ReplyHeader(), request, response, cb, path, serverPath, ctx, wcb);
    }

    public void exists(String path, boolean watch, StatCallback cb, Object ctx) {
        this.exists(path, watch ? this.watchManager.defaultWatcher : null, cb, ctx);
    }

	// 获取节点数据
    public byte[] getData(String path, Watcher watcher, Stat stat) throws KeeperException, InterruptedException {
        PathUtils.validatePath(path);
        ZooKeeper.WatchRegistration wcb = null;
        if (watcher != null) {
            wcb = new ZooKeeper.DataWatchRegistration(watcher, path);
        }

        String serverPath = this.prependChroot(path);
        RequestHeader h = new RequestHeader();
        h.setType(4);
        GetDataRequest request = new GetDataRequest();
        request.setPath(serverPath);
        request.setWatch(watcher != null);
        GetDataResponse response = new GetDataResponse();
        ReplyHeader r = this.cnxn.submitRequest(h, request, response, wcb);
        if (r.getErr() != 0) {
            throw KeeperException.create(Code.get(r.getErr()), path);
        } else {
            if (stat != null) {
                DataTree.copyStat(response.getStat(), stat);
            }

            return response.getData();
        }
    }

    public byte[] getData(String path, boolean watch, Stat stat) throws KeeperException, InterruptedException {
        return this.getData(path, watch ? this.watchManager.defaultWatcher : null, stat);
    }

    public void getData(String path, Watcher watcher, DataCallback cb, Object ctx) {
        PathUtils.validatePath(path);
        ZooKeeper.WatchRegistration wcb = null;
        if (watcher != null) {
            wcb = new ZooKeeper.DataWatchRegistration(watcher, path);
        }

        String serverPath = this.prependChroot(path);
        RequestHeader h = new RequestHeader();
        h.setType(4);
        GetDataRequest request = new GetDataRequest();
        request.setPath(serverPath);
        request.setWatch(watcher != null);
        GetDataResponse response = new GetDataResponse();
        this.cnxn.queuePacket(h, new ReplyHeader(), request, response, cb, path, serverPath, ctx, wcb);
    }

    public void getData(String path, boolean watch, DataCallback cb, Object ctx) {
        this.getData(path, watch ? this.watchManager.defaultWatcher : null, cb, ctx);
    }

	// 设置节点数据
    public Stat setData(String path, byte[] data, int version) throws KeeperException, InterruptedException {
        PathUtils.validatePath(path);
        String serverPath = this.prependChroot(path);
        RequestHeader h = new RequestHeader();
        h.setType(5);
        SetDataRequest request = new SetDataRequest();
        request.setPath(serverPath);
        request.setData(data);
        request.setVersion(version);
        SetDataResponse response = new SetDataResponse();
        ReplyHeader r = this.cnxn.submitRequest(h, request, response, (ZooKeeper.WatchRegistration)null);
        if (r.getErr() != 0) {
            throw KeeperException.create(Code.get(r.getErr()), path);
        } else {
            return response.getStat();
        }
    }

    public void setData(String path, byte[] data, int version, StatCallback cb, Object ctx) {
        PathUtils.validatePath(path);
        String serverPath = this.prependChroot(path);
        RequestHeader h = new RequestHeader();
        h.setType(5);
        SetDataRequest request = new SetDataRequest();
        request.setPath(serverPath);
        request.setData(data);
        request.setVersion(version);
        SetDataResponse response = new SetDataResponse();
        this.cnxn.queuePacket(h, new ReplyHeader(), request, response, cb, path, serverPath, ctx, (ZooKeeper.WatchRegistration)null);
    }

    public List<ACL> getACL(String path, Stat stat) throws KeeperException, InterruptedException {
        PathUtils.validatePath(path);
        String serverPath = this.prependChroot(path);
        RequestHeader h = new RequestHeader();
        h.setType(6);
        GetACLRequest request = new GetACLRequest();
        request.setPath(serverPath);
        GetACLResponse response = new GetACLResponse();
        ReplyHeader r = this.cnxn.submitRequest(h, request, response, (ZooKeeper.WatchRegistration)null);
        if (r.getErr() != 0) {
            throw KeeperException.create(Code.get(r.getErr()), path);
        } else {
            DataTree.copyStat(response.getStat(), stat);
            return response.getAcl();
        }
    }

    public void getACL(String path, Stat stat, ACLCallback cb, Object ctx) {
        PathUtils.validatePath(path);
        String serverPath = this.prependChroot(path);
        RequestHeader h = new RequestHeader();
        h.setType(6);
        GetACLRequest request = new GetACLRequest();
        request.setPath(serverPath);
        GetACLResponse response = new GetACLResponse();
        this.cnxn.queuePacket(h, new ReplyHeader(), request, response, cb, path, serverPath, ctx, (ZooKeeper.WatchRegistration)null);
    }

    public Stat setACL(String path, List<ACL> acl, int version) throws KeeperException, InterruptedException {
        PathUtils.validatePath(path);
        String serverPath = this.prependChroot(path);
        RequestHeader h = new RequestHeader();
        h.setType(7);
        SetACLRequest request = new SetACLRequest();
        request.setPath(serverPath);
        if (acl != null && acl.size() == 0) {
            throw new InvalidACLException(path);
        } else {
            request.setAcl(acl);
            request.setVersion(version);
            SetACLResponse response = new SetACLResponse();
            ReplyHeader r = this.cnxn.submitRequest(h, request, response, (ZooKeeper.WatchRegistration)null);
            if (r.getErr() != 0) {
                throw KeeperException.create(Code.get(r.getErr()), path);
            } else {
                return response.getStat();
            }
        }
    }

    public void setACL(String path, List<ACL> acl, int version, StatCallback cb, Object ctx) {
        PathUtils.validatePath(path);
        String serverPath = this.prependChroot(path);
        RequestHeader h = new RequestHeader();
        h.setType(7);
        SetACLRequest request = new SetACLRequest();
        request.setPath(serverPath);
        request.setAcl(acl);
        request.setVersion(version);
        SetACLResponse response = new SetACLResponse();
        this.cnxn.queuePacket(h, new ReplyHeader(), request, response, cb, path, serverPath, ctx, (ZooKeeper.WatchRegistration)null);
    }

    public List<String> getChildren(String path, Watcher watcher) throws KeeperException, InterruptedException {
        PathUtils.validatePath(path);
        ZooKeeper.WatchRegistration wcb = null;
        if (watcher != null) {
            wcb = new ZooKeeper.ChildWatchRegistration(watcher, path);
        }

        String serverPath = this.prependChroot(path);
        RequestHeader h = new RequestHeader();
        h.setType(8);
        GetChildrenRequest request = new GetChildrenRequest();
        request.setPath(serverPath);
        request.setWatch(watcher != null);
        GetChildrenResponse response = new GetChildrenResponse();
        ReplyHeader r = this.cnxn.submitRequest(h, request, response, wcb);
        if (r.getErr() != 0) {
            throw KeeperException.create(Code.get(r.getErr()), path);
        } else {
            return response.getChildren();
        }
    }

    public List<String> getChildren(String path, boolean watch) throws KeeperException, InterruptedException {
        return this.getChildren(path, watch ? this.watchManager.defaultWatcher : null);
    }

    public void getChildren(String path, Watcher watcher, ChildrenCallback cb, Object ctx) {
        PathUtils.validatePath(path);
        ZooKeeper.WatchRegistration wcb = null;
        if (watcher != null) {
            wcb = new ZooKeeper.ChildWatchRegistration(watcher, path);
        }

        String serverPath = this.prependChroot(path);
        RequestHeader h = new RequestHeader();
        h.setType(8);
        GetChildrenRequest request = new GetChildrenRequest();
        request.setPath(serverPath);
        request.setWatch(watcher != null);
        GetChildrenResponse response = new GetChildrenResponse();
        this.cnxn.queuePacket(h, new ReplyHeader(), request, response, cb, path, serverPath, ctx, wcb);
    }

    public void getChildren(String path, boolean watch, ChildrenCallback cb, Object ctx) {
        this.getChildren(path, watch ? this.watchManager.defaultWatcher : null, cb, ctx);
    }

    public List<String> getChildren(String path, Watcher watcher, Stat stat) throws KeeperException, InterruptedException {
        PathUtils.validatePath(path);
        ZooKeeper.WatchRegistration wcb = null;
        if (watcher != null) {
            wcb = new ZooKeeper.ChildWatchRegistration(watcher, path);
        }

        String serverPath = this.prependChroot(path);
        RequestHeader h = new RequestHeader();
        h.setType(12);
        GetChildren2Request request = new GetChildren2Request();
        request.setPath(serverPath);
        request.setWatch(watcher != null);
        GetChildren2Response response = new GetChildren2Response();
        ReplyHeader r = this.cnxn.submitRequest(h, request, response, wcb);
        if (r.getErr() != 0) {
            throw KeeperException.create(Code.get(r.getErr()), path);
        } else {
            if (stat != null) {
                DataTree.copyStat(response.getStat(), stat);
            }

            return response.getChildren();
        }
    }

    public List<String> getChildren(String path, boolean watch, Stat stat) throws KeeperException, InterruptedException {
        return this.getChildren(path, watch ? this.watchManager.defaultWatcher : null, stat);
    }

    public void getChildren(String path, Watcher watcher, Children2Callback cb, Object ctx) {
        PathUtils.validatePath(path);
        ZooKeeper.WatchRegistration wcb = null;
        if (watcher != null) {
            wcb = new ZooKeeper.ChildWatchRegistration(watcher, path);
        }

        String serverPath = this.prependChroot(path);
        RequestHeader h = new RequestHeader();
        h.setType(12);
        GetChildren2Request request = new GetChildren2Request();
        request.setPath(serverPath);
        request.setWatch(watcher != null);
        GetChildren2Response response = new GetChildren2Response();
        this.cnxn.queuePacket(h, new ReplyHeader(), request, response, cb, path, serverPath, ctx, wcb);
    }

    public void getChildren(String path, boolean watch, Children2Callback cb, Object ctx) {
        this.getChildren(path, watch ? this.watchManager.defaultWatcher : null, cb, ctx);
    }

    public void sync(String path, VoidCallback cb, Object ctx) {
        PathUtils.validatePath(path);
        String serverPath = this.prependChroot(path);
        RequestHeader h = new RequestHeader();
        h.setType(9);
        SyncRequest request = new SyncRequest();
        SyncResponse response = new SyncResponse();
        request.setPath(serverPath);
        this.cnxn.queuePacket(h, new ReplyHeader(), request, response, cb, path, serverPath, ctx, (ZooKeeper.WatchRegistration)null);
    }

	// 获取状态
    public ZooKeeper.States getState() {
        return this.cnxn.getState();
    }

    public String toString() {
        ZooKeeper.States state = this.getState();
        return "State:" + state.toString() + (state.isConnected() ? " Timeout:" + this.getSessionTimeout() + " " : " ") + this.cnxn;
    }

    protected boolean testableWaitForShutdown(int wait) throws InterruptedException {
        this.cnxn.sendThread.join((long)wait);
        if (this.cnxn.sendThread.isAlive()) {
            return false;
        } else {
            this.cnxn.eventThread.join((long)wait);
            return !this.cnxn.eventThread.isAlive();
        }
    }

    protected SocketAddress testableRemoteSocketAddress() {
        return this.cnxn.sendThread.getClientCnxnSocket().getRemoteSocketAddress();
    }

    protected SocketAddress testableLocalSocketAddress() {
        return this.cnxn.sendThread.getClientCnxnSocket().getLocalSocketAddress();
    }

    private static ClientCnxnSocket getClientCnxnSocket() throws IOException {
        String clientCnxnSocketName = System.getProperty("zookeeper.clientCnxnSocket");
        if (clientCnxnSocketName == null) {
            clientCnxnSocketName = ClientCnxnSocketNIO.class.getName();
        }

        try {
            return (ClientCnxnSocket)Class.forName(clientCnxnSocketName).newInstance();
        } catch (Exception var3) {
            IOException ioe = new IOException("Couldn't instantiate " + clientCnxnSocketName);
            ioe.initCause(var3);
            throw ioe;
        }
    }

	// 打印系统变量
    static {
        Environment.logEnv("Client environment:", LOG);
    }

    public static enum States {
        CONNECTING,
        ASSOCIATING,
        CONNECTED,
        CONNECTEDREADONLY,
        CLOSED,
        AUTH_FAILED,
        NOT_CONNECTED;

        private States() {
        }

        public boolean isAlive() {
            return this != CLOSED && this != AUTH_FAILED;
        }

        public boolean isConnected() {
            return this == CONNECTED || this == CONNECTEDREADONLY;
        }
    }

    class ChildWatchRegistration extends ZooKeeper.WatchRegistration {
        public ChildWatchRegistration(Watcher watcher, String clientPath) {
            super(watcher, clientPath);
        }

        protected Map<String, Set<Watcher>> getWatches(int rc) {
            return ZooKeeper.this.watchManager.childWatches;
        }
    }

    class DataWatchRegistration extends ZooKeeper.WatchRegistration {
        public DataWatchRegistration(Watcher watcher, String clientPath) {
            super(watcher, clientPath);
        }

        protected Map<String, Set<Watcher>> getWatches(int rc) {
            return ZooKeeper.this.watchManager.dataWatches;
        }
    }

    class ExistsWatchRegistration extends ZooKeeper.WatchRegistration {
        public ExistsWatchRegistration(Watcher watcher, String clientPath) {
            super(watcher, clientPath);
        }

        protected Map<String, Set<Watcher>> getWatches(int rc) {
            return rc == 0 ? ZooKeeper.this.watchManager.dataWatches : ZooKeeper.this.watchManager.existWatches;
        }

        protected boolean shouldAddWatch(int rc) {
            return rc == 0 || rc == Code.NONODE.intValue();
        }
    }

    abstract class WatchRegistration {
        private Watcher watcher;
        private String clientPath;

        public WatchRegistration(Watcher watcher, String clientPath) {
            this.watcher = watcher;
            this.clientPath = clientPath;
        }

        protected abstract Map<String, Set<Watcher>> getWatches(int var1);

        public void register(int rc) {
            if (this.shouldAddWatch(rc)) {
                Map<String, Set<Watcher>> watches = this.getWatches(rc);
                synchronized(watches) {
                    Set<Watcher> watchers = (Set)watches.get(this.clientPath);
                    if (watchers == null) {
                        watchers = new HashSet();
                        watches.put(this.clientPath, watchers);
                    }

                    ((Set)watchers).add(this.watcher);
                }
            }

        }

        protected boolean shouldAddWatch(int rc) {
            return rc == 0;
        }
    }

	// watch管理器
    private static class ZKWatchManager implements ClientWatchManager {
        private final Map<String, Set<Watcher>> dataWatches;		// 数据监听器集合
        private final Map<String, Set<Watcher>> existWatches;		// 是否存在监听器集合
        private final Map<String, Set<Watcher>> childWatches;		// 子节点监听器集合
        private volatile Watcher defaultWatcher;					// 默认的监听器

        private ZKWatchManager() {
            this.dataWatches = new HashMap();
            this.existWatches = new HashMap();
            this.childWatches = new HashMap();
        }

        private final void addTo(Set<Watcher> from, Set<Watcher> to) {
            if (from != null) {
                to.addAll(from);
            }

        }

		// 实现
        public Set<Watcher> materialize(KeeperState state, EventType type, String clientPath) {
            Set<Watcher> result = new HashSet();
            switch(type) {
            case None:
                result.add(this.defaultWatcher);
                boolean clear = ClientCnxn.getDisableAutoResetWatch() && state != KeeperState.SyncConnected;
                Set ws;
                Iterator i$;
                synchronized(this.dataWatches) {
                    i$ = this.dataWatches.values().iterator();

                    while(i$.hasNext()) {
                        ws = (Set)i$.next();
                        result.addAll(ws);
                    }

                    if (clear) {
                        this.dataWatches.clear();
                    }
                }

                synchronized(this.existWatches) {
                    i$ = this.existWatches.values().iterator();

                    while(true) {
                        if (!i$.hasNext()) {
                            if (clear) {
                                this.existWatches.clear();
                            }
                            break;
                        }

                        ws = (Set)i$.next();
                        result.addAll(ws);
                    }
                }

                synchronized(this.childWatches) {
                    i$ = this.childWatches.values().iterator();

                    while(i$.hasNext()) {
                        ws = (Set)i$.next();
                        result.addAll(ws);
                    }

                    if (clear) {
                        this.childWatches.clear();
                    }

                    return result;
                }
            case NodeDataChanged:
            case NodeCreated:
                synchronized(this.dataWatches) {
                    this.addTo((Set)this.dataWatches.remove(clientPath), result);
                }

                synchronized(this.existWatches) {
                    this.addTo((Set)this.existWatches.remove(clientPath), result);
                    break;
                }
            case NodeChildrenChanged:
                synchronized(this.childWatches) {
                    this.addTo((Set)this.childWatches.remove(clientPath), result);
                    break;
                }
            case NodeDeleted:
                synchronized(this.dataWatches) {
                    this.addTo((Set)this.dataWatches.remove(clientPath), result);
                }

                synchronized(this.existWatches) {
                    Set<Watcher> list = (Set)this.existWatches.remove(clientPath);
                    if (list != null) {
                        this.addTo((Set)this.existWatches.remove(clientPath), result);
                        ZooKeeper.LOG.warn("We are triggering an exists watch for delete! Shouldn't happen!");
                    }
                }

                synchronized(this.childWatches) {
                    this.addTo((Set)this.childWatches.remove(clientPath), result);
                    break;
                }
            default:
                String msg = "Unhandled watch event type " + type + " with state " + state + " on path " + clientPath;
                ZooKeeper.LOG.error(msg);
                throw new RuntimeException(msg);
            }

            return result;
        }
    }
}
