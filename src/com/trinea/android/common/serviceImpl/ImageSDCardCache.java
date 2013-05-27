package com.trinea.android.common.serviceImpl;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.View;

import com.trinea.android.common.service.FileNameRule;
import com.trinea.android.common.utils.ImageUtils;
import com.trinea.android.common.utils.FileUtils;
import com.trinea.android.common.utils.SerializeUtils;
import com.trinea.android.common.utils.StringUtils;
import com.trinea.android.common.entity.CacheObject;
import com.trinea.android.common.service.Cache;
import com.trinea.android.common.service.CacheFullRemoveType;
import com.trinea.android.common.serviceImpl.PreloadDataCache.OnGetDataListener;

/**
 * <strong>图片Sd卡缓存</strong>，适用于图片较大，防止在内存中缓存会占用太多内存情况，图片较小情况可使用{@link ImageCache}。<br/>
 * <ul>
 * 缓存使用
 * <li>使用下面缓存初始化中介绍的几种构造函数之一初始化缓存</li>
 * <li>调用{@link #loadImageFile(String, List, View)}获取当前图片并预取新图片; {@link #loadImageFile(String, View)}获取当前图片;
 * {@link #loadImageFile(String, View, OnImageSDCallListener)}获取当前图片并调用自己的回调接口;
 * {@link #loadImageFile(String, List, View, OnImageSDCallListener)}获取当前图片后调用自己的回调接口并预取新图片</li>
 * <li>使用{@link #saveCache(String, ImageSDCardCache)}保存缓存到文件</li>
 * <li>使用{@link #put(String, CacheObject)}或{@link #put(String, String)}或{@link #putAll(ImageSDCardCache)}向缓存中添加元素</li>
 * <li>使用{@link #setFileNameRule(FileNameRule)}设置缓存图片保存的文件名规则，默认规则为{@link FileNameRuleImageUrl};
 * {@link #setCacheFolder(String)}设置缓存图片的保存目录</li>
 * </ul>
 * <ul>
 * 缓存初始化
 * <li>{@link #ImageSDCardCache()}</li>
 * <li>{@link #ImageSDCardCache(OnImageSDCallListener)}</li>
 * <li>{@link #ImageSDCardCache(OnImageSDCallListener, int)}</li>
 * <li>{@link #ImageSDCardCache(OnImageSDCallListener, String)}</li>
 * <li>{@link #ImageSDCardCache(OnImageSDCallListener, int, CacheFullRemoveType)}</li>
 * <li>{@link #ImageSDCardCache(OnImageSDCallListener, String, int)}</li>
 * <li>{@link #ImageSDCardCache(OnImageSDCallListener, String, int, CacheFullRemoveType)}</li>
 * <li>{@link #ImageSDCardCache(OnImageSDCallListener, String, int, long)}</li>
 * <li>{@link #ImageSDCardCache(String, int, long, CacheFullRemoveType)}</li>
 * <li>{@link #ImageSDCardCache(OnImageSDCallListener, String, int, long, CacheFullRemoveType)}</li>
 * <li>{@link #ImageSDCardCache(OnGetDataListener, OnImageSDCallListener, String, int, long, CacheFullRemoveType)}</li>
 * <li>{@link #loadCache(String)}从文件中恢复缓存</li>
 * </ul>
 * 
 * @author Trinea 2012-4-5 下午10:24:52
 */
public class ImageSDCardCache implements Serializable, Cache<String, String> {

    private static final long           serialVersionUID     = 1L;

    private static final String         TAG                  = "ImageSDCardCache";

    /** 是否打印获取图片失败异常 **/
    public static boolean               printException       = true;
    /** 默认缓存大小 **/
    public static final int             DEFAULT_MAX_SIZE     = 128;
    /** 缓存图片保存的默认目录 **/
    public static final String          DEFAULT_CACHE_FOLDER = Environment.getExternalStorageDirectory()
                                                                          .getAbsolutePath()
                                                               + File.separator
                                                               + "Trinea"
                                                               + File.separator
                                                               + "AndroidCommon"
                                                               + File.separator + "ImageCache";
    /** 线程池 **/
    private transient ExecutorService   threadPool           = Executors.newFixedThreadPool(Runtime.getRuntime()
                                                                                                   .availableProcessors() * 2 + 1);

