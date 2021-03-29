package wrapper;

import org.apache.dubbo.common.URL;

/**
 * @author JokerLee
 * @version V1.0
 * @Description: DubboAdaptiveExt
 * @date 2021/3/26
 */
public class DubboAdaptiveExt implements AdaptiveExt {

    private AdaptiveExt adaptiveExt;

    // 此处DubboAdaptiveExt依赖注入一个AdaptiveExt 类型的实例,此处测试用例注入的是ThriftAdaptiveExt
    public void setAdaptiveExt(AdaptiveExt adaptiveExt) {
        this.adaptiveExt = adaptiveExt;
    }

    @Override
    public String echo(String msg, URL url) {
        System.out.println(this.adaptiveExt.echo(msg, url));
        return "dubbo";
    }
}
