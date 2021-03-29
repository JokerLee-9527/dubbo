/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.dubbo.common.extension;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.context.Lifecycle;
import org.apache.dubbo.common.extension.support.ActivateComparator;
import org.apache.dubbo.common.extension.support.WrapperComparator;
import org.apache.dubbo.common.lang.Prioritized;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.ArrayUtils;
import org.apache.dubbo.common.utils.ClassUtils;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.common.utils.ConcurrentHashSet;
import org.apache.dubbo.common.utils.ConfigUtils;
import org.apache.dubbo.common.utils.Holder;
import org.apache.dubbo.common.utils.ReflectUtils;
import org.apache.dubbo.common.utils.StringUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

import static java.util.Arrays.asList;
import static java.util.Collections.sort;
import static java.util.ServiceLoader.load;
import static java.util.stream.StreamSupport.stream;
import static org.apache.dubbo.common.constants.CommonConstants.COMMA_SPLIT_PATTERN;
import static org.apache.dubbo.common.constants.CommonConstants.DEFAULT_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.REMOVE_VALUE_PREFIX;


/**
 * 1. ExtensionLoader对象与@SPI注解的接口一一对应
 * @SPI("dog")
 * public interface AnimalService
 *
 * 2. ExtensionLoader<AnimalService> loader = ExtensionLoader.getExtensionLoader(AnimalService.class);
 * getExtensionLoader静态方法,记载ExtensionLoader(AnimalService)并缓存到静态数组中
 * private static final ConcurrentMap<Class<?>, ExtensionLoader<?>> EXTENSION_LOADERS = new ConcurrentHashMap<>(64);
 *
 * 静态数组中EXTENSION_LOADERS中的ExtensionLoader与@SPI注解的接口一一对应,起到一个缓存的作用
 *
 * 3. AnimalService animalService = loader.getExtension("dog");
 * 记载扩点的具体实现类(testSpi.DogService)
 *   3.1 获取扩展点的具体实现类的Holder:
 *       3.1.1 从private final ConcurrentMap<String, Holder<Object>> cachedInstances = new ConcurrentHashMap<>();查询
 *       3.1.2 如果没有就创建这个加载点的具体实现Class,并用Holder包裹,传到对象的cachedInstances这个Map中
 *             (注意: ConcurrentMap<String, Holder<Object>> cachedInstance 是对象级别的,一个接口(对应一个ExtensionLoader)可以有多个具体实现类)
 *
 *
 *
 *
 *
 *
 */

/**
 * {@link org.apache.dubbo.rpc.model.ApplicationModel}, {@code DubboBootstrap} and this class are
 * at present designed to be singleton or static (by itself totally static or uses some static fields).
 * So the instances returned from them are of process or classloader scope. If you want to support
 * multiple dubbo servers in a single process, you may need to refactor these three classes.
 * <p>
 * Load dubbo extensions
 * <ul>
 * <li>auto inject dependency extension </li>
 * <li>auto wrap extension in wrapper </li>
 * <li>default extension is an adaptive instance</li>
 * </ul>
 *
 * @see <a href="http://java.sun.com/j2se/1.5.0/docs/guide/jar/jar.html#Service%20Provider">Service Provider in Java 5</a>
 * @see org.apache.dubbo.common.extension.SPI
 * @see org.apache.dubbo.common.extension.Adaptive
 * @see org.apache.dubbo.common.extension.Activate
 */
public class ExtensionLoader<T> {

    private static final Logger logger = LoggerFactory.getLogger(ExtensionLoader.class);

    private static final Pattern NAME_SEPARATOR = Pattern.compile("\\s*[,]+\\s*");

    /**
     * 静态数组中EXTENSION_LOADERS中的ExtensionLoader与@SPI注解的接口一一对应,起到一个缓存的作用,不需要每次创建。
     * 静态数组是进程唯一的。
     */
    private static final ConcurrentMap<Class<?>, ExtensionLoader<?>> EXTENSION_LOADERS = new ConcurrentHashMap<>(64);

    /**
     * 扩增点的实现类,与内存中的实例(Instance)的对应关系.
     * 这个是Map是静态全局的,不仅仅是某个加载点(接口)的实现类,全部的.
     */
    private static final ConcurrentMap<Class<?>, Object> EXTENSION_INSTANCES = new ConcurrentHashMap<>(64);

    private final Class<?> type;

    /**
     * 扩增点ExtensionFactory
     * @SPI
     * public interface ExtensionFactory
     *
     * 每个ExtensionLoader(扩增点,被@PSI注解的接口)都要有个objectFactory, 除非本身就是ExtensionFactory.class的扩增点
     * 反过来讲: ExtensionFactory扩增点的objectFactory为null
     * {@link ExtensionLoader#ExtensionLoader(java.lang.Class)}
     *
     * 下面文件中记载了扩增点
     * dubbo-common\src\main\resources\META-INF\dubbo\internal\org.apache.dubbo.common.extension.ExtensionFactory
     * adaptive=org.apache.dubbo.common.extension.factory.AdaptiveExtensionFactory
     * spi=org.apache.dubbo.common.extension.factory.SpiExtensionFactory
     *
     * dubbo-config\dubbo-config-spring\src\main\resources\META-INF\dubbo\internal\org.apache.dubbo.common.extension.ExtensionFactory
     * spring=org.apache.dubbo.config.spring.extension.SpringExtensionFactory
     *
     * 该扩增点有3个实现类SpiExtensionFactory, AdaptiveExtensionFactory, SpringExtensionFactory
     *
     */
    private final ExtensionFactory objectFactory;

