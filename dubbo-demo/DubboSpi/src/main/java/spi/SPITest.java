package spi;


import com.alibaba.dubbo.common.extension.ExtensionLoader;


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

    }


}
