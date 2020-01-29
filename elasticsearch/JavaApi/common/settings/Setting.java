//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package org.elasticsearch.common.settings;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.ElasticsearchParseException;
import org.elasticsearch.common.Booleans;
import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.collect.Tuple;
import org.elasticsearch.common.regex.Regex;
import org.elasticsearch.common.settings.AbstractScopedSettings.SettingUpdater;
import org.elasticsearch.common.settings.Settings.Builder;
import org.elasticsearch.common.settings.Settings.DeprecationLoggerHolder;
import org.elasticsearch.common.unit.ByteSizeValue;
import org.elasticsearch.common.unit.MemorySizeValue;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.common.xcontent.ToXContentObject;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.common.xcontent.ToXContent.Params;
import org.elasticsearch.common.xcontent.XContentParser.Token;

public class Setting<T> implements ToXContentObject {
    private final Setting.Key key;
    protected final Function<Settings, String> defaultValue;
    @Nullable
    private final Setting<T> fallbackSetting;
    private final Function<String, T> parser;
    private final Setting.Validator<T> validator;
    private final EnumSet<Setting.Property> properties;
    private static final EnumSet<Setting.Property> EMPTY_PROPERTIES = EnumSet.noneOf(Setting.Property.class);

    private Setting(Setting.Key key, @Nullable Setting<T> fallbackSetting, Function<Settings, String> defaultValue, Function<String, T> parser, Setting.Validator<T> validator, Setting.Property... properties) {
        assert this instanceof SecureSetting || this.isGroupSetting() || parser.apply((String)defaultValue.apply(Settings.EMPTY)) != null : "parser returned null";

        this.key = key;
        this.fallbackSetting = fallbackSetting;
        this.defaultValue = defaultValue;
        this.parser = parser;
        this.validator = validator;
        if(properties == null) {
            throw new IllegalArgumentException("properties cannot be null for setting [" + key + "]");
        } else {
            if(properties.length == 0) {
                this.properties = EMPTY_PROPERTIES;
            } else {
                this.properties = EnumSet.copyOf(Arrays.asList(properties));
                if(this.isDynamic() && this.isFinal()) {
                    throw new IllegalArgumentException("final setting [" + key + "] cannot be dynamic");
                }
            }

        }
    }

    public Setting(Setting.Key key, Function<Settings, String> defaultValue, Function<String, T> parser, Setting.Property... properties) {
        this(key, defaultValue, parser, (v, s) -> {
        }, properties);
    }

    public Setting(Setting.Key key, Function<Settings, String> defaultValue, Function<String, T> parser, Setting.Validator<T> validator, Setting.Property... properties) {
        this(key, (Setting)null, defaultValue, parser, validator, properties);
    }

    public Setting(String key, String defaultValue, Function<String, T> parser, Setting.Property... properties) {
        this(key, (s) -> {
            return defaultValue;
        }, parser, properties);
    }

    public Setting(String key, String defaultValue, Function<String, T> parser, Setting.Validator<T> validator, Setting.Property... properties) {
        this((Setting.Key)(new Setting.SimpleKey(key)), (Function)((s) -> {
            return defaultValue;
        }), parser, validator, properties);
    }

    public Setting(String key, Function<Settings, String> defaultValue, Function<String, T> parser, Setting.Property... properties) {
        this((Setting.Key)(new Setting.SimpleKey(key)), (Function)defaultValue, parser, properties);
    }

    public Setting(Setting.Key key, Setting<T> fallbackSetting, Function<String, T> parser, Setting.Property... properties) {
        Objects.requireNonNull(fallbackSetting);
        this(key, fallbackSetting, fallbackSetting::getRaw, parser, (v, m) -> {
        }, properties);
    }

    public Setting(String key, Setting<T> fallBackSetting, Function<String, T> parser, Setting.Property... properties) {
        this((Setting.Key)(new Setting.SimpleKey(key)), (Setting)fallBackSetting, parser, properties);
    }

    public final String getKey() {
        return this.key.toString();
    }

    public final Setting.Key getRawKey() {
        return this.key;
    }

    public final boolean isDynamic() {
        return this.properties.contains(Setting.Property.Dynamic);
    }

    public final boolean isFinal() {
        return this.properties.contains(Setting.Property.Final);
    }

    public EnumSet<Setting.Property> getProperties() {
        return this.properties;
    }

    public boolean isFiltered() {
        return this.properties.contains(Setting.Property.Filtered);
    }

    public boolean hasNodeScope() {
        return this.properties.contains(Setting.Property.NodeScope);
    }

    public boolean hasIndexScope() {
        return this.properties.contains(Setting.Property.IndexScope);
    }

    public boolean isDeprecated() {
        return this.properties.contains(Setting.Property.Deprecated);
    }

    boolean isGroupSetting() {
        return false;
    }

    boolean hasComplexMatcher() {
        return this.isGroupSetting();
    }

    public String getDefaultRaw(Settings settings) {
        return (String)this.defaultValue.apply(settings);
    }

    public T getDefault(Settings settings) {
        return this.parser.apply(this.getDefaultRaw(settings));
    }

    public boolean exists(Settings settings) {
        return settings.keySet().contains(this.getKey());
    }

    public T get(Settings settings) {
        return this.get(settings, true);
    }

