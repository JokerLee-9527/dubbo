package testAdaptive;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.Adaptive;
import org.apache.dubbo.common.extension.SPI;

/**
 * @author JokerLee
 * @version V1.0
 * @Description:
 * @date 2021/3/24
 */
@SPI
public interface AnimalServiceTA {

    @Adaptive({"firstName"})
    void show(URL url);

    @Adaptive({"lastName"})
    void run(AnimalTA animal);

    void say();

}