    private final ConcurrentMap<Class<?>, String> cachedNames = new ConcurrentHashMap<>();

    /**
     * 最外层是个Holder(类似于一个指针)
     * 然后是个map,创建是扩增点的名(dog,等号左边的dog=testAdaptive.DogServiceTA,而不是类名)和具体Class
     * 这个是ExtensionLoader对象级别, 每个扩增点(@PSI注解的接口)对应一组扩增点实现(接口实现)
     *
     */
    private final Holder<Map<String, Class<?>>> cachedClasses = new Holder<>();

    private final Map<String, Object> cachedActivates = new ConcurrentHashMap<>();

    /**
     *  加载所有的扩增点(接口interface)的实现类
     *  add的地方参照{@link ExtensionLoader#getExtensionClasses()}
     */
    private final ConcurrentMap<String, Holder<Object>> cachedInstances = new ConcurrentHashMap<>();
    private final Holder<Object> cachedAdaptiveInstance = new Holder<>();
    private volatile Class<?> cachedAdaptiveClass = null;
    private String cachedDefaultName;
    private volatile Throwable createAdaptiveInstanceError;

    private Set<Class<?>> cachedWrapperClasses;

    private Map<String, IllegalStateException> exceptions = new ConcurrentHashMap<>();

    /**
     *  strategy[0].directory(),strategy[1].directory(),strategy[2].directory()
     *  META-INF/dubbo/internal/   META-INF/dubbo/   META-INF/services/
     *
     *  loadLoadingStrategies()从才用了Jdk原生SPI,加载LoadingStrategy扩增点的具体实现,再通过实现类的directory()获取路径.
     *  采用这个方法,我觉得是可能考虑可以扩增吧! 还是比较奇怪的写法.
     */
    private static volatile LoadingStrategy[] strategies = loadLoadingStrategies();

    public static void setLoadingStrategies(LoadingStrategy... strategies) {
        if (ArrayUtils.isNotEmpty(strategies)) {
            ExtensionLoader.strategies = strategies;
        }
    }

    /**
     * Load all {@link Prioritized prioritized} {@link LoadingStrategy Loading Strategies} via {@link ServiceLoader}
     *
     * @return non-null
     * @since 2.7.7
     */
    private static LoadingStrategy[] loadLoadingStrategies() {
        return stream(load(LoadingStrategy.class).spliterator(), false)
                .sorted()
                .toArray(LoadingStrategy[]::new);
    }

    /**
     * Get all {@link LoadingStrategy Loading Strategies}
     *
     * @return non-null
     * @see LoadingStrategy
     * @see Prioritized
     * @since 2.7.7
     */
    public static List<LoadingStrategy> getLoadingStrategies() {
        return asList(strategies);
    }

    /**
     * 注意这个构造函数是private,也是无法在函数外部new这个对象的. 需要特别的方法创建
     * {@link ExtensionLoader#getExtensionLoader(java.lang.Class)}
     * 用该方法创建ExtensionLoader是想在{@link ExtensionLoader#EXTENSION_LOADERS}
     * 中缓存,避免每次创建.
     *
     * @PSI标注的接口与ExtensionLoader一一对应,并且缓存起来
     *
     * 注意除了
     *
     * #by Joker
     *
     */
    private ExtensionLoader(Class<?> type) {
        this.type = type;
        /**
         * 每个ExtensionLoader(扩增点,被@PSI注解的接口)都要有个objectFactory(ExtensionFactory),除非本身是ExtensionFactory.class的扩增点
         * 反过来讲: ExtensionFactory扩增点的objectFactory为null
         */
        objectFactory = (type == ExtensionFactory.class ? null : ExtensionLoader.getExtensionLoader(ExtensionFactory.class).getAdaptiveExtension());
    }

    private static <T> boolean withExtensionAnnotation(Class<T> type) {
        return type.isAnnotationPresent(SPI.class);
    }

    /**
     * 参考这个方法的说明
     * {@link ExtensionLoader#ExtensionLoader(java.lang.Class)}
     * #by Joker
     *
     */
    @SuppressWarnings("unchecked")
    public static <T> ExtensionLoader<T> getExtensionLoader(Class<T> type) {
        if (type == null) {
            throw new IllegalArgumentException("Extension type == null");
        }
        //必须是接口 #by Joker
        if (!type.isInterface()) {
            throw new IllegalArgumentException("Extension type (" + type + ") is not an interface!");
        }
        //必须@SPI注解 #by Joker
        if (!withExtensionAnnotation(type)) {
            throw new IllegalArgumentException("Extension type (" + type +
                    ") is not an extension, because it is NOT annotated with @" + SPI.class.getSimpleName() + "!");
        }

        // 从缓存中获取与拓展类对应的ExtensionLoader #By Joker
        ExtensionLoader<T> loader = (ExtensionLoader<T>) EXTENSION_LOADERS.get(type);
        if (loader == null) {
            // 若缓存未命中,则创建一个新的实例 #By Joker
            // 构造函数是private, ExtensionLoader对象通过getExtensionLoader方法创建
            EXTENSION_LOADERS.putIfAbsent(type, new ExtensionLoader<T>(type));
            loader = (ExtensionLoader<T>) EXTENSION_LOADERS.get(type);
        }
        return loader;
    }

