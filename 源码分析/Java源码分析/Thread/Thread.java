//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package java.lang;

import java.lang.ThreadLocal.ThreadLocalMap;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import sun.misc.Contended;
import sun.misc.VM;
import sun.nio.ch.Interruptible;
import sun.reflect.CallerSensitive;
import sun.reflect.Reflection;
import sun.security.util.SecurityConstants;

public class Thread implements Runnable {
    private volatile String name;                         // 线程名字
    private int priority;                                 // 线程优先级 1-10 默认5
    private Thread threadQ;
    private long eetop;
    private boolean single_step;
    private boolean daemon = false;                        // 是否是守护线程
    private boolean stillborn = false;
    private Runnable target;                               // 要执行的任务
    private ThreadGroup group;                             // 线程组
    private ClassLoader contextClassLoader;                // 类加载器
    private AccessControlContext inheritedAccessControlContext; // 权限控制器上下文
    private static int threadInitNumber;                   // 线程初始序号
    ThreadLocalMap threadLocals = null;                    // 保存线程局部变量
    ThreadLocalMap inheritableThreadLocals = null;         // 保存继承的局部变量
    private long stackSize;                                // 栈深
    private long nativeParkEventPointer;
    private long tid;                                     
    private static long threadSeqNumber;                   // 线程序号
    private volatile int threadStatus = 0;                 // 线程状态
    volatile Object parkBlocker;
    private volatile Interruptible blocker;                // 中断
    private final Object blockerLock = new Object();       // 阻塞锁
    public static final int MIN_PRIORITY = 1;              // 最小优先级
    public static final int NORM_PRIORITY = 5;             // 正常优先级 (默认值)
    public static final int MAX_PRIORITY = 10;             // 最大优先级
    private static final StackTraceElement[] EMPTY_STACK_TRACE;   // 栈轨迹元素
    private static final RuntimePermission SUBCLASS_IMPLEMENTATION_PERMISSION;               // 子类实现权限
    private volatile Thread.UncaughtExceptionHandler uncaughtExceptionHandler;               // 未捕获异常处理器
    private static volatile Thread.UncaughtExceptionHandler defaultUncaughtExceptionHandler; // 默认的未捕获异常处理器
    @Contended("tlr")
    long threadLocalRandomSeed;
    @Contended("tlr")
    int threadLocalRandomProbe;
    @Contended("tlr")
    int threadLocalRandomSecondarySeed;

	// 注册本地方法
    private static native void registerNatives();

	// 获取下一个线程num
    private static synchronized int nextThreadNum() {
        return threadInitNumber++;
    }

	// 获取下一个线程id
    private static synchronized long nextThreadID() {
        return ++threadSeqNumber;
    }

    void blockedOn(Interruptible var1) {
        Object var2 = this.blockerLock;
        synchronized(this.blockerLock) {
            this.blocker = var1;
        }
    }

	// 获取当前线程(本地方法)
    public static native Thread currentThread();

	// 睡眠(本地方法)
    public static native void yield();

	// 当前执行线程暂停一段时间 不会释放锁 可以使别的线程得到一定的执行时间 var0 毫秒
    public static native void sleep(long var0) throws InterruptedException;

	// var0 毫秒 var2 纳秒
    public static void sleep(long var0, int var2) throws InterruptedException {
        if (var0 < 0L) {
            throw new IllegalArgumentException("timeout value is negative");
        } else if (var2 >= 0 && var2 <= 999999) {
            if (var2 >= 500000 || var2 != 0 && var0 == 0L) {
                ++var0;
            }

            sleep(var0);
        } else {
            throw new IllegalArgumentException("nanosecond timeout value out of range");
        }
    }

    private void init(ThreadGroup var1, Runnable var2, String var3, long var4) {
        this.init(var1, var2, var3, var4, (AccessControlContext)null, true);
    }