    private T get(Settings settings, boolean validate) {
        String value = this.getRaw(settings);

        try {
            T parsed = this.parser.apply(value);
            if(validate) {
                Iterator<Setting<T>> it = this.validator.settings();
                Object map;
                if(it.hasNext()) {
                    map = new HashMap();

                    while(it.hasNext()) {
                        Setting<T> setting = (Setting)it.next();
                        ((Map)map).put(setting, setting.get(settings, false));
                    }
                } else {
                    map = Collections.emptyMap();
                }

                this.validator.validate(parsed, (Map)map);
            }

            return parsed;
        } catch (ElasticsearchParseException var8) {
            throw new IllegalArgumentException(var8.getMessage(), var8);
        } catch (NumberFormatException var9) {
            throw new IllegalArgumentException("Failed to parse value [" + value + "] for setting [" + this.getKey() + "]", var9);
        } catch (IllegalArgumentException var10) {
            throw var10;
        } catch (Exception var11) {
            throw new IllegalArgumentException("Failed to parse value [" + value + "] for setting [" + this.getKey() + "]", var11);
        }
    }

    public void diff(Builder builder, Settings source, Settings defaultSettings) {
        if(!this.exists(source)) {
            builder.put(this.getKey(), this.getRaw(defaultSettings));
        }

    }

    public String getRaw(Settings settings) {
        this.checkDeprecation(settings);
        return settings.get(this.getKey(), (String)this.defaultValue.apply(settings));
    }

    void checkDeprecation(Settings settings) {
        if(this.isDeprecated() && this.exists(settings)) {
            String key = this.getKey();
            DeprecationLoggerHolder.deprecationLogger.deprecatedAndMaybeLog(key, "[{}] setting was deprecated in Elasticsearch and will be removed in a future release! See the breaking changes documentation for the next major version.", new Object[]{key});
        }

    }

    public final boolean match(String toTest) {
        return this.key.match(toTest);
    }

