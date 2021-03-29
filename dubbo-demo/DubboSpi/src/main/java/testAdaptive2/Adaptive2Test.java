package testAdaptive2;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.junit.Test;

/**
 * @author JokerLee
 * @version V1.0
 * @Description:
 * @date 2021/3/25
 */
public class Adaptive2Test {

    /**
     * 优先级顺序
     * @Adaptive标注的类 > URL参数 > @SPI注解中的类@SPI("dubbo")
     *
     */

    /**
     * 先打开下面的注释 META-INF/dubbo/testAdaptive2.AdaptiveExt中
     * #thrift_case3=testAdaptive2.ThriftAdaptiveExtCase3 -> thrift_case3=testAdaptive2.ThriftAdaptiveExtCase3
     *
     * SPI上有注解,@SPI("dubbo"),URL中也有具体的值,ThriftAdaptiveExtCase3实现类上面有@Adaptive注解,输出thrift case 3
     *
     * 测试其他case要注释掉
     * thrift_case3=testAdaptive2.ThriftAdaptiveExtCase3 -> #thrift_case3=testAdaptive2.ThriftAdaptiveExtCase3
     */
    @Test
    public void test3(){
        ExtensionLoader<AdaptiveExt> loader = ExtensionLoader.getExtensionLoader(AdaptiveExt.class);
        AdaptiveExt adaptiveExtension = loader.getAdaptiveExtension();
        URL url = URL.valueOf("test://localhost/test?adaptive.ext=cloud");
        System.out.println(adaptiveExtension.echo("d", url));
    }



    /**
     * SPI上有注解,@SPI("dubbo"),URL中也有具体的值,输出spring cloud,注意这里对方法标注有@Adaptive注解,
     * 但是该注解没有值
     *
     * 从测试2看出,方法有注解@Adaptive,同时URL配置的是默认参数,该参数时通过AdaptiveExt通过转小写生成(adaptive.ext=cloud),
     * 则创建的就是cloud对应类的实例,可以看出,其实测试2和4类似,只要URL中有参数并且配置正确,则忽略@SPI注解上的值
     *
     */
    @Test
    public void test2(){
        ExtensionLoader<AdaptiveExt> loader = ExtensionLoader.getExtensionLoader(AdaptiveExt.class);
        AdaptiveExt adaptiveExtension = loader.getAdaptiveExtension();
        URL url = URL.valueOf("test://localhost/test?adaptive.ext=cloud");
        System.out.println(adaptiveExtension.echo("d", url));
    }

    /**
     * SPI上有注解,@SPI("dubbo"),URL中也有具体的值,接口方法中加上注解@Adaptive({"t"}),各个实现类上面没有
     * @Adaptive注解,输出spring cloud
     *
     * 从测试4看出,若方法上有注解@Adpative({"t"}),则URL中应当配上该参数t=cloud,创建cloud对应的实例
     *
     */
    @Test
    public void test4(){
        ExtensionLoader<AdaptiveExt> loader = ExtensionLoader.getExtensionLoader(AdaptiveExt.class);
        AdaptiveExt adaptiveExtension = loader.getAdaptiveExtension();
        URL url = URL.valueOf("test://localhost/test?t=cloud");
        System.out.println(adaptiveExtension.echo("d", url));
    }

    @Test
    public void test4_(){
        ExtensionLoader<AdaptiveExt> loader = ExtensionLoader.getExtensionLoader(AdaptiveExt.class);
//        Map<String, String> map = new HashMap<>();
//        // t这个key就是根据@Adaptive("t")定的,两者要一致
//        map.put("t", "cloud");
//        URL url = new URL("", "", 1, map);
        URL url = URL.valueOf("test://localhost/test?t=cloud");
        AdaptiveExt adaptiveExtension = loader.getExtension("dubbo");
        System.out.println(adaptiveExtension.echo("d", url));
    }


    /**
     * SPI上有注解,@SPI("dubbo"),url无参数,没有类上添加@Adaptive注解的类,方法@Adaptive注解上无参数,输出dubbo
     */
    @Test
    public void test1(){
        ExtensionLoader<AdaptiveExt> loader = ExtensionLoader.getExtensionLoader(AdaptiveExt.class);
        AdaptiveExt adaptiveExtension = loader.getAdaptiveExtension();
        URL url = URL.valueOf("test://localhost/test");
        System.out.println(adaptiveExtension.echo("d", url));
    }

///////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * 与上面case的没有关系
     */
    @Test
    public void testOther(){
        ExtensionLoader<AdaptiveExt> loader = ExtensionLoader.getExtensionLoader(AdaptiveExt.class);
        AdaptiveExt adaptiveExtension = loader.getExtension("dubbo");
        URL url = URL.valueOf("test://localhost/test");
        System.out.println(adaptiveExtension.echo("d", url));
    }



}
