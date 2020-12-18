package org.springframework.beans.factory.support;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.beans.BeanMetadataAttributeAccessor;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.core.io.DescriptiveResource;
import org.springframework.core.io.Resource;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

public abstract class AbstractBeanDefinition extends BeanMetadataAttributeAccessor implements BeanDefinition, Cloneable {
    public static final String SCOPE_DEFAULT = "";				// 默认bean的作用域
    public static final int AUTOWIRE_NO = 0;					// 注入模式 不自动装配(需手动注入)/名称/类型/构造/自动检测(尝试优先使用类型注入，然后使用构造注入)
    public static final int AUTOWIRE_BY_NAME = 1;
    public static final int AUTOWIRE_BY_TYPE = 2;
    public static final int AUTOWIRE_CONSTRUCTOR = 3;
    /** @deprecated */
    @Deprecated
    public static final int AUTOWIRE_AUTODETECT = 4;
    public static final int DEPENDENCY_CHECK_NONE = 0;			// 依赖检查模式 NONE 无依赖/OBJECTS 对象间的引用/SIMPLE 检查所有的原始类型和String类型的属性/ALL 检查所有属性
    public static final int DEPENDENCY_CHECK_OBJECTS = 1;
    public static final int DEPENDENCY_CHECK_SIMPLE = 2;
    public static final int DEPENDENCY_CHECK_ALL = 3;
    public static final String INFER_METHOD = "(inferred)";		//
    @Nullable
    private volatile Object beanClass;							// bean实例 可能是实例，可能是bean的名称 <bean class="">
    @Nullable
    private String scope;										// 作用域 <bean scope="singleton/prototype">
    private boolean abstractFlag;								// 是否是抽象类
    private boolean lazyInit;									// 是否懒加载 <bean lazy-init="true/false">
    private int autowireMode;									// 注入模式
    private int dependencyCheck;								// 依赖检查模式
    @Nullable
    private String[] dependsOn;									// 依赖的bean @DependsOn  <bean depends-on="">
    private boolean autowireCandidate;							// 是否是自动装配的候选者  <bean autowire-candidate="true/false">
    private boolean primary;									// 是否是优先的bean 主候选的bean，同类型多个bean实例时优先  @Primary
    private final Map<String, AutowireCandidateQualifier> qualifiers; // 记录Qualifier
    @Nullable
    private Supplier<?> instanceSupplier;
    private boolean nonPublicAccessAllowed;						// 是否允许非公开的构造方法
    private boolean lenientConstructorResolution;				// 是否以宽松方式解析构造函数
    @Nullable
    private String factoryBeanName;								// 工厂bean名称 <bean factory-bean="">
    @Nullable
    private String factoryMethodName;							// 工厂方法名称 <bean factory-method="">
    @Nullable
    private ConstructorArgumentValues constructorArgumentValues;// bean的构造参数值 <constructor-arg>
    @Nullable
    private MutablePropertyValues propertyValues;				// 普通属性集合 <property>
    @Nullable
    private MethodOverrides methodOverrides;					// 方法重写的持有者，记录 lookup-method，replaced-method元素
    @Nullable
    private String initMethodName;								// init-method方法的名称
    @Nullable
    private String destroyMethodName;							// destroy-mothod方法的名称
    private boolean enforceInitMethod;							// 是否执行initMethod
    private boolean enforceDestroyMethod;						// 是否执行destroyMethod
    private boolean synthetic;									// 是否是合成的bean
    private int role;											// 角色 0：用户 1：复杂配置 2：内部使用
    @Nullable
    private String description;									// bean的描述
    @Nullable
    private Resource resource;									// bean定义的来源Resource

	// 空参构造
    protected AbstractBeanDefinition() {
        this((ConstructorArgumentValues)null, (MutablePropertyValues)null);
    }

	// 指定的构造函数的参数值和属性值
    protected AbstractBeanDefinition(@Nullable ConstructorArgumentValues cargs, @Nullable MutablePropertyValues pvs) {
        this.scope = "";							// 默认单例
        this.abstractFlag = false;
        this.lazyInit = false;						// 默认非懒加载
        this.autowireMode = 0;						// 默认不自动装配
        this.dependencyCheck = 0;
        this.autowireCandidate = true;
        this.primary = false;
        this.qualifiers = new LinkedHashMap();
        this.nonPublicAccessAllowed = true;			// 默认允许访问非public构造
        this.lenientConstructorResolution = true;
        this.enforceInitMethod = true;
        this.enforceDestroyMethod = true;
        this.synthetic = false;
        this.role = 0;
        this.constructorArgumentValues = cargs;
        this.propertyValues = pvs;
    }

