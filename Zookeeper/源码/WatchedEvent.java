package org.apache.zookeeper;

import org.apache.zookeeper.Watcher.Event.EventType;
import org.apache.zookeeper.Watcher.Event.KeeperState;
import org.apache.zookeeper.proto.WatcherEvent;

// 事件
public class WatchedEvent {
    private final KeeperState keeperState;	// 状态类型
    private final EventType eventType;		// 事件类型
    private String path;	// 节点(由路径唯一标识)

    public WatchedEvent(EventType eventType, KeeperState keeperState, String path) {
        this.keeperState = keeperState;
        this.eventType = eventType;
        this.path = path;
    }

    public WatchedEvent(WatcherEvent eventMessage) {
        this.keeperState = KeeperState.fromInt(eventMessage.getState());
        this.eventType = EventType.fromInt(eventMessage.getType());
        this.path = eventMessage.getPath();
    }

    public KeeperState getState() {
        return this.keeperState;
    }

    public EventType getType() {
        return this.eventType;
    }

    public String getPath() {
        return this.path;
    }

    public String toString() {
        return "WatchedEvent state:" + this.keeperState + " type:" + this.eventType + " path:" + this.path;
    }

    public WatcherEvent getWrapper() {
        return new WatcherEvent(this.eventType.getIntValue(), this.keeperState.getIntValue(), this.path);
    }
}
