package com.netflix.hystrix;

import com.netflix.hystrix.ExecutionResult.EventCounts;
import com.netflix.hystrix.HystrixCircuitBreaker.NoOpCircuitBreaker;
import com.netflix.hystrix.HystrixCommandKey.Factory;
import com.netflix.hystrix.HystrixCommandProperties.ExecutionIsolationStrategy;
import com.netflix.hystrix.HystrixCommandProperties.Setter;
import com.netflix.hystrix.exception.ExceptionNotWrappedByHystrix;
import com.netflix.hystrix.exception.HystrixBadRequestException;
import com.netflix.hystrix.exception.HystrixRuntimeException;
import com.netflix.hystrix.exception.HystrixTimeoutException;
import com.netflix.hystrix.exception.HystrixRuntimeException.FailureType;
import com.netflix.hystrix.strategy.HystrixPlugins;
import com.netflix.hystrix.strategy.concurrency.HystrixConcurrencyStrategy;
import com.netflix.hystrix.strategy.concurrency.HystrixContextRunnable;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;
import com.netflix.hystrix.strategy.eventnotifier.HystrixEventNotifier;
import com.netflix.hystrix.strategy.executionhook.HystrixCommandExecutionHook;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisherFactory;
import com.netflix.hystrix.strategy.properties.HystrixPropertiesFactory;
import com.netflix.hystrix.strategy.properties.HystrixPropertiesStrategy;
import com.netflix.hystrix.strategy.properties.HystrixProperty;
import com.netflix.hystrix.util.HystrixTimer;
import com.netflix.hystrix.util.HystrixTimer.TimerListener;
import java.lang.ref.Reference;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Notification;
import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.Observable.Operator;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func0;
import rx.functions.Func1;
import rx.subjects.ReplaySubject;
import rx.subscriptions.CompositeSubscription;

// 抽象命令
abstract class AbstractCommand<R> implements HystrixInvokableInfo<R>, HystrixObservable<R> {
    private static final Logger logger = LoggerFactory.getLogger(AbstractCommand.class);
    protected final HystrixCircuitBreaker circuitBreaker;			// 断路器
    protected final HystrixThreadPool threadPool;					// 线程池
    protected final HystrixThreadPoolKey threadPoolKey;				// 线程池key
    protected final HystrixCommandProperties properties;
    protected final HystrixCommandMetrics metrics;
    protected final HystrixCommandKey commandKey;					// 命令key
    protected final HystrixCommandGroupKey commandGroup;			// 命令组key
    protected final HystrixEventNotifier eventNotifier;				// 事件通知器
    protected final HystrixConcurrencyStrategy concurrencyStrategy;	// 并发策略
    protected final HystrixCommandExecutionHook executionHook;		// 钩子
    protected final AbstractCommand.TryableSemaphore fallbackSemaphoreOverride;		// 降级信号量
    protected static final ConcurrentHashMap<String, AbstractCommand.TryableSemaphore> fallbackSemaphorePerCircuit = new ConcurrentHashMap();
    protected final AbstractCommand.TryableSemaphore executionSemaphoreOverride;	// 执行信号量
    protected static final ConcurrentHashMap<String, AbstractCommand.TryableSemaphore> executionSemaphorePerCircuit = new ConcurrentHashMap();
    protected final AtomicReference<Reference<TimerListener>> timeoutTimer = new AtomicReference();
    protected AtomicReference<AbstractCommand.CommandState> commandState;			// 命令状态
    protected AtomicReference<AbstractCommand.ThreadState> threadState;				// 线程状态
    protected volatile ExecutionResult executionResult;				// 执行结果
    protected volatile boolean isResponseFromCache;					// 是否从缓存中响应
    protected volatile ExecutionResult executionResultAtTimeOfCancellation;
    protected volatile long commandStartTimestamp;					// 命令执行开始时间
    protected final AtomicReference<AbstractCommand.TimedOutStatus> isCommandTimedOut;	// 命令执行是否超时
    protected volatile Action0 endCurrentThreadExecutingCommand;
    protected final HystrixRequestCache requestCache;				// 命令请求缓存
    protected final HystrixRequestLog currentRequestLog;
    private static ConcurrentHashMap<Class<?>, String> defaultNameCache = new ConcurrentHashMap();	// 缓存class类型对应的类名 用于作为默认的key值
    protected static ConcurrentHashMap<HystrixCommandKey, Boolean> commandContainsFallback = new ConcurrentHashMap();	// 命令key是否有回调的缓存

    static String getDefaultNameFromClass(Class<?> cls) {
        String fromCache = (String)defaultNameCache.get(cls);
        if (fromCache != null) {
            return fromCache;
        } else {
            String name = cls.getSimpleName();
            if (name.equals("")) {
                name = cls.getName();
                name = name.substring(name.lastIndexOf(46) + 1, name.length());
            }

            defaultNameCache.put(cls, name);
            return name;
        }
    }

	// 构造
    protected AbstractCommand(HystrixCommandGroupKey group, HystrixCommandKey key, HystrixThreadPoolKey threadPoolKey, HystrixCircuitBreaker circuitBreaker, HystrixThreadPool threadPool, Setter commandPropertiesDefaults, com.netflix.hystrix.HystrixThreadPoolProperties.Setter threadPoolPropertiesDefaults, HystrixCommandMetrics metrics, AbstractCommand.TryableSemaphore fallbackSemaphore, AbstractCommand.TryableSemaphore executionSemaphore, HystrixPropertiesStrategy propertiesStrategy, HystrixCommandExecutionHook executionHook) {
        this.commandState = new AtomicReference(AbstractCommand.CommandState.NOT_STARTED);    	// 默认命令状态 未执行
        this.threadState = new AtomicReference(AbstractCommand.ThreadState.NOT_USING_THREAD);	// 默认线程状态 未使用线程
        this.executionResult = ExecutionResult.EMPTY;											// 默认执行结果 空
        this.isResponseFromCache = false;														// 默认未从缓存中获取响应
        this.commandStartTimestamp = -1L;														// 默认命令执行开始时间
        this.isCommandTimedOut = new AtomicReference(AbstractCommand.TimedOutStatus.NOT_EXECUTED); // 默认是否超时 未执行
        this.commandGroup = initGroupKey(group);
        this.commandKey = initCommandKey(key, this.getClass());
        this.properties = initCommandProperties(this.commandKey, propertiesStrategy, commandPropertiesDefaults);
        this.threadPoolKey = initThreadPoolKey(threadPoolKey, this.commandGroup, (String)this.properties.executionIsolationThreadPoolKeyOverride().get());
        this.metrics = initMetrics(metrics, this.commandGroup, this.threadPoolKey, this.commandKey, this.properties);
        this.circuitBreaker = initCircuitBreaker((Boolean)this.properties.circuitBreakerEnabled().get(), circuitBreaker, this.commandGroup, this.commandKey, this.properties, this.metrics);
        this.threadPool = initThreadPool(threadPool, this.threadPoolKey, threadPoolPropertiesDefaults);
        this.eventNotifier = HystrixPlugins.getInstance().getEventNotifier();
        this.concurrencyStrategy = HystrixPlugins.getInstance().getConcurrencyStrategy();
        HystrixMetricsPublisherFactory.createOrRetrievePublisherForCommand(this.commandKey, this.commandGroup, this.metrics, this.circuitBreaker, this.properties);
        this.executionHook = initExecutionHook(executionHook);
        this.requestCache = HystrixRequestCache.getInstance(this.commandKey, this.concurrencyStrategy);
        this.currentRequestLog = initRequestLog((Boolean)this.properties.requestLogEnabled().get(), this.concurrencyStrategy);
        this.fallbackSemaphoreOverride = fallbackSemaphore;					// 降级信号量
        this.executionSemaphoreOverride = executionSemaphore;				// 执行信号量
    }

	// 初始化分组key
    private static HystrixCommandGroupKey initGroupKey(HystrixCommandGroupKey fromConstructor) {
        if (fromConstructor == null) {
            throw new IllegalStateException("HystrixCommandGroup can not be NULL");
        } else {
            return fromConstructor;
        }
    }

	// 初始化命令key 优先从构造传入 否则默认为类名
    private static HystrixCommandKey initCommandKey(HystrixCommandKey fromConstructor, Class<?> clazz) {
        if (fromConstructor != null && !fromConstructor.name().trim().equals("")) {
            return fromConstructor;
        } else {
            String keyName = getDefaultNameFromClass(clazz);
            return Factory.asKey(keyName);
        }
    }

    private static HystrixCommandProperties initCommandProperties(HystrixCommandKey commandKey, HystrixPropertiesStrategy propertiesStrategy, Setter commandPropertiesDefaults) {
        return propertiesStrategy == null ? HystrixPropertiesFactory.getCommandProperties(commandKey, commandPropertiesDefaults) : propertiesStrategy.getCommandProperties(commandKey, commandPropertiesDefaults);
    }

