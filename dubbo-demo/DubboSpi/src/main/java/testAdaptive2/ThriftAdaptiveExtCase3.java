package testAdaptive2;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.Adaptive;

/**
 * 单元测试3中加上@Adaptive注解,其余不加
 *
 * @author JokerLee
 * @version V1.0
 * @Description:
 * @date 2021/3/25
 */
@Adaptive
public class ThriftAdaptiveExtCase3 implements AdaptiveExt {
    @Override
    public String echo(String msg, URL url) {
        return "thrift case 3";
    }
}