    /** 缓存图片的保存目录 **/
    private String                      cacheFolder;
    /** 缓存图片保存的文件名规则 **/
    private FileNameRule                fileNameRule         = new FileNameRuleImageUrl();

    /** image获取成功的message what **/
    private static final int            IMAGE_LOADED_WHAT    = 1;
    /** image重新获取成功的message what **/
    private static final int            IMAGE_RELOADED_WHAT  = 2;

    /** 图片缓存 **/
    private final FileSimpleCache       imageCache;

    /** 图片获取结束后的回调接口 **/
    private final OnImageSDCallListener listener;

    /** 发送并处理消息 **/
    private Handler                     handler;

    /** http read time out **/
    public static int                   httpReadTimeOut      = -1;

    /**
     * 初始化缓存
     * <ul>
     * <li>listener为空，只能通过{@link #loadImageFile(String, View, OnImageSDCallListener)}和
     * {@link #loadImageFile(String, List, View, OnImageSDCallListener)}load图片</li>
     * <li>缓存图片的保存目录为{@link #DEFAULT_CACHE_FOLDER}</li>
     * <li>缓存最大容量为{@link #DEFAULT_MAX_SIZE}</li>
     * <li>元素不会失效</li>
     * <li>cache满时删除元素类型为{@link RemoveTypeFileSmall}</li>
     * </ul>
     */
    public ImageSDCardCache(){
        this(null, DEFAULT_CACHE_FOLDER, DEFAULT_MAX_SIZE, -1, new RemoveTypeFileSmall());
    }

    /**
     * 初始化缓存
     * <ul>
     * <li>listener为空，只能通过{@link #loadImageFile(String, View, OnImageSDCallListener)}和
     * {@link #loadImageFile(String, List, View, OnImageSDCallListener)}load图片</li>
     * </ul>
     * 
     * @param cacheFolder 图片保存的目录
     * @param maxSize 缓存最大容量
     * @param validTime 缓存中元素有效时间，小于等于0表示元素不会失效，失效规则见{@link SimpleCache#isExpired(CacheObject)}
     * @param cacheFullRemoveType cache满时删除元素类型，见{@link CacheFullRemoveType}
     */
    public ImageSDCardCache(String cacheFolder, int maxSize, long validTime,
                            CacheFullRemoveType<String> cacheFullRemoveType){
        this(null, cacheFolder, maxSize, validTime, cacheFullRemoveType);
    }

    /**
     * 初始化缓存
     * <ul>
     * <li>缓存图片的保存目录为{@link #DEFAULT_CACHE_FOLDER}</li>
     * <li>缓存最大容量为{@link #DEFAULT_MAX_SIZE}</li>
     * <li>元素不会失效</li>
     * <li>cache满时删除元素类型为{@link RemoveTypeFileSmall}</li>
     * </ul>
     * 
     * @param listener 图片获取结束后的回调接口
     */
    public ImageSDCardCache(OnImageSDCallListener listener){
        this(listener, DEFAULT_CACHE_FOLDER, DEFAULT_MAX_SIZE, -1, new RemoveTypeFileSmall());
    }

    /**
     * 初始化缓存
     * <ul>
     * <li>缓存图片的保存目录为{@link #DEFAULT_CACHE_FOLDER}</li>
     * <li>元素不会失效</li>
     * <li>cache满时删除元素类型为{@link RemoveTypeFileSmall}</li>
     * </ul>
     * 
     * @param listener 图片获取结束后的回调接口
     * @param maxSize 缓存最大容量
     */
    public ImageSDCardCache(OnImageSDCallListener listener, int maxSize){
        this(listener, DEFAULT_CACHE_FOLDER, maxSize, -1, new RemoveTypeFileSmall());
    }