    private static HystrixThreadPoolKey initThreadPoolKey(HystrixThreadPoolKey threadPoolKey, HystrixCommandGroupKey groupKey, String threadPoolKeyOverride) {
        if (threadPoolKeyOverride == null) {
            return threadPoolKey == null ? com.netflix.hystrix.HystrixThreadPoolKey.Factory.asKey(groupKey.name()) : threadPoolKey;
        } else {
            return com.netflix.hystrix.HystrixThreadPoolKey.Factory.asKey(threadPoolKeyOverride);
        }
    }

    private static HystrixCommandMetrics initMetrics(HystrixCommandMetrics fromConstructor, HystrixCommandGroupKey groupKey, HystrixThreadPoolKey threadPoolKey, HystrixCommandKey commandKey, HystrixCommandProperties properties) {
        return fromConstructor == null ? HystrixCommandMetrics.getInstance(commandKey, groupKey, threadPoolKey, properties) : fromConstructor;
    }

    private static HystrixCircuitBreaker initCircuitBreaker(boolean enabled, HystrixCircuitBreaker fromConstructor, HystrixCommandGroupKey groupKey, HystrixCommandKey commandKey, HystrixCommandProperties properties, HystrixCommandMetrics metrics) {
        if (enabled) {
            return fromConstructor == null ? com.netflix.hystrix.HystrixCircuitBreaker.Factory.getInstance(commandKey, groupKey, properties, metrics) : fromConstructor;
        } else {
            return new NoOpCircuitBreaker();
        }
    }

    private static HystrixCommandExecutionHook initExecutionHook(HystrixCommandExecutionHook fromConstructor) {
        if (fromConstructor == null) {
            return new AbstractCommand.ExecutionHookDeprecationWrapper(HystrixPlugins.getInstance().getCommandExecutionHook());
        } else {
            return (HystrixCommandExecutionHook)(fromConstructor instanceof AbstractCommand.ExecutionHookDeprecationWrapper ? fromConstructor : new AbstractCommand.ExecutionHookDeprecationWrapper(fromConstructor));
        }
    }

    private static HystrixThreadPool initThreadPool(HystrixThreadPool fromConstructor, HystrixThreadPoolKey threadPoolKey, com.netflix.hystrix.HystrixThreadPoolProperties.Setter threadPoolPropertiesDefaults) {
        return fromConstructor == null ? com.netflix.hystrix.HystrixThreadPool.Factory.getInstance(threadPoolKey, threadPoolPropertiesDefaults) : fromConstructor;
    }

    private static HystrixRequestLog initRequestLog(boolean enabled, HystrixConcurrencyStrategy concurrencyStrategy) {
        return enabled ? HystrixRequestLog.getCurrentRequest(concurrencyStrategy) : null;
    }

    void markAsCollapsedCommand(HystrixCollapserKey collapserKey, int sizeOfBatch) {
        this.eventNotifier.markEvent(HystrixEventType.COLLAPSED, this.commandKey);
        this.executionResult = this.executionResult.markCollapsed(collapserKey, sizeOfBatch);
    }

    public Observable<R> observe() {
        ReplaySubject<R> subject = ReplaySubject.create();
        final Subscription sourceSubscription = this.toObservable().subscribe(subject);
        return subject.doOnUnsubscribe(new Action0() {
            public void call() {
                sourceSubscription.unsubscribe();
            }
        });
    }

    protected abstract Observable<R> getExecutionObservable();

    protected abstract Observable<R> getFallbackObservable();

    public Observable<R> toObservable() {
        final Action0 terminateCommandCleanup = new Action0() {
            public void call() {
                if (AbstractCommand.this.commandState.compareAndSet(AbstractCommand.CommandState.OBSERVABLE_CHAIN_CREATED, AbstractCommand.CommandState.TERMINAL)) {
                    AbstractCommand.this.handleCommandEnd(false);
                } else if (AbstractCommand.this.commandState.compareAndSet(AbstractCommand.CommandState.USER_CODE_EXECUTED, AbstractCommand.CommandState.TERMINAL)) {
                    AbstractCommand.this.handleCommandEnd(true);
                }

            }
        };
        final Action0 unsubscribeCommandCleanup = new Action0() {
            public void call() {
                if (AbstractCommand.this.commandState.compareAndSet(AbstractCommand.CommandState.OBSERVABLE_CHAIN_CREATED, AbstractCommand.CommandState.UNSUBSCRIBED)) {
                    if (!AbstractCommand.this.executionResult.containsTerminalEvent()) {
                        AbstractCommand.this.eventNotifier.markEvent(HystrixEventType.CANCELLED, AbstractCommand.this.commandKey);

                        try {
                            AbstractCommand.this.executionHook.onUnsubscribe(AbstractCommand.this);
                        } catch (Throwable var3) {
                            AbstractCommand.logger.warn("Error calling HystrixCommandExecutionHook.onUnsubscribe", var3);
                        }

                        AbstractCommand.this.executionResultAtTimeOfCancellation = AbstractCommand.this.executionResult.addEvent((int)(System.currentTimeMillis() - AbstractCommand.this.commandStartTimestamp), HystrixEventType.CANCELLED);
                    }

                    AbstractCommand.this.handleCommandEnd(false);
                } else if (AbstractCommand.this.commandState.compareAndSet(AbstractCommand.CommandState.USER_CODE_EXECUTED, AbstractCommand.CommandState.UNSUBSCRIBED)) {
                    if (!AbstractCommand.this.executionResult.containsTerminalEvent()) {
                        AbstractCommand.this.eventNotifier.markEvent(HystrixEventType.CANCELLED, AbstractCommand.this.commandKey);

                        try {
                            AbstractCommand.this.executionHook.onUnsubscribe(AbstractCommand.this);
                        } catch (Throwable var2) {
                            AbstractCommand.logger.warn("Error calling HystrixCommandExecutionHook.onUnsubscribe", var2);
                        }

                        AbstractCommand.this.executionResultAtTimeOfCancellation = AbstractCommand.this.executionResult.addEvent((int)(System.currentTimeMillis() - AbstractCommand.this.commandStartTimestamp), HystrixEventType.CANCELLED);
                    }

                    AbstractCommand.this.handleCommandEnd(true);
                }

            }
        };
        final Func0<Observable<R>> applyHystrixSemantics = new Func0<Observable<R>>() {
            public Observable<R> call() {
                return ((AbstractCommand.CommandState)AbstractCommand.this.commandState.get()).equals(AbstractCommand.CommandState.UNSUBSCRIBED) ? Observable.never() : AbstractCommand.this.applyHystrixSemantics(AbstractCommand.this);
            }
        };
        final Func1<R, R> wrapWithAllOnNextHooks = new Func1<R, R>() {
            public R call(R r) {
                Object afterFirstApplication = r;

                try {
                    afterFirstApplication = AbstractCommand.this.executionHook.onComplete(AbstractCommand.this, r);
                } catch (Throwable var5) {
                    AbstractCommand.logger.warn("Error calling HystrixCommandExecutionHook.onComplete", var5);
                }

                try {
                    return AbstractCommand.this.executionHook.onEmit(AbstractCommand.this, afterFirstApplication);
                } catch (Throwable var4) {
                    AbstractCommand.logger.warn("Error calling HystrixCommandExecutionHook.onEmit", var4);
                    return afterFirstApplication;
                }
            }
        };
        final Action0 fireOnCompletedHook = new Action0() {
            public void call() {
                try {
                    AbstractCommand.this.executionHook.onSuccess(AbstractCommand.this);
                } catch (Throwable var2) {
                    AbstractCommand.logger.warn("Error calling HystrixCommandExecutionHook.onSuccess", var2);
                }

            }
        };
        return Observable.defer(new Func0<Observable<R>>() {
            public Observable<R> call() {
                if (!AbstractCommand.this.commandState.compareAndSet(AbstractCommand.CommandState.NOT_STARTED, AbstractCommand.CommandState.OBSERVABLE_CHAIN_CREATED)) {
                    IllegalStateException ex = new IllegalStateException("This instance can only be executed once. Please instantiate a new instance.");
                    throw new HystrixRuntimeException(FailureType.BAD_REQUEST_EXCEPTION, AbstractCommand.this.getClass(), AbstractCommand.this.getLogMessagePrefix() + " command executed multiple times - this is not permitted.", ex, (Throwable)null);
                } else {
                    AbstractCommand.this.commandStartTimestamp = System.currentTimeMillis();
                    if ((Boolean)AbstractCommand.this.properties.requestLogEnabled().get() && AbstractCommand.this.currentRequestLog != null) {
                        AbstractCommand.this.currentRequestLog.addExecutedCommand(AbstractCommand.this);
                    }

                    boolean requestCacheEnabled = AbstractCommand.this.isRequestCachingEnabled();
                    String cacheKey = AbstractCommand.this.getCacheKey();
                    if (requestCacheEnabled) {
                        HystrixCommandResponseFromCache<R> fromCachex = (HystrixCommandResponseFromCache)AbstractCommand.this.requestCache.get(cacheKey);
                        if (fromCachex != null) {
                            AbstractCommand.this.isResponseFromCache = true;
                            return AbstractCommand.this.handleRequestCacheHitAndEmitValues(fromCachex, AbstractCommand.this);
                        }
                    }

                    Observable<R> hystrixObservable = Observable.defer(applyHystrixSemantics).map(wrapWithAllOnNextHooks);
                    Observable afterCache;
                    if (requestCacheEnabled && cacheKey != null) {
                        HystrixCachedObservable<R> toCache = HystrixCachedObservable.from(hystrixObservable, AbstractCommand.this);
                        HystrixCommandResponseFromCache<R> fromCache = (HystrixCommandResponseFromCache)AbstractCommand.this.requestCache.putIfAbsent(cacheKey, toCache);
                        if (fromCache != null) {
                            toCache.unsubscribe();
                            AbstractCommand.this.isResponseFromCache = true;
                            return AbstractCommand.this.handleRequestCacheHitAndEmitValues(fromCache, AbstractCommand.this);
                        }

                        afterCache = toCache.toObservable();
                    } else {
                        afterCache = hystrixObservable;
                    }

                    return afterCache.doOnTerminate(terminateCommandCleanup).doOnUnsubscribe(unsubscribeCommandCleanup).doOnCompleted(fireOnCompletedHook);
                }
            }
        });
    }

