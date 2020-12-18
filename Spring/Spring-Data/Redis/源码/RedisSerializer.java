package org.springframework.data.redis.serializer;

import org.springframework.lang.Nullable;

public interface RedisSerializer<T> {
    @Nullable
    byte[] serialize(@Nullable T var1) throws SerializationException;

    @Nullable
    T deserialize(@Nullable byte[] var1) throws SerializationException;

    static RedisSerializer<Object> java() {
        return java((ClassLoader)null);
    }

    static RedisSerializer<Object> java(@Nullable ClassLoader classLoader) {
		// 默认的java序列化器 使用了jdk自带的对象流
        return new JdkSerializationRedisSerializer(classLoader);
    }

    static RedisSerializer<Object> json() {
		// 默认的json序列化器 使用了jackson框架
        return new GenericJackson2JsonRedisSerializer();
    }

    static RedisSerializer<String> string() {
		// 默认的string序列化器 UTF-8的
        return StringRedisSerializer.UTF_8;
    }
}
