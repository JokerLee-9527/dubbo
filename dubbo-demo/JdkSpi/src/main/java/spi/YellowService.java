package spi;

/**
 * @author JokerLee
 * @version V1.0
 * @Description: YellowService
 * @date 2021/3/23
 */
public class YellowService  implements DubboService {
    @Override
    public void sayHello() {
        System.out.println("我是YellowService服务实现");
    }
}