    private Observable<R> applyHystrixSemantics(AbstractCommand<R> _cmd) {
        this.executionHook.onStart(_cmd);
        if (this.circuitBreaker.allowRequest()) {
            final AbstractCommand.TryableSemaphore executionSemaphore = this.getExecutionSemaphore();
            final AtomicBoolean semaphoreHasBeenReleased = new AtomicBoolean(false);
            Action0 singleSemaphoreRelease = new Action0() {
                public void call() {
                    if (semaphoreHasBeenReleased.compareAndSet(false, true)) {
                        executionSemaphore.release();
                    }

                }
            };
            Action1<Throwable> markExceptionThrown = new Action1<Throwable>() {
                public void call(Throwable t) {
                    AbstractCommand.this.eventNotifier.markEvent(HystrixEventType.EXCEPTION_THROWN, AbstractCommand.this.commandKey);
                }
            };
            if (executionSemaphore.tryAcquire()) {
                try {
                    this.executionResult = this.executionResult.setInvocationStartTime(System.currentTimeMillis());
                    return this.executeCommandAndObserve(_cmd).doOnError(markExceptionThrown).doOnTerminate(singleSemaphoreRelease).doOnUnsubscribe(singleSemaphoreRelease);
                } catch (RuntimeException var7) {
                    return Observable.error(var7);
                }
            } else {
                return this.handleSemaphoreRejectionViaFallback();
            }
        } else {
            return this.handleShortCircuitViaFallback();
        }
    }

    protected abstract boolean commandIsScalar();

    private Observable<R> executeCommandAndObserve(AbstractCommand<R> _cmd) {
        final HystrixRequestContext currentRequestContext = HystrixRequestContext.getContextForCurrentThread();
        Action1<R> markEmits = new Action1<R>() {
            public void call(R r) {
                if (AbstractCommand.this.shouldOutputOnNextEvents()) {
                    AbstractCommand.this.executionResult = AbstractCommand.this.executionResult.addEvent(HystrixEventType.EMIT);
                    AbstractCommand.this.eventNotifier.markEvent(HystrixEventType.EMIT, AbstractCommand.this.commandKey);
                }

                if (AbstractCommand.this.commandIsScalar()) {
                    long latency = System.currentTimeMillis() - AbstractCommand.this.executionResult.getStartTimestamp();
                    AbstractCommand.this.eventNotifier.markCommandExecution(AbstractCommand.this.getCommandKey(), (ExecutionIsolationStrategy)AbstractCommand.this.properties.executionIsolationStrategy().get(), (int)latency, AbstractCommand.this.executionResult.getOrderedList());
                    AbstractCommand.this.eventNotifier.markEvent(HystrixEventType.SUCCESS, AbstractCommand.this.commandKey);
                    AbstractCommand.this.executionResult = AbstractCommand.this.executionResult.addEvent((int)latency, HystrixEventType.SUCCESS);
                    AbstractCommand.this.circuitBreaker.markSuccess();
                }

            }
        };
        Action0 markOnCompleted = new Action0() {
            public void call() {
                if (!AbstractCommand.this.commandIsScalar()) {
                    long latency = System.currentTimeMillis() - AbstractCommand.this.executionResult.getStartTimestamp();
                    AbstractCommand.this.eventNotifier.markCommandExecution(AbstractCommand.this.getCommandKey(), (ExecutionIsolationStrategy)AbstractCommand.this.properties.executionIsolationStrategy().get(), (int)latency, AbstractCommand.this.executionResult.getOrderedList());
                    AbstractCommand.this.eventNotifier.markEvent(HystrixEventType.SUCCESS, AbstractCommand.this.commandKey);
                    AbstractCommand.this.executionResult = AbstractCommand.this.executionResult.addEvent((int)latency, HystrixEventType.SUCCESS);
                    AbstractCommand.this.circuitBreaker.markSuccess();
                }

            }
        };
        Func1<Throwable, Observable<R>> handleFallback = new Func1<Throwable, Observable<R>>() {
            public Observable<R> call(Throwable t) {
                Exception e = AbstractCommand.this.getExceptionFromThrowable(t);
                AbstractCommand.this.executionResult = AbstractCommand.this.executionResult.setExecutionException(e);
                if (e instanceof RejectedExecutionException) {
                    return AbstractCommand.this.handleThreadPoolRejectionViaFallback(e);
                } else if (t instanceof HystrixTimeoutException) {
                    return AbstractCommand.this.handleTimeoutViaFallback();
                } else if (t instanceof HystrixBadRequestException) {
                    return AbstractCommand.this.handleBadRequestByEmittingError(e);
                } else if (e instanceof HystrixBadRequestException) {
                    AbstractCommand.this.eventNotifier.markEvent(HystrixEventType.BAD_REQUEST, AbstractCommand.this.commandKey);
                    return Observable.error(e);
                } else {
                    return AbstractCommand.this.handleFailureViaFallback(e);
                }
            }
        };
        Action1<Notification<? super R>> setRequestContext = new Action1<Notification<? super R>>() {
            public void call(Notification<? super R> rNotification) {
                AbstractCommand.setRequestContextIfNeeded(currentRequestContext);
            }
        };
        Observable execution;
        if ((Boolean)this.properties.executionTimeoutEnabled().get()) {
            execution = this.executeCommandWithSpecifiedIsolation(_cmd).lift(new AbstractCommand.HystrixObservableTimeoutOperator(_cmd));
        } else {
            execution = this.executeCommandWithSpecifiedIsolation(_cmd);
        }

        return execution.doOnNext(markEmits).doOnCompleted(markOnCompleted).onErrorResumeNext(handleFallback).doOnEach(setRequestContext);
    }

