package org.springframework.beans.factory.support;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.core.ResolvableType;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

public class RootBeanDefinition extends AbstractBeanDefinition {
    @Nullable
    private BeanDefinitionHolder decoratedDefinition;		// bean定义的持有者
    @Nullable
    private AnnotatedElement qualifiedElement;
    boolean allowCaching = true;							// 是否允许缓存
    boolean isFactoryMethodUnique = false;					// 是否已经指定引用非重载方法的工厂方法名
    @Nullable
    volatile ResolvableType targetType;
    @Nullable
    volatile Class<?> resolvedTargetType;					// bean的Class类型
    @Nullable
    volatile ResolvableType factoryMethodReturnType;
    @Nullable
    volatile Method factoryMethodToIntrospect;				// 已解析的工厂方法
    final Object constructorArgumentLock = new Object();	// 用于构造函数注入属性的锁对象
    @Nullable
    Executable resolvedConstructorOrFactoryMethod;			// 缓存已解析的构造函数或工厂方法
    boolean constructorArgumentsResolved = false;			// 将构造函数参数标记为已解析
    @Nullable
    Object[] resolvedConstructorArguments;					// 缓存完全解析的构造函数参数
    @Nullable
    Object[] preparedConstructorArguments;					// 缓存部分准备好的构造函数参数
    final Object postProcessingLock = new Object();			// 后处理锁对象
    boolean postProcessed = false;							// 表明已应用mergedbeandefinitionpostprocessor
    @Nullable 
    volatile Boolean beforeInstantiationResolved;			// 指示已启动的一个实例化之前的后置处理器
    @Nullable
    private Set<Member> externallyManagedConfigMembers;
    @Nullable
    private Set<String> externallyManagedInitMethods;
    @Nullable
    private Set<String> externallyManagedDestroyMethods;

    public RootBeanDefinition() {
    }

    public RootBeanDefinition(@Nullable Class<?> beanClass) {
        this.setBeanClass(beanClass);
    }

    public <T> RootBeanDefinition(@Nullable Class<T> beanClass, @Nullable Supplier<T> instanceSupplier) {
        this.setBeanClass(beanClass);
        this.setInstanceSupplier(instanceSupplier);
    }

    public <T> RootBeanDefinition(@Nullable Class<T> beanClass, String scope, @Nullable Supplier<T> instanceSupplier) {
        this.setBeanClass(beanClass);
        this.setScope(scope);
        this.setInstanceSupplier(instanceSupplier);
    }

    public RootBeanDefinition(@Nullable Class<?> beanClass, int autowireMode, boolean dependencyCheck) {
        this.setBeanClass(beanClass);
        this.setAutowireMode(autowireMode);
        if (dependencyCheck && this.getResolvedAutowireMode() != 3) {
            this.setDependencyCheck(1);
        }

    }

    public RootBeanDefinition(@Nullable Class<?> beanClass, @Nullable ConstructorArgumentValues cargs, @Nullable MutablePropertyValues pvs) {
        super(cargs, pvs);
        this.setBeanClass(beanClass);
    }

    public RootBeanDefinition(String beanClassName) {
        this.setBeanClassName(beanClassName);
    }

    public RootBeanDefinition(String beanClassName, ConstructorArgumentValues cargs, MutablePropertyValues pvs) {
        super(cargs, pvs);
        this.setBeanClassName(beanClassName);
    }

    public RootBeanDefinition(RootBeanDefinition original) {
        super(original);
        this.decoratedDefinition = original.decoratedDefinition;
        this.qualifiedElement = original.qualifiedElement;
        this.allowCaching = original.allowCaching;
        this.isFactoryMethodUnique = original.isFactoryMethodUnique;
        this.targetType = original.targetType;
    }

    RootBeanDefinition(BeanDefinition original) {
        super(original);
    }

    public String getParentName() {
        return null;
    }

    public void setParentName(@Nullable String parentName) {
        if (parentName != null) {
            throw new IllegalArgumentException("Root bean cannot be changed into a child bean with parent reference");
        }
    }