    public final XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field("key", this.key.toString());
        builder.field("properties", this.properties);
        builder.field("is_group_setting", this.isGroupSetting());
        builder.field("default", (String)this.defaultValue.apply(Settings.EMPTY));
        builder.endObject();
        return builder;
    }

    public String toString() {
        return Strings.toString(this, true, true);
    }

    public final T get(Settings primary, Settings secondary) {
        return this.exists(primary)?this.get(primary):(this.exists(secondary)?this.get(secondary):(this.fallbackSetting == null?this.get(primary):(this.fallbackSetting.exists(primary)?this.fallbackSetting.get(primary):this.fallbackSetting.get(secondary))));
    }

    public Setting<T> getConcreteSetting(String key) {
        assert key.startsWith(this.getKey()) : "was " + key + " expected: " + this.getKey();

        return this;
    }

    public Set<String> getSettingsDependencies(String key) {
        return Collections.emptySet();
    }

    final SettingUpdater<T> newUpdater(Consumer<T> consumer, Logger logger) {
        return this.newUpdater(consumer, logger, (s) -> {
        });
    }

    SettingUpdater<T> newUpdater(Consumer<T> consumer, Logger logger, Consumer<T> validator) {
        if(this.isDynamic()) {
            return new Setting.Updater(consumer, logger, validator);
        } else {
            throw new IllegalStateException("setting [" + this.getKey() + "] is not dynamic");
        }
    }

    static <A, B> SettingUpdater<Tuple<A, B>> compoundUpdater(final BiConsumer<A, B> consumer, final BiConsumer<A, B> validator, final Setting<A> aSetting, final Setting<B> bSetting, final Logger logger) {
        final SettingUpdater<A> aSettingUpdater = aSetting.newUpdater((Consumer)null, logger);
        final SettingUpdater<B> bSettingUpdater = bSetting.newUpdater((Consumer)null, logger);
        return new SettingUpdater<Tuple<A, B>>() {
            public boolean hasChanged(Settings current, Settings previous) {
                return aSettingUpdater.hasChanged(current, previous) || bSettingUpdater.hasChanged(current, previous);
            }

            public Tuple<A, B> getValue(Settings current, Settings previous) {
                A valueA = aSettingUpdater.getValue(current, previous);
                B valueB = bSettingUpdater.getValue(current, previous);
                validator.accept(valueA, valueB);
                return new Tuple(valueA, valueB);
            }

            public void apply(Tuple<A, B> value, Settings current, Settings previous) {
                if(aSettingUpdater.hasChanged(current, previous)) {
                    Setting.logSettingUpdate(aSetting, current, previous, logger);
                }

                if(bSettingUpdater.hasChanged(current, previous)) {
                    Setting.logSettingUpdate(bSetting, current, previous, logger);
                }

                consumer.accept(value.v1(), value.v2());
            }

            public String toString() {
                return "CompoundUpdater for: " + aSettingUpdater + " and " + bSettingUpdater;
            }
        };
    }

    static SettingUpdater<Settings> groupedSettingsUpdater(final Consumer<Settings> consumer, Logger logger, final List<? extends Setting<?>> configuredSettings) {
        return new SettingUpdater<Settings>() {
            private Settings get(Settings settings) {
                return settings.filter((s) -> {
                    Iterator var2 = configuredSettings.iterator();

                    Setting setting;
                    do {
                        if(!var2.hasNext()) {
                            return false;
                        }

                        setting = (Setting)var2.next();
                    } while(!setting.key.match(s));

                    return true;
                });
            }

            public boolean hasChanged(Settings current, Settings previous) {
                Settings currentSettings = this.get(current);
                Settings previousSettings = this.get(previous);
                return !currentSettings.equals(previousSettings);
            }

            public Settings getValue(Settings current, Settings previous) {
                return this.get(current);
            }

            public void apply(Settings value, Settings current, Settings previous) {
                consumer.accept(value);
            }

            public String toString() {
                return "Updater grouped: " + (String)configuredSettings.stream().map(Setting::getKey).collect(Collectors.joining(", "));
            }
        };
    }

    public static Setting<Float> floatSetting(String key, float defaultValue, Setting.Property... properties) {
        return new Setting(key, (s) -> {
            return Float.toString(defaultValue);
        }, Float::parseFloat, properties);
    }

    public static Setting<Float> floatSetting(String key, float defaultValue, float minValue, Setting.Property... properties) {
        return new Setting(key, (s) -> {
            return Float.toString(defaultValue);
        }, (s) -> {
            float value = Float.parseFloat(s);
            if(value < minValue) {
                throw new IllegalArgumentException("Failed to parse value [" + s + "] for setting [" + key + "] must be >= " + minValue);
            } else {
                return Float.valueOf(value);
            }
        }, properties);
    }

    public static Setting<Integer> intSetting(String key, int defaultValue, int minValue, int maxValue, Setting.Property... properties) {
        return new Setting(key, (s) -> {
            return Integer.toString(defaultValue);
        }, (s) -> {
            return Integer.valueOf(parseInt(s, minValue, maxValue, key));
        }, properties);
    }

    public static Setting<Integer> intSetting(String key, int defaultValue, int minValue, Setting.Property... properties) {
        return new Setting(key, (s) -> {
            return Integer.toString(defaultValue);
        }, (s) -> {
            return Integer.valueOf(parseInt(s, minValue, key));
        }, properties);
    }

    public static Setting<Integer> intSetting(String key, Setting<Integer> fallbackSetting, int minValue, Setting.Property... properties) {
        return new Setting(key, fallbackSetting, (s) -> {
            return Integer.valueOf(parseInt(s, minValue, key));
        }, properties);
    }

    public static Setting<Integer> intSetting(String key, Setting<Integer> fallbackSetting, int minValue, Setting.Validator<Integer> validator, Setting.Property... properties) {
        Setting.SimpleKey var10002 = new Setting.SimpleKey(key);
        Objects.requireNonNull(fallbackSetting);
        return new Setting(var10002, fallbackSetting, fallbackSetting::getRaw, (s) -> {
            return Integer.valueOf(parseInt(s, minValue, key));
        }, validator, properties);
    }

    public static Setting<Long> longSetting(String key, long defaultValue, long minValue, Setting.Property... properties) {
        return new Setting(key, (s) -> {
            return Long.toString(defaultValue);
        }, (s) -> {
            return Long.valueOf(parseLong(s, minValue, key));
        }, properties);
    }

    public static Setting<String> simpleString(String key, Setting.Property... properties) {
        return new Setting(key, (s) -> {
            return "";
        }, Function.identity(), properties);
    }

    public static Setting<String> simpleString(String key, Setting<String> fallback, Setting.Property... properties) {
        return new Setting(key, fallback, Function.identity(), properties);
    }

    public static Setting<String> simpleString(String key, Setting.Validator<String> validator, Setting.Property... properties) {
        return new Setting(new Setting.SimpleKey(key), (Setting)null, (s) -> {
            return "";
        }, Function.identity(), validator, properties);
    }

    public static int parseInt(String s, int minValue, String key) {
        return parseInt(s, minValue, 2147483647, key);
    }

    public static int parseInt(String s, int minValue, int maxValue, String key) {
        int value = Integer.parseInt(s);
        if(value < minValue) {
            throw new IllegalArgumentException("Failed to parse value [" + s + "] for setting [" + key + "] must be >= " + minValue);
        } else if(value > maxValue) {
            throw new IllegalArgumentException("Failed to parse value [" + s + "] for setting [" + key + "] must be <= " + maxValue);
        } else {
            return value;
        }
    }

    public static long parseLong(String s, long minValue, String key) {
        long value = Long.parseLong(s);
        if(value < minValue) {
            throw new IllegalArgumentException("Failed to parse value [" + s + "] for setting [" + key + "] must be >= " + minValue);
        } else {
            return value;
        }
    }

    public static TimeValue parseTimeValue(String s, TimeValue minValue, String key) {
        TimeValue timeValue = TimeValue.parseTimeValue(s, (TimeValue)null, key);
        if(timeValue.millis() < minValue.millis()) {
            throw new IllegalArgumentException("Failed to parse value [" + s + "] for setting [" + key + "] must be >= " + minValue);
        } else {
            return timeValue;
        }
    }

    public static Setting<Integer> intSetting(String key, int defaultValue, Setting.Property... properties) {
        return intSetting(key, defaultValue, -2147483648, properties);
    }

    public static Setting<Boolean> boolSetting(String key, boolean defaultValue, Setting.Property... properties) {
        return new Setting(key, (s) -> {
            return Boolean.toString(defaultValue);
        }, Booleans::parseBoolean, properties);
    }

    public static Setting<Boolean> boolSetting(String key, Setting<Boolean> fallbackSetting, Setting.Property... properties) {
        return new Setting(key, fallbackSetting, Booleans::parseBoolean, properties);
    }

    public static Setting<Boolean> boolSetting(String key, Function<Settings, String> defaultValueFn, Setting.Property... properties) {
        return new Setting(key, defaultValueFn, Booleans::parseBoolean, properties);
    }

    public static Setting<ByteSizeValue> byteSizeSetting(String key, ByteSizeValue value, Setting.Property... properties) {
        return byteSizeSetting(key, (s) -> {
            return value.toString();
        }, properties);
    }

    public static Setting<ByteSizeValue> byteSizeSetting(String key, Setting<ByteSizeValue> fallbackSetting, Setting.Property... properties) {
        return new Setting(key, fallbackSetting, (s) -> {
            return ByteSizeValue.parseBytesSizeValue(s, key);
        }, properties);
    }

    public static Setting<ByteSizeValue> byteSizeSetting(String key, Function<Settings, String> defaultValue, Setting.Property... properties) {
        return new Setting(key, defaultValue, (s) -> {
            return ByteSizeValue.parseBytesSizeValue(s, key);
        }, properties);
    }

    public static Setting<ByteSizeValue> byteSizeSetting(String key, ByteSizeValue defaultValue, ByteSizeValue minValue, ByteSizeValue maxValue, Setting.Property... properties) {
        return byteSizeSetting(key, (s) -> {
            return defaultValue.getStringRep();
        }, minValue, maxValue, properties);
    }

    public static Setting<ByteSizeValue> byteSizeSetting(String key, Function<Settings, String> defaultValue, ByteSizeValue minValue, ByteSizeValue maxValue, Setting.Property... properties) {
        return new Setting(key, defaultValue, (s) -> {
            return parseByteSize(s, minValue, maxValue, key);
        }, properties);
    }

    public static ByteSizeValue parseByteSize(String s, ByteSizeValue minValue, ByteSizeValue maxValue, String key) {
        ByteSizeValue value = ByteSizeValue.parseBytesSizeValue(s, key);
        if(value.getBytes() < minValue.getBytes()) {
            throw new IllegalArgumentException("Failed to parse value [" + s + "] for setting [" + key + "] must be >= " + minValue);
        } else if(value.getBytes() > maxValue.getBytes()) {
            throw new IllegalArgumentException("Failed to parse value [" + s + "] for setting [" + key + "] must be <= " + maxValue);
        } else {
            return value;
        }
    }

    public static Setting<ByteSizeValue> memorySizeSetting(String key, ByteSizeValue defaultValue, Setting.Property... properties) {
        return memorySizeSetting(key, (s) -> {
            return defaultValue.toString();
        }, properties);
    }

    public static Setting<ByteSizeValue> memorySizeSetting(String key, Function<Settings, String> defaultValue, Setting.Property... properties) {
        return new Setting(key, defaultValue, (s) -> {
            return MemorySizeValue.parseBytesSizeValueOrHeapRatio(s, key);
        }, properties);
    }

    public static Setting<ByteSizeValue> memorySizeSetting(String key, String defaultPercentage, Setting.Property... properties) {
        return new Setting(key, (s) -> {
            return defaultPercentage;
        }, (s) -> {
            return MemorySizeValue.parseBytesSizeValueOrHeapRatio(s, key);
        }, properties);
    }

    public static <T> Setting<List<T>> listSetting(String key, List<String> defaultStringValue, Function<String, T> singleValueParser, Setting.Property... properties) {
        return listSetting(key, (s) -> {
            return defaultStringValue;
        }, singleValueParser, properties);
    }

    public static <T> Setting<List<T>> listSetting(String key, Setting<List<T>> fallbackSetting, Function<String, T> singleValueParser, Setting.Property... properties) {
        return listSetting(key, (s) -> {
            return parseableStringToList(fallbackSetting.getRaw(s));
        }, singleValueParser, properties);
    }

    public static <T> Setting<List<T>> listSetting(String key, Function<Settings, List<String>> defaultStringValue, Function<String, T> singleValueParser, Setting.Property... properties) {
        if(defaultStringValue.apply(Settings.EMPTY) == null) {
            throw new IllegalArgumentException("default value function must not return null");
        } else {
            Function<String, List<T>> parser = (s) -> {
                return (List)parseableStringToList(s).stream().map(singleValueParser).collect(Collectors.toList());
            };
            return new Setting.ListSetting(key, defaultStringValue, parser, properties, null);
        }
    }

    private static List<String> parseableStringToList(String parsableString) {
        try {
            XContentParser xContentParser = XContentType.JSON.xContent().createParser(NamedXContentRegistry.EMPTY, parsableString);
            Throwable var2 = null;

            ArrayList var5;
            try {
                Token token = xContentParser.nextToken();
                if(token != Token.START_ARRAY) {
                    throw new IllegalArgumentException("expected START_ARRAY but got " + token);
                }

                ArrayList list = new ArrayList();

                while((token = xContentParser.nextToken()) != Token.END_ARRAY) {
                    if(token != Token.VALUE_STRING) {
                        throw new IllegalArgumentException("expected VALUE_STRING but got " + token);
                    }

                    list.add(xContentParser.text());
                }

                var5 = list;
            } catch (Throwable var15) {
                var2 = var15;
                throw var15;
            } finally {
                if(xContentParser != null) {
                    if(var2 != null) {
                        try {
                            xContentParser.close();
                        } catch (Throwable var14) {
                            var2.addSuppressed(var14);
                        }
                    } else {
                        xContentParser.close();
                    }
                }

            }

            return var5;
        } catch (IOException var17) {
            throw new IllegalArgumentException("failed to parse array", var17);
        }
    }

    private static String arrayToParsableString(List<String> array) {
        try {
            XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
            builder.startArray();
            Iterator var2 = array.iterator();

            while(var2.hasNext()) {
                String element = (String)var2.next();
                builder.value(element);
            }

            builder.endArray();
            return builder.string();
        } catch (IOException var4) {
            throw new ElasticsearchException(var4);
        }
    }

    static void logSettingUpdate(Setting setting, Settings current, Settings previous, Logger logger) {
        if(logger.isInfoEnabled()) {
            if(setting.isFiltered()) {
                logger.info("updating [{}]", setting.key);
            } else {
                logger.info("updating [{}] from [{}] to [{}]", setting.key, setting.getRaw(previous), setting.getRaw(current));
            }
        }

    }

    public static Setting<Settings> groupSetting(String key, Setting.Property... properties) {
        return groupSetting(key, (s) -> {
        }, properties);
    }

    public static Setting<Settings> groupSetting(String key, Consumer<Settings> validator, Setting.Property... properties) {
        return new Setting.GroupSetting(key, validator, properties, null);
    }

    public static Setting<TimeValue> timeSetting(String key, Function<Settings, TimeValue> defaultValue, TimeValue minValue, Setting.Property... properties) {
        return new Setting(key, (s) -> {
            return ((TimeValue)defaultValue.apply(s)).getStringRep();
        }, (s) -> {
            TimeValue timeValue = TimeValue.parseTimeValue(s, (TimeValue)null, key);
            if(timeValue.millis() < minValue.millis()) {
                throw new IllegalArgumentException("Failed to parse value [" + s + "] for setting [" + key + "] must be >= " + minValue);
            } else {
                return timeValue;
            }
        }, properties);
    }

    public static Setting<TimeValue> timeSetting(String key, TimeValue defaultValue, TimeValue minValue, Setting.Property... properties) {
        return timeSetting(key, (s) -> {
            return defaultValue;
        }, minValue, properties);
    }

    public static Setting<TimeValue> timeSetting(String key, TimeValue defaultValue, Setting.Property... properties) {
        return new Setting(key, (s) -> {
            return defaultValue.getStringRep();
        }, (s) -> {
            return TimeValue.parseTimeValue(s, key);
        }, properties);
    }

    public static Setting<TimeValue> timeSetting(String key, Setting<TimeValue> fallbackSetting, Setting.Property... properties) {
        return new Setting(key, fallbackSetting, (s) -> {
            return TimeValue.parseTimeValue(s, key);
        }, properties);
    }

    public static Setting<TimeValue> positiveTimeSetting(String key, TimeValue defaultValue, Setting.Property... properties) {
        return timeSetting(key, defaultValue, TimeValue.timeValueMillis(0L), properties);
    }

    public static Setting<Double> doubleSetting(String key, double defaultValue, double minValue, Setting.Property... properties) {
        return new Setting(key, (s) -> {
            return Double.toString(defaultValue);
        }, (s) -> {
            double d = Double.parseDouble(s);
            if(d < minValue) {
                throw new IllegalArgumentException("Failed to parse value [" + s + "] for setting [" + key + "] must be >= " + minValue);
            } else {
                return Double.valueOf(d);
            }
        }, properties);
    }

    public boolean equals(Object o) {
        if(this == o) {
            return true;
        } else if(o != null && this.getClass() == o.getClass()) {
            Setting<?> setting = (Setting)o;
            return Objects.equals(this.key, setting.key);
        } else {
            return false;
        }
    }

    public int hashCode() {
        return Objects.hash(new Object[]{this.key});
    }

    public static <T> Setting.AffixSetting<T> prefixKeySetting(String prefix, Function<String, Setting<T>> delegateFactory) {
        return affixKeySetting(new Setting.AffixKey(prefix), delegateFactory, new Setting.AffixSetting[0]);
    }

    public static <T> Setting.AffixSetting<T> affixKeySetting(String prefix, String suffix, Function<String, Setting<T>> delegateFactory, Setting.AffixSetting... dependencies) {
        return affixKeySetting(new Setting.AffixKey(prefix, suffix), delegateFactory, dependencies);
    }

    private static <T> Setting.AffixSetting<T> affixKeySetting(Setting.AffixKey key, Function<String, Setting<T>> delegateFactory, Setting.AffixSetting... dependencies) {
        Setting<T> delegate = (Setting)delegateFactory.apply("_na_");
        return new Setting.AffixSetting(key, delegate, delegateFactory, dependencies);
    }

    public static final class AffixKey implements Setting.Key {
        private final Pattern pattern;
        private final String prefix;
        private final String suffix;

        AffixKey(String prefix) {
            this(prefix, (String)null);
        }

        AffixKey(String prefix, String suffix) {
            assert prefix != null || suffix != null : "Either prefix or suffix must be non-null";

            this.prefix = prefix;
            if(!prefix.endsWith(".")) {
                throw new IllegalArgumentException("prefix must end with a '.'");
            } else {
                this.suffix = suffix;
                if(suffix == null) {
                    this.pattern = Pattern.compile("(" + Pattern.quote(prefix) + "((?:[-\\w]+[.])*[-\\w]+$))");
                } else {
                    this.pattern = Pattern.compile("(" + Pattern.quote(prefix) + "([-\\w]+)\\." + Pattern.quote(suffix) + ")(?:\\..*)?");
                }

            }
        }

        public boolean match(String key) {
            return this.pattern.matcher(key).matches();
        }

        String getConcreteString(String key) {
            Matcher matcher = this.pattern.matcher(key);
            if(!matcher.matches()) {
                throw new IllegalStateException("can't get concrete string for key " + key + " key doesn't match");
            } else {
                return matcher.group(1);
            }
        }

        String getNamespace(String key) {
            Matcher matcher = this.pattern.matcher(key);
            if(!matcher.matches()) {
                throw new IllegalStateException("can't get concrete string for key " + key + " key doesn't match");
            } else {
                return matcher.group(2);
            }
        }

        public Setting.SimpleKey toConcreteKey(String missingPart) {
            StringBuilder key = new StringBuilder();
            if(this.prefix != null) {
                key.append(this.prefix);
            }

            key.append(missingPart);
            if(this.suffix != null) {
                key.append(".");
                key.append(this.suffix);
            }

            return new Setting.SimpleKey(key.toString());
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();
            if(this.prefix != null) {
                sb.append(this.prefix);
            }

            if(this.suffix != null) {
                sb.append('*');
                sb.append('.');
                sb.append(this.suffix);
            }

            return sb.toString();
        }

        public boolean equals(Object o) {
            if(this == o) {
                return true;
            } else if(o != null && this.getClass() == o.getClass()) {
                Setting.AffixKey that = (Setting.AffixKey)o;
                return Objects.equals(this.prefix, that.prefix) && Objects.equals(this.suffix, that.suffix);
            } else {
                return false;
            }
        }

        public int hashCode() {
            return Objects.hash(new Object[]{this.prefix, this.suffix});
        }
    }

    public static final class ListKey extends Setting.SimpleKey {
        private final Pattern pattern;

        public ListKey(String key) {
            super(key);
            this.pattern = Pattern.compile(Pattern.quote(key) + "(\\.\\d+)?");
        }

        public boolean match(String toTest) {
            return this.pattern.matcher(toTest).matches();
        }
    }

    public static final class GroupKey extends Setting.SimpleKey {
        public GroupKey(String key) {
            super(key);
            if(!key.endsWith(".")) {
                throw new IllegalArgumentException("key must end with a '.'");
            }
        }

        public boolean match(String toTest) {
            return Regex.simpleMatch(this.key + "*", toTest);
        }
    }

    public static class SimpleKey implements Setting.Key {
        protected final String key;

        public SimpleKey(String key) {
            this.key = key;
        }

        public boolean match(String key) {
            return this.key.equals(key);
        }

        public String toString() {
            return this.key;
        }

        public boolean equals(Object o) {
            if(this == o) {
                return true;
            } else if(o != null && this.getClass() == o.getClass()) {
                Setting.SimpleKey simpleKey = (Setting.SimpleKey)o;
                return Objects.equals(this.key, simpleKey.key);
            } else {
                return false;
            }
        }

        public int hashCode() {
            return Objects.hash(new Object[]{this.key});
        }
    }

    public interface Key {
        boolean match(String var1);
    }

    private final class Updater implements SettingUpdater<T> {
        private final Consumer<T> consumer;
        private final Logger logger;
        private final Consumer<T> accept;

        Updater(Consumer<T> var1, Logger consumer, Consumer<T> logger) {
            this.consumer = consumer;
            this.logger = logger;
            this.accept = accept;
        }

        public String toString() {
            return "Updater for: " + Setting.this.toString();
        }

        public boolean hasChanged(Settings current, Settings previous) {
            String newValue = Setting.this.getRaw(current);
            String value = Setting.this.getRaw(previous);

            assert !Setting.this.isGroupSetting() : "group settings must override this method";

            assert value != null : "value was null but can't be unless default is null which is invalid";

            return !value.equals(newValue);
        }

        public T getValue(Settings current, Settings previous) {
            String newValue = Setting.this.getRaw(current);
            String value = Setting.this.getRaw(previous);

            try {
                T inst = Setting.this.get(current);
                this.accept.accept(inst);
                return inst;
            } catch (AssertionError | Exception var6) {
                throw new IllegalArgumentException("illegal value can't update [" + Setting.this.key + "] from [" + value + "] to [" + newValue + "]", var6);
            }
        }

        public void apply(T value, Settings current, Settings previous) {
            Setting.logSettingUpdate(Setting.this, current, previous, this.logger);
            this.consumer.accept(value);
        }
    }

    private static class ListSetting<T> extends Setting<List<T>> {
        private final Function<Settings, List<String>> defaultStringValue;

        private ListSetting(String key, Function<Settings, List<String>> defaultStringValue, Function<String, List<T>> parser, Setting.Property... properties) {
            super((Setting.Key)(new Setting.ListKey(key)), (Function)((s) -> {
                return Setting.arrayToParsableString((List)defaultStringValue.apply(s));
            }), parser, properties);
            this.defaultStringValue = defaultStringValue;
        }

        public String getRaw(Settings settings) {
            List<String> array = settings.getAsList(this.getKey(), (List)null);
            return array == null?(String)this.defaultValue.apply(settings):Setting.arrayToParsableString(array);
        }

        boolean hasComplexMatcher() {
            return true;
        }

        public void diff(Builder builder, Settings source, Settings defaultSettings) {
            if(!this.exists(source)) {
                List<String> asList = defaultSettings.getAsList(this.getKey(), (List)null);
                if(asList == null) {
                    builder.putList(this.getKey(), (List)this.defaultStringValue.apply(defaultSettings));
                } else {
                    builder.putList(this.getKey(), asList);
                }
            }

        }
    }

    private static class GroupSetting extends Setting<Settings> {
        private final String key;
        private final Consumer<Settings> validator;

        private GroupSetting(String key, Consumer<Settings> validator, Setting.Property... properties) {
            super((Setting.Key)(new Setting.GroupKey(key)), (Function)((s) -> {
                return "";
            }), (s) -> {
                return null;
            }, properties);
            this.key = key;
            this.validator = validator;
        }

        public boolean isGroupSetting() {
            return true;
        }

        public String getRaw(Settings settings) {
            Settings subSettings = this.get(settings);

            try {
                XContentBuilder builder = XContentFactory.jsonBuilder();
                builder.startObject();
                subSettings.toXContent(builder, EMPTY_PARAMS);
                builder.endObject();
                return builder.string();
            } catch (IOException var4) {
                throw new RuntimeException(var4);
            }
        }

        public Settings get(Settings settings) {
            Settings byPrefix = settings.getByPrefix(this.getKey());
            this.validator.accept(byPrefix);
            return byPrefix;
        }

        public boolean exists(Settings settings) {
            Iterator var2 = settings.keySet().iterator();

            String settingsKey;
            do {
                if(!var2.hasNext()) {
                    return false;
                }

                settingsKey = (String)var2.next();
            } while(!settingsKey.startsWith(this.key));

            return true;
        }

        public void diff(Builder builder, Settings source, Settings defaultSettings) {
            Set<String> leftGroup = this.get(source).keySet();
            Settings defaultGroup = this.get(defaultSettings);
            builder.put(Settings.builder().put(defaultGroup.filter((k) -> {
                return !leftGroup.contains(k);
            }), false).normalizePrefix(this.getKey()).build(), false);
        }

        public SettingUpdater<Settings> newUpdater(final Consumer<Settings> consumer, final Logger logger, final Consumer<Settings> validator) {
            if(!this.isDynamic()) {
                throw new IllegalStateException("setting [" + this.getKey() + "] is not dynamic");
            } else {
                return new SettingUpdater<Settings>() {
                    public boolean hasChanged(Settings current, Settings previous) {
                        Settings currentSettings = GroupSetting.this.get(current);
                        Settings previousSettings = GroupSetting.this.get(previous);
                        return !currentSettings.equals(previousSettings);
                    }

                    public Settings getValue(Settings current, Settings previous) {
                        Settings currentSettings = GroupSetting.this.get(current);
                        Settings previousSettings = GroupSetting.this.get(previous);

                        try {
                            validator.accept(currentSettings);
                            return currentSettings;
                        } catch (AssertionError | Exception var6) {
                            throw new IllegalArgumentException("illegal value can't update [" + GroupSetting.this.key + "] from [" + previousSettings + "] to [" + currentSettings + "]", var6);
                        }
                    }

                    public void apply(Settings value, Settings current, Settings previous) {
                        Setting.logSettingUpdate(GroupSetting.this, current, previous, logger);
                        consumer.accept(value);
                    }

                    public String toString() {
                        return "Updater for: " + GroupSetting.this.toString();
                    }
                };
            }
        }
    }

    @FunctionalInterface
    public interface Validator<T> {
        void validate(T var1, Map<Setting<T>, T> var2);

        default Iterator<Setting<T>> settings() {
            return Collections.emptyIterator();
        }
    }

    public static class AffixSetting<T> extends Setting<T> {
        private final Setting.AffixKey key;
        private final Function<String, Setting<T>> delegateFactory;
        private final Set<Setting.AffixSetting> dependencies;

        public AffixSetting(Setting.AffixKey key, Setting<T> delegate, Function<String, Setting<T>> delegateFactory, Setting.AffixSetting... dependencies) {
            super((Setting.Key)key, (Function)delegate.defaultValue, delegate.parser, (Setting.Property[])delegate.properties.toArray(new Setting.Property[0]));
            this.key = key;
            this.delegateFactory = delegateFactory;
            this.dependencies = Collections.unmodifiableSet(new HashSet(Arrays.asList(dependencies)));
        }

        boolean isGroupSetting() {
            return true;
        }

        private Stream<String> matchStream(Settings settings) {
            Stream var10000 = settings.keySet().stream().filter(this::match);
            Setting.AffixKey var10001 = this.key;
            Objects.requireNonNull(this.key);
            return var10000.map(var10001::getConcreteString);
        }

        public Set<String> getSettingsDependencies(String settingsKey) {
            if(this.dependencies.isEmpty()) {
                return Collections.emptySet();
            } else {
                String namespace = this.key.getNamespace(settingsKey);
                return (Set)this.dependencies.stream().map((s) -> {
                    return s.key.toConcreteKey(namespace).key;
                }).collect(Collectors.toSet());
            }
        }

        SettingUpdater<Map<SettingUpdater<T>, T>> newAffixUpdater(final BiConsumer<String, T> consumer, final Logger logger, final BiConsumer<String, T> validator) {
            return new SettingUpdater<Map<SettingUpdater<T>, T>>() {
                public boolean hasChanged(Settings current, Settings previous) {
                    return Stream.concat(AffixSetting.this.matchStream(current), AffixSetting.this.matchStream(previous)).findAny().isPresent();
                }

                public Map<SettingUpdater<T>, T> getValue(Settings current, Settings previous) {
                    Map<SettingUpdater<T>, T> result = new IdentityHashMap();
                    Stream.concat(AffixSetting.this.matchStream(current), AffixSetting.this.matchStream(previous)).distinct().forEach((aKey) -> {
                        String namespace = AffixSetting.this.key.getNamespace(aKey);
                        Setting<T> concreteSetting = AffixSetting.this.getConcreteSetting(aKey);
                        SettingUpdater<T> updater = concreteSetting.newUpdater((v) -> {
                            consumer.accept(namespace, v);
                        }, logger, (v) -> {
                            validator.accept(namespace, v);
                        });
                        if(updater.hasChanged(current, previous)) {
                            T value = updater.getValue(current, previous);
                            result.put(updater, value);
                        }

                    });
                    return result;
                }

                public void apply(Map<SettingUpdater<T>, T> value, Settings current, Settings previous) {
                    Iterator var4 = value.entrySet().iterator();

                    while(var4.hasNext()) {
                        Entry<SettingUpdater<T>, T> entry = (Entry)var4.next();
                        ((SettingUpdater)entry.getKey()).apply(entry.getValue(), current, previous);
                    }

                }
            };
        }

        SettingUpdater<Map<String, T>> newAffixMapUpdater(final Consumer<Map<String, T>> consumer, final Logger logger, final BiConsumer<String, T> validator, final boolean omitDefaults) {
            return new SettingUpdater<Map<String, T>>() {
                public boolean hasChanged(Settings current, Settings previous) {
                    return !current.filter((k) -> {
                        return AffixSetting.this.match(k);
                    }).equals(previous.filter((k) -> {
                        return AffixSetting.this.match(k);
                    }));
                }

                public Map<String, T> getValue(Settings current, Settings previous) {
                    Map<String, T> result = new IdentityHashMap();
                    Stream.concat(AffixSetting.this.matchStream(current), AffixSetting.this.matchStream(previous)).distinct().forEach((aKey) -> {
                        String namespace = AffixSetting.this.key.getNamespace(aKey);
                        Setting<T> concreteSetting = AffixSetting.this.getConcreteSetting(aKey);
                        SettingUpdater<T> updater = concreteSetting.newUpdater((v) -> {
                        }, logger, (v) -> {
                            validator.accept(namespace, v);
                        });
                        if(updater.hasChanged(current, previous)) {
                            T value = updater.getValue(current, previous);
                            if(!omitDefaults || !value.equals(concreteSetting.getDefault(current))) {
                                result.put(namespace, value);
                            }
                        }

                    });
                    return result;
                }

                public void apply(Map<String, T> value, Settings current, Settings previous) {
                    consumer.accept(value);
                }
            };
        }

        public T get(Settings settings) {
            throw new UnsupportedOperationException("affix settings can't return values use #getConcreteSetting to obtain a concrete setting");
        }

        public String getRaw(Settings settings) {
            throw new UnsupportedOperationException("affix settings can't return values use #getConcreteSetting to obtain a concrete setting");
        }

        public Setting<T> getConcreteSetting(String key) {
            if(this.match(key)) {
                return (Setting)this.delegateFactory.apply(key);
            } else {
                throw new IllegalArgumentException("key [" + key + "] must match [" + this.getKey() + "] but didn't.");
            }
        }

        public Setting<T> getConcreteSettingForNamespace(String namespace) {
            String fullKey = this.key.toConcreteKey(namespace).toString();
            return this.getConcreteSetting(fullKey);
        }

        public void diff(Builder builder, Settings source, Settings defaultSettings) {
            this.matchStream(defaultSettings).forEach((key) -> {
                this.getConcreteSetting(key).diff(builder, source, defaultSettings);
            });
        }

        public String getNamespace(Setting<T> concreteSetting) {
            return this.key.getNamespace(concreteSetting.getKey());
        }

        public Stream<Setting<T>> getAllConcreteSettings(Settings settings) {
            return this.matchStream(settings).distinct().map(this::getConcreteSetting);
        }

        public Set<String> getNamespaces(Settings settings) {
            Stream var10000 = settings.keySet().stream().filter(this::match);
            Setting.AffixKey var10001 = this.key;
            Objects.requireNonNull(this.key);
            return (Set)var10000.map(var10001::getNamespace).collect(Collectors.toSet());
        }

        public Map<String, T> getAsMap(Settings settings) {
            Map<String, T> map = new HashMap();
            this.matchStream(settings).distinct().forEach((key) -> {
                Setting<T> concreteSetting = this.getConcreteSetting(key);
                map.put(this.getNamespace(concreteSetting), concreteSetting.get(settings));
            });
            return Collections.unmodifiableMap(map);
        }
    }

    public static enum Property {
        Filtered,
        Dynamic,
        Final,
        Deprecated,
        NodeScope,
        IndexScope;

        private Property() {
        }
    }
}
