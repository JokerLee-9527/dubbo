package spi.invoke;

import spi.DubboService;

/**
 * @author JokerLee
 * @version V1.0
 * @Description: RedService
 * @date 2021/3/23
 */
public class BlackService implements DubboService {
    @Override
    public void sayHello() {
        System.out.println("我是Black服务实现");
    }
}