    private Observable<R> executeCommandWithSpecifiedIsolation(final AbstractCommand<R> _cmd) {
        return this.properties.executionIsolationStrategy().get() == ExecutionIsolationStrategy.THREAD ? Observable.defer(new Func0<Observable<R>>() {
            public Observable<R> call() {
                AbstractCommand.this.executionResult = AbstractCommand.this.executionResult.setExecutionOccurred();
                if (!AbstractCommand.this.commandState.compareAndSet(AbstractCommand.CommandState.OBSERVABLE_CHAIN_CREATED, AbstractCommand.CommandState.USER_CODE_EXECUTED)) {
                    return Observable.error(new IllegalStateException("execution attempted while in state : " + ((AbstractCommand.CommandState)AbstractCommand.this.commandState.get()).name()));
                } else {
                    AbstractCommand.this.metrics.markCommandStart(AbstractCommand.this.commandKey, AbstractCommand.this.threadPoolKey, ExecutionIsolationStrategy.THREAD);
                    if (AbstractCommand.this.isCommandTimedOut.get() == AbstractCommand.TimedOutStatus.TIMED_OUT) {
                        return Observable.error(new RuntimeException("timed out before executing run()"));
                    } else if (AbstractCommand.this.threadState.compareAndSet(AbstractCommand.ThreadState.NOT_USING_THREAD, AbstractCommand.ThreadState.STARTED)) {
                        HystrixCounters.incrementGlobalConcurrentThreads();
                        AbstractCommand.this.threadPool.markThreadExecution();
                        AbstractCommand.this.endCurrentThreadExecutingCommand = Hystrix.startCurrentThreadExecutingCommand(AbstractCommand.this.getCommandKey());
                        AbstractCommand.this.executionResult = AbstractCommand.this.executionResult.setExecutedInThread();

                        try {
                            AbstractCommand.this.executionHook.onThreadStart(_cmd);
                            AbstractCommand.this.executionHook.onRunStart(_cmd);
                            AbstractCommand.this.executionHook.onExecutionStart(_cmd);
                            return AbstractCommand.this.getUserExecutionObservable(_cmd);
                        } catch (Throwable var2) {
                            return Observable.error(var2);
                        }
                    } else {
                        return Observable.error(new RuntimeException("unsubscribed before executing run()"));
                    }
                }
            }
        }).doOnTerminate(new Action0() {
            public void call() {
                if (AbstractCommand.this.threadState.compareAndSet(AbstractCommand.ThreadState.STARTED, AbstractCommand.ThreadState.TERMINAL)) {
                    AbstractCommand.this.handleThreadEnd(_cmd);
                }

                if (AbstractCommand.this.threadState.compareAndSet(AbstractCommand.ThreadState.NOT_USING_THREAD, AbstractCommand.ThreadState.TERMINAL)) {
                }

            }
        }).doOnUnsubscribe(new Action0() {
            public void call() {
                if (AbstractCommand.this.threadState.compareAndSet(AbstractCommand.ThreadState.STARTED, AbstractCommand.ThreadState.UNSUBSCRIBED)) {
                    AbstractCommand.this.handleThreadEnd(_cmd);
                }

                if (AbstractCommand.this.threadState.compareAndSet(AbstractCommand.ThreadState.NOT_USING_THREAD, AbstractCommand.ThreadState.UNSUBSCRIBED)) {
                }

            }
        }).subscribeOn(this.threadPool.getScheduler(new Func0<Boolean>() {
            public Boolean call() {
                return (Boolean)AbstractCommand.this.properties.executionIsolationThreadInterruptOnTimeout().get() && _cmd.isCommandTimedOut.get() == AbstractCommand.TimedOutStatus.TIMED_OUT;
            }
        })) : Observable.defer(new Func0<Observable<R>>() {
            public Observable<R> call() {
                AbstractCommand.this.executionResult = AbstractCommand.this.executionResult.setExecutionOccurred();
                if (!AbstractCommand.this.commandState.compareAndSet(AbstractCommand.CommandState.OBSERVABLE_CHAIN_CREATED, AbstractCommand.CommandState.USER_CODE_EXECUTED)) {
                    return Observable.error(new IllegalStateException("execution attempted while in state : " + ((AbstractCommand.CommandState)AbstractCommand.this.commandState.get()).name()));
                } else {
                    AbstractCommand.this.metrics.markCommandStart(AbstractCommand.this.commandKey, AbstractCommand.this.threadPoolKey, ExecutionIsolationStrategy.SEMAPHORE);
                    AbstractCommand.this.endCurrentThreadExecutingCommand = Hystrix.startCurrentThreadExecutingCommand(AbstractCommand.this.getCommandKey());

                    try {
                        AbstractCommand.this.executionHook.onRunStart(_cmd);
                        AbstractCommand.this.executionHook.onExecutionStart(_cmd);
                        return AbstractCommand.this.getUserExecutionObservable(_cmd);
                    } catch (Throwable var2) {
                        return Observable.error(var2);
                    }
                }
            }
        });
    }

    private Observable<R> getFallbackOrThrowException(final AbstractCommand<R> _cmd, HystrixEventType eventType, final FailureType failureType, final String message, final Exception originalException) {
        final HystrixRequestContext requestContext = HystrixRequestContext.getContextForCurrentThread();
        long latency = System.currentTimeMillis() - this.executionResult.getStartTimestamp();
        this.executionResult = this.executionResult.addEvent((int)latency, eventType);
        Exception e;
        if (this.shouldNotBeWrapped(originalException)) {
            e = this.wrapWithOnErrorHook(failureType, originalException);
            return Observable.error(e);
        } else if (this.isUnrecoverable(originalException)) {
            logger.error("Unrecoverable Error for HystrixCommand so will throw HystrixRuntimeException and not apply fallback. ", originalException);
            e = this.wrapWithOnErrorHook(failureType, originalException);
            return Observable.error(new HystrixRuntimeException(failureType, this.getClass(), this.getLogMessagePrefix() + " " + message + " and encountered unrecoverable error.", e, (Throwable)null));
        } else {
            if (this.isRecoverableError(originalException)) {
                logger.warn("Recovered from java.lang.Error by serving Hystrix fallback", originalException);
            }

            if ((Boolean)this.properties.fallbackEnabled().get()) {
                Action1<Notification<? super R>> setRequestContext = new Action1<Notification<? super R>>() {
                    public void call(Notification<? super R> rNotification) {
                        AbstractCommand.setRequestContextIfNeeded(requestContext);
                    }
                };
                Action1<R> markFallbackEmit = new Action1<R>() {
                    public void call(R r) {
                        if (AbstractCommand.this.shouldOutputOnNextEvents()) {
                            AbstractCommand.this.executionResult = AbstractCommand.this.executionResult.addEvent(HystrixEventType.FALLBACK_EMIT);
                            AbstractCommand.this.eventNotifier.markEvent(HystrixEventType.FALLBACK_EMIT, AbstractCommand.this.commandKey);
                        }

                    }
                };
                Action0 markFallbackCompleted = new Action0() {
                    public void call() {
                        long latency = System.currentTimeMillis() - AbstractCommand.this.executionResult.getStartTimestamp();
                        AbstractCommand.this.eventNotifier.markEvent(HystrixEventType.FALLBACK_SUCCESS, AbstractCommand.this.commandKey);
                        AbstractCommand.this.executionResult = AbstractCommand.this.executionResult.addEvent((int)latency, HystrixEventType.FALLBACK_SUCCESS);
                    }
                };
                Func1<Throwable, Observable<R>> handleFallbackError = new Func1<Throwable, Observable<R>>() {
                    public Observable<R> call(Throwable t) {
                        Exception e = originalException;
                        Exception fe = AbstractCommand.this.getExceptionFromThrowable(t);
                        long latency;
                        if (fe instanceof UnsupportedOperationException) {
                            latency = System.currentTimeMillis() - AbstractCommand.this.executionResult.getStartTimestamp();
                            AbstractCommand.logger.debug("No fallback for HystrixCommand. ", fe);
                            AbstractCommand.this.eventNotifier.markEvent(HystrixEventType.FALLBACK_MISSING, AbstractCommand.this.commandKey);
                            AbstractCommand.this.executionResult = AbstractCommand.this.executionResult.addEvent((int)latency, HystrixEventType.FALLBACK_MISSING);
                            e = AbstractCommand.this.wrapWithOnErrorHook(failureType, e);
                            return Observable.error(new HystrixRuntimeException(failureType, _cmd.getClass(), AbstractCommand.this.getLogMessagePrefix() + " " + message + " and no fallback available.", e, fe));
                        } else {
                            latency = System.currentTimeMillis() - AbstractCommand.this.executionResult.getStartTimestamp();
                            AbstractCommand.logger.debug("HystrixCommand execution " + failureType.name() + " and fallback failed.", fe);
                            AbstractCommand.this.eventNotifier.markEvent(HystrixEventType.FALLBACK_FAILURE, AbstractCommand.this.commandKey);
                            AbstractCommand.this.executionResult = AbstractCommand.this.executionResult.addEvent((int)latency, HystrixEventType.FALLBACK_FAILURE);
                            e = AbstractCommand.this.wrapWithOnErrorHook(failureType, e);
                            return Observable.error(new HystrixRuntimeException(failureType, _cmd.getClass(), AbstractCommand.this.getLogMessagePrefix() + " " + message + " and fallback failed.", e, fe));
                        }
                    }
                };
                final AbstractCommand.TryableSemaphore fallbackSemaphore = this.getFallbackSemaphore();
                final AtomicBoolean semaphoreHasBeenReleased = new AtomicBoolean(false);
                Action0 singleSemaphoreRelease = new Action0() {
                    public void call() {
                        if (semaphoreHasBeenReleased.compareAndSet(false, true)) {
                            fallbackSemaphore.release();
                        }

                    }
                };
                if (fallbackSemaphore.tryAcquire()) {
                    Observable fallbackExecutionChain;
                    try {
                        if (this.isFallbackUserDefined()) {
                            this.executionHook.onFallbackStart(this);
                            fallbackExecutionChain = this.getFallbackObservable();
                        } else {
                            fallbackExecutionChain = this.getFallbackObservable();
                        }
                    } catch (Throwable var18) {
                        fallbackExecutionChain = Observable.error(var18);
                    }

                    return fallbackExecutionChain.doOnEach(setRequestContext).lift(new AbstractCommand.FallbackHookApplication(_cmd)).lift(new AbstractCommand.DeprecatedOnFallbackHookApplication(_cmd)).doOnNext(markFallbackEmit).doOnCompleted(markFallbackCompleted).onErrorResumeNext(handleFallbackError).doOnTerminate(singleSemaphoreRelease).doOnUnsubscribe(singleSemaphoreRelease);
                } else {
                    return this.handleFallbackRejectionByEmittingError();
                }
            } else {
                return this.handleFallbackDisabledByEmittingError(originalException, failureType, message);
            }
        }
    }

    private Observable<R> getUserExecutionObservable(AbstractCommand<R> _cmd) {
        Observable userObservable;
        try {
            userObservable = this.getExecutionObservable();
        } catch (Throwable var4) {
            userObservable = Observable.error(var4);
        }

        return userObservable.lift(new AbstractCommand.ExecutionHookApplication(_cmd)).lift(new AbstractCommand.DeprecatedOnRunHookApplication(_cmd));
    }