    /**
     * 初始化缓存
     * <ul>
     * <li>缓存最大容量为{@link #DEFAULT_MAX_SIZE}</li>
     * <li>元素不会失效</li>
     * <li>cache满时删除元素类型为{@link RemoveTypeFileSmall}</li>
     * </ul>
     * 
     * @param listener 图片获取结束后的回调接口
     * @param cacheFolder 图片保存的目录
     */
    public ImageSDCardCache(OnImageSDCallListener listener, String cacheFolder){
        this(listener, cacheFolder, DEFAULT_MAX_SIZE, -1, new RemoveTypeFileSmall());
    }

    /**
     * 初始化缓存
     * <ul>
     * <li>元素不会失效</li>
     * <li>cache满时删除元素类型为{@link RemoveTypeFileSmall}</li>
     * </ul>
     * 
     * @param listener 图片获取结束后的回调接口
     * @param cacheFolder 图片保存的目录
     * @param maxSize 缓存最大容量
     */
    public ImageSDCardCache(OnImageSDCallListener listener, String cacheFolder, int maxSize){
        this(listener, cacheFolder, maxSize, -1, new RemoveTypeFileSmall());
    }

    /**
     * 初始化缓存
     * <ul>
     * <li>缓存图片的保存目录为{@link #DEFAULT_CACHE_FOLDER}</li>
     * <li>元素不会失效</li>
     * </ul>
     * 
     * @param listener 图片获取结束后的回调接口
     * @param maxSize 缓存最大容量
     * @param cacheFullRemoveType cache满时删除元素类型，见{@link CacheFullRemoveType}
     */
    public ImageSDCardCache(OnImageSDCallListener listener, int maxSize,
                            CacheFullRemoveType<String> cacheFullRemoveType){
        this(listener, DEFAULT_CACHE_FOLDER, maxSize, -1, cacheFullRemoveType);
    }

    /**
     * 初始化缓存
     * <ul>
     * <li>cache满时删除元素类型为{@link RemoveTypeFileSmall}</li>
     * </ul>
     * 
     * @param listener 图片获取结束后的回调接口
     * @param cacheFolder 图片保存的目录
     * @param maxSize 缓存最大容量
     * @param validTime 缓存中元素有效时间，小于等于0表示元素不会失效，失效规则见{@link SimpleCache#isExpired(CacheObject)}
     */
    public ImageSDCardCache(OnImageSDCallListener listener, String cacheFolder, int maxSize,
                            long validTime){
        this(listener, cacheFolder, maxSize, validTime, new RemoveTypeFileSmall());
    }

    /**
     * 初始化缓存
     * <ul>
     * <li>元素不会失效</li>
     * </ul>
     * 
     * @param listener 图片获取结束后的回调接口
     * @param cacheFolder 图片保存的目录
     * @param maxSize 缓存最大容量
     * @param cacheFullRemoveType cache满时删除元素类型，见{@link CacheFullRemoveType}
     */
    public ImageSDCardCache(OnImageSDCallListener listener, String cacheFolder, int maxSize,
                            CacheFullRemoveType<String> cacheFullRemoveType){
        this(listener, cacheFolder, maxSize, -1, cacheFullRemoveType);
    }

    /**
     * 初始化缓存
     * 
     * @param listener 图片获取结束后的回调接口
     * @param cacheFolder 图片保存的目录
     * @param maxSize 缓存最大容量
     * @param validTime 缓存中元素有效时间，小于等于0表示元素不会失效，失效规则见{@link SimpleCache#isExpired(CacheObject)}
     * @param cacheFullRemoveType cache满时删除元素类型，见{@link CacheFullRemoveType}
     */
    public ImageSDCardCache(OnImageSDCallListener listener, String cacheFolder, int maxSize,
                            long validTime, CacheFullRemoveType<String> cacheFullRemoveType){
        if (StringUtils.isEmpty(cacheFolder)) {
            throw new IllegalArgumentException("The cacheFolder of cache can not be null.");
        }

        this.listener = listener;
        this.cacheFolder = cacheFolder;
        this.imageCache = new FileSimpleCache(getOnGetDataListener(), maxSize, validTime,
                                              cacheFullRemoveType);
        this.handler = new MyHandler();
        if (Looper.myLooper() == null) {
            Looper.prepare();
        }
    }

