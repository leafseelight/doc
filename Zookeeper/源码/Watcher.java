package org.apache.zookeeper;

// 监听器
public interface Watcher {
	// 核心方法 处理事件
    void process(WatchedEvent var1);

    public interface Event {
		// 事件类型
        public static enum EventType {
            None(-1),
            NodeCreated(1),					// 节点创建
            NodeDeleted(2),					// 节点删除
            NodeDataChanged(3),				// 节点数据变化
            NodeChildrenChanged(4);			// 子节点变化

            private final int intValue;

            private EventType(int intValue) {
                this.intValue = intValue;
            }

            public int getIntValue() {
                return this.intValue;
            }

            public static Watcher.Event.EventType fromInt(int intValue) {
                switch(intValue) {
                case -1:
                    return None;
                case 0:
                default:
                    throw new RuntimeException("Invalid integer value for conversion to EventType");
                case 1:
                    return NodeCreated;
                case 2:
                    return NodeDeleted;
                case 3:
                    return NodeDataChanged;
                case 4:
                    return NodeChildrenChanged;
                }
            }
        }

		// 状态类型
        public static enum KeeperState {
            /** @deprecated */
            @Deprecated
            Unknown(-1),
            Disconnected(0),			// 未连接 连接失败 连接断开
            /** @deprecated */
            @Deprecated
            NoSyncConnected(1),
            SyncConnected(3),			// 连接成功
            AuthFailed(4),				// 认证失败
            ConnectedReadOnly(5),		// 连接只读
            SaslAuthenticated(6),
            Expired(-112);				// 会话过期

            private final int intValue;

            private KeeperState(int intValue) {
                this.intValue = intValue;
            }

            public int getIntValue() {
                return this.intValue;
            }

            public static Watcher.Event.KeeperState fromInt(int intValue) {
                switch(intValue) {
                case -112:
                    return Expired;
                case -1:
                    return Unknown;
                case 0:
                    return Disconnected;
                case 1:
                    return NoSyncConnected;
                case 3:
                    return SyncConnected;
                case 4:
                    return AuthFailed;
                case 5:
                    return ConnectedReadOnly;
                case 6:
                    return SaslAuthenticated;
                default:
                    throw new RuntimeException("Invalid integer value for conversion to KeeperState");
                }
            }
        }
    }
}