	// 深拷贝构造
    protected AbstractBeanDefinition(BeanDefinition original) {
        this.scope = "";
        this.abstractFlag = false;
        this.lazyInit = false;
        this.autowireMode = 0;
        this.dependencyCheck = 0;
        this.autowireCandidate = true;
        this.primary = false;
        this.qualifiers = new LinkedHashMap();
        this.nonPublicAccessAllowed = true;
        this.lenientConstructorResolution = true;
        this.enforceInitMethod = true;
        this.enforceDestroyMethod = true;
        this.synthetic = false;
        this.role = 0;
        this.setParentName(original.getParentName());
        this.setBeanClassName(original.getBeanClassName());
        this.setScope(original.getScope());
        this.setAbstract(original.isAbstract());
        this.setLazyInit(original.isLazyInit());
        this.setFactoryBeanName(original.getFactoryBeanName());
        this.setFactoryMethodName(original.getFactoryMethodName());
        this.setRole(original.getRole());
        this.setSource(original.getSource());
        this.copyAttributesFrom(original);
        if (original instanceof AbstractBeanDefinition) {
            AbstractBeanDefinition originalAbd = (AbstractBeanDefinition)original;
            if (originalAbd.hasBeanClass()) {
                this.setBeanClass(originalAbd.getBeanClass());
            }

            if (originalAbd.hasConstructorArgumentValues()) {
                this.setConstructorArgumentValues(new ConstructorArgumentValues(original.getConstructorArgumentValues()));
            }

            if (originalAbd.hasPropertyValues()) {
                this.setPropertyValues(new MutablePropertyValues(original.getPropertyValues()));
            }

            if (originalAbd.hasMethodOverrides()) {
                this.setMethodOverrides(new MethodOverrides(originalAbd.getMethodOverrides()));
            }

            this.setAutowireMode(originalAbd.getAutowireMode());
            this.setDependencyCheck(originalAbd.getDependencyCheck());
            this.setDependsOn(originalAbd.getDependsOn());
            this.setAutowireCandidate(originalAbd.isAutowireCandidate());
            this.setPrimary(originalAbd.isPrimary());
            this.copyQualifiersFrom(originalAbd);
            this.setInstanceSupplier(originalAbd.getInstanceSupplier());
            this.setNonPublicAccessAllowed(originalAbd.isNonPublicAccessAllowed());
            this.setLenientConstructorResolution(originalAbd.isLenientConstructorResolution());
            this.setInitMethodName(originalAbd.getInitMethodName());
            this.setEnforceInitMethod(originalAbd.isEnforceInitMethod());
            this.setDestroyMethodName(originalAbd.getDestroyMethodName());
            this.setEnforceDestroyMethod(originalAbd.isEnforceDestroyMethod());
            this.setSynthetic(originalAbd.isSynthetic());
            this.setResource(originalAbd.getResource());
        } else {
            this.setConstructorArgumentValues(new ConstructorArgumentValues(original.getConstructorArgumentValues()));
            this.setPropertyValues(new MutablePropertyValues(original.getPropertyValues()));
            this.setResourceDescription(original.getResourceDescription());
        }

    }