    private Observable<R> handleRequestCacheHitAndEmitValues(HystrixCommandResponseFromCache<R> fromCache, AbstractCommand<R> _cmd) {
        try {
            this.executionHook.onCacheHit(this);
        } catch (Throwable var4) {
            logger.warn("Error calling HystrixCommandExecutionHook.onCacheHit", var4);
        }

        return fromCache.toObservableWithStateCopiedInto(this).doOnTerminate(new Action0() {
            public void call() {
                if (AbstractCommand.this.commandState.compareAndSet(AbstractCommand.CommandState.OBSERVABLE_CHAIN_CREATED, AbstractCommand.CommandState.TERMINAL)) {
                    AbstractCommand.this.cleanUpAfterResponseFromCache(false);
                } else if (AbstractCommand.this.commandState.compareAndSet(AbstractCommand.CommandState.USER_CODE_EXECUTED, AbstractCommand.CommandState.TERMINAL)) {
                    AbstractCommand.this.cleanUpAfterResponseFromCache(true);
                }

            }
        }).doOnUnsubscribe(new Action0() {
            public void call() {
                if (AbstractCommand.this.commandState.compareAndSet(AbstractCommand.CommandState.OBSERVABLE_CHAIN_CREATED, AbstractCommand.CommandState.UNSUBSCRIBED)) {
                    AbstractCommand.this.cleanUpAfterResponseFromCache(false);
                } else if (AbstractCommand.this.commandState.compareAndSet(AbstractCommand.CommandState.USER_CODE_EXECUTED, AbstractCommand.CommandState.UNSUBSCRIBED)) {
                    AbstractCommand.this.cleanUpAfterResponseFromCache(true);
                }

            }
        });
    }

    private void cleanUpAfterResponseFromCache(boolean commandExecutionStarted) {
        Reference<TimerListener> tl = (Reference)this.timeoutTimer.get();
        if (tl != null) {
            tl.clear();
        }

        long latency = System.currentTimeMillis() - this.commandStartTimestamp;
        this.executionResult = this.executionResult.addEvent(-1, HystrixEventType.RESPONSE_FROM_CACHE).markUserThreadCompletion(latency).setNotExecutedInThread();
        ExecutionResult cacheOnlyForMetrics = ExecutionResult.from(new HystrixEventType[]{HystrixEventType.RESPONSE_FROM_CACHE}).markUserThreadCompletion(latency);
        this.metrics.markCommandDone(cacheOnlyForMetrics, this.commandKey, this.threadPoolKey, commandExecutionStarted);
        this.eventNotifier.markEvent(HystrixEventType.RESPONSE_FROM_CACHE, this.commandKey);
    }

    private void handleCommandEnd(boolean commandExecutionStarted) {
        Reference<TimerListener> tl = (Reference)this.timeoutTimer.get();
        if (tl != null) {
            tl.clear();
        }

        long userThreadLatency = System.currentTimeMillis() - this.commandStartTimestamp;
        this.executionResult = this.executionResult.markUserThreadCompletion((long)((int)userThreadLatency));
        if (this.executionResultAtTimeOfCancellation == null) {
            this.metrics.markCommandDone(this.executionResult, this.commandKey, this.threadPoolKey, commandExecutionStarted);
        } else {
            this.metrics.markCommandDone(this.executionResultAtTimeOfCancellation, this.commandKey, this.threadPoolKey, commandExecutionStarted);
        }

        if (this.endCurrentThreadExecutingCommand != null) {
            this.endCurrentThreadExecutingCommand.call();
        }

    }

    private Observable<R> handleSemaphoreRejectionViaFallback() {
        Exception semaphoreRejectionException = new RuntimeException("could not acquire a semaphore for execution");
        this.executionResult = this.executionResult.setExecutionException(semaphoreRejectionException);
        this.eventNotifier.markEvent(HystrixEventType.SEMAPHORE_REJECTED, this.commandKey);
        logger.debug("HystrixCommand Execution Rejection by Semaphore.");
        return this.getFallbackOrThrowException(this, HystrixEventType.SEMAPHORE_REJECTED, FailureType.REJECTED_SEMAPHORE_EXECUTION, "could not acquire a semaphore for execution", semaphoreRejectionException);
    }

    private Observable<R> handleShortCircuitViaFallback() {
        this.eventNotifier.markEvent(HystrixEventType.SHORT_CIRCUITED, this.commandKey);
        Exception shortCircuitException = new RuntimeException("Hystrix circuit short-circuited and is OPEN");
        this.executionResult = this.executionResult.setExecutionException(shortCircuitException);

        try {
            return this.getFallbackOrThrowException(this, HystrixEventType.SHORT_CIRCUITED, FailureType.SHORTCIRCUIT, "short-circuited", shortCircuitException);
        } catch (Exception var3) {
            return Observable.error(var3);
        }
    }

    private Observable<R> handleThreadPoolRejectionViaFallback(Exception underlying) {
        this.eventNotifier.markEvent(HystrixEventType.THREAD_POOL_REJECTED, this.commandKey);
        this.threadPool.markThreadRejection();
        return this.getFallbackOrThrowException(this, HystrixEventType.THREAD_POOL_REJECTED, FailureType.REJECTED_THREAD_EXECUTION, "could not be queued for execution", underlying);
    }

    private Observable<R> handleTimeoutViaFallback() {
        return this.getFallbackOrThrowException(this, HystrixEventType.TIMEOUT, FailureType.TIMEOUT, "timed-out", new TimeoutException());
    }

    private Observable<R> handleBadRequestByEmittingError(Exception underlying) {
        Exception toEmit = underlying;

        try {
            long executionLatency = System.currentTimeMillis() - this.executionResult.getStartTimestamp();
            this.eventNotifier.markEvent(HystrixEventType.BAD_REQUEST, this.commandKey);
            this.executionResult = this.executionResult.addEvent((int)executionLatency, HystrixEventType.BAD_REQUEST);
            Exception decorated = this.executionHook.onError(this, FailureType.BAD_REQUEST_EXCEPTION, underlying);
            if (decorated instanceof HystrixBadRequestException) {
                toEmit = decorated;
            } else {
                logger.warn("ExecutionHook.onError returned an exception that was not an instance of HystrixBadRequestException so will be ignored.", decorated);
            }
        } catch (Exception var6) {
            logger.warn("Error calling HystrixCommandExecutionHook.onError", var6);
        }

        return Observable.error(toEmit);
    }

    private Observable<R> handleFailureViaFallback(Exception underlying) {
        logger.debug("Error executing HystrixCommand.run(). Proceeding to fallback logic ...", underlying);
        this.eventNotifier.markEvent(HystrixEventType.FAILURE, this.commandKey);
        this.executionResult = this.executionResult.setException(underlying);
        return this.getFallbackOrThrowException(this, HystrixEventType.FAILURE, FailureType.COMMAND_EXCEPTION, "failed", underlying);
    }

    private Observable<R> handleFallbackRejectionByEmittingError() {
        long latencyWithFallback = System.currentTimeMillis() - this.executionResult.getStartTimestamp();
        this.eventNotifier.markEvent(HystrixEventType.FALLBACK_REJECTION, this.commandKey);
        this.executionResult = this.executionResult.addEvent((int)latencyWithFallback, HystrixEventType.FALLBACK_REJECTION);
        logger.debug("HystrixCommand Fallback Rejection.");
        return Observable.error(new HystrixRuntimeException(FailureType.REJECTED_SEMAPHORE_FALLBACK, this.getClass(), this.getLogMessagePrefix() + " fallback execution rejected.", (Exception)null, (Throwable)null));
    }

    private Observable<R> handleFallbackDisabledByEmittingError(Exception underlying, FailureType failureType, String message) {
        logger.debug("Fallback disabled for HystrixCommand so will throw HystrixRuntimeException. ", underlying);
        Exception wrapped = this.wrapWithOnErrorHook(failureType, underlying);
        return Observable.error(new HystrixRuntimeException(failureType, this.getClass(), this.getLogMessagePrefix() + " " + message + " and fallback disabled.", wrapped, (Throwable)null));
    }

    protected boolean shouldNotBeWrapped(Throwable underlying) {
        return underlying instanceof ExceptionNotWrappedByHystrix;
    }

    private boolean isUnrecoverable(Throwable t) {
        if (t != null && t.getCause() != null) {
            Throwable cause = t.getCause();
            if (cause instanceof StackOverflowError) {
                return true;
            }

            if (cause instanceof VirtualMachineError) {
                return true;
            }

            if (cause instanceof ThreadDeath) {
                return true;
            }

            if (cause instanceof LinkageError) {
                return true;
            }
        }

        return false;
    }

    private boolean isRecoverableError(Throwable t) {
        if (t != null && t.getCause() != null) {
            Throwable cause = t.getCause();
            if (cause instanceof Error) {
                return !this.isUnrecoverable(t);
            }
        }

        return false;
    }

