package wrapper;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.junit.Test;


/**
 * todo 这个测试case问题. wrapper没搞懂
 *
 * @author JokerLee
 * @version V1.0
 * @Description:
 * @date 2021/3/26
 */
public class WrapperTest {

    @Test
    public void test1(){
        ExtensionLoader<AdaptiveExt> loader = ExtensionLoader.getExtensionLoader(AdaptiveExt.class);
        AdaptiveExt adaptiveExtension = loader.getExtension("dubbo");
        URL url = URL.valueOf("test://localhost/test");
        adaptiveExtension.echo("d", url);
    }

}
