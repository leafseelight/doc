package org.springframework.core.serializer.support;

import java.io.ByteArrayOutputStream;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.serializer.DefaultSerializer;
import org.springframework.core.serializer.Serializer;
import org.springframework.util.Assert;

// 序列化转换器
public class SerializingConverter implements Converter<Object, byte[]> {
    private final Serializer<Object> serializer;

    public SerializingConverter() {
        this.serializer = new DefaultSerializer();
    }

    public SerializingConverter(Serializer<Object> serializer) {
        Assert.notNull(serializer, "Serializer must not be null");
        this.serializer = serializer;
    }

	// 转换  默认使用了jdk自带的对象输出流
    public byte[] convert(Object source) {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream(1024);

        try {
            this.serializer.serialize(source, byteStream);
            return byteStream.toByteArray();
        } catch (Throwable var4) {
            throw new SerializationFailedException("Failed to serialize object using " + this.serializer.getClass().getSimpleName(), var4);
        }
    }
}