    /**
     * 初始化缓存
     * 
     * @param getDataListener 获取图片的接口
     * @param listener 图片获取结束后的回调接口
     * @param cacheFolder 图片保存的目录
     * @param maxSize 缓存最大容量
     * @param validTime 缓存中元素有效时间，小于等于0表示元素不会失效，失效规则见{@link SimpleCache#isExpired(CacheObject)}
     * @param cacheFullRemoveType cache满时删除元素类型，见{@link CacheFullRemoveType}
     */
    public ImageSDCardCache(OnGetDataListener<String, String> getDataListener,
                            OnImageSDCallListener listener, String cacheFolder, int maxSize,
                            long validTime, CacheFullRemoveType<String> cacheFullRemoveType){
        if (getDataListener == null) {
            throw new IllegalArgumentException("The getDataListener of cache can not be null.");
        }
        if (StringUtils.isEmpty(cacheFolder)) {
            throw new IllegalArgumentException("The cacheFolder of cache can not be null.");
        }

        this.listener = listener;
        this.cacheFolder = cacheFolder;
        this.imageCache = new FileSimpleCache(getDataListener, maxSize, validTime,
                                              cacheFullRemoveType);
    }

    /**
     * load图片
     * 
     * @param imageUrl 图片url
     * @param view 操作图片的view
     * @return 图片是否在缓存中，true表示是
     */
    public boolean loadImageFile(final String imageUrl, final View view) {
        return loadImageFile(imageUrl, null, view);
    }

    /**
     * load图片并提前预取
     * 
     * @param imageUrl 图片url
     * @param urlList 图片url list，按照该list中的url顺序获取新图片进行缓存，为空表示不进行缓存
     * @param view 操作图片的view
     * @return 图片是否在缓存中，true表示是
     */
    public boolean loadImageFile(final String imageUrl, final List<String> urlList, final View view) {
        if (StringUtils.isEmpty(imageUrl)) {
            return false;
        }

        /**
         * 若图片在缓存中直接调用listener，否则新建线程等待获取完成
         */
        CacheObject<String> object = imageCache.getAsync(imageUrl);
        if (object != null) {
            String imagePath = object.getData();
            if (StringUtils.isEmpty(imagePath) || !FileUtils.isFileExist(imagePath)) {
                remove(imageUrl);
                return false;
            } else {
                listener.onImageLoaded(imageUrl, imagePath, view, true);
                return true;
            }
        }
        if (imageCache.isExistGettingDataThread(imageUrl)) {
            return false;
        }

        startGetImageThread(IMAGE_LOADED_WHAT, imageUrl, urlList, view);
        return false;
    }

    /**
     * 事件处理
     * 
     * @author Trinea 2012-11-20
     */
    private class MyHandler extends Handler {

        public void handleMessage(Message message) {
            switch (message.what) {
                case IMAGE_LOADED_WHAT:
                case IMAGE_RELOADED_WHAT:

                    UpdateMessage updateMessage = (UpdateMessage)message.obj;
                    if (updateMessage != null) {
                        // 图片文件若不存在删除缓存并重新获取
                        if (StringUtils.isEmpty(updateMessage.imagePath)
                            || !FileUtils.isFileExist(updateMessage.imagePath)) {
                            remove(updateMessage.imageUrl);
                            if (message.what == IMAGE_LOADED_WHAT) {
                                startGetImageThread(IMAGE_LOADED_WHAT, updateMessage.imageUrl,
                                                    updateMessage.urlList, updateMessage.view);
                                break;
                            }
                        }
                        listener.onImageLoaded(updateMessage.imageUrl, updateMessage.imagePath,
                                               updateMessage.view, false);
                    }
                    break;
            }
        }
    }

    /**
     * 用来更新的消息
     * 
     * @author Trinea 2013-1-14
     */
    private class UpdateMessage {

        String       imageUrl;
        String       imagePath;
        List<String> urlList;
        View         view;