    public void setDecoratedDefinition(@Nullable BeanDefinitionHolder decoratedDefinition) {
        this.decoratedDefinition = decoratedDefinition;
    }

    @Nullable
    public BeanDefinitionHolder getDecoratedDefinition() {
        return this.decoratedDefinition;
    }

    public void setQualifiedElement(@Nullable AnnotatedElement qualifiedElement) {
        this.qualifiedElement = qualifiedElement;
    }

    @Nullable
    public AnnotatedElement getQualifiedElement() {
        return this.qualifiedElement;
    }

    public void setTargetType(ResolvableType targetType) {
        this.targetType = targetType;
    }

    public void setTargetType(@Nullable Class<?> targetType) {
        this.targetType = targetType != null ? ResolvableType.forClass(targetType) : null;
    }

    @Nullable
    public Class<?> getTargetType() {
        if (this.resolvedTargetType != null) {
            return this.resolvedTargetType;
        } else {
            ResolvableType targetType = this.targetType;
            return targetType != null ? targetType.resolve() : null;
        }
    }

    public ResolvableType getResolvableType() {
        ResolvableType targetType = this.targetType;
        return targetType != null ? targetType : ResolvableType.forClass(this.getBeanClass());
    }

    @Nullable
    public Constructor<?>[] getPreferredConstructors() {
        return null;
    }

	// 指定引用非重载方法的工厂方法名
    public void setUniqueFactoryMethodName(String name) {
        Assert.hasText(name, "Factory method name must not be empty");
        this.setFactoryMethodName(name);
        this.isFactoryMethodUnique = true;
    }

	// 检查给定的候选方法是否为工厂方法
    public boolean isFactoryMethod(Method candidate) {
        return candidate.getName().equals(this.getFactoryMethodName());
    }

	// 返回解析后的工厂方法作为Java对象方法,如果可用。
    @Nullable
    public Method getResolvedFactoryMethod() {
        return this.factoryMethodToIntrospect;
    }

    public void registerExternallyManagedConfigMember(Member configMember) {
        synchronized(this.postProcessingLock) {
            if (this.externallyManagedConfigMembers == null) {
                this.externallyManagedConfigMembers = new HashSet(1);
            }

            this.externallyManagedConfigMembers.add(configMember);
        }
    }

    public boolean isExternallyManagedConfigMember(Member configMember) {
        synchronized(this.postProcessingLock) {
            return this.externallyManagedConfigMembers != null && this.externallyManagedConfigMembers.contains(configMember);
        }
    }

    public void registerExternallyManagedInitMethod(String initMethod) {
        synchronized(this.postProcessingLock) {
            if (this.externallyManagedInitMethods == null) {
                this.externallyManagedInitMethods = new HashSet(1);
            }

            this.externallyManagedInitMethods.add(initMethod);
        }
    }

    public boolean isExternallyManagedInitMethod(String initMethod) {
        synchronized(this.postProcessingLock) {
            return this.externallyManagedInitMethods != null && this.externallyManagedInitMethods.contains(initMethod);
        }
    }

    public void registerExternallyManagedDestroyMethod(String destroyMethod) {
        synchronized(this.postProcessingLock) {
            if (this.externallyManagedDestroyMethods == null) {
                this.externallyManagedDestroyMethods = new HashSet(1);
            }

            this.externallyManagedDestroyMethods.add(destroyMethod);
        }
    }

    public boolean isExternallyManagedDestroyMethod(String destroyMethod) {
        synchronized(this.postProcessingLock) {
            return this.externallyManagedDestroyMethods != null && this.externallyManagedDestroyMethods.contains(destroyMethod);
        }
    }

    public RootBeanDefinition cloneBeanDefinition() {
        return new RootBeanDefinition(this);
    }

    public boolean equals(Object other) {
        return this == other || other instanceof RootBeanDefinition && super.equals(other);
    }

    public String toString() {
        return "Root bean: " + super.toString();
    }
}
