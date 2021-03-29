package testAdaptive2;

import org.apache.dubbo.common.URL;

/**
 * 单元测试3中加上@Adaptive注解,其余不加
 *
 * @author JokerLee
 * @version V1.0
 * @Description:
 * @date 2021/3/25
 */
public class ThriftAdaptiveExt implements AdaptiveExt {
    @Override
    public String echo(String msg, URL url) {
        return "thrift";
    }
}
