package org.springframework.data.redis.serializer;

import org.springframework.core.convert.converter.Converter;
import org.springframework.core.serializer.support.DeserializingConverter;
import org.springframework.core.serializer.support.SerializingConverter;
import org.springframework.util.Assert;

// Jdk序列化器 具体逻辑由转换器Converter实现
public class JdkSerializationRedisSerializer implements RedisSerializer<Object> {
    private final Converter<Object, byte[]> serializer;
    private final Converter<byte[], Object> deserializer;

    public JdkSerializationRedisSerializer() {
        this(new SerializingConverter(), new DeserializingConverter());
    }

    public JdkSerializationRedisSerializer(ClassLoader classLoader) {
        this(new SerializingConverter(), new DeserializingConverter(classLoader));
    }

    public JdkSerializationRedisSerializer(Converter<Object, byte[]> serializer, Converter<byte[], Object> deserializer) {
        Assert.notNull(serializer, "Serializer must not be null!");
        Assert.notNull(deserializer, "Deserializer must not be null!");
        this.serializer = serializer;
        this.deserializer = deserializer;
    }

	// 反序列化
    public Object deserialize(byte[] bytes) {
        if (SerializationUtils.isEmpty(bytes)) {
            return null;
        } else {
            try {
                return this.deserializer.convert(bytes);
            } catch (Exception var3) {
                throw new SerializationException("Cannot deserialize", var3);
            }
        }
    }

	// 序列化
    public byte[] serialize(Object object) {
        if (object == null) {
            return SerializationUtils.EMPTY_ARRAY;
        } else {
            try {
                return (byte[])this.serializer.convert(object);
            } catch (Exception var3) {
                throw new SerializationException("Cannot serialize", var3);
            }
        }
    }
}