	/*
	 * 初始化方法
	 * @param var1 线程组
	 * @param var2 要执行的任务
	 * @param var3 线程名字
	 * @param var4 栈深
	 * @param var6 权限控制器上下文
	 * @param var7 是否允许继承父线程的线程局部变量
	 */
    private void init(ThreadGroup var1, Runnable var2, String var3, long var4, AccessControlContext var6, boolean var7) {
        if (var3 == null) {
            throw new NullPointerException("name cannot be null");
        } else {
            this.name = var3;                  // 设置线程名字
            Thread var8 = currentThread();     // 设置当前线程对象
            SecurityManager var9 = System.getSecurityManager();  // 获取安全管理器
            if (var1 == null) {
                if (var9 != null) {
                    var1 = var9.getThreadGroup();    // 优先从安全管理器获取线程组 再从当前线程获取线程组
                }

                if (var1 == null) {
                    var1 = var8.getThreadGroup();
                }
            }

            var1.checkAccess();   // 检查访问权限(安全管理器概念)  // 如果启用了安全管理器 则线程组不能为空 如果是根线程组 则检查是否有更改线程组的权限
            if (var9 != null && isCCLOverridden(this.getClass())) {
				// 如果是重写的线程类且启用了安全管理器 检查是否有重写Thread类的权限
                var9.checkPermission(SUBCLASS_IMPLEMENTATION_PERMISSION);
            }

            var1.addUnstarted();  // 线程组中为start的线程计数器+1
            this.group = var1;    // 设置当前所属线程组 如果是子类 继承自父Thread类
            this.daemon = var8.isDaemon();       // 设置是否是守护进程 如果是子类 继承自父Thread类
            this.priority = var8.getPriority();  // 设置优先级 如果是子类 继承自父Thread类
            if (var9 != null && !isCCLOverridden(var8.getClass())) {
                this.contextClassLoader = var8.contextClassLoader;       // 设置类加载器 如果是子类 继承自父Thread类
            } else {
                this.contextClassLoader = var8.getContextClassLoader();  // 设置类加载器
            }

            this.inheritedAccessControlContext = var6 != null ? var6 : AccessController.getContext();  // 设置
            this.target = var2;                 // 设置待执行任务
            this.setPriority(this.priority);    // 具体设置线程优先级 最终会调用本地方法设置线程优先级 会校验权限 比较线程组的优先级
            if (var7 && var8.inheritableThreadLocals != null) {   // 如果允许继承父类线程局部变量 则设置inheritableThreadLocals值
                this.inheritableThreadLocals = ThreadLocal.createInheritedMap(var8.inheritableThreadLocals);
            }

            this.stackSize = var4;       // 设置栈深
            this.tid = nextThreadID();   // 设置线程序号
        }
    }

