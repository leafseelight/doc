package org.springframework.transaction.interceptor;

import io.vavr.control.Try;
import java.lang.reflect.Method;
import java.util.Properties;
import java.util.concurrent.ConcurrentMap;
import kotlin.reflect.KFunction;
import kotlin.reflect.jvm.ReflectJvmMapping;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.BeanFactoryAnnotationUtils;
import org.springframework.core.KotlinDetector;
import org.springframework.core.NamedThreadLocal;
import org.springframework.core.ReactiveAdapter;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.lang.Nullable;
import org.springframework.transaction.NoTransactionException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.ReactiveTransaction;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.TransactionUsageException;
import org.springframework.transaction.reactive.TransactionContextManager;
import org.springframework.transaction.support.CallbackPreferringPlatformTransactionManager;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ConcurrentReferenceHashMap;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

// 事务切面支持类
public abstract class TransactionAspectSupport implements BeanFactoryAware, InitializingBean {
    private static final Object DEFAULT_TRANSACTION_MANAGER_KEY = new Object();
    private static final boolean vavrPresent = ClassUtils.isPresent("io.vavr.control.Try", TransactionAspectSupport.class.getClassLoader()); // 类加载路径中是否有io.vavr.control.Try类
    private static final boolean reactiveStreamsPresent = ClassUtils.isPresent("org.reactivestreams.Publisher", TransactionAspectSupport.class.getClassLoader()); // 类加载路径中是否有org.reactivestreams.Publisher类
    private static final ThreadLocal<TransactionAspectSupport.TransactionInfo> transactionInfoHolder = new NamedThreadLocal("Current aspect-driven transaction"); // 线程本地变量中保存事务相关信息
    protected final Log logger = LogFactory.getLog(this.getClass());
    @Nullable
    private final ReactiveAdapterRegistry reactiveAdapterRegistry;
    @Nullable
    private String transactionManagerBeanName;						// 事务管理器的beanName
    @Nullable
    private TransactionManager transactionManager;					// 事务管理器
    @Nullable
    private TransactionAttributeSource transactionAttributeSource;	// 事务定义属性来源
    @Nullable
    private BeanFactory beanFactory;
    private final ConcurrentMap<Object, TransactionManager> transactionManagerCache = new ConcurrentReferenceHashMap(4);
    private final ConcurrentMap<Method, TransactionAspectSupport.ReactiveTransactionSupport> transactionSupportCache = new ConcurrentReferenceHashMap(1024);

    @Nullable
    protected static TransactionAspectSupport.TransactionInfo currentTransactionInfo() throws NoTransactionException {
        return (TransactionAspectSupport.TransactionInfo)transactionInfoHolder.get();
    }

    public static TransactionStatus currentTransactionStatus() throws NoTransactionException {
        TransactionAspectSupport.TransactionInfo info = currentTransactionInfo();
        if (info != null && info.transactionStatus != null) {
            return info.transactionStatus;
        } else {
            throw new NoTransactionException("No transaction aspect-managed TransactionStatus in scope");
        }
    }

    protected TransactionAspectSupport() {
        if (reactiveStreamsPresent) {
            this.reactiveAdapterRegistry = ReactiveAdapterRegistry.getSharedInstance();
        } else {
            this.reactiveAdapterRegistry = null;
        }

    }

    public void setTransactionManagerBeanName(@Nullable String transactionManagerBeanName) {
        this.transactionManagerBeanName = transactionManagerBeanName;
    }

    @Nullable
    protected final String getTransactionManagerBeanName() {
        return this.transactionManagerBeanName;
    }