    protected void handleThreadEnd(AbstractCommand<R> _cmd) {
        HystrixCounters.decrementGlobalConcurrentThreads();
        this.threadPool.markThreadCompletion();

        try {
            this.executionHook.onThreadComplete(_cmd);
        } catch (Throwable var3) {
            logger.warn("Error calling HystrixCommandExecutionHook.onThreadComplete", var3);
        }

    }

    protected boolean shouldOutputOnNextEvents() {
        return false;
    }

    private static void setRequestContextIfNeeded(HystrixRequestContext currentRequestContext) {
        if (!HystrixRequestContext.isCurrentThreadInitialized()) {
            HystrixRequestContext.setContextOnCurrentThread(currentRequestContext);
        }

    }

    protected AbstractCommand.TryableSemaphore getFallbackSemaphore() {
        if (this.fallbackSemaphoreOverride == null) {
            AbstractCommand.TryableSemaphore _s = (AbstractCommand.TryableSemaphore)fallbackSemaphorePerCircuit.get(this.commandKey.name());
            if (_s == null) {
                fallbackSemaphorePerCircuit.putIfAbsent(this.commandKey.name(), new AbstractCommand.TryableSemaphoreActual(this.properties.fallbackIsolationSemaphoreMaxConcurrentRequests()));
                return (AbstractCommand.TryableSemaphore)fallbackSemaphorePerCircuit.get(this.commandKey.name());
            } else {
                return _s;
            }
        } else {
            return this.fallbackSemaphoreOverride;
        }
    }

    protected AbstractCommand.TryableSemaphore getExecutionSemaphore() {
        if (this.properties.executionIsolationStrategy().get() == ExecutionIsolationStrategy.SEMAPHORE) {
            if (this.executionSemaphoreOverride == null) {
                AbstractCommand.TryableSemaphore _s = (AbstractCommand.TryableSemaphore)executionSemaphorePerCircuit.get(this.commandKey.name());
                if (_s == null) {
                    executionSemaphorePerCircuit.putIfAbsent(this.commandKey.name(), new AbstractCommand.TryableSemaphoreActual(this.properties.executionIsolationSemaphoreMaxConcurrentRequests()));
                    return (AbstractCommand.TryableSemaphore)executionSemaphorePerCircuit.get(this.commandKey.name());
                } else {
                    return _s;
                }
            } else {
                return this.executionSemaphoreOverride;
            }
        } else {
            return AbstractCommand.TryableSemaphoreNoOp.DEFAULT;
        }
    }

    /** @deprecated */
    @Deprecated
    protected abstract String getFallbackMethodName();

    protected abstract boolean isFallbackUserDefined();

    public HystrixCommandGroupKey getCommandGroup() {
        return this.commandGroup;
    }

    public HystrixCommandKey getCommandKey() {
        return this.commandKey;
    }

    public HystrixThreadPoolKey getThreadPoolKey() {
        return this.threadPoolKey;
    }

    HystrixCircuitBreaker getCircuitBreaker() {
        return this.circuitBreaker;
    }

    public HystrixCommandMetrics getMetrics() {
        return this.metrics;
    }

    public HystrixCommandProperties getProperties() {
        return this.properties;
    }

    private Exception wrapWithOnExecutionErrorHook(Throwable t) {
        Exception e = this.getExceptionFromThrowable(t);

        try {
            return this.executionHook.onExecutionError(this, e);
        } catch (Throwable var4) {
            logger.warn("Error calling HystrixCommandExecutionHook.onExecutionError", var4);
            return e;
        }
    }

    private Exception wrapWithOnFallbackErrorHook(Throwable t) {
        Exception e = this.getExceptionFromThrowable(t);

        try {
            return this.isFallbackUserDefined() ? this.executionHook.onFallbackError(this, e) : e;
        } catch (Throwable var4) {
            logger.warn("Error calling HystrixCommandExecutionHook.onFallbackError", var4);
            return e;
        }
    }

    private Exception wrapWithOnErrorHook(FailureType failureType, Throwable t) {
        Exception e = this.getExceptionFromThrowable(t);

        try {
            return this.executionHook.onError(this, failureType, e);
        } catch (Throwable var5) {
            logger.warn("Error calling HystrixCommandExecutionHook.onError", var5);
            return e;
        }
    }

    private R wrapWithOnExecutionEmitHook(R r) {
        try {
            return this.executionHook.onExecutionEmit(this, r);
        } catch (Throwable var3) {
            logger.warn("Error calling HystrixCommandExecutionHook.onExecutionEmit", var3);
            return r;
        }
    }

    private R wrapWithOnFallbackEmitHook(R r) {
        try {
            return this.executionHook.onFallbackEmit(this, r);
        } catch (Throwable var3) {
            logger.warn("Error calling HystrixCommandExecutionHook.onFallbackEmit", var3);
            return r;
        }
    }

    private R wrapWithOnEmitHook(R r) {
        try {
            return this.executionHook.onEmit(this, r);
        } catch (Throwable var3) {
            logger.warn("Error calling HystrixCommandExecutionHook.onEmit", var3);
            return r;
        }
    }

    protected Throwable decomposeException(Exception e) {
        if (e instanceof IllegalStateException) {
            return (IllegalStateException)e;
        } else if (e instanceof HystrixBadRequestException) {
            return (Throwable)(this.shouldNotBeWrapped(e.getCause()) ? e.getCause() : (HystrixBadRequestException)e);
        } else if (e.getCause() instanceof HystrixBadRequestException) {
            return (Throwable)(this.shouldNotBeWrapped(e.getCause().getCause()) ? e.getCause().getCause() : (HystrixBadRequestException)e.getCause());
        } else if (e instanceof HystrixRuntimeException) {
            return (HystrixRuntimeException)e;
        } else if (e.getCause() instanceof HystrixRuntimeException) {
            return (HystrixRuntimeException)e.getCause();
        } else if (this.shouldNotBeWrapped(e)) {
            return e;
        } else if (this.shouldNotBeWrapped(e.getCause())) {
            return e.getCause();
        } else {
            String message = this.getLogMessagePrefix() + " failed while executing.";
            logger.debug(message, e);
            return new HystrixRuntimeException(FailureType.COMMAND_EXCEPTION, this.getClass(), message, e, (Throwable)null);
        }
    }

    protected String getCacheKey() {
        return null;
    }

    public String getPublicCacheKey() {
        return this.getCacheKey();
    }

    protected boolean isRequestCachingEnabled() {
        return (Boolean)this.properties.requestCacheEnabled().get() && this.getCacheKey() != null;
    }

    protected String getLogMessagePrefix() {
        return this.getCommandKey().name();
    }

    public boolean isCircuitBreakerOpen() {
        return (Boolean)this.properties.circuitBreakerForceOpen().get() || !(Boolean)this.properties.circuitBreakerForceClosed().get() && this.circuitBreaker.isOpen();
    }

    public boolean isExecutionComplete() {
        return this.commandState.get() == AbstractCommand.CommandState.TERMINAL;
    }

    public boolean isExecutedInThread() {
        return this.getCommandResult().isExecutedInThread();
    }

    public boolean isSuccessfulExecution() {
        return this.getCommandResult().getEventCounts().contains(HystrixEventType.SUCCESS);
    }

    public boolean isFailedExecution() {
        return this.getCommandResult().getEventCounts().contains(HystrixEventType.FAILURE);
    }

    public Throwable getFailedExecutionException() {
        return this.executionResult.getException();
    }

    public Throwable getExecutionException() {
        return this.executionResult.getExecutionException();
    }

    public boolean isResponseFromFallback() {
        return this.getCommandResult().getEventCounts().contains(HystrixEventType.FALLBACK_SUCCESS);
    }

    public boolean isResponseTimedOut() {
        return this.getCommandResult().getEventCounts().contains(HystrixEventType.TIMEOUT);
    }

    public boolean isResponseShortCircuited() {
        return this.getCommandResult().getEventCounts().contains(HystrixEventType.SHORT_CIRCUITED);
    }

    public boolean isResponseFromCache() {
        return this.isResponseFromCache;
    }

    public boolean isResponseSemaphoreRejected() {
        return this.getCommandResult().isResponseSemaphoreRejected();
    }

    public boolean isResponseThreadPoolRejected() {
        return this.getCommandResult().isResponseThreadPoolRejected();
    }

    public boolean isResponseRejected() {
        return this.getCommandResult().isResponseRejected();
    }

    public List<HystrixEventType> getExecutionEvents() {
        return this.getCommandResult().getOrderedList();
    }

    private ExecutionResult getCommandResult() {
        ExecutionResult resultToReturn;
        if (this.executionResultAtTimeOfCancellation == null) {
            resultToReturn = this.executionResult;
        } else {
            resultToReturn = this.executionResultAtTimeOfCancellation;
        }

        if (this.isResponseFromCache) {
            resultToReturn = resultToReturn.addEvent(HystrixEventType.RESPONSE_FROM_CACHE);
        }

        return resultToReturn;
    }

