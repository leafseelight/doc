package com.netflix.hystrix;

import com.netflix.hystrix.HystrixCommandMetrics.HealthCounts;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

// 断路器
public interface HystrixCircuitBreaker {
    boolean allowRequest(); 	// 是否允许请求

    boolean isOpen();			// 断路器是否打开

    void markSuccess();			// 闭合断路器

	// 一个简单的不执行操作的断路器实现 就是永远不断路 永远允许请求通过
    public static class NoOpCircuitBreaker implements HystrixCircuitBreaker {
        public NoOpCircuitBreaker() {
        }

        public boolean allowRequest() {
            return true;
        }

        public boolean isOpen() {
            return false;
        }

        public void markSuccess() {
        }
    }

    public static class HystrixCircuitBreakerImpl implements HystrixCircuitBreaker {
        private final HystrixCommandProperties properties;						// 配置属性
        private final HystrixCommandMetrics metrics;							// 监控属性
        private AtomicBoolean circuitOpen = new AtomicBoolean(false);			// 断路器打开标识
        private AtomicLong circuitOpenedOrLastTestedTime = new AtomicLong();	// 断路器开启时间或者上次测试时间

        protected HystrixCircuitBreakerImpl(HystrixCommandKey key, HystrixCommandGroupKey commandGroup, HystrixCommandProperties properties, HystrixCommandMetrics metrics) {
            this.properties = properties;
            this.metrics = metrics;
        }

        public void markSuccess() {
            if (this.circuitOpen.get() && this.circuitOpen.compareAndSet(true, false)) {
				// 重置监控数据 
                this.metrics.resetStream();
            }

        }

		// 是否允许请求
        public boolean allowRequest() {
			// 如果断路器强制打开 则不允许请求
            if ((Boolean)this.properties.circuitBreakerForceOpen().get()) {
                return false;
			// 如果断路器强制关闭，则再判断是否打开。返回true允许请求
            } else if ((Boolean)this.properties.circuitBreakerForceClosed().get()) {
                this.isOpen();
                return true;
            } else {
                return !this.isOpen() || this.allowSingleTest();
            }
        }

		// 是否允许测试
		// 如果断路器打开且当前时间过了上次断路器打开时间加上窗口时间之和 则允许
		// 作用就是在断路器打开并休眠一段时间(默认5s)，允许一些请求尝试访问, 此时断路器的半开状态，如果此时请求继续失败，路由器又进入打开状态，并且继续等待下一个休眠窗口过去之后再次尝试，
		// 如果请求成功，则关闭断路器。
        public boolean allowSingleTest() {
            long timeCircuitOpenedOrWasLastTested = this.circuitOpenedOrLastTestedTime.get();
            return this.circuitOpen.get() 
			&& System.currentTimeMillis() > timeCircuitOpenedOrWasLastTested + (long)(Integer)this.properties.circuitBreakerSleepWindowInMilliseconds().get() 
			&& this.circuitOpenedOrLastTestedTime.compareAndSet(timeCircuitOpenedOrWasLastTested, System.currentTimeMillis());
        }

		// 断路器是否打开 
        public boolean isOpen() {
            if (this.circuitOpen.get()) {
                return true;
            } else {
                HealthCounts health = this.metrics.getHealthCounts();
				// 一段时间内请求数的阈值 默认10s20次
				// 如果一段时间内的请求数未达到阈值，即使这段时间内的请求全部失败，也不会继续去判断错误百分比，不会打开断路器
                if (health.getTotalRequests() < (long)(Integer)this.properties.circuitBreakerRequestVolumeThreshold().get()) {
                    return false;
				// 错误百分比默认50%
				// 一段时间内的请求错误或者重试的次数占这段时间全部请求的数的百分比，如果超出阈值，则打开断路器
                } else if (health.getErrorPercentage() < (Integer)this.properties.circuitBreakerErrorThresholdPercentage().get()) {
                    return false;
				// 打开断路器 cas设置circuitOpen值 并设置打开断路器的时间
                } else if (this.circuitOpen.compareAndSet(false, true)) {
                    this.circuitOpenedOrLastTestedTime.set(System.currentTimeMillis());
                    return true;
                } else {
                    return true;
                }
            }
        }
    }

    public static class Factory {
        private static ConcurrentHashMap<String, HystrixCircuitBreaker> circuitBreakersByCommand = new ConcurrentHashMap();  // commandKey与断路器的映射关系

        public Factory() {
        }

        public static HystrixCircuitBreaker getInstance(HystrixCommandKey key, HystrixCommandGroupKey group, HystrixCommandProperties properties, HystrixCommandMetrics metrics) {
            HystrixCircuitBreaker previouslyCached = (HystrixCircuitBreaker)circuitBreakersByCommand.get(key.name());
            if (previouslyCached != null) {
                return previouslyCached;
            } else {
                HystrixCircuitBreaker cbForCommand = (HystrixCircuitBreaker)circuitBreakersByCommand.putIfAbsent(key.name(), new HystrixCircuitBreaker.HystrixCircuitBreakerImpl(key, group, properties, metrics));
                return cbForCommand == null ? (HystrixCircuitBreaker)circuitBreakersByCommand.get(key.name()) : cbForCommand;
            }
        }

        public static HystrixCircuitBreaker getInstance(HystrixCommandKey key) {
            return (HystrixCircuitBreaker)circuitBreakersByCommand.get(key.name());
        }

        static void reset() {
            circuitBreakersByCommand.clear();
        }
    }
}
