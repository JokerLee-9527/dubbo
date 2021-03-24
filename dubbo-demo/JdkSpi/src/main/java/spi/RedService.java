package spi;

/**
 * @author JokerLee
 * @version V1.0
 * @Description: RedService
 * @date 2021/3/23
 */
public class RedService implements DubboService {
    @Override
    public void sayHello() {
        System.out.println("我是RedService服务实现");
    }
}
