package me.darkdiable.www.videoDownloader;

import com.alibaba.fastjson.JSONObject;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author bilei
 * @description
 * @date 2025-09-03 18:01
 */
public class VideoDownloader {
    private static final Map<String, VideoParser> PARSERS = new HashMap<>();

    static {
        PARSERS.put("douyin", new DouyinParser());
        PARSERS.put("bilibili", new BilibiliParser());
    }

    public static void main(String[] args) {
        String videoUrl = "https://www.bilibili.com/video/BV1KjYTzaEcx";
        String outputPath = "output.mp4";

        try {
            // 1. 解析视频真实地址
            VideoInfo videoInfo = parseVideoUrl(videoUrl);

            // 2. 下载视频
            downloadVideo(videoInfo.getVideoUrl(), outputPath);

            // 3. 去水印处理
            removeWatermark(outputPath, "output_no_watermark.mp4");

            System.out.println("视频下载和去水印处理完成!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 解析视频URL
    private static VideoInfo parseVideoUrl(String url) throws Exception {
        // 自动识别平台
        String platform = detectPlatform(url);
        VideoParser parser = PARSERS.get(platform);
        if (parser == null) {
            throw new UnsupportedOperationException("不支持的视频平台: " + platform);
        }
        return parser.parse(url);
    }

    // 检测视频平台
    private static String detectPlatform(String url) {
        if (url.contains("douyin.com") || url.contains("iesdouyin.com")) {
            return "douyin";
        } else if (url.contains("bilibili.com")) {
            return "bilibili";
        }
        throw new IllegalArgumentException("无法识别的视频平台URL");
    }

    // 下载视频
    private static void downloadVideo(String videoUrl, String outputPath) throws Exception {
        // todo 实现下载功能

        System.out.println("视频下载完成 -> " + outputPath);
    }

    // 去水印处理
    private static void removeWatermark(String inputPath, String outputPath) throws Exception {
        // todo 去水印处理
    }
}

// 视频信息封装类
class VideoInfo {
    private String videoUrl;
    private String title;

    public VideoInfo(String videoUrl, String title) {
        this.videoUrl = videoUrl;
        this.title = title;
        System.out.println("videoUrl -> " + videoUrl);
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public String getTitle() {
        return title;
    }
}

// 视频解析器接口
interface VideoParser {
    VideoInfo parse(String url) throws Exception;
}

// 抖音视频解析器
class DouyinParser implements VideoParser {
    @Override
    public VideoInfo parse(String url) throws Exception {
        // 1. 获取重定向后的URL
        String redirectUrl = getRedirectUrl(url);

        // 2. 提取视频ID
        String videoId = extractVideoId(redirectUrl);

        // 3. 调用解析API获取无水印视频地址
        String apiUrl = "https://www.iesdouyin.com/web/api/v2/aweme/iteminfo/?item_ids=" + videoId;
        String jsonResponse = sendHttpGet(apiUrl);

        JSONObject json = JSONObject.parseObject(jsonResponse);
        JSONObject item = json.getJSONArray("item_list").getJSONObject(0);
        String title = item.getString("desc");
        String videoUrl = item.getJSONObject("video")
                .getJSONObject("play_addr")
                .getJSONArray("url_list")
                .getString(0);

        // 替换为无水印地址
        videoUrl = videoUrl.replace("playwm", "play");

        return new VideoInfo(videoUrl, title);
    }

    private String getRedirectUrl(String url) throws Exception {
        CloseableHttpClient client = HttpClients.createDefault();
        HttpGet httpGet = new HttpGet(url);
        httpGet.setHeader("User-Agent", "Mozilla/5.0");

        try (CloseableHttpResponse response = client.execute(httpGet)) {
            return response.getLastHeader("Location").getValue();
        }
    }

    private String extractVideoId(String url) {
        Pattern pattern = Pattern.compile("/video/(\\d+)");
        Matcher matcher = pattern.matcher(url);
        if (matcher.find()) {
            return matcher.group(1);
        }
        throw new IllegalArgumentException("无法从URL中提取视频ID");
    }

    private String sendHttpGet(String url) throws Exception {
        CloseableHttpClient client = HttpClients.createDefault();
        HttpGet httpGet = new HttpGet(url);
        httpGet.setHeader("User-Agent", "Mozilla/5.0");

        try (CloseableHttpResponse response = client.execute(httpGet)) {
            HttpEntity entity = response.getEntity();
            return EntityUtils.toString(entity);
        }
    }
}

// B站视频解析器
class BilibiliParser implements VideoParser {
    @Override
    public VideoInfo parse(String url) throws Exception {
        // 提取BV号
        String bvid = extractBvid(url);

        // 调用B站API获取视频信息
        String apiUrl = "https://api.bilibili.com/x/web-interface/view?bvid=" + bvid;
        String jsonResponse = sendHttpGet(apiUrl);

        JSONObject json = JSONObject.parseObject(jsonResponse);
        JSONObject data = json.getJSONObject("data");
        String title = data.getString("title");
        String cid = data.getString("cid");
        String aid = data.getString("aid");

        // 获取视频流信息
        String playUrl = "https://api.bilibili.com/x/player/wbi/playurl?bvid=" + bvid + "&cid=" + cid + "&qn=80" + "&aid=" + aid;
        String playJson = sendHttpGet(playUrl);
        System.out.println("playJson -> " + playJson);
        JSONObject playData = JSONObject.parseObject(playJson).getJSONObject("data");
        String videoUrl = playData.getJSONArray("durl").getJSONObject(0).getJSONArray("backup_url").get(0).toString();

        return new VideoInfo(videoUrl, title);
    }

    private String extractBvid(String url) {
        Pattern pattern = Pattern.compile("BV[0-9A-Za-z]{10}");
        Matcher matcher = pattern.matcher(url);
        if (matcher.find()) {
            return matcher.group();
        }
        throw new IllegalArgumentException("无法从URL中提取BV号");
    }

    private String sendHttpGet(String url) throws Exception {
        CloseableHttpClient client = HttpClients.createDefault();
        HttpGet httpGet = new HttpGet(url);
        httpGet.setHeader("User-Agent", "Mozilla/5.0");
        httpGet.setHeader("Referer", "https://www.bilibili.com");

        try (CloseableHttpResponse response = client.execute(httpGet)) {
            HttpEntity entity = response.getEntity();
            return EntityUtils.toString(entity);
        }
    }
}