    public int getNumberEmissions() {
        return this.getCommandResult().getEventCounts().getCount(HystrixEventType.EMIT);
    }

    public int getNumberFallbackEmissions() {
        return this.getCommandResult().getEventCounts().getCount(HystrixEventType.FALLBACK_EMIT);
    }

    public int getNumberCollapsed() {
        return this.getCommandResult().getEventCounts().getCount(HystrixEventType.COLLAPSED);
    }

    public HystrixCollapserKey getOriginatingCollapserKey() {
        return this.executionResult.getCollapserKey();
    }

    public int getExecutionTimeInMilliseconds() {
        return this.getCommandResult().getExecutionLatency();
    }

    public long getCommandRunStartTimeInNanos() {
        return this.executionResult.getCommandRunStartTimeInNanos();
    }

    public EventCounts getEventCounts() {
        return this.getCommandResult().getEventCounts();
    }

    protected Exception getExceptionFromThrowable(Throwable t) {
        Exception e;
        if (t instanceof Exception) {
            e = (Exception)t;
        } else {
            e = new Exception("Throwable caught while executing.", t);
        }

        return e;
    }

    private static class ExecutionHookDeprecationWrapper extends HystrixCommandExecutionHook {
        private final HystrixCommandExecutionHook actual;

        ExecutionHookDeprecationWrapper(HystrixCommandExecutionHook actual) {
            this.actual = actual;
        }

        public <T> T onEmit(HystrixInvokable<T> commandInstance, T value) {
            return this.actual.onEmit(commandInstance, value);
        }

        public <T> void onSuccess(HystrixInvokable<T> commandInstance) {
            this.actual.onSuccess(commandInstance);
        }

        public <T> void onExecutionStart(HystrixInvokable<T> commandInstance) {
            this.actual.onExecutionStart(commandInstance);
        }

        public <T> T onExecutionEmit(HystrixInvokable<T> commandInstance, T value) {
            return this.actual.onExecutionEmit(commandInstance, value);
        }

        public <T> Exception onExecutionError(HystrixInvokable<T> commandInstance, Exception e) {
            return this.actual.onExecutionError(commandInstance, e);
        }

        public <T> void onExecutionSuccess(HystrixInvokable<T> commandInstance) {
            this.actual.onExecutionSuccess(commandInstance);
        }

        public <T> T onFallbackEmit(HystrixInvokable<T> commandInstance, T value) {
            return this.actual.onFallbackEmit(commandInstance, value);
        }

        public <T> void onFallbackSuccess(HystrixInvokable<T> commandInstance) {
            this.actual.onFallbackSuccess(commandInstance);
        }

        /** @deprecated */
        @Deprecated
        public <T> void onRunStart(HystrixCommand<T> commandInstance) {
            this.actual.onRunStart(commandInstance);
        }

        public <T> void onRunStart(HystrixInvokable<T> commandInstance) {
            HystrixCommand<T> c = this.getHystrixCommandFromAbstractIfApplicable(commandInstance);
            if (c != null) {
                this.onRunStart(c);
            }

            this.actual.onRunStart(commandInstance);
        }

        /** @deprecated */
        @Deprecated
        public <T> T onRunSuccess(HystrixCommand<T> commandInstance, T response) {
            return this.actual.onRunSuccess(commandInstance, response);
        }

        /** @deprecated */
        @Deprecated
        public <T> T onRunSuccess(HystrixInvokable<T> commandInstance, T response) {
            HystrixCommand<T> c = this.getHystrixCommandFromAbstractIfApplicable(commandInstance);
            if (c != null) {
                response = this.onRunSuccess(c, response);
            }

            return this.actual.onRunSuccess(commandInstance, response);
        }

        /** @deprecated */
        @Deprecated
        public <T> Exception onRunError(HystrixCommand<T> commandInstance, Exception e) {
            return this.actual.onRunError(commandInstance, e);
        }

        /** @deprecated */
        @Deprecated
        public <T> Exception onRunError(HystrixInvokable<T> commandInstance, Exception e) {
            HystrixCommand<T> c = this.getHystrixCommandFromAbstractIfApplicable(commandInstance);
            if (c != null) {
                e = this.onRunError(c, e);
            }

            return this.actual.onRunError(commandInstance, e);
        }

        /** @deprecated */
        @Deprecated
        public <T> void onFallbackStart(HystrixCommand<T> commandInstance) {
            this.actual.onFallbackStart(commandInstance);
        }

        public <T> void onFallbackStart(HystrixInvokable<T> commandInstance) {
            HystrixCommand<T> c = this.getHystrixCommandFromAbstractIfApplicable(commandInstance);
            if (c != null) {
                this.onFallbackStart(c);
            }

            this.actual.onFallbackStart(commandInstance);
        }

        /** @deprecated */
        @Deprecated
        public <T> T onFallbackSuccess(HystrixCommand<T> commandInstance, T fallbackResponse) {
            return this.actual.onFallbackSuccess(commandInstance, fallbackResponse);
        }

        /** @deprecated */
        @Deprecated
        public <T> T onFallbackSuccess(HystrixInvokable<T> commandInstance, T fallbackResponse) {
            HystrixCommand<T> c = this.getHystrixCommandFromAbstractIfApplicable(commandInstance);
            if (c != null) {
                fallbackResponse = this.onFallbackSuccess(c, fallbackResponse);
            }

            return this.actual.onFallbackSuccess(commandInstance, fallbackResponse);
        }

        /** @deprecated */
        @Deprecated
        public <T> Exception onFallbackError(HystrixCommand<T> commandInstance, Exception e) {
            return this.actual.onFallbackError(commandInstance, e);
        }

        public <T> Exception onFallbackError(HystrixInvokable<T> commandInstance, Exception e) {
            HystrixCommand<T> c = this.getHystrixCommandFromAbstractIfApplicable(commandInstance);
            if (c != null) {
                e = this.onFallbackError(c, e);
            }

            return this.actual.onFallbackError(commandInstance, e);
        }

        /** @deprecated */
        @Deprecated
        public <T> void onStart(HystrixCommand<T> commandInstance) {
            this.actual.onStart(commandInstance);
        }

        public <T> void onStart(HystrixInvokable<T> commandInstance) {
            HystrixCommand<T> c = this.getHystrixCommandFromAbstractIfApplicable(commandInstance);
            if (c != null) {
                this.onStart(c);
            }

            this.actual.onStart(commandInstance);
        }

        /** @deprecated */
        @Deprecated
        public <T> T onComplete(HystrixCommand<T> commandInstance, T response) {
            return this.actual.onComplete(commandInstance, response);
        }

        /** @deprecated */
        @Deprecated
        public <T> T onComplete(HystrixInvokable<T> commandInstance, T response) {
            HystrixCommand<T> c = this.getHystrixCommandFromAbstractIfApplicable(commandInstance);
            if (c != null) {
                response = this.onComplete(c, response);
            }

            return this.actual.onComplete(commandInstance, response);
        }

        /** @deprecated */
        @Deprecated
        public <T> Exception onError(HystrixCommand<T> commandInstance, FailureType failureType, Exception e) {
            return this.actual.onError(commandInstance, failureType, e);
        }

        public <T> Exception onError(HystrixInvokable<T> commandInstance, FailureType failureType, Exception e) {
            HystrixCommand<T> c = this.getHystrixCommandFromAbstractIfApplicable(commandInstance);
            if (c != null) {
                e = this.onError(c, failureType, e);
            }

            return this.actual.onError(commandInstance, failureType, e);
        }

        /** @deprecated */
        @Deprecated
        public <T> void onThreadStart(HystrixCommand<T> commandInstance) {
            this.actual.onThreadStart(commandInstance);
        }

        public <T> void onThreadStart(HystrixInvokable<T> commandInstance) {
            HystrixCommand<T> c = this.getHystrixCommandFromAbstractIfApplicable(commandInstance);
            if (c != null) {
                this.onThreadStart(c);
            }

            this.actual.onThreadStart(commandInstance);
        }

        /** @deprecated */
        @Deprecated
        public <T> void onThreadComplete(HystrixCommand<T> commandInstance) {
            this.actual.onThreadComplete(commandInstance);
        }

        public <T> void onThreadComplete(HystrixInvokable<T> commandInstance) {
            HystrixCommand<T> c = this.getHystrixCommandFromAbstractIfApplicable(commandInstance);
            if (c != null) {
                this.onThreadComplete(c);
            }

            this.actual.onThreadComplete(commandInstance);
        }

        public <T> void onCacheHit(HystrixInvokable<T> commandInstance) {
            this.actual.onCacheHit(commandInstance);
        }

        public <T> void onUnsubscribe(HystrixInvokable<T> commandInstance) {
            this.actual.onUnsubscribe(commandInstance);
        }

        private <T> HystrixCommand<T> getHystrixCommandFromAbstractIfApplicable(HystrixInvokable<T> commandInstance) {
            return commandInstance instanceof HystrixCommand ? (HystrixCommand)commandInstance : null;
        }
    }

    interface TryableSemaphore {
        boolean tryAcquire();

        void release();

