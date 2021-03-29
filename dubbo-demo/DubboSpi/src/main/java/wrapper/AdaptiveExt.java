package wrapper;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.Adaptive;
import org.apache.dubbo.common.extension.SPI;

/**
 * @author JokerLee
 * @version V1.0
 * @Description: AdaptiveExt
 * @date 2021/3/25
 */
@SPI("dubbo")
public interface AdaptiveExt {

    // 单元测试方法4的注解为@Adaptive({"t"})
    // @Adaptive({"t"})
    // case4 URL.valueOf("test://localhost/test?t=cloud") 输出 spring cloud --> t=cloud有效
    // case2 URL.valueOf("test://localhost/test?adaptive.ext=cloud");  输出 dubbo --> adaptive.ext=cloud无效,  @SPI("dubbo")起效果

    // 单元测试方法2的注解为@Adaptive
    // @Adaptive
    // case4 URL.valueOf("test://localhost/test?t=cloud") 输出 dubbo --> t=cloud无效 @SPI("dubbo")起效果
    // case2 URL.valueOf("test://localhost/test?adaptive.ext=cloud");  输出 spring cloud --> adaptive.ext=cloud有效

    @Adaptive
    String echo(String msg, URL url);
}