        public UpdateMessage(String imageUrl, String imagePath, List<String> urlList, View view){
            this.imageUrl = imageUrl;
            this.imagePath = imagePath;
            this.urlList = urlList;
            this.view = view;
        }
    }

    /**
     * 启动获取图片线程
     * 
     * @param imageUrl 图片url
     * @param urlList 图片url list，按照该list中的url顺序获取新图片进行缓存，为空表示不进行缓存
     * @param messsageWhat 事件what
     */
    private void startGetImageThread(final int messsageWhat, final String imageUrl,
                                     final List<String> urlList, final View view) {
        // 获取图片并发送图片获取成功的message what
        threadPool.execute(new Runnable() {

            @Override
            public void run() {
                CacheObject<String> object = imageCache.get(imageUrl, urlList);
                String savePath = (object == null ? null : object.getData());
                handler.sendMessage(handler.obtainMessage(messsageWhat, new UpdateMessage(imageUrl,
                                                                                          savePath,
                                                                                          urlList,
                                                                                          view)));
            }
        });
    }

    /**
     * 向缓存中添加元素, key和value均不允许为空
     * 
     * @param key key
     * @param value 元素
     * @return 为空表示缓存已满无法put，否则为put的value。
     */
    public CacheObject<String> put(String key, CacheObject<String> value) {
        return this.imageCache.put(key, value);
    }

    /**
     * 向缓存中添加元素, key不允许为空
     * <ul>
     * <li>见{@link #put(String, CacheObject)}</li>
     * </ul>
     * 
     * @param key key
     * @param value 元素值
     * @return 为空表示缓存已满无法put，否则为put的value。
     */
    public CacheObject<String> put(String key, String value) {
        return this.imageCache.put(key, value);
    }

    /**
     * 将cache2中的所有元素复制到当前cache，相当于将cache2中的每一个元素{@link #put(String, CacheObject)}到当前cache
     * 
     * @param cache2
     */
    public void putAll(ImageSDCardCache cache2) {
        this.imageCache.putAll(cache2.imageCache);
    }

    /**
     * 得到缓存图片的保存目录
     * 
     * @return the cacheFolder
     */
    public String getCacheFolder() {
        return cacheFolder;
    }

    /**
     * 设置缓存图片的保存目录
     * 
     * @param cacheFolder
     */
    public void setCacheFolder(String cacheFolder) {
        this.cacheFolder = cacheFolder;
    }

    /**
     * 得到缓存图片保存的文件名规则
     * 
     * @return the fileNameRule
     */
    public FileNameRule getFileNameRule() {
        return fileNameRule;
    }

    /**
     * 设置缓存图片保存的文件名规则，使用{@link FileNameRule#getFileName(Object)}设置文件名
     * 
     * @param fileNameRule
     */
    public void setFileNameRule(FileNameRule fileNameRule) {
        if (fileNameRule == null) {
            throw new IllegalArgumentException("The fileNameRule of cache can not be null.");
        }
        this.fileNameRule = fileNameRule;
    }

    /**
     * 图片获取结束后的回调接口
     * <ul>
     * <li>实现{@link OnImageSDCallListener#onImageLoaded(String, String, View)}表示获取到图片后的操作</li>
     * </ul>
     * 
     * @author Trinea 2012-4-5 下午10:31:59
     */
    public interface OnImageSDCallListener extends Serializable {

        /**
         * 图片获取后的回调接口
         * 
         * @param imageUrl 图片url
         * @param imagePath 图片sd卡路径
         * @param view 操作图片的view
         * @param isInCache 是否在缓存中
         */
        public void onImageLoaded(String imageUrl, String imagePath, View view, boolean isInCache);
    }

    /**
     * 文件特殊缓存
     * <ul>
     * <li>删除缓存中文件路径同时，删除其路径对应的文件</li>
     * </ul>
     * 
     * @author Trinea 2012-6-30 下午09:42:00
     */
    class FileSimpleCache extends PreloadDataCache<String, String> {

        private static final long serialVersionUID = 1L;