    public void overrideFrom(BeanDefinition other) {
        if (StringUtils.hasLength(other.getBeanClassName())) {
            this.setBeanClassName(other.getBeanClassName());
        }

        if (StringUtils.hasLength(other.getScope())) {
            this.setScope(other.getScope());
        }

        this.setAbstract(other.isAbstract());
        this.setLazyInit(other.isLazyInit());
        if (StringUtils.hasLength(other.getFactoryBeanName())) {
            this.setFactoryBeanName(other.getFactoryBeanName());
        }

        if (StringUtils.hasLength(other.getFactoryMethodName())) {
            this.setFactoryMethodName(other.getFactoryMethodName());
        }

        this.setRole(other.getRole());
        this.setSource(other.getSource());
        this.copyAttributesFrom(other);
        if (other instanceof AbstractBeanDefinition) {
            AbstractBeanDefinition otherAbd = (AbstractBeanDefinition)other;
            if (otherAbd.hasBeanClass()) {
                this.setBeanClass(otherAbd.getBeanClass());
            }

            if (otherAbd.hasConstructorArgumentValues()) {
                this.getConstructorArgumentValues().addArgumentValues(other.getConstructorArgumentValues());
            }

            if (otherAbd.hasPropertyValues()) {
                this.getPropertyValues().addPropertyValues(other.getPropertyValues());
            }

            if (otherAbd.hasMethodOverrides()) {
                this.getMethodOverrides().addOverrides(otherAbd.getMethodOverrides());
            }

            this.setAutowireMode(otherAbd.getAutowireMode());
            this.setDependencyCheck(otherAbd.getDependencyCheck());
            this.setDependsOn(otherAbd.getDependsOn());
            this.setAutowireCandidate(otherAbd.isAutowireCandidate());
            this.setPrimary(otherAbd.isPrimary());
            this.copyQualifiersFrom(otherAbd);
            this.setInstanceSupplier(otherAbd.getInstanceSupplier());
            this.setNonPublicAccessAllowed(otherAbd.isNonPublicAccessAllowed());
            this.setLenientConstructorResolution(otherAbd.isLenientConstructorResolution());
            if (otherAbd.getInitMethodName() != null) {
                this.setInitMethodName(otherAbd.getInitMethodName());
                this.setEnforceInitMethod(otherAbd.isEnforceInitMethod());
            }

            if (otherAbd.getDestroyMethodName() != null) {
                this.setDestroyMethodName(otherAbd.getDestroyMethodName());
                this.setEnforceDestroyMethod(otherAbd.isEnforceDestroyMethod());
            }

            this.setSynthetic(otherAbd.isSynthetic());
            this.setResource(otherAbd.getResource());
        } else {
            this.getConstructorArgumentValues().addArgumentValues(other.getConstructorArgumentValues());
            this.getPropertyValues().addPropertyValues(other.getPropertyValues());
            this.setResourceDescription(other.getResourceDescription());
        }

    }

    public void applyDefaults(BeanDefinitionDefaults defaults) {
        this.setLazyInit(defaults.isLazyInit());
        this.setAutowireMode(defaults.getAutowireMode());
        this.setDependencyCheck(defaults.getDependencyCheck());
        this.setInitMethodName(defaults.getInitMethodName());
        this.setEnforceInitMethod(false);
        this.setDestroyMethodName(defaults.getDestroyMethodName());
        this.setEnforceDestroyMethod(false);
    }

	// 设置bean类型的名称
    public void setBeanClassName(@Nullable String beanClassName) {
        this.beanClass = beanClassName;
    }

	// 获取bean的类型名称
    @Nullable
    public String getBeanClassName() {
        Object beanClassObject = this.beanClass;
        return beanClassObject instanceof Class ? ((Class)beanClassObject).getName() : (String)beanClassObject;
    }

	// 设置bean的类型
    public void setBeanClass(@Nullable Class<?> beanClass) {
        this.beanClass = beanClass;
    }

	// 获取bean的类型
    public Class<?> getBeanClass() throws IllegalStateException {
        Object beanClassObject = this.beanClass;
        if (beanClassObject == null) {
            throw new IllegalStateException("No bean class specified on bean definition");
        } else if (!(beanClassObject instanceof Class)) {
            throw new IllegalStateException("Bean class name [" + beanClassObject + "] has not been resolved into an actual Class");
        } else {
            return (Class)beanClassObject;
        }
    }

	// bean definition是否有class
    public boolean hasBeanClass() {
        return this.beanClass instanceof Class;
    }

	// 解析bean的类型
    @Nullable
    public Class<?> resolveBeanClass(@Nullable ClassLoader classLoader) throws ClassNotFoundException {
        String className = this.getBeanClassName();
        if (className == null) {
            return null;
        } else {
            Class<?> resolvedClass = ClassUtils.forName(className, classLoader);
            this.beanClass = resolvedClass;
            return resolvedClass;
        }
    }

    public void setScope(@Nullable String scope) {
        this.scope = scope;
    }

    @Nullable
    public String getScope() {
        return this.scope;
    }

    public boolean isSingleton() {
        return "singleton".equals(this.scope) || "".equals(this.scope);
    }

    public boolean isPrototype() {
        return "prototype".equals(this.scope);
    }

