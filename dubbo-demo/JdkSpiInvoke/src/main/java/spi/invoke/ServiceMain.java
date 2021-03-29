package spi.invoke;

import spi.DubboService;

import java.util.Iterator;
import java.util.ServiceLoader;

/**
 * @author JokerLee
 * @version V1.0
 * @Description: ServiceMain
 * @date 2021/3/29
 */
public class ServiceMain {

    public static void main(String[] args) {

        ServiceLoader<DubboService> spiLoader = ServiceLoader.load(DubboService.class);
        Iterator<DubboService> iteratorSpi = spiLoader.iterator();
        while (iteratorSpi.hasNext()) {
            DubboService dubboService = iteratorSpi.next();
            dubboService.sayHello();
        }

    }
}
