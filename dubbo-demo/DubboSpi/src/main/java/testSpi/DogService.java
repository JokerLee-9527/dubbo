package testSpi;

/**
 * @author JokerLee
 * @version V1.0
 * @Description: spi.DogService
 * @date 2021/3/24
 */
public class DogService implements AnimalService {

    @Override
    public void say() {
        System.out.println("dog say woo woo");
    }

}