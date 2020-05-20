#######
	简单写了一些规范 不定期更新

1.mapper.xml
	1) sql关键字大写 sql加注释【强制】
	
	2) 不要使用生成的 BaseResultMap Base_Column_List这种
		改成对应的例如userResultMap、userColumnList(所有标签的id属性值用驼峰命名)【强制】
		
	3) 新增用addXX  查询用getXXByXX  更新updateXX 删除deleteXX(逻辑删除 实际是update语句)【强制】
	
	4) 新增不要用动态sql 用写全的sql(id除外) 【强制】
	   新增加上主键回显的功能(useGeneratedKeys="true" keyProperty="id【对应主键】") 不要用自动生成的那个主键回显
	   自增主键id禁止在业务代码中使用(唯一可能用到的地方是单元测试)
	   
	5) 更新用动态sql 不需要更新的字段(比如userId create_time这种插进去后永远不会改变的就不要写在update语句中)【强制】
	   删除将del_flag置为1 并更新update_time等字段
	   所有查询记得加上del_flag=1的条件 del_flag 1表示删除 2表示未删除 del_flag默认值2【约定】【强制】
	   
	5) 尽量不要在查询的resultMap上使用collection这种写法 效率比较低 不如直接用关联查询(数据库对join会进行优化) 可以单独定义bean来接收查询结果
		因为是分布式的架构 禁止关联查非本模块的表【强制】 此时需要单独查 在app服务层进行数据聚合
		
	6) 需要分页查询的 添加一个map参数的查询方法 将查询参数(参考common模块的Query工具类的使用)设置到map中 mapper.xml的sql中加上LIMIT #{offset}, #{limit}就可以
	
	7) 原则上不能在循环中操作sql 对于insert update可以采用批量的方式【强制】
	
	8) 查询/更新语句可通用的可以通用，查询建议使用Map作为查询参数，方便配合Query处理分页问题
	   
	
2.数据库
	所有表 表名前缀 如耗材项目就用yxcg_ 【强制】
	
	表名 字段名使用下划线命名的方式 全小写 【强制】

	项目中使用了业务主键 而不是数据库默认的主键 (代码中一定不要用数据库主键id进行操作 统一用业务主键)【强制】 
	
	业务主键的生成方式：  service中注入IdWorker 调用generate方法 （不是sequence表的方式）
	
	del_flag create_time modify_time create_person update_person有这几个字段的 命名都统一成这样 不需要的就不管【基本上所有的表都需要del_flag create_time modify_time这三个字段】
	
	非空字段尽量加上NOT NULL约束
	字段的长度尽量合理
	
	状态字段：不要在数据库中用enum枚举类型 使用tinyint(1) 并在代码中使用Enum类型 在mybatis中对应使用TypeHandler 【强制】
	所有状态字段必须要有默认值【强制】
	所有状态字段的值从1开始 不能使用0(避免默认值是0产生的混淆) 可以使用-1等【强制】
	
	索引命名规则 idx_列名 如idx_user_id
	所有的表的业务主键必须建唯一索引 在建表时就创建 【强制】
	关联的其他表的业务主键按需可以建普通索引
	其他索引暂时不建 需要的时候再加
	
	数据库的时间存的是秒数int(11) bean里面是Long类型的 (不用之前的datetypehandler) 【强制】
	
	所有建表语句 包括后期的表结构修改 基础数据新增修改 统一提供出sql语句 所有sql必须带上库名【强制】
	提供建表sql语句时 将AUTO_INCREMENT改成1
	
	所有varchar类型必须指定编码为utf8mb4(支持的字符集比utf8更大)【强制】
	
	所有表、字段必须加注释 某些字段修改影响注释正确性的必须修改注释【强制】
	
3.controller、service和mapper接口

	生成的mapper接口中的方法参数名有些是record 改成对应的比如user【强制】
	
	接口上都添加注释	
	
	注入的时候使用@Autowored  如果service有多个实现类 再用@Qualify注解指定具体的实现类 不要用@Resource 【约定】【强制】
	
	controller中的接口路径前都加上/ 方便搜索
	
	
4.其他

	多校验 非空校验 格式(正则)校验 金额数量字段大于等于0 其他逻辑校验 【强制】

	所有的bean的字段使用驼峰命名 java代码中原则上不使用下划线 【强制】

	一般情况下 所有的属性(注解的属性 xml标签的属性等)也是驼峰命名 【强制】
	
	所有命名尽量合理 能表达出其含义 有些英文可以先百度一下
	
	所有的状态类型用枚举，枚举不要使用0这个值 可以使用1 2 3 -1等 参考已有的Delete枚举类 和对应的IntTypeHandler的使用 【强制】
	
	经常ctrl+shift+O一下 可以自动导包并删除掉没有用的导包  因为有时候修改了文件可能会留下无用的导包 （idea的快捷键可以设置为eclipse的）
	
	可扩展的东西尽量用可配置的方式
	
	pom.xml中添加依赖的时候 
		如果多个模块都需要 可以放在父pom.xml中的<dependencyManagement>中 子模块中再写一份 此时不用写version 
			【dependencyManagement】是maven用来管理依赖的版本的统一性问题的 所以在dependencyManagement定义了version 子模块中就不需要再写version
		类似于工具的依赖可以直接写在common的pom.xml中 其他模块依赖common即可
		
	会话用的是redis-token的方式实现的 没有用tomcat的session管理会话 
	
	代码中不要使用servlet/tomcat的session 【强制】
	
	service的基本方法写单元测试(最基本的测试增删改查)【强制】
	
	尽量使用原有工具类 不符合需求的可以修改扩展
	新加工具类尽量保证可用性 通用性
	
5.接口是restful风格的
	restful接口命名规范

	举例：
		新增商品  /goods                 POST方法
		修改商品  /goods/{goodsId}       POST方法 
		查询商品
			单个  /goods/{goodsId}       GET方法 
			列表  /goods	             GET方法
		删除商品     /goods/{goodsId}/del POST方法
		
		当查询参数比较多的时候 其他参数可以放到请求param或者body中
		非必传参数禁止放到路径参数中【强制】
		禁止路径参数前没有目录 比如/{goodsId}/del 不允许【强制】
		尽量按照restful的方式去命名接口路径 
	
6.接口文档用swagger注解方式
  swagger的使用可以参考以前项目 或者官方文档
  有兴趣的可以用json文件的方式写文档 但是要保证一个模块下的文档按一种方式
  
  动态文档支持直接调用controller接口 写完接口建议用文档或者postman测试下
  
7.json
	原则上不要混用各种json框架 推荐使用jackson 因为spring框架默认用的也是jackson
	自定义的RestResult使用了jackson 
	
	生成的自增主键是64位数值 js在反序列化的时候会丢失精度 
	因此返回的bean里所有的业务主键(包括关联的其他表的业务主键)上需要使用@JsonSerialize(using = LongToStringSerializer.class)) 【强制】
    原理就是使用该注解的Long类型字段在json序列化的时候会转成String类型