	// 线程对象不能克隆
    protected Object clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }

    public Thread() {
        this.init((ThreadGroup)null, (Runnable)null, "Thread-" + nextThreadNum(), 0L);
    }

    public Thread(Runnable var1) {
        this.init((ThreadGroup)null, var1, "Thread-" + nextThreadNum(), 0L);
    }

    Thread(Runnable var1, AccessControlContext var2) {
        this.init((ThreadGroup)null, var1, "Thread-" + nextThreadNum(), 0L, var2, false);
    }

    public Thread(ThreadGroup var1, Runnable var2) {
        this.init(var1, var2, "Thread-" + nextThreadNum(), 0L);
    }

    public Thread(String var1) {
        this.init((ThreadGroup)null, (Runnable)null, var1, 0L);
    }

    public Thread(ThreadGroup var1, String var2) {
        this.init(var1, (Runnable)null, var2, 0L);
    }

    public Thread(Runnable var1, String var2) {
        this.init((ThreadGroup)null, var1, var2, 0L);
    }

    public Thread(ThreadGroup var1, Runnable var2, String var3) {
        this.init(var1, var2, var3, 0L);
    }

    public Thread(ThreadGroup var1, Runnable var2, String var3, long var4) {
        this.init(var1, var2, var3, var4);
    }

	// 开启一个线程 方法执行完成之后系统才会开启一个新线程并分配线程所需要的资源
    public synchronized void start() {
        if (this.threadStatus != 0) {
            throw new IllegalThreadStateException(); // 线程状态的初始化值是0 如果不是0 说明线程处于其他状态 不能再执行开启线程的方法
        } else {
            this.group.add(this);  // 将当前线程添加到线程组
            boolean var1 = false;  // 开启标识

            try {
                this.start0();     // 开启线程 调用本地方法
                var1 = true;
            } finally {
                try {
                    if (!var1) {
                        this.group.threadStartFailed(this);  // 处理线程没有开启成功的情况
                    }
                } catch (Throwable var8) {
                    ;
                }

            }

        }
    }

	// 开启线程的本地方法
    private native void start0();

	// 运行线程需要执行的任务 不需要手动调用 start() 之后如果当前线程获取到了CPU执行权 则自动执行
    public void run() {
        if (this.target != null) {
            this.target.run();
        }

    }

	// 退出
    private void exit() {
        if (this.group != null) {
            this.group.threadTerminated(this); // 线程组相关处理
            this.group = null;
        }

        this.target = null;
        this.threadLocals = null;
        this.inheritableThreadLocals = null;
        this.inheritedAccessControlContext = null;
        this.blocker = null;
        this.uncaughtExceptionHandler = null;
    }

    /** @deprecated */
    @Deprecated
    public final void stop() {
        SecurityManager var1 = System.getSecurityManager();
        if (var1 != null) {
            this.checkAccess(); // 校验是否有修改线程的权限
            if (this != currentThread()) {
				// 如果当前对象不是当前线程 则检查是否有停止线程的权限
                var1.checkPermission(SecurityConstants.STOP_THREAD_PERMISSION);
            }
        }

        if (this.threadStatus != 0) {
            this.resume();
        }

        this.stop0(new ThreadDeath()); // 调用停止线程的本地方法
    }

	// 不支持停止线程方法
    /** @deprecated */
    @Deprecated
    public final synchronized void stop(Throwable var1) {
        throw new UnsupportedOperationException();
    }

	// 中断
    public void interrupt() {
        if (this != currentThread()) {
            this.checkAccess();
        }

        Object var1 = this.blockerLock;
        synchronized(this.blockerLock) {
            Interruptible var2 = this.blocker;
            if (var2 != null) {
                this.interrupt0();
                var2.interrupt(this);
                return;
            }
        }

        this.interrupt0();
    }

	// 判断线程是否中断
    public static boolean interrupted() {
        return currentThread().isInterrupted(true);
    }

    public boolean isInterrupted() {
        return this.isInterrupted(false);
    }

    private native boolean isInterrupted(boolean var1);

	// 不支持销毁线程方法
    /** @deprecated */
    @Deprecated
    public void destroy() {
        throw new NoSuchMethodError();
    }

	// 判断线程是否存活(本地方法)
    public final native boolean isAlive();

    /** @deprecated */
    @Deprecated
    public final void suspend() {
        this.checkAccess();
        this.suspend0();
    }

    /** @deprecated */
    @Deprecated
    public final void resume() {
        this.checkAccess();
        this.resume0();
    }

	// 设置线程优先级
    public final void setPriority(int var1) {
		// 如果启用了线程组 且是根线程组 校验是否有修改线程的权限
        this.checkAccess();
        if (var1 <= 10 && var1 >= 1) {
            ThreadGroup var2;
            if ((var2 = this.getThreadGroup()) != null) {
                if (var1 > var2.getMaxPriority()) {
                    var1 = var2.getMaxPriority();  // 线程的优先级不能超出线程组的最大优先级
                }

                this.setPriority0(this.priority = var1);  // 调用本地方法设置线程的优先级
            }

        } else {
            throw new IllegalArgumentException();  // 线程的优先级值必须在1-10之间
        }
    }

    public final int getPriority() {
        return this.priority;
    }

	// 设置线程名字
    public final synchronized void setName(String var1) {
        this.checkAccess();
        if (var1 == null) {
            throw new NullPointerException("name cannot be null");
        } else {
            this.name = var1;
            if (this.threadStatus != 0) {
                this.setNativeName(var1);
            }

        }
    }

    public final String getName() {
        return this.name;
    }

    public final ThreadGroup getThreadGroup() {
        return this.group;
    }

    public static int activeCount() {
        return currentThread().getThreadGroup().activeCount();
    }

    public static int enumerate(Thread[] var0) {
        return currentThread().getThreadGroup().enumerate(var0);
    }

    /** @deprecated */
    @Deprecated
    public native int countStackFrames();

	// 加入线程 var1 加入并执行的时间
    public final synchronized void join(long var1) throws InterruptedException {
        long var3 = System.currentTimeMillis();
        long var5 = 0L;
        if (var1 < 0L) {
            throw new IllegalArgumentException("timeout value is negative");
        } else {
            if (var1 == 0L) {
                while(this.isAlive()) {
                    this.wait(0L);
                }
            } else {
                while(this.isAlive()) {
                    long var7 = var1 - var5;
                    if (var7 <= 0L) {
                        break;
                    }

                    this.wait(var7);
                    var5 = System.currentTimeMillis() - var3;
                }
            }

        }
    }

    public final synchronized void join(long var1, int var3) throws InterruptedException {
        if (var1 < 0L) {
            throw new IllegalArgumentException("timeout value is negative");
        } else if (var3 >= 0 && var3 <= 999999) {
            if (var3 >= 500000 || var3 != 0 && var1 == 0L) {
                ++var1;
            }

            this.join(var1);
        } else {
            throw new IllegalArgumentException("nanosecond timeout value out of range");
        }
    }

	// 加入 知道加入的线程执行完毕
    public final void join() throws InterruptedException {
        this.join(0L);
    }

    public static void dumpStack() {
        (new Exception("Stack trace")).printStackTrace();
    }

    public final void setDaemon(boolean var1) {
        this.checkAccess();
        if (this.isAlive()) {
            throw new IllegalThreadStateException();
        } else {
            this.daemon = var1;
        }
    }

    public final boolean isDaemon() {
        return this.daemon;
    }

    public final void checkAccess() {
        SecurityManager var1 = System.getSecurityManager();
        if (var1 != null) {
            var1.checkAccess(this);
        }

    }

    public String toString() {
        ThreadGroup var1 = this.getThreadGroup();
        return var1 != null ? "Thread[" + this.getName() + "," + this.getPriority() + "," + var1.getName() + "]" : "Thread[" + this.getName() + "," + this.getPriority() + ",]";
    }

	// 获取类加载器
	// Reflection.getCallerClass()方法调用所在的方法必须用@CallerSensitive进行注解，通过此方法获取class时会跳过链路上所有的有@CallerSensitive注解的方法的类，直到遇到第一个未使用该注解的类，避免了用Reflection.getCallerClass(int n) 这个过时方法来自己做判断。
    @CallerSensitive
    public ClassLoader getContextClassLoader() {
        if (this.contextClassLoader == null) {
            return null;
        } else {
            SecurityManager var1 = System.getSecurityManager();
            if (var1 != null) {
				// 校验是否有获取类加载器的权限
                ClassLoader.checkClassLoaderPermission(this.contextClassLoader, Reflection.getCallerClass());
            }

            return this.contextClassLoader;
        }
    }

	// 设置上下文类加载器
    public void setContextClassLoader(ClassLoader var1) {
        SecurityManager var2 = System.getSecurityManager();
        if (var2 != null) {
			// 校验是否有设置上下文类加载器的权限
            var2.checkPermission(new RuntimePermission("setContextClassLoader"));
        }

        this.contextClassLoader = var1;
    }

	// 判断当前线程是否持有锁(本地方法)
    public static native boolean holdsLock(Object var0);

    public StackTraceElement[] getStackTrace() {
        if (this != currentThread()) {
            SecurityManager var1 = System.getSecurityManager();
            if (var1 != null) {
                var1.checkPermission(SecurityConstants.GET_STACK_TRACE_PERMISSION);
            }

            if (!this.isAlive()) {
                return EMPTY_STACK_TRACE;
            } else {
                StackTraceElement[][] var2 = dumpThreads(new Thread[]{this});
                StackTraceElement[] var3 = var2[0];
                if (var3 == null) {
                    var3 = EMPTY_STACK_TRACE;
                }

                return var3;
            }
        } else {
            return (new Exception()).getStackTrace();
        }
    }

	// 获取所有栈轨迹
    public static Map<Thread, StackTraceElement[]> getAllStackTraces() {
        SecurityManager var0 = System.getSecurityManager();
        if (var0 != null) {
			// 校验是否有查看栈轨迹和修改线程组的权限
            var0.checkPermission(SecurityConstants.GET_STACK_TRACE_PERMISSION);
            var0.checkPermission(SecurityConstants.MODIFY_THREADGROUP_PERMISSION);
        }

        Thread[] var1 = getThreads();  // 调用本地方法获取线程
        StackTraceElement[][] var2 = dumpThreads(var1);  // 获取线程的栈轨迹 
        HashMap var3 = new HashMap(var1.length);

        for(int var4 = 0; var4 < var1.length; ++var4) {
            StackTraceElement[] var5 = var2[var4];
            if (var5 != null) {
                var3.put(var1[var4], var5);
            }
        }

        return var3;
    }

    private static boolean isCCLOverridden(Class<?> var0) {
        if (var0 == Thread.class) {
            return false;
        } else {
            processQueue(Thread.Caches.subclassAuditsQueue, Thread.Caches.subclassAudits);
            Thread.WeakClassKey var1 = new Thread.WeakClassKey(var0, Thread.Caches.subclassAuditsQueue);
            Boolean var2 = (Boolean)Thread.Caches.subclassAudits.get(var1);
            if (var2 == null) {
                var2 = auditSubclass(var0);
                Thread.Caches.subclassAudits.putIfAbsent(var1, var2);
            }

            return var2;
        }
    }

	// 审核子类
    private static boolean auditSubclass(final Class<?> var0) {
        Boolean var1 = (Boolean)AccessController.doPrivileged(new PrivilegedAction<Boolean>() {
            public Boolean run() {
                Class var1 = var0;

                while(var1 != Thread.class) {
                    try {
                        var1.getDeclaredMethod("getContextClassLoader");
                        return Boolean.TRUE;
                    } catch (NoSuchMethodException var4) {
                        try {
                            Class[] var2 = new Class[]{ClassLoader.class};
                            var1.getDeclaredMethod("setContextClassLoader", var2);
                            return Boolean.TRUE;
                        } catch (NoSuchMethodException var3) {
                            var1 = var1.getSuperclass();
                        }
                    }
                }

                return Boolean.FALSE;
            }
        });
        return var1;
    }

    private static native StackTraceElement[][] dumpThreads(Thread[] var0);

    private static native Thread[] getThreads();

    public long getId() {
        return this.tid;
    }

    public Thread.State getState() {
        return VM.toThreadState(this.threadStatus);
    }

    public static void setDefaultUncaughtExceptionHandler(Thread.UncaughtExceptionHandler var0) {
        SecurityManager var1 = System.getSecurityManager();
        if (var1 != null) {
            var1.checkPermission(new RuntimePermission("setDefaultUncaughtExceptionHandler"));
        }

        defaultUncaughtExceptionHandler = var0;
    }

    public static Thread.UncaughtExceptionHandler getDefaultUncaughtExceptionHandler() {
        return defaultUncaughtExceptionHandler;
    }

    public Thread.UncaughtExceptionHandler getUncaughtExceptionHandler() {
        return (Thread.UncaughtExceptionHandler)(this.uncaughtExceptionHandler != null ? this.uncaughtExceptionHandler : this.group);
    }

    public void setUncaughtExceptionHandler(Thread.UncaughtExceptionHandler var1) {
        this.checkAccess();
        this.uncaughtExceptionHandler = var1;
    }

    private void dispatchUncaughtException(Throwable var1) {
        this.getUncaughtExceptionHandler().uncaughtException(this, var1);
    }

    static void processQueue(ReferenceQueue<Class<?>> var0, ConcurrentMap<? extends WeakReference<Class<?>>, ?> var1) {
        Reference var2;
        while((var2 = var0.poll()) != null) {
            var1.remove(var2);
        }

    }

    private native void setPriority0(int var1);

    private native void stop0(Object var1);

    private native void suspend0();

    private native void resume0();

    private native void interrupt0();

    private native void setNativeName(String var1);

    static {
        registerNatives();                              // 注册本地方法
        EMPTY_STACK_TRACE = new StackTraceElement[0];   // 初始化栈轨迹
        SUBCLASS_IMPLEMENTATION_PERMISSION = new RuntimePermission("enableContextClassLoaderOverride");    // 允许重写上下文类加载器常量
    }

    private static class Caches {
        static final ConcurrentMap<Thread.WeakClassKey, Boolean> subclassAudits = new ConcurrentHashMap();
        static final ReferenceQueue<Class<?>> subclassAuditsQueue = new ReferenceQueue();

        private Caches() {
        }
    }

    public static enum State {
        NEW,          // 创建
        RUNNABLE,     // 就绪
        BLOCKED,      // 阻塞
        WAITING,      // 等待
        TIMED_WAITING, // 睡眠
        TERMINATED;   // 终止

        private State() {
        }
    }

    @FunctionalInterface
    public interface UncaughtExceptionHandler {
        void uncaughtException(Thread var1, Throwable var2);
    }

    static class WeakClassKey extends WeakReference<Class<?>> {
        private final int hash;

        WeakClassKey(Class<?> var1, ReferenceQueue<Class<?>> var2) {
            super(var1, var2);
            this.hash = System.identityHashCode(var1);
        }

        public int hashCode() {
            return this.hash;
        }

        public boolean equals(Object var1) {
            if (var1 == this) {
                return true;
            } else if (!(var1 instanceof Thread.WeakClassKey)) {
                return false;
            } else {
                Object var2 = this.get();
                return var2 != null && var2 == ((Thread.WeakClassKey)var1).get();
            }
        }
    }
}