    public void setAbstract(boolean abstractFlag) {
        this.abstractFlag = abstractFlag;
    }

    public boolean isAbstract() {
        return this.abstractFlag;
    }

    public void setLazyInit(boolean lazyInit) {
        this.lazyInit = lazyInit;
    }

    public boolean isLazyInit() {
        return this.lazyInit;
    }

    public void setAutowireMode(int autowireMode) {
        this.autowireMode = autowireMode;
    }

    public int getAutowireMode() {
        return this.autowireMode;
    }

	// 解析注入模式，如果是4(自动检测)，则判断bean是否有空参构造，有的话返回2(按类型注入)，没有的话返回3(按构造注入)
    public int getResolvedAutowireMode() {
        if (this.autowireMode == 4) {
            Constructor<?>[] constructors = this.getBeanClass().getConstructors();
            Constructor[] var2 = constructors;
            int var3 = constructors.length;

            for(int var4 = 0; var4 < var3; ++var4) {
                Constructor<?> constructor = var2[var4];
                if (constructor.getParameterCount() == 0) {
                    return 2;
                }
            }

            return 3;
        } else {
            return this.autowireMode;
        }
    }

    public void setDependencyCheck(int dependencyCheck) {
        this.dependencyCheck = dependencyCheck;
    }

    public int getDependencyCheck() {
        return this.dependencyCheck;
    }

    public void setDependsOn(@Nullable String... dependsOn) {
        this.dependsOn = dependsOn;
    }

    @Nullable
    public String[] getDependsOn() {
        return this.dependsOn;
    }

    public void setAutowireCandidate(boolean autowireCandidate) {
        this.autowireCandidate = autowireCandidate;
    }

    public boolean isAutowireCandidate() {
        return this.autowireCandidate;
    }

    public void setPrimary(boolean primary) {
        this.primary = primary;
    }

    public boolean isPrimary() {
        return this.primary;
    }

    public void addQualifier(AutowireCandidateQualifier qualifier) {
        this.qualifiers.put(qualifier.getTypeName(), qualifier);
    }

    public boolean hasQualifier(String typeName) {
        return this.qualifiers.containsKey(typeName);
    }

    @Nullable
    public AutowireCandidateQualifier getQualifier(String typeName) {
        return (AutowireCandidateQualifier)this.qualifiers.get(typeName);
    }

    public Set<AutowireCandidateQualifier> getQualifiers() {
        return new LinkedHashSet(this.qualifiers.values());
    }

    public void copyQualifiersFrom(AbstractBeanDefinition source) {
        Assert.notNull(source, "Source must not be null");
        this.qualifiers.putAll(source.qualifiers);
    }

    public void setInstanceSupplier(@Nullable Supplier<?> instanceSupplier) {
        this.instanceSupplier = instanceSupplier;
    }

    @Nullable
    public Supplier<?> getInstanceSupplier() {
        return this.instanceSupplier;
    }

    public void setNonPublicAccessAllowed(boolean nonPublicAccessAllowed) {
        this.nonPublicAccessAllowed = nonPublicAccessAllowed;
    }

    public boolean isNonPublicAccessAllowed() {
        return this.nonPublicAccessAllowed;
    }

    public void setLenientConstructorResolution(boolean lenientConstructorResolution) {
        this.lenientConstructorResolution = lenientConstructorResolution;
    }

    public boolean isLenientConstructorResolution() {
        return this.lenientConstructorResolution;
    }

    public void setFactoryBeanName(@Nullable String factoryBeanName) {
        this.factoryBeanName = factoryBeanName;
    }

    @Nullable
    public String getFactoryBeanName() {
        return this.factoryBeanName;
    }

    public void setFactoryMethodName(@Nullable String factoryMethodName) {
        this.factoryMethodName = factoryMethodName;
    }

    @Nullable
    public String getFactoryMethodName() {
        return this.factoryMethodName;
    }

    public void setConstructorArgumentValues(ConstructorArgumentValues constructorArgumentValues) {
        this.constructorArgumentValues = constructorArgumentValues;
    }

    public ConstructorArgumentValues getConstructorArgumentValues() {
        if (this.constructorArgumentValues == null) {
            this.constructorArgumentValues = new ConstructorArgumentValues();
        }

        return this.constructorArgumentValues;
    }

    public boolean hasConstructorArgumentValues() {
        return this.constructorArgumentValues != null && !this.constructorArgumentValues.isEmpty();
    }

