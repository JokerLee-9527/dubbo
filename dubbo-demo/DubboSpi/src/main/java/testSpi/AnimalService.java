package testSpi;

//import com.alibaba.dubbo.common.extension.SPI;

import org.apache.dubbo.common.extension.SPI;

/**
 * @author JokerLee
 * @version V1.0
 * @Description: spi.AnimalService
 * @date 2021/3/24
 */
@SPI("dog")
public interface AnimalService {

    void say();

}