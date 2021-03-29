package spi;

/**
 * @author JokerLee
 * @version V1.0
 * @Description: RedService
 * @date 2021/3/23
 */
public class BlueService implements DubboService {
    @Override
    public void sayHello() {
        System.out.println("我是New BlueService服务实现");
    }
}
