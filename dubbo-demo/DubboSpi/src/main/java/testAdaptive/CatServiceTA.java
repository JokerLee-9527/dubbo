package testAdaptive;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.Adaptive;

/**
 * @author JokerLee
 * @version V1.0
 * @Description:
 * @date 2021/3/24
 */
@Adaptive
public class CatServiceTA implements AnimalServiceTA {
    @Override
    public void say() {
        System.out.println("cat say miao miao");
    }

    @Override
    public void show(URL url) {
        System.out.println("cat" + url.toString());
    }

    @Override
    public void run(AnimalTA animal) {
        System.out.println("cat" + animal.getUrl().toString());
    }

}
