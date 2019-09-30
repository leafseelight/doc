//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package java.lang;

import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public class ThreadLocal<T> {
    private final int threadLocalHashCode = nextHashCode();
    private static AtomicInteger nextHashCode = new AtomicInteger();
    private static final int HASH_INCREMENT = 1640531527;   //0x61c88647

    private static int nextHashCode() {
        return nextHashCode.getAndAdd(HASH_INCREMENT);
    }

    protected T initialValue() {
        return null;
    }

    public static <S> ThreadLocal<S> withInitial(Supplier<? extends S> var0) {
        return new ThreadLocal.SuppliedThreadLocal(var0);
    }

    public ThreadLocal() {
    }

	// 获取当前threadLocal变量保存的值 如果不存在 返回null
    public T get() {
        Thread var1 = Thread.currentThread();
        ThreadLocal.ThreadLocalMap var2 = this.getMap(var1);
        if (var2 != null) {
            ThreadLocal.ThreadLocalMap.Entry var3 = var2.getEntry(this);
            if (var3 != null) {
                Object var4 = var3.value;
                return var4;
            }
        }

        return this.setInitialValue(); // 设置初始值
    }

    private T setInitialValue() {
        Object var1 = this.initialValue();
        Thread var2 = Thread.currentThread();
        ThreadLocal.ThreadLocalMap var3 = this.getMap(var2);
        if (var3 != null) {
            var3.set(this, var1);   // var1为null
        } else {
            this.createMap(var2, var1);
        }

        return var1;
    }

	// 给当前threadLocal变量设置值
    public void set(T var1) {
        Thread var2 = Thread.currentThread();
        ThreadLocal.ThreadLocalMap var3 = this.getMap(var2);
        if (var3 != null) {
            var3.set(this, var1);
        } else {
            this.createMap(var2, var1);
        }

    }

	// 删除threadLocal变量 底层就是删除threadLocalMap中保存的数据
    public void remove() {
        ThreadLocal.ThreadLocalMap var1 = this.getMap(Thread.currentThread());
        if (var1 != null) {
            var1.remove(this);
        }

    }

	// 获取当前线程中保存的threadLocalMap
    ThreadLocal.ThreadLocalMap getMap(Thread var1) {
        return var1.threadLocals;
    }

	// 创建threadLocalMap
    void createMap(Thread var1, T var2) {
        var1.threadLocals = new ThreadLocal.ThreadLocalMap(this, var2);
    }

    static ThreadLocal.ThreadLocalMap createInheritedMap(ThreadLocal.ThreadLocalMap var0) {
        return new ThreadLocal.ThreadLocalMap(var0);
    }

    T childValue(T var1) {
        throw new UnsupportedOperationException();
    }

    static final class SuppliedThreadLocal<T> extends ThreadLocal<T> {
        private final Supplier<? extends T> supplier;

        SuppliedThreadLocal(Supplier<? extends T> var1) {
            this.supplier = (Supplier)Objects.requireNonNull(var1);
        }

        protected T initialValue() {
            return this.supplier.get();
        }
    }

    static class ThreadLocalMap {
        private static final int INITIAL_CAPACITY = 16;     // 初始化数组大小
        private ThreadLocal.ThreadLocalMap.Entry[] table;   // 内部用数组保存线程局部变量的键值对
        private int size;                                   // 保存的threadLocal数
        private int threshold;                              // 容量

        private void setThreshold(int var1) {
            this.threshold = var1 * 2 / 3;
        }

        private static int nextIndex(int var0, int var1) {
            return var0 + 1 < var1 ? var0 + 1 : 0;
        }

        private static int prevIndex(int var0, int var1) {
            return var0 - 1 >= 0 ? var0 - 1 : var1 - 1;
        }

		// 构造
        ThreadLocalMap(ThreadLocal<?> var1, Object var2) {
            this.size = 0;
            this.table = new ThreadLocal.ThreadLocalMap.Entry[INITIAL_CAPACITY];  // 初始化一个数组用来保存threadLocal变量 数组的类型为Entry key为threalLocal变量名 value为变量值 初始化数组大小为16
            int var3 = var1.threadLocalHashCode & 15;    // 15二进制：1111  用threadLocal变量的hashCode值位与1111 得到的结果(范围在0-15之间)作为该变量(封装后)存到数组中的位置(索引)
            this.table[var3] = new ThreadLocal.ThreadLocalMap.Entry(var1, var2);
            this.size = 1;
            this.setThreshold(16);
        }

		// 私有构造 整理ThreadLocalMap
        private ThreadLocalMap(ThreadLocal.ThreadLocalMap var1) {
            this.size = 0;
            ThreadLocal.ThreadLocalMap.Entry[] var2 = var1.table;
            int var3 = var2.length;
            this.setThreshold(var3);
            this.table = new ThreadLocal.ThreadLocalMap.Entry[var3];

            for(int var4 = 0; var4 < var3; ++var4) {
                ThreadLocal.ThreadLocalMap.Entry var5 = var2[var4];
                if (var5 != null) {
                    ThreadLocal var6 = (ThreadLocal)var5.get();
                    if (var6 != null) {
                        Object var7 = var6.childValue(var5.value);
                        ThreadLocal.ThreadLocalMap.Entry var8 = new ThreadLocal.ThreadLocalMap.Entry(var6, var7);

                        int var9;
                        for(var9 = var6.threadLocalHashCode & var3 - 1; this.table[var9] != null; var9 = nextIndex(var9, var3)) {
                            ;
                        }

                        this.table[var9] = var8;
                        ++this.size;
                    }
                }
            }

        }

		// 根据当前threadLocal变量值获取entry键值对
        private ThreadLocal.ThreadLocalMap.Entry getEntry(ThreadLocal<?> var1) {
            int var2 = var1.threadLocalHashCode & this.table.length - 1;
            ThreadLocal.ThreadLocalMap.Entry var3 = this.table[var2];
            return var3 != null && var3.get() == var1 ? var3 : this.getEntryAfterMiss(var1, var2, var3);
        }

        private ThreadLocal.ThreadLocalMap.Entry getEntryAfterMiss(ThreadLocal<?> var1, int var2, ThreadLocal.ThreadLocalMap.Entry var3) {
            ThreadLocal.ThreadLocalMap.Entry[] var4 = this.table;

            for(int var5 = var4.length; var3 != null; var3 = var4[var2]) {
                ThreadLocal var6 = (ThreadLocal)var3.get();
                if (var6 == var1) {
                    return var3;
                }

                if (var6 == null) {
                    this.expungeStaleEntry(var2);
                } else {
                    var2 = nextIndex(var2, var5);
                }
            }

            return null;
        }

        private void set(ThreadLocal<?> var1, Object var2) {
            ThreadLocal.ThreadLocalMap.Entry[] var3 = this.table;
            int var4 = var3.length;
            int var5 = var1.threadLocalHashCode & var4 - 1;   // - 优先级大于 &  // 计算当前threadLocal变量在数组中的位置

            for(ThreadLocal.ThreadLocalMap.Entry var6 = var3[var5]; var6 != null; var6 = var3[var5 = nextIndex(var5, var4)]) {
                ThreadLocal var7 = (ThreadLocal)var6.get();
                if (var7 == var1) {
                    var6.value = var2;  // 如果已经存在则设置值
                    return;
                }

                if (var7 == null) {
                    this.replaceStaleEntry(var1, var2, var5);
                    return;
                }
            }
			// 不存在则设置值 并将size自增
            var3[var5] = new ThreadLocal.ThreadLocalMap.Entry(var1, var2);
            int var8 = ++this.size;
            if (!this.cleanSomeSlots(var5, var8) && var8 >= this.threshold) {
                this.rehash();
            }

        }

        private void remove(ThreadLocal<?> var1) {
            ThreadLocal.ThreadLocalMap.Entry[] var2 = this.table;
            int var3 = var2.length;
            int var4 = var1.threadLocalHashCode & var3 - 1;

            for(ThreadLocal.ThreadLocalMap.Entry var5 = var2[var4]; var5 != null; var5 = var2[var4 = nextIndex(var4, var3)]) {
                if (var5.get() == var1) {
                    var5.clear();
                    this.expungeStaleEntry(var4);
                    return;
                }
            }

        }

		// 替换脏entry
        private void replaceStaleEntry(ThreadLocal<?> var1, Object var2, int var3) {
            ThreadLocal.ThreadLocalMap.Entry[] var4 = this.table;
            int var5 = var4.length;
            int var7 = var3;

            ThreadLocal.ThreadLocalMap.Entry var6;
            int var8;
            for(var8 = prevIndex(var3, var5); (var6 = var4[var8]) != null; var8 = prevIndex(var8, var5)) {
                if (var6.get() == null) {
                    var7 = var8;
                }
            }

            for(var8 = nextIndex(var3, var5); (var6 = var4[var8]) != null; var8 = nextIndex(var8, var5)) {
                ThreadLocal var9 = (ThreadLocal)var6.get();
                if (var9 == var1) {
                    var6.value = var2;
                    var4[var8] = var4[var3];
                    var4[var3] = var6;
                    if (var7 == var3) {
                        var7 = var8;
                    }

                    this.cleanSomeSlots(this.expungeStaleEntry(var7), var5);
                    return;
                }

                if (var9 == null && var7 == var3) {
                    var7 = var8;
                }
            }

            var4[var3].value = null;
            var4[var3] = new ThreadLocal.ThreadLocalMap.Entry(var1, var2);
            if (var7 != var3) {
                this.cleanSomeSlots(this.expungeStaleEntry(var7), var5);
            }

        }

		// 清除脏entry
        private int expungeStaleEntry(int var1) {
            ThreadLocal.ThreadLocalMap.Entry[] var2 = this.table;
            int var3 = var2.length;
            var2[var1].value = null;
            var2[var1] = null;
            --this.size;

            ThreadLocal.ThreadLocalMap.Entry var4;
            int var5;
            for(var5 = nextIndex(var1, var3); (var4 = var2[var5]) != null; var5 = nextIndex(var5, var3)) {
                ThreadLocal var6 = (ThreadLocal)var4.get();
                if (var6 == null) {
                    var4.value = null;
                    var2[var5] = null;
                    --this.size;
                } else {
                    int var7 = var6.threadLocalHashCode & var3 - 1;
                    if (var7 != var5) {
                        for(var2[var5] = null; var2[var7] != null; var7 = nextIndex(var7, var3)) {
                            ;
                        }

                        var2[var7] = var4;
                    }
                }
            }

            return var5;
        }

        private boolean cleanSomeSlots(int var1, int var2) {
            boolean var3 = false;
            ThreadLocal.ThreadLocalMap.Entry[] var4 = this.table;
            int var5 = var4.length;

            do {
                var1 = nextIndex(var1, var5);
                ThreadLocal.ThreadLocalMap.Entry var6 = var4[var1];
                if (var6 != null && var6.get() == null) {
                    var2 = var5;
                    var3 = true;
                    var1 = this.expungeStaleEntry(var1);
                }
            } while((var2 >>>= 1) != 0);

            return var3;
        }

        private void rehash() {
            this.expungeStaleEntries();
            if (this.size >= this.threshold - this.threshold / 4) {
                this.resize();
            }

        }

        private void resize() {
            ThreadLocal.ThreadLocalMap.Entry[] var1 = this.table;
            int var2 = var1.length;
            int var3 = var2 * 2;
            ThreadLocal.ThreadLocalMap.Entry[] var4 = new ThreadLocal.ThreadLocalMap.Entry[var3];
            int var5 = 0;

            for(int var6 = 0; var6 < var2; ++var6) {
                ThreadLocal.ThreadLocalMap.Entry var7 = var1[var6];
                if (var7 != null) {
                    ThreadLocal var8 = (ThreadLocal)var7.get();
                    if (var8 == null) {
                        var7.value = null;
                    } else {
                        int var9;
                        for(var9 = var8.threadLocalHashCode & var3 - 1; var4[var9] != null; var9 = nextIndex(var9, var3)) {
                            ;
                        }

                        var4[var9] = var7;
                        ++var5;
                    }
                }
            }

            this.setThreshold(var3);
            this.size = var5;
            this.table = var4;
        }

        private void expungeStaleEntries() {
            ThreadLocal.ThreadLocalMap.Entry[] var1 = this.table;
            int var2 = var1.length;

            for(int var3 = 0; var3 < var2; ++var3) {
                ThreadLocal.ThreadLocalMap.Entry var4 = var1[var3];
                if (var4 != null && var4.get() == null) {
                    this.expungeStaleEntry(var3);
                }
            }

        }

        static class Entry extends WeakReference<ThreadLocal<?>> {
            Object value;

            Entry(ThreadLocal<?> var1, Object var2) {
                super(var1);                // threadLocal变量作为key
                this.value = var2;
            }
        }
    }
}
