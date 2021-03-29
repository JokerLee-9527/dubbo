package testSpi;


//import com.alibaba.dubbo.common.extension.ExtensionLoader;


import org.apache.dubbo.common.extension.ExtensionLoader;

/**
 * @author JokerLee
 * @version V1.0
 * @Description: SPITest
 * @date 2021/3/24
 */
public class SPITest {

    public static void main(String[] args) {

        ExtensionLoader<AnimalService> loader = ExtensionLoader.getExtensionLoader(AnimalService.class);
        // 创建DogService实例
        AnimalService animalService = loader.getExtension("dog");
        // 结果输出：dog say woo woo
        animalService.say();


//        /**
//         * 调用默认扩展实现类  @SPI("dog") {@link AnimalService}
//         */
//        ExtensionLoader<AnimalService> loader = ExtensionLoader.getExtensionLoader(AnimalService.class);
//
//        /**
//         * 1.方法一用"true"
//         *  {@link ExtensionLoader#getExtension(java.lang.String, boolean)}
//         *          if ("true".equals(name)) {
//         *             // 创建默认扩展点 #By Joker
//         *             return getDefaultExtension();
//         *         }
//         */
//        AnimalService service1 = loader.getExtension("true");
//
//        /**
//         * 2. 直接调用getDefaultExtension方法
//         * {@link ExtensionLoader#getDefaultExtension()}
//         *
//         */
//        AnimalService service2 = loader.getDefaultExtension();
//        service1.say();
//        service2.say();

    }


}
