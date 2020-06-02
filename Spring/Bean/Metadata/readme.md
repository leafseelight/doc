ClassMetadata
	类元数据
	String getClassName();
    boolean isInterface();
    boolean isAnnotation();
    boolean isAbstract();
    boolean isConcrete();
    boolean isFinal();
    boolean isIndependent();
    boolean hasEnclosingClass();
    @Nullable
    String getEnclosingClassName();
    boolean hasSuperClass();
    @Nullable
    String getSuperClassName();
    String[] getInterfaceNames();
    String[] getMemberClassNames();
	
	
AnnotatedTypeMetadata
	注解类型元数据
	boolean isAnnotated(String var1);
    @Nullable
    Map<String, Object> getAnnotationAttributes(String var1);
    @Nullable
    Map<String, Object> getAnnotationAttributes(String var1, boolean var2);
    @Nullable
    MultiValueMap<String, Object> getAllAnnotationAttributes(String var1);
    @Nullable
    MultiValueMap<String, Object> getAllAnnotationAttributes(String var1, boolean var2);
	
	
AnnotationMetadata extends ClassMetadata, AnnotatedTypeMetadata
	注解元数据
	Set<String> getAnnotationTypes();
    Set<String> getMetaAnnotationTypes(String var1);
    boolean hasAnnotation(String var1);
    boolean hasMetaAnnotation(String var1);
    boolean hasAnnotatedMethods(String var1);
    Set<MethodMetadata> getAnnotatedMethods(String var1);
	
	
MethodMetadata extends AnnotatedTypeMetadata
	方法元数据
	String getMethodName();
    String getDeclaringClassName();
    String getReturnTypeName();
    boolean isAbstract();
    boolean isStatic();
    boolean isFinal();
    boolean isOverridable();
	
=============================================================================================================
ConfigurationMethod
	配置方法元数据，持有方法元数据和配置类信息
	
BeanMethod extends ConfigurationMethod
	bean 方法元数据 @Bean注解的方法不能是private或者final类型的