package testAdaptive;

import org.apache.dubbo.common.extension.ExtensionLoader;

/**
 * @author JokerLee
 * @version V1.0
 * @Description: AdaptiveTest
 * @date 2021/3/24
 */
public class AdaptiveTest {

    public static void main(String[] args) {

        ExtensionLoader<AnimalServiceTA> loader = ExtensionLoader.getExtensionLoader(AnimalServiceTA.class);

        /**
         *
         *  loader.getExtension("dog")
         *  loader.getExtension("true")
         *  loader.getDefaultExtension()
         *
         *  loader.getAdaptiveExtension()
         *  getAdaptiveExtension使用方法更加灵活
         *
         *
         */
        AnimalServiceTA animalServiceTA = loader.getAdaptiveExtension();

        /**
         * 返回CatServiceTA,CatServiceTA上标注了@Adaptive
         * {@link CatServiceTA}
         */
        System.out.println(animalServiceTA.getClass().getName());
    }

}
