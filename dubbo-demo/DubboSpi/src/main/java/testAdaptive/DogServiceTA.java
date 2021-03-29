package testAdaptive;

import org.apache.dubbo.common.URL;

/**
 * @author JokerLee
 * @version V1.0
 * @Description:
 * @date 2021/3/24
 */
public class DogServiceTA implements AnimalServiceTA {
    @Override
    public void say() {
        System.out.println("dog say woo woo");
    }

    @Override
    public void show(URL url) {
        System.out.println("dog" + url.toString());
    }

    @Override
    public void run(AnimalTA animal) {
        System.out.println("dog" + animal.getUrl().toString());
    }
}
