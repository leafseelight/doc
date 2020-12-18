BeanMetadataElement
	Bean元数据元素的抽象接口，抽象基本的访问元数据(source配置源)的能力
	Object getSource();

BeanMetadataAttribute implements BeanMetadataElement
	Bean元数据属性的封装类
	
------------------------------------------------------------------------------------------------------------
AttributeAccessor 
	对对象元数据访问的抽象接口
	void setAttribute(String name, @Nullable Object value);
	@Nullable
	Object getAttribute(String name);
	@Nullable
	Object removeAttribute(String name);
	boolean hasAttribute(String name);
	String[] attributeNames();

AttributeAccessorSupport implements AttributeAccessor
	持有元数据，通过内部的LinkedHashMap实现了操作元数据的所有方法
	
BeanMetadataAttributeAccessor extends AttributeAccessorSupport implements BeanMetadataElement
	持有Bean的配置源，比如xml文件的bean标签对应的对象或者@Configuration元配置对象
	实现了对bean元数据属性访问的方法
	
------------------------------------------------------------------------------------------------------------
【BeanDefinition】 extends AttributeAccessor, BeanMetadataElement	
	BeanDefinition的基础定义 主要功能是允许BeanFactoryPostProcessor
	
AnnotatedBeanDefinition extends BeanDefinition
	可注解的bean定义 抽象了获取注解元数据和方法元数据，它不需要加载该类即可访问
	AnnotationMetadata getMetadata();
	MethodMetadata getFactoryMethodMetadata();	

【AbstractBeanDefinition】 extends BeanMetadataAttributeAccessor implements BeanDefinition, Cloneable
	BeanDefinition的抽象实现

【RootBeanDefinition】 extends AbstractBeanDefinition	
	可合并的bean definition
	
	【ConfigurationClassBeanDefinition】 extends RootBeanDefinition implements AnnotatedBeanDefinition 
		【@Bean注解方式定义的bean加载后的definition就是ConfigurationClassBeanDefinition】
		ConfigurationClassBeanDefinitionReader的一个私有的静态内部类，【将@Bean注解的方法转换为对应的ConfigurationClassBeanDefinition类】
		如果@Bean注解没有指定bean的名字，默认会用方法的名字命名bean
		@Configuration注解的类会成为一个工厂类，而所有的@Bean注解的方法会成为工厂方法，通过工厂方法实例化Bean，而不是直接通过构造函数初始化
	
ChildBeanDefinition extends AbstractBeanDefinition	
	子bean definition 它可以继承它父类的设置。推荐使用GenericBeanDefinition
	
GenericBeanDefinition extends AbstractBeanDefinition	
	通用/标准的bean定义 可以设置parent bean definition
	持有一个parentName属性，所有操作已经在AbstractBeanDefinition实现
	【xml配置方式被加载进来的原始bean定义就是GenericBeanDefinition】
	
	AnnotatedGenericBeanDefinition extends GenericBeanDefinition implements AnnotatedBeanDefinition	
		持有注解元数据和工厂方法元数据，只能用于已经被注册或被扫描到的类，可以用来获取注解信息
		
	ScannedGenericBeanDefinition extends GenericBeanDefinition implements AnnotatedBeanDefinition	
		存储@Component、@Service、@Controller等注解注释的类
	
	
//
bean初始化时，bean definition会都转成RootBeanDefinition，然后进行初始化