    public void setTransactionManager(@Nullable TransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    @Nullable
    public TransactionManager getTransactionManager() {
        return this.transactionManager;
    }

    public void setTransactionAttributes(Properties transactionAttributes) {
        NameMatchTransactionAttributeSource tas = new NameMatchTransactionAttributeSource();
        tas.setProperties(transactionAttributes);
        this.transactionAttributeSource = tas;
    }

    public void setTransactionAttributeSources(TransactionAttributeSource... transactionAttributeSources) {
        this.transactionAttributeSource = new CompositeTransactionAttributeSource(transactionAttributeSources);
    }

    public void setTransactionAttributeSource(@Nullable TransactionAttributeSource transactionAttributeSource) {
        this.transactionAttributeSource = transactionAttributeSource;
    }

    @Nullable
    public TransactionAttributeSource getTransactionAttributeSource() {
        return this.transactionAttributeSource;
    }

    public void setBeanFactory(@Nullable BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

	// BeanFactoryAware bean工厂感知接口的实现方法 为了获取bean工厂
    @Nullable
    protected final BeanFactory getBeanFactory() {
        return this.beanFactory;
    }

    public void afterPropertiesSet() {
        if (this.getTransactionManager() == null && this.beanFactory == null) {
            throw new IllegalStateException("Set the 'transactionManager' property or make sure to run within a BeanFactory containing a TransactionManager bean!");
        } else if (this.getTransactionAttributeSource() == null) {
            throw new IllegalStateException("Either 'transactionAttributeSource' or 'transactionAttributes' is required: If there are no transactional methods, then don't use a transaction aspect.");
        }
    }

    @Nullable
    protected Object invokeWithinTransaction(Method method, @Nullable Class<?> targetClass, TransactionAspectSupport.InvocationCallback invocation) throws Throwable {
        TransactionAttributeSource tas = this.getTransactionAttributeSource();									// 获取事务属性源
        TransactionAttribute txAttr = tas != null ? tas.getTransactionAttribute(method, targetClass) : null;	// 获取事务属性
        TransactionManager tm = this.determineTransactionManager(txAttr);										// 获取事务管理器
        if (this.reactiveAdapterRegistry != null && tm instanceof ReactiveTransactionManager) {
            TransactionAspectSupport.ReactiveTransactionSupport txSupport = (TransactionAspectSupport.ReactiveTransactionSupport)this.transactionSupportCache.computeIfAbsent(method, (key) -> {
                if (KotlinDetector.isKotlinType(method.getDeclaringClass()) && TransactionAspectSupport.KotlinDelegate.isSuspend(method)) {
                    throw new TransactionUsageException("Unsupported annotated transaction on suspending function detected: " + method + ". Use TransactionalOperator.transactional extensions instead.");
                } else {
                    ReactiveAdapter adapter = this.reactiveAdapterRegistry.getAdapter(method.getReturnType());
                    if (adapter == null) {
                        throw new IllegalStateException("Cannot apply reactive transaction to non-reactive return type: " + method.getReturnType());
                    } else {
                        return new TransactionAspectSupport.ReactiveTransactionSupport(adapter);
                    }
                }
            });
            return txSupport.invokeWithinTransaction(method, targetClass, invocation, txAttr, (ReactiveTransactionManager)tm);
        } else {
            PlatformTransactionManager ptm = this.asPlatformTransactionManager(tm);							// 转成平台事务管理器
            String joinpointIdentification = this.methodIdentification(method, targetClass, txAttr);		// 连接点方法标识
            if (txAttr != null && ptm instanceof CallbackPreferringPlatformTransactionManager) {
                TransactionAspectSupport.ThrowableHolder throwableHolder = new TransactionAspectSupport.ThrowableHolder();

                Object result;
                try {
                    result = ((CallbackPreferringPlatformTransactionManager)ptm).execute(txAttr, (statusx) -> {
                        TransactionAspectSupport.TransactionInfo txInfo = this.prepareTransactionInfo(ptm, txAttr, joinpointIdentification, statusx);

                        Object var9;
                        try {
                            Object retVal = invocation.proceedWithInvocation();
                            if (retVal != null && vavrPresent && TransactionAspectSupport.VavrDelegate.isVavrTry(retVal)) {
                                retVal = TransactionAspectSupport.VavrDelegate.evaluateTryFailure(retVal, txAttr, statusx);
                            }

                            var9 = retVal;
                            return var9;
                        } catch (Throwable var13) {
                            if (txAttr.rollbackOn(var13)) {
                                if (var13 instanceof RuntimeException) {
                                    throw (RuntimeException)var13;
                                }

                                throw new TransactionAspectSupport.ThrowableHolderException(var13);
                            }

                            throwableHolder.throwable = var13;
                            var9 = null;
                        } finally {
                            this.cleanupTransactionInfo(txInfo);
                        }

                        return var9;
                    });
                } catch (TransactionAspectSupport.ThrowableHolderException var20) {
                    throw var20.getCause();
                } catch (TransactionSystemException var21) {
                    if (throwableHolder.throwable != null) {
                        this.logger.error("Application exception overridden by commit exception", throwableHolder.throwable);
                        var21.initApplicationException(throwableHolder.throwable);
                    }

                    throw var21;
                } catch (Throwable var22) {
                    if (throwableHolder.throwable != null) {
                        this.logger.error("Application exception overridden by commit exception", throwableHolder.throwable);
                    }

                    throw var22;
                }

                if (throwableHolder.throwable != null) {
                    throw throwableHolder.throwable;
                } else {
                    return result;
                }
            } else {
                TransactionAspectSupport.TransactionInfo txInfo = this.createTransactionIfNecessary(ptm, txAttr, joinpointIdentification);  // 如果需要 则创建事务 事务信息等会绑定到线程本地变量

                Object retVal;
                try {
                    retVal = invocation.proceedWithInvocation();			// 方法执行
                } catch (Throwable var18) {
                    this.completeTransactionAfterThrowing(txInfo, var18);	// 异常处理(如果是指定异常 则事务回滚，否则提交事务)后重新抛出
                    throw var18;
                } finally {
                    this.cleanupTransactionInfo(txInfo);					// 清除线程本地变量保存的事务信息
                }

                if (retVal != null && vavrPresent && TransactionAspectSupport.VavrDelegate.isVavrTry(retVal)) {
                    TransactionStatus status = txInfo.getTransactionStatus();
                    if (status != null && txAttr != null) {
                        retVal = TransactionAspectSupport.VavrDelegate.evaluateTryFailure(retVal, txAttr, status);
                    }
                }

                this.commitTransactionAfterReturning(txInfo);			// 没有异常 直接提交事务
                return retVal;
            }
        }
    }

    protected void clearTransactionManagerCache() {
        this.transactionManagerCache.clear();
        this.beanFactory = null;
    }

	// 确定事务管理器
    @Nullable
    protected TransactionManager determineTransactionManager(@Nullable TransactionAttribute txAttr) {
        if (txAttr != null && this.beanFactory != null) {
            String qualifier = txAttr.getQualifier();												// 优先从qualifier获取，就是@Transactional的value属性或transactionManager属性
            if (StringUtils.hasText(qualifier)) {
                return this.determineQualifiedTransactionManager(this.beanFactory, qualifier);		
            } else if (StringUtils.hasText(this.transactionManagerBeanName)) {						// 再根据当前支持类的transactionManager属性获取 一般是初始化的时候进行设置
                return this.determineQualifiedTransactionManager(this.beanFactory, this.transactionManagerBeanName);
            } else {
                TransactionManager defaultTransactionManager = this.getTransactionManager();		// 然后获取的是默认的事务管理器
                if (defaultTransactionManager == null) {
                    defaultTransactionManager = (TransactionManager)this.transactionManagerCache.get(DEFAULT_TRANSACTION_MANAGER_KEY);
                    if (defaultTransactionManager == null) {
                        defaultTransactionManager = (TransactionManager)this.beanFactory.getBean(TransactionManager.class);
                        this.transactionManagerCache.putIfAbsent(DEFAULT_TRANSACTION_MANAGER_KEY, defaultTransactionManager);
                    }
                }

                return defaultTransactionManager;
            }
        } else {
            return this.getTransactionManager();
        }
    }

	// 确定事务管理器 从bean工厂获取
    private TransactionManager determineQualifiedTransactionManager(BeanFactory beanFactory, String qualifier) {
        TransactionManager txManager = (TransactionManager)this.transactionManagerCache.get(qualifier);
        if (txManager == null) {
            txManager = (TransactionManager)BeanFactoryAnnotationUtils.qualifiedBeanOfType(beanFactory, TransactionManager.class, qualifier);
            this.transactionManagerCache.putIfAbsent(qualifier, txManager);
        }

        return txManager;
    }

    @Nullable
    private PlatformTransactionManager asPlatformTransactionManager(@Nullable Object transactionManager) {
        if (transactionManager != null && !(transactionManager instanceof PlatformTransactionManager)) {
            throw new IllegalStateException("Specified transaction manager is not a PlatformTransactionManager: " + transactionManager);
        } else {
            return (PlatformTransactionManager)transactionManager;
        }
    }

    private String methodIdentification(Method method, @Nullable Class<?> targetClass, @Nullable TransactionAttribute txAttr) {
        String methodIdentification = this.methodIdentification(method, targetClass);
        if (methodIdentification == null) {
            if (txAttr instanceof DefaultTransactionAttribute) {
                methodIdentification = ((DefaultTransactionAttribute)txAttr).getDescriptor();
            }

            if (methodIdentification == null) {
                methodIdentification = ClassUtils.getQualifiedMethodName(method, targetClass);
            }
        }

        return methodIdentification;
    }

    @Nullable
    protected String methodIdentification(Method method, @Nullable Class<?> targetClass) {
        return null;
    }

    protected TransactionAspectSupport.TransactionInfo createTransactionIfNecessary(@Nullable PlatformTransactionManager tm, @Nullable TransactionAttribute txAttr, final String joinpointIdentification) {
        if (txAttr != null && ((TransactionAttribute)txAttr).getName() == null) {			// 如果有事务属性但是没有标识  则创建事务属性的代理 标识设为连接点方法标识名
            txAttr = new DelegatingTransactionAttribute((TransactionAttribute)txAttr) {
                public String getName() {
                    return joinpointIdentification;
                }
            };
        }

        TransactionStatus status = null;
        if (txAttr != null) {
            if (tm != null) {
                status = tm.getTransaction((TransactionDefinition)txAttr);		// 获取事务 
            } else if (this.logger.isDebugEnabled()) {
                this.logger.debug("Skipping transactional joinpoint [" + joinpointIdentification + "] because no transaction manager has been configured");
            }
        }

        return this.prepareTransactionInfo(tm, (TransactionAttribute)txAttr, joinpointIdentification, status);
    }

    protected TransactionAspectSupport.TransactionInfo prepareTransactionInfo(@Nullable PlatformTransactionManager tm, @Nullable TransactionAttribute txAttr, String joinpointIdentification, @Nullable TransactionStatus status) {
        TransactionAspectSupport.TransactionInfo txInfo = new TransactionAspectSupport.TransactionInfo(tm, txAttr, joinpointIdentification);
        if (txAttr != null) {
            if (this.logger.isTraceEnabled()) {
                this.logger.trace("Getting transaction for [" + txInfo.getJoinpointIdentification() + "]");
            }

            txInfo.newTransactionStatus(status);
        } else if (this.logger.isTraceEnabled()) {
            this.logger.trace("No need to create transaction for [" + joinpointIdentification + "]: This method is not transactional.");
        }

        txInfo.bindToThread();	// 事务信息绑定到线程本地变量
        return txInfo;
    }

    protected void commitTransactionAfterReturning(@Nullable TransactionAspectSupport.TransactionInfo txInfo) {
        if (txInfo != null && txInfo.getTransactionStatus() != null) {
            if (this.logger.isTraceEnabled()) {
                this.logger.trace("Completing transaction for [" + txInfo.getJoinpointIdentification() + "]");
            }

            txInfo.getTransactionManager().commit(txInfo.getTransactionStatus());
        }

    }

    protected void completeTransactionAfterThrowing(@Nullable TransactionAspectSupport.TransactionInfo txInfo, Throwable ex) {
        if (txInfo != null && txInfo.getTransactionStatus() != null) {
            if (this.logger.isTraceEnabled()) {
                this.logger.trace("Completing transaction for [" + txInfo.getJoinpointIdentification() + "] after exception: " + ex);
            }

            if (txInfo.transactionAttribute != null && txInfo.transactionAttribute.rollbackOn(ex)) {
                try {
                    txInfo.getTransactionManager().rollback(txInfo.getTransactionStatus());			// 如果抛出的异常是rollbackFor指定的异常 则事务回滚
                } catch (TransactionSystemException var6) {
                    this.logger.error("Application exception overridden by rollback exception", ex);
                    var6.initApplicationException(ex);
                    throw var6;
                } catch (Error | RuntimeException var7) {
                    this.logger.error("Application exception overridden by rollback exception", ex);
                    throw var7;
                }
            } else {
                try {
                    txInfo.getTransactionManager().commit(txInfo.getTransactionStatus());			// 否则 继续提交事务
                } catch (TransactionSystemException var4) {
                    this.logger.error("Application exception overridden by commit exception", ex);
                    var4.initApplicationException(ex);
                    throw var4;
                } catch (Error | RuntimeException var5) {
                    this.logger.error("Application exception overridden by commit exception", ex);
                    throw var5;
                }
            }
        }

    }

    protected void cleanupTransactionInfo(@Nullable TransactionAspectSupport.TransactionInfo txInfo) {
        if (txInfo != null) {
            txInfo.restoreThreadLocalStatus();
        }

    }

    private static final class ReactiveTransactionInfo {
        @Nullable
        private final ReactiveTransactionManager transactionManager;
        @Nullable
        private final TransactionAttribute transactionAttribute;
        private final String joinpointIdentification;
        @Nullable
        private ReactiveTransaction reactiveTransaction;

        public ReactiveTransactionInfo(@Nullable ReactiveTransactionManager transactionManager, @Nullable TransactionAttribute transactionAttribute, String joinpointIdentification) {
            this.transactionManager = transactionManager;
            this.transactionAttribute = transactionAttribute;
            this.joinpointIdentification = joinpointIdentification;
        }

        public ReactiveTransactionManager getTransactionManager() {
            Assert.state(this.transactionManager != null, "No ReactiveTransactionManager set");
            return this.transactionManager;
        }

        @Nullable
        public TransactionAttribute getTransactionAttribute() {
            return this.transactionAttribute;
        }

        public String getJoinpointIdentification() {
            return this.joinpointIdentification;
        }

        public void newReactiveTransaction(@Nullable ReactiveTransaction transaction) {
            this.reactiveTransaction = transaction;
        }

        @Nullable
        public ReactiveTransaction getReactiveTransaction() {
            return this.reactiveTransaction;
        }

        public String toString() {
            return this.transactionAttribute != null ? this.transactionAttribute.toString() : "No transaction";
        }
    }

    private class ReactiveTransactionSupport {
        private final ReactiveAdapter adapter;

        public ReactiveTransactionSupport(ReactiveAdapter adapter) {
            this.adapter = adapter;
        }

        public Object invokeWithinTransaction(Method method, @Nullable Class<?> targetClass, TransactionAspectSupport.InvocationCallback invocation, @Nullable TransactionAttribute txAttr, ReactiveTransactionManager rtm) {
            String joinpointIdentification = TransactionAspectSupport.this.methodIdentification(method, targetClass, txAttr);
            return Mono.class.isAssignableFrom(method.getReturnType()) ? TransactionContextManager.currentContext().flatMap((context) -> {
                return this.createTransactionIfNecessary(rtm, txAttr, joinpointIdentification).flatMap((it) -> {
                    try {
                        return Mono.usingWhen(Mono.just(it), (txInfo) -> {
                            try {
                                return (Mono)invocation.proceedWithInvocation();
                            } catch (Throwable var3) {
                                return Mono.error(var3);
                            }
                        }, this::commitTransactionAfterReturning, (txInfo, err) -> {
                            return Mono.empty();
                        }, this::commitTransactionAfterReturning).onErrorResume((ex) -> {
                            return this.completeTransactionAfterThrowing(it, ex).then(Mono.error(ex));
                        });
                    } catch (Throwable var4) {
                        return this.completeTransactionAfterThrowing(it, var4).then(Mono.error(var4));
                    }
                });
            }).subscriberContext(TransactionContextManager.getOrCreateContext()).subscriberContext(TransactionContextManager.getOrCreateContextHolder()) : this.adapter.fromPublisher(TransactionContextManager.currentContext().flatMapMany((context) -> {
                return this.createTransactionIfNecessary(rtm, txAttr, joinpointIdentification).flatMapMany((it) -> {
                    try {
                        return Flux.usingWhen(Mono.just(it), (txInfo) -> {
                            try {
                                return this.adapter.toPublisher(invocation.proceedWithInvocation());
                            } catch (Throwable var4) {
                                return Mono.error(var4);
                            }
                        }, this::commitTransactionAfterReturning, (txInfo, ex) -> {
                            return Mono.empty();
                        }, this::commitTransactionAfterReturning).onErrorResume((ex) -> {
                            return this.completeTransactionAfterThrowing(it, ex).then(Mono.error(ex));
                        });
                    } catch (Throwable var4) {
                        return this.completeTransactionAfterThrowing(it, var4).then(Mono.error(var4));
                    }
                });
            }).subscriberContext(TransactionContextManager.getOrCreateContext()).subscriberContext(TransactionContextManager.getOrCreateContextHolder()));
        }

        private Mono<TransactionAspectSupport.ReactiveTransactionInfo> createTransactionIfNecessary(ReactiveTransactionManager tm, @Nullable TransactionAttribute txAttr, final String joinpointIdentification) {
            if (txAttr != null && ((TransactionAttribute)txAttr).getName() == null) {
                txAttr = new DelegatingTransactionAttribute((TransactionAttribute)txAttr) {
                    public String getName() {
                        return joinpointIdentification;
                    }
                };
            }

            Mono<ReactiveTransaction> tx = txAttr != null ? tm.getReactiveTransaction((TransactionDefinition)txAttr) : Mono.empty();
            return tx.map((it) -> {
                return this.prepareTransactionInfo(tm, txAttr, joinpointIdentification, it);
            }).switchIfEmpty(Mono.defer(() -> {
                return Mono.just(this.prepareTransactionInfo(tm, txAttr, joinpointIdentification, (ReactiveTransaction)null));
            }));
        }

        private TransactionAspectSupport.ReactiveTransactionInfo prepareTransactionInfo(@Nullable ReactiveTransactionManager tm, @Nullable TransactionAttribute txAttr, String joinpointIdentification, @Nullable ReactiveTransaction transaction) {
            TransactionAspectSupport.ReactiveTransactionInfo txInfo = new TransactionAspectSupport.ReactiveTransactionInfo(tm, txAttr, joinpointIdentification);
            if (txAttr != null) {
                if (TransactionAspectSupport.this.logger.isTraceEnabled()) {
                    TransactionAspectSupport.this.logger.trace("Getting transaction for [" + txInfo.getJoinpointIdentification() + "]");
                }

                txInfo.newReactiveTransaction(transaction);
            } else if (TransactionAspectSupport.this.logger.isTraceEnabled()) {
                TransactionAspectSupport.this.logger.trace("Don't need to create transaction for [" + joinpointIdentification + "]: This method isn't transactional.");
            }

            return txInfo;
        }

        private Mono<Void> commitTransactionAfterReturning(@Nullable TransactionAspectSupport.ReactiveTransactionInfo txInfo) {
            if (txInfo != null && txInfo.getReactiveTransaction() != null) {
                if (TransactionAspectSupport.this.logger.isTraceEnabled()) {
                    TransactionAspectSupport.this.logger.trace("Completing transaction for [" + txInfo.getJoinpointIdentification() + "]");
                }

                return txInfo.getTransactionManager().commit(txInfo.getReactiveTransaction());
            } else {
                return Mono.empty();
            }
        }

        private Mono<Void> completeTransactionAfterThrowing(@Nullable TransactionAspectSupport.ReactiveTransactionInfo txInfo, Throwable ex) {
            if (txInfo != null && txInfo.getReactiveTransaction() != null) {
                if (TransactionAspectSupport.this.logger.isTraceEnabled()) {
                    TransactionAspectSupport.this.logger.trace("Completing transaction for [" + txInfo.getJoinpointIdentification() + "] after exception: " + ex);
                }

                return txInfo.transactionAttribute != null && txInfo.transactionAttribute.rollbackOn(ex) ? txInfo.getTransactionManager().rollback(txInfo.getReactiveTransaction()).onErrorMap((ex2) -> {
                    TransactionAspectSupport.this.logger.error("Application exception overridden by rollback exception", ex);
                    if (ex2 instanceof TransactionSystemException) {
                        ((TransactionSystemException)ex2).initApplicationException(ex);
                    }

                    return ex2;
                }) : txInfo.getTransactionManager().commit(txInfo.getReactiveTransaction()).onErrorMap((ex2) -> {
                    TransactionAspectSupport.this.logger.error("Application exception overridden by commit exception", ex);
                    if (ex2 instanceof TransactionSystemException) {
                        ((TransactionSystemException)ex2).initApplicationException(ex);
                    }

                    return ex2;
                });
            } else {
                return Mono.empty();
            }
        }
    }

    private static class KotlinDelegate {
        private KotlinDelegate() {
        }

        private static boolean isSuspend(Method method) {
            KFunction<?> function = ReflectJvmMapping.getKotlinFunction(method);
            return function != null && function.isSuspend();
        }
    }

    private static class VavrDelegate {
        private VavrDelegate() {
        }

        public static boolean isVavrTry(Object retVal) {
            return retVal instanceof Try;
        }

        public static Object evaluateTryFailure(Object retVal, TransactionAttribute txAttr, TransactionStatus status) {
            return ((Try)retVal).onFailure((ex) -> {
                if (txAttr.rollbackOn(ex)) {
                    status.setRollbackOnly();
                }

            });
        }
    }

    private static class ThrowableHolderException extends RuntimeException {
        public ThrowableHolderException(Throwable throwable) {
            super(throwable);
        }

        public String toString() {
            return this.getCause().toString();
        }
    }

	// 异常持有类
    private static class ThrowableHolder {
        @Nullable
        public Throwable throwable;

        private ThrowableHolder() {
        }
    }

    @FunctionalInterface
    protected interface InvocationCallback {
        @Nullable
        Object proceedWithInvocation() throws Throwable;
    }

	// 事务信息封装 包括事务状态 事务管理器 连接点信息等
    protected static final class TransactionInfo {
        @Nullable
        private final PlatformTransactionManager transactionManager;	// 平台事务管理器
        @Nullable
        private final TransactionAttribute transactionAttribute;		// 事务属性(扩展的事务定义信息)
        private final String joinpointIdentification;					// 连接点标识？
        @Nullable
        private TransactionStatus transactionStatus;					// 事务状态
        @Nullable
        private TransactionAspectSupport.TransactionInfo oldTransactionInfo;

        public TransactionInfo(@Nullable PlatformTransactionManager transactionManager, @Nullable TransactionAttribute transactionAttribute, String joinpointIdentification) {
            this.transactionManager = transactionManager;
            this.transactionAttribute = transactionAttribute;
            this.joinpointIdentification = joinpointIdentification;
        }

        public PlatformTransactionManager getTransactionManager() {
            Assert.state(this.transactionManager != null, "No PlatformTransactionManager set");
            return this.transactionManager;
        }

        @Nullable
        public TransactionAttribute getTransactionAttribute() {
            return this.transactionAttribute;
        }

        public String getJoinpointIdentification() {
            return this.joinpointIdentification;
        }

        public void newTransactionStatus(@Nullable TransactionStatus status) {
            this.transactionStatus = status;
        }

        @Nullable
        public TransactionStatus getTransactionStatus() {
            return this.transactionStatus;
        }

        public boolean hasTransaction() {
            return this.transactionStatus != null;
        }

		// 事务状态绑定到线程
        private void bindToThread() {
            this.oldTransactionInfo = (TransactionAspectSupport.TransactionInfo)TransactionAspectSupport.transactionInfoHolder.get();
            TransactionAspectSupport.transactionInfoHolder.set(this);
        }

		// 恢复到之前的事务状态
        private void restoreThreadLocalStatus() {
            TransactionAspectSupport.transactionInfoHolder.set(this.oldTransactionInfo);
        }

        public String toString() {
            return this.transactionAttribute != null ? this.transactionAttribute.toString() : "No transaction";
        }
    }
}