        int getNumberOfPermitsUsed();
    }

    static class TryableSemaphoreNoOp implements AbstractCommand.TryableSemaphore {
        public static final AbstractCommand.TryableSemaphore DEFAULT = new AbstractCommand.TryableSemaphoreNoOp();

        TryableSemaphoreNoOp() {
        }

        public boolean tryAcquire() {
            return true;
        }

        public void release() {
        }

        public int getNumberOfPermitsUsed() {
            return 0;
        }
    }

    static class TryableSemaphoreActual implements AbstractCommand.TryableSemaphore {
        protected final HystrixProperty<Integer> numberOfPermits;
        private final AtomicInteger count = new AtomicInteger(0);

        public TryableSemaphoreActual(HystrixProperty<Integer> numberOfPermits) {
            this.numberOfPermits = numberOfPermits;
        }

        public boolean tryAcquire() {
            int currentCount = this.count.incrementAndGet();
            if (currentCount > (Integer)this.numberOfPermits.get()) {
                this.count.decrementAndGet();
                return false;
            } else {
                return true;
            }
        }

        public void release() {
            this.count.decrementAndGet();
        }

        public int getNumberOfPermitsUsed() {
            return this.count.get();
        }
    }

    /** @deprecated */
    @Deprecated
    private class DeprecatedOnFallbackHookApplication implements Operator<R, R> {
        private final HystrixInvokable<R> cmd;

        DeprecatedOnFallbackHookApplication(HystrixInvokable<R> cmd) {
            this.cmd = cmd;
        }

        public Subscriber<? super R> call(final Subscriber<? super R> subscriber) {
            return new Subscriber<R>(subscriber) {
                public void onCompleted() {
                    subscriber.onCompleted();
                }

                public void onError(Throwable t) {
                    subscriber.onError(t);
                }

                public void onNext(R r) {
                    try {
                        R wrappedValue = AbstractCommand.this.executionHook.onFallbackSuccess(DeprecatedOnFallbackHookApplication.this.cmd, r);
                        subscriber.onNext(wrappedValue);
                    } catch (Throwable var3) {
                        AbstractCommand.logger.warn("Error calling HystrixCommandExecutionHook.onFallbackSuccess", var3);
                        subscriber.onNext(r);
                    }

                }
            };
        }
    }

    /** @deprecated */
    @Deprecated
    private class DeprecatedOnRunHookApplication implements Operator<R, R> {
        private final HystrixInvokable<R> cmd;

        DeprecatedOnRunHookApplication(HystrixInvokable<R> cmd) {
            this.cmd = cmd;
        }

        public Subscriber<? super R> call(final Subscriber<? super R> subscriber) {
            return new Subscriber<R>(subscriber) {
                public void onCompleted() {
                    subscriber.onCompleted();
                }

                public void onError(Throwable t) {
                    Exception e = AbstractCommand.this.getExceptionFromThrowable(t);

                    try {
                        Exception wrappedEx = AbstractCommand.this.executionHook.onRunError(DeprecatedOnRunHookApplication.this.cmd, e);
                        subscriber.onError(wrappedEx);
                    } catch (Throwable var4) {
                        AbstractCommand.logger.warn("Error calling HystrixCommandExecutionHook.onRunError", var4);
                        subscriber.onError(e);
                    }

                }

                public void onNext(R r) {
                    try {
                        R wrappedValue = AbstractCommand.this.executionHook.onRunSuccess(DeprecatedOnRunHookApplication.this.cmd, r);
                        subscriber.onNext(wrappedValue);
                    } catch (Throwable var3) {
                        AbstractCommand.logger.warn("Error calling HystrixCommandExecutionHook.onRunSuccess", var3);
                        subscriber.onNext(r);
                    }

                }
            };
        }
    }

    private class FallbackHookApplication implements Operator<R, R> {
        private final HystrixInvokable<R> cmd;

        FallbackHookApplication(HystrixInvokable<R> cmd) {
            this.cmd = cmd;
        }

        public Subscriber<? super R> call(final Subscriber<? super R> subscriber) {
            return new Subscriber<R>(subscriber) {
                public void onCompleted() {
                    try {
                        AbstractCommand.this.executionHook.onFallbackSuccess(FallbackHookApplication.this.cmd);
                    } catch (Throwable var2) {
                        AbstractCommand.logger.warn("Error calling HystrixCommandExecutionHook.onFallbackSuccess", var2);
                    }

                    subscriber.onCompleted();
                }

                public void onError(Throwable e) {
                    Exception wrappedEx = AbstractCommand.this.wrapWithOnFallbackErrorHook(e);
                    subscriber.onError(wrappedEx);
                }

                public void onNext(R r) {
                    R wrappedValue = AbstractCommand.this.wrapWithOnFallbackEmitHook(r);
                    subscriber.onNext(wrappedValue);
                }
            };
        }
    }

    private class ExecutionHookApplication implements Operator<R, R> {
        private final HystrixInvokable<R> cmd;

        ExecutionHookApplication(HystrixInvokable<R> cmd) {
            this.cmd = cmd;
        }

        public Subscriber<? super R> call(final Subscriber<? super R> subscriber) {
            return new Subscriber<R>(subscriber) {
                public void onCompleted() {
                    try {
                        AbstractCommand.this.executionHook.onExecutionSuccess(ExecutionHookApplication.this.cmd);
                    } catch (Throwable var2) {
                        AbstractCommand.logger.warn("Error calling HystrixCommandExecutionHook.onExecutionSuccess", var2);
                    }

                    subscriber.onCompleted();
                }

                public void onError(Throwable e) {
                    Exception wrappedEx = AbstractCommand.this.wrapWithOnExecutionErrorHook(e);
                    subscriber.onError(wrappedEx);
                }

                public void onNext(R r) {
                    R wrappedValue = AbstractCommand.this.wrapWithOnExecutionEmitHook(r);
                    subscriber.onNext(wrappedValue);
                }
            };
        }
    }

    private static class HystrixObservableTimeoutOperator<R> implements Operator<R, R> {
        final AbstractCommand<R> originalCommand;

        public HystrixObservableTimeoutOperator(AbstractCommand<R> originalCommand) {
            this.originalCommand = originalCommand;
        }

        public Subscriber<? super R> call(final Subscriber<? super R> child) {
            final CompositeSubscription s = new CompositeSubscription();
            child.add(s);
            final HystrixContextRunnable timeoutRunnable = new HystrixContextRunnable(this.originalCommand.concurrencyStrategy, new Runnable() {
                public void run() {
                    child.onError(new HystrixTimeoutException());
                }
            });
            TimerListener listener = new TimerListener() {
                public void tick() {
                    if (HystrixObservableTimeoutOperator.this.originalCommand.isCommandTimedOut.compareAndSet(AbstractCommand.TimedOutStatus.NOT_EXECUTED, AbstractCommand.TimedOutStatus.TIMED_OUT)) {
                        HystrixObservableTimeoutOperator.this.originalCommand.eventNotifier.markEvent(HystrixEventType.TIMEOUT, HystrixObservableTimeoutOperator.this.originalCommand.commandKey);
                        s.unsubscribe();
                        timeoutRunnable.run();
                    }

                }

                public int getIntervalTimeInMilliseconds() {
                    return (Integer)HystrixObservableTimeoutOperator.this.originalCommand.properties.executionTimeoutInMilliseconds().get();
                }
            };
            final Reference<TimerListener> tl = HystrixTimer.getInstance().addTimerListener(listener);
            this.originalCommand.timeoutTimer.set(tl);
            Subscriber<R> parent = new Subscriber<R>() {
                public void onCompleted() {
                    if (this.isNotTimedOut()) {
                        tl.clear();
                        child.onCompleted();
                    }

                }

                public void onError(Throwable e) {
                    if (this.isNotTimedOut()) {
                        tl.clear();
                        child.onError(e);
                    }

                }

                public void onNext(R v) {
                    if (this.isNotTimedOut()) {
                        child.onNext(v);
                    }

                }

                private boolean isNotTimedOut() {
                    return HystrixObservableTimeoutOperator.this.originalCommand.isCommandTimedOut.get() == AbstractCommand.TimedOutStatus.COMPLETED || HystrixObservableTimeoutOperator.this.originalCommand.isCommandTimedOut.compareAndSet(AbstractCommand.TimedOutStatus.NOT_EXECUTED, AbstractCommand.TimedOutStatus.COMPLETED);
                }
            };
            s.add(parent);
            return parent;
        }
    }

    protected static enum ThreadState {
        NOT_USING_THREAD,
        STARTED,
        UNSUBSCRIBED,
        TERMINAL;

        private ThreadState() {
        }
    }

    protected static enum CommandState {
        NOT_STARTED,
        OBSERVABLE_CHAIN_CREATED,
        USER_CODE_EXECUTED,
        UNSUBSCRIBED,
        TERMINAL;

        private CommandState() {
        }
    }

    protected static enum TimedOutStatus {
        NOT_EXECUTED,
        COMPLETED,
        TIMED_OUT;

        private TimedOutStatus() {
        }
    }
}