    public void setPropertyValues(MutablePropertyValues propertyValues) {
        this.propertyValues = propertyValues;
    }

    public MutablePropertyValues getPropertyValues() {
        if (this.propertyValues == null) {
            this.propertyValues = new MutablePropertyValues();
        }

        return this.propertyValues;
    }

    public boolean hasPropertyValues() {
        return this.propertyValues != null && !this.propertyValues.isEmpty();
    }

    public void setMethodOverrides(MethodOverrides methodOverrides) {
        this.methodOverrides = methodOverrides;
    }

    public MethodOverrides getMethodOverrides() {
        if (this.methodOverrides == null) {
            this.methodOverrides = new MethodOverrides();
        }

        return this.methodOverrides;
    }

    public boolean hasMethodOverrides() {
        return this.methodOverrides != null && !this.methodOverrides.isEmpty();
    }

    public void setInitMethodName(@Nullable String initMethodName) {
        this.initMethodName = initMethodName;
    }

    @Nullable
    public String getInitMethodName() {
        return this.initMethodName;
    }

    public void setEnforceInitMethod(boolean enforceInitMethod) {
        this.enforceInitMethod = enforceInitMethod;
    }

    public boolean isEnforceInitMethod() {
        return this.enforceInitMethod;
    }

    public void setDestroyMethodName(@Nullable String destroyMethodName) {
        this.destroyMethodName = destroyMethodName;
    }

    @Nullable
    public String getDestroyMethodName() {
        return this.destroyMethodName;
    }

    public void setEnforceDestroyMethod(boolean enforceDestroyMethod) {
        this.enforceDestroyMethod = enforceDestroyMethod;
    }

    public boolean isEnforceDestroyMethod() {
        return this.enforceDestroyMethod;
    }

    public void setSynthetic(boolean synthetic) {
        this.synthetic = synthetic;
    }

    public boolean isSynthetic() {
        return this.synthetic;
    }

    public void setRole(int role) {
        this.role = role;
    }

    public int getRole() {
        return this.role;
    }

    public void setDescription(@Nullable String description) {
        this.description = description;
    }

    @Nullable
    public String getDescription() {
        return this.description;
    }

    public void setResource(@Nullable Resource resource) {
        this.resource = resource;
    }

    @Nullable
    public Resource getResource() {
        return this.resource;
    }

	// 设置该bean定义的资源的描述（用于显示错误情况下的上下文）
    public void setResourceDescription(@Nullable String resourceDescription) {
        this.resource = resourceDescription != null ? new DescriptiveResource(resourceDescription) : null;
    }

    @Nullable
    public String getResourceDescription() {
        return this.resource != null ? this.resource.getDescription() : null;
    }

    public void setOriginatingBeanDefinition(BeanDefinition originatingBd) {
        this.resource = new BeanDefinitionResource(originatingBd);
    }

    @Nullable
    public BeanDefinition getOriginatingBeanDefinition() {
        return this.resource instanceof BeanDefinitionResource ? ((BeanDefinitionResource)this.resource).getBeanDefinition() : null;
    }

	// 校验 方法重写和静态工厂方法不能组合
    public void validate() throws BeanDefinitionValidationException {
        if (this.hasMethodOverrides() && this.getFactoryMethodName() != null) {
            throw new BeanDefinitionValidationException("Cannot combine static factory method with method overrides: the static factory method must create the instance");
        } else {
            if (this.hasBeanClass()) {
                this.prepareMethodOverrides();
            }

        }
    }

	// 开始准备验证这个bean定义的重写方法，检查指定名称的方法是否存在
    public void prepareMethodOverrides() throws BeanDefinitionValidationException {
        if (this.hasMethodOverrides()) {
            Set<MethodOverride> overrides = this.getMethodOverrides().getOverrides();
            synchronized(overrides) {
                Iterator var3 = overrides.iterator();

                while(var3.hasNext()) {
                    MethodOverride mo = (MethodOverride)var3.next();
                    this.prepareMethodOverride(mo);
                }
            }
        }

    }

