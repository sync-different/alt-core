package javaapplication1;

import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.BufferedReader;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;

class StatHat {

        private static void httpPost(String path, String data) {
                try {
                        URL url = new URL(path);
                        URLConnection conn = url.openConnection();
                        conn.setDoOutput(true);
                        OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream());
                        wr.write(data);
                        wr.flush();

                        BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                        String line;
                        while ((line = rd.readLine()) != null) {
                                System.out.println(line);
                        }
                        wr.close();
                        rd.close();
                }
                catch (Exception e) {
                        System.err.println(e);
                }
        }

        public static void postValue(String userKey, String statKey, Double value) {
                try {
                        String data = URLEncoder.encode("ukey", "UTF-8") + "=" + URLEncoder.encode(userKey, "UTF-8");
                        data += "&" + URLEncoder.encode("key", "UTF-8") + "=" + URLEncoder.encode(statKey, "UTF-8");
                        data += "&" + URLEncoder.encode("value", "UTF-8") + "=" + URLEncoder.encode(value.toString(), "UTF-8");
                        httpPost("https://api.stathat.com/v", data);
                }
                catch (Exception e) {
                        System.err.println("postValue exception:  " + e);
                }
        }

        public static void postCount(String userKey, String statKey, Double count) {
                try {
                        String data = URLEncoder.encode("ukey", "UTF-8") + "=" + URLEncoder.encode(userKey, "UTF-8");
                        data += "&" + URLEncoder.encode("key", "UTF-8") + "=" + URLEncoder.encode(statKey, "UTF-8");
                        data += "&" + URLEncoder.encode("count", "UTF-8") + "=" + URLEncoder.encode(count.toString(), "UTF-8");
                        httpPost("https://api.stathat.com/c", data);
                }
                catch (Exception e) {
                        System.err.println("postCount exception:  " + e);
                }
        }

        public static void ezPostValue(String ezkey, String statName, Double value) {
                try {
                        String data = URLEncoder.encode("ezkey", "UTF-8") + "=" + URLEncoder.encode(ezkey, "UTF-8");
                        data += "&" + URLEncoder.encode("stat", "UTF-8") + "=" + URLEncoder.encode(statName, "UTF-8");
                        data += "&" + URLEncoder.encode("value", "UTF-8") + "=" + URLEncoder.encode(value.toString(), "UTF-8");
                        httpPost("https://api.stathat.com/ez", data);
                }
                catch (Exception e) {
                        System.err.println("ezPostValue exception:  " + e);
                }
        }

        public static void ezPostCount(String ezkey, String statName, Double count) {
                try {
                        String data = URLEncoder.encode("ezkey", "UTF-8") + "=" + URLEncoder.encode(ezkey, "UTF-8");
                        data += "&" + URLEncoder.encode("stat", "UTF-8") + "=" + URLEncoder.encode(statName, "UTF-8");
                        data += "&" + URLEncoder.encode("count", "UTF-8") + "=" + URLEncoder.encode(count.toString(), "UTF-8");
                        System.out.println("***EzPost: '" + data + "'");
                        httpPost("https://api.stathat.com/ez", data);
                }
                catch (Exception e) {
                        System.err.println("ezPostCount exception:  " + e);
                }
        }

            public static int ezPostCount_GA(String v, String tid, String cid, String t, String ec, String ea, String el, String ev, String an) {
                try {
                        String data = URLEncoder.encode("v", "UTF-8") + "=" + URLEncoder.encode(v, "UTF-8");
                        data += "&" + URLEncoder.encode("tid", "UTF-8") + "=" + URLEncoder.encode(tid, "UTF-8");
                        data += "&" + URLEncoder.encode("cid", "UTF-8") + "=" + URLEncoder.encode(cid, "UTF-8");
                        data += "&" + URLEncoder.encode("t", "UTF-8") + "=" + URLEncoder.encode(t, "UTF-8");
                        data += "&" + URLEncoder.encode("ec", "UTF-8") + "=" + URLEncoder.encode(ec, "UTF-8");
                        data += "&" + URLEncoder.encode("ea", "UTF-8") + "=" + URLEncoder.encode(ea, "UTF-8");
                        data += "&" + URLEncoder.encode("el", "UTF-8") + "=" + URLEncoder.encode(el, "UTF-8");
                        data += "&" + URLEncoder.encode("ev", "UTF-8") + "=" + URLEncoder.encode(ev, "UTF-8");
                        data += "&" + URLEncoder.encode("an", "UTF-8") + "=" + URLEncoder.encode(an, "UTF-8");
                        System.out.println("***EzPost: '" + data + "'");
                        
                        PostMethod postFile = new PostMethod("https://www.google-analytics.com/collect");
                                
                        postFile.setRequestEntity(new StringRequestEntity(data,null,null));
 
                        HttpClient httpclient = new HttpClient();
                        return httpclient.executeMethod(postFile);
                       
                }
                
                catch (Exception e) {
                        System.err.println("ezPostCount exception:  " + e);
                        return -1;
                }
        }


}