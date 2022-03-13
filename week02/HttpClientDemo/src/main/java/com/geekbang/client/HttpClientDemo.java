package com.geekbang.client;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class HttpClientDemo {

    private static final Logger logger = LoggerFactory.getLogger(HttpClientDemo.class);

    public static void main(String[] args) {
        HttpClient httpClient = HttpClients.createDefault();
        HttpGet getRequest = new HttpGet("http://localhost:8801");
        try {
            HttpResponse httpResponse = httpClient.execute(getRequest);
            String responseStr = EntityUtils.toString(httpResponse.getEntity(), StandardCharsets.UTF_8);
            logger.info("response: {}", responseStr);
        } catch (IOException e) {
            logger.error("request failed!");
            // wrap exception
            e.printStackTrace();
        }
    }
}