    // For testing purposes only
    public static void resetExtensionLoader(Class type) {
        ExtensionLoader loader = EXTENSION_LOADERS.get(type);
        if (loader != null) {
            // Remove all instances associated with this loader as well
            Map<String, Class<?>> classes = loader.getExtensionClasses();
            for (Map.Entry<String, Class<?>> entry : classes.entrySet()) {
                EXTENSION_INSTANCES.remove(entry.getValue());
            }
            classes.clear();
            EXTENSION_LOADERS.remove(type);
        }
    }

    public static void destroyAll() {
        EXTENSION_INSTANCES.forEach((_type, instance) -> {
            if (instance instanceof Lifecycle) {
                Lifecycle lifecycle = (Lifecycle) instance;
                try {
                    lifecycle.destroy();
                } catch (Exception e) {
                    logger.error("Error destroying extension " + lifecycle, e);
                }
            }
        });
    }

    private static ClassLoader findClassLoader() {
        return ClassUtils.getClassLoader(ExtensionLoader.class);
    }

    public String getExtensionName(T extensionInstance) {
        return getExtensionName(extensionInstance.getClass());
    }

    public String getExtensionName(Class<?> extensionClass) {
        getExtensionClasses();// load class
        return cachedNames.get(extensionClass);
    }

    /**
     * This is equivalent to {@code getActivateExtension(url, key, null)}
     *
     * @param url url
     * @param key url parameter key which used to get extension point names
     * @return extension list which are activated.
     * @see #getActivateExtension(org.apache.dubbo.common.URL, String, String)
     */
    public List<T> getActivateExtension(URL url, String key) {
        return getActivateExtension(url, key, null);
    }

    /**
     * This is equivalent to {@code getActivateExtension(url, values, null)}
     *
     * @param url    url
     * @param values extension point names
     * @return extension list which are activated
     * @see #getActivateExtension(org.apache.dubbo.common.URL, String[], String)
     */
    public List<T> getActivateExtension(URL url, String[] values) {
        return getActivateExtension(url, values, null);
    }

    /**
     * This is equivalent to {@code getActivateExtension(url, url.getParameter(key).split(","), null)}
     *
     * @param url   url
     * @param key   url parameter key which used to get extension point names
     * @param group group
     * @return extension list which are activated.
     * @see #getActivateExtension(org.apache.dubbo.common.URL, String[], String)
     */
    public List<T> getActivateExtension(URL url, String key, String group) {
        String value = url.getParameter(key);
        return getActivateExtension(url, StringUtils.isEmpty(value) ? null : COMMA_SPLIT_PATTERN.split(value), group);
    }