        public FileSimpleCache(OnGetDataListener<String, String> onGetDataListener, int maxSize,
                               long validTime, CacheFullRemoveType<String> cacheFullRemoveType){
            super(onGetDataListener, maxSize, validTime, cacheFullRemoveType);
        }

        @Override
        protected CacheObject<String> fullRemoveOne() {
            CacheObject<String> o = super.fullRemoveOne();
            if (o != null) {
                deleteFile(o.getData());
            }
            return o;
        }

        @Override
        public CacheObject<String> remove(String key) {
            CacheObject<String> o = super.remove(key);
            if (o != null) {
                deleteFile(o.getData());
            }
            return o;
        }

        @Override
        public void clear() {
            for (Entry<String, CacheObject<String>> entry : entrySet()) {
                if (entry != null && entry.getValue() != null) {
                    deleteFile(entry.getValue().getData());
                }
            }
            cache.clear();
        }

        private boolean deleteFile(String path) {
            if (!StringUtils.isEmpty(path)) {
                if (!FileUtils.deleteFile(path)) {
                    Log.e(TAG, "删除文件失败，路径为：" + path);
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * 从文件中恢复缓存
     * 
     * @param filePath 文件路径
     * @return
     */
    public static ImageSDCardCache loadCache(String filePath) {
        return (ImageSDCardCache)SerializeUtils.deserialization(filePath);
    }

    /**
     * 保存缓存到文件
     * 
     * @param filePath 文件路径
     * @param cache 缓存
     */
    public static void saveCache(String filePath, ImageSDCardCache cache) {
        SerializeUtils.serialization(filePath, cache);
    }

    /**
     * 得到获取新数据的类
     * 
     * @return
     */
    private OnGetDataListener<String, String> getOnGetDataListener() {
        return new OnGetDataListener<String, String>() {

            private static final long serialVersionUID = 1L;

            @Override
            public CacheObject<String> onGetData(String key) {

                String savePath = null;
                try {
                    InputStream stream = ImageUtils.getInputStreamFromUrl(key, httpReadTimeOut);
                    if (stream != null) {
                        savePath = cacheFolder + File.separator + fileNameRule.getFileName(key);
                        try {
                            FileUtils.writeFile(savePath, stream);
                        } catch (Exception e) {
                            if (e.getCause() instanceof FileNotFoundException) {
                                FileUtils.makeFolders(savePath);
                                FileUtils.writeFile(savePath, stream);
                            } else {
                                savePath = null;
                                Log.e(TAG, "根据imageUrl获得InputStream后写文件异常，imageUrl为：" + key
                                           + "。保存路径为：" + savePath, e);
                            }
                        }
                    }
                } catch (Exception e) {
                    if (printException) {
                        Log.e(TAG, "根据imageUrl获得InputStream异常，imageUrl为：" + key, e);
                    }
                }

                return (StringUtils.isEmpty(savePath) ? null : new CacheObject<String>(savePath));
            }
        };
    }

    @Override
    public int getSize() {
        return imageCache.getSize();
    }

    @Override
    public CacheObject<String> get(String key) {
        return imageCache.get(key);
    }

    @Override
    public void putAll(Cache<String, String> cache2) {
        imageCache.putAll(cache2);
    }

    @Override
    public boolean containsKey(String key) {
        return imageCache.containsKey(key);
    }

    @Override
    public CacheObject<String> remove(String key) {
        return imageCache.remove(key);
    }

    @Override
    public void clear() {
        imageCache.clear();
    }

    @Override
    public double getHitRate() {
        return imageCache.getHitRate();
    }

    @Override
    public Set<String> keySet() {
        return imageCache.keySet();
    }

    @Override
    public Set<Entry<String, CacheObject<String>>> entrySet() {
        return imageCache.entrySet();
    }

    @Override
    public Collection<CacheObject<String>> values() {
        return imageCache.values();
    }

    public void shutdown() {
        threadPool.shutdown();
    }

    public List<Runnable> shutdownNow() {
        return threadPool.shutdownNow();
    }
}