	//检查指定名称的方法是否存在，如果没有找到，则标记为未重载。
    protected void prepareMethodOverride(MethodOverride mo) throws BeanDefinitionValidationException {
        int count = ClassUtils.getMethodCountForName(this.getBeanClass(), mo.getMethodName());
        if (count == 0) {
            throw new BeanDefinitionValidationException("Invalid method override: no method with name '" + mo.getMethodName() + "' on class [" + this.getBeanClassName() + "]");
        } else {
            if (count == 1) {
				//标记不是重载，以避免类型检查的开销
                mo.setOverloaded(false);
            }

        }
    }

    public Object clone() {
        return this.cloneBeanDefinition();
    }

    public abstract AbstractBeanDefinition cloneBeanDefinition();

    public boolean equals(Object other) {
        if (this == other) {
            return true;
        } else if (!(other instanceof AbstractBeanDefinition)) {
            return false;
        } else {
            AbstractBeanDefinition that = (AbstractBeanDefinition)other;
            boolean rtn = ObjectUtils.nullSafeEquals(this.getBeanClassName(), that.getBeanClassName());
            rtn &= ObjectUtils.nullSafeEquals(this.scope, that.scope);
            rtn &= this.abstractFlag == that.abstractFlag;
            rtn &= this.lazyInit == that.lazyInit;
            rtn &= this.autowireMode == that.autowireMode;
            rtn &= this.dependencyCheck == that.dependencyCheck;
            rtn &= Arrays.equals(this.dependsOn, that.dependsOn);
            rtn &= this.autowireCandidate == that.autowireCandidate;
            rtn &= ObjectUtils.nullSafeEquals(this.qualifiers, that.qualifiers);
            rtn &= this.primary == that.primary;
            rtn &= this.nonPublicAccessAllowed == that.nonPublicAccessAllowed;
            rtn &= this.lenientConstructorResolution == that.lenientConstructorResolution;
            rtn &= ObjectUtils.nullSafeEquals(this.constructorArgumentValues, that.constructorArgumentValues);
            rtn &= ObjectUtils.nullSafeEquals(this.propertyValues, that.propertyValues);
            rtn &= ObjectUtils.nullSafeEquals(this.methodOverrides, that.methodOverrides);
            rtn &= ObjectUtils.nullSafeEquals(this.factoryBeanName, that.factoryBeanName);
            rtn &= ObjectUtils.nullSafeEquals(this.factoryMethodName, that.factoryMethodName);
            rtn &= ObjectUtils.nullSafeEquals(this.initMethodName, that.initMethodName);
            rtn &= this.enforceInitMethod == that.enforceInitMethod;
            rtn &= ObjectUtils.nullSafeEquals(this.destroyMethodName, that.destroyMethodName);
            rtn &= this.enforceDestroyMethod == that.enforceDestroyMethod;
            rtn &= this.synthetic == that.synthetic;
            rtn &= this.role == that.role;
            return rtn && super.equals(other);
        }
    }

    public int hashCode() {
        int hashCode = ObjectUtils.nullSafeHashCode(this.getBeanClassName());
        hashCode = 29 * hashCode + ObjectUtils.nullSafeHashCode(this.scope);
        hashCode = 29 * hashCode + ObjectUtils.nullSafeHashCode(this.constructorArgumentValues);
        hashCode = 29 * hashCode + ObjectUtils.nullSafeHashCode(this.propertyValues);
        hashCode = 29 * hashCode + ObjectUtils.nullSafeHashCode(this.factoryBeanName);
        hashCode = 29 * hashCode + ObjectUtils.nullSafeHashCode(this.factoryMethodName);
        hashCode = 29 * hashCode + super.hashCode();
        return hashCode;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("class [");
        sb.append(this.getBeanClassName()).append("]");
        sb.append("; scope=").append(this.scope);
        sb.append("; abstract=").append(this.abstractFlag);
        sb.append("; lazyInit=").append(this.lazyInit);
        sb.append("; autowireMode=").append(this.autowireMode);
        sb.append("; dependencyCheck=").append(this.dependencyCheck);
        sb.append("; autowireCandidate=").append(this.autowireCandidate);
        sb.append("; primary=").append(this.primary);
        sb.append("; factoryBeanName=").append(this.factoryBeanName);
        sb.append("; factoryMethodName=").append(this.factoryMethodName);
        sb.append("; initMethodName=").append(this.initMethodName);
        sb.append("; destroyMethodName=").append(this.destroyMethodName);
        if (this.resource != null) {
            sb.append("; defined in ").append(this.resource.getDescription());
        }

        return sb.toString();
    }
}