    /**
     * Get activate extensions.
     *
     * @param url    url
     * @param values extension point names
     * @param group  group
     * @return extension list which are activated
     * @see org.apache.dubbo.common.extension.Activate
     */
    public List<T> getActivateExtension(URL url, String[] values, String group) {
        List<T> activateExtensions = new ArrayList<>();
        List<String> names = values == null ? new ArrayList<>(0) : asList(values);
        if (!names.contains(REMOVE_VALUE_PREFIX + DEFAULT_KEY)) {
            getExtensionClasses();
            for (Map.Entry<String, Object> entry : cachedActivates.entrySet()) {
                String name = entry.getKey();
                Object activate = entry.getValue();

                String[] activateGroup, activateValue;

                if (activate instanceof Activate) {
                    activateGroup = ((Activate) activate).group();
                    activateValue = ((Activate) activate).value();
                } else if (activate instanceof com.alibaba.dubbo.common.extension.Activate) {
                    activateGroup = ((com.alibaba.dubbo.common.extension.Activate) activate).group();
                    activateValue = ((com.alibaba.dubbo.common.extension.Activate) activate).value();
                } else {
                    continue;
                }
                if (isMatchGroup(group, activateGroup)
                        && !names.contains(name)
                        && !names.contains(REMOVE_VALUE_PREFIX + name)
                        && isActive(activateValue, url)) {
                    activateExtensions.add(getExtension(name));
                }
            }
            activateExtensions.sort(ActivateComparator.COMPARATOR);
        }
        List<T> loadedExtensions = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            if (!name.startsWith(REMOVE_VALUE_PREFIX)
                    && !names.contains(REMOVE_VALUE_PREFIX + name)) {
                if (DEFAULT_KEY.equals(name)) {
                    if (!loadedExtensions.isEmpty()) {
                        activateExtensions.addAll(0, loadedExtensions);
                        loadedExtensions.clear();
                    }
                } else {
                    loadedExtensions.add(getExtension(name));
                }
            }
        }
        if (!loadedExtensions.isEmpty()) {
            activateExtensions.addAll(loadedExtensions);
        }
        return activateExtensions;
    }

    private boolean isMatchGroup(String group, String[] groups) {
        if (StringUtils.isEmpty(group)) {
            return true;
        }
        if (groups != null && groups.length > 0) {
            for (String g : groups) {
                if (group.equals(g)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isActive(String[] keys, URL url) {
        if (keys.length == 0) {
            return true;
        }
        for (String key : keys) {
            // @Active(value="key1:value1, key2:value2")
            String keyValue = null;
            if (key.contains(":")) {
                String[] arr = key.split(":");
                key = arr[0];
                keyValue = arr[1];
            }

            for (Map.Entry<String, String> entry : url.getParameters().entrySet()) {
                String k = entry.getKey();
                String v = entry.getValue();
                if ((k.equals(key) || k.endsWith("." + key))
                        && ((keyValue != null && keyValue.equals(v)) || (keyValue == null && ConfigUtils.isNotEmpty(v)))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Get extension's instance. Return <code>null</code> if extension is not found or is not initialized. Pls. note
     * that this method will not trigger extension load.
     * <p>
     * In order to trigger extension load, call {@link #getExtension(String)} instead.
     *
     * @see #getExtension(String)
     */
    @SuppressWarnings("unchecked")
    public T getLoadedExtension(String name) {
        if (StringUtils.isEmpty(name)) {
            throw new IllegalArgumentException("Extension name == null");
        }
        Holder<Object> holder = getOrCreateHolder(name);
        return (T) holder.get();
    }

    private Holder<Object> getOrCreateHolder(String name) {
        Holder<Object> holder = cachedInstances.get(name);
        if (holder == null) {
            // 没有就创建个空的
            cachedInstances.putIfAbsent(name, new Holder<>());
            holder = cachedInstances.get(name);
        }
        return holder;
    }

    /**
     * Return the list of extensions which are already loaded.
     * <p>
     * Usually {@link #getSupportedExtensions()} should be called in order to get all extensions.
     *
     * @see #getSupportedExtensions()
     */
    public Set<String> getLoadedExtensions() {
        return Collections.unmodifiableSet(new TreeSet<>(cachedInstances.keySet()));
    }

    public List<T> getLoadedExtensionInstances() {
        List<T> instances = new ArrayList<>();
        cachedInstances.values().forEach(holder -> instances.add((T) holder.get()));
        return instances;
    }

    public Object getLoadedAdaptiveExtensionInstances() {
        return cachedAdaptiveInstance.get();
    }

//    public T getPrioritizedExtensionInstance() {
//        Set<String> supported = getSupportedExtensions();
//
//        Set<T> instances = new HashSet<>();
//        Set<T> prioritized = new HashSet<>();
//        for (String s : supported) {
//
//        }
//
//    }

    /**
     * Find the extension with the given name. If the specified name is not found, then {@link IllegalStateException}
     * will be thrown.
     */
    @SuppressWarnings("unchecked")
    public T getExtension(String name) {
        return getExtension(name, true);
    }

    public T getExtension(String name, boolean wrap) {
        if (StringUtils.isEmpty(name)) {
            throw new IllegalArgumentException("Extension name == null");
        }
        if ("true".equals(name)) {
            // 创建默认扩展点 #By Joker
            return getDefaultExtension();
        }
        // Holder也是用于持有对象的. 如果原没有cahce过,创建一个空的,并且缓存到
        // private final ConcurrentMap<String, Holder<Object>> cachedInstances = new ConcurrentHashMap<>();
        //
        // Holder有点象个C的指针,不用管内部存的什么对象. #By Joker
        final Holder<Object> holder = getOrCreateHolder(name);
        Object instance = holder.get();
        if (instance == null) {
            synchronized (holder) {
                instance = holder.get();
                if (instance == null) {
                    // 创建拓展点实例 #By Joker
                    instance = createExtension(name, wrap);
                    //
                    holder.set(instance);
                }
            }
        }
        return (T) instance;
    }

    /**
     * Get the extension by specified name if found, or {@link #getDefaultExtension() returns the default one}
     *
     * @param name the name of extension
     * @return non-null
     */
    public T getOrDefaultExtension(String name) {
        return containsExtension(name) ? getExtension(name) : getDefaultExtension();
    }

    /**
     * Return default extension, return <code>null</code> if it's not configured.
     */
    public T getDefaultExtension() {
        getExtensionClasses();
        if (StringUtils.isBlank(cachedDefaultName) || "true".equals(cachedDefaultName)) {
            return null;
        }
        return getExtension(cachedDefaultName);
    }

    public boolean hasExtension(String name) {
        if (StringUtils.isEmpty(name)) {
            throw new IllegalArgumentException("Extension name == null");
        }
        Class<?> c = this.getExtensionClass(name);
        return c != null;
    }

    public Set<String> getSupportedExtensions() {
        Map<String, Class<?>> clazzes = getExtensionClasses();
        /**
         * 返回一个只读的set
         */
        return Collections.unmodifiableSet(new TreeSet<>(clazzes.keySet()));
    }

    public Set<T> getSupportedExtensionInstances() {
        List<T> instances = new LinkedList<>();
        Set<String> supportedExtensions = getSupportedExtensions();
        if (CollectionUtils.isNotEmpty(supportedExtensions)) {
            for (String name : supportedExtensions) {
                instances.add(getExtension(name));
            }
        }
        // sort the Prioritized instances
        sort(instances, Prioritized.COMPARATOR);
        return new LinkedHashSet<>(instances);
    }

    /**
     * Return default extension name, return <code>null</code> if not configured.
     */
    public String getDefaultExtensionName() {
        getExtensionClasses();
        return cachedDefaultName;
    }

    /**
     * Register new extension via API
     *
     * @param name  extension name
     * @param clazz extension class
     * @throws IllegalStateException when extension with the same name has already been registered.
     */
    public void addExtension(String name, Class<?> clazz) {
        getExtensionClasses(); // load classes

        if (!type.isAssignableFrom(clazz)) {
            throw new IllegalStateException("Input type " +
                    clazz + " doesn't implement the Extension " + type);
        }
        if (clazz.isInterface()) {
            throw new IllegalStateException("Input type " +
                    clazz + " can't be interface!");
        }

        if (!clazz.isAnnotationPresent(Adaptive.class)) {
            if (StringUtils.isBlank(name)) {
                throw new IllegalStateException("Extension name is blank (Extension " + type + ")!");
            }
            if (cachedClasses.get().containsKey(name)) {
                throw new IllegalStateException("Extension name " +
                        name + " already exists (Extension " + type + ")!");
            }

            cachedNames.put(clazz, name);
            cachedClasses.get().put(name, clazz);
        } else {
            if (cachedAdaptiveClass != null) {
                throw new IllegalStateException("Adaptive Extension already exists (Extension " + type + ")!");
            }

            cachedAdaptiveClass = clazz;
        }
    }

    /**
     * Replace the existing extension via API
     *
     * @param name  extension name
     * @param clazz extension class
     * @throws IllegalStateException when extension to be placed doesn't exist
     * @deprecated not recommended any longer, and use only when test
     */
    @Deprecated
    public void replaceExtension(String name, Class<?> clazz) {
        getExtensionClasses(); // load classes

        if (!type.isAssignableFrom(clazz)) {
            throw new IllegalStateException("Input type " +
                    clazz + " doesn't implement Extension " + type);
        }
        if (clazz.isInterface()) {
            throw new IllegalStateException("Input type " +
                    clazz + " can't be interface!");
        }

        if (!clazz.isAnnotationPresent(Adaptive.class)) {
            if (StringUtils.isBlank(name)) {
                throw new IllegalStateException("Extension name is blank (Extension " + type + ")!");
            }
            if (!cachedClasses.get().containsKey(name)) {
                throw new IllegalStateException("Extension name " +
                        name + " doesn't exist (Extension " + type + ")!");
            }

            cachedNames.put(clazz, name);
            cachedClasses.get().put(name, clazz);
            cachedInstances.remove(name);
        } else {
            if (cachedAdaptiveClass == null) {
                throw new IllegalStateException("Adaptive Extension doesn't exist (Extension " + type + ")!");
            }

            cachedAdaptiveClass = clazz;
            cachedAdaptiveInstance.set(null);
        }
    }

    @SuppressWarnings("unchecked")
    public T getAdaptiveExtension() {
        Object instance = cachedAdaptiveInstance.get();
        if (instance == null) {
            if (createAdaptiveInstanceError != null) {
                throw new IllegalStateException("Failed to create adaptive instance: " +
                        createAdaptiveInstanceError.toString(),
                        createAdaptiveInstanceError);
            }

            synchronized (cachedAdaptiveInstance) {
                instance = cachedAdaptiveInstance.get();
                if (instance == null) {
                    try {
                        /**
                         * 创建自适应扩展点实现 #by Joker
                         */
                        instance = createAdaptiveExtension();
                        //
                        /**
                         * 保存到Holder对象中,注意因为自适应扩增点的实现类及被@Adaptive注解的类
                         * @Adaptive
                         * public class ThriftAdaptiveExtCase3 implements AdaptiveExt
                         * #by Joker
                         */
                        cachedAdaptiveInstance.set(instance);
                    } catch (Throwable t) {
                        createAdaptiveInstanceError = t;
                        throw new IllegalStateException("Failed to create adaptive instance: " + t.toString(), t);
                    }
                }
            }
        }

        return (T) instance;
    }

    private IllegalStateException findException(String name) {
        for (Map.Entry<String, IllegalStateException> entry : exceptions.entrySet()) {
            if (entry.getKey().toLowerCase().contains(name.toLowerCase())) {
                return entry.getValue();
            }
        }
        StringBuilder buf = new StringBuilder("No such extension " + type.getName() + " by name " + name);


        int i = 1;
        for (Map.Entry<String, IllegalStateException> entry : exceptions.entrySet()) {
            if (i == 1) {
                buf.append(", possible causes: ");
            }

            buf.append("\r\n(");
            buf.append(i++);
            buf.append(") ");
            buf.append(entry.getKey());
            buf.append(":\r\n");
            buf.append(StringUtils.toString(entry.getValue()));
        }
        return new IllegalStateException(buf.toString());
    }

    @SuppressWarnings("unchecked")
    private T createExtension(String name, boolean wrap) {
        // 从配置文件中加载拓展类实现,根据“配置项名称”到“配置类”的映射关系表
        // dog=testSpi.DogService
        // 得到Class testSpi.DogService
        Class<?> clazz = getExtensionClasses().get(name);
        if (clazz == null) {
            throw findException(name);
        }
        try {
            // 也是尝试先从缓存获取,获取不到通过反射创建一个并放到缓存中
            T instance = (T) EXTENSION_INSTANCES.get(clazz);
            if (instance == null) {
                EXTENSION_INSTANCES.putIfAbsent(clazz, clazz.newInstance());
                instance = (T) EXTENSION_INSTANCES.get(clazz);
            }
            // 依赖注入(查询set方法然后注入)
            // 和cachedWrapperClasses  todo
            injectExtension(instance);


            if (wrap) {

                List<Class<?>> wrapperClassesList = new ArrayList<>();
                if (cachedWrapperClasses != null) {
                    wrapperClassesList.addAll(cachedWrapperClasses);
                    wrapperClassesList.sort(WrapperComparator.COMPARATOR);
                    Collections.reverse(wrapperClassesList);
                }

                if (CollectionUtils.isNotEmpty(wrapperClassesList)) {
                    for (Class<?> wrapperClass : wrapperClassesList) {
                        Wrapper wrapper = wrapperClass.getAnnotation(Wrapper.class);
                        if (wrapper == null
                                || (ArrayUtils.contains(wrapper.matches(), name) && !ArrayUtils.contains(wrapper.mismatches(), name))) {
                            instance = injectExtension((T) wrapperClass.getConstructor(type).newInstance(instance));
                        }
                    }
                }
            }

            initExtension(instance);
            return instance;
        } catch (Throwable t) {
            throw new IllegalStateException("Extension instance (name: " + name + ", class: " +
                    type + ") couldn't be instantiated: " + t.getMessage(), t);
        }
    }

    private boolean containsExtension(String name) {
        return getExtensionClasses().containsKey(name);
    }

    private T injectExtension(T instance) {


        if (objectFactory == null) {
            /**
             * ExtensionFactory扩增点
             * 实现类: SpiExtensionFactory, AdaptiveExtensionFactory, SpringExtensionFactory
             * 不需要反转注入IOC
             */
            return instance;
        }

        try {
            /**
             * 遍历获取方法,条件如下
             * 1. 是Set
             * 2. 没有@DisableInject注解
             * 3. 方法第一个参数不是基本类型的
             *
             */
            for (Method method : instance.getClass().getMethods()) {
                if (!isSetter(method)) {
                    continue;
                }
                /**
                 * Check {@link DisableInject} to see if we need auto injection for this property
                 */
                if (method.getAnnotation(DisableInject.class) != null) {
                    continue;
                }
                Class<?> pt = method.getParameterTypes()[0];
                if (ReflectUtils.isPrimitives(pt)) {
                    continue;
                }

                try {
                    // 由setName() 方法名,获取属性名称
                    String property = getSetterProperty(method);
                    // todo
                    Object object = objectFactory.getExtension(pt, property);
                    // 获取之后通过反射赋值
                    if (object != null) {
                        method.invoke(instance, object);
                    }
                } catch (Exception e) {
                    logger.error("Failed to inject via method " + method.getName()
                            + " of interface " + type.getName() + ": " + e.getMessage(), e);
                }

            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return instance;
    }

    private void initExtension(T instance) {
        if (instance instanceof Lifecycle) {
            Lifecycle lifecycle = (Lifecycle) instance;
            lifecycle.initialize();
        }
    }

    /**
     * get properties name for setter, for instance: setVersion, return "version"
     * <p>
     * return "", if setter name with length less than 3
     */
    private String getSetterProperty(Method method) {
        return method.getName().length() > 3 ? method.getName().substring(3, 4).toLowerCase() + method.getName().substring(4) : "";
    }

    /**
     * return true if and only if:
     * <p>
     * 1, public
     * <p>
     * 2, name starts with "set"
     * <p>
     * 3, only has one parameter
     */
    private boolean isSetter(Method method) {
        return method.getName().startsWith("set")
                && method.getParameterTypes().length == 1
                && Modifier.isPublic(method.getModifiers());
    }

    private Class<?> getExtensionClass(String name) {
        if (type == null) {
            throw new IllegalArgumentException("Extension type == null");
        }
        if (name == null) {
            throw new IllegalArgumentException("Extension name == null");
        }
        return getExtensionClasses().get(name);
    }

    /**
     * 扩增点实现名(如:dog,等号左边的dog=testAdaptive.DogServiceTA,而不是类名)和具体Class的map
     * {@link ExtensionLoader#cachedClasses}
     * private final Holder<Map<String, Class<?>>> cachedClasses = new Holder<>();
     *
     */
    private Map<String, Class<?>> getExtensionClasses() {
        // 从缓存这两个获取映射关系表 #by Joker
        Map<String, Class<?>> classes = cachedClasses.get();
        // 双重检查 #by Joker
        if (classes == null) {
            synchronized (cachedClasses) {
                classes = cachedClasses.get();
                if (classes == null) {
                    //  #by Joker
                    /**
                     * 如果缓存中没有,则去加载这个映射关系
                     * (扩增点实现名与扩增点实现类Class的对应关系)
                     */
                    classes = loadExtensionClasses();
                    cachedClasses.set(classes);
                }
            }
        }
        return classes;
    }

    /**
     * synchronized in getExtensionClasses
     */
    private Map<String, Class<?>> loadExtensionClasses() {
        /**
         * 缓存扩展点的默认实现列@SPI("dog") {@link testSpi.AnimalService},
         * 仅仅是缓存,不放到方法的返回值中.
         */
        cacheDefaultExtensionName();

        Map<String, Class<?>> extensionClasses = new HashMap<>();

        /**
         * strategy[0].directory(),strategy[1].directory(),strategy[2].directory()
         * META-INF/dubbo/internal/   META-INF/dubbo/   META-INF/services/
         * 从这些目录中加载扩增点的名(dog,等号左边的dog=testAdaptive.DogServiceTA,而不是类名)和具体Class
         */
        for (LoadingStrategy strategy : strategies) {
            loadDirectory(extensionClasses, strategy.directory(), type.getName(), strategy.preferExtensionClassLoader(), strategy.overridden(), strategy.excludedPackages());
            loadDirectory(extensionClasses, strategy.directory(), type.getName().replace("org.apache", "com.alibaba"), strategy.preferExtensionClassLoader(), strategy.overridden(), strategy.excludedPackages());
        }

        return extensionClasses;
    }

    /**
     * extract and cache default extension name if exists
     */
    private void cacheDefaultExtensionName() {
        // 获取SPI注解,这里的type是在调用getExtensionLoader方法时传入的
        final SPI defaultAnnotation = type.getAnnotation(SPI.class);
        if (defaultAnnotation == null) {
            return;
        }

        // @SPI("dog") 中的默认value = "dog"
        String value = defaultAnnotation.value();
        if ((value = value.trim()).length() > 0) {
            String[] names = NAME_SEPARATOR.split(value);
            if (names.length > 1) {
                // 如果用','分割定义多个就报错
                throw new IllegalStateException("More than 1 default extension name on extension " + type.getName()
                        + ": " + Arrays.toString(names));
            }
            if (names.length == 1) {
                // 最终放到ExtensionLoad这个对象的cachedDefaultName
                cachedDefaultName = names[0];
            }
        }
    }

    private void loadDirectory(Map<String, Class<?>> extensionClasses, String dir, String type) {
        loadDirectory(extensionClasses, dir, type, false, false);
    }

    private void loadDirectory(Map<String, Class<?>> extensionClasses, String dir, String type,
                               boolean extensionLoaderClassLoaderFirst, boolean overridden, String... excludedPackages) {
        String fileName = dir + type;
        try {
            Enumeration<java.net.URL> urls = null;
            ClassLoader classLoader = findClassLoader();

            // try to load from ExtensionLoader's ClassLoader first
            if (extensionLoaderClassLoaderFirst) {
                ClassLoader extensionLoaderClassLoader = ExtensionLoader.class.getClassLoader();
                if (ClassLoader.getSystemClassLoader() != extensionLoaderClassLoader) {
                    urls = extensionLoaderClassLoader.getResources(fileName);
                }
            }

            if (urls == null || !urls.hasMoreElements()) {
                if (classLoader != null) {
                    // todo
                    urls = classLoader.getResources(fileName);
                } else {
                    urls = ClassLoader.getSystemResources(fileName);
                }
            }

            if (urls != null) {
                while (urls.hasMoreElements()) {
                    java.net.URL resourceURL = urls.nextElement();
                    loadResource(extensionClasses, classLoader, resourceURL, overridden, excludedPackages);
                }
            }
        } catch (Throwable t) {
            logger.error("Exception occurred when loading extension class (interface: " +
                    type + ", description file: " + fileName + ").", t);
        }
    }

    private void loadResource(Map<String, Class<?>> extensionClasses, ClassLoader classLoader,
                              java.net.URL resourceURL, boolean overridden, String... excludedPackages) {
        try {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(resourceURL.openStream(), StandardCharsets.UTF_8))) {
                String line;
                // 按行读取配置内容 #by Joker
                while ((line = reader.readLine()) != null) {
                    // # 是注释
                    final int ci = line.indexOf('#');
                    if (ci >= 0) {
                        line = line.substring(0, ci);
                    }
                    line = line.trim();
                    if (line.length() > 0) {
                        try {
                            String name = null;
                            // dog=testSpi.DogService
                            int i = line.indexOf('=');
                            if (i > 0) {
                                name = line.substring(0, i).trim();
                                line = line.substring(i + 1).trim();
                            }
                            if (line.length() > 0 && !isExcluded(line, excludedPackages)) {
                                // 真正加载Class
                                loadClass(extensionClasses, resourceURL, Class.forName(line, true, classLoader), name, overridden);
                            }
                        } catch (Throwable t) {
                            IllegalStateException e = new IllegalStateException("Failed to load extension class (interface: " + type + ", class line: " + line + ") in " + resourceURL + ", cause: " + t.getMessage(), t);
                            exceptions.put(line, e);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            logger.error("Exception occurred when loading extension class (interface: " +
                    type + ", class file: " + resourceURL + ") in " + resourceURL, t);
        }
    }

    private boolean isExcluded(String className, String... excludedPackages) {
        if (excludedPackages != null) {
            for (String excludePackage : excludedPackages) {
                if (className.startsWith(excludePackage + ".")) {
                    return true;
                }
            }
        }
        return false;
    }

    private void loadClass(Map<String, Class<?>> extensionClasses, java.net.URL resourceURL, Class<?> clazz, String name,
                           boolean overridden) throws NoSuchMethodException {
        // isAssignableFrom()方法是判断是否为某个类的父类
        if (!type.isAssignableFrom(clazz)) {
            throw new IllegalStateException("Error occurred when loading extension class (interface: " +
                    type + ", class line: " + clazz.getName() + "), class "
                    + clazz.getName() + " is not subtype of interface.");
        }
        // 扩增点的实现类是否标注了@Adaptive注解
        if (clazz.isAnnotationPresent(Adaptive.class)) {
            // 存在到 ExtensionLoader对象的 private volatile Class<?> cachedAdaptiveClass = null;
            cacheAdaptiveClass(clazz, overridden);
        }
        // 是否Wrapper类型
        else if (isWrapperClass(clazz)) {
            cacheWrapperClass(clazz);
        } else {
            // 程序进入此分支,表明clazz是一个普通的拓展类,testSpi.DogService
            clazz.getConstructor();
            if (StringUtils.isEmpty(name)) {
                name = findAnnotationName(clazz);
                if (name.length() == 0) {
                    throw new IllegalStateException("No such extension name for the class " + clazz.getName() + " in the config " + resourceURL);
                }
            }

            String[] names = NAME_SEPARATOR.split(name);
            if (ArrayUtils.isNotEmpty(names)) {
                cacheActivateClass(clazz, names[0]);
                for (String n : names) {
                    cacheName(clazz, n);
                    saveInExtensionClass(extensionClasses, clazz, n, overridden);
                }
            }
        }
    }

    /**
     * cache name
     */
    private void cacheName(Class<?> clazz, String name) {
        if (!cachedNames.containsKey(clazz)) {
            cachedNames.put(clazz, name);
        }
    }

    /**
     * put clazz in extensionClasses
     */
    private void saveInExtensionClass(Map<String, Class<?>> extensionClasses, Class<?> clazz, String name, boolean overridden) {
        Class<?> c = extensionClasses.get(name);
        if (c == null || overridden) {
            extensionClasses.put(name, clazz);
        } else if (c != clazz) {
            String duplicateMsg = "Duplicate extension " + type.getName() + " name " + name + " on " + c.getName() + " and " + clazz.getName();
            logger.error(duplicateMsg);
            throw new IllegalStateException(duplicateMsg);
        }
    }

    /**
     * cache Activate class which is annotated with <code>Activate</code>
     * <p>
     * for compatibility, also cache class with old alibaba Activate annotation
     */
    private void cacheActivateClass(Class<?> clazz, String name) {
        Activate activate = clazz.getAnnotation(Activate.class);
        if (activate != null) {
            cachedActivates.put(name, activate);
        } else {
            // support com.alibaba.dubbo.common.extension.Activate
            com.alibaba.dubbo.common.extension.Activate oldActivate = clazz.getAnnotation(com.alibaba.dubbo.common.extension.Activate.class);
            if (oldActivate != null) {
                cachedActivates.put(name, oldActivate);
            }
        }
    }

    /**
     * cache Adaptive class which is annotated with <code>Adaptive</code>
     */
    private void cacheAdaptiveClass(Class<?> clazz, boolean overridden) {
        if (cachedAdaptiveClass == null || overridden) {
            cachedAdaptiveClass = clazz;
        } else if (!cachedAdaptiveClass.equals(clazz)) {
            throw new IllegalStateException("More than 1 adaptive class found: "
                    + cachedAdaptiveClass.getName()
                    + ", " + clazz.getName());
        }
    }

    /**
     * cache wrapper class
     * <p>
     * like: ProtocolFilterWrapper, ProtocolListenerWrapper
     */
    private void cacheWrapperClass(Class<?> clazz) {
        if (cachedWrapperClasses == null) {
            cachedWrapperClasses = new ConcurrentHashSet<>();
        }
        cachedWrapperClasses.add(clazz);
    }

    /**
     * test if clazz is a wrapper class
     * <p>
     * which has Constructor with given class type as its only argument
     */
    private boolean isWrapperClass(Class<?> clazz) {
        try {
            clazz.getConstructor(type);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    @SuppressWarnings("deprecation")
    private String findAnnotationName(Class<?> clazz) {
        org.apache.dubbo.common.Extension extension = clazz.getAnnotation(org.apache.dubbo.common.Extension.class);
        if (extension != null) {
            return extension.value();
        }

        String name = clazz.getSimpleName();
        if (name.endsWith(type.getSimpleName())) {
            name = name.substring(0, name.length() - type.getSimpleName().length());
        }
        return name.toLowerCase();
    }

    @SuppressWarnings("unchecked")
    private T createAdaptiveExtension() {
        try {
            /**
             * 分为3步:
             * 1是创建自适应拓展代理类Class对象,
             * 2是通过反射创建对象,
             * 3是给创建的对象按需依赖注入
             * todo ?
             */
            return injectExtension((T) getAdaptiveExtensionClass().newInstance());
        } catch (Exception e) {
            throw new IllegalStateException("Can't create adaptive extension " + type + ", cause: " + e.getMessage(), e);
        }
    }

    private Class<?> getAdaptiveExtensionClass() {
        getExtensionClasses();
        if (cachedAdaptiveClass != null) {
            return cachedAdaptiveClass;
        }
        return cachedAdaptiveClass = createAdaptiveExtensionClass();
    }

    private Class<?> createAdaptiveExtensionClass() {
        /**
         * code就是保存了创建的class字符串数据
         */
        String code = new AdaptiveClassCodeGenerator(type, cachedDefaultName).generate();
        ClassLoader classLoader = findClassLoader();
        org.apache.dubbo.common.compiler.Compiler compiler = ExtensionLoader.getExtensionLoader(org.apache.dubbo.common.compiler.Compiler.class).getAdaptiveExtension();
        return compiler.compile(code, classLoader);
    }

    @Override
    public String toString() {
        return this.getClass().getName() + "[" + type.getName() + "]";
    }

}
