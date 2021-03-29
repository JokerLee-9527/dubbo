package testAdaptive2;

import org.apache.dubbo.common.URL;

/**
 * @author JokerLee
 * @version V1.0
 * @Description: SpringCloudAdaptiveExt
 * @date 2021/3/25
 */
public class SpringCloudAdaptiveExt implements AdaptiveExt {
    @Override
    public String echo(String msg, URL url) {
        return "spring cloud";
    }
}
